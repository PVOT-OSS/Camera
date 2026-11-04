/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

interface ScreenFlash {
    fun apply(onApplied: () -> Unit)

    fun clear()
}
