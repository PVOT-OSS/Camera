/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.device

import android.hardware.camera2.CameraCharacteristics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prauga.camera.capture.models.HardwareLevel
import org.prauga.camera.capture.models.ProcessingTier

class DeviceResolverTest {
    private val device =
        DeviceInfo(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 9",
            device = "tokay",
            socManufacturer = "Google",
            socModel = "Tensor G4",
            sdk = 37,
        )

    private class TestModule(
        override val id: String,
        override val layer: DeviceModule.Layer,
        private val matchingDevice: String? = null,
        private val includedCameraId: String? = null,
        private val zoomRatio: Float? = null,
    ) : DeviceModule {
        override fun matches(device: DeviceInfo) =
            matchingDevice == null || device.device == matchingDevice

        override fun includesCamera(cameraId: String, default: Boolean) =
            if (cameraId == includedCameraId) true else default

        override fun capabilities(
            base: CameraCapabilities,
            characteristics: (String) -> CameraCharacteristics,
        ) =
            zoomRatio?.let {
                base.copy(
                    logicalZoomRatios = base.logicalZoomRatios.toSortedMap().apply { put(it, it) }
                )
            } ?: base
    }

    private val base =
        CameraCapabilities(
            cameraId = "0",
            hardwareLevel = HardwareLevel.FULL,
            processingTier = ProcessingTier.A,
            logicalZoomRatios = sortedMapOf(1f to 1f),
            additionalVideoFrameRates = mapOf(),
        )

    @Test
    fun resolvesOnlyMatchingModulesInLayerOrder() {
        val resolved =
            DeviceResolver(
                    listOf(
                        TestModule("pixel-9", DeviceModule.Layer.DEVICE, matchingDevice = "tokay"),
                        TestModule("other", DeviceModule.Layer.DEVICE, matchingDevice = "frankel"),
                        TestModule("tensor", DeviceModule.Layer.SOC),
                        GenericModule(),
                    )
                )
                .resolve(device)

        assertEquals(listOf("generic", "tensor", "pixel-9"), resolved.modules.map { it.id })
    }

    @Test
    fun modulesWithoutOverridesKeepTheDefaults() {
        val resolved =
            DeviceResolver(listOf(TestModule("generic", DeviceModule.Layer.GENERIC)))
                .resolve(device)

        assertTrue(resolved.includesCamera("0", default = true))
        assertFalse(resolved.includesCamera("2", default = false))
        assertEquals(base, resolved.capabilities(base) { error("not needed") })
    }

    @Test
    fun laterLayersBuildOnEarlierOnes() {
        val resolved =
            DeviceResolver(
                    listOf(
                        TestModule("generic", DeviceModule.Layer.GENERIC),
                        TestModule(
                            "soc",
                            DeviceModule.Layer.SOC,
                            includedCameraId = "2",
                            zoomRatio = 0.5f,
                        ),
                        TestModule("device", DeviceModule.Layer.DEVICE, zoomRatio = 2f),
                    )
                )
                .resolve(device)

        assertTrue(resolved.includesCamera("2", default = false))
        assertEquals(
            listOf(0.5f, 1f, 2f),
            resolved.capabilities(base) { error("not needed") }.logicalZoomRatios.keys.toList(),
        )
    }
}
