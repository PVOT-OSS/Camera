/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Locale

object Camera2MediaStore {
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    private val STORAGE_DESTINATION = "${Environment.DIRECTORY_DCIM}/Camera"

    fun savePhoto(
        contentResolver: ContentResolver,
        bytes: ByteArray,
        mimeType: String,
        timestamp: Long,
    ): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, getTimeString(timestamp))
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, STORAGE_DESTINATION)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
        }

        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ) ?: error("MediaStore insert failed")

        try {
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Unable to open $uri")
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }

        return uri
    }

    private fun getTimeString(timestamp: Long): String {
        return SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(timestamp)
    }
}
