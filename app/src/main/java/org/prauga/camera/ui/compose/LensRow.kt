/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlinx.coroutines.delay
import org.prauga.camera.capture.ZoomInfo
import org.prauga.camera.capture.models.Camera

private const val DIAL_HOLD_MS = 450L
private const val DIAL_HIDE_MS = 1200L

private val CropZoomRatios = listOf(28f / 24f, 35f / 24f)
private val DialLabels = listOf(0.5f, 1f, 2f, 3f, 5f, 10f, 15f, 25f, 50f, 100f)

private data class Lens(val approximate: Float, val exact: Float, val camera: Camera?)

private sealed interface PressResult {
    data class Tap(val x: Float) : PressResult

    data object Hold : PressResult
}

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

    if (!enabled || (lenses.size < 2 && !canZoom)) {
        return
    }

    val selectedIndex =
        when (usesLogicalZoomRatio) {
            true -> lenses.indexOfLast { zoomRatio >= it.exact * 0.99f }.coerceAtLeast(0)
            false -> lenses.indexOfFirst { it.camera == activeCamera }.coerceAtLeast(0)
        }

    var dialVisible by remember { mutableStateOf(false) }
    var dialInteraction by remember { mutableIntStateOf(0) }
    LaunchedEffect(dialInteraction) {
        if (dialVisible) {
            delay(DIAL_HIDE_MS)
            dialVisible = false
        }
    }

    val itemBounds = remember { mutableMapOf<Int, ClosedFloatingPointRange<Float>>() }

    val onLensTap: (Int) -> Unit = { index ->
        val lens = lenses[index]
        when {
            index != selectedIndex ->
                when (val camera = lens.camera) {
                    null -> onZoom(lens.exact)
                    else -> onSelectCamera(camera)
                }

            usesLogicalZoomRatio && lens.approximate == 1f -> {
                val next = CropZoomRatios.firstOrNull { it > zoomRatio + 0.02f }
                onZoom(next ?: lens.exact)
            }

            else -> onResetZoom()
        }
    }

    val currentOnLensTap by rememberUpdatedState(onLensTap)
    val currentCanZoom by rememberUpdatedState(canZoom)

    Box(modifier = Modifier.fillMaxSize()) {
        if (zoomState != null) {
            AnimatedVisibility(
                visible = dialVisible,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = referenceBottom - 131.dp),
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(220)),
            ) {
                ZoomDial(zoomState = zoomState)
            }
        }

        val maxLens = lenses.lastOrNull()?.exact ?: 1f
        AnimatedVisibility(
            visible = !dialVisible && usesLogicalZoomRatio && zoomRatio > maxLens * 1.05f,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = referenceBottom - 84.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            GlassSurface(
                modifier = Modifier.height(26.dp).width(48.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "${formatZoomRatio(zoomRatio)}×",
                    color = CameraYellow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Row(
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .offset(y = referenceBottom - 56.dp)
                    .pointerInput(Unit) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val result =
                                withTimeoutOrNull(DIAL_HOLD_MS) {
                                    while (true) {
                                        val change = awaitPointerEvent().changes.first()
                                        if (!change.pressed) {
                                            return@withTimeoutOrNull PressResult.Tap(
                                                change.position.x
                                            )
                                        }
                                        if (abs(change.position.x - down.position.x) > slop) {
                                            return@withTimeoutOrNull PressResult.Hold
                                        }
                                    }
                                } ?: PressResult.Hold

                            when (result) {
                                is PressResult.Tap -> {
                                    val x = result.x - 12.dp.toPx()
                                    itemBounds.entries
                                        .firstOrNull { x in it.value }
                                        ?.let { currentOnLensTap(it.key) }
                                }

                                PressResult.Hold -> {
                                    if (!currentCanZoom) {
                                        return@awaitEachGesture
                                    }
                                    dialVisible = true
                                    var lastX = down.position.x
                                    val stepPx = 80.dp.toPx()
                                    while (true) {
                                        val change = awaitPointerEvent().changes.first()
                                        if (!change.pressed) {
                                            break
                                        }
                                        val dx = change.position.x - lastX
                                        lastX = change.position.x
                                        if (dx != 0f) {
                                            onPinchZoom(exp(dx / stepPx * ln(2f)))
                                        }
                                        change.consume()
                                        dialInteraction++
                                    }
                                    dialInteraction++
                                }
                            }
                        }
                    }
                    .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (lenses.size >= 2) {
                lenses.forEachIndexed { index, lens ->
                    val selected = index == selectedIndex
                    val size by animateDpAsState(if (selected) 34.dp else 30.dp, tween(240))

                    Box(
                        modifier =
                            Modifier.size(size)
                                .onGloballyPositioned {
                                    val x = it.positionInParent().x
                                    itemBounds[index] = (x - 2f)..(x + it.size.width + 2f)
                                }
                                .clip(CircleShape)
                                .background(
                                    CameraLens.copy(alpha = if (selected) 0.65f else 0.22f)
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text =
                                when (selected) {
                                    true ->
                                        selectedLensLabel(
                                            lens = lens,
                                            zoomRatio = zoomRatio,
                                            usesLogicalZoomRatio = usesLogicalZoomRatio,
                                            isLast = index == lenses.lastIndex,
                                        )
                                    false -> formatZoomRatio(lens.approximate)
                                },
                            color = if (selected) CameraYellow else Color.White,
                            fontSize = if (selected) 14.sp else 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.rotate(LocalIconRotation.current),
                        )
                    }
                }
            } else {
                Box(
                    modifier =
                        Modifier.size(34.dp)
                            .onGloballyPositioned {
                                val x = it.positionInParent().x
                                itemBounds[0] = x..(x + it.size.width)
                            }
                            .clip(CircleShape)
                            .background(CameraLens.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${formatZoomRatio(zoomRatio)}x",
                        color = CameraYellow,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.rotate(LocalIconRotation.current),
                    )
                }
            }
        }
    }
}

