/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import org.junit.Assert.assertEquals
import org.junit.Test

class Camera2ZoomTest {
    private val range = 0.5f..8f

    @Test
    fun linearZoomSpansTheRange() {
        assertEquals(0f, Camera2Zoom.linearZoom(range, 0.5f), 0.0001f)
        assertEquals(1f, Camera2Zoom.linearZoom(range, 8f), 0.0001f)
    }

    @Test
    fun linearZoomRoundTrips() {
        listOf(0.5f, 1f, 2f, 5f, 8f).forEach {
            assertEquals(it, Camera2Zoom.zoomRatio(range, Camera2Zoom.linearZoom(range, it)), 0.001f)
        }
    }

    @Test
    fun zoomInfoClampsTheRatio() {
        val zoomInfo = Camera2Zoom.zoomInfo(range, 20f)
        assertEquals(8f, zoomInfo.zoomRatio)
        assertEquals(1f, zoomInfo.linearZoom, 0.0001f)
    }

    @Test
    fun fixedRangeHasNoLinearZoom() {
        assertEquals(0f, Camera2Zoom.linearZoom(1f..1f, 1f))
        assertEquals(1f, Camera2Zoom.zoomRatio(1f..1f, 0.5f))
    }
}
