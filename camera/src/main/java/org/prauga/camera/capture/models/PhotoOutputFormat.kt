/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture.models

enum class PhotoOutputFormat {
    /** JPEG format. */
    JPEG,

    /** JPEG Ultra HDR format. */
    JPEG_ULTRA_HDR,

    /** RAW format. */
    RAW,

    /** RAW + JPEG format. */
    RAW_JPEG;

    val isRaw: Boolean
        get() = this == RAW || this == RAW_JPEG
}
