/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CameraColorScheme =
    darkColorScheme(
        primary = CameraYellow,
        onPrimary = Color.Black,
        onSurface = Color.White,
        onSurfaceVariant = Color.White,
        surface = Color.Black,
        surfaceVariant = Color(0xFF2C2C2E),
        background = Color.Black,
    )

@Composable
fun CameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CameraColorScheme, content = content)
}
