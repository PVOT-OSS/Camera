/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ext

import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionsManager
import org.prauga.camera.camerax.toCameraXExtensionMode
import org.prauga.camera.models.ExtensionMode

internal fun ExtensionsManager.getSupportedModes(
    cameraSelector: CameraSelector,
) = ExtensionMode.entries.filter {
    isExtensionAvailable(cameraSelector, it.toCameraXExtensionMode())
}.toSet()
