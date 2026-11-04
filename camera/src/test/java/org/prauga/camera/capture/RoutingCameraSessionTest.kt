/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prauga.camera.capture.models.Camera
import org.prauga.camera.capture.models.CameraConfiguration
import org.prauga.camera.capture.models.CameraFacing
import org.prauga.camera.capture.models.ExtensionMode
import org.prauga.camera.capture.models.FlashMode
import org.prauga.camera.capture.models.HardwareLevel
import org.prauga.camera.capture.models.PhotoAspectRatio
import org.prauga.camera.capture.models.PhotoCaptureMode
import org.prauga.camera.capture.models.PhotoOutputFormat
import org.prauga.camera.capture.models.ProcessingTier

class RoutingCameraSessionTest {
    private val primary = FakeCameraSession()
    private val photo = FakeCameraSession()
    private val session =
        RoutingCameraSession(CoroutineScope(Dispatchers.Unconfined), primary, photo) {
            it is CameraConfiguration.Photo
        }

    private val lifecycleOwner =
        object : LifecycleOwner {
            override val lifecycle: Lifecycle
                get() = error("Not used by the fake sessions")
        }

    private val camera =
        Camera(
            cameraId = "0",
            cameraFacing = CameraFacing.BACK,
            hardwareLevel = HardwareLevel.FULL,
            processingTier = ProcessingTier.A,
            exposureCompensationRange = -12..12,
            exposureCompensationStep = 1f / 6,
            intrinsicZoomRatio = 1f,
            logicalZoomRatios = sortedMapOf(1f to 1f),
            supportedExtensionModes = setOf(ExtensionMode.NONE),
            supportedPhotoOutputFormats = listOf(PhotoOutputFormat.JPEG),
            supportedVideoQualities = mapOf(),
            supportedVideoStabilizationModes = listOf(),
            supportsZsl = false,
            supportedEdgeModes = setOf(),
            supportedNoiseReductionModes = setOf(),
            supportedShadingModes = setOf(),
            supportedColorCorrectionAberrationModes = setOf(),
            supportedDistortionCorrectionModes = setOf(),
            supportedHotPixelModes = setOf(),
            supportedFlashModes = setOf(FlashMode.OFF),
        )

    private val photoConfiguration =
        CameraConfiguration.Photo(
            camera = camera,
            extensionMode = ExtensionMode.NONE,
            camera2Options = CameraConfiguration.Camera2Options.DEFAULT,
            photoCaptureMode = PhotoCaptureMode.MINIMIZE_LATENCY,
            photoAspectRatio = PhotoAspectRatio.RATIO_4_3,
            enableHighResolution = true,
            photoOutputFormat = PhotoOutputFormat.JPEG,
        )

    private val qrConfiguration = CameraConfiguration.Qr(camera)

    @Test
    fun startsOnThePrimarySession() {
        assertSame(primary, session.active)
    }

    @Test
    fun routesPhotoModeToThePhotoSession() {
        session.configure(photoConfiguration, lifecycleOwner)

        assertSame(photo, session.active)
        assertEquals(listOf("release"), primary.calls)
        assertEquals(listOf("configure"), photo.calls)
    }

    @Test
    fun goesBackToThePrimarySession() {
        session.configure(photoConfiguration, lifecycleOwner)
        session.configure(qrConfiguration, lifecycleOwner)

        assertSame(primary, session.active)
        assertEquals(listOf("configure", "release"), photo.calls)
        assertEquals(qrConfiguration, primary.configuration)
    }

    @Test
    fun replaysTheControlsToTheNewSession() {
        session.setFlashMode(FlashMode.ON)
        session.setExposureCompensationIndex(3)

        session.configure(photoConfiguration, lifecycleOwner)

        assertEquals(FlashMode.ON, photo.lastFlashMode)
        assertEquals(3, photo.lastExposureCompensationIndex)
    }

    @Test
    fun previewStreamingFollowsTheActiveSession() {
        primary.previewStreaming.value = true
        assertTrue(session.previewStreaming.value)

        session.configure(photoConfiguration, lifecycleOwner)
        assertFalse(session.previewStreaming.value)

        photo.previewStreaming.value = true
        assertTrue(session.previewStreaming.value)
    }

    @Test
    fun fallsBackToThePrimarySession() {
        session.setFlashMode(FlashMode.ON)
        session.configure(photoConfiguration, lifecycleOwner)

        session.fallBackToPrimary()

        assertSame(primary, session.active)
        assertEquals(listOf("configure", "release"), photo.calls)
        assertEquals(photoConfiguration, primary.configuration)
        assertEquals(FlashMode.ON, primary.lastFlashMode)
    }

    @Test
    fun ignoresAFallBackWhenAlreadyPrimary() {
        session.fallBackToPrimary()

        assertTrue(primary.calls.isEmpty())
    }

    @Test
    fun videoCallsStayOnThePrimarySession() {
        session.configure(photoConfiguration, lifecycleOwner)
        session.setAudioEnabled(true)

        assertTrue("setAudioEnabled" in primary.calls)
    }
}
