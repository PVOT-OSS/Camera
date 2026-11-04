/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.processing

import java.util.concurrent.ForkJoinPool
import java.util.stream.IntStream

class BurstProcessor(private val look: Look) {
    fun interface Output {
        fun rows(top: Int, rows: Int, width: Int, photo: IntArray, base: IntArray)
    }

    class Size(val width: Int, val height: Int)

    fun outputSize(frame: RawFrame) = Size(frame.width / 2 * 2, frame.height / 2 * 2)

    fun process(frames: List<RawFrame>, metadata: BurstMetadata, output: Output) {
        require(frames.isNotEmpty())
        val planeWidth = frames[0].width / 2
        val planeHeight = frames[0].height / 2
        val readers = frames.map { FrameReader(it, metadata.blackLevel, metadata.whiteLevel) }

        val reference = gray(readers[0], planeWidth, planeHeight)
        val alignments =
            readers.drop(1).map {
                Align.align(reference, gray(it, planeWidth, planeHeight), planeWidth, planeHeight)
            }
        val planes =
            Merge.planes(
                readers,
                alignments,
                metadata.noiseProfile,
                reference,
                planeWidth,
                planeHeight,
            )

        correct(planes, planeWidth, planeHeight, metadata)
        val tone = Tone.field(planes, planeWidth, planeHeight, metadata)
        val strip = Strip(planes, planeWidth, planeHeight, metadata, tone, look)

        val strips = (strip.height + STRIP_ROWS - 1) / STRIP_ROWS
        ForkJoinPool(PARALLEL_STRIPS)
            .submit {
                IntStream.range(0, strips).parallel().forEach { index ->
                    val top = index * STRIP_ROWS
                    val rows = minOf(STRIP_ROWS, strip.height - top)
                    val photo = IntArray(rows * strip.width)
                    val base = IntArray(rows * strip.width)
                    strip.render(top, rows, photo, base)
                    synchronized(output) { output.rows(top, rows, strip.width, photo, base) }
                }
            }
            .get()
    }

    private fun gray(reader: FrameReader, width: Int, height: Int) =
        FloatArray(width * height).also { out ->
            IntStream.range(0, height).parallel().forEach { y ->
                for (x in 0 until width) {
                    out[y * width + x] =
                        (reader.get(0, x, y) +
                            reader.get(1, x, y) +
                            reader.get(2, x, y) +
                            reader.get(3, x, y)) / 4
                }
            }
        }

    private fun correct(
        planes: Array<FloatArray>,
        width: Int,
        height: Int,
        metadata: BurstMetadata,
    ) {
        val shading = metadata.lensShading
        val fullWidth = width * 2
        val fullHeight = height * 2
        for (position in 0 until 4) {
            val channel = Bayer.shadingChannel(metadata.colorFilterArrangement, position)
            val balance = metadata.whiteBalance[channel]
            val plane = planes[position]
            IntStream.range(0, height).parallel().forEach { y ->
                for (x in 0 until width) {
                    val gain =
                        if (shading == null) {
                            1f
                        } else {
                            shadingGain(
                                shading,
                                channel,
                                (2 * x + (position and 1)) * (shading.columns - 1f) /
                                    (fullWidth - 1),
                                (2 * y + (position shr 1)) * (shading.rows - 1f) / (fullHeight - 1),
                            )
                        }
                    plane[y * width + x] *= gain * balance
                }
            }
        }
    }

    private fun shadingGain(shading: LensShading, channel: Int, u: Float, v: Float): Float {
        val x0 = u.toInt().coerceIn(0, shading.columns - 1)
        val y0 = v.toInt().coerceIn(0, shading.rows - 1)
        val x1 = minOf(x0 + 1, shading.columns - 1)
        val y1 = minOf(y0 + 1, shading.rows - 1)
        val fx = u - x0
        val fy = v - y0
        val gains = shading.gains
        fun at(x: Int, y: Int) = gains[(y * shading.columns + x) * 4 + channel]
        val top = at(x0, y0) * (1 - fx) + at(x1, y0) * fx
        val bottom = at(x0, y1) * (1 - fx) + at(x1, y1) * fx
        return top * (1 - fy) + bottom * fy
    }

    companion object {
        private const val STRIP_ROWS = 128
        private const val PARALLEL_STRIPS = 4
    }
}
