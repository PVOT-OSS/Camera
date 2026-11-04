/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.camera.PvotGlassChip
import com.prauga.pvot.designsystem.components.camera.PvotZoomDial
import com.prauga.pvot.designsystem.components.camera.PvotZoomSelector
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlinx.coroutines.delay
import org.prauga.camera.capture.ZoomInfo
import org.prauga.camera.capture.models.Camera

private const val DIAL_HIDE_MS = 1200L

private val CropZoomRatios = listOf(28f / 24f, 35f / 24f)

private data class Lens(val approximate: Float, val exact: Float, val camera: Camera?)

@Composable
fun LensRow(
    activeCamera: Camera,
    availableCameras: List<Camera>,
    zoomState: ZoomInfo?,
    enabled: Boolean,
    referenceBottom: Dp,
    onSelectCamera: (Camera) -> Unit,
    onZoom: (Float) -> Unit,
    onPinchZoom: (Float) -> Unit,
    onResetZoom: () -> Unit,
) {
    val usesLogicalZoomRatio = activeCamera.logicalZoomRatios.size > 1
    val lenses =
        when (usesLogicalZoomRatio) {
            true ->
                activeCamera.logicalZoomRatios.map { (approximate, exact) ->
                    Lens(approximate, exact, null)
                }

            false ->
                availableCameras
                    .sortedBy { it.intrinsicZoomRatio }
                    .map { Lens(it.intrinsicZoomRatio, it.intrinsicZoomRatio, it) }
        }

    val zoomRatio = zoomState?.zoomRatio ?: 1f
    val canZoom = zoomState != null && zoomState.minZoomRatio < zoomState.maxZoomRatio

    if (!enabled || (lenses.isEmpty() && !canZoom)) {
        return
    }

    val stops = lenses.ifEmpty { listOf(Lens(1f, 1f, null)) }
    val selectedIndex =
        when (usesLogicalZoomRatio) {
            true -> stops.indexOfLast { zoomRatio >= it.exact * 0.99f }.coerceAtLeast(0)
            false -> stops.indexOfFirst { it.camera == activeCamera }.coerceAtLeast(0)
        }
    val selected = stops[selectedIndex]
    val beyondLastLens =
        usesLogicalZoomRatio &&
            selectedIndex == stops.lastIndex &&
            zoomRatio > selected.exact * 1.05f

    var dialVisible by remember { mutableStateOf(false) }
    var dialInteraction by remember { mutableIntStateOf(0) }
    LaunchedEffect(dialVisible, dialInteraction) {
        if (dialVisible && dialInteraction < 0) {
            delay(DIAL_HIDE_MS)
            dialVisible = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (zoomState != null) {
            AnimatedVisibility(
                visible = dialVisible,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = referenceBottom - 131.dp),
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(220)),
            ) {
                PvotZoomDial(
                    value = zoomState.zoomRatio,
                    range = zoomState.minZoomRatio..zoomState.maxZoomRatio,
                    label = "${formatZoomRatio(zoomState.zoomRatio)}×",
                    markLabel = ::formatZoomRatio,
                )
            }
        }

        AnimatedVisibility(
            visible = !dialVisible && beyondLastLens,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = referenceBottom - 84.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PvotGlassChip(text = "${formatZoomRatio(zoomRatio)}×", cornerRadius = 8.dp)
        }

        PvotZoomSelector(
            labels = stops.map { formatZoomRatio(it.approximate) },
            selectedIndex = selectedIndex,
            selectedLabel =
                when {
                    !usesLogicalZoomRatio -> "${formatZoomRatio(selected.approximate)}x"
                    beyondLastLens -> formatZoomRatio(selected.approximate)
                    else -> "${formatZoomRatio(zoomRatio)}x"
                },
            onSelect = { index ->
                val lens = stops[index]
                when {
                    index != selectedIndex ->
                        when (val camera = lens.camera) {
                            null -> onZoom(lens.exact)
                            else -> onSelectCamera(camera)
                        }

                    usesLogicalZoomRatio && lens.approximate == 1f ->
                        onZoom(CropZoomRatios.firstOrNull { it > zoomRatio + 0.02f } ?: lens.exact)

                    else -> onResetZoom()
                }
            },
            modifier = Modifier.align(Alignment.TopCenter).offset(y = referenceBottom - 56.dp),
            onAdjustStart =
                if (canZoom) {
                    {
                        dialVisible = true
                        dialInteraction = 1
                    }
                } else null,
            onAdjust = onPinchZoom,
            onAdjustEnd = { dialInteraction = -1 },
        )
    }
}

private val DECIMAL_FORMAT_SYMBOLS = DecimalFormatSymbols(Locale.US)
private val ZOOM_RATIO_FORMATTER = DecimalFormat("0.#", DECIMAL_FORMAT_SYMBOLS)
private val ZOOM_RATIO_FORMATTER_SUB_ONE = DecimalFormat(".#", DECIMAL_FORMAT_SYMBOLS)

fun formatZoomRatio(zoomRatio: Float): String =
    when (zoomRatio < 1f) {
        true -> ZOOM_RATIO_FORMATTER_SUB_ONE.format(zoomRatio)
        false -> ZOOM_RATIO_FORMATTER.format(zoomRatio)
    }
