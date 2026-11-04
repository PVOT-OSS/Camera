/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

import androidx.camera.view.PreviewView
import androidx.camera.view.ScreenFlashView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.prauga.camera.models.CameraConfiguration
import org.prauga.camera.models.FlashMode

interface CameraSession {
    val zoomState: Flow<ZoomInfo>
    val tapToFocusInfoState: Flow<FocusEvent>
    val isRecordingAudioSourceConfigured: Flow<Boolean>
    val videoRecordingDuration: StateFlow<Long>
    val videoRecordEvent: SharedFlow<RecordingEvent>

    fun attachPreview(previewView: PreviewView, screenFlashView: ScreenFlashView)
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
