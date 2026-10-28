/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.camera

import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.camera.view.PreviewView
import androidx.camera.viewfinder.core.ZoomGestureDetector
import kotlin.math.abs
import org.prauga.camera.viewmodels.CameraViewModel

class ViewfinderGestures(
    private val viewFinder: PreviewView,
    private val viewModel: CameraViewModel,
    private val onTap: () -> Unit,
) {
    private val context = viewFinder.context

    var viewFinderTouchEvent: MotionEvent? = null

    private val gestureDetector by lazy {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                viewFinderTouchEvent = e
                return false
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                return e1?.let {
                    if (!handler.hasMessages(MSG_ON_PINCH_TO_ZOOM) &&
                        abs(it.x - e2.x) > 75 * context.resources.displayMetrics.density
                    ) {
                        if (e2.x > it.x) {
                            // Left to right
                            viewModel.previousCameraMode()
                        } else {
                            // Right to left
                            viewModel.nextCameraMode()
                        }
                    }
                    true
                } ?: false
            }
        })
    }

    private val zoomGestureDetector by lazy {
        ZoomGestureDetector(context) {
            when (it) {
                is ZoomGestureDetector.ZoomEvent.Begin -> {
                    zoomGestureDetectorIsInProgress = true
                }

                is ZoomGestureDetector.ZoomEvent.Move -> {
                    viewModel.cameraController.onPinchToZoom(it.incrementalScaleFactor)
                    handler.removeMessages(MSG_ON_PINCH_TO_ZOOM)
                    handler.sendMessageDelayed(handler.obtainMessage(MSG_ON_PINCH_TO_ZOOM), 500)
                }

                is ZoomGestureDetector.ZoomEvent.End -> {
                    zoomGestureDetectorIsInProgress = false
                }
            }
            true
        }
    }
    private var zoomGestureDetectorIsInProgress = false

    private val handler = Handler(Looper.getMainLooper())

    @Suppress("ClickableViewAccessibility")
    fun attach() {
        viewFinder.setOnTouchListener { _, event ->
            if (zoomGestureDetector.onTouchEvent(event) && zoomGestureDetectorIsInProgress) {
                return@setOnTouchListener true
            }
            return@setOnTouchListener gestureDetector.onTouchEvent(event)
        }
        viewFinder.setOnClickListener { onTap() }
    }

    companion object {
        private const val MSG_ON_PINCH_TO_ZOOM = 3
    }
}
