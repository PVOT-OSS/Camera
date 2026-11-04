/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

import android.net.Uri
import java.io.ByteArrayOutputStream

/** Photo capture event. */
sealed interface PhotoCaptureStatus {
    data object CaptureStarted : PhotoCaptureStatus

    data class ImageSaved(val savedUri: Uri?, val photoOutputStream: ByteArrayOutputStream?) :
        PhotoCaptureStatus

    data class Error(val exception: Exception) : PhotoCaptureStatus
}
