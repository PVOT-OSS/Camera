/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.processing

import java.util.stream.IntStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

internal object Merge {
    private const val TILE = Fft16.SIZE
    private const val STEP = TILE / 2
    private const val STRENGTH = 8f

    private val window =
        FloatArray(TILE) { sqrt(0.5 - 0.5 * cos(2 * PI * (it + 0.5) / TILE)).toFloat() }
            .let { one -> FloatArray(Fft16.AREA) { one[it / TILE] * one[it % TILE] } }
    private val windowEnergy = window.sumOf { (it * it).toDouble() }.toFloat()

    fun planes(
        readers: List<FrameReader>,
        alignments: List<Alignment>,
        noiseProfile: Array<DoubleArray>,
        level: FloatArray,
        width: Int,
        height: Int,
    ): Array<FloatArray> {
        val reference = readers[0]
        val weight = FloatArray(width * height)
        return Array(4) { position ->
            val out = FloatArray(width * height)
            if (readers.size > 1) {
                merge(
                    readers,
                    alignments,
                    position,
                    noiseProfile[position],
                    level,
                    width,
                    height,
                    out,
                    if (position == 0) weight else null,
                )
            }
            IntStream.range(0, height).parallel().forEach { y ->
                for (x in 0 until width) {
                    val i = y * width + x
                    out[i] =
                        if (weight[i] > 0.5f) out[i] / weight[i] else reference.get(position, x, y)
                }
            }
            out
        }
    }

    private fun merge(
        readers: List<FrameReader>,
        alignments: List<Alignment>,
        position: Int,
        noise: DoubleArray,
        level: FloatArray,
        width: Int,
        height: Int,
        out: FloatArray,
        weight: FloatArray?,
    ) {
        val rows = (height - TILE) / STEP + 1
        val columns = (width - TILE) / STEP + 1
        if (rows <= 0 || columns <= 0) {
            return
        }
        val count = readers.size
        val scale = noise[0].toFloat()
        val offset = noise[1].toFloat()

        for (parity in 0..1) {
            IntStream.range(0, rows)
                .filter { it % 2 == parity }
                .parallel()
                .forEach { row ->
                    val fft = Fft16()
                    val referenceReal = FloatArray(Fft16.AREA)
                    val referenceImaginary = FloatArray(Fft16.AREA)
                    val real = FloatArray(Fft16.AREA)
                    val imaginary = FloatArray(Fft16.AREA)
                    val sumReal = FloatArray(Fft16.AREA)
                    val sumImaginary = FloatArray(Fft16.AREA)
                    val top = row * STEP

                    for (column in 0 until columns) {
                        val left = column * STEP
                        var levelSum = 0f
                        for (k in 0 until Fft16.AREA) {
                            val x = left + k % TILE
                            val y = top + k / TILE
                            referenceReal[k] = readers[0].get(position, x, y) * window[k]
                            referenceImaginary[k] = 0f
                            levelSum += level[y * width + x]
                        }
                        fft.transform(referenceReal, referenceImaginary, false)
                        referenceReal.copyInto(sumReal)
                        referenceImaginary.copyInto(sumImaginary)

                        val variance =
                            (scale * maxOf(levelSum / Fft16.AREA, 0f) + offset) *
                                windowEnergy *
                                STRENGTH

                        for (z in 1 until count) {
                            val alignment = alignments[z - 1]
                            val y0 = top + alignment.dy(left, top)
                            val x0 = left + alignment.dx(left, top)
                            if (y0 < 0 || x0 < 0 || y0 > height - TILE || x0 > width - TILE) {
                                for (k in 0 until Fft16.AREA) {
                                    sumReal[k] += referenceReal[k]
                                    sumImaginary[k] += referenceImaginary[k]
                                }
                                continue
                            }
                            for (k in 0 until Fft16.AREA) {
                                real[k] =
                                    readers[z].get(position, x0 + k % TILE, y0 + k / TILE) *
                                        window[k]
                                imaginary[k] = 0f
                            }
                            fft.transform(real, imaginary, false)
                            for (k in 0 until Fft16.AREA) {
                                val dr = referenceReal[k] - real[k]
                                val di = referenceImaginary[k] - imaginary[k]
                                val distance = dr * dr + di * di
                                val shrink = distance / (distance + variance)
                                sumReal[k] += real[k] + shrink * dr
                                sumImaginary[k] += imaginary[k] + shrink * di
                            }
                        }

                        fft.transform(sumReal, sumImaginary, true)
                        val normalize = 1f / (count * Fft16.AREA)
                        for (k in 0 until Fft16.AREA) {
                            val i = (top + k / TILE) * width + left + k % TILE
                            out[i] += sumReal[k] * normalize * window[k]
                            if (weight != null) {
                                weight[i] += window[k] * window[k]
                            }
                        }
                    }
                }
        }
    }
}
