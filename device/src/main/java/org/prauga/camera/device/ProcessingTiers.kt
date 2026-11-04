/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.device

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureResult
import org.prauga.camera.capture.models.HardwareLevel
import org.prauga.camera.capture.models.ProcessingTier

object ProcessingTiers {
    data class Inputs(
        val raw: Boolean,
        val manualSensor: Boolean,
        val readSensorSettings: Boolean,
        val burstCapture: Boolean,
        val rawFps: Double?,
        val yuvFps: Double?,
        val noiseProfile: Boolean,
        val shadingMap: Boolean,
        val realtimeTimestamps: Boolean,
        val aeLock: Boolean,
    )

    fun tier(inputs: Inputs) =
        when {
            inputs.raw &&
                inputs.manualSensor &&
                inputs.readSensorSettings &&
                inputs.burstCapture &&
                (inputs.rawFps ?: 0.0) >= 20.0 &&
                inputs.noiseProfile &&
                inputs.shadingMap &&
                inputs.realtimeTimestamps -> ProcessingTier.A

            (inputs.yuvFps ?: 0.0) >= 10.0 && (inputs.manualSensor || inputs.aeLock) ->
                ProcessingTier.B

            else -> ProcessingTier.C
        }

    fun inputs(characteristics: CameraCharacteristics): Inputs {
        val capabilities =
            characteristics
                .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.toSet()
                .orEmpty()
        val resultKeys = characteristics.availableCaptureResultKeys.map { it.name }.toSet()
        val streamConfigurationMap =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        fun maxFps(format: Int) =
            streamConfigurationMap
                ?.getOutputSizes(format)
                ?.maxByOrNull { it.width.toLong() * it.height }
                ?.let { streamConfigurationMap.getOutputMinFrameDuration(format, it) }
                ?.takeIf { it > 0 }
                ?.let { 1e9 / it }

        return Inputs(
            raw = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW in capabilities,
            manualSensor =
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capabilities,
            readSensorSettings =
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS in capabilities,
            burstCapture =
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE in capabilities,
            rawFps = maxFps(ImageFormat.RAW_SENSOR),
            yuvFps = maxFps(ImageFormat.YUV_420_888),
            noiseProfile = CaptureResult.SENSOR_NOISE_PROFILE.name in resultKeys,
            shadingMap =
                characteristics
                    .get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES)
                    ?.contains(CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON) == true,
            realtimeTimestamps =
                characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) ==
                    CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME,
            aeLock = characteristics.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true,
        )
    }

    fun hardwareLevel(characteristics: CameraCharacteristics) =
        when (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> HardwareLevel.LIMITED
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> HardwareLevel.FULL
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> HardwareLevel.LEVEL_3
            CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> HardwareLevel.EXTERNAL
            else -> HardwareLevel.LEGACY
        }
}
