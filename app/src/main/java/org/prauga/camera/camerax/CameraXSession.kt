/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camerax

import android.content.Context
import android.location.Location
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.ScreenFlashView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.asFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.prauga.camera.capture.CameraSession
import org.prauga.camera.capture.CaptureStateMachine
import org.prauga.camera.ext.flashMode
import org.prauga.camera.models.Camera
import org.prauga.camera.models.CameraConfiguration
import org.prauga.camera.models.CameraFacing
import org.prauga.camera.models.Event
import org.prauga.camera.models.ExtensionMode
import org.prauga.camera.models.FlashMode
import org.prauga.camera.repositories.MediaRepository
import org.prauga.camera.utils.CameraSoundsUtils

class CameraXSession(
    context: Context,
    scope: CoroutineScope,
    mediaRepository: MediaRepository,
    cameraSoundsUtils: CameraSoundsUtils,
    captureStateMachine: CaptureStateMachine,
    inSingleCaptureMode: StateFlow<Boolean>,
    qrImageAnalyzer: ImageAnalysis.Analyzer,
    extensionCameraSelector: (Camera, ExtensionMode) -> CameraSelector,
    location: () -> Location?,
    cameraFacing: () -> CameraFacing,
    photoFfcMirror: () -> Boolean,
    emitEvent: (Event) -> Unit,
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
        mediaRepository,
        cameraSoundsUtils,
        captureStateMachine,
        inSingleCaptureMode,
        location,
        cameraFacing,
        photoFfcMirror,
        emitEvent,
    )

    private val zoom = CameraXZoom(cameraController) { cameraController.zoomState.value }

    override val zoomState = cameraController.zoomState.asFlow().map { it.toZoomInfo() }

    override val tapToFocusInfoState = cameraController.tapToFocusInfoState.asFlow()

    override val isRecordingAudioSourceConfigured = capture.videoRecording.map {
        it?.isAudioSourceConfigured == true
    }

    override val videoRecordingDuration = capture.videoRecordingDuration

    override val videoRecordEvent = capture.videoRecordEvent

    override fun attachPreview(previewView: PreviewView, screenFlashView: ScreenFlashView) {
        previewView.controller = cameraController
        screenFlashView.setController(cameraController)
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
