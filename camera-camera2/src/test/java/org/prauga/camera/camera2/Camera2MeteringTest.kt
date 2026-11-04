/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Camera2MeteringTest {
    @Test
    fun mapsTheBackCameraTopLeft() {
        val (x, y) = Camera2Metering.viewToSensor(0f, 0f, 90, mirrored = false)
        assertEquals(0f, x)
        assertEquals(1f, y)
    }

    @Test
    fun mapsTheCenterToTheCenter() {
        listOf(0, 90, 180, 270).forEach { orientation ->
            listOf(false, true).forEach { mirrored ->
                val (x, y) = Camera2Metering.viewToSensor(0.5f, 0.5f, orientation, mirrored)
                assertEquals(0.5f, x)
                assertEquals(0.5f, y)
            }
        }
    }

    @Test
    fun mapsTheMirroredFrontCamera() {
        val (x, y) = Camera2Metering.viewToSensor(0f, 0f, 270, mirrored = true)
        assertEquals(1f, x)
        assertEquals(1f, y)
    }

    @Test
    fun keepsTheRegionInsideTheSensor() {
        assertArrayEquals(
            intArrayOf(0, 0, 300, 300),
            Camera2Metering.regionBounds(0f, 0f, 4000, 3000),
        )
        assertArrayEquals(
            intArrayOf(3700, 2700, 300, 300),
            Camera2Metering.regionBounds(1f, 1f, 4000, 3000),
        )
        assertArrayEquals(
            intArrayOf(1850, 1350, 300, 300),
            Camera2Metering.regionBounds(0.5f, 0.5f, 4000, 3000),
        )
    }
}
