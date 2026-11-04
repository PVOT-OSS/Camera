/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camerax

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.MirrorMode
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.video.QualitySelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.Executor
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import org.prauga.camera.capture.models.Camera
import org.prauga.camera.capture.models.CameraConfiguration
import org.prauga.camera.capture.models.CameraFacing
import org.prauga.camera.capture.models.ExtensionMode
import org.prauga.camera.capture.models.FlashMode
import org.prauga.camera.capture.models.PhotoCaptureMode
import org.prauga.camera.capture.models.PhotoOutputFormat
import org.prauga.camera.capture.models.VideoMirrorMode
import org.prauga.camera.capture.models.VideoStabilizationMode

@OptIn(ExperimentalCamera2Interop::class, ExperimentalZeroShutterLag::class)
class CameraXBinder(
    private val cameraController: LifecycleCameraController,
    private val analyzerExecutor: Executor,
    private val qrImageAnalyzer: ImageAnalysis.Analyzer,
    private val extensionCameraSelector: (Camera, ExtensionMode) -> CameraSelector,
) {
    fun bind(cameraConfiguration: CameraConfiguration, lifecycleOwner: LifecycleOwner) {
        // Unbind previous use cases
        cameraController.unbind()

        // If the current camera doesn't support the selected camera mode, give up
        require(cameraConfiguration.camera.supportsCameraMode(cameraConfiguration.cameraMode)) {
            "Selected camera mode (${
                cameraConfiguration.cameraMode
            }) not supported by camera ${cameraConfiguration.camera.cameraId}"
        }

        // Make sure the extension mode is supported
        require(
            cameraConfiguration.camera.supportsExtensionMode(cameraConfiguration.extensionMode)
        ) {
            "Selected extension mode (${
                cameraConfiguration.extensionMode
            }) not supported by camera ${cameraConfiguration.camera.cameraId}"
        }

        // Initialize the use case we want and set its properties
        val cameraUseCases =
            when (cameraConfiguration) {
                is CameraConfiguration.Photo -> {
                    require(
                        cameraConfiguration.photoCaptureMode != PhotoCaptureMode.ZERO_SHUTTER_LAG ||
                            cameraConfiguration.camera.supportsZsl
                    ) {
                        "Requested ZSL capture mode when the camera doesn't support it"
                    }

                    require(
                        cameraConfiguration.camera.supportedPhotoOutputFormats.contains(
                            cameraConfiguration.photoOutputFormat
                        )
                    ) {
                        "Selected photo output format (${
                        cameraConfiguration.photoOutputFormat
                    } not supported by camera ${cameraConfiguration.camera.cameraId})"
                    }

                    cameraController.imageCaptureMode =
                        cameraConfiguration.photoCaptureMode.toCameraXCaptureMode()

                    cameraController.imageOutputFormat =
                        when (cameraConfiguration.photoOutputFormat) {
                            PhotoOutputFormat.JPEG -> ImageCapture.OUTPUT_FORMAT_JPEG
                            PhotoOutputFormat.JPEG_ULTRA_HDR ->
                                ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR
                            PhotoOutputFormat.RAW -> ImageCapture.OUTPUT_FORMAT_RAW
                            PhotoOutputFormat.RAW_JPEG -> ImageCapture.OUTPUT_FORMAT_RAW_JPEG
                        }

                    cameraController.imageCaptureResolutionSelector =
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(
                                AspectRatioStrategy(
                                    cameraConfiguration.photoAspectRatio.toCameraXAspectRatio(),
                                    AspectRatioStrategy.FALLBACK_RULE_AUTO,
                                )
                            )
                            .setAllowedResolutionMode(
                                if (cameraConfiguration.enableHighResolution) {
                                    ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
                                } else {
                                    ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION
                                }
                            )
                            .build()

                    CameraController.IMAGE_CAPTURE
                }

                is CameraConfiguration.Video -> {
                    // Check whether or not the video quality is supported
                    val videoQualityInfo =
                        cameraConfiguration.camera.supportedVideoQualities[
                                cameraConfiguration.videoQuality]
                            ?: error("Video quality not supported by the camera")

                    require(
                        videoQualityInfo.supportedFrameRates.contains(
                            cameraConfiguration.videoFrameRate
                        )
                    ) {
                        "Video frame rate not supported with the requested video quality"
                    }

                    require(
                        videoQualityInfo.supportedDynamicRanges.contains(
                            cameraConfiguration.videoDynamicRange
                        )
                    ) {
                        "Video dynamic range not supported with the requested video quality"
                    }

                    // Set the quality
                    cameraController.videoCaptureQualitySelector =
                        QualitySelector.from(cameraConfiguration.videoQuality.toCameraXQuality())

                    // Set the dynamic range
                    cameraController.videoCaptureDynamicRange =
                        cameraConfiguration.videoDynamicRange.toCameraXDynamicRange()

                    // Set video mirror mode
                    cameraController.videoCaptureMirrorMode =
                        when (cameraConfiguration.videoMirrorMode) {
                            VideoMirrorMode.OFF -> MirrorMode.MIRROR_MODE_OFF
                            VideoMirrorMode.ON -> MirrorMode.MIRROR_MODE_ON
                            VideoMirrorMode.ON_FFC_ONLY ->
                                when (cameraConfiguration.camera.cameraFacing) {
                                    CameraFacing.FRONT -> MirrorMode.MIRROR_MODE_ON
                                    else -> MirrorMode.MIRROR_MODE_OFF
                                }
                        }

                    CameraController.VIDEO_CAPTURE
                }

                is CameraConfiguration.Qr -> {
                    cameraController.setImageAnalysisAnalyzer(analyzerExecutor, qrImageAnalyzer)

                    CameraController.IMAGE_ANALYSIS
                }
            }

        // Get the camera selector
        val cameraSelector =
            extensionCameraSelector(cameraConfiguration.camera, cameraConfiguration.extensionMode)

        // Workaround: We cannot set flash mode to screen with a non front facing camera.
        // VM will set the correct value later on
        if (
            cameraConfiguration.camera.cameraFacing != CameraFacing.FRONT &&
                cameraController.flashMode == FlashMode.SCREEN
        ) {
            cameraController.flashMode = FlashMode.OFF
        }

        // Bind use cases to camera
        cameraController.cameraSelector = cameraSelector
        cameraController.setEnabledUseCases(cameraUseCases)

        // Bind camera controller to lifecycle
        cameraController.bindToLifecycle(lifecycleOwner)

        // Wait for camera to be ready
        lifecycleOwner.lifecycleScope.launch {
            cameraController.initializationFuture.await()

            applyCamera2Options(cameraConfiguration)
        }
    }

    private fun applyCamera2Options(cameraConfiguration: CameraConfiguration) {
        val camera2CameraControl =
            cameraController.camera2CameraControl
                ?: run {
                    Log.wtf(LOG_TAG, "Camera2CameraControl not available even with camera ready?")
                    return
                }

        val camera2Options = cameraConfiguration.camera2Options

        // Set Camera2 CaptureRequest options
        camera2CameraControl.setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setFrameRate(
                    when (cameraConfiguration) {
                        is CameraConfiguration.Video -> cameraConfiguration.videoFrameRate
                        else -> null
                    }
                )
                .setVideoStabilizationMode(
                    when (cameraConfiguration) {
                        is CameraConfiguration.Video ->
                            when (cameraConfiguration.enableVideoStabilization) {
                                true -> VideoStabilizationMode.getMode(cameraConfiguration.camera)
                                false -> null
                            }

                        else -> null
                    } ?: VideoStabilizationMode.OFF
                )
                .setEdgeMode(camera2Options.edgeMode)
                .setNoiseReductionMode(camera2Options.noiseReductionMode)
                .setShadingMode(camera2Options.shadingMode)
                .setColorCorrectionAberrationMode(camera2Options.colorCorrectionAberrationMode)
                .setDistortionCorrectionMode(camera2Options.distortionCorrectionMode)
                .setHotPixelMode(camera2Options.hotPixelMode)
                .build()
        )
    }

    companion object {
        private val LOG_TAG = CameraXBinder::class.simpleName!!
    }
}
