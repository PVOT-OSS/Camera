/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camerax

import androidx.camera.core.AspectRatio
import androidx.camera.video.Quality
import org.prauga.camera.models.PhotoAspectRatio
import org.prauga.camera.models.VideoQuality

fun PhotoAspectRatio.toCameraXAspectRatio() = when (this) {
    PhotoAspectRatio.RATIO_4_3 -> AspectRatio.RATIO_4_3
    PhotoAspectRatio.RATIO_16_9 -> AspectRatio.RATIO_16_9
}

fun VideoQuality.toCameraXQuality(): Quality = when (this) {
    VideoQuality.SD -> Quality.SD
    VideoQuality.HD -> Quality.HD
    VideoQuality.FHD -> Quality.FHD
    VideoQuality.QHD -> Quality.QHD
    VideoQuality.UHD -> Quality.UHD
}

fun Quality.toVideoQuality(): VideoQuality? = when (this) {
    Quality.SD -> VideoQuality.SD
    Quality.HD -> VideoQuality.HD
    Quality.FHD -> VideoQuality.FHD
    Quality.QHD -> VideoQuality.QHD
    Quality.UHD -> VideoQuality.UHD
    else -> null
}
