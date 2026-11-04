/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.camera

import android.animation.ValueAnimator
import android.graphics.Rect
import android.icu.text.DecimalFormat
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.widget.ImageView
import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.prauga.camera.R
import org.prauga.camera.capture.FocusEvent
import org.prauga.camera.ext.mapToRange
import org.prauga.camera.ext.px
import org.prauga.camera.ext.select
import org.prauga.camera.ext.setPadding
import org.prauga.camera.ui.views.CountDownView
import org.prauga.camera.ui.views.GridView
import org.prauga.camera.ui.views.HorizontalSlider
import org.prauga.camera.ui.views.IslandView
import org.prauga.camera.ui.views.LevelerView
import org.prauga.camera.ui.views.PreviewBlurView
import org.prauga.camera.ui.views.VerticalSlider
import org.prauga.camera.viewmodels.CameraViewModel

class ViewfinderOverlays(
    root: View,
    private val viewModel: CameraViewModel,
    private val viewFinder: PreviewView,
) {
    private val countDownView = root.findViewById<CountDownView>(R.id.countDownView)
    private val exposureLevel = root.findViewById<VerticalSlider>(R.id.exposureLevel)
    private val gridView = root.findViewById<GridView>(R.id.gridView)
    private val islandView = root.findViewById<IslandView>(R.id.islandView)
    private val levelerView = root.findViewById<LevelerView>(R.id.levelerView)
    private val previewBlurView = root.findViewById<PreviewBlurView>(R.id.previewBlurView)
    private val viewFinderFocus = root.findViewById<ImageView>(R.id.viewFinderFocus)
    private val zoomLevel = root.findViewById<HorizontalSlider>(R.id.zoomLevel)

    private val handler =
        object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                when (msg.what) {
                    MSG_HIDE_ZOOM_SLIDER -> {
                        zoomLevel.isVisible = false
                    }

                    MSG_HIDE_FOCUS_RING -> {
                        viewFinderFocus.isVisible = false
                    }

                    MSG_HIDE_EXPOSURE_SLIDER -> {
                        exposureLevel.isVisible = false
                    }
                }
            }
        }

    init {
        previewBlurView.previewBitmap = viewModel::previewBitmap

        zoomLevel.onProgressChangedByUser = { viewModel.setLinearZoom(it) }
        zoomLevel.textFormatter = { "%.1fx".format(viewModel.uiState.value.zoomState?.zoomRatio) }

        // Set expose level callback & text formatter
        exposureLevel.onProgressChangedByUser = {
            viewModel.setExposureCompensationLevel(it)

            handler.removeMessages(MSG_HIDE_EXPOSURE_SLIDER)
            handler.sendMessageDelayed(handler.obtainMessage(MSG_HIDE_EXPOSURE_SLIDER), 2000)
        }
    }

    fun showExposureSlider() {
        exposureLevel.isVisible = true
        handler.removeMessages(MSG_HIDE_EXPOSURE_SLIDER)
        handler.sendMessageDelayed(handler.obtainMessage(MSG_HIDE_EXPOSURE_SLIDER), 2000)
    }

    fun onRebind() {
        // Show blurred preview
        previewBlurView.freeze()
        previewBlurView.isVisible = true

        // Hide grid until preview is ready
        gridView.alpha = 0f
    }

    fun onPreviewStreaming() {
        // Show grid
        gridView.alpha = 1f
        gridView.previewView = viewFinder

        // Hide preview blur
        previewBlurView.isVisible = false
    }

    fun cancelCountDown() = countDownView.cancelCountDown()

    fun startCountDown(seconds: Int, onCountDownEnded: () -> Unit) {
        countDownView.onPreviewAreaChanged(Rect().apply { viewFinder.getGlobalVisibleRect(this) })
        countDownView.startCountDown(seconds, onCountDownEnded)
    }

    fun CoroutineScope.bind() {
        launch {
            viewModel.uiState
                .select { it.screenRotation }
                .collectLatest { screenRotation ->
                    // Rotate sliders
                    exposureLevel.screenRotation = screenRotation
                    zoomLevel.screenRotation = screenRotation

                    // Rotate info chip
                    islandView.setScreenRotation(screenRotation)

                    // Rotate count down view
                    countDownView.setScreenRotation(screenRotation)
                }
        }

        launch {
            viewModel.uiState
                .select { it.gridMode }
                .collectLatest { gridMode -> gridView.mode = gridMode }
        }

        launch {
            viewModel.uiState
                .select { it.levelerEnabled }
                .collectLatest { levelerEnabled -> levelerView.isVisible = levelerEnabled }
        }

        launch {
            viewModel.uiState
                .select { it.islandItems }
                .collectLatest { islandItems -> islandView.setItems(islandItems) }
        }
        launch {
            viewModel.uiState
                .select { it.zoomState }
                .collectLatest { zoomState ->
                    zoomState
                        ?.takeIf { it.minZoomRatio != it.maxZoomRatio }
                        ?.let {
                            zoomLevel.progress = it.linearZoom
                            zoomLevel.isVisible = true

                            handler.removeMessages(MSG_HIDE_ZOOM_SLIDER)
                            handler.sendMessageDelayed(
                                handler.obtainMessage(MSG_HIDE_ZOOM_SLIDER),
                                2000,
                            )
                        }
                }
        }

        launch {
            viewModel.tapToFocusInfoState.collectLatest { tapToFocusInfoState ->
                when (tapToFocusInfoState.focusState) {
                    FocusEvent.State.STARTED -> {
                        viewFinderFocus.x =
                            tapToFocusInfoState.tapPoint!!.x - (viewFinderFocus.width / 2)
                        viewFinderFocus.y =
                            tapToFocusInfoState.tapPoint!!.y - (viewFinderFocus.height / 2)
                        viewFinderFocus.isVisible = true
                        handler.removeMessages(MSG_HIDE_FOCUS_RING)
                        ValueAnimator.ofInt(0.px, 8.px)
                            .apply {
                                addUpdateListener { anim ->
                                    viewFinderFocus.setPadding(anim.animatedValue as Int)
                                }
                            }
                            .start()
                    }

                    else -> {
                        handler.removeMessages(MSG_HIDE_FOCUS_RING)
                        ValueAnimator.ofInt(8.px, 0.px)
                            .apply {
                                addUpdateListener { anim ->
                                    viewFinderFocus.setPadding(anim.animatedValue as Int)
                                }
                            }
                            .start()

                        handler.sendMessageDelayed(handler.obtainMessage(MSG_HIDE_FOCUS_RING), 500)
                    }
                }
            }
        }

        launch {
            viewModel.uiState
                .select { it.exposureCompensationRangeToLevel }
                .filterNotNull()
                .collectLatest { (range, level) ->
                    exposureLevel.steps = range.endInclusive - range.start
                    exposureLevel.progress = level
                    exposureLevel.textFormatter = {
                        val ev = Int.mapToRange(range, it)
                        when (ev == 0) {
                            true -> "0"
                            false -> EXPOSURE_LEVEL_FORMATTER.format(ev).toString()
                        }
                    }
                }
        }
    }

    companion object {
        private const val MSG_HIDE_ZOOM_SLIDER = 0
        private const val MSG_HIDE_FOCUS_RING = 1
        private const val MSG_HIDE_EXPOSURE_SLIDER = 2

        private val EXPOSURE_LEVEL_FORMATTER = DecimalFormat("+#;-#")
    }
}
