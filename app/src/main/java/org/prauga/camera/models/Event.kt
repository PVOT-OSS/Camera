/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.models

import org.prauga.camera.capture.PhotoCaptureStatus

/**
 * A generic event, emitted by the VM, handled by the activity. Consider those as one-shot events,
 * so they shouldn't be handled more than once per emission.
 */
sealed interface Event {
    /** No camera is available on the device, the app should close. */
    data object NoCamera : Event

    /** Show the force torch introduction. */
    data object ShowForceTorchHelp : Event

    /** Start the flip camera button animation. */
    data object FlipCameraAnimation : Event

    data class PhotoCapture(val status: PhotoCaptureStatus) : Event
}
