/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.device

import android.hardware.camera2.CameraCharacteristics
import android.os.Build

class GenericModule : DeviceModule {
    override val id = "generic"
    override val layer = DeviceModule.Layer.GENERIC

    override fun matches(device: DeviceInfo) = true

    override fun capabilities(
        base: CameraCapabilities,
        characteristics: (String) -> CameraCharacteristics,
    ): CameraCapabilities {
        val logicalCharacteristics = characteristics(base.cameraId)
        val tiered =
            base.copy(
                hardwareLevel = ProcessingTiers.hardwareLevel(logicalCharacteristics),
                processingTier =
                    ProcessingTiers.tier(ProcessingTiers.inputs(logicalCharacteristics)),
            )

        return lensZoomRatios(tiered, logicalCharacteristics, characteristics)
    }

    private fun lensZoomRatios(
        base: CameraCapabilities,
        logicalCharacteristics: CameraCharacteristics,
        characteristics: (String) -> CameraCharacteristics,
    ): CameraCapabilities {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return base
        }

        val physicalCameraIds = logicalCharacteristics.physicalCameraIds
        if (physicalCameraIds.size < 2) {
            return base
        }

        val zoomRatioRange =
            logicalCharacteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                ?: return base
        val logicalFieldOfViewScale = fieldOfViewScale(logicalCharacteristics) ?: return base

        val lensZoomRatios =
            physicalCameraIds.mapNotNull { physicalCameraId ->
                fieldOfViewScale(characteristics(physicalCameraId))?.let {
                    it / logicalFieldOfViewScale
                }
            }

        val logicalZoomRatios =
            LensZoomRatios.of(lensZoomRatios, zoomRatioRange.lower, zoomRatioRange.upper)

        return when (logicalZoomRatios.size > 1) {
            true -> base.copy(logicalZoomRatios = logicalZoomRatios)
            false -> base
        }
    }

    private fun fieldOfViewScale(characteristics: CameraCharacteristics): Float? {
        val focalLength =
            characteristics
                .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull() ?: return null
        val physicalSize =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        val pixelArraySize =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: return null
        val activeArraySize =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null

        val activeWidth = physicalSize.width * activeArraySize.width() / pixelArraySize.width

        return focalLength / activeWidth
    }
}
