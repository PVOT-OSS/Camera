/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.models

/**
 * Camera configuration. Should only contain information about the session that can only be
 * configured at binding time.
 */
sealed interface CameraConfiguration {
    /**
     * Camera2 interop options.
     *
     * @param edgeMode The [EdgeMode] to use
     * @param noiseReductionMode The [NoiseReductionMode] to use
     * @param shadingMode The [ShadingMode] to use
     * @param colorCorrectionAberrationMode The [ColorCorrectionAberrationMode] to use
     * @param distortionCorrectionMode The [DistortionCorrectionMode] to use
     * @param hotPixelMode The [HotPixelMode] to use
     */
    data class Camera2Options(
        val edgeMode: EdgeMode?,
        val noiseReductionMode: NoiseReductionMode?,
        val shadingMode: ShadingMode?,
        val colorCorrectionAberrationMode: ColorCorrectionAberrationMode?,
        val distortionCorrectionMode: DistortionCorrectionMode?,
        val hotPixelMode: HotPixelMode?,
    ) {
        companion object {
            /**
             * Default instance that will not alter Camera2 options.
             */
            val DEFAULT = Camera2Options(
                edgeMode = null,
                noiseReductionMode = null,
                shadingMode = null,
                colorCorrectionAberrationMode = null,
                distortionCorrectionMode = null,
                hotPixelMode = null,
            )
        }
    }

    /**
     * The [Camera] to use.
     */
    val camera: Camera

    /**
     * The [CameraMode] to use.
     */
    val cameraMode: CameraMode

    /**
     * The [ExtensionMode] to use.
     */
    val extensionMode: ExtensionMode

    /**
     * The [Camera2Options] to use.
     */
    val camera2Options: Camera2Options

    /**
     * Photo mode configuration.
     *
     * @param photoAspectRatio The [PhotoAspectRatio] to use
     * @param enableHighResolution Whether to enable high resolution or not
     * @param photoCaptureMode The [PhotoCaptureMode] to use
     * @param photoOutputFormat The [PhotoOutputFormat] to use
     */
    data class Photo(
        override val camera: Camera,
        override val extensionMode: ExtensionMode,
        override val camera2Options: Camera2Options,
        val photoCaptureMode: PhotoCaptureMode,
        val photoAspectRatio: PhotoAspectRatio,
        val enableHighResolution: Boolean,
        val photoOutputFormat: PhotoOutputFormat,
    ) : CameraConfiguration {
        override val cameraMode = CameraMode.PHOTO
    }

    /**
     * Video mode configuration.
     *
     * @param videoQuality The [VideoQuality] to use
     * @param videoFrameRate The [FrameRate] to use
     * @param videoDynamicRange The [VideoDynamicRange] to use
     * @param videoMirrorMode The [VideoMirrorMode] to use
     * @param enableVideoStabilization Whether to enable video stabilization or not
     */
    data class Video(
        override val camera: Camera,
        override val camera2Options: Camera2Options,
        val videoQuality: VideoQuality,
        val videoFrameRate: FrameRate?,
        val videoDynamicRange: VideoDynamicRange,
        val videoMirrorMode: VideoMirrorMode,
        val enableVideoStabilization: Boolean,
    ) : CameraConfiguration {
        override val cameraMode = CameraMode.VIDEO
        override val extensionMode = ExtensionMode.NONE
    }

    /**
     * QR mode configuration.
     */
    data class Qr(
        override val camera: Camera,
    ) : CameraConfiguration {
        override val cameraMode = CameraMode.QR
        override val extensionMode = ExtensionMode.NONE
        override val camera2Options = Camera2Options.DEFAULT
    }
}
