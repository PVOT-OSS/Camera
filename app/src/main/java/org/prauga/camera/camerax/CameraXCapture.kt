/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camerax

import android.content.Context
import android.graphics.ImageFormat
import android.location.Location
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.prauga.camera.camerax.CameraXCapture
import org.prauga.camera.capture.CaptureStateMachine
import org.prauga.camera.capture.RecordingEvent
import org.prauga.camera.models.CameraFacing
import org.prauga.camera.models.CameraState
import org.prauga.camera.models.Event
import org.prauga.camera.repositories.MediaRepository
import org.prauga.camera.utils.CameraSoundsUtils
import org.prauga.camera.utils.StorageUtils

class CameraXCapture(
    private val context: Context,
    private val cameraController: LifecycleCameraController,
    private val cameraExecutor: ExecutorService,
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val cameraSoundsUtils: CameraSoundsUtils,
    private val captureStateMachine: CaptureStateMachine,
    private val inSingleCaptureMode: StateFlow<Boolean>,
    private val location: () -> Location?,
    private val cameraFacing: () -> CameraFacing,
    private val photoFfcMirror: () -> Boolean,
    private val emitEvent: (Event) -> Unit,
) {
    /**
     * Video [AudioConfig].
     */
    private var videoAudioConfig: AudioConfig = AudioConfig.AUDIO_DISABLED

    /**
     * Video [Recording].
     */
    val videoRecording = MutableStateFlow<Recording?>(null)

    /**
     * Video recording duration.
     */
    val videoRecordingDuration = MutableStateFlow(0L)

    /**
     * Video record event.
     */
    val videoRecordEvent = MutableSharedFlow<RecordingEvent>()

    fun takePhoto() {
        // Bail out if a photo is already being taken
        if (!captureStateMachine.startPhoto()) {
            return
        }

        val photoOutputStream = if (inSingleCaptureMode.value) {
            ByteArrayOutputStream(SINGLE_CAPTURE_PHOTO_BUFFER_INITIAL_SIZE_BYTES)
        } else {
            null
        }

        val imageOutputFormat = cameraController.imageOutputFormat

        val mimeType = when (imageOutputFormat) {
            ImageCapture.OUTPUT_FORMAT_JPEG,
            ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR,
            ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> "image/jpeg"

            ImageCapture.OUTPUT_FORMAT_RAW -> "image/x-adobe-dng"

            else -> error("Unknown image output format")
        }

        val imageCount = AtomicInteger(
            when (imageOutputFormat) {
                ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> 2
                else -> 1
            }
        )

        // Create output options object which contains file + metadata
        val timestamp = System.currentTimeMillis()
        val outputOptions = StorageUtils.getPhotoMediaStoreOutputOptions(
            context.contentResolver,
            ImageCapture.Metadata().apply {
                if (!inSingleCaptureMode.value) {
                    location = location()
                }
                if (cameraFacing() == CameraFacing.FRONT) {
                    isReversedHorizontal = photoFfcMirror()
                }
            },
            mimeType,
            timestamp,
            photoOutputStream,
        )

        // Set up image capture listener, which is triggered after photo has
        // been taken
        val onImageSavedCallback = object : ImageCapture.OnImageSavedCallback {
            override fun onCaptureStarted() {
                emitEvent(Event.PhotoCaptureStatus.CaptureStarted)

                cameraSoundsUtils.playShutterClick()
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Log.d(
                    LOG_TAG,
                    "Photo capture succeeded: ${output.savedUri} format = ${output.imageFormat}"
                )

                if (!inSingleCaptureMode.value) {
                    output.savedUri?.let {
                        mediaRepository.broadcastNewPicture(it)
                    }
                }

                if (imageCount.decrementAndGet() == 0) {
                    captureStateMachine.endPhoto()
                }

                // We only care about JPEG-like formats
                if (imageOutputFormat == ImageCapture.OUTPUT_FORMAT_RAW_JPEG
                    && output.imageFormat in RAW_FORMATS
                ) {
                    return
                }

                emitEvent(
                    Event.PhotoCaptureStatus.ImageSaved(
                        output,
                        photoOutputStream,
                    )
                )
            }

            override fun onError(exc: ImageCaptureException) {
                emitEvent(Event.PhotoCaptureStatus.Error(exc))

                Log.e(LOG_TAG, "Photo capture failed", exc)
                captureStateMachine.endPhoto()
            }
        }

        when (imageOutputFormat) {
            ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> {
                // Create output options object which contains raw file + metadata
                val rawOutputOptions = StorageUtils.getPhotoMediaStoreOutputOptions(
                    context.contentResolver,
                    ImageCapture.Metadata().apply {
                        if (!inSingleCaptureMode.value) {
                            location = location()
                        }
                        if (cameraFacing() == CameraFacing.FRONT) {
                            isReversedHorizontal = photoFfcMirror()
                        }
                    },
                    "image/x-adobe-dng",
                    timestamp,
                )

                cameraController.takePicture(
                    rawOutputOptions,
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    onImageSavedCallback,
                )
            }

            else -> cameraController.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                onImageSavedCallback,
            )
        }
    }

    fun captureVideo() {
        // Disallow state changes while we are about to prepare for recording video
        if (!captureStateMachine.prepareRecording()) {
            if (cameraController.isRecording) {
                // Stop the current recording session.
                videoRecording.value?.stop()
            }
            return
        }

        // Update duration text
        videoRecordingDuration.value = 0L

        // Create output options object which contains file + metadata
        val outputOptions = StorageUtils.getVideoMediaStoreOutputOptions(
            context.contentResolver,
            System.currentTimeMillis(),
            location().takeUnless { inSingleCaptureMode.value }
        )

        // Play shutter sound
        val delayTime = if (cameraSoundsUtils.playStartVideoRecording()) 500L else 0L

        scope.launch {
            delay(delayTime)

            // Start recording
            videoRecording.value = cameraController.startRecording(
                outputOptions,
                videoAudioConfig,
                cameraExecutor,
            ) {
                scope.launch {
                    it.toRecordingEvent()?.let { event -> videoRecordEvent.emit(event) }
                }

                when (it) {
                    is VideoRecordEvent.Start -> {
                        captureStateMachine.onRecordingStarted()
                    }

                    is VideoRecordEvent.Pause -> {
                        captureStateMachine.onRecordingPaused()
                    }

                    is VideoRecordEvent.Resume -> {
                        captureStateMachine.onRecordingResumed()
                    }

                    is VideoRecordEvent.Status -> {
                        videoRecordingDuration.value = it.recordingStats.recordedDurationNanos
                    }

                    is VideoRecordEvent.Finalize -> {
                        cameraSoundsUtils.playStopVideoRecording()
                        if (it.error != VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA) {
                            Log.d(
                                LOG_TAG,
                                "Video capture succeeded: ${it.outputResults.outputUri}",
                            )

                            if (!inSingleCaptureMode.value) {
                                mediaRepository.broadcastNewVideo(it.outputResults.outputUri)
                            }
                        }
                        captureStateMachine.endRecording()
                        videoRecording.value = null
                    }
                }
            }
        }
    }

    fun onVideoRecordingStateButtonPress() {
        when (val cameraState = captureStateMachine.state.value) {
            CameraState.RECORDING_VIDEO -> videoRecording.value?.pause()
            CameraState.RECORDING_VIDEO_PAUSED -> videoRecording.value?.resume()
            else -> error(
                "videoRecordingStateButton clicked while in invalid state: $cameraState"
            )
        }
    }

    fun setAudioEnabled(enabled: Boolean) {
        videoAudioConfig = when (enabled) {
            true -> AudioConfig.create(true)
            false -> AudioConfig.AUDIO_DISABLED
        }

        videoRecording.value?.mute(!enabled)
    }

    companion object {
        private val LOG_TAG = CameraXCapture::class.simpleName!!
        private const val SINGLE_CAPTURE_PHOTO_BUFFER_INITIAL_SIZE_BYTES = 8 * 1024 * 1024 // 8 MiB
        private val RAW_FORMATS = listOf(
            ImageFormat.RAW10,
            ImageFormat.RAW12,
            ImageFormat.RAW_SENSOR,
            ImageFormat.RAW_PRIVATE,
        )
    }
}
