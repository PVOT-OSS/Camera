/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.viewmodels

import android.content.Intent
import android.os.BatteryManager
import org.prauga.camera.capture.models.CameraConfiguration
import org.prauga.camera.capture.models.PhotoOutputFormat
import org.prauga.camera.models.IslandItem
import org.prauga.camera.models.ThermalStatus

fun islandItemsOf(
    cameraConfiguration: CameraConfiguration,
    videoMicMode: Boolean,
    batteryIntent: Intent?,
    thermalStatus: ThermalStatus,
) = buildList {
    when (thermalStatus) {
        ThermalStatus.NONE,
        ThermalStatus.LIGHT -> Unit

        ThermalStatus.MODERATE,
        ThermalStatus.SEVERE -> add(
            IslandItem.ThermalThrottling(false)
        )

        ThermalStatus.CRITICAL,
        ThermalStatus.EMERGENCY,
        ThermalStatus.SHUTDOWN -> add(
            IslandItem.ThermalThrottling(true)
        )
    }

    batteryIntent?.let {
        val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        val percentage = level.times(100).div(scale)

        if (percentage <= 15) {
            add(
                IslandItem.LowBattery(
                    isCharging = it.getIntExtra(
                        BatteryManager.EXTRA_PLUGGED,
                        0,
                    ) != 0,
                )
            )
        }

        when (cameraConfiguration) {
            is CameraConfiguration.Photo -> {
                when (cameraConfiguration.photoOutputFormat) {
                    PhotoOutputFormat.JPEG -> {}

                    PhotoOutputFormat.JPEG_ULTRA_HDR -> add(
                        IslandItem.PhotoJpegUltraHdr
                    )

                    PhotoOutputFormat.RAW -> add(
                        IslandItem.PhotoRawEnabled(false)
                    )

                    PhotoOutputFormat.RAW_JPEG -> add(
                        IslandItem.PhotoRawEnabled(true)
                    )
                }
            }

            is CameraConfiguration.Video -> {
                if (!videoMicMode) {
                    add(IslandItem.VideoMicMuted)
                }
            }

            is CameraConfiguration.Qr -> {}
        }
    }
}
