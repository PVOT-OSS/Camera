/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.device

import java.util.SortedMap
import kotlin.math.floor
import kotlin.math.roundToInt

object LensZoomRatios {
    fun of(
        lensZoomRatios: List<Float>,
        minZoomRatio: Float,
        maxZoomRatio: Float,
    ): SortedMap<Float, Float> {
        val zoomRatios = sortedMapOf(1f to 1f)

        for (zoomRatio in lensZoomRatios.map { it.coerceIn(minZoomRatio, maxZoomRatio) }.sorted()) {
            zoomRatios.putIfAbsent(approximate(zoomRatio), zoomRatio)
        }

        return zoomRatios
    }

    fun approximate(zoomRatio: Float) = when {
        zoomRatio < 1f -> floor(zoomRatio * 10f + 0.1f) / 10f
        else -> zoomRatio.roundToInt().toFloat()
    }
}
