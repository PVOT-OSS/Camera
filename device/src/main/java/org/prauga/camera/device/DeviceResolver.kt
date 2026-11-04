/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.device

import android.hardware.camera2.CameraCharacteristics

class DeviceResolver(private val modules: List<DeviceModule>) {
    fun resolve(device: DeviceInfo) =
        ResolvedDevice(modules.filter { it.matches(device) }.sortedBy { it.layer })

    class ResolvedDevice(val modules: List<DeviceModule>) {
        fun includesCamera(cameraId: String, default: Boolean) =
            modules.fold(default) { acc, module -> module.includesCamera(cameraId, acc) }

        fun capabilities(
            base: CameraCapabilities,
            characteristics: (String) -> CameraCharacteristics,
        ) = modules.fold(base) { acc, module -> module.capabilities(acc, characteristics) }
    }
}
