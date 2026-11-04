/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.processing

internal object Bayer {
    const val RED = 0
    const val GREEN = 1
    const val BLUE = 2

    private val PATTERNS =
        arrayOf(
            intArrayOf(RED, GREEN, GREEN, BLUE),
            intArrayOf(GREEN, RED, BLUE, GREEN),
            intArrayOf(GREEN, BLUE, RED, GREEN),
            intArrayOf(BLUE, GREEN, GREEN, RED),
        )

    fun color(arrangement: Int, position: Int) =
        PATTERNS.getOrElse(arrangement) { PATTERNS[0] }[position]

    fun shadingChannel(arrangement: Int, position: Int) =
        when (color(arrangement, position)) {
            RED -> 0
            BLUE -> 3
            else -> if (position < 2) 1 else 2
        }

    fun positionOf(arrangement: Int, color: Int) =
        (0 until 4).first { color(arrangement, it) == color }

    fun greens(arrangement: Int) = (0 until 4).filter { color(arrangement, it) == GREEN }
}

internal class FrameReader(frame: RawFrame, blackLevel: FloatArray, whiteLevel: Float) {
    private val data = frame.data
    private val rowStride = frame.rowStride
    private val black = blackLevel.copyOf()
    private val scale = FloatArray(4) { 1f / (whiteLevel - blackLevel[it]) }

    fun get(position: Int, x: Int, y: Int): Float {
        val row = 2 * y + (position shr 1)
        val column = 2 * x + (position and 1)
        return ((data.get(row * rowStride + column).toInt() and 0xFFFF) - black[position]) *
            scale[position]
    }
}
