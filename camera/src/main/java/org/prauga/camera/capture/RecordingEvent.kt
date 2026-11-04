/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

import android.net.Uri

sealed interface RecordingEvent {
    data object Started : RecordingEvent

    data object Paused : RecordingEvent

    data object Resumed : RecordingEvent

    data class Status(val recordedDurationNanos: Long) : RecordingEvent

    data class Finalized(val outputUri: Uri, val hasValidData: Boolean) : RecordingEvent
}
