/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.processing

import java.util.stream.IntStream
import kotlin.math.abs

internal class Alignment(val tilesX: Int, val tilesY: Int, val offsets: IntArray) {
    fun dy(x: Int, y: Int) = offsets[tile(x, y) * 2]

    fun dx(x: Int, y: Int) = offsets[tile(x, y) * 2 + 1]

    private fun tile(x: Int, y: Int) =
        minOf(y / Align.TILE, tilesY - 1) * tilesX + minOf(x / Align.TILE, tilesX - 1)
}

internal object Align {
    const val TILE = 32
    private const val RADIUS = 4
    private const val COARSE = 4
    private const val COARSE_RADIUS = 8

    fun align(reference: FloatArray, frame: FloatArray, width: Int, height: Int): Alignment {
        val (globalY, globalX) = globalShift(reference, frame, width, height)
        val tilesX = maxOf(width / TILE, 1)
        val tilesY = maxOf(height / TILE, 1)
        val offsets = IntArray(tilesX * tilesY * 2)

        IntStream.range(0, tilesX * tilesY).parallel().forEach { tile ->
            val top = (tile / tilesX) * TILE
            val left = (tile % tilesX) * TILE
            var best = Float.MAX_VALUE
            for (dy in globalY - RADIUS..globalY + RADIUS) {
                for (dx in globalX - RADIUS..globalX + RADIUS) {
                    var cost = 0f
                    for (y in top until minOf(top + TILE, height) step 2) {
                        val sy = (y + dy).coerceIn(0, height - 1)
                        for (x in left until minOf(left + TILE, width) step 2) {
                            val sx = (x + dx).coerceIn(0, width - 1)
                            cost += abs(frame[sy * width + sx] - reference[y * width + x])
                        }
                    }
                    if (cost < best) {
                        best = cost
                        offsets[tile * 2] = dy
                        offsets[tile * 2 + 1] = dx
                    }
                }
            }
        }
        return Alignment(tilesX, tilesY, offsets)
    }

    private fun globalShift(
        reference: FloatArray,
        frame: FloatArray,
        width: Int,
        height: Int,
    ): Pair<Int, Int> {
        val coarseWidth = width / COARSE
        val coarseHeight = height / COARSE
        val coarseReference = downsample(reference, width, coarseWidth, coarseHeight)
        val coarseFrame = downsample(frame, width, coarseWidth, coarseHeight)
        val margin = COARSE_RADIUS + 1
        if (coarseWidth <= 2 * margin || coarseHeight <= 2 * margin) {
            return 0 to 0
        }

        val shifts =
            (-COARSE_RADIUS..COARSE_RADIUS).flatMap { dy ->
                (-COARSE_RADIUS..COARSE_RADIUS).map { dx -> dy to dx }
            }
        val costs =
            shifts
                .parallelStream()
                .map { (dy, dx) ->
                    var cost = 0f
                    for (y in margin until coarseHeight - margin step 2) {
                        for (x in margin until coarseWidth - margin step 2) {
                            cost +=
                                abs(
                                    coarseFrame[(y + dy) * coarseWidth + x + dx] -
                                        coarseReference[y * coarseWidth + x]
                                )
                        }
                    }
                    cost
                }
                .toList()
        val (dy, dx) = shifts[costs.indices.minBy { costs[it] }]
        return dy * COARSE to dx * COARSE
    }

    private fun downsample(image: FloatArray, width: Int, outWidth: Int, outHeight: Int) =
        FloatArray(outWidth * outHeight).also { out ->
            IntStream.range(0, outHeight).parallel().forEach { y ->
                for (x in 0 until outWidth) {
                    var sum = 0f
                    for (dy in 0 until COARSE) {
                        for (dx in 0 until COARSE) {
                            sum += image[(y * COARSE + dy) * width + x * COARSE + dx]
                        }
                    }
                    out[y * outWidth + x] = sum / (COARSE * COARSE)
                }
            }
        }
}
