/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import android.view.ViewGroup
import org.prauga.camera.capture.PreviewTarget
import org.prauga.camera.capture.ScreenFlash

class Camera2PreviewTarget(
    val container: ViewGroup,
    val screenFlash: ScreenFlash? = null,
) : PreviewTarget
