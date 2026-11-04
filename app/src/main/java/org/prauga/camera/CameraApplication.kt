/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera

import android.app.Application
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.MainScope
import org.prauga.camera.camerax.CameraXDevices
import org.prauga.camera.capture.CameraDevices
import org.prauga.camera.repositories.DeviceStateRepository
import org.prauga.camera.repositories.MediaRepository
import org.prauga.camera.repositories.PreferencesRepository

class CameraApplication : Application() {
    private val coroutineScope = MainScope()

    val cameraXDevices by lazy { CameraXDevices(this, coroutineScope) }
    val cameraDevices: CameraDevices get() = cameraXDevices
    val deviceStateRepository by lazy { DeviceStateRepository(this) }
    val mediaRepository by lazy { MediaRepository(this) }
    val preferencesRepository by lazy { PreferencesRepository(this, coroutineScope) }

    override fun onCreate() {
        super.onCreate()

        // Observe dynamic colors changes
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
