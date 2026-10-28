/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera

import android.annotation.SuppressLint
import android.app.Application
import androidx.camera.camera2.internal.CameraCompatibilityFilter
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.MainScope
import org.prauga.camera.repositories.CameraRepository
import org.prauga.camera.repositories.MediaRepository
import org.prauga.camera.repositories.OverlaysRepository
import org.prauga.camera.repositories.PreferencesRepository

class CameraApplication : Application() {
    private val coroutineScope = MainScope()

    val cameraRepository by lazy { CameraRepository(this, coroutineScope, overlaysRepository) }
    val mediaRepository by lazy { MediaRepository(this) }
    val overlaysRepository by lazy { OverlaysRepository(this) }
    val preferencesRepository by lazy { PreferencesRepository(this, coroutineScope) }

    @SuppressLint("RestrictedApi")
    override fun onCreate() {
        super.onCreate()

        // Observe dynamic colors changes
        DynamicColors.applyToActivitiesIfAvailable(this)

        // Set backward compatible camera ids
        CameraCompatibilityFilter.setBackwardCompatibleCameraIds(
            overlaysRepository.backwardCompatibleCameraIds.asList()
        )
    }
}
