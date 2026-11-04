/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.device

import android.hardware.camera2.CameraCharacteristics

interface DeviceModule {
    val id: String
    val layer: Layer

    fun matches(device: DeviceInfo): Boolean

    fun includesCamera(cameraId: String, default: Boolean) = default

    fun capabilities(
        base: CameraCapabilities,
        characteristics: (String) -> CameraCharacteristics,
    ) = base

    enum class Layer {
        GENERIC,
        SOC,
        OEM,
        DEVICE,
    }
}
