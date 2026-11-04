/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

import android.graphics.PointF

data class FocusEvent(val focusState: State, val tapPoint: PointF?) {
    enum class State {
        NOT_STARTED,
        STARTED,
        FOCUSED,
        NOT_FOCUSED,
        FAILED,
    }
}
