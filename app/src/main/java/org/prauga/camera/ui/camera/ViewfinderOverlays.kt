/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.camera

import android.view.View
import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.prauga.camera.R
import org.prauga.camera.ext.select
import org.prauga.camera.ui.views.GridView
import org.prauga.camera.ui.views.IslandView
import org.prauga.camera.ui.views.LevelerView
import org.prauga.camera.ui.views.PreviewBlurView
import org.prauga.camera.viewmodels.CameraViewModel

class ViewfinderOverlays(
    root: View,
    private val viewModel: CameraViewModel,
    private val viewFinder: PreviewView,
) {
    private val gridView = root.findViewById<GridView>(R.id.gridView)
    private val islandView = root.findViewById<IslandView>(R.id.islandView)
    private val levelerView = root.findViewById<LevelerView>(R.id.levelerView)
    private val previewBlurView = root.findViewById<PreviewBlurView>(R.id.previewBlurView)

    init {
        previewBlurView.previewBitmap = viewModel::previewBitmap
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

    fun CoroutineScope.bind() {
        launch {
            viewModel.uiState
                .select { it.screenRotation }
                .collectLatest { screenRotation ->
                    // Rotate info chip
                    islandView.setScreenRotation(screenRotation)
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
    }
}
