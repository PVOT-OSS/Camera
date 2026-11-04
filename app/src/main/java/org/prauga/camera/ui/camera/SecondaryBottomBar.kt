/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.camera

import android.view.View
import android.widget.ImageButton
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.prauga.camera.R
import org.prauga.camera.ext.select
import org.prauga.camera.ext.smoothRotate
import org.prauga.camera.models.CameraMode
import org.prauga.camera.models.FlashMode
import org.prauga.camera.ui.views.LensSelectorLayout
import org.prauga.camera.viewmodels.CameraViewModel

class SecondaryBottomBar(
    root: View,
    private val viewModel: CameraViewModel,
    onPro: () -> Unit,
) {
    private val proButton = root.findViewById<ImageButton>(R.id.proButton)
    private val lensSelectorLayout = root.findViewById<LensSelectorLayout>(R.id.lensSelectorLayout)
    private val flashButton = root.findViewById<ImageButton>(R.id.flashButton)

    init {
        proButton.setOnClickListener { onPro() }
        flashButton.setOnClickListener { viewModel.cycleFlashMode(false) }
        flashButton.setOnLongClickListener { viewModel.cycleFlashMode(true) }

        // Set lens switching callback
        lensSelectorLayout.onCameraChangeCallback = {
            viewModel.selectCamera(it)
        }
        lensSelectorLayout.onZoomRatioChangeCallback = {
            viewModel.smoothZoom(it)
        }
        lensSelectorLayout.onResetZoomRatioCallback = {
            viewModel.resetZoom()
        }
    }

    fun CoroutineScope.bind() {
        launch {
            viewModel.uiState.select { it.cameraMode }.collectLatest { cameraMode ->
                proButton.isVisible = cameraMode != CameraMode.QR
            }
        }

        launch {
            viewModel.uiState.select { it.cameraState }.collectLatest { cameraState ->
                lensSelectorLayout.setCameraState(cameraState)
            }
        }

        launch {
            viewModel.uiState.select { it.screenRotation }.collectLatest { screenRotation ->
                val compensationValue = screenRotation.compensationValue.toFloat()

                proButton.smoothRotate(compensationValue)
                lensSelectorLayout.setScreenRotation(screenRotation)
                flashButton.smoothRotate(compensationValue)
            }
        }

        launch {
            viewModel.uiState.select { it.zoomState }.collectLatest { zoomState ->
                zoomState?.takeIf { it.minZoomRatio != it.maxZoomRatio }?.let {
                    lensSelectorLayout.onZoomRatioChanged(it.zoomRatio)
                }
            }
        }

        launch {
            viewModel.uiState.select { it.lenses }.collectLatest { (activeCamera, availableCameras) ->
                // Update lens selector
                activeCamera?.let {
                    lensSelectorLayout.setCamera(
                        it,
                        availableCameras,
                    )
                }
            }
        }

        launch {
            viewModel.uiState.select { it.supportedFlashModes }.collectLatest { supportedFlashModes ->
                // Hide the button if the only available mode is off,
                // we want the user to know if any other mode is being used
                flashButton.isVisible = supportedFlashModes.size != 1
                        || supportedFlashModes.first() != FlashMode.OFF
            }
        }

        launch {
            viewModel.uiState.select { it.flashMode }.collectLatest { flashMode ->
                // Update secondary bar buttons
                flashButton.setImageResource(
                    when (flashMode) {
                        FlashMode.OFF -> R.drawable.ic_flash_off
                        FlashMode.AUTO -> R.drawable.ic_flash_auto
                        FlashMode.ON -> R.drawable.ic_flash_on
                        FlashMode.TORCH -> R.drawable.ic_flashlight_on
                        FlashMode.SCREEN -> R.drawable.ic_flash_screen
                    }
                )
            }
        }

        launch {
            viewModel.uiState.select { it.isFlashButtonEnabled }.collectLatest { isFlashButtonEnabled ->
                flashButton.isEnabled = isFlashButtonEnabled
            }
        }
    }
}
