/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.processing

import java.nio.ShortBuffer
import java.util.Random
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstProcessorTest {
    @Test
    fun fftRoundTrips() {
        val fft = Fft16()
        val real = FloatArray(Fft16.AREA) { (it * 37 % 11).toFloat() }
        val imaginary = FloatArray(Fft16.AREA)
        val original = real.copyOf()
        fft.transform(real, imaginary, false)
        assertEquals(original.sum(), real[0], 1e-3f)
        fft.transform(real, imaginary, true)
        for (k in original.indices) {
            assertEquals(original[k], real[k] / Fft16.AREA, 1e-3f)
        }
    }

    @Test
    fun alignsAShiftedFrame() {
        val width = 256
        val height = 192
        val texture = texture(width + 32, height + 32)
        val reference =
            FloatArray(width * height) {
                texture[(it / width + 16) * (width + 32) + it % width + 16]
            }
        val shifted =
            FloatArray(width * height) {
                texture[(it / width + 16 - 4) * (width + 32) + it % width + 16 + 6]
            }
        val alignment = Align.align(reference, shifted, width, height)
        assertEquals(4, alignment.dy(128, 96))
        assertEquals(-6, alignment.dx(128, 96))
    }

    @Test
    fun mergesAFlatGrayBurst() {
        val width = 128
        val height = 96
        val frames =
            List(3) { index ->
                RawFrame(
                    ShortBuffer.wrap(
                        ShortArray(width * height) {
                            (300 + (it * 7919 + index * 31) % 17).toShort()
                        }
                    ),
                    width,
                    width,
                    height,
                )
            }
        val metadata =
            BurstMetadata(
                colorFilterArrangement = 0,
                blackLevel = FloatArray(4) { 64f },
                whiteLevel = 1023f,
                noiseProfile = Array(4) { doubleArrayOf(1e-4, 1e-6) },
                whiteBalance = floatArrayOf(1f, 1f, 1f, 1f),
                colorTransform = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
                lensShading = null,
                faces = emptyList(),
            )
        val pixels = IntArray(width * height)
        BurstProcessor(Look.IDENTITY).process(frames, metadata) { top, rows, rowWidth, photo, _ ->
            photo.copyInto(pixels, top * rowWidth, 0, rows * rowWidth)
        }
        val center = pixels[(height / 2) * width + width / 2]
        val red = center shr 16 and 0xFF
        val green = center shr 8 and 0xFF
        val blue = center and 0xFF
        assertTrue(red > 100 && abs(red - green) < 6 && abs(green - blue) < 6)
    }

    private fun texture(width: Int, height: Int): FloatArray {
        val random = Random(7)
        val noise = FloatArray(width * height) { random.nextFloat() }
        return FloatArray(width * height) {
            val x = it % width
            val y = it / width
            var sum = 0f
            for (dy in -2..2) {
                for (dx in -2..2) {
                    sum +=
                        noise[
                            (y + dy).coerceIn(0, height - 1) * width +
                                (x + dx).coerceIn(0, width - 1)]
                }
            }
            sum / 25
        }
    }
}
