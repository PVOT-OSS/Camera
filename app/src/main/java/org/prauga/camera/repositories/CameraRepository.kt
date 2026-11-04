/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.repositories

import android.Manifest
import android.content.Context
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.asFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import org.prauga.camera.camerax.cameraFromCameraX
import org.prauga.camera.camerax.toCameraError
import org.prauga.camera.camerax.toCameraXExtensionMode
import org.prauga.camera.ext.permissionGranted
import org.prauga.camera.models.Camera
import org.prauga.camera.models.CameraFacing
import org.prauga.camera.models.ExtensionMode

/**
 * Repository that provides camera devices.
 */
@AndroidXOptIn(ExperimentalCamera2Interop::class, ExperimentalLensFacing::class)
class CameraRepository(
    private val context: Context,
    coroutineScope: CoroutineScope,
) {
    /**
     * CameraX's [ProcessCameraProvider].
     */
    private val cameraProvider by lazy {
        requireCameraPermission()
        ProcessCameraProvider.getInstance(context).get()
    }

    /**
     * CameraX's [ExtensionsManager].
     */
    private val extensionsManager: ExtensionsManager by lazy {
        ExtensionsManager.getInstanceAsync(context, cameraProvider).get()
    }

    /**
     * List of internal cameras. These should never change.
     */
    private val internalCameras by lazy {
        cameraProvider.availableCameraInfos
            .filter { cameraXCameraInfo ->
                cameraXCameraInfo.lensFacing != CameraSelector.LENS_FACING_EXTERNAL
                        && cameraXCameraInfo.isInternalCameraAllowed()
            }
            .mapToCamera()
    }

    val mainBackCamera by lazy {
        internalCameras.firstOrNull { camera ->
            camera.cameraFacing == CameraFacing.BACK
        }
    }

    val mainFrontCamera by lazy {
        internalCameras.firstOrNull { camera ->
            camera.cameraFacing == CameraFacing.FRONT
        }
    }

    /**
     * List of external cameras. These will change once the user connects or disconnects a camera.
     */
    private val externalCameras = suspend {
        cameraProvider.availableCameraInfos
            .filter { cameraXCameraInfo ->
                cameraXCameraInfo.lensFacing == CameraSelector.LENS_FACING_EXTERNAL
            }
            .mapToCamera()
    }.asFlow()
        .flowOn(Dispatchers.IO)
        .shareIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val cameras = externalCameras.mapLatest { externalCameras ->
        internalCameras + externalCameras
    }
        .flowOn(Dispatchers.IO)
        .shareIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1,
        )

    /**
     * @see ExtensionsManager.getExtensionEnabledCameraSelector
     */
    fun getExtensionEnabledCameraSelector(
        camera: Camera,
        mode: ExtensionMode,
    ): CameraSelector = extensionsManager.getExtensionEnabledCameraSelector(
        cameraInfo(camera).cameraSelector, mode.toCameraXExtensionMode()
    )

    fun cameraErrors(camera: Camera) = cameraInfo(camera).cameraState.asFlow().map {
        it.error?.toCameraError()
    }

    private fun cameraInfo(camera: Camera) = cameraProvider.availableCameraInfos.first {
        Camera2CameraInfo.from(it).cameraId == camera.cameraId
    }

    private fun CameraInfo.isInternalCameraAllowed() =
        Camera2CameraInfo.from(this).cameraId in mainCameraIds

    private fun List<CameraInfo>.mapToCamera() = map { it.toCamera() }.sortedBy { it.cameraId }

    private fun CameraInfo.toCamera() = cameraFromCameraX(
        this,
        extensionsManager,
    )

    private fun requireCameraPermission() = require(
        context.permissionGranted(Manifest.permission.CAMERA)
    ) { "Camera permission not granted" }

    companion object {
        /**
         * List of main camera IDs. These should never be excluded.
         */
        private val mainCameraIds = setOf(
            "0",
            "1",
        )
    }
}
