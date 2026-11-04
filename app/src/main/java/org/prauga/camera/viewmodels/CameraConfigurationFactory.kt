/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.viewmodels

import org.prauga.camera.models.Camera
import org.prauga.camera.models.CameraConfiguration
import org.prauga.camera.models.CameraMode
import org.prauga.camera.models.ColorCorrectionAberrationMode
import org.prauga.camera.models.DistortionCorrectionMode
import org.prauga.camera.models.EdgeMode
import org.prauga.camera.models.HotPixelMode
import org.prauga.camera.models.NoiseReductionMode
import org.prauga.camera.models.PhotoCaptureMode
import org.prauga.camera.models.PhotoOutputFormat
import org.prauga.camera.models.ShadingMode
import org.prauga.camera.repositories.PreferencesRepository

class CameraConfigurationFactory(
    private val preferencesRepository: PreferencesRepository,
    private val inSingleCaptureMode: () -> Boolean,
) {
    fun create(
        camera: Camera,
        cameraMode: CameraMode,
    ) = when (cameraMode) {
        CameraMode.PHOTO -> {
            // Enable ZSL when requested by the user and supported by the camera
            val photoCaptureMode = when (
                val photoCaptureMode = preferencesRepository.photoCaptureMode.value
            ) {
                PhotoCaptureMode.MINIMIZE_LATENCY -> when (
                    preferencesRepository.enableZsl.value && camera.supportsZsl
                ) {
                    true -> PhotoCaptureMode.ZERO_SHUTTER_LAG
                    false -> PhotoCaptureMode.MINIMIZE_LATENCY
                }

                else -> photoCaptureMode
            }

            val usePhotoJpegUltraHdr = preferencesRepository.usePhotoJpegUltraHdr.value
            val enableRawImageCapture = preferencesRepository.enableRawImageCapture.value
            val disableJpegWithRaw = preferencesRepository.disableJpegWithRaw.value
            val photoOutputFormat = when {
                inSingleCaptureMode() -> PhotoOutputFormat.JPEG

                enableRawImageCapture
                        && disableJpegWithRaw
                        && camera.supportedPhotoOutputFormats.contains(
                    PhotoOutputFormat.RAW
                ) -> PhotoOutputFormat.RAW

                enableRawImageCapture && camera.supportedPhotoOutputFormats.contains(
                    PhotoOutputFormat.RAW_JPEG
                ) -> PhotoOutputFormat.RAW_JPEG

                usePhotoJpegUltraHdr && camera.supportedPhotoOutputFormats.contains(
                    PhotoOutputFormat.JPEG_ULTRA_HDR
                ) -> PhotoOutputFormat.JPEG_ULTRA_HDR

                else -> PhotoOutputFormat.JPEG
            }

            CameraConfiguration.Photo(
                camera = camera,
                extensionMode = preferencesRepository.photoEffect.value,
                camera2Options = buildPhotoCamera2Options(camera, photoCaptureMode),
                photoCaptureMode = photoCaptureMode,
                photoAspectRatio = preferencesRepository.photoAspectRatio.value,
                enableHighResolution = true,
                photoOutputFormat = photoOutputFormat,
            )
        }

        CameraMode.VIDEO -> {
            val videoQuality = preferencesRepository.videoQuality.value.takeIf {
                camera.supportedVideoQualities.contains(it)
            } ?: camera.supportedVideoQualities.keys.firstOrNull() ?: error(
                "Camera ${camera.cameraId} does not support any video quality"
            )

            val videoQualityInfo = camera.supportedVideoQualities[videoQuality] ?: error(
                "Camera ${camera.cameraId} does not support video quality $videoQuality"
            )

            val videoFrameRate = preferencesRepository.videoFrameRate.value.takeIf {
                videoQualityInfo.supportedFrameRates.contains(it)
            } ?: videoQualityInfo.supportedFrameRates.firstOrNull()

            val videoDynamicRange = preferencesRepository.videoDynamicRange.value.takeIf {
                videoQualityInfo.supportedDynamicRanges.contains(it)
            } ?: videoQualityInfo.supportedDynamicRanges.firstOrNull() ?: error(
                "No video dynamic range supported by the camera"
            )

            CameraConfiguration.Video(
                camera = camera,
                camera2Options = buildVideoCamera2Options(camera),
                videoQuality = videoQuality,
                videoFrameRate = videoFrameRate,
                videoDynamicRange = videoDynamicRange,
                videoMirrorMode = preferencesRepository.videoMirrorMode.value,
                enableVideoStabilization = preferencesRepository.videoStabilization.value,
            )
        }

        CameraMode.QR -> CameraConfiguration.Qr(
            camera = camera,
        )
    }

    private fun buildPhotoCamera2Options(
        camera: Camera,
        photoCaptureMode: PhotoCaptureMode,
    ) = CameraConfiguration.Camera2Options(
        edgeMode = preferencesRepository.edgeMode.value?.takeIf {
            camera.supportedEdgeModes.contains(it) && when (
                photoCaptureMode
            ) {
                PhotoCaptureMode.ZERO_SHUTTER_LAG ->
                    EdgeMode.ALLOWED_MODES_ON_ZSL.contains(it)

                else -> true
            }
        },
        noiseReductionMode = preferencesRepository.noiseReductionMode.value?.takeIf {
            camera.supportedNoiseReductionModes.contains(it) && when (
                photoCaptureMode
            ) {
                PhotoCaptureMode.ZERO_SHUTTER_LAG ->
                    NoiseReductionMode.ALLOWED_MODES_ON_ZSL.contains(it)

                else -> true
            }
        },
        shadingMode = preferencesRepository.shadingMode.value?.takeIf {
            camera.supportedShadingModes.contains(it) && when (
                photoCaptureMode
            ) {
                PhotoCaptureMode.ZERO_SHUTTER_LAG ->
                    ShadingMode.ALLOWED_MODES_ON_ZSL.contains(it)

                else -> true
            }
        },
        colorCorrectionAberrationMode = preferencesRepository.colorCorrectionAberrationMode.value?.takeIf {
            camera.supportedColorCorrectionAberrationModes.contains(it) && when (
                photoCaptureMode
            ) {
                PhotoCaptureMode.ZERO_SHUTTER_LAG ->
                    ColorCorrectionAberrationMode.ALLOWED_MODES_ON_ZSL.contains(it)

                else -> true
            }
        },
        distortionCorrectionMode = preferencesRepository.distortionCorrectionMode.value?.takeIf {
            camera.supportedDistortionCorrectionModes.contains(it) && when (
                photoCaptureMode
            ) {
                PhotoCaptureMode.ZERO_SHUTTER_LAG ->
                    DistortionCorrectionMode.ALLOWED_MODES_ON_ZSL.contains(it)

                else -> true
            }
        },
        hotPixelMode = preferencesRepository.hotPixelMode.value?.takeIf {
            camera.supportedHotPixelModes.contains(it) && when (
                photoCaptureMode
            ) {
                PhotoCaptureMode.ZERO_SHUTTER_LAG ->
                    HotPixelMode.ALLOWED_MODES_ON_ZSL.contains(it)

                else -> true
            }
        },
    )

    private fun buildVideoCamera2Options(
        camera: Camera,
    ) = CameraConfiguration.Camera2Options(
        edgeMode = preferencesRepository.edgeMode.value?.takeIf {
            camera.supportedEdgeModes.contains(it)
                    && EdgeMode.ALLOWED_MODES_ON_VIDEO_MODE.contains(it)
        },
        noiseReductionMode = preferencesRepository.noiseReductionMode.value?.takeIf {
            camera.supportedNoiseReductionModes.contains(it)
                    && NoiseReductionMode.ALLOWED_MODES_ON_VIDEO_MODE.contains(it)
        },
        shadingMode = preferencesRepository.shadingMode.value?.takeIf {
            camera.supportedShadingModes.contains(it)
                    && ShadingMode.ALLOWED_MODES_ON_VIDEO_MODE.contains(it)
        },
        colorCorrectionAberrationMode = preferencesRepository.colorCorrectionAberrationMode.value?.takeIf {
            camera.supportedColorCorrectionAberrationModes.contains(it)
                    && ColorCorrectionAberrationMode.ALLOWED_MODES_ON_VIDEO_MODE.contains(it)
        },
        distortionCorrectionMode = preferencesRepository.distortionCorrectionMode.value?.takeIf {
            camera.supportedDistortionCorrectionModes.contains(it)
                    && DistortionCorrectionMode.ALLOWED_MODES_ON_VIDEO_MODE.contains(it)
        },
        hotPixelMode = preferencesRepository.hotPixelMode.value?.takeIf {
            camera.supportedHotPixelModes.contains(it)
                    && HotPixelMode.ALLOWED_MODES_ON_VIDEO_MODE.contains(it)
        },
    )
}
