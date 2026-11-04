/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import org.prauga.camera.models.Camera

interface CameraDevices {
    val cameras: SharedFlow<List<Camera>>
    val mainBackCamera: Camera?
    val mainFrontCamera: Camera?

    fun cameraErrors(camera: Camera): Flow<CameraError?>
}
