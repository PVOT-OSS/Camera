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
    private val cameraApplication = getApplication<CameraApplication>()

    protected val cameraDevices = cameraApplication.cameraDevices
    protected val cameraXDevices = cameraApplication.cameraXDevices
    protected val deviceStateRepository = cameraApplication.deviceStateRepository
    protected val mediaRepository = cameraApplication.mediaRepository
    protected val preferencesRepository = cameraApplication.preferencesRepository

    @Suppress("EmptyMethod")
    final override fun <T : Application> getApplication() = super.getApplication<T>()
}
