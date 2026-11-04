/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.device

import org.prauga.camera.capture.models.FrameRate
import org.prauga.camera.capture.models.VideoQuality
import java.util.SortedMap

data class CameraCapabilities(
    val cameraId: String,
    val logicalZoomRatios: SortedMap<Float, Float>,
    val additionalVideoFrameRates: Map<VideoQuality, Map<FrameRate, Boolean>>,
)
