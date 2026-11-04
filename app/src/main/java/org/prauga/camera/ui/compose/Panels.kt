/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.compose

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prauga.pvot.designsystem.components.camera.PvotAdjustmentSheet
import com.prauga.pvot.designsystem.components.camera.PvotChoiceRow
import com.prauga.pvot.designsystem.components.camera.PvotControlPanel
import com.prauga.pvot.designsystem.components.camera.PvotControlTile
import com.prauga.pvot.designsystem.components.camera.PvotRuler
import java.util.Locale
import kotlin.math.roundToInt
import org.prauga.camera.R
import org.prauga.camera.capture.models.CameraConfiguration
import org.prauga.camera.capture.models.CameraMode
import org.prauga.camera.capture.models.CameraState
import org.prauga.camera.capture.models.ExtensionMode
import org.prauga.camera.capture.models.FlashMode
import org.prauga.camera.capture.models.PhotoAspectRatio
import org.prauga.camera.capture.models.VideoDynamicRange
import org.prauga.camera.models.GridMode
import org.prauga.camera.models.TimerMode
import org.prauga.camera.viewmodels.CameraUiState
import org.prauga.camera.viewmodels.CameraViewModel

private data class Tile(
    @DrawableRes val icon: Int,
    val label: String,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val badge: String? = null,
    val onClick: () -> Unit,
)

@Composable
fun Panels(
    uiState: CameraUiState,
    cameraConfiguration: CameraConfiguration?,
    state: CameraScreenState,
    viewModel: CameraViewModel,
    actions: CameraScreenActions,
) {
    val panel = state.panel
    val dismiss = { state.panel = CameraScreenState.Panel.NONE }

    BackHandler(enabled = panel != CameraScreenState.Panel.NONE, onBack = dismiss)

    Box(modifier = Modifier.fillMaxSize()) {
        if (panel != CameraScreenState.Panel.NONE) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = dismiss,
                        )
            )
        }

        val panelModifier =
            Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 11.dp, end = 11.dp, bottom = 28.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()

        AnimatedVisibility(
            visible = panel == CameraScreenState.Panel.CONTROLS,
            modifier = panelModifier,
            enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.92f),
            exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.92f),
        ) {
            ControlsPanel(tiles = controlTiles(uiState, state, viewModel, actions))
        }

        AnimatedVisibility(
            visible =
                panel != CameraScreenState.Panel.NONE && panel != CameraScreenState.Panel.CONTROLS,
            modifier = panelModifier,
            enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.92f),
            exit = fadeOut(tween(220)),
        ) {
            when (panel) {
                CameraScreenState.Panel.TIMER -> TimerSheet(uiState, viewModel, dismiss)
                CameraScreenState.Panel.EXPOSURE ->
                    ExposureSheet(uiState, cameraConfiguration, viewModel, dismiss)
                CameraScreenState.Panel.EFFECTS ->
                    EffectsSheet(uiState, cameraConfiguration, viewModel, dismiss)
                CameraScreenState.Panel.PHOTO_FORMAT ->
                    PhotoFormatSheet(cameraConfiguration, viewModel, dismiss)
                CameraScreenState.Panel.VIDEO_FORMAT ->
                    VideoFormatSheet(cameraConfiguration, viewModel, dismiss)
                else -> {}
            }
        }
    }
}

