/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.processing

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class Fft16 {
    private val cosines = FloatArray(SIZE / 2) { cos(2 * PI * it / SIZE).toFloat() }
    private val sines = FloatArray(SIZE / 2) { sin(2 * PI * it / SIZE).toFloat() }
    private val reversed = IntArray(SIZE) { Integer.reverse(it) ushr (32 - BITS) }

    fun transform(real: FloatArray, imaginary: FloatArray, inverse: Boolean) {
        for (row in 0 until SIZE) {
            line(real, imaginary, row * SIZE, 1, inverse)
        }
        for (column in 0 until SIZE) {
            line(real, imaginary, column, SIZE, inverse)
        }
    }

    private fun line(
        real: FloatArray,
        imaginary: FloatArray,
        start: Int,
        stride: Int,
        inverse: Boolean,
    ) {
        for (i in 0 until SIZE) {
            val j = reversed[i]
            if (j > i) {
                val a = start + i * stride
                val b = start + j * stride
                val re = real[a]
                real[a] = real[b]
                real[b] = re
                val im = imaginary[a]
                imaginary[a] = imaginary[b]
                imaginary[b] = im
            }
        }

        var size = 2
        while (size <= SIZE) {
            val half = size / 2
            val step = SIZE / size
            for (begin in 0 until SIZE step size) {
                for (k in 0 until half) {
                    val wr = cosines[k * step]
                    val wi = if (inverse) sines[k * step] else -sines[k * step]
                    val a = start + (begin + k) * stride
                    val b = a + half * stride
                    val tr = real[b] * wr - imaginary[b] * wi
                    val ti = real[b] * wi + imaginary[b] * wr
                    real[b] = real[a] - tr
                    imaginary[b] = imaginary[a] - ti
                    real[a] += tr
                    imaginary[a] += ti
                }
            }
            size *= 2
        }
    }

    companion object {
        const val SIZE = 16
        const val AREA = SIZE * SIZE
        private const val BITS = 4
    }
}
