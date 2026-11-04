/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.compose

import android.net.Uri
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.prauga.pvot.designsystem.components.PvotGlassIconButton
import com.prauga.pvot.designsystem.components.PvotGlassSurface
import com.prauga.pvot.designsystem.components.camera.PvotCameraGlassDefaults
import com.prauga.pvot.designsystem.components.camera.PvotGlassChip
import com.prauga.pvot.designsystem.components.camera.PvotModeSelector
import com.prauga.pvot.designsystem.components.camera.PvotShutterButton
import com.prauga.pvot.designsystem.components.camera.PvotShutterState
import com.prauga.pvot.designsystem.effects.LocalPvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.PvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.pvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.rememberPvotGlassBackdrop
import org.prauga.camera.R
import org.prauga.camera.capture.models.CameraConfiguration
import org.prauga.camera.capture.models.CameraMode
import org.prauga.camera.capture.models.CameraState
import org.prauga.camera.capture.models.ExtensionMode
import org.prauga.camera.capture.models.FlashMode
import org.prauga.camera.capture.models.PhotoAspectRatio
import org.prauga.camera.capture.models.PhotoOutputFormat
import org.prauga.camera.capture.models.VideoQuality
import org.prauga.camera.utils.TimeUtils
import org.prauga.camera.viewmodels.CameraUiState
import org.prauga.camera.viewmodels.CameraViewModel

class CameraScreenActions(
    val onShutter: () -> Unit,
    val onQuickTakeStart: () -> Unit,
    val onQuickTakeLock: () -> Unit,
    val onQuickTakeStop: () -> Unit,
    val onBurstStart: () -> Unit,
    val onBurstStop: () -> Unit,
    val onOpenGallery: () -> Unit,
    val onOpenSettings: () -> Unit,
    val canReview: () -> Boolean,
)

private val TopBarHeight = 142.dp
private val BottomBarHeight = 196.dp

private val ModeOrder = listOf(CameraMode.VIDEO, CameraMode.PHOTO, CameraMode.QR)

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    state: CameraScreenState,
    actions: CameraScreenActions,
    viewfinder: View,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraConfiguration by viewModel.cameraConfiguration.collectAsStateWithLifecycle(null)

    val iconRotation by
        animateFloatAsState(uiState.screenRotation.compensationValue.toFloat(), tween(280))

    LaunchedEffect(uiState.capturedMedia) {
        if (actions.canReview()) {
            state.latestMedia = uiState.capturedMedia.firstOrNull()
        }
    }

    LaunchedEffect(uiState.cameraState) {
        if (uiState.cameraState != CameraState.IDLE) {
            state.panel = CameraScreenState.Panel.NONE
        }
    }

    val backdrop = rememberPvotGlassBackdrop()

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        CameraContent(
            viewModel,
            state,
            actions,
            viewfinder,
            uiState,
            cameraConfiguration,
            iconRotation,
            backdrop,
        )
    }
}

