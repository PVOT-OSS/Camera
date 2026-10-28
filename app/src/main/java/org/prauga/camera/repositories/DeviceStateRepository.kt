/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.repositories

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.view.OrientationEventListener
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import org.prauga.camera.ext.broadcastReceiverFlow
import org.prauga.camera.ext.locationFlow
import org.prauga.camera.ext.thermalStatusFlow
import org.prauga.camera.models.ThermalStatus

class DeviceStateRepository(private val context: Context) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    val orientation = callbackFlow {
        val orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                trySend(orientation)
            }
        }

        orientationEventListener.enable()

        awaitClose {
            orientationEventListener.disable()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        powerManager.thermalStatusFlow().mapLatest { thermalStatus ->
            when (thermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
                else -> error("Unknown thermal status: $thermalStatus")
            }
        }
    } else {
        flowOf(ThermalStatus.NONE)
    }

    @Suppress("MissingPermission")
    val location = locationManager.locationFlow(
        LocationRequestCompat.Builder(1000)
            .setMinUpdateDistanceMeters(1f)
            .setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
            .build()
    )

    val batteryIntent = context.broadcastReceiverFlow(
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    )
        .onStart { emit(null) }
}
