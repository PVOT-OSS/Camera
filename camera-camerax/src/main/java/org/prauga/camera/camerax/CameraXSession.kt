/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camerax

import android.content.Context
import android.location.Location
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.asFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.prauga.camera.capture.CameraSession
import org.prauga.camera.capture.CaptureSounds
import org.prauga.camera.capture.CaptureStateMachine
import org.prauga.camera.capture.PhotoCaptureStatus
import org.prauga.camera.capture.PreviewTarget
import org.prauga.camera.capture.models.Camera
import org.prauga.camera.capture.models.CameraConfiguration
import org.prauga.camera.capture.models.CameraFacing
import org.prauga.camera.capture.models.ExtensionMode
import org.prauga.camera.capture.models.FlashMode

class CameraXSession(
    context: Context,
    scope: CoroutineScope,
    captureSounds: CaptureSounds,
    captureStateMachine: CaptureStateMachine,
    inSingleCaptureMode: StateFlow<Boolean>,
    qrImageAnalyzer: ImageAnalysis.Analyzer,
    extensionCameraSelector: (Camera, ExtensionMode) -> CameraSelector,
    location: () -> Location?,
    cameraFacing: () -> CameraFacing,
    photoFfcMirror: () -> Boolean,
    onPhotoCaptureStatus: (PhotoCaptureStatus) -> Unit,
    onNewPicture: (Uri) -> Unit,
    onNewVideo: (Uri) -> Unit,
) : CameraSession {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val cameraController = LifecycleCameraController(context)

    private val binder = CameraXBinder(
        cameraController,
        cameraExecutor,
        qrImageAnalyzer,
        extensionCameraSelector,
    )

    private val capture = CameraXCapture(
        context,
        cameraController,
        cameraExecutor,
        scope,
        captureSounds,
        captureStateMachine,
        inSingleCaptureMode,
        location,
        cameraFacing,
        photoFfcMirror,
        onPhotoCaptureStatus,
        onNewPicture,
        onNewVideo,
    )

    private val zoom = CameraXZoom(cameraController) { cameraController.zoomState.value }

    private val _previewStreaming = MutableStateFlow(false)
    override val previewStreaming = _previewStreaming.asStateFlow()

    override val zoomState = cameraController.zoomState.asFlow().map { it.toZoomInfo() }

    override val tapToFocusInfoState = cameraController.tapToFocusInfoState.asFlow().map {
        it.toFocusEvent()
    }

    override val isRecordingAudioSourceConfigured = capture.videoRecording.map {
        it?.isAudioSourceConfigured == true
    }

    override val videoRecordingDuration = capture.videoRecordingDuration

    override val videoRecordEvent = capture.videoRecordEvent

    override fun attachPreview(target: PreviewTarget) {
        require(target is CameraXPreviewTarget) { "Unsupported preview target $target" }

        target.previewView.controller = cameraController
        target.screenFlashView.setController(cameraController)
        target.previewView.previewStreamState.observeForever {
            _previewStreaming.value = it == PreviewView.StreamState.STREAMING
        }
    }

    override fun configure(
        cameraConfiguration: CameraConfiguration,
        lifecycleOwner: LifecycleOwner,
    ) = binder.bind(cameraConfiguration, lifecycleOwner)

    override fun close() {
        cameraController.unbind()

        cameraExecutor.shutdown()
    }

    override fun takePhoto() = capture.takePhoto()

    override fun captureVideo() = capture.captureVideo()

    override fun toggleRecordingPause() = capture.onVideoRecordingStateButtonPress()

    override fun setAudioEnabled(enabled: Boolean) = capture.setAudioEnabled(enabled)

    override fun setFlashMode(flashMode: FlashMode) {
        cameraController.flashMode = flashMode
    }

    override fun setExposureCompensationIndex(exposureCompensationIndex: Int) {
        cameraController.cameraControl?.setExposureCompensationIndex(exposureCompensationIndex)
    }

    override fun setLinearZoom(linearZoom: Float) {
        cameraController.setLinearZoom(linearZoom)
    }

    override fun pinchToZoom(scaleFactor: Float) {
        cameraController.onPinchToZoom(scaleFactor)
    }

    override fun smoothZoom(zoomRatio: Float) = zoom.smoothZoom(zoomRatio)

    override fun resetZoom() = zoom.resetZoom()

    override fun zoomIn() {
        zoom.zoomIn()
    }

    override fun zoomOut() {
        zoom.zoomOut()
    }
}
