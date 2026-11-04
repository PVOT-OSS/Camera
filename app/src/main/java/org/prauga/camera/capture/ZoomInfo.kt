/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.capture

data class ZoomInfo(
    val zoomRatio: Float,
    val minZoomRatio: Float,
    val maxZoomRatio: Float,
    val linearZoom: Float,
)
