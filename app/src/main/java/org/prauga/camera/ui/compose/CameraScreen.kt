/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.compose

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.prauga.camera.R
import org.prauga.camera.capture.models.Camera
import org.prauga.camera.capture.models.CameraConfiguration
import org.prauga.camera.capture.models.CameraMode
import org.prauga.camera.capture.models.CameraState
import org.prauga.camera.capture.models.FlashMode
import org.prauga.camera.capture.models.PhotoOutputFormat
import org.prauga.camera.capture.models.VideoQuality
import org.prauga.camera.viewmodels.CameraViewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val Yellow = Color(0xFFFFD60A)
private val Recording = Color(0xFFFF3B30)
private val Glass = Color(0xFF444444)
private val Lens = Color(0xFF222222)

private val TopBarHeight = 142.dp

private val ModeOrder = listOf(CameraMode.VIDEO, CameraMode.PHOTO, CameraMode.QR)

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onShutter: () -> Unit,
    onGallery: () -> Unit,
    onOpenControls: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraConfiguration by viewModel.cameraConfiguration.collectAsStateWithLifecycle(null)

    val isIdle = uiState.cameraState == CameraState.IDLE

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewFinderBottom = TopBarHeight + maxWidth * 4 / 3

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TopBarHeight)
                .padding(start = 21.dp, end = 22.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            FormatChip(cameraConfiguration)

            QuickControls(
                flashMode = uiState.flashMode,
                isFlashEnabled = uiState.isFlashButtonEnabled,
                onFlash = { viewModel.cycleFlashMode(false) },
                onFlashLongPress = { viewModel.cycleFlashMode(true) },
                onOpenControls = onOpenControls,
            )
        }

        uiState.lenses.first?.let { activeCamera ->
            LensRow(
                activeCamera = activeCamera,
                availableCameras = uiState.lenses.second,
                zoomRatio = uiState.zoomState?.zoomRatio ?: 1f,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = viewFinderBottom - 48.dp),
                onSelectCamera = viewModel::selectCamera,
                onZoom = viewModel::smoothZoom,
                onResetZoom = viewModel::resetZoom,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(maxHeight - viewFinderBottom)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            ShutterButton(
                cameraMode = uiState.cameraMode,
                cameraState = uiState.cameraState,
                enabled = uiState.isShutterButtonEnabled,
                onClick = onShutter,
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 31.dp, end = 30.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                LatestCapture(
                    uri = uiState.capturedMedia.firstOrNull(),
                    modifier = Modifier.align(Alignment.CenterStart),
                    onClick = onGallery,
                )

                if (!uiState.inSingleCaptureMode) {
                    ModeSelector(
                        cameraMode = uiState.cameraMode,
                        enabled = isIdle,
                        onModeSelected = viewModel::setCameraMode,
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
private fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    alpha: Float = 0.65f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f))
            .clip(shape)
            .background(Glass.copy(alpha = alpha))
            .border(BorderStroke(0.6.dp, Color.White.copy(alpha = 0.24f)), shape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun FormatChip(cameraConfiguration: CameraConfiguration?) {
    val photoOutputFormat = (cameraConfiguration as? CameraConfiguration.Photo)?.photoOutputFormat

    GlassSurface(
        modifier = Modifier.size(74.dp, 32.dp),
        shape = RoundedCornerShape(17.dp),
    ) {
        Text(
            text = when (photoOutputFormat) {
                PhotoOutputFormat.JPEG -> "JPEG"
                PhotoOutputFormat.JPEG_ULTRA_HDR -> "UHDR"
                PhotoOutputFormat.RAW -> "RAW"
                null -> when (cameraConfiguration) {
                    is CameraConfiguration.Video -> stringResource(
                        videoQualityTitle(cameraConfiguration.videoQuality)
                    )
                    else -> "QR"
                }
                else -> "RAW+J"
            },
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun videoQualityTitle(videoQuality: VideoQuality) = when (videoQuality) {
    VideoQuality.SD -> R.string.video_quality_sd
    VideoQuality.HD -> R.string.video_quality_hd
    VideoQuality.FHD -> R.string.video_quality_fhd
    VideoQuality.QHD -> R.string.video_quality_qhd
    VideoQuality.UHD -> R.string.video_quality_uhd
}

@Composable
private fun QuickControls(
    flashMode: FlashMode,
    isFlashEnabled: Boolean,
    onFlash: () -> Unit,
    onFlashLongPress: () -> Unit,
    onOpenControls: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.size(76.dp, 32.dp),
        shape = RoundedCornerShape(17.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        enabled = isFlashEnabled,
                        onLongClick = onFlashLongPress,
                        onClick = onFlash,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(flashIcon(flashMode)),
                    contentDescription = stringResource(R.string.flash_button_description),
                    modifier = Modifier.size(18.dp),
                    tint = when (flashMode) {
                        FlashMode.OFF -> Color.White
                        else -> Yellow
                    }.copy(alpha = if (isFlashEnabled) 1f else 0.4f),
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenControls),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_quick_controls),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

@DrawableRes
private fun flashIcon(flashMode: FlashMode) = when (flashMode) {
    FlashMode.OFF -> R.drawable.ic_quick_flash_off
    FlashMode.AUTO -> R.drawable.ic_flash_auto
    FlashMode.ON -> R.drawable.ic_flash_on
    FlashMode.TORCH -> R.drawable.ic_flashlight_on
    FlashMode.SCREEN -> R.drawable.ic_flash_screen
}

@Composable
private fun LensRow(
    activeCamera: Camera,
    availableCameras: List<Camera>,
    zoomRatio: Float,
    modifier: Modifier = Modifier,
    onSelectCamera: (Camera) -> Unit,
    onZoom: (Float) -> Unit,
    onResetZoom: () -> Unit,
) {
    val usesLogicalZoomRatio = activeCamera.logicalZoomRatios.size > 1

    val lenses = when (usesLogicalZoomRatio) {
        true -> activeCamera.logicalZoomRatios.map { (approximate, exact) ->
            Triple(approximate, exact, null as Camera?)
        }

        false -> availableCameras.sortedBy { it.intrinsicZoomRatio }.map {
            Triple(it.intrinsicZoomRatio, it.intrinsicZoomRatio, it)
        }
    }

    if (lenses.size < 2) {
        return
    }

    val selectedIndex = when (usesLogicalZoomRatio) {
        true -> lenses.indexOfLast { zoomRatio >= it.second }.coerceAtLeast(0)
        false -> lenses.indexOfFirst { it.third == activeCamera }.coerceAtLeast(0)
    }

    Row(
        modifier = modifier.height(34.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lenses.forEachIndexed { index, (approximate, exact, camera) ->
            val selected = index == selectedIndex
            val size by animateDpAsState(if (selected) 34.dp else 30.dp, tween(240))

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Lens.copy(alpha = if (selected) 0.65f else 0.22f))
                    .clickable {
                        when {
                            selected -> onResetZoom()
                            camera != null -> onSelectCamera(camera)
                            else -> onZoom(exact)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (selected) {
                        true -> "${formatZoomRatio(if (usesLogicalZoomRatio) zoomRatio else approximate)}x"
                        false -> formatZoomRatio(approximate)
                    },
                    color = if (selected) Yellow else Color.White,
                    fontSize = if (selected) 14.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private val DECIMAL_FORMAT_SYMBOLS = DecimalFormatSymbols(Locale.US)
private val ZOOM_RATIO_FORMATTER = DecimalFormat("0.#", DECIMAL_FORMAT_SYMBOLS)
private val ZOOM_RATIO_FORMATTER_SUB_ONE = DecimalFormat(".#", DECIMAL_FORMAT_SYMBOLS)

private fun formatZoomRatio(zoomRatio: Float): String = when (zoomRatio < 1f) {
    true -> ZOOM_RATIO_FORMATTER_SUB_ONE.format(zoomRatio)
    false -> ZOOM_RATIO_FORMATTER.format(zoomRatio)
}

@Composable
private fun ShutterButton(
    cameraMode: CameraMode,
    cameraState: CameraState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, tween(85))

    val recording = cameraState == CameraState.RECORDING_VIDEO ||
            cameraState == CameraState.RECORDING_VIDEO_PAUSED
    val coreColor by animateColorAsState(
        when (cameraMode) {
            CameraMode.VIDEO -> Recording
            else -> Color.White
        },
        tween(240),
    )
    val coreSize by animateDpAsState(if (recording) 30.dp else 64.dp, tween(240))
    val coreRadius by animateDpAsState(if (recording) 8.dp else 32.dp, tween(240))

    Box(
        modifier = Modifier
            .size(78.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color(0xFF999999).copy(alpha = 0.25f))
            .border(BorderStroke(1.2.dp, Color.White.copy(alpha = 0.48f)), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(coreSize)
                .clip(RoundedCornerShape(coreRadius))
                .background(coreColor.copy(alpha = if (enabled) 1f else 0.5f)),
        )
    }
}

@Composable
private fun LatestCapture(
    uri: Uri?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(43.dp)
            .clip(CircleShape)
            .background(Glass.copy(alpha = 0.55f))
            .border(BorderStroke(0.6.dp, Color.White.copy(alpha = 0.24f)), CircleShape)
            .clickable(onClick = onClick),
    ) {
        uri?.let {
            AsyncImage(
                model = it,
                contentDescription = stringResource(R.string.gallery_button_description),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun ModeSelector(
    cameraMode: CameraMode,
    enabled: Boolean,
    onModeSelected: (CameraMode) -> Unit,
) {
    Row(
        modifier = Modifier.height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeOrder.forEach { mode ->
            val selected = mode == cameraMode
            val background by animateColorAsState(
                Glass.copy(alpha = if (selected) 0.62f else 0f),
                tween(260),
            )
            val textColor by animateColorAsState(
                if (selected) Yellow else Color.White,
                tween(260),
            )

            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(background)
                    .clickable(enabled = enabled && !selected) { onModeSelected(mode) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(modeTitle(mode)).uppercase(),
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun modeTitle(cameraMode: CameraMode) = when (cameraMode) {
    CameraMode.PHOTO -> R.string.camera_mode_photo
    CameraMode.VIDEO -> R.string.camera_mode_video
    CameraMode.QR -> R.string.camera_mode_qr
}

@Composable
private fun FlipButton(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var turns by remember { mutableFloatStateOf(0f) }
    val rotation by animateFloatAsState(turns * 180f, tween(300))

    GlassSurface(
        modifier = modifier
            .size(44.dp)
            .clickable(enabled = enabled) {
                turns += 1f
                onClick()
            },
        shape = CircleShape,
        alpha = 0.55f,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_quick_flip),
            contentDescription = stringResource(R.string.flip_camera_button_description),
            modifier = Modifier
                .size(20.dp)
                .rotate(rotation),
            tint = Color.White,
        )
    }
}
