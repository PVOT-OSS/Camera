/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camerax

import org.junit.Assert.assertEquals
import org.junit.Test
import org.prauga.camera.capture.models.ExtensionMode
import org.prauga.camera.capture.models.PhotoAspectRatio
import org.prauga.camera.capture.models.PhotoCaptureMode
import org.prauga.camera.capture.models.VideoDynamicRange

class CameraXTypesTest {
    @Test
    fun videoDynamicRangeRoundTrips() {
        VideoDynamicRange.entries.forEach {
            assertEquals(it, it.toCameraXDynamicRange().toVideoDynamicRange())
        }
    }

    @Test
    fun extensionModesAreDistinct() {
        assertEquals(
            ExtensionMode.entries.size,
            ExtensionMode.entries.map { it.toCameraXExtensionMode() }.toSet().size,
        )
    }

    @Test
    fun photoAspectRatiosAreDistinct() {
        assertEquals(
            PhotoAspectRatio.entries.size,
            PhotoAspectRatio.entries.map { it.toCameraXAspectRatio() }.toSet().size,
        )
    }

    @Test
    fun photoCaptureModesAreDistinct() {
        assertEquals(
            PhotoCaptureMode.entries.size,
            PhotoCaptureMode.entries.map { it.toCameraXCaptureMode() }.toSet().size,
        )
    }
}
