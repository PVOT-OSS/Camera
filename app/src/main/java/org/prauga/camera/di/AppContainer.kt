/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.di

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageAnalysis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.prauga.camera.camera2.Camera2Health
import org.prauga.camera.camera2.Camera2Session
import org.prauga.camera.camerax.CameraXDevices
import org.prauga.camera.camerax.CameraXSession
import org.prauga.camera.capture.CameraDevices
import org.prauga.camera.capture.CameraSession
import org.prauga.camera.capture.CaptureSounds
import org.prauga.camera.capture.CaptureStateMachine
import org.prauga.camera.capture.PhotoCaptureStatus
import org.prauga.camera.capture.RoutingCameraSession
import org.prauga.camera.capture.models.CameraFacing
import org.prauga.camera.capture.models.HardwareLevel
import org.prauga.camera.device.DeviceInfo
import org.prauga.camera.device.DeviceResolver
import org.prauga.camera.device.GenericModule
import org.prauga.camera.models.CameraEngine
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

    private val camera2Health by lazy { Camera2Health(context) }
    private val mainHandler = Handler(Looper.getMainLooper())

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
    ): CameraSession {
        lateinit var session: RoutingCameraSession

        session = RoutingCameraSession(
            scope,
            CameraXSession(
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
            ),
            Camera2Session(
                context,
                scope,
                captureSounds,
                captureStateMachine,
                inSingleCaptureMode,
                location = location,
                onPhotoCaptureStatus = onPhotoCaptureStatus,
                onNewPicture = mediaRepository::broadcastNewPicture,
                onFailure = { cameraId ->
                    camera2Health.markFailed(cameraId)
                    mainHandler.post { session.fallBackToPrimary() }
                },
            ),
        ) {
            when (preferencesRepository.cameraEngine.value) {
                CameraEngine.AUTO -> it.camera.hardwareLevel >= HardwareLevel.FULL
                CameraEngine.CAMERAX -> false
                CameraEngine.CAMERA2 -> true
            }
                    && Camera2Session.supports(it)
                    && camera2Health.isHealthy(it.camera.cameraId)
        }

        return session
    }
}
