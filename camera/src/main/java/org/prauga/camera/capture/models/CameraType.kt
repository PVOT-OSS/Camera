/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture.models

/**
 * [Camera] type.
 */
enum class CameraType {
    /**
     * Camera bundled with the device.
     */
    INTERNAL,

    /**
     * Camera connected to the device with hot-swap support.
     */
    EXTERNAL,
}
