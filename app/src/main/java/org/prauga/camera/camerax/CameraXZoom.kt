/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camerax

import android.animation.ValueAnimator
import androidx.camera.core.ZoomState
import androidx.camera.view.LifecycleCameraController
import androidx.core.animation.addListener
import kotlinx.coroutines.sync.Mutex
import org.prauga.camera.camerax.CameraXZoom

class CameraXZoom(
    private val cameraController: LifecycleCameraController,
    private val zoomState: () -> ZoomState?,
) {
    /**
     * Mutex used to zoom the camera.
     */
    private var zoomGestureMutex = Mutex()

    /**
     * Apply the specified zoom smoothly. The value will be automatically clamped
     * between min and max.
     * @param zoomRatio The zoom ratio to apply
     */
    fun smoothZoom(zoomRatio: Float) {
        val acquired = zoomGestureMutex.tryLock()
        if (!acquired) {
            return
        }

        val zoomState = zoomState() ?: return

        ValueAnimator.ofFloat(
            zoomState.zoomRatio,
            zoomRatio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        ).apply {
            addUpdateListener {
                cameraController.setZoomRatio(it.animatedValue as Float)
            }
            addListener(
                onEnd = {
                    zoomGestureMutex.unlock()
                }
            )
        }.start()
    }

    /**
     * Reset the zoom to 1.0x (value relative to the lens, not of the FOV).
     */
    fun resetZoom() = smoothZoom(1f)

    /**
     * Zoom in by a power of 2.
     */
    fun zoomIn() = zoomState()?.zoomRatio?.let {
        smoothZoom(it.nextPowerOfTwo())
    }

    /**
     * Zoom out by a power of 2.
     */
    fun zoomOut() = zoomState()?.zoomRatio?.let {
        smoothZoom(it.previousPowerOfTwo())
    }
}
