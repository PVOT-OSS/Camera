/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.processing

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

internal class Strip(
    private val planes: Array<FloatArray>,
    private val planeWidth: Int,
    private val planeHeight: Int,
    private val metadata: BurstMetadata,
    private val tone: ToneField,
    private val look: Look,
) {
    val width = planeWidth * 2
    val height = planeHeight * 2

    private val red = Bayer.positionOf(metadata.colorFilterArrangement, Bayer.RED)
    private val blue = Bayer.positionOf(metadata.colorFilterArrangement, Bayer.BLUE)
    private val greens = Bayer.greens(metadata.colorFilterArrangement)
    private val transform = metadata.colorTransform
    private val curveScale = (look.lumaCurve.size - 1).toFloat()
    private val tintScale = (look.tint[0].size - 1).toFloat()
    private val steps =
        FloatArray(look.sigmas.size) {
            val previous = if (it == 0) 0f else look.sigmas[it - 1]
            sqrt(max(look.sigmas[it] * look.sigmas[it] - previous * previous, 0f))
        }
    private val margin = ceil(3 * look.sigmas.fold(0f) { a, b -> max(a, b) }).toInt() + 2

    fun render(top: Int, rows: Int, photo: IntArray, base: IntArray) {
        val start = max(top - margin, 0)
        val end = min(top + rows + margin, height)
        val count = end - start
        val size = count * width
        val luma = FloatArray(size)
        val cr = FloatArray(size)
        val cb = FloatArray(size)
        val rgb = FloatArray(3)

        for (row in 0 until count) {
            val y = start + row
            for (x in 0 until width) {
                pixel(x, y, rgb)
                if (y >= top && y < top + rows) {
                    base[(y - top) * width + x] =
                        argb(encode(rgb[0]), encode(rgb[1]), encode(rgb[2]))
                }
                applyColor(rgb)
                val i = row * width + x
                val value = 0.299f * rgb[0] + 0.587f * rgb[1] + 0.114f * rgb[2]
                luma[i] = value
                cr[i] = 0.5f + look.chroma * (rgb[0] - value) * 0.713f
                cb[i] = 0.5f + look.chroma * (rgb[2] - value) * 0.564f
            }
        }

        val delta = detail(luma, count)
        for (row in top - start until top - start + rows) {
            for (x in 0 until width) {
                val i = row * width + x
                val value = luma[i] + delta[i]
                val r = value + 1.403f * (cr[i] - 0.5f)
                val g = value - 0.714f * (cr[i] - 0.5f) - 0.344f * (cb[i] - 0.5f)
                val b = value + 1.773f * (cb[i] - 0.5f)
                photo[(row - (top - start)) * width + x] = argb(r, g, b)
            }
        }
    }

    private fun pixel(x: Int, y: Int, out: FloatArray) {
        val r = sample(red, x, y)
        val g = (sample(greens[0], x, y) + sample(greens[1], x, y)) / 2
        val b = sample(blue, x, y)
        val gain = tone.sample(x.toFloat(), y.toFloat())
        var peak = 0f
        for (row in 0 until 3) {
            val value =
                max(
                    transform[row * 3] * r +
                        transform[row * 3 + 1] * g +
                        transform[row * 3 + 2] * b,
                    0f,
                ) * gain
            out[row] = value
            peak = max(peak, value)
        }
        if (peak > 0f) {
            val scale = Tone.shoulder(peak) / peak
            for (row in 0 until 3) {
                out[row] = min(out[row] * scale, 1f)
            }
        }
    }

    private fun sample(position: Int, x: Int, y: Int): Float {
        val u = ((x - (position and 1)) / 2f).coerceIn(0f, (planeWidth - 1).toFloat())
        val v = ((y - (position shr 1)) / 2f).coerceIn(0f, (planeHeight - 1).toFloat())
        val x0 = u.toInt()
        val y0 = v.toInt()
        val x1 = min(x0 + 1, planeWidth - 1)
        val y1 = min(y0 + 1, planeHeight - 1)
        val fx = u - x0
        val fy = v - y0
        val plane = planes[position]
        val top = plane[y0 * planeWidth + x0] * (1 - fx) + plane[y0 * planeWidth + x1] * fx
        val bottom = plane[y1 * planeWidth + x0] * (1 - fx) + plane[y1 * planeWidth + x1] * fx
        return top * (1 - fy) + bottom * fy
    }

    private fun applyColor(rgb: FloatArray) {
        val m = look.matrix
        val r = rgb[0]
        val g = rgb[1]
        val b = rgb[2]
        for (row in 0 until 3) {
            rgb[row] = encode(m[row * 3] * r + m[row * 3 + 1] * g + m[row * 3 + 2] * b)
        }

        val luma = LUMA_R * rgb[0] + LUMA_G * rgb[1] + LUMA_B * rgb[2]
        val toned = interpolate(look.lumaCurve, luma * curveScale)
        val ratio = toned / max(luma, 1e-4f)
        for (channel in 0 until 3) {
            val tint = interpolate(look.tint[channel], toned * tintScale)
            rgb[channel] = (rgb[channel] * ratio * tint).coerceIn(0f, 1f)
        }
    }

    private fun detail(luma: FloatArray, rows: Int): FloatArray {
        val delta = FloatArray(luma.size)
        var previous = luma
        for (k in look.sigmas.indices) {
            val current = blur(previous, rows, steps[k])
            val gain = look.gains[k]
            val threshold = look.thresholds.getOrElse(k) { 0f }
            for (i in delta.indices) {
                val band = previous[i] - current[i]
                val kept =
                    if (threshold > 0f) band * band / (band * band + threshold * threshold + 1e-12f)
                    else 1f
                delta[i] += (gain * kept - 1) * band
            }
            previous = current
        }
        return delta
    }

    private fun blur(image: FloatArray, rows: Int, sigma: Float): FloatArray {
        if (sigma <= 0f) {
            return image.copyOf()
        }
        val kernel = kernel(sigma)
        val radius = kernel.size / 2
        val temp = FloatArray(image.size)
        for (y in 0 until rows) {
            val offset = y * width
            for (x in 0 until width) {
                var sum = 0f
                for (k in kernel.indices) {
                    sum += kernel[k] * image[offset + (x + k - radius).coerceIn(0, width - 1)]
                }
                temp[offset + x] = sum
            }
        }
        val out = FloatArray(image.size)
        for (y in 0 until rows) {
            for (x in 0 until width) {
                var sum = 0f
                for (k in kernel.indices) {
                    sum += kernel[k] * temp[(y + k - radius).coerceIn(0, rows - 1) * width + x]
                }
                out[y * width + x] = sum
            }
        }
        return out
    }

    companion object {
        private const val LUMA_R = 0.2126f
        private const val LUMA_G = 0.7152f
        private const val LUMA_B = 0.0722f
        private const val TABLE = 4096
        private val ENCODE =
            FloatArray(TABLE + 1) {
                val x = it / TABLE.toFloat()
                if (x <= 0.0031308f) 12.92f * x else 1.055f * x.pow(1 / 2.4f) - 0.055f
            }

        fun encode(value: Float): Float {
            val position = value.coerceIn(0f, 1f) * TABLE
            val index = min(position.toInt(), TABLE - 1)
            val fraction = position - index
            return ENCODE[index] * (1 - fraction) + ENCODE[index + 1] * fraction
        }

        fun interpolate(table: FloatArray, position: Float): Float {
            val clamped = position.coerceIn(0f, (table.size - 1).toFloat())
            val index = min(clamped.toInt(), table.size - 2)
            val fraction = clamped - index
            return table[index] * (1 - fraction) + table[index + 1] * fraction
        }

        fun argb(r: Float, g: Float, b: Float): Int {
            val red = (r.coerceIn(0f, 1f) * 255 + 0.5f).toInt()
            val green = (g.coerceIn(0f, 1f) * 255 + 0.5f).toInt()
            val blue = (b.coerceIn(0f, 1f) * 255 + 0.5f).toInt()
            return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
        }

        fun kernel(sigma: Float): FloatArray {
            val radius = max(ceil(3 * sigma).toInt(), 1)
            val kernel =
                FloatArray(2 * radius + 1) {
                    exp(-((it - radius) * (it - radius)) / (2 * sigma * sigma))
                }
            val sum = kernel.sum()
            return FloatArray(kernel.size) { kernel[it] / sum }
        }
    }
}
