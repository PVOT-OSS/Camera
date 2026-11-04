/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.device

import org.junit.Assert.assertEquals
import org.junit.Test

class LensZoomRatiosTest {
    @Test
    fun approximatesLensZoomRatios() {
        assertEquals(0.5f, LensZoomRatios.approximate(0.5557f))
        assertEquals(0.9f, LensZoomRatios.approximate(0.896f))
        assertEquals(2f, LensZoomRatios.approximate(2.04f))
        assertEquals(4f, LensZoomRatios.approximate(4.13f))
    }

    @Test
    fun keepsOneTimesExact() {
        assertEquals(
            mapOf(0.5f to 0.5557f, 1f to 1f, 4f to 4.13f),
            LensZoomRatios.of(listOf(0.5557f, 1.003f, 4.13f), 0.5557f, 20f),
        )
    }

    @Test
    fun clampsToTheZoomRange() {
        assertEquals(
            mapOf(0.6f to 0.6f, 1f to 1f, 8f to 8f),
            LensZoomRatios.of(listOf(0.52f, 10f), 0.6f, 8f),
        )
    }

    @Test
    fun singleLensHasNoButtons() {
        assertEquals(mapOf(1f to 1f), LensZoomRatios.of(listOf(1f), 1f, 8f))
    }
}
