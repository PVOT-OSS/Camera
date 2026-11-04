/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

data class CameraError(val code: Code, val type: Type, val cause: Throwable?) {
    enum class Code {
        MAX_CAMERAS_IN_USE,
        CAMERA_IN_USE,
        OTHER_RECOVERABLE_ERROR,
        STREAM_CONFIG,
        CAMERA_DISABLED,
        CAMERA_FATAL_ERROR,
        DO_NOT_DISTURB_MODE_ENABLED,
        UNKNOWN,
    }

    enum class Type {
        RECOVERABLE,
        CRITICAL,
    }
}
