/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import org.prauga.camera.CameraApplication

/**
 * Base view model for all app view models.
 */
abstract class CameraBaseViewModel(application: Application) : AndroidViewModel(application) {
    protected val container = getApplication<CameraApplication>().container

    protected val cameraDevices = container.cameraDevices
    protected val deviceStateRepository = container.deviceStateRepository
    protected val mediaRepository = container.mediaRepository
    protected val preferencesRepository = container.preferencesRepository

    @Suppress("EmptyMethod")
    final override fun <T : Application> getApplication() = super.getApplication<T>()
}
