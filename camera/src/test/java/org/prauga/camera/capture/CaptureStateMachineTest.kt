/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prauga.camera.capture.models.CameraState

class CaptureStateMachineTest {
    private val stateMachine = CaptureStateMachine()

    @Test
    fun startsIdle() {
        assertEquals(CameraState.IDLE, stateMachine.state.value)
        assertTrue(stateMachine.isIdle)
    }

    @Test
    fun photoCannotStartTwice() {
        assertTrue(stateMachine.startPhoto())
        assertFalse(stateMachine.startPhoto())
        assertEquals(CameraState.TAKING_PHOTO, stateMachine.state.value)

        stateMachine.endPhoto()
        assertTrue(stateMachine.isIdle)
    }

    @Test
    fun countdownLeavesIdle() {
        stateMachine.startCountdown()
        assertEquals(CameraState.COUNTDOWN, stateMachine.state.value)
        assertFalse(stateMachine.isIdle)

        stateMachine.endCountdown()
        assertTrue(stateMachine.isIdle)
    }

    @Test
    fun recordingOnlyStartsFromIdle() {
        stateMachine.startPhoto()
        assertFalse(stateMachine.prepareRecording())

        stateMachine.endPhoto()
        stateMachine.startCountdown()
        assertFalse(stateMachine.prepareRecording())

        stateMachine.endCountdown()
        assertTrue(stateMachine.prepareRecording())
        assertEquals(CameraState.PRE_RECORDING_VIDEO, stateMachine.state.value)
    }

    @Test
    fun recordingLifecycle() {
        assertTrue(stateMachine.prepareRecording())

        stateMachine.onRecordingStarted()
        assertEquals(CameraState.RECORDING_VIDEO, stateMachine.state.value)
        assertTrue(stateMachine.state.value.isRecordingVideo)

        stateMachine.onRecordingPaused()
        assertEquals(CameraState.RECORDING_VIDEO_PAUSED, stateMachine.state.value)
        assertTrue(stateMachine.state.value.isRecordingVideo)

        stateMachine.onRecordingResumed()
        assertEquals(CameraState.RECORDING_VIDEO, stateMachine.state.value)

        stateMachine.endRecording()
        assertTrue(stateMachine.isIdle)
        assertFalse(stateMachine.state.value.isRecordingVideo)
    }
}
