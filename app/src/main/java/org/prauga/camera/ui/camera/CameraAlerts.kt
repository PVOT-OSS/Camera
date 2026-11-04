/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.camera

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.prauga.camera.R
import org.prauga.camera.capture.CameraError
import org.prauga.camera.ext.select
import org.prauga.camera.models.ThermalStatus
import org.prauga.camera.viewmodels.CameraViewModel

class CameraAlerts(
    root: View,
    private val viewModel: CameraViewModel,
    private val activity: Activity,
) {
    private val secondaryBottomBarLayout =
        root.findViewById<ConstraintLayout>(R.id.secondaryBottomBarLayout)

    private val forceTorchSnackbar by lazy {
        Snackbar.make(
                secondaryBottomBarLayout,
                R.string.force_torch_help,
                Snackbar.LENGTH_INDEFINITE,
            )
            .setAnchorView(secondaryBottomBarLayout)
            .setAction(android.R.string.ok) { viewModel.setForceModeHelpShown(true) }
    }

    fun showForceTorchHelp() {
        if (!forceTorchSnackbar.isShownOrQueued) {
            forceTorchSnackbar.show()
        }
    }

    fun CoroutineScope.bind() {
        launch {
            viewModel.cameraError.collect { cameraError ->
                cameraError?.let {
                    // Log the error
                    Log.e(LOG_TAG, "Error: code: ${it.code}, type: ${it.type}", it.cause)

                    val showToast = { stringId: Int ->
                        Toast.makeText(activity, stringId, Toast.LENGTH_SHORT).show()
                    }

                    when (it.code) {
                        CameraError.Code.MAX_CAMERAS_IN_USE -> {
                            // No way to fix it without user action, bail out
                            showToast(R.string.error_max_cameras_in_use)
                            activity.finish()
                        }

                        CameraError.Code.CAMERA_IN_USE -> {
                            // No way to fix it without user action, bail out
                            showToast(R.string.error_camera_in_use)
                            activity.finish()
                        }

                        CameraError.Code.OTHER_RECOVERABLE_ERROR -> {
                            // Warn the user and don't do anything
                            showToast(R.string.error_other_recoverable_error)
                        }

                        CameraError.Code.STREAM_CONFIG -> {
                            // CameraX use case misconfiguration, no way to recover
                            showToast(R.string.error_stream_config)
                            activity.finish()
                        }

                        CameraError.Code.CAMERA_DISABLED -> {
                            // No way to fix it without user action, bail out
                            showToast(R.string.error_camera_disabled)
                            activity.finish()
                        }

                        CameraError.Code.CAMERA_FATAL_ERROR -> {
                            // No way to fix it without user action, bail out
                            showToast(R.string.error_camera_fatal_error)
                            activity.finish()
                        }

                        CameraError.Code.DO_NOT_DISTURB_MODE_ENABLED -> {
                            // No way to fix it without user action, bail out
                            showToast(R.string.error_do_not_disturb_mode_enabled)
                            activity.finish()
                        }

                        else -> {
                            // We know anything about it, just check if it's recoverable or
                            // critical
                            when (it.type) {
                                CameraError.Type.RECOVERABLE -> {
                                    showToast(R.string.error_unknown_recoverable)
                                }

                                CameraError.Type.CRITICAL -> {
                                    showToast(R.string.error_unknown_critical)
                                    activity.finish()
                                }
                            }
                        }
                    }
                }
            }
        }

        launch {
            viewModel.uiState
                .select { it.thermalStatus }
                .collectLatest { thermalStatus ->
                    val showSnackBar = { stringId: Int ->
                        Snackbar.make(
                                secondaryBottomBarLayout,
                                stringId,
                                Snackbar.LENGTH_INDEFINITE,
                            )
                            .setAnchorView(secondaryBottomBarLayout)
                            .setAction(android.R.string.ok) {
                                // Do nothing
                            }
                            .show()
                    }

                    when (thermalStatus) {
                        ThermalStatus.NONE,
                        ThermalStatus.LIGHT -> Unit

                        ThermalStatus.MODERATE -> {
                            showSnackBar(R.string.thermal_status_moderate)
                        }

                        ThermalStatus.SEVERE -> {
                            showSnackBar(R.string.thermal_status_severe)
                        }

                        ThermalStatus.CRITICAL -> {
                            showSnackBar(R.string.thermal_status_critical)
                        }

                        ThermalStatus.EMERGENCY -> {
                            showSnackBar(R.string.thermal_status_emergency)
                            activity.finish()
                        }

                        ThermalStatus.SHUTDOWN -> {
                            showSnackBar(R.string.thermal_status_shutdown)
                            activity.finish()
                        }
                    }
                }
        }
    }

    companion object {
        private val LOG_TAG = CameraAlerts::class.simpleName!!
    }
}
