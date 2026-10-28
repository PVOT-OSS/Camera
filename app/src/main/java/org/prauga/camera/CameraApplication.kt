/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera

import android.app.Application
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.MainScope
import org.prauga.camera.repositories.CameraRepository
import org.prauga.camera.repositories.DeviceStateRepository
import org.prauga.camera.repositories.MediaRepository
import org.prauga.camera.repositories.PreferencesRepository

class CameraApplication : Application() {
    private val coroutineScope = MainScope()

    val cameraRepository by lazy { CameraRepository(this, coroutineScope) }
    val deviceStateRepository by lazy { DeviceStateRepository(this) }
    val mediaRepository by lazy { MediaRepository(this) }
    val preferencesRepository by lazy { PreferencesRepository(this, coroutineScope) }

    override fun onCreate() {
        super.onCreate()

        // Observe dynamic colors changes
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
