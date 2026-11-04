/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val CameraYellow = Color(0xFFFFD60A)
val CameraRecording = Color(0xFFFF3B30)
val CameraGlass = Color(0xFF444444)
val CameraPanel = Color(0xFF2C2C2E)
val CameraLens = Color(0xFF222222)

val LocalIconRotation = compositionLocalOf { 0f }

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    alpha: Float = 0.65f,
    color: Color = CameraGlass,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f))
                .clip(shape)
                .background(color.copy(alpha = alpha))
                .border(BorderStroke(0.6.dp, Color.White.copy(alpha = 0.24f)), shape),
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}

@Composable
fun CameraIcon(
    @DrawableRes icon: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size).rotate(LocalIconRotation.current),
        tint = tint,
    )
}
