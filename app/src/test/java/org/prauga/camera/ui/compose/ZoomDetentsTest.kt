/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomDetentsTest {
    private val stops = listOf(0.5f, 1f, 2f)
    private val range = 0.5f..10f

    @Test
    fun holdsAtAStopUntilThePinchGoesPastIt() {
        val detents = ZoomDetents()
        detents.begin(1f)

        val (held, _) = detents.scale(1.05f, stops, range)
        assertEquals(1f, held)

        val (released, reached) = detents.scale(1.05f, stops, range)
        assertEquals(1.1025f, released, 0.0001f)
        assertFalse(reached)
    }

    @Test
    fun ticksOnceWhenItReachesAStop() {
        val detents = ZoomDetents()
        detents.begin(1.6f)

        val (snapped, reached) = detents.scale(1.2f, stops, range)
        assertEquals(2f, snapped)
        assertTrue(reached)

        val (stillSnapped, reachedAgain) = detents.scale(1.01f, stops, range)
        assertEquals(2f, stillSnapped)
        assertFalse(reachedAgain)
    }

    @Test
    fun staysWithinTheRange() {
        val detents = ZoomDetents()
        detents.begin(8f)

        val (zoomRatio, _) = detents.scale(4f, stops, range)
        assertEquals(10f, zoomRatio)
    }
}
