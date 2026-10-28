/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.utils

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import org.prauga.camera.ext.scale
import org.prauga.camera.ext.transform
import java.io.FileNotFoundException
import java.io.InputStream

object CaptureIntentResult {
    private val LOG_TAG = CaptureIntentResult::class.simpleName!!

    // We need to return something small enough so as not to overwhelm Binder. 1MB is the
    // per-process limit across all transactions. Camera2 sets a max pixel count of 51200.
    // We set a max side length of 256, for a max pixel count of 65536. Even at 4 bytes per
    // pixel, this is only 256K, well within the limits. (Note: It's not clear if any modern
    // app expects a photo to be returned inline, rather than providing an output URI.)
    // https://developer.android.com/guide/components/activities/parcelables-and-bundles#sdbp
    private const val INLINE_MAX_SIDE_LEN_PIXELS = 256

    fun build(
        contentResolver: ContentResolver,
        request: Intent,
        input: Any,
    ): Pair<Int, Intent?> {
        var outputUri: Uri? = null
        if (request.extras?.containsKey(MediaStore.EXTRA_OUTPUT) == true) {
            outputUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                request.extras?.getParcelable(MediaStore.EXTRA_OUTPUT, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                request.extras?.get(MediaStore.EXTRA_OUTPUT) as Uri
            }
        }

        outputUri?.let {
            return try {
                contentResolver.openOutputStream(it, "wt").use { outputStream ->
                    when (input) {
                        is InputStream -> input.use {
                            input.copyTo(outputStream!!)
                        }

                        is Uri -> contentResolver.openInputStream(input).use { inputStream ->
                            inputStream!!.copyTo(outputStream!!)
                        }

                        else -> throw IllegalStateException("Input is not Uri or InputStream")
                    }
                }

                Activity.RESULT_OK to null
            } catch (exc: FileNotFoundException) {
                Log.e(LOG_TAG, "Failed to open URI")
                Activity.RESULT_CANCELED to null
            }
        }

        return Activity.RESULT_OK to Intent().apply {
            when (input) {
                is InputStream -> {
                    // No output URI provided, so return the photo inline as a downscaled Bitmap.
                    action = "inline-data"
                    val transform = ExifUtils.getTransform(input)
                    val bitmap = input.use { BitmapFactory.decodeStream(input) }
                    val scaledAndRotatedBitmap = bitmap.scale(
                        INLINE_MAX_SIDE_LEN_PIXELS
                    ).transform(transform)
                    putExtra("data", scaledAndRotatedBitmap)
                }

                is Uri -> {
                    // We saved the media (video), so return the URI that we saved.
                    data = input
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    putExtra(MediaStore.EXTRA_OUTPUT, input)
                }

                else -> throw IllegalStateException("Input is not Uri or InputStream")
            }
        }
    }
}