@Composable
private fun CameraContent(
    viewModel: CameraViewModel,
    state: CameraScreenState,
    actions: CameraScreenActions,
    viewfinder: View,
    uiState: CameraUiState,
    cameraConfiguration: CameraConfiguration?,
    iconRotation: Float,
    backdrop: PvotGlassBackdrop,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val aspectRatio =
            when {
                uiState.cameraMode == CameraMode.VIDEO -> 16f / 9f
                uiState.cameraMode == CameraMode.PHOTO &&
                    uiState.photoAspectRatio == PhotoAspectRatio.RATIO_16_9 -> 16f / 9f
                else -> 4f / 3f
            }
        val isVideoFrame = aspectRatio != 4f / 3f

        val referenceHeight = maxWidth * 4 / 3
        val referenceTop =
            min(TopBarHeight, max(0.dp, maxHeight - referenceHeight - BottomBarHeight))
        val referenceBottom = referenceTop + referenceHeight

        val frameHeight = min(maxHeight, maxWidth * aspectRatio)
        val frameTop =
            when (isVideoFrame) {
                true -> max(0.dp, (maxHeight - frameHeight) / 2)
                false -> referenceTop
            }

        AndroidView(
            factory = { viewfinder },
            modifier =
                Modifier.offset(y = frameTop)
                    .fillMaxWidth()
                    .height(frameHeight)
                    .pvotGlassBackdrop(backdrop),
        )

        CompositionLocalProvider(
            LocalIconRotation provides iconRotation,
            LocalPvotGlassBackdrop provides backdrop,
        ) {
            Shades(
                isVideoFrame = isVideoFrame,
                topHeight = referenceTop,
                bottomHeight = maxHeight - referenceBottom,
            )

            ViewfinderOverlay(
                state = state,
                uiState = uiState,
                modifier = Modifier.offset(y = frameTop).fillMaxWidth().height(frameHeight),
                lockTop = referenceTop - frameTop + 9.dp,
            )

            TopBar(
                uiState = uiState,
                cameraConfiguration = cameraConfiguration,
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth().height(max(referenceTop, 64.dp)),
            )

            BottomControls(
                uiState = uiState,
                state = state,
                viewModel = viewModel,
                actions = actions,
                referenceBottom = referenceBottom,
            )

            Panels(
                uiState = uiState,
                cameraConfiguration = cameraConfiguration,
                state = state,
                viewModel = viewModel,
                actions = actions,
            )
        }

        ReviewScreen(
            visible = state.reviewOpen,
            media = uiState.capturedMedia,
            initialUri = state.latestMedia,
            onDismiss = { state.reviewOpen = false },
            onOpenGallery = actions.onOpenGallery,
        )
    }
}

@Composable
private fun Shades(isVideoFrame: Boolean, topHeight: Dp, bottomHeight: Dp) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(topHeight)
                    .background(Color.Black.copy(alpha = if (isVideoFrame) 0.28f else 0.52f))
        )
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(bottomHeight)
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = if (isVideoFrame) 0.3f else 0.58f))
        )
    }
}

