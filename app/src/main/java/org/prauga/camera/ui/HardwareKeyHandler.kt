/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui

import android.util.Log
import android.view.KeyEvent
import org.prauga.camera.capture.models.CameraMode
import org.prauga.camera.models.GestureAction
import org.prauga.camera.models.HardwareKey
import org.prauga.camera.viewmodels.CameraViewModel

class HardwareKeyHandler(
    private val viewModel: CameraViewModel,
    private val onShutter: () -> Unit,
    private val onFocus: () -> Unit,
    private val isMicButtonEnabled: () -> Boolean,
) {
    enum class Result {
        HANDLED,
        DEFAULT,
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent?) =
        HardwareKey.match(keyCode)?.let { (hardwareKey, tempIncrease) ->
            val increase =
                when (viewModel.getHardwareKeyInvert(hardwareKey)) {
                    true -> !tempIncrease
                    false -> tempIncrease
                }

            val gestureAction = viewModel.getHardwareKeyAction(hardwareKey)

            if (gestureAction.isTwoWayAction && !hardwareKey.isTwoWayKey) {
                Log.wtf(
                    LOG_TAG,
                    "${gestureAction.name} requires two-way key but ${hardwareKey.name} is not",
                )
                return@let Result.HANDLED
            }

            when (gestureAction) {
                GestureAction.SHUTTER -> {
                    if (
                        viewModel.uiState.value.cameraMode == CameraMode.VIDEO &&
                            viewModel.uiState.value.isShutterButtonEnabled &&
                            event?.repeatCount == 0
                    ) {
                        onShutter()
                    }
                    Result.HANDLED
                }

                GestureAction.FOCUS -> {
                    if (event?.repeatCount == 0) {
                        onFocus()
                    }
                    Result.HANDLED
                }

                GestureAction.MIC_MUTE -> {
                    if (
                        viewModel.uiState.value.cameraMode == CameraMode.VIDEO &&
                            isMicButtonEnabled() &&
                            event?.repeatCount == 0
                    ) {
                        viewModel.toggleVideoMicrophoneEnabled()
                    }
                    Result.HANDLED
                }

                GestureAction.ZOOM -> {
                    when (increase) {
                        true -> viewModel.zoomIn()
                        false -> viewModel.zoomOut()
                    }
                    Result.HANDLED
                }

                GestureAction.DEFAULT -> default(hardwareKey)

                GestureAction.NOTHING -> Result.HANDLED
            }
        }

    fun onKeyUp(keyCode: Int) =
        HardwareKey.match(keyCode)?.let { (hardwareKey, _) ->
            val gestureAction = viewModel.getHardwareKeyAction(hardwareKey)

            if (gestureAction.isTwoWayAction && !hardwareKey.isTwoWayKey) {
                Log.wtf(
                    LOG_TAG,
                    "${gestureAction.name} requires two-way key but ${hardwareKey.name} is not",
                )
                return@let Result.HANDLED
            }

            when (gestureAction) {
                GestureAction.SHUTTER -> {
                    if (
                        viewModel.uiState.value.cameraMode != CameraMode.QR &&
                            viewModel.uiState.value.isShutterButtonEnabled
                    ) {
                        onShutter()
                    }
                    Result.HANDLED
                }

                GestureAction.FOCUS,
                GestureAction.MIC_MUTE,
                GestureAction.ZOOM,
                GestureAction.NOTHING -> Result.HANDLED

                GestureAction.DEFAULT -> default(hardwareKey)
            }
        }

    private fun default(hardwareKey: HardwareKey) =
        when (hardwareKey.supportsDefault) {
            true -> Result.DEFAULT
            false -> {
                Log.wtf(
                    LOG_TAG,
                    "Got GestureAction.DEFAULT for ${hardwareKey.name} which doesn't support it",
                )
                Result.HANDLED
            }
        }

    companion object {
        private val LOG_TAG = HardwareKeyHandler::class.simpleName!!
    }
}
