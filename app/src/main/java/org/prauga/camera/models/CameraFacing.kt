/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.models

enum class CameraFacing(
    val cameraType: CameraType,
) {
    UNKNOWN(
        CameraType.INTERNAL,
    ),
    FRONT(
        CameraType.INTERNAL,
    ),
    BACK(
        CameraType.INTERNAL,
    ),
    EXTERNAL(
        CameraType.EXTERNAL,
    ),
}
