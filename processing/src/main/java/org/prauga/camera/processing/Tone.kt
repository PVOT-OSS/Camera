/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.processing

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal class ToneField(val width: Int, val height: Int, val gains: FloatArray) {
    fun sample(x: Float, y: Float): Float {
        val gx = (x / Tone.CELL - 0.5f).coerceIn(0f, (width - 1).toFloat())
        val gy = (y / Tone.CELL - 0.5f).coerceIn(0f, (height - 1).toFloat())
        val x0 = gx.toInt()
        val y0 = gy.toInt()
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)
        val fx = gx - x0
        val fy = gy - y0
        val top = gains[y0 * width + x0] * (1 - fx) + gains[y0 * width + x1] * fx
        val bottom = gains[y1 * width + x0] * (1 - fx) + gains[y1 * width + x1] * fx
        return top * (1 - fy) + bottom * fy
    }
}

internal object Tone {
    const val CELL = 16
    private const val EXPOSURE_TARGET = 0.24f
    private const val FACE_LEVEL = 1.0f
    private const val SHADOW_LIFT = 0.15f
    private const val LIFT_SIGMA = 12f
    private const val KNEE = 0.6f
    private val LUMA = floatArrayOf(0.2126f, 0.7152f, 0.0722f)

    fun field(
        planes: Array<FloatArray>,
        planeWidth: Int,
        planeHeight: Int,
        metadata: BurstMetadata,
    ): ToneField {
        val step = CELL / 2
        val width = (planeWidth + step - 1) / step
        val height = (planeHeight + step - 1) / step
        val arrangement = metadata.colorFilterArrangement
        val red = Bayer.positionOf(arrangement, Bayer.RED)
        val blue = Bayer.positionOf(arrangement, Bayer.BLUE)
        val greens = Bayer.greens(arrangement)
        val transform = metadata.colorTransform

        val luminance = FloatArray(width * height)
        for (cy in 0 until height) {
            for (cx in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                var n = 0
                for (y in cy * step until min((cy + 1) * step, planeHeight)) {
                    for (x in cx * step until min((cx + 1) * step, planeWidth)) {
                        val i = y * planeWidth + x
                        r += planes[red][i]
                        g += (planes[greens[0]][i] + planes[greens[1]][i]) / 2
                        b += planes[blue][i]
                        n++
                    }
                }
                var value = 0f
                for (row in 0 until 3) {
                    val linear =
                        (transform[row * 3] * r +
                            transform[row * 3 + 1] * g +
                            transform[row * 3 + 2] * b) / n
                    value += LUMA[row] * linear
                }
                luminance[cy * width + cx] = max(value, 1e-5f)
            }
        }

        val median = luminance.sortedArray()[luminance.size / 2]
        val exposure = (EXPOSURE_TARGET / median).coerceIn(0.5f, 16f)
        val faces = faceGains(luminance, width, height, median, metadata)

        val base = FloatArray(width * height) { ln(luminance[it] * exposure * faces[it]) / ln(2f) }
        val blurred = blur(base, width, height, LIFT_SIGMA)
        val pivot =
            blurred.sortedArray()[(blurred.size * 0.7f).toInt().coerceAtMost(blurred.size - 1)]
        val gains =
            FloatArray(width * height) {
                val below = min(blurred[it] - pivot, 0f)
                exposure * faces[it] * min(2f.pow(-SHADOW_LIFT * below), 3f)
            }
        return ToneField(width, height, gains)
    }

    fun shoulder(value: Float) =
        if (value <= KNEE) value else KNEE + (1 - KNEE) * (1 - exp(-(value - KNEE) / (1 - KNEE)))

    private fun faceGains(
        luminance: FloatArray,
        width: Int,
        height: Int,
        median: Float,
        metadata: BurstMetadata,
    ): FloatArray {
        val gains = FloatArray(width * height) { 1f }
        for (face in metadata.faces) {
            val left = face[0] / CELL.toFloat()
            val top = face[1] / CELL.toFloat()
            val right = face[2] / CELL.toFloat()
            val bottom = face[3] / CELL.toFloat()
            val cx = (left + right) / 2
            val cy = (top + bottom) / 2
            val size = max(right - left, bottom - top)
            val quarter = max(size / 4, 0.5f)

            val inside = mutableListOf<Float>()
            for (y in (cy - quarter).toInt()..(cy + quarter).toInt()) {
                for (x in (cx - quarter).toInt()..(cx + quarter).toInt()) {
                    if (x in 0 until width && y in 0 until height) {
                        inside += luminance[y * width + x]
                    }
                }
            }
            if (inside.isEmpty()) {
                continue
            }
            inside.sort()
            val gain = (FACE_LEVEL * median / inside[inside.size / 2]).coerceIn(1f, 3f)

            val mask = FloatArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val ex = (x + 0.5f - cx) / (size * 0.9f)
                    val ey = (y + 0.5f - cy) / (size * 1.2f)
                    if (ex * ex + ey * ey <= 1f) {
                        mask[y * width + x] = 1f
                    }
                }
            }
            val soft = blur(mask, width, height, max(width, height) / 40f)
            for (i in gains.indices) {
                gains[i] = max(gains[i], 1 + (gain - 1) * soft[i].coerceIn(0f, 1f))
            }
        }
        return gains
    }

    fun blur(image: FloatArray, width: Int, height: Int, sigma: Float): FloatArray {
        val kernel = Strip.kernel(sigma)
        val radius = kernel.size / 2
        val temp = FloatArray(image.size)
        val out = FloatArray(image.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                for (k in kernel.indices) {
                    sum += kernel[k] * image[y * width + (x + k - radius).coerceIn(0, width - 1)]
                }
                temp[y * width + x] = sum
            }
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                for (k in kernel.indices) {
                    sum += kernel[k] * temp[(y + k - radius).coerceIn(0, height - 1) * width + x]
                }
                out[y * width + x] = sum
            }
        }
        return out
    }
}
