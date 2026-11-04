/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.device

import android.os.Build

data class DeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val socManufacturer: String?,
    val socModel: String?,
    val sdk: Int,
) {
    companion object {
        fun current() =
            DeviceInfo(
                manufacturer = Build.MANUFACTURER,
                brand = Build.BRAND,
                model = Build.MODEL,
                device = Build.DEVICE,
                socManufacturer =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Build.SOC_MANUFACTURER
                    } else {
                        null
                    },
                socModel =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Build.SOC_MODEL
                    } else {
                        null
                    },
                sdk = Build.VERSION.SDK_INT,
            )
    }
}
