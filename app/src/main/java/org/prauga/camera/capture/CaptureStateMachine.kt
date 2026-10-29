/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.prauga.camera.models.CameraState

class CaptureStateMachine {
    private val _state = MutableStateFlow(CameraState.IDLE)
    val state = _state.asStateFlow()

    val isIdle: Boolean
        get() = _state.value == CameraState.IDLE

    fun startCountdown() {
        _state.value = CameraState.COUNTDOWN
    }

    fun endCountdown() {
        _state.value = CameraState.IDLE
    }

    fun startPhoto(): Boolean {
        if (_state.value == CameraState.TAKING_PHOTO) {
            return false
        }

        _state.value = CameraState.TAKING_PHOTO
        return true
    }

    fun endPhoto() {
        _state.value = CameraState.IDLE
    }

    fun prepareRecording(): Boolean {
        if (_state.value != CameraState.IDLE) {
            return false
        }

        _state.value = CameraState.PRE_RECORDING_VIDEO
        return true
    }

    fun onRecordingStarted() {
        _state.value = CameraState.RECORDING_VIDEO
    }

    fun onRecordingPaused() {
        _state.value = CameraState.RECORDING_VIDEO_PAUSED
    }

    fun onRecordingResumed() {
        _state.value = CameraState.RECORDING_VIDEO
    }

    fun endRecording() {
        _state.value = CameraState.IDLE
    }
}