private fun selectedLensLabel(
    lens: Lens,
    zoomRatio: Float,
    usesLogicalZoomRatio: Boolean,
    isLast: Boolean,
) =
    when {
        !usesLogicalZoomRatio -> "${formatZoomRatio(lens.approximate)}x"
        isLast && zoomRatio > lens.exact * 1.05f -> formatZoomRatio(lens.approximate)
        else -> "${formatZoomRatio(zoomRatio)}x"
    }

@Composable
private fun ZoomDial(zoomState: ZoomInfo) {
    GlassSurface(
        modifier = Modifier.width(330.dp).height(131.dp),
        shape = RoundedCornerShape(48.dp),
        alpha = 0.6f,
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = "${formatZoomRatio(zoomState.zoomRatio)}×",
            color = CameraYellow,
            fontSize = 22.sp,
            modifier = Modifier.padding(top = 15.dp),
        )

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(top = 64.dp, start = 19.dp, end = 19.dp)
        ) {
            val minLog = ln(zoomState.minZoomRatio)
            val maxLog = ln(zoomState.maxZoomRatio)
            val span = (maxLog - minLog).takeIf { it > 0f } ?: 1f
            val labels =
                DialLabels.filter { it >= zoomState.minZoomRatio && it <= zoomState.maxZoomRatio }

            Canvas(modifier = Modifier.fillMaxWidth().height(37.dp)) {
                val ticks = 38
                for (i in 0..ticks) {
                    val x = size.width * i / ticks
                    val height = if (i % 5 == 0) 23.dp.toPx() else 11.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = if (i % 5 == 0) 0.9f else 0.5f),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val indexX = size.width * (ln(zoomState.zoomRatio) - minLog) / span
                drawLine(
                    color = CameraYellow,
                    start = Offset(indexX, 0f),
                    end = Offset(indexX, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }

            val width = maxWidth
            labels.forEach { label ->
                Text(
                    text = formatZoomRatio(label),
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier =
                        Modifier.offset(x = width * ((ln(label) - minLog) / span) - 4.dp, y = 35.dp),
                )
            }
        }
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
