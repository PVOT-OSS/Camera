/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import android.hardware.camera2.params.MeteringRectangle
import kotlin.math.roundToInt

object Camera2Metering {
    private const val REGION_FRACTION = 0.1f

    fun viewToSensor(
        u: Float,
        v: Float,
        sensorOrientation: Int,
        mirrored: Boolean,
    ): Pair<Float, Float> {
        val x = if (mirrored) 1f - u else u

        return when ((sensorOrientation % 360 + 360) % 360) {
            90 -> v to 1f - x
            180 -> 1f - x to 1f - v
            270 -> 1f - v to x
            else -> x to v
        }
    }

    fun regionBounds(
        sensorX: Float,
        sensorY: Float,
        width: Int,
        height: Int,
    ): IntArray {
        val size = (minOf(width, height) * REGION_FRACTION).roundToInt().coerceAtLeast(1)
        val left = (sensorX * width - size / 2f).roundToInt().coerceIn(0, (width - size).coerceAtLeast(0))
        val top = (sensorY * height - size / 2f).roundToInt().coerceIn(0, (height - size).coerceAtLeast(0))

        return intArrayOf(left, top, size, size)
    }

    fun region(sensorX: Float, sensorY: Float, width: Int, height: Int): MeteringRectangle {
        val (left, top, regionWidth, regionHeight) = regionBounds(sensorX, sensorY, width, height)

        return MeteringRectangle(
            left, top, regionWidth, regionHeight, MeteringRectangle.METERING_WEIGHT_MAX - 1
        )
    }
}
