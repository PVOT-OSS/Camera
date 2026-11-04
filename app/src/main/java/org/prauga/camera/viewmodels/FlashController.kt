/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.viewmodels

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.prauga.camera.ext.next
import org.prauga.camera.models.CameraConfiguration
import org.prauga.camera.models.CameraMode
import org.prauga.camera.models.CameraState
import org.prauga.camera.models.Event
import org.prauga.camera.models.FlashMode
import org.prauga.camera.repositories.PreferencesRepository

class FlashController(
    private val scope: CoroutineScope,
    private val cameraConfiguration: Flow<CameraConfiguration>,
    private val currentConfiguration: () -> CameraConfiguration?,
    private val cameraState: StateFlow<CameraState>,
    private val cameraMode: StateFlow<CameraMode>,
    private val preferencesRepository: PreferencesRepository,
    private val emitEvent: (Event) -> Unit,
) {
    /**
     * The current list of supported [FlashMode]s.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val supportedFlashModes = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            cameraConfiguration.cameraMode.supportedFlashModes.intersect(
                cameraConfiguration.camera.supportedFlashModes
            )
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope,
            started = SharingStarted.Eagerly,
            initialValue = setOf()
        )

    /**
     * Whether force torch mode should be enabled.
     */
    private val forceTorch = MutableStateFlow(false)

    /**
     * Whether QR torch mode should be enabled.
     */
    private val qrFlashMode = MutableStateFlow(FlashMode.OFF)

    /**
     * The user selected flash mode.
     */
    private val wantedFlashMode = combine(
        cameraConfiguration,
        preferencesRepository.photoFlashMode,
        preferencesRepository.videoFlashMode,
        qrFlashMode,
    ) { cameraConfiguration, photoFlashMode, videoFlashMode, qrFlashMode ->
        when (cameraConfiguration.cameraMode) {
            CameraMode.PHOTO -> photoFlashMode
            CameraMode.VIDEO -> videoFlashMode
            CameraMode.QR -> qrFlashMode
        }
    }
        .flowOn(Dispatchers.IO)
        .shareIn(
            scope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    /**
     * The flash mode that will actually be used.
     */
    val flashMode = combine(
        supportedFlashModes,
        wantedFlashMode,
        forceTorch,
        cameraConfiguration,
    ) { supportedFlashModes, wantedFlashMode, forceTorch, cameraConfiguration ->
        val flashMode = wantedFlashMode.takeIf { it in supportedFlashModes } ?: FlashMode.OFF

        val shouldForceTorch = forceTorch
                && cameraConfiguration.cameraMode == CameraMode.PHOTO
                && FlashMode.TORCH in cameraConfiguration.camera.supportedFlashModes

        when (shouldForceTorch) {
            true -> FlashMode.TORCH
            false -> flashMode
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope,
            started = SharingStarted.Eagerly,
            initialValue = FlashMode.OFF
        )

    /**
     * Whether the user should be able to cycle between different flash modes.
     */
    val isFlashButtonEnabled = combine(
        cameraState,
        cameraMode,
    ) { cameraState, cameraMode ->
        cameraState == CameraState.IDLE || cameraMode == CameraMode.VIDEO
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    /**
     * Cycle flash mode
     *
     * @param forceTorch Whether force torch mode should be toggled
     * @return true if the flash mode was changed, false otherwise
     */
    fun cycleFlashMode(forceTorch: Boolean): Boolean {
        val cameraConfiguration = currentConfiguration() ?: return false

        // Long-press is supported only on photo mode and if torch mode is available
        val forceTorchAvailable = cameraConfiguration.cameraMode == CameraMode.PHOTO
                && cameraConfiguration.camera.supportedFlashModes.contains(FlashMode.TORCH)
        if (forceTorch && !forceTorchAvailable) {
            this.forceTorch.value = false

            return false
        }

        when (forceTorch) {
            true -> {
                this.forceTorch.value = this.forceTorch.value.not()
            }

            else -> when (this.forceTorch.value) {
                true -> {
                    // Just disable torch mode
                    this.forceTorch.value = false
                }

                false -> supportedFlashModes.value.toList().next(flashMode.value)?.let {
                    when (cameraConfiguration.cameraMode) {
                        CameraMode.PHOTO -> preferencesRepository.photoFlashMode.value = it
                        CameraMode.VIDEO -> preferencesRepository.videoFlashMode.value = it
                        CameraMode.QR -> qrFlashMode.value = it
                    }
                }
            }
        }

        // Check if we should show the force torch suggestion
        if (!preferencesRepository.forceTorchHelpShown.value) {
            if (forceTorch) {
                // The user figured it out by themself
                preferencesRepository.forceTorchHelpShown.value = true
            } else {
                emitEvent(Event.ShowForceTorchHelp)
            }
        }

        return true
    }

    /**
     * Set whether the force torch help has been shown.
     */
    fun setForceModeHelpShown(forceTorchHelpShown: Boolean) {
        preferencesRepository.forceTorchHelpShown.value = forceTorchHelpShown
    }
}