@Composable
private fun controlTiles(
    uiState: CameraUiState,
    state: CameraScreenState,
    viewModel: CameraViewModel,
    actions: CameraScreenActions,
): List<Tile> {
    val isIdle = uiState.cameraState == CameraState.IDLE
    val open = { panel: CameraScreenState.Panel -> state.panel = panel }
    val exposureLevel = uiState.exposureCompensationRangeToLevel?.second ?: 0.5f

    return buildList {
        add(
            Tile(
                icon = flashIcon(uiState.flashMode),
                label = stringResource(R.string.control_flash),
                active = uiState.flashMode != FlashMode.OFF,
                enabled = uiState.isFlashButtonEnabled,
                onClick = { viewModel.cycleFlashMode(false) },
            )
        )

        if (uiState.cameraMode == CameraMode.VIDEO) {
            add(
                Tile(
                    icon =
                        if (uiState.videoMicMode) R.drawable.ic_cam_volume
                        else R.drawable.ic_mic_off,
                    label =
                        stringResource(
                            if (uiState.videoMicMode) R.string.mic_on else R.string.mic_off
                        ),
                    active = uiState.videoMicMode,
                    enabled = uiState.isVideoMicButtonEnabled,
                    onClick = viewModel::toggleVideoMicrophoneEnabled,
                )
            )
        }

        add(
            Tile(
                icon = R.drawable.ic_cam_timer,
                label = stringResource(R.string.control_timer),
                active = uiState.timerMode != TimerMode.OFF,
                enabled = isIdle,
                badge = uiState.timerMode.takeIf { it != TimerMode.OFF }?.let { "${it.seconds}s" },
                onClick = { open(CameraScreenState.Panel.TIMER) },
            )
        )

        add(
            Tile(
                icon = R.drawable.ic_cam_exposure,
                label = stringResource(R.string.control_exposure),
                active = exposureLevel != 0.5f,
                enabled = uiState.exposureCompensationRangeToLevel != null,
                onClick = { open(CameraScreenState.Panel.EXPOSURE) },
            )
        )

        if (uiState.cameraMode == CameraMode.PHOTO && uiState.isPhotoEffectButtonVisible) {
            add(
                Tile(
                    icon = R.drawable.ic_cam_sliders,
                    label = stringResource(R.string.control_effects),
                    active = uiState.photoEffect != ExtensionMode.NONE,
                    enabled = isIdle,
                    onClick = { open(CameraScreenState.Panel.EFFECTS) },
                )
            )
        }

        if (uiState.cameraMode == CameraMode.PHOTO) {
            add(
                Tile(
                    icon = R.drawable.ic_cam_aspect,
                    label = stringResource(R.string.control_aspect),
                    enabled = isIdle,
                    badge =
                        stringResource(
                            when (uiState.photoAspectRatio) {
                                PhotoAspectRatio.RATIO_4_3 -> R.string.aspect_ratio_4_3
                                PhotoAspectRatio.RATIO_16_9 -> R.string.aspect_ratio_16_9
                            }
                        ),
                    onClick = {
                        viewModel.setPhotoAspectRatio(
                            when (uiState.photoAspectRatio) {
                                PhotoAspectRatio.RATIO_4_3 -> PhotoAspectRatio.RATIO_16_9
                                PhotoAspectRatio.RATIO_16_9 -> PhotoAspectRatio.RATIO_4_3
                            }
                        )
                    },
                )
            )
        }

        if (uiState.cameraMode == CameraMode.VIDEO && uiState.isVideoDynamicRangeButtonEnabled) {
            add(
                Tile(
                    icon =
                        when (uiState.videoDynamicRange) {
                            VideoDynamicRange.SDR -> R.drawable.ic_hdr_off
                            else -> R.drawable.ic_hdr_on
                        },
                    label = stringResource(R.string.control_hdr),
                    active = uiState.videoDynamicRange != VideoDynamicRange.SDR,
                    enabled = isIdle,
                    onClick = viewModel::cycleVideoDynamicRange,
                )
            )
        }

        add(
            Tile(
                icon =
                    when (uiState.gridMode) {
                        GridMode.OFF -> R.drawable.ic_grid_3x3_off
                        GridMode.ON_3 -> R.drawable.ic_grid_3x3
                        GridMode.ON_4 -> R.drawable.ic_grid_4x4
                        GridMode.ON_GOLDEN_RATIO -> R.drawable.ic_grid_goldenratio
                    },
                label =
                    stringResource(
                        when (uiState.gridMode) {
                            GridMode.OFF -> R.string.grid_off
                            GridMode.ON_3 -> R.string.grid_on_3
                            GridMode.ON_4 -> R.string.grid_on_4
                            GridMode.ON_GOLDEN_RATIO -> R.string.grid_on_goldenratio
                        }
                    ),
                active = uiState.gridMode != GridMode.OFF,
                onClick = viewModel::cycleGridMode,
            )
        )

        add(
            Tile(
                icon = R.drawable.ic_settings,
                label = stringResource(R.string.control_settings),
                enabled = isIdle,
                onClick = actions.onOpenSettings,
            )
        )
    }
}

@Composable
private fun ControlsPanel(tiles: List<Tile>) {
    PvotControlPanel(modifier = Modifier.fillMaxWidth()) {
        tiles.forEach { tile ->
            PvotControlTile(
                label = tile.label.uppercase(),
                onClick = tile.onClick,
                active = tile.active,
                enabled = tile.enabled,
                badge = tile.badge,
            ) {
                CameraIcon(icon = tile.icon, size = 30.dp)
            }
        }
    }
}

@Composable
private fun Sheet(
    title: String,
    @DrawableRes icon: Int? = null,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    PvotAdjustmentSheet(
        title = title.uppercase(),
        onClose = onClose,
        closeContentDescription = stringResource(R.string.close),
        modifier = Modifier.fillMaxWidth(),
        icon = icon?.let { { CameraIcon(icon = it, size = 23.dp) } },
        closeIcon = { CameraIcon(icon = R.drawable.ic_cam_close, size = 20.dp) },
        content = content,
    )
}

