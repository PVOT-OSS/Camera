/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GalleryLauncher(
    private val activity: Activity,
    private val fileExists: (Uri) -> Boolean,
) {
    private val keyguardManager = activity.getSystemService(KeyguardManager::class.java)

    /**
     * Medias captured from secure activity will be stored here
     * NOTE: Order is important, the first element is the newest one.
     */
    private val secureMediaUris = ArrayDeque<Uri>()

    var galleryUri: Uri? = null

    val isKeyguardLocked: Boolean
        get() = keyguardManager.isKeyguardLocked

    /**
     * Keep track of media items captured while in a secure lockscreen state so that they
     * can be passed to the gallery for viewing without unlocking the device. However, if the
     * keyguard is no longer locked, clear any existing URIs, and do not add this one.
     */
    fun onCapturedMedia(item: Uri?) {
        if (!keyguardManager.isKeyguardLocked) {
            secureMediaUris.clear()
        } else {
            item?.let {
                secureMediaUris.addFirst(it)
            }
        }
    }

    fun dismissKeyguardAndRun(runnable: () -> Unit) {
        if (!keyguardManager.isKeyguardLocked) {
            runnable()
            return
        }

        keyguardManager.requestDismissKeyguard(
            activity,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    super.onDismissSucceeded()
                    runnable()
                }
            }
        )
    }

    suspend fun openGallery() {
        // secureMediaUris will be cleared if keyguard is unlocked
        // or none of the items were found.
        withContext(Dispatchers.IO) {
            updateSecureMediaUris(keyguardManager.isKeyguardLocked)
        }

        if (keyguardManager.isKeyguardLocked) {
            // In this state the only thing we can do is launch a secure review intent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && secureMediaUris.isNotEmpty()
            ) {
                val intent = Intent(
                    MediaStore.ACTION_REVIEW_SECURE, secureMediaUris.first()
                ).apply {
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    if (secureMediaUris.size > 1) {
                        clipData = ClipData.newUri(
                            activity.contentResolver, null, secureMediaUris[1]
                        ).apply {
                            for (i in 2 until secureMediaUris.size) {
                                addItem(activity.contentResolver, ClipData.Item(secureMediaUris[i]))
                            }
                        }
                    }
                }
                runCatching {
                    activity.startActivity(intent)
                    return
                }
            }
        }

        galleryUri?.also { uri ->
            // Try to open the Uri in the non secure gallery
            dismissKeyguardAndRun {
                mutableListOf<String>().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        add(MediaStore.ACTION_REVIEW)
                    }
                    add(Intent.ACTION_VIEW)
                }.forEach {
                    val intent = Intent(it, uri).apply {
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    runCatching {
                        activity.startActivity(intent)
                        return@dismissKeyguardAndRun
                    }
                }
            }
        } ?: run {
            // If the Uri is null, attempt to launch non secure-gallery
            dismissKeyguardAndRun {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                }
                runCatching {
                    activity.startActivity(intent)
                }
            }
        }
    }

    /**
     * If keyguard is not locked, remove any URIs that were stored while in the secure camera state.
     * Otherwise, remove any URIs that no longer exist.
     */
    private fun updateSecureMediaUris(keyguardLocked: Boolean) {
        if (!keyguardLocked) {
            secureMediaUris.clear()
        } else {
            secureMediaUris.removeIf { !fileExists(it) }
        }
    }
}
