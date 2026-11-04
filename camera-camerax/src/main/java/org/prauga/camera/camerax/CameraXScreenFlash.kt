/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camerax

import androidx.camera.view.ScreenFlashView
import org.prauga.camera.capture.ScreenFlash

class CameraXScreenFlash(
    private val screenFlashView: ScreenFlashView,
) : ScreenFlash {
    override fun apply(onApplied: () -> Unit) {
        screenFlashView.screenFlash?.apply(System.currentTimeMillis() + TIMEOUT_MS) {
            onApplied()
        } ?: onApplied()
    }

    override fun clear() {
        screenFlashView.screenFlash?.clear()
    }

    companion object {
        private const val TIMEOUT_MS = 3000L
    }
}
