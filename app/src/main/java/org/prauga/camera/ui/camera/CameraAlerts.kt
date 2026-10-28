/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.camera

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.camera.core.CameraState as CameraXCameraState
import androidx.constraintlayout.widget.ConstraintLayout
import coil3.request.error
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.prauga.camera.R
import org.prauga.camera.models.ThermalStatus
import org.prauga.camera.viewmodels.CameraViewModel

class CameraAlerts(
    root: View,
    private val viewModel: CameraViewModel,
    private val activity: Activity,
) {
    private val secondaryBottomBarLayout = root.findViewById<ConstraintLayout>(R.id.secondaryBottomBarLayout)

    private val forceTorchSnackbar by lazy {
        Snackbar.make(
            secondaryBottomBarLayout, R.string.force_torch_help, Snackbar.LENGTH_INDEFINITE
        )
            .setAnchorView(secondaryBottomBarLayout)
            .setAction(android.R.string.ok) {
                viewModel.setForceModeHelpShown(true)
            }
    }

    fun showForceTorchHelp() {
        if (!forceTorchSnackbar.isShownOrQueued) {
            forceTorchSnackbar.show()
        }
    }

    fun CoroutineScope.bind() {
        launch {
            viewModel.cameraXCameraState.collect { cameraXCameraState ->
                cameraXCameraState.error?.let {
                    // Log the error
                    Log.e(
                        LOG_TAG,
                        "Error: code: ${it.code}, type: ${it.type}",
                        it.cause
                    )

                    val showToast = { stringId: Int ->
                        Toast.makeText(
                            activity,
                            stringId,
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    when (it.code) {
                        CameraXCameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                            // No way to fix it without user action, bail out
                            showToast(R.string.error_max_cameras_in_use)
                            activity.finish()
                        }

                        CameraXCameraState.ERROR_CAMERA_IN_USE -> {
                            // No way to fix it without user action, bail out
                            showToast(R.string.error_camera_in_use)
                            activity.finish()
                        }

                        CameraXCameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
                            // Warn the user and don't do anything
                            showToast(R.string.error_other_recoverable_error)
                        }

                        CameraXCameraState.ERROR_STREAM_CONFIG -> {
                            // CameraX use case misconfiguration, no way to recover
                            showToast(R.string.error_stream_config)
                            activity.finish()
                        }

                        CameraXCameraState.ERROR_CAMERA_DISABLED -> {
                            // No way to fix it without user action, bail out
                            showToast(R.string.error_camera_disabled)
                            activity.finish()
                        }

                        CameraXCameraState.ERROR_CAMERA_FATAL_ERROR -> {
                            // No way to fix it without user action, bail out
                            showToast(R.string.error_camera_fatal_error)
                            activity.finish()
                        }

                        CameraXCameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                            // No way to fix it without user action, bail out
                            showToast(R.string.error_do_not_disturb_mode_enabled)
                            activity.finish()
                        }

                        else -> {
                            // We know anything about it, just check if it's recoverable or
                            // critical
                            when (it.type) {
                                CameraXCameraState.ErrorType.RECOVERABLE -> {
                                    showToast(R.string.error_unknown_recoverable)
                                }

                                CameraXCameraState.ErrorType.CRITICAL -> {
                                    showToast(R.string.error_unknown_critical)
                                    activity.finish()
                                }

                                null -> {
                                    // Do nothing
                                }
                            }
                        }
                    }
                }
            }
        }

        launch {
            viewModel.thermalStatus.collectLatest { thermalStatus ->
                val showSnackBar = { stringId: Int ->
                    Snackbar.make(
                        secondaryBottomBarLayout,
                        stringId,
                        Snackbar.LENGTH_INDEFINITE
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
