/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import org.prauga.camera.capture.models.CameraConfiguration
import org.prauga.camera.capture.models.FlashMode

@OptIn(ExperimentalCoroutinesApi::class)
class RoutingCameraSession(
    scope: CoroutineScope,
    private val primary: CameraSession,
    private val photo: CameraSession,
    private val usePhotoSession: (CameraConfiguration) -> Boolean,
) : CameraSession {
    class Target(
        val primary: PreviewTarget,
        val photo: PreviewTarget,
    ) : PreviewTarget

    private val _active = MutableStateFlow(primary)
    val active: CameraSession
        get() = _active.value

    private var flashMode: FlashMode? = null
    private var exposureCompensationIndex: Int? = null

    override val previewStreaming = combine(
        _active,
        primary.previewStreaming,
        photo.previewStreaming,
    ) { active, primaryStreaming, photoStreaming ->
        if (active === photo) photoStreaming else primaryStreaming
    }.stateIn(scope, SharingStarted.Eagerly, false)

    override val zoomState = _active.flatMapLatest { it.zoomState }
    override val tapToFocusInfoState = _active.flatMapLatest { it.tapToFocusInfoState }
    override val isRecordingAudioSourceConfigured = primary.isRecordingAudioSourceConfigured
    override val videoRecordingDuration = primary.videoRecordingDuration
    override val videoRecordEvent = primary.videoRecordEvent

    override fun attachPreview(target: PreviewTarget) {
        require(target is Target) { "Unsupported preview target $target" }

        primary.attachPreview(target.primary)
        photo.attachPreview(target.photo)
    }

    override fun configure(cameraConfiguration: CameraConfiguration, lifecycleOwner: LifecycleOwner) {
        val next = if (usePhotoSession(cameraConfiguration)) photo else primary

        if (next !== active) {
            active.release()
            _active.value = next
        }

        next.configure(cameraConfiguration, lifecycleOwner)

        flashMode?.let(next::setFlashMode)
        exposureCompensationIndex?.let(next::setExposureCompensationIndex)
    }

    override fun release() = active.release()

    override fun close() {
        primary.close()
        photo.close()
    }

    override fun takePhoto() = active.takePhoto()

    override fun captureVideo() = active.captureVideo()

    override fun toggleRecordingPause() = active.toggleRecordingPause()

    override fun setAudioEnabled(enabled: Boolean) = primary.setAudioEnabled(enabled)

    override fun setFlashMode(flashMode: FlashMode) {
        this.flashMode = flashMode
        active.setFlashMode(flashMode)
    }

    override fun setExposureCompensationIndex(exposureCompensationIndex: Int) {
        this.exposureCompensationIndex = exposureCompensationIndex
        active.setExposureCompensationIndex(exposureCompensationIndex)
    }

    override fun setLinearZoom(linearZoom: Float) = active.setLinearZoom(linearZoom)

    override fun pinchToZoom(scaleFactor: Float) = active.pinchToZoom(scaleFactor)

    override fun smoothZoom(zoomRatio: Float) = active.smoothZoom(zoomRatio)

    override fun resetZoom() = active.resetZoom()

    override fun zoomIn() = active.zoomIn()

    override fun zoomOut() = active.zoomOut()
}
