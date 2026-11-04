/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import android.content.Context
import android.os.Build

class Camera2Health(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isHealthy(cameraId: String) = !preferences.getBoolean(key(cameraId), false)

    fun markFailed(cameraId: String) {
        preferences.edit().putBoolean(key(cameraId), true).apply()
    }

    private fun key(cameraId: String) = "${Build.FINGERPRINT}/$cameraId"

    companion object {
        private const val PREFERENCES_NAME = "camera2_health"
    }
}