@Composable
private fun TopBar(
    uiState: CameraUiState,
    cameraConfiguration: CameraConfiguration?,
    state: CameraScreenState,
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier,
) {
    val recording = uiState.cameraState.isRecordingVideo

    Box(modifier = modifier.padding(start = 21.dp, end = 22.dp, bottom = 22.dp)) {
        Row(
            modifier = Modifier.align(Alignment.BottomStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(visible = !recording, enter = fadeIn(), exit = fadeOut()) {
                PvotGlassChip(
                    text = formatLabel(cameraConfiguration),
                    enabled = uiState.cameraState == CameraState.IDLE,
                    onClick =
                        when (cameraConfiguration) {
                            is CameraConfiguration.Photo -> {
                                { state.panel = CameraScreenState.Panel.PHOTO_FORMAT }
                            }
                            is CameraConfiguration.Video -> {
                                { state.panel = CameraScreenState.Panel.VIDEO_FORMAT }
                            }
                            else -> null
                        },
                )
            }

            val nightSupported =
                uiState.isPhotoEffectButtonVisible &&
                    cameraConfiguration
                        ?.camera
                        ?.supportedExtensionModes
                        ?.contains(ExtensionMode.NIGHT) == true
            if (!recording && nightSupported) {
                val active = uiState.photoEffect == ExtensionMode.NIGHT
                PvotGlassChip(
                    text = null,
                    modifier = Modifier.width(32.dp),
                    contentPadding = PaddingValues(0.dp),
                    onClick = {
                        viewModel.setExtensionMode(
                            if (active) ExtensionMode.NONE else ExtensionMode.NIGHT
                        )
                    },
                    leading = {
                        CameraIcon(
                            icon = R.drawable.ic_cam_moon,
                            size = 18.dp,
                            tint = if (active) CameraYellow else Color.White,
                            contentDescription = stringResource(R.string.effect_night),
                        )
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = recording,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            RecordingTimer(
                durationNanos = uiState.videoRecordingDuration,
                paused = uiState.cameraState == CameraState.RECORDING_VIDEO_PAUSED,
            )
        }

        QuickControls(
            modifier = Modifier.align(Alignment.BottomEnd),
            flashMode = uiState.flashMode,
            isFlashEnabled = uiState.isFlashButtonEnabled,
            onFlash = { viewModel.cycleFlashMode(false) },
            onFlashLongPress = { viewModel.cycleFlashMode(true) },
            onOpenControls = {
                state.panel =
                    when (state.panel) {
                        CameraScreenState.Panel.CONTROLS -> CameraScreenState.Panel.NONE
                        else -> CameraScreenState.Panel.CONTROLS
                    }
            },
        )
    }
}

@Composable
private fun formatLabel(cameraConfiguration: CameraConfiguration?) =
    when (cameraConfiguration) {
        is CameraConfiguration.Photo ->
            photoOutputFormatTitle(cameraConfiguration.photoOutputFormat)
        is CameraConfiguration.Video ->
            videoFormatTitle(
                cameraConfiguration.videoQuality,
                cameraConfiguration.videoFrameRate?.value,
            )
        else -> stringResource(R.string.camera_mode_qr).uppercase()
    }

@Composable
fun photoOutputFormatTitle(photoOutputFormat: PhotoOutputFormat) =
    stringResource(
        when (photoOutputFormat) {
            PhotoOutputFormat.JPEG -> R.string.photo_format_jpeg
            PhotoOutputFormat.JPEG_ULTRA_HDR -> R.string.photo_format_ultra_hdr
            PhotoOutputFormat.RAW -> R.string.photo_format_raw
            PhotoOutputFormat.RAW_JPEG -> R.string.photo_format_raw_jpeg
        }
    )

@Composable
fun videoQualityTitle(videoQuality: VideoQuality) =
    stringResource(
        when (videoQuality) {
            VideoQuality.SD -> R.string.video_format_sd
            VideoQuality.HD -> R.string.video_format_hd
            VideoQuality.FHD -> R.string.video_format_fhd
            VideoQuality.QHD -> R.string.video_format_qhd
            VideoQuality.UHD -> R.string.video_format_uhd
        }
    )

@Composable
fun videoFormatTitle(videoQuality: VideoQuality, frameRate: Int?) =
    when (frameRate) {
        null -> videoQualityTitle(videoQuality)
        else -> "${videoQualityTitle(videoQuality)} · $frameRate"
    }

@Composable
private fun RecordingTimer(durationNanos: Long, paused: Boolean) {
    Box(contentAlignment = Alignment.TopCenter) {
        PvotGlassChip(
            text = TimeUtils.convertNanosToString(durationNanos),
            modifier = Modifier.width(108.dp),
            leading = {
                Box(
                    modifier =
                        Modifier.size(7.dp)
                            .clip(CircleShape)
                            .background(if (paused) Color.White else CameraRecording)
                )
            },
        )

        if (paused) {
            PvotGlassChip(
                text = stringResource(R.string.recording_paused),
                modifier = Modifier.offset(y = 40.dp),
                cornerRadius = 6.dp,
            )
        }
    }
}

@Composable
private fun QuickControls(
    flashMode: FlashMode,
    isFlashEnabled: Boolean,
    onFlash: () -> Unit,
    onFlashLongPress: () -> Unit,
    onOpenControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PvotGlassSurface(
        modifier = modifier.height(32.dp).width(76.dp),
        style = PvotCameraGlassDefaults.Style,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier.size(38.dp, 32.dp)
                        .combinedClickable(
                            enabled = isFlashEnabled,
                            onLongClick = onFlashLongPress,
                            onClick = onFlash,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                CameraIcon(
                    icon = flashIcon(flashMode),
                    size = 18.dp,
                    contentDescription = stringResource(R.string.flash_button_description),
                    tint =
                        when (flashMode) {
                            FlashMode.OFF -> Color.White
                            else -> CameraYellow
                        }.copy(alpha = if (isFlashEnabled) 1f else 0.4f),
                )
            }

            Box(
                modifier = Modifier.size(38.dp, 32.dp).clickable(onClick = onOpenControls),
                contentAlignment = Alignment.Center,
            ) {
                CameraIcon(
                    icon = R.drawable.ic_quick_controls,
                    size = 16.dp,
                    tint = Color.White,
                    contentDescription = stringResource(R.string.camera_controls),
                )
            }
        }
    }
}

fun flashIcon(flashMode: FlashMode) =
    when (flashMode) {
        FlashMode.OFF -> R.drawable.ic_quick_flash_off
        FlashMode.AUTO -> R.drawable.ic_flash_auto
        FlashMode.ON -> R.drawable.ic_cam_flash
        FlashMode.TORCH -> R.drawable.ic_flashlight_on
        FlashMode.SCREEN -> R.drawable.ic_flash_screen
    }

@Composable
private fun BottomControls(
    uiState: CameraUiState,
    state: CameraScreenState,
    viewModel: CameraViewModel,
    actions: CameraScreenActions,
    referenceBottom: Dp,
) {
    val recording = uiState.cameraState.isRecordingVideo
    val isIdle = uiState.cameraState == CameraState.IDLE
    val alpha by
        animateFloatAsState(if (state.panel == CameraScreenState.Panel.NONE) 1f else 0f, tween(220))

    Box(modifier = Modifier.fillMaxSize().alpha(alpha)) {
        uiState.lenses.first?.let { activeCamera ->
            LensRow(
                activeCamera = activeCamera,
                availableCameras = uiState.lenses.second,
                zoomState = uiState.zoomState,
                enabled = uiState.cameraMode != CameraMode.QR,
                referenceBottom = referenceBottom,
                onSelectCamera = viewModel::selectCamera,
                onZoom = viewModel::smoothZoom,
                onPinchZoom = viewModel::pinchToZoom,
                onResetZoom = viewModel::resetZoom,
            )
        }

        CaptureHint(
            state = state,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = referenceBottom - 84.dp),
        )

        if (uiState.cameraMode != CameraMode.QR) {
            val gesturesEnabled =
                !uiState.inSingleCaptureMode &&
                    state.countdown == null &&
                    uiState.cameraMode == CameraMode.PHOTO &&
                    isIdle &&
                    state.quickTake == CameraScreenState.QuickTake.NONE

            PvotShutterButton(
                state =
                    when {
                        uiState.cameraState.isRecordingVideo ||
                            state.quickTake == CameraScreenState.QuickTake.LOCKED ->
                            PvotShutterState.Recording
                        uiState.cameraMode == CameraMode.VIDEO -> PvotShutterState.Video
                        else -> PvotShutterState.Photo
                    },
                onClick = actions.onShutter,
                contentDescription = stringResource(R.string.shutter_button_description),
                modifier = Modifier.align(Alignment.TopCenter).offset(y = referenceBottom + 12.dp),
                enabled = uiState.isShutterButtonEnabled,
                holding = state.quickTake == CameraScreenState.QuickTake.HOLDING,
                onHold = if (gesturesEnabled) actions.onQuickTakeStart else null,
                onHoldLock = actions.onQuickTakeLock,
                onHoldRelease = actions.onQuickTakeStop,
                onSlideStart = if (gesturesEnabled) actions.onBurstStart else null,
                onSlideRelease = actions.onBurstStop,
            )
        }

        if (recording) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .offset(x = (-103).dp, y = referenceBottom + 22.dp)
                        .size(58.dp)
                        .clip(CircleShape)
                        .clickable(onClick = viewModel::onVideoRecordingStateButtonPress),
                contentAlignment = Alignment.Center,
            ) {
                CameraIcon(
                    icon =
                        when (uiState.cameraState) {
                            CameraState.RECORDING_VIDEO_PAUSED -> R.drawable.ic_cam_play
                            else -> R.drawable.ic_cam_pause
                        },
                    size = 24.dp,
                    tint = Color.White,
                    contentDescription =
                        stringResource(R.string.video_recording_state_button_description),
                )
            }
        }

        AnimatedVisibility(
            visible = !recording,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(240)),
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 31.dp, end = 30.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!uiState.inSingleCaptureMode) {
                    LatestCapture(
                        uri = state.latestMedia,
                        key = state.latestMediaKey,
                        enabled = isIdle,
                        modifier = Modifier.align(Alignment.CenterStart),
                        onClick = {
                            when (actions.canReview() && state.latestMedia != null) {
                                true -> state.reviewOpen = true
                                false -> actions.onOpenGallery()
                            }
                        },
                    )

                    val modeTitles = ModeOrder.map { stringResource(modeTitle(it)).uppercase() }
                    PvotModeSelector(
                        modes = modeTitles,
                        selectedIndex = ModeOrder.indexOf(uiState.cameraMode),
                        enabled = isIdle,
                        onSelect = { index ->
                            val mode = ModeOrder[index]
                            when (mode == uiState.cameraMode && mode != CameraMode.QR) {
                                true -> state.panel = CameraScreenState.Panel.CONTROLS
                                false -> viewModel.setCameraMode(mode)
                            }
                        },
                    )
                }

                if (uiState.canFlipCamera) {
                    FlipButton(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        enabled = isIdle,
                        onClick = viewModel::flipCamera,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureHint(state: CameraScreenState, modifier: Modifier = Modifier) {
    val burstCount = state.burstCount
    val text =
        when {
            burstCount != null -> burstCount.toString()
            state.quickTake == CameraScreenState.QuickTake.HOLDING ->
                stringResource(R.string.quick_take_slide_to_lock)
            else -> null
        }

    AnimatedVisibility(
        visible = text != null,
        modifier = modifier,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(160)),
    ) {
        PvotGlassChip(text = text.orEmpty(), cornerRadius = 8.dp)
    }
}

@Composable
private fun LatestCapture(
    uri: Uri?,
    key: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(key) {
        if (key > 0) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, tween(500))
        }
    }

    PvotGlassSurface(
        modifier = modifier.size(43.dp).scale(scale.value).rotate(LocalIconRotation.current),
        style = PvotCameraGlassDefaults.Style,
        onClick = onClick,
        enabled = enabled,
    ) {
        when (uri) {
            null ->
                CameraIcon(
                    icon = R.drawable.ic_image,
                    size = 20.dp,
                    tint = Color.White,
                    contentDescription = stringResource(R.string.gallery_button_description),
                )

            else ->
                AsyncImage(
                    model = mediaRequest(uri),
                    contentDescription = stringResource(R.string.gallery_button_description),
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
        }
    }
}

@Composable
fun mediaRequest(uri: Uri) =
    ImageRequest.Builder(LocalContext.current)
        .data(uri)
        .decoderFactory(VideoFrameDecoder.Factory())
        .crossfade(true)
        .build()

private fun modeTitle(cameraMode: CameraMode) =
    when (cameraMode) {
        CameraMode.PHOTO -> R.string.camera_mode_photo
        CameraMode.VIDEO -> R.string.camera_mode_video
        CameraMode.QR -> R.string.camera_mode_qr
    }

@Composable
private fun FlipButton(modifier: Modifier = Modifier, enabled: Boolean, onClick: () -> Unit) {
    var turns by remember { mutableFloatStateOf(0f) }
    val rotation by animateFloatAsState(turns * 180f, tween(300))

    PvotGlassIconButton(
        onClick = {
            turns += 1f
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        size = 44.dp,
        style = PvotCameraGlassDefaults.Style,
        contentColor = Color.White,
    ) {
        CameraIcon(
            icon = R.drawable.ic_quick_flip,
            size = 20.dp,
            modifier = Modifier.rotate(rotation),
            contentDescription = stringResource(R.string.flip_camera_button_description),
        )
    }
}
