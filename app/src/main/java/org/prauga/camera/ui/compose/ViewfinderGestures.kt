/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private enum class ViewfinderGesture {
    Tap,
    Drag,
    Pinch,
}

@Composable
fun Modifier.viewfinderGestures(
    canAdjustExposure: () -> Boolean,
    onTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit,
    onExposureDrag: (Float) -> Unit,
    onPinch: (Float) -> Unit,
    onSwipe: (next: Boolean) -> Unit,
): Modifier {
    val currentCanAdjustExposure by rememberUpdatedState(canAdjustExposure)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnExposureDrag by rememberUpdatedState(onExposureDrag)
    val currentOnPinch by rememberUpdatedState(onPinch)
    val currentOnSwipe by rememberUpdatedState(onSwipe)

    return pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        val swipeDistance = 75.dp.toPx()

        awaitEachGesture {
            val down = awaitFirstDown()

            var gesture: ViewfinderGesture? = null
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (gesture == null) {
                    val event = awaitPointerEvent()
                    val change = event.changes.first()
                    gesture =
                        when {
                            event.changes.size > 1 -> ViewfinderGesture.Pinch
                            !change.pressed -> ViewfinderGesture.Tap
                            (change.position - down.position).getDistance() > slop ->
                                ViewfinderGesture.Drag
                            else -> null
                        }
                }
            }

            when (gesture) {
                null -> {
                    currentOnLongPress(down.position)
                    awaitRelease()
                }

                ViewfinderGesture.Tap -> currentOnTap(down.position)

                ViewfinderGesture.Pinch -> pinch(currentOnPinch)

                ViewfinderGesture.Drag -> {
                    var last = down.position
                    var total = Offset.Zero
                    var exposure: Boolean? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) {
                            pinch(currentOnPinch, event)
                            return@awaitEachGesture
                        }
                        val change = event.changes.first()
                        if (!change.pressed) break
                        val delta = change.position - last
                        last = change.position
                        total = change.position - down.position
                        if (exposure == null) {
                            exposure = abs(total.y) > abs(total.x) && currentCanAdjustExposure()
                        }
                        if (exposure) {
                            currentOnExposureDrag(-delta.y / (size.height * 0.75f))
                        }
                        if (change.positionChanged()) change.consume()
                    }
                    if (exposure != true && abs(total.x) > swipeDistance) {
                        currentOnSwipe(total.x < 0)
                    }
                }
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitRelease() {
    while (awaitPointerEvent().changes.any { it.pressed }) {
        continue
    }
}

private suspend fun AwaitPointerEventScope.pinch(
    onPinch: (Float) -> Unit,
    first: PointerEvent? = null,
) {
    var event = first ?: awaitPointerEvent()
    while (event.changes.any { it.pressed }) {
        val zoom = event.calculateZoom()
        if (zoom != 1f) {
            onPinch(zoom)
        }
        event.changes.forEach { if (it.positionChanged()) it.consume() }
        event = awaitPointerEvent()
    }
}
