/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture.models

import java.util.SortedMap
import kotlin.reflect.safeCast

/**
 * Class representing a device camera.
 */
class Camera(
    /**
     * Camera2's camera ID.
     */
    val cameraId: String,
    /**
     * The [CameraFacing] of this camera.
     */
    val cameraFacing: CameraFacing,
    val hardwareLevel: HardwareLevel,
    val processingTier: ProcessingTier,
    val exposureCompensationRange: ClosedRange<Int>,
    val intrinsicZoomRatio: Float,
    val logicalZoomRatios: SortedMap<Float, Float>,
    val supportedExtensionModes: Set<ExtensionMode>,
    val supportedPhotoOutputFormats: List<PhotoOutputFormat>,
    val supportedVideoQualities: Map<VideoQuality, VideoQualityInfo>,
    val supportedVideoStabilizationModes: List<VideoStabilizationMode>,
    val supportsZsl: Boolean,
    val supportedEdgeModes: Set<EdgeMode>,
    val supportedNoiseReductionModes: Set<NoiseReductionMode>,
    val supportedShadingModes: Set<ShadingMode>,
    val supportedColorCorrectionAberrationModes: Set<ColorCorrectionAberrationMode>,
    val supportedDistortionCorrectionModes: Set<DistortionCorrectionMode>,
    val supportedHotPixelModes: Set<HotPixelMode>,
    /**
     * The supported flash modes of this camera.
     * Keep in mind that support also depends on the camera mode used.
     */
    val supportedFlashModes: Set<FlashMode>,
) {
    override fun equals(other: Any?) = this::class.safeCast(other)?.let {
        this.cameraId == it.cameraId
    } ?: false

    override fun hashCode() = this::class.qualifiedName.hashCode() + cameraId.hashCode()

    fun supportsExtensionMode(extensionMode: ExtensionMode): Boolean {
        return supportedExtensionModes.contains(extensionMode)
    }

    fun supportsCameraMode(cameraMode: CameraMode): Boolean {
        return when (cameraMode) {
            CameraMode.VIDEO -> supportedVideoQualities.isNotEmpty()
            else -> true
        }
    }
}
