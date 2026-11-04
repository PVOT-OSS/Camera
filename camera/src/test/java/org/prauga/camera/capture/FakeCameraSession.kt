/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

import android.graphics.Bitmap
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.prauga.camera.capture.models.CameraConfiguration
import org.prauga.camera.capture.models.FlashMode

class FakeCameraSession : CameraSession {
    val calls = mutableListOf<String>()
    var configuration: CameraConfiguration? = null
    var lastFlashMode: FlashMode? = null
    var lastExposureCompensationIndex: Int? = null

    override val previewStreaming = MutableStateFlow(false)
    override val zoomState = MutableSharedFlow<ZoomInfo>()
    override val tapToFocusInfoState = MutableSharedFlow<FocusEvent>()
    override val isRecordingAudioSourceConfigured = flowOf(false)
    override val videoRecordingDuration = MutableStateFlow(0L)
    override val videoRecordEvent = MutableSharedFlow<RecordingEvent>()

    override fun attachPreview(target: PreviewTarget) {
        calls += "attachPreview"
    }

    override fun previewBitmap(): Bitmap? = null

    override fun configure(cameraConfiguration: CameraConfiguration, lifecycleOwner: LifecycleOwner) {
        calls += "configure"
        configuration = cameraConfiguration
    }

    override fun release() {
        calls += "release"
    }

    override fun close() {
        calls += "close"
    }

    override fun takePhoto() {
        calls += "takePhoto"
    }

    override fun captureVideo() {
        calls += "captureVideo"
    }

    override fun toggleRecordingPause() {
        calls += "toggleRecordingPause"
    }

    override fun setAudioEnabled(enabled: Boolean) {
        calls += "setAudioEnabled"
    }

    override fun setFlashMode(flashMode: FlashMode) {
        lastFlashMode = flashMode
    }

    override fun setExposureCompensationIndex(exposureCompensationIndex: Int) {
        lastExposureCompensationIndex = exposureCompensationIndex
    }

    override fun focus(x: Float, y: Float) {
        calls += "focus"
    }

    override fun setLinearZoom(linearZoom: Float) {}

    override fun pinchToZoom(scaleFactor: Float) {}

    override fun smoothZoom(zoomRatio: Float) {}

    override fun resetZoom() {}

    override fun zoomIn() {}

    override fun zoomOut() {}
}
