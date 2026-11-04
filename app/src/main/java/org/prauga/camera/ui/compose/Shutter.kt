/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.prauga.camera.R
import org.prauga.camera.capture.models.CameraMode
import org.prauga.camera.capture.models.CameraState

private const val QUICK_TAKE_HOLD_MS = 450L

private sealed interface ShutterPress {
    data object Tap : ShutterPress

    data object Cancel : ShutterPress

    data object Burst : ShutterPress

    data object Hold : ShutterPress
}

@Composable
fun ShutterButton(
    cameraMode: CameraMode,
    cameraState: CameraState,
    quickTake: CameraScreenState.QuickTake,
    enabled: Boolean,
    gesturesEnabled: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onQuickTakeStart: () -> Unit,
    onQuickTakeLock: () -> Unit,
    onQuickTakeStop: () -> Unit,
    onBurstStart: () -> Unit,
    onBurstStop: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, tween(85))

    val recording = cameraState.isRecordingVideo || quickTake != CameraScreenState.QuickTake.NONE
    val coreColor by
        animateColorAsState(
            when {
                recording || cameraMode == CameraMode.VIDEO -> CameraRecording
                else -> Color.White
            },
            tween(240),
        )
    val coreSize by animateDpAsState(if (recording) 30.dp else 64.dp, tween(240))
    val coreRadius by animateDpAsState(if (recording) 8.dp else 32.dp, tween(240))

    val currentEnabled by rememberUpdatedState(enabled)
    val currentGesturesEnabled by
        rememberUpdatedState(
            gesturesEnabled &&
                cameraMode == CameraMode.PHOTO &&
                cameraState == CameraState.IDLE &&
                quickTake == CameraScreenState.QuickTake.NONE
        )
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnQuickTakeStart by rememberUpdatedState(onQuickTakeStart)
    val currentOnQuickTakeLock by rememberUpdatedState(onQuickTakeLock)
    val currentOnQuickTakeStop by rememberUpdatedState(onQuickTakeStop)
    val currentOnBurstStart by rememberUpdatedState(onBurstStart)
    val currentOnBurstStop by rememberUpdatedState(onBurstStop)

    val lockDistance = with(LocalDensity.current) { 90.dp.toPx() }
    val description = stringResource(R.string.shutter_button_description)

    Box(modifier = modifier.size(78.dp), contentAlignment = Alignment.Center) {
        if (quickTake == CameraScreenState.QuickTake.HOLDING) {
            Box(
                modifier =
                    Modifier.offset(x = 90.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f))
            )
        }

        Box(
            modifier =
                Modifier.size(78.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Color(0xFF999999).copy(alpha = if (recording) 0.45f else 0.25f))
                    .border(BorderStroke(1.2.dp, Color.White.copy(alpha = 0.48f)), CircleShape)
                    .semantics {
                        role = Role.Button
                        contentDescription = description
                        onClick {
                            currentOnTap()
                            true
                        }
                    }
                    .pointerInput(Unit) {
                        val burstDistance = 40.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (!currentEnabled) {
                                return@awaitEachGesture
                            }
                            pressed = true

                            val result =
                                when (currentGesturesEnabled) {
                                    false -> {
                                        var press: ShutterPress = ShutterPress.Tap
                                        while (true) {
                                            val change = awaitPointerEvent().changes.first()
                                            if (!change.pressed) {
                                                break
                                            }
                                            if (
                                                change.position.x !in 0f..size.width.toFloat() ||
                                                    change.position.y !in 0f..size.height.toFloat()
                                            ) {
                                                press = ShutterPress.Cancel
                                            }
                                        }
                                        press
                                    }

                                    true ->
                                        withTimeoutOrNull(QUICK_TAKE_HOLD_MS) {
                                            while (true) {
                                                val change = awaitPointerEvent().changes.first()
                                                if (!change.pressed) {
                                                    return@withTimeoutOrNull ShutterPress.Tap
                                                }
                                                if (
                                                    change.position.x - down.position.x <
                                                        -burstDistance
                                                ) {
                                                    return@withTimeoutOrNull ShutterPress.Burst
                                                }
                                            }
                                        } ?: ShutterPress.Hold
                                }

                            when (result) {
                                ShutterPress.Tap -> currentOnTap()

                                ShutterPress.Cancel -> {}

                                ShutterPress.Burst -> {
                                    currentOnBurstStart()
                                    while (awaitPointerEvent().changes.first().pressed) {
                                        continue
                                    }
                                    currentOnBurstStop()
                                }

                                ShutterPress.Hold -> {
                                    currentOnQuickTakeStart()
                                    var locked = false
                                    while (true) {
                                        val change = awaitPointerEvent().changes.first()
                                        if (!change.pressed) {
                                            break
                                        }
                                        dragOffset =
                                            (change.position.x - down.position.x).coerceIn(
                                                0f,
                                                lockDistance,
                                            )
                                        if (!locked && dragOffset >= lockDistance * 0.9f) {
                                            locked = true
                                            currentOnQuickTakeLock()
                                        }
                                    }
                                    if (!locked) {
                                        currentOnQuickTakeStop()
                                    }
                                }
                            }

                            pressed = false
                            dragOffset = 0f
                        }
                    }
        )

        Box(
            modifier =
                Modifier.offset { IntOffset(dragOffset.toInt(), 0) }
                    .scale(scale)
                    .size(coreSize)
                    .clip(RoundedCornerShape(coreRadius))
                    .background(coreColor.copy(alpha = if (enabled) 1f else 0.5f))
        )
    }
}
