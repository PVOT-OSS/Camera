/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.camera

import android.animation.ValueAnimator
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import androidx.camera.core.AspectRatio
import androidx.camera.extensions.ExtensionMode
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import kotlin.reflect.safeCast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.prauga.camera.R
import org.prauga.camera.ext.select
import org.prauga.camera.ext.slide
import org.prauga.camera.ext.slideDown
import org.prauga.camera.ext.smoothRotate
import org.prauga.camera.models.CameraMode
import org.prauga.camera.models.CameraState
import org.prauga.camera.models.GridMode
import org.prauga.camera.models.Rotation
import org.prauga.camera.models.TimerMode
import org.prauga.camera.models.VideoDynamicRange
import org.prauga.camera.models.VideoQuality
import org.prauga.camera.viewmodels.CameraViewModel

class SecondaryTopBar(
    root: View,
    private val viewModel: CameraViewModel,
    onSettings: () -> Unit,
) {
    private val resources = root.resources

    private val secondaryTopBarLayout = root.findViewById<HorizontalScrollView>(R.id.secondaryTopBarLayout)
    private val aspectRatioButton = root.findViewById<Button>(R.id.aspectRatioButton)
    private val effectButton = root.findViewById<Button>(R.id.effectButton)
    private val gridButton = root.findViewById<Button>(R.id.gridButton)
    private val micButton = root.findViewById<Button>(R.id.micButton)
    private val settingsButton = root.findViewById<Button>(R.id.settingsButton)
    private val timerButton = root.findViewById<Button>(R.id.timerButton)
    private val videoDynamicRangeButton = root.findViewById<Button>(R.id.videoDynamicRangeButton)
    private val videoFrameRateButton = root.findViewById<Button>(R.id.videoFrameRateButton)
    private val videoQualityButton = root.findViewById<Button>(R.id.videoQualityButton)

    val isMicButtonEnabled: Boolean
        get() = micButton.isEnabled

    init {
        aspectRatioButton.setOnClickListener { viewModel.cyclePhotoAspectRatio() }
        videoQualityButton.setOnClickListener { viewModel.cycleVideoQuality() }
        videoFrameRateButton.setOnClickListener { viewModel.cycleVideoFrameRate() }
        videoDynamicRangeButton.setOnClickListener { viewModel.cycleVideoDynamicRange() }
        effectButton.setOnClickListener { viewModel.cycleExtensionMode() }
        gridButton.setOnClickListener { viewModel.cycleGridMode() }
        timerButton.setOnClickListener { viewModel.toggleTimerMode() }
        micButton.setOnClickListener { viewModel.toggleVideoMicrophoneEnabled() }
        settingsButton.setOnClickListener { onSettings() }
    }

    fun slide() = secondaryTopBarLayout.slide()

    fun slideDown() = secondaryTopBarLayout.slideDown()

    fun CoroutineScope.bind() {
        launch {
            viewModel.uiState.select { it.cameraMode }.collectLatest { cameraMode ->
                // Hide secondary top bar
                secondaryTopBarLayout.isVisible = false

                // Update secondary top bar buttons
                aspectRatioButton.isVisible = cameraMode != CameraMode.VIDEO
                videoQualityButton.isVisible = cameraMode == CameraMode.VIDEO
                videoFrameRateButton.isVisible = cameraMode == CameraMode.VIDEO
                videoDynamicRangeButton.isVisible = cameraMode == CameraMode.VIDEO
                micButton.isVisible = cameraMode == CameraMode.VIDEO
            }
        }

        launch {
            viewModel.uiState.select { it.cameraState }.collectLatest { cameraState ->
                timerButton.isEnabled = cameraState == CameraState.IDLE
                aspectRatioButton.isEnabled = cameraState == CameraState.IDLE
                effectButton.isEnabled = cameraState == CameraState.IDLE
                settingsButton.isEnabled = cameraState == CameraState.IDLE
            }
        }

        launch {
            viewModel.uiState.select { it.screenRotation }.collectLatest { screenRotation ->
                val compensationValue = screenRotation.compensationValue.toFloat()

                ConstraintLayout::class.safeCast(
                    secondaryTopBarLayout.getChildAt(0)
                )?.let { layout ->
                    for (child in layout.children) {
                        Button::class.safeCast(child)?.let {
                            it.smoothRotate(compensationValue)
                            ValueAnimator.ofFloat(
                                (it.layoutParams as ConstraintLayout.LayoutParams).verticalBias,
                                when (screenRotation) {
                                    Rotation.ROTATION_0 -> 0.0f
                                    Rotation.ROTATION_180 -> 1.0f
                                    Rotation.ROTATION_90,
                                    Rotation.ROTATION_270 -> 0.5f
                                }
                            ).apply {
                                addUpdateListener { anim ->
                                    it.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                        verticalBias = anim.animatedValue as Float
                                    }
                                }
                            }.start()
                        }
                    }
                }
            }
        }

        launch {
            viewModel.uiState.select { it.gridMode }.collectLatest { gridMode ->
                // Update secondary bar buttons
                gridButton.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    when (gridMode) {
                        GridMode.OFF -> R.drawable.ic_grid_3x3_off
                        GridMode.ON_3 -> R.drawable.ic_grid_3x3
                        GridMode.ON_4 -> R.drawable.ic_grid_4x4
                        GridMode.ON_GOLDEN_RATIO -> R.drawable.ic_grid_goldenratio
                    },
                    0,
                    0
                )
                gridButton.setText(
                    when (gridMode) {
                        GridMode.OFF -> R.string.grid_off
                        GridMode.ON_3 -> R.string.grid_on_3
                        GridMode.ON_4 -> R.string.grid_on_4
                        GridMode.ON_GOLDEN_RATIO -> R.string.grid_on_goldenratio
                    }
                )
            }
        }

        launch {
            viewModel.uiState.select { it.timerMode }.collectLatest { timerMode ->
                // Update secondary bar buttons
                timerButton.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    when (timerMode) {
                        TimerMode.OFF -> R.drawable.ic_timer_off
                        TimerMode.ON_3S -> R.drawable.ic_timer_3_alt_1
                        TimerMode.ON_10S -> R.drawable.ic_timer_10_alt_1
                    },
                    0,
                    0
                )
                timerButton.setText(
                    when (timerMode) {
                        TimerMode.OFF -> R.string.timer_off
                        TimerMode.ON_3S -> R.string.timer_3
                        TimerMode.ON_10S -> R.string.timer_10
                    }
                )
            }
        }

        launch {
            viewModel.uiState.select { it.photoAspectRatio }.collectLatest { photoAspectRatio ->
                // Update secondary bar buttons
                aspectRatioButton.setText(
                    when (photoAspectRatio) {
                        AspectRatio.RATIO_4_3 -> R.string.aspect_ratio_4_3
                        AspectRatio.RATIO_16_9 -> R.string.aspect_ratio_16_9
                        else -> throw Exception("Unknown aspect ratio $photoAspectRatio")
                    }
                )
            }
        }

        launch {
            viewModel.uiState.select { it.photoEffect }.collectLatest { photoEffect ->
                // Update secondary bar buttons
                effectButton.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    when (photoEffect) {
                        ExtensionMode.NONE -> R.drawable.ic_blur_off
                        ExtensionMode.BOKEH -> R.drawable.ic_effect_bokeh
                        ExtensionMode.HDR -> R.drawable.ic_hdr_on
                        ExtensionMode.NIGHT -> R.drawable.ic_clear_night
                        ExtensionMode.FACE_RETOUCH -> R.drawable.ic_face_retouching_natural
                        ExtensionMode.AUTO -> R.drawable.ic_hdr_auto
                        else -> R.drawable.ic_blur_off
                    },
                    0,
                    0
                )
                effectButton.setText(
                    when (photoEffect) {
                        ExtensionMode.NONE -> R.string.effect_none
                        ExtensionMode.BOKEH -> R.string.effect_bokeh
                        ExtensionMode.HDR -> R.string.effect_hdr
                        ExtensionMode.NIGHT -> R.string.effect_night
                        ExtensionMode.FACE_RETOUCH -> R.string.effect_face_retouch
                        ExtensionMode.AUTO -> R.string.effect_auto
                        else -> R.string.effect_none
                    }
                )
            }
        }

        launch {
            viewModel.uiState.select { it.isPhotoEffectButtonVisible }.collectLatest { isPhotoEffectButtonVisible ->
                effectButton.isVisible = isPhotoEffectButtonVisible
            }
        }

        launch {
            viewModel.uiState.select { it.videoQuality }.collectLatest { videoQuality ->
                // Update secondary bar buttons
                videoQualityButton.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    when (videoQuality) {
                        VideoQuality.SD -> R.drawable.ic_sd
                        VideoQuality.HD -> R.drawable.ic_hd
                        VideoQuality.FHD -> R.drawable.ic_full_hd
                        VideoQuality.QHD -> R.drawable.ic_qhd
                        VideoQuality.UHD -> R.drawable.ic_4k
                    },
                    0,
                    0
                )
                videoQualityButton.setText(
                    when (videoQuality) {
                        VideoQuality.SD -> R.string.video_quality_sd
                        VideoQuality.HD -> R.string.video_quality_hd
                        VideoQuality.FHD -> R.string.video_quality_fhd
                        VideoQuality.QHD -> R.string.video_quality_qhd
                        VideoQuality.UHD -> R.string.video_quality_uhd
                    }
                )
            }
        }

        launch {
            viewModel.uiState.select { it.isVideoQualityButtonEnabled }.collectLatest { isVideoQualityButtonEnabled ->
                videoQualityButton.isEnabled = isVideoQualityButtonEnabled
            }
        }

        launch {
            viewModel.uiState.select { it.videoFrameRate }.collectLatest { videoFrameRate ->
                // Update secondary bar buttons
                videoFrameRateButton.text = videoFrameRate?.let {
                    resources.getString(R.string.video_framerate_value, it.value)
                } ?: resources.getString(R.string.video_framerate_auto)
            }
        }

        launch {
            viewModel.uiState.select { it.isVideoFrameRateButtonEnabled }.collectLatest { isVideoFrameRateButtonEnabled ->
                videoFrameRateButton.isEnabled = isVideoFrameRateButtonEnabled
            }
        }

        launch {
            viewModel.uiState.select { it.videoDynamicRange }.collectLatest { videoDynamicRange ->
                // Update secondary bar buttons
                videoDynamicRangeButton.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    when (videoDynamicRange) {
                        VideoDynamicRange.SDR -> R.drawable.ic_hdr_off
                        VideoDynamicRange.HLG_10_BIT -> R.drawable.ic_hdr_on
                        VideoDynamicRange.HDR10_10_BIT -> R.drawable.ic_hdr_on
                        VideoDynamicRange.HDR10_PLUS_10_BIT -> R.drawable.ic_hdr_on
                        VideoDynamicRange.DOLBY_VISION_10_BIT -> R.drawable.ic_dolby
                        VideoDynamicRange.DOLBY_VISION_8_BIT -> R.drawable.ic_dolby
                    },
                    0,
                    0,
                )
                videoDynamicRangeButton.setText(
                    when (videoDynamicRange) {
                        VideoDynamicRange.SDR -> R.string.video_dynamic_range_sdr
                        VideoDynamicRange.HLG_10_BIT -> R.string.video_dynamic_range_hlg_10_bit
                        VideoDynamicRange.HDR10_10_BIT -> R.string.video_dynamic_range_hdr10_10_bit
                        VideoDynamicRange.HDR10_PLUS_10_BIT -> R.string.video_dynamic_range_hdr10_plus_10_bit
                        VideoDynamicRange.DOLBY_VISION_10_BIT -> R.string.video_dynamic_range_dolby_vision_10_bit
                        VideoDynamicRange.DOLBY_VISION_8_BIT -> R.string.video_dynamic_range_dolby_vision_8_bit
                    }
                )
            }
        }

        launch {
            viewModel.uiState.select { it.isVideoDynamicRangeButtonEnabled }.collectLatest { isVideoDynamicRangeButtonEnabled ->
                videoDynamicRangeButton.isEnabled = isVideoDynamicRangeButtonEnabled
            }
        }

        launch {
            viewModel.uiState.select { it.videoMicMode }.collectLatest { videoMicMode ->
                micButton.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    when (videoMicMode) {
                        true -> R.drawable.ic_mic_on
                        false -> R.drawable.ic_mic_off
                    },
                    0,
                    0
                )
                micButton.setText(
                    when (videoMicMode) {
                        true -> R.string.mic_on
                        false -> R.string.mic_off
                    }
                )
            }
        }

        launch {
            viewModel.uiState.select { it.isVideoMicButtonEnabled }.collectLatest { isVideoMicButtonEnabled ->
                micButton.isEnabled = isVideoMicButtonEnabled
            }
        }
    }
}
