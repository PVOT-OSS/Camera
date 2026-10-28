/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.models

import android.content.Intent
import android.provider.MediaStore
import org.prauga.camera.utils.ShortcutsUtils

data class LaunchRequest(
    val cameraMode: CameraMode? = null,
    val cameraFacing: CameraFacing? = null,
    val singleCapture: Boolean = false,
    val assistant: AssistantIntent? = null,
    val voiceLaunch: Boolean = false,
) {
    val captureOnStart: Boolean
        get() = (voiceLaunch || assistant?.cameraOpenOnly != null)
                && assistant?.cameraOpenOnly != true

    val timerDurationSeconds: Int?
        get() = assistant?.timerDurationSeconds

    companion object {
        private val actionModes = mapOf(
            // Intents
            MediaStore.ACTION_IMAGE_CAPTURE to CameraMode.PHOTO,
            MediaStore.ACTION_IMAGE_CAPTURE_SECURE to CameraMode.PHOTO,
            MediaStore.ACTION_VIDEO_CAPTURE to CameraMode.VIDEO,
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA to CameraMode.PHOTO,
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE to CameraMode.PHOTO,
            MediaStore.INTENT_ACTION_VIDEO_CAMERA to CameraMode.VIDEO,

            // Shortcuts
            ShortcutsUtils.SHORTCUT_ID_SELFIE to CameraMode.PHOTO,
            ShortcutsUtils.SHORTCUT_ID_VIDEO to CameraMode.VIDEO,
            ShortcutsUtils.SHORTCUT_ID_QR to CameraMode.QR,
        )

        private val actionFacings = mapOf(
            ShortcutsUtils.SHORTCUT_ID_SELFIE to CameraFacing.FRONT,
            ShortcutsUtils.SHORTCUT_ID_VIDEO to CameraFacing.BACK,
        )

        private val singleCaptureActions = setOf(
            MediaStore.ACTION_IMAGE_CAPTURE,
            MediaStore.ACTION_IMAGE_CAPTURE_SECURE,
            MediaStore.ACTION_VIDEO_CAPTURE,
        )

        fun fromIntent(
            intent: Intent,
            defaultCameraMode: CameraMode?,
            voiceLaunch: Boolean,
        ): LaunchRequest {
            val action = intent.action
            val assistant = AssistantIntent.fromIntent(intent)

            return LaunchRequest(
                cameraMode = actionModes[action] ?: defaultCameraMode,
                cameraFacing = assistant?.useFrontCamera?.let {
                    if (it) CameraFacing.FRONT else CameraFacing.BACK
                } ?: actionFacings[action],
                singleCapture = action in singleCaptureActions,
                assistant = assistant,
                voiceLaunch = voiceLaunch && intent.hasCategory(Intent.CATEGORY_VOICE),
            )
        }
    }
}
