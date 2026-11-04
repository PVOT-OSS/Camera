/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.di

import android.content.Context
import android.location.Location
import androidx.camera.core.ImageAnalysis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.prauga.camera.camerax.CameraXDevices
import org.prauga.camera.camerax.CameraXSession
import org.prauga.camera.capture.CameraDevices
import org.prauga.camera.capture.CameraSession
import org.prauga.camera.capture.CaptureSounds
import org.prauga.camera.capture.CaptureStateMachine
import org.prauga.camera.capture.PhotoCaptureStatus
import org.prauga.camera.capture.models.CameraFacing
import org.prauga.camera.device.DeviceInfo
import org.prauga.camera.device.DeviceResolver
import org.prauga.camera.device.GenericModule
import org.prauga.camera.repositories.DeviceStateRepository
import org.prauga.camera.repositories.MediaRepository
import org.prauga.camera.repositories.PreferencesRepository

class AppContainer(
    private val context: Context,
    coroutineScope: CoroutineScope,
) {
    private val device by lazy {
        DeviceResolver(listOf(GenericModule())).resolve(DeviceInfo.current())
    }

    private val cameraXDevices by lazy { CameraXDevices(context, coroutineScope, device) }

    val cameraDevices: CameraDevices get() = cameraXDevices
    val deviceStateRepository by lazy { DeviceStateRepository(context) }
    val mediaRepository by lazy { MediaRepository(context) }
    val preferencesRepository by lazy { PreferencesRepository(context, coroutineScope) }

    fun createCameraSession(
        scope: CoroutineScope,
        captureSounds: CaptureSounds,
        captureStateMachine: CaptureStateMachine,
        inSingleCaptureMode: StateFlow<Boolean>,
        qrImageAnalyzer: ImageAnalysis.Analyzer,
        location: () -> Location?,
        cameraFacing: () -> CameraFacing,
        photoFfcMirror: () -> Boolean,
        onPhotoCaptureStatus: (PhotoCaptureStatus) -> Unit,
    ): CameraSession = CameraXSession(
        context,
        scope,
        captureSounds,
        captureStateMachine,
        inSingleCaptureMode,
        qrImageAnalyzer,
        cameraXDevices::getExtensionEnabledCameraSelector,
        location = location,
        cameraFacing = cameraFacing,
        photoFfcMirror = photoFfcMirror,
        onPhotoCaptureStatus = onPhotoCaptureStatus,
        onNewPicture = mediaRepository::broadcastNewPicture,
        onNewVideo = mediaRepository::broadcastNewVideo,
    )
}
