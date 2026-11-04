/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camerax

import androidx.annotation.OptIn
import androidx.camera.core.AspectRatio
import androidx.camera.core.DynamicRange
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.core.ImageCapture
import androidx.camera.extensions.ExtensionMode as CameraXExtensionMode
import androidx.camera.core.ZoomState
import androidx.camera.video.Quality
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.TapToFocusInfo
import org.prauga.camera.capture.FocusEvent
import org.prauga.camera.capture.RecordingEvent
import org.prauga.camera.capture.ZoomInfo
import org.prauga.camera.models.ExtensionMode
import org.prauga.camera.models.PhotoAspectRatio
import org.prauga.camera.models.PhotoCaptureMode
import org.prauga.camera.models.VideoDynamicRange
import org.prauga.camera.models.VideoQuality

fun ExtensionMode.toCameraXExtensionMode() = when (this) {
    ExtensionMode.NONE -> CameraXExtensionMode.NONE
    ExtensionMode.BOKEH -> CameraXExtensionMode.BOKEH
    ExtensionMode.HDR -> CameraXExtensionMode.HDR
    ExtensionMode.NIGHT -> CameraXExtensionMode.NIGHT
    ExtensionMode.FACE_RETOUCH -> CameraXExtensionMode.FACE_RETOUCH
    ExtensionMode.AUTO -> CameraXExtensionMode.AUTO
}

fun PhotoAspectRatio.toCameraXAspectRatio() = when (this) {
    PhotoAspectRatio.RATIO_4_3 -> AspectRatio.RATIO_4_3
    PhotoAspectRatio.RATIO_16_9 -> AspectRatio.RATIO_16_9
}

@OptIn(ExperimentalZeroShutterLag::class)
fun PhotoCaptureMode.toCameraXCaptureMode() = when (this) {
    PhotoCaptureMode.MAXIMIZE_QUALITY -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
    PhotoCaptureMode.MINIMIZE_LATENCY -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
    PhotoCaptureMode.ZERO_SHUTTER_LAG -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
}

fun VideoDynamicRange.toCameraXDynamicRange(): DynamicRange = when (this) {
    VideoDynamicRange.SDR -> DynamicRange.SDR
    VideoDynamicRange.HLG_10_BIT -> DynamicRange.HLG_10_BIT
    VideoDynamicRange.HDR10_10_BIT -> DynamicRange.HDR10_10_BIT
    VideoDynamicRange.HDR10_PLUS_10_BIT -> DynamicRange.HDR10_PLUS_10_BIT
    VideoDynamicRange.DOLBY_VISION_10_BIT -> DynamicRange.DOLBY_VISION_10_BIT
    VideoDynamicRange.DOLBY_VISION_8_BIT -> DynamicRange.DOLBY_VISION_8_BIT
}

fun DynamicRange.toVideoDynamicRange() = VideoDynamicRange.entries.first {
    it.toCameraXDynamicRange() == this
}

fun VideoRecordEvent.toRecordingEvent(): RecordingEvent? = when (this) {
    is VideoRecordEvent.Start -> RecordingEvent.Started
    is VideoRecordEvent.Pause -> RecordingEvent.Paused
    is VideoRecordEvent.Resume -> RecordingEvent.Resumed
    is VideoRecordEvent.Status -> RecordingEvent.Status(recordingStats.recordedDurationNanos)
    is VideoRecordEvent.Finalize -> RecordingEvent.Finalized(
        outputUri = outputResults.outputUri,
        hasValidData = error != VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA,
    )

    else -> null
}

fun TapToFocusInfo.toFocusEvent() = FocusEvent(
    focusState = when (focusState) {
        CameraController.TAP_TO_FOCUS_STARTED -> FocusEvent.State.STARTED
        CameraController.TAP_TO_FOCUS_FOCUSED -> FocusEvent.State.FOCUSED
        CameraController.TAP_TO_FOCUS_NOT_FOCUSED -> FocusEvent.State.NOT_FOCUSED
        CameraController.TAP_TO_FOCUS_FAILED -> FocusEvent.State.FAILED
        else -> FocusEvent.State.NOT_STARTED
    },
    tapPoint = tapPoint,
)

fun ZoomState.toZoomInfo() = ZoomInfo(
    zoomRatio = zoomRatio,
    minZoomRatio = minZoomRatio,
    maxZoomRatio = maxZoomRatio,
    linearZoom = linearZoom,
)

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
