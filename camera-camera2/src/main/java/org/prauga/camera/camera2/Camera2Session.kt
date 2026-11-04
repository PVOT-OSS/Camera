/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
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
import kotlin.math.roundToInt
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
    private val photoFfcMirror: () -> Boolean,
    private val onPhotoCaptureStatus: (PhotoCaptureStatus) -> Unit,
    private val onNewPicture: (Uri) -> Unit,
    private val onFailure: (cameraId: String) -> Unit,
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

    @Volatile
    private var flashMode = FlashMode.OFF

    @Volatile
    private var exposureCompensationIndex = 0

    @Volatile
    private var meteringRegions: Array<MeteringRectangle>? = null
    private var focusTapPoint: PointF? = null
    private var focusState = FocusState.IDLE
    private var activeArraySize: Rect? = null
    private var maxAfRegions = 0
    private var maxAeRegions = 0
    private val cancelFocus = Runnable {
        meteringRegions = null
        focusState = FocusState.IDLE
        sendAfTrigger(CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
        updatePreview()
    }

    private var precaptureState = PrecaptureState.IDLE
    private val precaptureTimeout = Runnable { finishPrecapture() }

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

    override val tapToFocusInfoState = MutableSharedFlow<FocusEvent>(extraBufferCapacity = 1)

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

    override fun previewBitmap(): Bitmap? {
        val textureView = textureView?.takeIf { it.isAvailable } ?: return null
        val width = (textureView.width * textureView.scaleX).roundToInt()
        val height = (textureView.height * textureView.scaleY).roundToInt()
        if (width <= 0 || height <= 0) {
            return null
        }

        return textureView.getBitmap(width, height)
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

        cameraHandler.post {
            when (flashMode) {
                FlashMode.AUTO, FlashMode.ON -> startPrecapture()
                else -> capturePhoto()
            }
        }
    }

    private fun startPrecapture() {
        val camera = cameraDevice
        val session = captureSession
        val previewSurface = previewSurface
        if (camera == null || session == null || previewSurface == null) {
            captureStateMachine.endPhoto()
            return
        }

        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
            applyControls()
            set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START,
            )
        }.build()

        precaptureState = PrecaptureState.WAITING_START
        cameraHandler.postDelayed(precaptureTimeout, PRECAPTURE_TIMEOUT_MS)
        session.capture(request, null, cameraHandler)
    }

    private fun onPreviewResult(result: CaptureResult) {
        if (focusState == FocusState.SCANNING) {
            val focused = when (result.get(CaptureResult.CONTROL_AF_STATE)) {
                CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED -> FocusEvent.State.FOCUSED
                CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> FocusEvent.State.NOT_FOCUSED
                else -> if (maxAfRegions == 0) FocusEvent.State.FOCUSED else null
            }

            focused?.let {
                focusState = FocusState.LOCKED
                val tapPoint = focusTapPoint
                scope.launch { tapToFocusInfoState.emit(FocusEvent(it, tapPoint)) }
            }
        }

        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)

        when (precaptureState) {
            PrecaptureState.IDLE -> {}

            PrecaptureState.WAITING_START -> when (aeState) {
                CameraMetadata.CONTROL_AE_STATE_PRECAPTURE -> {
                    precaptureState = PrecaptureState.WAITING_DONE
                }

                null,
                CameraMetadata.CONTROL_AE_STATE_CONVERGED,
                CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED -> finishPrecapture()
            }

            PrecaptureState.WAITING_DONE -> if (aeState != CameraMetadata.CONTROL_AE_STATE_PRECAPTURE) {
                finishPrecapture()
            }
        }
    }

    private fun finishPrecapture() {
        if (precaptureState == PrecaptureState.IDLE) {
            return
        }

        precaptureState = PrecaptureState.IDLE
        cameraHandler.removeCallbacks(precaptureTimeout)

        capturePhoto()
    }

    private fun capturePhoto() {
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
        if (lensFacing == CameraMetadata.LENS_FACING_FRONT && photoFfcMirror()) {
            JpegExif.flipHorizontally(bytes)
        }
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

    override fun setFlashMode(flashMode: FlashMode) {
        this.flashMode = flashMode

        updatePreview()
    }

    override fun setExposureCompensationIndex(exposureCompensationIndex: Int) {
        this.exposureCompensationIndex = exposureCompensationIndex

        updatePreview()
    }

    override fun focus(x: Float, y: Float) {
        val textureView = textureView ?: return
        val activeArraySize = activeArraySize ?: return

        val displayedWidth = textureView.width * textureView.scaleX
        val displayedHeight = textureView.height * textureView.scaleY
        if (displayedWidth <= 0f || displayedHeight <= 0f) {
            return
        }

        val u = ((x - (textureView.width - displayedWidth) / 2f) / displayedWidth).coerceIn(0f, 1f)
        val v = ((y - (textureView.height - displayedHeight) / 2f) / displayedHeight).coerceIn(0f, 1f)

        val (sensorX, sensorY) = Camera2Metering.viewToSensor(
            u, v, sensorOrientation, lensFacing == CameraMetadata.LENS_FACING_FRONT
        )
        val region = Camera2Metering.region(
            sensorX, sensorY, activeArraySize.width(), activeArraySize.height()
        )

        cameraHandler.post {
            cameraHandler.removeCallbacks(cancelFocus)

            meteringRegions = arrayOf(region)
            focusTapPoint = PointF(x, y)
            focusState = FocusState.SCANNING
            scope.launch { tapToFocusInfoState.emit(FocusEvent(FocusEvent.State.STARTED, PointF(x, y))) }

            updatePreview()
            sendAfTrigger(CameraMetadata.CONTROL_AF_TRIGGER_START)

            cameraHandler.postDelayed(cancelFocus, FOCUS_RESET_MS)
        }
    }

    private fun sendAfTrigger(trigger: Int) {
        val camera = cameraDevice ?: return
        val session = captureSession ?: return
        val previewSurface = previewSurface ?: return

        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
            applyControls()
            set(CaptureRequest.CONTROL_AF_TRIGGER, trigger)
        }.build()

        try {
            session.capture(request, null, cameraHandler)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Unable to send the AF trigger", e)
        }
    }

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

    private fun openCamera() {
        try {
            startCamera()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Unable to open the camera", e)
            fail()
        }
    }

    private fun fail() {
        cameraConfiguration?.camera?.cameraId?.let(onFailure)
    }

    @SuppressLint("MissingPermission")
    private fun startCamera() {
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
        activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        maxAfRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
        maxAeRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0

        val photoFormat = when (cameraConfiguration.photoOutputFormat) {
            PhotoOutputFormat.JPEG_ULTRA_HDR -> ImageFormat.JPEG_R
            else -> ImageFormat.JPEG
        }
        val photoSize = Camera2Sizes.jpegSize(
            characteristics,
            cameraConfiguration.photoAspectRatio,
            photoFormat,
            cameraConfiguration.enableHighResolution,
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
                if (error == ERROR_CAMERA_DEVICE || error == ERROR_CAMERA_SERVICE) {
                    fail()
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
                    fail()
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

            onPreviewResult(result)
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
        val meteringRegions = meteringRegions
        if (meteringRegions != null) {
            set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
            if (maxAfRegions > 0) {
                set(CaptureRequest.CONTROL_AF_REGIONS, meteringRegions)
            }
            if (maxAeRegions > 0) {
                set(CaptureRequest.CONTROL_AE_REGIONS, meteringRegions)
            }
        } else {
            set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
        }
        set(
            CaptureRequest.CONTROL_AE_MODE,
            when (flashMode) {
                FlashMode.AUTO -> CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH
                FlashMode.ON -> CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                else -> CameraMetadata.CONTROL_AE_MODE_ON
            },
        )
        set(
            CaptureRequest.FLASH_MODE,
            when (flashMode) {
                FlashMode.TORCH -> CameraMetadata.FLASH_MODE_TORCH
                else -> CameraMetadata.FLASH_MODE_OFF
            },
        )
        set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompensationIndex)
    }

    private enum class FocusState {
        IDLE,
        SCANNING,
        LOCKED,
    }

    private enum class PrecaptureState {
        IDLE,
        WAITING_START,
        WAITING_DONE,
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
        private const val PRECAPTURE_TIMEOUT_MS = 1500L
        private const val FOCUS_RESET_MS = 5000L

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
