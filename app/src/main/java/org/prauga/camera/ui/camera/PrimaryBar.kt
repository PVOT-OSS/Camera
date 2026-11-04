/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.camera

import android.graphics.drawable.AnimatedVectorDrawable
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.fallback
import coil3.size.Scale
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.prauga.camera.R
import org.prauga.camera.capture.RecordingEvent
import org.prauga.camera.ext.px
import org.prauga.camera.ext.select
import org.prauga.camera.ext.smoothRotate
import org.prauga.camera.models.CameraMode
import org.prauga.camera.models.CameraState
import org.prauga.camera.ui.GalleryLauncher
import org.prauga.camera.viewmodels.CameraViewModel

class PrimaryBar(
    root: View,
    private val viewModel: CameraViewModel,
    private val galleryLauncher: GalleryLauncher,
    onShutter: () -> Unit,
    onGallery: () -> Unit,
) {
    private val context = root.context

    private val galleryButtonCardView = root.findViewById<CardView>(R.id.galleryButtonCardView)
    private val galleryButtonIconImageView = root.findViewById<ImageView>(R.id.galleryButtonIconImageView)
    private val galleryButtonPreviewImageView = root.findViewById<ImageView>(R.id.galleryButtonPreviewImageView)
    private val shutterButton = root.findViewById<ImageButton>(R.id.shutterButton)
    private val flipCameraButton = root.findViewById<ImageButton>(R.id.flipCameraButton)
    private val videoRecordingStateButton = root.findViewById<ImageButton>(R.id.videoRecordingStateButton)

    enum class ShutterAnimation(val resourceId: Int) {
        InitPhoto(R.drawable.avd_photo_capture),
        InitVideo(R.drawable.avd_mode_video_photo),

        PhotoCapture(R.drawable.avd_photo_capture),
        PhotoToVideo(R.drawable.avd_mode_photo_video),

        VideoToPhoto(R.drawable.avd_mode_video_photo),
        VideoStart(R.drawable.avd_video_start),
        VideoEnd(R.drawable.avd_video_end),
    }

    enum class VideoRecordingStateAnimation(val resourceId: Int) {
        Init(R.drawable.avd_video_recording_pause),
        ResumeToPause(R.drawable.avd_video_recording_pause),
        PauseToResume(R.drawable.avd_video_recording_resume),
    }

    init {
        flipCameraButton.setOnClickListener { viewModel.flipCamera() }

        videoRecordingStateButton.setOnClickListener {
            viewModel.onVideoRecordingStateButtonPress()
        }

        shutterButton.setOnClickListener { onShutter() }

        galleryButtonCardView.setOnClickListener { onGallery() }
    }

    fun performShutterClick() = shutterButton.performClick()

    fun startFlipAnimation() = (flipCameraButton.drawable as AnimatedVectorDrawable).start()

    fun startShutterAnimation(shutterAnimation: ShutterAnimation) {
        // Get appropriate drawable
        val drawable = ContextCompat.getDrawable(
            context, shutterAnimation.resourceId
        ) as AnimatedVectorDrawable

        // Update current drawable
        shutterButton.setImageDrawable(drawable)

        // Start or reset animation
        when (shutterAnimation) {
            ShutterAnimation.InitPhoto,
            ShutterAnimation.InitVideo -> drawable.reset()

            else -> drawable.start()
        }
    }

    private fun startVideoRecordingStateAnimation(animation: VideoRecordingStateAnimation) {
        // Get appropriate drawable
        val drawable = ContextCompat.getDrawable(
            context, animation.resourceId
        ) as AnimatedVectorDrawable

        // Update current drawable
        videoRecordingStateButton.setImageDrawable(drawable)

        // Start or reset animation
        when (animation) {
            VideoRecordingStateAnimation.Init -> drawable.reset()
            else -> drawable.start()
        }
    }

    fun updateGalleryButton(uri: Uri?, fromCapture: Boolean) {
        runOnUiThread {
            val keyguardLocked = galleryLauncher.isKeyguardLocked

            galleryButtonIconImageView.setImageResource(
                when (keyguardLocked) {
                    true -> R.drawable.ic_lock
                    false -> R.drawable.ic_image
                }
            )

            if (keyguardLocked && !fromCapture) {
                return@runOnUiThread
            }

            galleryLauncher.galleryUri = uri

            // When keyguard is unlocked, we want media from MediaStore, else we only trust the
            // ones coming from the capture
            uri?.also {
                galleryButtonPreviewImageView.load(uri) {
                    decoderFactory(VideoFrameDecoder.Factory())
                    crossfade(true)
                    scale(Scale.FILL)
                    size(75.px)
                    error(R.drawable.ic_image)
                    fallback(R.drawable.ic_image)
                    listener(
                        onCancel = {
                            galleryButtonPreviewImageView.isVisible = false
                            galleryButtonIconImageView.isVisible = true
                        },
                        onError = { _, _ ->
                            galleryButtonPreviewImageView.isVisible = false
                            galleryButtonIconImageView.isVisible = true
                        },
                        onSuccess = { _, _ ->
                            galleryButtonPreviewImageView.isVisible = true
                            galleryButtonIconImageView.isVisible = false
                        }
                    )
                }
            } ?: run {
                galleryButtonIconImageView.isVisible = true
                galleryButtonPreviewImageView.isVisible = false
            }
        }
    }

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            shutterButton.post(action)
        }
    }

    fun CoroutineScope.bind() {
        launch {
            viewModel.uiState.select { it.cameraMode }.collectLatest { cameraMode ->
                shutterButton.isInvisible = cameraMode == CameraMode.QR
            }
        }

        launch {
            viewModel.uiState.select { it.inSingleCaptureMode }.collectLatest { inSingleCaptureMode ->
                galleryButtonCardView.isInvisible = inSingleCaptureMode
            }
        }

        launch {
            viewModel.uiState.select { it.cameraState }.collectLatest { cameraState ->
                galleryButtonCardView.isEnabled = cameraState == CameraState.IDLE
                // Shutter button must stay enabled
                flipCameraButton.isEnabled = cameraState == CameraState.IDLE
                videoRecordingStateButton.isVisible = cameraState.isRecordingVideo
            }
        }

        launch {
            viewModel.uiState.select { it.screenRotation }.collectLatest { screenRotation ->
                val compensationValue = screenRotation.compensationValue.toFloat()

                galleryButtonCardView.smoothRotate(compensationValue)
                shutterButton.smoothRotate(compensationValue)
                flipCameraButton.smoothRotate(compensationValue)
            }
        }

        launch {
            viewModel.videoRecordEvent.collect { videoRecordEvent ->
                when (videoRecordEvent) {
                    is RecordingEvent.Started -> runOnUiThread {
                        startVideoRecordingStateAnimation(
                            VideoRecordingStateAnimation.Init
                        )
                    }

                    is RecordingEvent.Paused -> runOnUiThread {
                        startVideoRecordingStateAnimation(
                            VideoRecordingStateAnimation.ResumeToPause
                        )
                    }

                    is RecordingEvent.Resumed -> runOnUiThread {
                        startVideoRecordingStateAnimation(
                            VideoRecordingStateAnimation.PauseToResume
                        )
                    }

                    is RecordingEvent.Finalized -> runOnUiThread {
                        startShutterAnimation(ShutterAnimation.VideoEnd)
                    }

                    else -> {}
                }
            }
        }

        launch {
            viewModel.cameraModeTransition.collectLatest { (oldCameraMode, newCameraMode) ->
                when (oldCameraMode) {
                    CameraMode.PHOTO -> when (newCameraMode) {
                        CameraMode.VIDEO -> startShutterAnimation(ShutterAnimation.PhotoToVideo)
                        else -> startShutterAnimation(ShutterAnimation.InitPhoto)
                    }

                    CameraMode.VIDEO -> when (newCameraMode) {
                        CameraMode.PHOTO -> startShutterAnimation(ShutterAnimation.VideoToPhoto)
                        else -> startShutterAnimation(ShutterAnimation.InitVideo)
                    }

                    CameraMode.QR, null -> when (newCameraMode) {
                        CameraMode.PHOTO -> startShutterAnimation(ShutterAnimation.InitPhoto)

                        CameraMode.VIDEO -> startShutterAnimation(ShutterAnimation.InitVideo)

                        CameraMode.QR -> {}
                    }
                }
            }
        }

        launch {
            viewModel.uiState.select { it.capturedMedia }.collectLatest { capturedMedia ->
                updateGalleryButton(capturedMedia.firstOrNull(), false)
            }
        }

        launch {
            viewModel.uiState.select { it.isShutterButtonEnabled }.collectLatest { isShutterButtonEnabled ->
                shutterButton.isEnabled = isShutterButtonEnabled
            }
        }

        launch {
            viewModel.uiState.select { it.canFlipCamera }.collectLatest { canFlipCamera ->
                flipCameraButton.isInvisible = !canFlipCamera
            }
        }
    }
}
