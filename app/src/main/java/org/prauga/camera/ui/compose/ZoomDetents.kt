/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.compose

import kotlin.math.abs
import kotlin.math.ln

class ZoomDetents(private val reach: Float = ln(1.08f)) {
    private var zoomRatio = 1f
    private var detent: Float? = null

    fun begin(zoomRatio: Float) {
        this.zoomRatio = zoomRatio
        detent = null
    }

    fun scale(
        scaleFactor: Float,
        stops: List<Float>,
        range: ClosedFloatingPointRange<Float>,
    ): Pair<Float, Boolean> {
        zoomRatio = (zoomRatio * scaleFactor).coerceIn(range)

        val next = stops.firstOrNull { it in range && abs(ln(zoomRatio / it)) < reach }
        val reached = next != null && next != detent
        detent = next

        return (next ?: zoomRatio) to reached
    }
}
