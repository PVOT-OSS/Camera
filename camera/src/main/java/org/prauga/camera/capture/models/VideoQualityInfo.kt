/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture.models

/**
 * Video [VideoQuality] info.
 *
 * @param quality The quality
 * @param supportedFrameRates The supported frame rates for this quality
 * @param supportedDynamicRanges The supported dynamic ranges for this quality
 */
data class VideoQualityInfo(
    val quality: VideoQuality,
    val supportedFrameRates: Set<FrameRate>,
    val supportedDynamicRanges: Set<VideoDynamicRange>,
)
