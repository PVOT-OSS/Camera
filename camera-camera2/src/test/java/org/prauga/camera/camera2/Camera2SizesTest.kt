/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import org.junit.Assert.assertEquals
import org.junit.Test
import org.prauga.camera.capture.models.PhotoAspectRatio

class Camera2SizesTest {
    private val sizes = listOf(
        4080 to 3072,
        4000 to 3000,
        3840 to 2160,
        1920 to 1440,
        1920 to 1080,
        1280 to 720,
        640 to 480,
    )

    @Test
    fun picksTheLargestSizeWithTheAspectRatio() {
        assertEquals(0, Camera2Sizes.chooseIndex(sizes, PhotoAspectRatio.RATIO_4_3, Int.MAX_VALUE))
        assertEquals(2, Camera2Sizes.chooseIndex(sizes, PhotoAspectRatio.RATIO_16_9, Int.MAX_VALUE))
    }

    @Test
    fun respectsTheMaximumArea() {
        assertEquals(3, Camera2Sizes.chooseIndex(sizes, PhotoAspectRatio.RATIO_4_3, 1920 * 1440))
        assertEquals(4, Camera2Sizes.chooseIndex(sizes, PhotoAspectRatio.RATIO_16_9, 1920 * 1440))
    }

    @Test
    fun fallsBackToTheLargestSize() {
        assertEquals(0, Camera2Sizes.chooseIndex(listOf(4000 to 2000, 100 to 100), PhotoAspectRatio.RATIO_4_3, Int.MAX_VALUE))
    }

    @Test
    fun fitsARotatedBufferInsideTheView() {
        val (scaleX, scaleY) = Camera2Sizes.fitCenterScale(1080, 1723, 1920, 1440, 90)
        assertEquals(1f, scaleX, 0.001f)
        assertEquals(1440f / 1723f, scaleY, 0.001f)
    }

    @Test
    fun keepsAnUnrotatedBuffer() {
        val (scaleX, scaleY) = Camera2Sizes.fitCenterScale(1000, 1000, 1000, 500, 0)
        assertEquals(1f, scaleX, 0.001f)
        assertEquals(0.5f, scaleY, 0.001f)
    }
}
