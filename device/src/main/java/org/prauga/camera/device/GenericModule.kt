/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.device

class GenericModule : DeviceModule {
    override val id = "generic"
    override val layer = DeviceModule.Layer.GENERIC

    override fun matches(device: DeviceInfo) = true
}
