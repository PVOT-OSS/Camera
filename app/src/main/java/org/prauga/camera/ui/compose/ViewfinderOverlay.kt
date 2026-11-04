/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.prauga.camera.R
import org.prauga.camera.viewmodels.CameraUiState

private val ReticleSize = 70.dp
private val RailLength = 100.dp

@Composable
fun ViewfinderOverlay(
    state: CameraScreenState,
    uiState: CameraUiState,
    modifier: Modifier = Modifier,
    lockTop: Dp,
) {
    Box(modifier = modifier) {
        FocusReticle(state = state, uiState = uiState)

        AnimatedVisibility(
            visible = state.focusLocked,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = lockTop),
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
        ) {
            GlassSurface(
                modifier = Modifier.height(28.dp).width(90.dp),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.ae_af_lock),
                    color = CameraYellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        AnimatedContent(
            targetState = state.countdown,
            modifier = Modifier.align(Alignment.Center),
            transitionSpec = { fadeIn(tween(50)) togetherWith fadeOut(tween(50)) },
        ) { countdown ->
            if (countdown != null) {
                Text(
                    text = countdown.toString(),
                    color = Color.White,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.rotate(LocalIconRotation.current),
                )
            }
        }

        Blackout(key = state.blackoutKey)
    }
}

@Composable
private fun FocusReticle(state: CameraScreenState, uiState: CameraUiState) {
    val point = state.focusPoint ?: return

    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(state.focusKey) {
        scale.snapTo(1.4f)
        scale.animateTo(1f, tween(220))
    }

    LaunchedEffect(state.focusKey, state.exposureKey) {
        alpha.animateTo(1f, tween(80))
        if (!state.focusLocked) {
            delay(1500)
            alpha.animateTo(0.5f, tween(300))
            delay(1500)
            alpha.animateTo(0f, tween(300))
            state.clearFocus()
        }
    }

    val density = LocalDensity.current
    val x = with(density) { point.x.toDp() }
    val y = with(density) { point.y.toDp() }

    val level = uiState.exposureCompensationRangeToLevel?.second ?: 0.5f

    Box(modifier = Modifier.fillMaxSize().alpha(alpha.value)) {
        Box(
            modifier =
                Modifier.offset(x = x - ReticleSize / 2, y = y - ReticleSize / 2)
                    .size(ReticleSize)
                    .scale(scale.value)
                    .border(BorderStroke(1.dp, CameraYellow))
        )

        Box(
            modifier =
                Modifier.offset(x = x + ReticleSize / 2 + 24.dp, y = y - RailLength / 2)
                    .width(0.6.dp)
                    .height(RailLength)
                    .background(CameraYellow)
        )

        CameraIcon(
            icon = R.drawable.ic_cam_sun,
            size = 22.dp,
            tint = CameraYellow,
            modifier =
                Modifier.offset(
                    x = x + ReticleSize / 2 + 24.dp - 11.dp,
                    y = y - 11.dp + RailLength * (0.5f - level),
                ),
        )
    }
}

@Composable
private fun Blackout(key: Int) {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(key) {
        if (key > 0) {
            alpha.snapTo(1f)
            delay(85)
            alpha.animateTo(0f, tween(120))
        }
    }

    if (alpha.value > 0f) {
        Box(modifier = Modifier.fillMaxSize().alpha(alpha.value).background(Color.Black))
    }
}
