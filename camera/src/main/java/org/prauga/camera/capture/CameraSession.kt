/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.prauga.camera.capture.models.CameraConfiguration
import org.prauga.camera.capture.models.FlashMode

interface CameraSession {
    val previewStreaming: StateFlow<Boolean>
    val zoomState: Flow<ZoomInfo>
    val tapToFocusInfoState: Flow<FocusEvent>
    val isRecordingAudioSourceConfigured: Flow<Boolean>
    val videoRecordingDuration: StateFlow<Long>
    val videoRecordEvent: SharedFlow<RecordingEvent>

    fun attachPreview(target: PreviewTarget)
    fun configure(cameraConfiguration: CameraConfiguration, lifecycleOwner: LifecycleOwner)
    fun close()

    fun takePhoto()
    fun captureVideo()
    fun toggleRecordingPause()
    fun setAudioEnabled(enabled: Boolean)

    fun setFlashMode(flashMode: FlashMode)
    fun setExposureCompensationIndex(exposureCompensationIndex: Int)

    fun setLinearZoom(linearZoom: Float)
    fun pinchToZoom(scaleFactor: Float)
    fun smoothZoom(zoomRatio: Float)
    fun resetZoom()
    fun zoomIn()
    fun zoomOut()
}
