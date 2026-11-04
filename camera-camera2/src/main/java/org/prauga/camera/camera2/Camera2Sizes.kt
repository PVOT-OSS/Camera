/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import kotlin.math.abs
import org.prauga.camera.capture.models.PhotoAspectRatio

object Camera2Sizes {
    private const val MAX_PREVIEW_AREA = 1920 * 1440

    fun previewSize(characteristics: CameraCharacteristics, photoAspectRatio: PhotoAspectRatio) =
        choose(
            streamConfigurationMap(characteristics).getOutputSizes(SurfaceTexture::class.java),
            photoAspectRatio,
            MAX_PREVIEW_AREA,
        )

    fun jpegSize(
        characteristics: CameraCharacteristics,
        photoAspectRatio: PhotoAspectRatio,
        format: Int = ImageFormat.JPEG,
        highResolution: Boolean = false,
    ): Size {
        val streamConfigurationMap = streamConfigurationMap(characteristics)
        val outputSizes: Array<Size> = streamConfigurationMap.getOutputSizes(format) ?: arrayOf()
        val highResolutionSizes: Array<Size> =
            when (highResolution) {
                true -> streamConfigurationMap.getHighResolutionOutputSizes(format) ?: arrayOf()
                false -> arrayOf()
            }
        val sizes = outputSizes + highResolutionSizes

        return choose(sizes, photoAspectRatio, Int.MAX_VALUE)
    }

    private fun streamConfigurationMap(characteristics: CameraCharacteristics) =
        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: error("No stream configuration map")

    private fun choose(sizes: Array<Size>, photoAspectRatio: PhotoAspectRatio, maxArea: Int): Size {
        val index = chooseIndex(sizes.map { it.width to it.height }, photoAspectRatio, maxArea)
        return sizes[index]
    }

    fun chooseIndex(
        sizes: List<Pair<Int, Int>>,
        photoAspectRatio: PhotoAspectRatio,
        maxArea: Int,
    ): Int {
        require(sizes.isNotEmpty()) { "No output sizes" }

        val ratio =
            when (photoAspectRatio) {
                PhotoAspectRatio.RATIO_4_3 -> 4f / 3f
                PhotoAspectRatio.RATIO_16_9 -> 16f / 9f
            }

        val candidates =
            sizes
                .withIndex()
                .filter { (_, size) -> size.first.toLong() * size.second <= maxArea }
                .ifEmpty { sizes.withIndex().toList() }

        val matching =
            candidates
                .filter { (_, size) ->
                    abs(
                        maxOf(size.first, size.second).toFloat() / minOf(size.first, size.second) -
                            ratio
                    ) < 0.01f
                }
                .ifEmpty { candidates }

        return matching.maxBy { (_, size) -> size.first.toLong() * size.second }.index
    }

    fun fitCenterScale(
        viewWidth: Int,
        viewHeight: Int,
        bufferWidth: Int,
        bufferHeight: Int,
        sensorOrientation: Int,
    ): Pair<Float, Float> {
        if (viewWidth <= 0 || viewHeight <= 0) {
            return 1f to 1f
        }

        val rotated = sensorOrientation % 180 != 0
        val contentWidth = if (rotated) bufferHeight else bufferWidth
        val contentHeight = if (rotated) bufferWidth else bufferHeight

        val scale = minOf(viewWidth.toFloat() / contentWidth, viewHeight.toFloat() / contentHeight)

        return (contentWidth * scale / viewWidth) to (contentHeight * scale / viewHeight)
    }
}
