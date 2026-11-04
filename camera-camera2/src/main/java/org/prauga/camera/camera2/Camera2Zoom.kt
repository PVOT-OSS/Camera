/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import org.prauga.camera.capture.ZoomInfo

object Camera2Zoom {
    fun zoomRatioRange(characteristics: CameraCharacteristics): ClosedFloatingPointRange<Float> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let {
                return it.lower..it.upper
            }
        }

        val maxDigitalZoom =
            characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f

        return 1f..maxDigitalZoom
    }

    fun zoomInfo(characteristics: CameraCharacteristics, zoomRatio: Float) =
        zoomInfo(zoomRatioRange(characteristics), zoomRatio)

    fun zoomInfo(range: ClosedFloatingPointRange<Float>, zoomRatio: Float): ZoomInfo {
        val ratio = zoomRatio.coerceIn(range)

        return ZoomInfo(
            zoomRatio = ratio,
            minZoomRatio = range.start,
            maxZoomRatio = range.endInclusive,
            linearZoom = linearZoom(range, ratio),
        )
    }

    fun linearZoom(range: ClosedFloatingPointRange<Float>, zoomRatio: Float): Float {
        if (range.start == range.endInclusive) {
            return 0f
        }

        return (1f / range.start - 1f / zoomRatio) / (1f / range.start - 1f / range.endInclusive)
    }

    fun zoomRatio(range: ClosedFloatingPointRange<Float>, linearZoom: Float): Float {
        if (range.start == range.endInclusive) {
            return range.start
        }

        val inverse =
            1f / range.start -
                linearZoom.coerceIn(0f, 1f) * (1f / range.start - 1f / range.endInclusive)

        return (1f / inverse).coerceIn(range)
    }
}
