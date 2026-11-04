/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.location.Location
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.prauga.camera.capture.CameraSession
import org.prauga.camera.capture.CaptureSounds
import org.prauga.camera.capture.CaptureStateMachine
import org.prauga.camera.capture.FocusEvent
import org.prauga.camera.capture.PhotoCaptureStatus
import org.prauga.camera.capture.PreviewTarget
import org.prauga.camera.capture.RecordingEvent
import org.prauga.camera.capture.ZoomInfo
import org.prauga.camera.capture.models.CameraConfiguration
import org.prauga.camera.capture.models.ExtensionMode
import org.prauga.camera.capture.models.FlashMode
import org.prauga.camera.capture.models.PhotoOutputFormat
import org.prauga.camera.capture.nextPowerOfTwo
import org.prauga.camera.capture.previousPowerOfTwo

class Camera2Session(
    context: Context,
    private val scope: CoroutineScope,
    private val captureSounds: CaptureSounds,
    private val captureStateMachine: CaptureStateMachine,
    private val inSingleCaptureMode: StateFlow<Boolean>,
    private val location: () -> Location?,
    private val onPhotoCaptureStatus: (PhotoCaptureStatus) -> Unit,
    private val onNewPicture: (Uri) -> Unit,
) : CameraSession {
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val contentResolver = context.contentResolver

    private val cameraThread = HandlerThread(LOG_TAG).apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var container: ViewGroup? = null
    private var textureView: TextureView? = null
    private val hiddenViews = mutableListOf<View>()

    private var cameraConfiguration: CameraConfiguration.Photo? = null
    private var lifecycleOwner: LifecycleOwner? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var previewSize: Size? = null
    private var imageReader: ImageReader? = null
    private var sensorOrientation = 0
    private var lensFacing = CameraMetadata.LENS_FACING_BACK
    private var deviceOrientation = 0
    private var photoTimestamp = 0L

    @Volatile
    private var zoomRatio = 1f

    @Volatile
    private var zoomRatioRange = 1f..1f
    private var zoomAnimator: ValueAnimator? = null

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation != ORIENTATION_UNKNOWN) {
                deviceOrientation = (orientation + 45) / 90 * 90 % 360
            }
        }
    }

    private val _previewStreaming = MutableStateFlow(false)
    override val previewStreaming = _previewStreaming.asStateFlow()

    private val _zoomState = MutableStateFlow<ZoomInfo?>(null)
    override val zoomState = _zoomState.filterNotNull()

    override val tapToFocusInfoState = MutableSharedFlow<FocusEvent>()

    override val isRecordingAudioSourceConfigured = flowOf(false)
    override val videoRecordingDuration = MutableStateFlow(0L).asStateFlow()
    override val videoRecordEvent = MutableSharedFlow<RecordingEvent>()

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> openCamera()
            Lifecycle.Event.ON_STOP -> closeCamera()
            else -> {}
        }
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            updatePreviewScale()
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    override fun attachPreview(target: PreviewTarget) {
        require(target is Camera2PreviewTarget) { "Unsupported preview target $target" }

        container = target.container
    }

    override fun configure(cameraConfiguration: CameraConfiguration, lifecycleOwner: LifecycleOwner) {
        require(cameraConfiguration is CameraConfiguration.Photo) {
            "Camera2 only supports photo mode, got $cameraConfiguration"
        }

        release()

        this.cameraConfiguration = cameraConfiguration
        this.lifecycleOwner = lifecycleOwner

        val container = container ?: error("No preview target attached")
        container.children.filter { it.isVisible }.forEach {
            it.isVisible = false
            hiddenViews.add(it)
        }
        textureView = TextureView(container.context).also {
            it.surfaceTextureListener = surfaceTextureListener
            container.addView(
                it,
                0,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    override fun release() {
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null

        closeCamera()

        textureView?.let { container?.removeView(it) }
        textureView = null

        hiddenViews.forEach { it.isVisible = true }
        hiddenViews.clear()
    }

    override fun close() {
        release()

        cameraThread.quitSafely()
    }

    override fun takePhoto() {
        if (!captureStateMachine.startPhoto()) {
            return
        }

        val camera = cameraDevice
        val session = captureSession
        val imageReader = imageReader
        if (camera == null || session == null || imageReader == null) {
            captureStateMachine.endPhoto()
            return
        }

        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(imageReader.surface)
            applyControls()
            set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation())
            if (!inSingleCaptureMode.value) {
                location()?.let { set(CaptureRequest.JPEG_GPS_LOCATION, it) }
            }
        }.build()

        photoTimestamp = System.currentTimeMillis()

        session.capture(request, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long,
            ) {
                onPhotoCaptureStatus(PhotoCaptureStatus.CaptureStarted)

                captureSounds.playShutterClick()
            }

            override fun onCaptureFailed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                failure: CaptureFailure,
            ) {
                Log.e(LOG_TAG, "Photo capture failed: ${failure.reason}")
                onPhotoCaptureStatus(
                    PhotoCaptureStatus.Error(Exception("Capture failed: ${failure.reason}"))
                )
                captureStateMachine.endPhoto()
            }
        }, cameraHandler)
    }

    private fun onImageAvailable(imageReader: ImageReader) {
        val bytes = imageReader.acquireNextImage()?.use { image ->
            val buffer = image.planes[0].buffer
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        } ?: return
        val timestamp = photoTimestamp
        val singleCapture = inSingleCaptureMode.value

        scope.launch(Dispatchers.IO) {
            try {
                if (singleCapture) {
                    val photoOutputStream = ByteArrayOutputStream(bytes.size).apply { write(bytes) }
                    captureStateMachine.endPhoto()
                    onPhotoCaptureStatus(PhotoCaptureStatus.ImageSaved(null, photoOutputStream))
                } else {
                    val uri = Camera2MediaStore.savePhoto(
                        contentResolver, bytes, "image/jpeg", timestamp
                    )
                    Log.d(LOG_TAG, "Photo capture succeeded: $uri")
                    onNewPicture(uri)
                    captureStateMachine.endPhoto()
                    onPhotoCaptureStatus(PhotoCaptureStatus.ImageSaved(uri, null))
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Photo save failed", e)
                onPhotoCaptureStatus(PhotoCaptureStatus.Error(e))
                captureStateMachine.endPhoto()
            }
        }
    }

    private fun jpegOrientation(): Int {
        val orientation = when (lensFacing) {
            CameraMetadata.LENS_FACING_FRONT -> -deviceOrientation
            else -> deviceOrientation
        }

        return (sensorOrientation + orientation + 360) % 360
    }

    override fun captureVideo() = error("Camera2 doesn't record video")

    override fun toggleRecordingPause() = error("Camera2 doesn't record video")

    override fun setAudioEnabled(enabled: Boolean) {}

    override fun setFlashMode(flashMode: FlashMode) {}

    override fun setExposureCompensationIndex(exposureCompensationIndex: Int) {}

    override fun setLinearZoom(linearZoom: Float) =
        setZoomRatio(Camera2Zoom.zoomRatio(zoomRatioRange, linearZoom))

    override fun pinchToZoom(scaleFactor: Float) = setZoomRatio(zoomRatio * scaleFactor)

    override fun smoothZoom(zoomRatio: Float) {
        if (zoomAnimator?.isRunning == true) {
            return
        }

        zoomAnimator = ValueAnimator.ofFloat(
            this.zoomRatio, zoomRatio.coerceIn(zoomRatioRange)
        ).apply {
            addUpdateListener { setZoomRatio(it.animatedValue as Float) }
            start()
        }
    }

    override fun resetZoom() = smoothZoom(1f)

    override fun zoomIn() = smoothZoom(zoomRatio.nextPowerOfTwo())

    override fun zoomOut() = smoothZoom(zoomRatio.previousPowerOfTwo())

    private fun setZoomRatio(zoomRatio: Float) {
        this.zoomRatio = zoomRatio.coerceIn(zoomRatioRange)
        _zoomState.value = Camera2Zoom.zoomInfo(zoomRatioRange, this.zoomRatio)

        updatePreview()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val cameraConfiguration = cameraConfiguration ?: return
        if (cameraDevice != null) {
            return
        }
        if (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != true) {
            return
        }
        val surfaceTexture = textureView?.takeIf { it.isAvailable }?.surfaceTexture ?: return

        val cameraId = cameraConfiguration.camera.cameraId
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            ?: CameraMetadata.LENS_FACING_BACK

        val photoFormat = when (cameraConfiguration.photoOutputFormat) {
            PhotoOutputFormat.JPEG_ULTRA_HDR -> ImageFormat.JPEG_R
            else -> ImageFormat.JPEG
        }
        val photoSize = Camera2Sizes.jpegSize(
            characteristics, cameraConfiguration.photoAspectRatio, photoFormat
        )
        val imageReader = ImageReader.newInstance(
            photoSize.width, photoSize.height, photoFormat, 2
        ).apply {
            setOnImageAvailableListener(::onImageAvailable, cameraHandler)
        }
        this.imageReader = imageReader
        orientationListener.enable()

        val previewSize = Camera2Sizes.previewSize(
            characteristics, cameraConfiguration.photoAspectRatio
        )
        this.previewSize = previewSize
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        updatePreviewScale()

        val previewSurface = Surface(surfaceTexture)
        this.previewSurface = previewSurface

        zoomRatioRange = Camera2Zoom.zoomRatioRange(characteristics)
        zoomRatio = 1f.coerceIn(zoomRatioRange)
        _zoomState.value = Camera2Zoom.zoomInfo(zoomRatioRange, zoomRatio)

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                createCaptureSession(camera, previewSurface, imageReader.surface)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                if (cameraDevice == camera) {
                    cameraDevice = null
                }
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(LOG_TAG, "Camera $cameraId error $error")
                camera.close()
                if (cameraDevice == camera) {
                    cameraDevice = null
                }
            }
        }, cameraHandler)
    }

    @Suppress("DEPRECATION")
    private fun createCaptureSession(
        camera: CameraDevice,
        previewSurface: Surface,
        photoSurface: Surface,
    ) {
        camera.createCaptureSession(
            listOf(previewSurface, photoSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice != camera) {
                        session.close()
                        return
                    }
                    captureSession = session
                    updatePreview()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(LOG_TAG, "Session configuration failed")
                }
            },
            cameraHandler,
        )
    }

    private val previewCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            _previewStreaming.value = true
        }
    }

    private fun updatePreview() = cameraHandler.post {
        val camera = cameraDevice ?: return@post
        val session = captureSession ?: return@post
        val previewSurface = previewSurface ?: return@post

        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
            applyControls()
        }.build()

        try {
            session.setRepeatingRequest(request, previewCallback, cameraHandler)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Unable to update the preview", e)
        }
    }

    private fun CaptureRequest.Builder.applyControls() {
        set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
        }
    }

    private fun closeCamera() {
        _previewStreaming.value = false

        captureSession?.close()
        captureSession = null

        cameraDevice?.close()
        cameraDevice = null

        previewSurface?.release()
        previewSurface = null

        imageReader?.close()
        imageReader = null

        orientationListener.disable()
    }

    private fun updatePreviewScale() {
        val textureView = textureView ?: return
        val previewSize = previewSize ?: return

        val (scaleX, scaleY) = Camera2Sizes.fitCenterScale(
            textureView.width,
            textureView.height,
            previewSize.width,
            previewSize.height,
            sensorOrientation,
        )
        textureView.scaleX = scaleX
        textureView.scaleY = scaleY
    }

    companion object {
        private const val LOG_TAG = "Camera2Session"

        val isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        fun supports(cameraConfiguration: CameraConfiguration) = isSupported
                && cameraConfiguration is CameraConfiguration.Photo
                && cameraConfiguration.extensionMode == ExtensionMode.NONE
                && when (cameraConfiguration.photoOutputFormat) {
                    PhotoOutputFormat.JPEG -> true
                    PhotoOutputFormat.JPEG_ULTRA_HDR ->
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    else -> false
                }
    }
}
