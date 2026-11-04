/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

interface CaptureSounds {
    fun playShutterClick()
    fun playStartVideoRecording(): Boolean
    fun playStopVideoRecording()
}
