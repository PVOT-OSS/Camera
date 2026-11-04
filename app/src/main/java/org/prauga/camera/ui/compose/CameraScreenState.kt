/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.compose

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

class CameraScreenState {
    enum class Panel {
        NONE,
        CONTROLS,
        TIMER,
        EXPOSURE,
        EFFECTS,
        PHOTO_FORMAT,
        VIDEO_FORMAT,
    }

    enum class QuickTake {
        NONE,
        HOLDING,
        LOCKED,
    }

    var panel by mutableStateOf(Panel.NONE)

    var countdown by mutableStateOf<Int?>(null)

    var focusPoint by mutableStateOf<Offset?>(null)
    var focusLocked by mutableStateOf(false)
    var focusKey by mutableIntStateOf(0)
    var exposureKey by mutableIntStateOf(0)

    var blackoutKey by mutableIntStateOf(0)

    var latestMedia by mutableStateOf<Uri?>(null)
    var latestMediaKey by mutableIntStateOf(0)

    var pinching by mutableStateOf(false)

    var burstCount by mutableStateOf<Int?>(null)
    var quickTake by mutableStateOf(QuickTake.NONE)

    var reviewOpen by mutableStateOf(false)

    fun showFocus(point: Offset, locked: Boolean) {
        focusPoint = point
        focusLocked = locked
        focusKey++
    }

    fun clearFocus() {
        focusPoint = null
        focusLocked = false
    }
}
