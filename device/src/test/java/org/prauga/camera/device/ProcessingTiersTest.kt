/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.device

import org.junit.Assert.assertEquals
import org.junit.Test
import org.prauga.camera.capture.models.ProcessingTier

class ProcessingTiersTest {
    private val tierA =
        ProcessingTiers.Inputs(
            raw = true,
            manualSensor = true,
            readSensorSettings = true,
            burstCapture = true,
            rawFps = 30.0,
            yuvFps = 30.0,
            noiseProfile = true,
            shadingMap = true,
            realtimeTimestamps = true,
            aeLock = true,
        )

    @Test
    fun fullRawCameraIsTierA() {
        assertEquals(ProcessingTier.A, ProcessingTiers.tier(tierA))
    }

    @Test
    fun slowRawFallsToTierB() {
        assertEquals(ProcessingTier.B, ProcessingTiers.tier(tierA.copy(rawFps = 15.0)))
    }

    @Test
    fun missingCalibrationFallsToTierB() {
        assertEquals(ProcessingTier.B, ProcessingTiers.tier(tierA.copy(noiseProfile = false)))
        assertEquals(ProcessingTier.B, ProcessingTiers.tier(tierA.copy(realtimeTimestamps = false)))
    }

    @Test
    fun yuvBurstNeedsExposureControl() {
        val yuvOnly = tierA.copy(raw = false, manualSensor = false, aeLock = false)
        assertEquals(ProcessingTier.C, ProcessingTiers.tier(yuvOnly))
        assertEquals(ProcessingTier.B, ProcessingTiers.tier(yuvOnly.copy(aeLock = true)))
    }

    @Test
    fun slowYuvIsTierC() {
        assertEquals(ProcessingTier.C, ProcessingTiers.tier(tierA.copy(raw = false, yuvFps = 5.0)))
    }
}
