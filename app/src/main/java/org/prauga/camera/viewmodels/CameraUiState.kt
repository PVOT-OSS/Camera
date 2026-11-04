/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.viewmodels

import android.net.Uri
import androidx.camera.core.ZoomState
import org.prauga.camera.models.Camera
import org.prauga.camera.models.CameraMode
import org.prauga.camera.models.CameraState
import org.prauga.camera.models.FlashMode
import org.prauga.camera.models.FrameRate
import org.prauga.camera.models.GridMode
import org.prauga.camera.models.IslandItem
import org.prauga.camera.models.PhotoAspectRatio
import org.prauga.camera.models.Rotation
import org.prauga.camera.models.ThermalStatus
import org.prauga.camera.models.TimerMode
import org.prauga.camera.models.VideoDynamicRange
import org.prauga.camera.models.VideoQuality

data class CameraUiState(
    val cameraMode: CameraMode,
    val cameraState: CameraState,
    val inSingleCaptureMode: Boolean,
    val screenRotation: Rotation,
    val lenses: Pair<Camera?, List<Camera>>,
    val capturedMedia: List<Uri>,
    val supportedFlashModes: Set<FlashMode>,
    val flashMode: FlashMode,
    val isFlashButtonEnabled: Boolean,
    val gridMode: GridMode,
    val timerMode: TimerMode,
    val levelerEnabled: Boolean,
    val fullScreenBrightness: Boolean,
    val thermalStatus: ThermalStatus,
    val zoomState: ZoomState?,
    val exposureCompensationRangeToLevel: Pair<ClosedRange<Int>, Float>?,
    val isShutterButtonEnabled: Boolean,
    val photoAspectRatio: PhotoAspectRatio,
    val photoEffect: Int,
    val isPhotoEffectButtonVisible: Boolean,
    val videoQuality: VideoQuality,
    val isVideoQualityButtonEnabled: Boolean,
    val videoFrameRate: FrameRate?,
    val isVideoFrameRateButtonEnabled: Boolean,
    val videoDynamicRange: VideoDynamicRange,
    val isVideoDynamicRangeButtonEnabled: Boolean,
    val videoMicMode: Boolean,
    val isVideoMicButtonEnabled: Boolean,
    val videoRecordingDuration: Long,
    val canFlipCamera: Boolean,
    val islandItems: List<IslandItem>,
)
