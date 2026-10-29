/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.camera

import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.prauga.camera.R
import org.prauga.camera.ext.select
import org.prauga.camera.ui.views.CameraModeSelectorLayout
import org.prauga.camera.viewmodels.CameraViewModel

class ModeSelector(
    root: View,
    private val viewModel: CameraViewModel,
) {
    private val cameraModeSelectorLayout = root.findViewById<CameraModeSelectorLayout>(R.id.cameraModeSelectorLayout)

    init {
        cameraModeSelectorLayout.onModeSelectedCallback = {
            viewModel.setCameraMode(it)
        }
    }

    fun CoroutineScope.bind() {
        launch {
            viewModel.uiState.select { it.cameraMode }.collectLatest { cameraMode ->
                cameraModeSelectorLayout.setCurrentCameraMode(cameraMode)
            }
        }

        launch {
            viewModel.uiState.select { it.inSingleCaptureMode }.collectLatest { inSingleCaptureMode ->
                cameraModeSelectorLayout.setInSingleCaptureMode(inSingleCaptureMode)
            }
        }

        launch {
            viewModel.uiState.select { it.cameraState }.collectLatest { cameraState ->
                cameraModeSelectorLayout.setCameraState(cameraState)
            }
        }

        launch {
            viewModel.uiState.select { it.videoRecordingDuration }.collectLatest { videoRecordingDuration ->
                cameraModeSelectorLayout.setVideoRecordingDuration(videoRecordingDuration)
            }
        }
    }
}
