/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera

import android.app.Application
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.MainScope
import org.prauga.camera.di.AppContainer

class CameraApplication : Application() {
    private val coroutineScope = MainScope()

    val container by lazy { AppContainer(this, coroutineScope) }

    override fun onCreate() {
        super.onCreate()

        // Observe dynamic colors changes
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
