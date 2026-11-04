/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camerax

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.core.ImageCapture
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.video.Recorder
import androidx.core.util.toClosedRange
import java.util.SortedMap
import org.prauga.camera.capture.models.Camera
import org.prauga.camera.capture.models.CameraFacing
import org.prauga.camera.capture.models.ColorCorrectionAberrationMode
import org.prauga.camera.capture.models.DistortionCorrectionMode
import org.prauga.camera.capture.models.EdgeMode
import org.prauga.camera.capture.models.FlashMode
import org.prauga.camera.capture.models.FrameRate
import org.prauga.camera.capture.models.HotPixelMode
import org.prauga.camera.capture.models.NoiseReductionMode
import org.prauga.camera.capture.models.PhotoOutputFormat
import org.prauga.camera.capture.models.ShadingMode
import org.prauga.camera.capture.models.VideoQuality
import org.prauga.camera.capture.models.VideoQualityInfo
import org.prauga.camera.capture.models.VideoStabilizationMode

@OptIn(
    ExperimentalCamera2Interop::class,
    ExperimentalLensFacing::class,
    ExperimentalZeroShutterLag::class,
)
fun cameraFromCameraX(
    cameraInfo: CameraInfo,
    extensionsManager: ExtensionsManager,
    logicalZoomRatios: SortedMap<Float, Float>,
    additionalVideoFrameRates: Map<VideoQuality, Map<FrameRate, Boolean>>,
): Camera {
    val supportedExtensionModes = extensionsManager.getSupportedModes(cameraInfo.cameraSelector)

    /**
     * The [Camera2CameraInfo] of this camera.
     */
    val camera2CameraInfo: Camera2CameraInfo = Camera2CameraInfo.from(cameraInfo)

    val cameraId: String = camera2CameraInfo.cameraId

    val cameraFacing = when (cameraInfo.lensFacing) {
        CameraSelector.LENS_FACING_UNKNOWN -> CameraFacing.UNKNOWN
        CameraSelector.LENS_FACING_FRONT -> CameraFacing.FRONT
        CameraSelector.LENS_FACING_BACK -> CameraFacing.BACK
        CameraSelector.LENS_FACING_EXTERNAL -> CameraFacing.EXTERNAL
        else -> throw Exception("Unknown lens facing value")
    }

    val exposureCompensationRange =
        cameraInfo.exposureState.exposureCompensationRange.toClosedRange<Int>()

    val intrinsicZoomRatio = cameraInfo.intrinsicZoomRatio

    val imageCaptureCapabilities = ImageCapture.getImageCaptureCapabilities(cameraInfo)

    val supportedPhotoOutputFormats = imageCaptureCapabilities.supportedOutputFormats.map {
        when (it) {
            ImageCapture.OUTPUT_FORMAT_JPEG -> PhotoOutputFormat.JPEG
            ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR -> PhotoOutputFormat.JPEG_ULTRA_HDR
            ImageCapture.OUTPUT_FORMAT_RAW -> PhotoOutputFormat.RAW
            ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> PhotoOutputFormat.RAW_JPEG
            else -> error("Unknown CameraX output format $it")
        }
    }

    val supportedVideoFrameRates = cameraInfo.supportedFrameRateRanges.mapNotNull {
        FrameRate.fromRange(it.toClosedRange())
    }.toSet()

    val videoCapabilities = Recorder.getVideoCapabilities(cameraInfo)

    val supportedVideoDynamicRanges = videoCapabilities.supportedDynamicRanges.map {
        it.toVideoDynamicRange()
    }

    val videoQualityForDynamicRanges = supportedVideoDynamicRanges.associateWith {
        videoCapabilities.getSupportedQualities(it.toCameraXDynamicRange()).mapNotNull { quality ->
            quality.toVideoQuality()
        }
    }

    val supportedVideoQualities =
        videoQualityForDynamicRanges.values.flatten().toSet().associateWith {
            VideoQualityInfo(
                it,
                supportedVideoFrameRates.toMutableSet().apply {
                    additionalVideoFrameRates[it].orEmpty().forEach { (frameRate, remove) ->
                        if (remove) {
                            remove(frameRate)
                        } else {
                            add(frameRate)
                        }
                    }
                }.toSet(),
                videoQualityForDynamicRanges.entries.filter { dynamicRangeToQualities ->
                    dynamicRangeToQualities.value.contains(it)
                }.map { dynamicRangeToQualities -> dynamicRangeToQualities.key }.toSet()
            )
        }

    val supportedVideoStabilizationModes = buildList {
        add(VideoStabilizationMode.OFF)

        val availableVideoStabilizationModes = camera2CameraInfo.getCameraCharacteristic(
            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
        ) ?: IntArray(0)

        if (
            availableVideoStabilizationModes.contains(
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
            )
        ) {
            add(VideoStabilizationMode.ON)
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            availableVideoStabilizationModes.contains(
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
            )
        ) {
            add(VideoStabilizationMode.ON_PREVIEW)
        }
    }

    val supportsZsl = cameraInfo.isZslSupported

    val supportedEdgeModes = camera2CameraInfo.getAndMapCameraCharacteristics(
        CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES,
    ) {
        when (it) {
            CameraCharacteristics.EDGE_MODE_OFF -> EdgeMode.OFF
            CameraCharacteristics.EDGE_MODE_FAST -> EdgeMode.FAST
            CameraCharacteristics.EDGE_MODE_HIGH_QUALITY -> EdgeMode.HIGH_QUALITY
            CameraCharacteristics.EDGE_MODE_ZERO_SHUTTER_LAG -> EdgeMode.ZERO_SHUTTER_LAG
            else -> null
        }
    }

    val supportedNoiseReductionModes = camera2CameraInfo.getAndMapCameraCharacteristics(
        CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES,
    ) {
        when (it) {
            CameraCharacteristics.NOISE_REDUCTION_MODE_OFF -> NoiseReductionMode.OFF
            CameraCharacteristics.NOISE_REDUCTION_MODE_FAST -> NoiseReductionMode.FAST
            CameraCharacteristics.NOISE_REDUCTION_MODE_HIGH_QUALITY ->
                NoiseReductionMode.HIGH_QUALITY

            CameraCharacteristics.NOISE_REDUCTION_MODE_MINIMAL -> NoiseReductionMode.MINIMAL
            CameraCharacteristics.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG ->
                NoiseReductionMode.ZERO_SHUTTER_LAG

            else -> null
        }
    }

    val supportedShadingModes = camera2CameraInfo.getAndMapCameraCharacteristics(
        CameraCharacteristics.SHADING_AVAILABLE_MODES,
    ) {
        when (it) {
            CameraCharacteristics.SHADING_MODE_OFF -> ShadingMode.OFF
            CameraCharacteristics.SHADING_MODE_FAST -> ShadingMode.FAST
            CameraCharacteristics.SHADING_MODE_HIGH_QUALITY -> ShadingMode.HIGH_QUALITY
            else -> null
        }
    }

    val supportedColorCorrectionAberrationModes = camera2CameraInfo.getAndMapCameraCharacteristics(
        CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES,
    ) {
        when (it) {
            CameraCharacteristics.COLOR_CORRECTION_ABERRATION_MODE_OFF ->
                ColorCorrectionAberrationMode.OFF

            CameraCharacteristics.COLOR_CORRECTION_ABERRATION_MODE_FAST ->
                ColorCorrectionAberrationMode.FAST

            CameraCharacteristics.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY ->
                ColorCorrectionAberrationMode.HIGH_QUALITY

            else -> null
        }
    }

    val supportedDistortionCorrectionModes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        camera2CameraInfo.getAndMapCameraCharacteristics(
            CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES,
        ) {
            when (it) {
                CameraCharacteristics.DISTORTION_CORRECTION_MODE_OFF ->
                    DistortionCorrectionMode.OFF

                CameraCharacteristics.DISTORTION_CORRECTION_MODE_FAST ->
                    DistortionCorrectionMode.FAST

                CameraCharacteristics.DISTORTION_CORRECTION_MODE_HIGH_QUALITY ->
                    DistortionCorrectionMode.HIGH_QUALITY

                else -> null
            }
        }
    } else {
        setOf()
    }

    val supportedHotPixelModes = camera2CameraInfo.getAndMapCameraCharacteristics(
        CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES,
    ) {
        when (it) {
            CameraCharacteristics.HOT_PIXEL_MODE_OFF -> HotPixelMode.OFF
            CameraCharacteristics.HOT_PIXEL_MODE_FAST -> HotPixelMode.FAST
            CameraCharacteristics.HOT_PIXEL_MODE_HIGH_QUALITY -> HotPixelMode.HIGH_QUALITY
            else -> null
        }
    }

    val supportedFlashModes = buildSet {
        add(FlashMode.OFF)

        if (cameraInfo.hasFlashUnit()) {
            add(FlashMode.AUTO)
            add(FlashMode.ON)
            add(FlashMode.TORCH)
        }

        if (cameraFacing == CameraFacing.FRONT) {
            add(FlashMode.SCREEN)
        }
    }

    return Camera(
        cameraId = cameraId,
        cameraFacing = cameraFacing,
        exposureCompensationRange = exposureCompensationRange,
        intrinsicZoomRatio = intrinsicZoomRatio,
        logicalZoomRatios = logicalZoomRatios,
        supportedExtensionModes = supportedExtensionModes,
        supportedPhotoOutputFormats = supportedPhotoOutputFormats,
        supportedVideoQualities = supportedVideoQualities,
        supportedVideoStabilizationModes = supportedVideoStabilizationModes,
        supportsZsl = supportsZsl,
        supportedEdgeModes = supportedEdgeModes,
        supportedNoiseReductionModes = supportedNoiseReductionModes,
        supportedShadingModes = supportedShadingModes,
        supportedColorCorrectionAberrationModes = supportedColorCorrectionAberrationModes,
        supportedDistortionCorrectionModes = supportedDistortionCorrectionModes,
        supportedHotPixelModes = supportedHotPixelModes,
        supportedFlashModes = supportedFlashModes,
    )
}

@OptIn(ExperimentalCamera2Interop::class)
private inline fun <T : Enum<T>> Camera2CameraInfo.getAndMapCameraCharacteristics(
    key: CameraCharacteristics.Key<IntArray>,
    mapper: (Int) -> T?,
): Set<T> = getCameraCharacteristic(key)?.toSet().orEmpty().mapNotNull {
    mapper(it)
}.toSet()
