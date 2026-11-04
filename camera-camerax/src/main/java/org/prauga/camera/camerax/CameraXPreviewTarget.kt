/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camerax

import androidx.camera.view.PreviewView
import androidx.camera.view.ScreenFlashView
import org.prauga.camera.capture.PreviewTarget

class CameraXPreviewTarget(
    val previewView: PreviewView,
    val screenFlashView: ScreenFlashView,
) : PreviewTarget