@Composable
private fun <T> OptionRow(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit) {
    PvotChoiceRow(
        options = options.map { it.second },
        selectedIndex = options.indexOfFirst { it.first == selected },
        onSelect = { onSelect(options[it].first) },
    )
}

@Composable
private fun TimerSheet(uiState: CameraUiState, viewModel: CameraViewModel, onClose: () -> Unit) {
    Sheet(title = stringResource(R.string.control_timer), onClose = onClose) {
        OptionRow(
            options =
                TimerMode.entries.map {
                    it to
                        when (it) {
                            TimerMode.OFF -> stringResource(R.string.option_off)
                            else -> "${it.seconds}s"
                        }
                },
            selected = uiState.timerMode,
            onSelect = {
                viewModel.setTimerMode(it)
                onClose()
            },
        )
    }
}

@Composable
private fun ExposureSheet(
    uiState: CameraUiState,
    cameraConfiguration: CameraConfiguration?,
    viewModel: CameraViewModel,
    onClose: () -> Unit,
) {
    val (range, level) = uiState.exposureCompensationRangeToLevel ?: return
    val step = cameraConfiguration?.camera?.exposureCompensationStep ?: 1f
    val steps = range.endInclusive - range.start

    val index = (range.start + level * steps).roundToInt()

    Sheet(
        title = stringResource(R.string.control_exposure),
        icon = R.drawable.ic_cam_exposure,
        onClose = onClose,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatEv(index * step),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 23.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            PvotRuler(
                value = level,
                onValueChange = viewModel::setExposureCompensationLevel,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp),
                steps = steps,
                startLabel = formatEv(range.start * step),
                endLabel = formatEv(range.endInclusive * step),
            )
        }
    }
}

private fun formatEv(ev: Float) =
    when {
        ev > 0.05f -> "+%.1f".format(Locale.US, ev)
        ev < -0.05f -> "−%.1f".format(Locale.US, -ev)
        else -> "0.0"
    }

@Composable
private fun EffectsSheet(
    uiState: CameraUiState,
    cameraConfiguration: CameraConfiguration?,
    viewModel: CameraViewModel,
    onClose: () -> Unit,
) {
    val modes = cameraConfiguration?.camera?.supportedExtensionModes?.sorted() ?: return

    Sheet(title = stringResource(R.string.control_effects), onClose = onClose) {
        OptionRow(
            options = modes.map { it to stringResource(effectTitle(it)) },
            selected = uiState.photoEffect,
            onSelect = {
                viewModel.setExtensionMode(it)
                onClose()
            },
        )
    }
}

private fun effectTitle(extensionMode: ExtensionMode) =
    when (extensionMode) {
        ExtensionMode.NONE -> R.string.option_off
        ExtensionMode.BOKEH -> R.string.effect_bokeh
        ExtensionMode.HDR -> R.string.effect_hdr
        ExtensionMode.NIGHT -> R.string.effect_night
        ExtensionMode.FACE_RETOUCH -> R.string.effect_face_retouch
        ExtensionMode.AUTO -> R.string.effect_auto
    }

@Composable
private fun PhotoFormatSheet(
    cameraConfiguration: CameraConfiguration?,
    viewModel: CameraViewModel,
    onClose: () -> Unit,
) {
    val photo = cameraConfiguration as? CameraConfiguration.Photo ?: return

    Sheet(title = stringResource(R.string.photo_format_title), onClose = onClose) {
        OptionRow(
            options =
                photo.camera.supportedPhotoOutputFormats.map { it to photoOutputFormatTitle(it) },
            selected = photo.photoOutputFormat,
            onSelect = {
                viewModel.setPhotoOutputFormat(it)
                onClose()
            },
        )
    }
}

@Composable
private fun VideoFormatSheet(
    cameraConfiguration: CameraConfiguration?,
    viewModel: CameraViewModel,
    onClose: () -> Unit,
) {
    val video = cameraConfiguration as? CameraConfiguration.Video ?: return
    val qualities = video.camera.supportedVideoQualities

    Sheet(title = stringResource(R.string.video_format_title), onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionRow(
                options = qualities.keys.sorted().map { it to videoQualityTitle(it) },
                selected = video.videoQuality,
                onSelect = { viewModel.setVideoFormat(it, video.videoFrameRate) },
            )

            val frameRates = qualities[video.videoQuality]?.supportedFrameRates?.sorted().orEmpty()
            if (frameRates.size > 1) {
                OptionRow(
                    options = frameRates.map { it to "${it.value}" },
                    selected = video.videoFrameRate,
                    onSelect = { viewModel.setVideoFormat(video.videoQuality, it) },
                )
            }
        }
    }
}
