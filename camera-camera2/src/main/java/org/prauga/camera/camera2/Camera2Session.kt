/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import org.prauga.camera.capture.CameraSession
import org.prauga.camera.capture.FocusEvent
import org.prauga.camera.capture.PreviewTarget
import org.prauga.camera.capture.RecordingEvent
import org.prauga.camera.capture.ZoomInfo
import org.prauga.camera.capture.models.CameraConfiguration
import org.prauga.camera.capture.models.FlashMode

class Camera2Session(
    context: Context,
) : CameraSession {
    private val cameraManager = context.getSystemService(CameraManager::class.java)

    private val cameraThread = HandlerThread(LOG_TAG).apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var container: ViewGroup? = null
    private var textureView: TextureView? = null

    private var cameraConfiguration: CameraConfiguration.Photo? = null
    private var lifecycleOwner: LifecycleOwner? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var previewSize: Size? = null
    private var sensorOrientation = 0

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

    fun release() {
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null

        closeCamera()

        textureView?.let { container?.removeView(it) }
        textureView = null
    }

    override fun close() {
        release()

        cameraThread.quitSafely()
    }

    override fun takePhoto() {}

    override fun captureVideo() = error("Camera2 doesn't record video")

    override fun toggleRecordingPause() = error("Camera2 doesn't record video")

    override fun setAudioEnabled(enabled: Boolean) {}

    override fun setFlashMode(flashMode: FlashMode) {}

    override fun setExposureCompensationIndex(exposureCompensationIndex: Int) {}

    override fun setLinearZoom(linearZoom: Float) {}

    override fun pinchToZoom(scaleFactor: Float) {}

    override fun smoothZoom(zoomRatio: Float) {}

    override fun resetZoom() {}

    override fun zoomIn() {}

    override fun zoomOut() {}

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

        val previewSize = Camera2Sizes.previewSize(
            characteristics, cameraConfiguration.photoAspectRatio
        )
        this.previewSize = previewSize
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        updatePreviewScale()

        val previewSurface = Surface(surfaceTexture)
        this.previewSurface = previewSurface

        _zoomState.value = Camera2Zoom.zoomInfo(characteristics, 1f)

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                createCaptureSession(camera, previewSurface)
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
    private fun createCaptureSession(camera: CameraDevice, previewSurface: Surface) {
        camera.createCaptureSession(
            listOf(previewSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice != camera) {
                        session.close()
                        return
                    }
                    captureSession = session
                    startPreview(camera, session, previewSurface)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(LOG_TAG, "Session configuration failed")
                }
            },
            cameraHandler,
        )
    }

    private fun startPreview(
        camera: CameraDevice,
        session: CameraCaptureSession,
        previewSurface: Surface,
    ) {
        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
            set(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            )
        }.build()

        session.setRepeatingRequest(request, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult,
            ) {
                _previewStreaming.value = true
            }
        }, cameraHandler)
    }

    private fun closeCamera() {
        _previewStreaming.value = false

        captureSession?.close()
        captureSession = null

        cameraDevice?.close()
        cameraDevice = null

        previewSurface?.release()
        previewSurface = null
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
    }
}
