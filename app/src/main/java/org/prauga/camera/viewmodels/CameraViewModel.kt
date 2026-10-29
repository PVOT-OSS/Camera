/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import android.view.OrientationEventListener
import androidx.camera.core.AspectRatio
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.core.ImageCapture
import androidx.camera.extensions.ExtensionMode
import androidx.camera.video.Quality
import androidx.camera.video.Recording
import androidx.camera.view.LifecycleCameraController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.reflect.safeCast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.prauga.camera.camerax.CameraXBinder
import org.prauga.camera.camerax.CameraXCapture
import org.prauga.camera.camerax.CameraXZoom
import org.prauga.camera.capture.CaptureStateMachine
import org.prauga.camera.ext.applicationContext
import org.prauga.camera.ext.flashMode
import org.prauga.camera.ext.mapToRange
import org.prauga.camera.ext.next
import org.prauga.camera.ext.previous
import org.prauga.camera.models.Camera
import org.prauga.camera.models.CameraConfiguration
import org.prauga.camera.models.CameraFacing
import org.prauga.camera.models.CameraMode
import org.prauga.camera.models.CameraState
import org.prauga.camera.models.Event
import org.prauga.camera.models.GridMode
import org.prauga.camera.models.HardwareKey
import org.prauga.camera.models.LaunchRequest
import org.prauga.camera.models.Rotation
import org.prauga.camera.models.ThermalStatus
import org.prauga.camera.models.TimerMode
import org.prauga.camera.qr.QrImageAnalyzer
import org.prauga.camera.utils.CameraSoundsUtils

/**
 * [ViewModel] representing a camera session. This data is used to receive
 * live data regarding the setting currently enabled.
 */
class CameraViewModel(application: Application) : CameraBaseViewModel(application) {
    // Camera sounds
    val cameraSoundsUtils = CameraSoundsUtils(preferencesRepository)

    // Orientation
    private val orientation = deviceStateRepository.orientation
        .flowOn(Dispatchers.IO)
        .shareIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1
        )

    /**
     * [ExecutorService] for camera related operations.
     */
    val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * CameraX's [LifecycleCameraController].
     */
    val cameraController = LifecycleCameraController(applicationContext)

    /**
     * Mutex used to rebind the camera.
     */
    val rebindMutex = Mutex()

    /**
     * QR image analyzer.
     */
    val qrImageAnalyzer = QrImageAnalyzer(applicationContext, viewModelScope)

    private val cameraXCapture by lazy {
        CameraXCapture(
            applicationContext,
            cameraController,
            cameraExecutor,
            viewModelScope,
            mediaRepository,
            cameraSoundsUtils,
            captureStateMachine,
            inSingleCaptureMode,
            location = { location.value },
            cameraFacing = { cameraFacing.value },
            photoFfcMirror = { preferencesRepository.photoFfcMirror.value },
            emitEvent = ::emitEvent,
        )
    }

    private val cameraXZoom by lazy { CameraXZoom(cameraController) { zoomState.value } }

    private val cameraConfigurationFactory = CameraConfigurationFactory(
        preferencesRepository,
        inSingleCaptureMode = { inSingleCaptureMode.value },
    )

    val cameraXBinder = CameraXBinder(
        cameraController,
        cameraExecutor,
        qrImageAnalyzer,
        cameraRepository::getExtensionEnabledCameraSelector,
    )

    /**
     * Do not emit here directly, use [emitEvent].
     */
    private val _event = MutableSharedFlow<Event>()

    /**
     * Events.
     */
    val event = _event.asSharedFlow()

    private var launchRequest = LaunchRequest()

    private val _inSingleCaptureMode = MutableStateFlow(false)

    /**
     * Whether the current session is in single capture mode.
     */
    private val inSingleCaptureMode = _inSingleCaptureMode.asStateFlow()

    private val _cameraConfiguration = MutableStateFlow<CameraConfiguration?>(null)
    val cameraConfiguration = _cameraConfiguration
        .filterNotNull()
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            replay = 1,
        )

    /**
     * The current camera.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val camera = cameraConfiguration
        .mapLatest { cameraConfiguration -> cameraConfiguration.camera }
        .flowOn(Dispatchers.IO)
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val cameraFacing = camera
        .mapLatest { camera -> camera.cameraFacing }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = preferencesRepository.lastCameraFacing.value,
        )

    /**
     * The current camera mode.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val cameraMode = cameraConfiguration
        .mapLatest { cameraConfiguration -> cameraConfiguration.cameraMode }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.lastCameraMode.value,
        )

    /**
     * The list of [Camera]s to use for cycling.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val camerasForCycling = combine(
        cameraRepository.cameras,
        cameraMode,
    ) { cameras, cameraMode ->
        cameras.filter { it.supportsCameraMode(cameraMode) }.groupBy(Camera::cameraFacing)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = mapOf(),
        )

    /**
     * Flow used for camera mode animations.
     */
    val cameraModeTransition = cameraMode
        .runningFold(null as CameraMode? to cameraMode.value) { acc, value ->
            acc.second to value
        }
        .flowOn(Dispatchers.IO)
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
        )

    /**
     * Pair of active camera to the available cameras.
     */
    private val lenses = combine(
        cameraRepository.cameras,
        camera,
        cameraMode,
    ) { cameras, camera, cameraMode ->
        camera to cameras.filter {
            it.cameraFacing == camera.cameraFacing && it.supportsCameraMode(cameraMode)
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null to listOf(),
        )

    /**
     * CameraX's state of the current camera.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val cameraXCameraState = camera
        .flatMapLatest { camera ->
            camera.cameraXCameraState
        }
        .flowOn(Dispatchers.IO)
        .shareIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1
        )

    private val captureStateMachine = CaptureStateMachine()

    /**
     * Current camera state.
     */
    private val cameraState = captureStateMachine.state

    /**
     * Current screen rotation.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val screenRotation = orientation
        .filter { it != OrientationEventListener.ORIENTATION_UNKNOWN }
        .mapLatest { orientation ->
            Rotation.fromDegreesInAperture(orientation)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = Rotation.ROTATION_0
        )

    /**
     * Captured media [Uri]s
     */
    private val capturedMedia = mediaRepository.capturedMedia()
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = listOf(),
        )

    private val flashController = FlashController(
        viewModelScope,
        cameraConfiguration,
        currentConfiguration = { _cameraConfiguration.value },
        cameraState,
        cameraMode,
        preferencesRepository,
        emitEvent = ::emitEvent,
    )

    private val supportedFlashModes = flashController.supportedFlashModes

    private val flashMode = flashController.flashMode

    private val isFlashButtonEnabled = flashController.isFlashButtonEnabled

    /**
     * Grid mode.
     */
    private val gridMode = combine(
        preferencesRepository.lastGridMode,
        cameraConfiguration,
    ) { gridMode, cameraConfiguration ->
        gridMode.takeIf { cameraConfiguration.cameraMode != CameraMode.QR } ?: GridMode.OFF
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = GridMode.OFF
        )

    /**
     * Timer mode.
     */
    private val timerMode = preferencesRepository.timerMode
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = TimerMode.OFF
        )

    /**
     * Whether the leveler is enabled.
     */
    private val levelerEnabled = preferencesRepository.leveler
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    /**
     * Whether screen brightness should be forced to full.
     */
    private val fullScreenBrightness = preferencesRepository.brightScreen
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    private val thermalStatus = deviceStateRepository.thermalStatus
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = ThermalStatus.NONE,
        )

    /**
     * The current zoom state.
     */
    private val zoomState = cameraController.zoomState.asFlow()
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    val tapToFocusInfoState = cameraController.tapToFocusInfoState.asFlow()
        .flowOn(Dispatchers.IO)
        .shareIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1
        )

    val location = deviceStateRepository.location
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    val saveLocation = combine(
        preferencesRepository.saveLocation,
        inSingleCaptureMode,
    ) { saveLocation, inSingleCaptureMode ->
        when (inSingleCaptureMode) {
            true -> false
            false -> saveLocation
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = preferencesRepository.saveLocation,
        )

    private val batteryIntent = deviceStateRepository.batteryIntent
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    /**
     * The current, exposure compensation level, from 0 to 1
     */
    private val exposureCompensationLevel = MutableStateFlow(0.5f)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val exposureCompensationRange = cameraConfiguration
        .mapLatest { it.camera.exposureCompensationRange }
        .flowOn(Dispatchers.IO)
        .shareIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1
        )

    private val exposureCompensationRangeToLevel = combine(
        exposureCompensationLevel,
        exposureCompensationRange,
    ) { exposureCompensationLevel, exposureCompensationRange ->
        exposureCompensationRange to exposureCompensationLevel
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val exposureCompensationIndex = exposureCompensationRangeToLevel
        .filterNotNull()
        .mapLatest { (exposureCompensationLevel, exposureCompensationRange) ->
            Int.mapToRange(exposureCompensationLevel, exposureCompensationRange)
        }
        .flowOn(Dispatchers.IO)
        .shareIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1
        )

    private val isShutterButtonEnabled = combine(
        cameraMode,
        cameraState,
    ) { cameraMode, cameraState ->
        when (cameraState) {
            CameraState.IDLE -> true
            CameraState.COUNTDOWN -> cameraMode == CameraMode.VIDEO
            CameraState.TAKING_PHOTO -> false
            CameraState.PRE_RECORDING_VIDEO -> false
            CameraState.RECORDING_VIDEO -> true
            CameraState.RECORDING_VIDEO_PAUSED -> true
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    // Photo

    /**
     * Photo capture mode.
     * @see ImageCapture.CaptureMode
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val photoCaptureMode = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            when (cameraConfiguration) {
                is CameraConfiguration.Photo -> cameraConfiguration.photoCaptureMode
                else -> preferencesRepository.photoCaptureMode.value
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.photoCaptureMode.value,
        )

    /**
     * Photo aspect ratio.
     * @see AspectRatio.Ratio
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val photoAspectRatio = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            when (cameraConfiguration) {
                is CameraConfiguration.Photo -> cameraConfiguration.photoAspectRatio
                else -> preferencesRepository.photoAspectRatio.value
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.photoAspectRatio.value,
        )

    /**
     * Photo effect.
     * @see ExtensionMode.Mode
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val photoEffect = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            cameraConfiguration.extensionMode
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.photoEffect.value,
        )

    /**
     * Whether the user should be able to select a photo effect.
     */
    @androidx.annotation.OptIn(ExperimentalZeroShutterLag::class)
    private val isPhotoEffectButtonVisible = combine(
        cameraConfiguration,
        cameraMode,
        photoCaptureMode,
    ) { cameraConfiguration, cameraMode, photoCaptureMode ->
        cameraMode == CameraMode.PHOTO &&
                photoCaptureMode != ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG &&
                cameraConfiguration.camera.supportedExtensionModes.size > 1
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false,
        )

    // Video

    /**
     * Video quality.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val videoQuality = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            when (cameraConfiguration) {
                is CameraConfiguration.Video -> cameraConfiguration.videoQuality
                else -> preferencesRepository.videoQuality.value
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.videoQuality.value,
        )

    private val isVideoQualityButtonEnabled = combine(
        camera,
        cameraState,
    ) { camera, cameraState ->
        cameraState == CameraState.IDLE && camera.supportedVideoQualities.size > 1
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false,
        )

    /**
     * Video frame rate.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val videoFrameRate = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            when (cameraConfiguration) {
                is CameraConfiguration.Video -> cameraConfiguration.videoFrameRate
                else -> preferencesRepository.videoFrameRate.value
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.videoFrameRate.value,
        )

    private val isVideoFrameRateButtonEnabled = combine(
        cameraConfiguration,
        cameraState,
    ) { cameraConfiguration, cameraState ->
        cameraState == CameraState.IDLE && when (cameraConfiguration) {
            is CameraConfiguration.Video -> {
                val videoQualityInfo = cameraConfiguration.camera.supportedVideoQualities[
                    cameraConfiguration.videoQuality
                ] ?: error("Video quality not supported")

                videoQualityInfo.supportedFrameRates.size > 1
            }

            else -> false
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false,
        )

    /**
     * Video dynamic range.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val videoDynamicRange = cameraConfiguration
        .mapLatest { cameraConfiguration ->
            when (cameraConfiguration) {
                is CameraConfiguration.Video -> cameraConfiguration.videoDynamicRange
                else -> preferencesRepository.videoDynamicRange.value
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = preferencesRepository.videoDynamicRange.value,
        )

    private val isVideoDynamicRangeButtonEnabled = combine(
        cameraConfiguration,
        cameraState,
    ) { cameraConfiguration, cameraState ->
        cameraState == CameraState.IDLE && when (cameraConfiguration) {
            is CameraConfiguration.Video -> {
                val videoQualityInfo = cameraConfiguration.camera.supportedVideoQualities[
                    cameraConfiguration.videoQuality
                ] ?: error("Video quality not supported")

                videoQualityInfo.supportedDynamicRanges.size > 1
            }

            else -> false
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false,
        )

    /**
     * Video mic mode.
     */
    private val videoMicMode = MutableStateFlow(preferencesRepository.videoMicMode.value)

    /**
     * Video [Recording].
     */
    val videoRecording = cameraXCapture.videoRecording

    private val isVideoMicButtonEnabled = combine(
        cameraState,
        videoRecording,
    ) { cameraState, videoRecording ->
        cameraState == CameraState.IDLE || videoRecording?.isAudioSourceConfigured == true
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false,
        )

    // QR

    /**
     * Video recording duration.
     */
    private val videoRecordingDuration = cameraXCapture.videoRecordingDuration

    /**
     * Video record event.
     */
    val videoRecordEvent = cameraXCapture.videoRecordEvent

    /**
     * QR result.
     */
    val qrResult = qrImageAnalyzer.qrResult
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
        )

    /**
     * Whether the camera can be flipped.
     */
    private val canFlipCamera = combine(
        camerasForCycling,
        cameraState,
    ) { camerasForCycling, cameraState ->
        camerasForCycling.keys.size > 1 && !cameraState.isRecordingVideo
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )

    /**
     * Island items.
     */
    private val islandItems = combine(
        cameraConfiguration,
        videoMicMode,
        batteryIntent,
        thermalStatus,
    ) { cameraConfiguration, videoMicMode, batteryIntent, thermalStatus ->
        islandItemsOf(cameraConfiguration, videoMicMode, batteryIntent, thermalStatus)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = listOf(),
        )

    val uiState = combine(
        listOf<Flow<Any?>>(
            cameraMode,
            cameraState,
            inSingleCaptureMode,
            screenRotation,
            lenses,
            capturedMedia,
            supportedFlashModes,
            flashMode,
            isFlashButtonEnabled,
            gridMode,
            timerMode,
            levelerEnabled,
            fullScreenBrightness,
            thermalStatus,
            zoomState,
            exposureCompensationRangeToLevel,
            isShutterButtonEnabled,
            photoAspectRatio,
            photoEffect,
            isPhotoEffectButtonVisible,
            videoQuality,
            isVideoQualityButtonEnabled,
            videoFrameRate,
            isVideoFrameRateButtonEnabled,
            videoDynamicRange,
            isVideoDynamicRangeButtonEnabled,
            videoMicMode,
            isVideoMicButtonEnabled,
            videoRecordingDuration,
            canFlipCamera,
            islandItems,
        )
    ) { currentUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = currentUiState(),
        )

    init {
        viewModelScope.launch {
            launch {
                flashMode.collectLatest { flashMode ->
                    cameraController.flashMode = flashMode
                }
            }

            launch {
                exposureCompensationIndex.collectLatest { exposureCompensationIndex ->
                    cameraController.cameraControl?.setExposureCompensationIndex(
                        exposureCompensationIndex
                    )
                }
            }

            launch {
                // Track all user preferences that requires a rebind
                combine<Any?, _>(
                    preferencesRepository.photoCaptureMode,
                    preferencesRepository.enableZsl,
                    preferencesRepository.usePhotoJpegUltraHdr,
                    preferencesRepository.enableRawImageCapture,
                    preferencesRepository.disableJpegWithRaw,
                    preferencesRepository.videoStabilization,
                    preferencesRepository.videoMirrorMode,
                    preferencesRepository.edgeMode,
                    preferencesRepository.noiseReductionMode,
                    preferencesRepository.shadingMode,
                    preferencesRepository.colorCorrectionAberrationMode,
                    preferencesRepository.distortionCorrectionMode,
                    preferencesRepository.hotPixelMode,
                ) { }.drop(1).collectLatest {
                    updateConfiguration<CameraConfiguration> { cameraConfiguration ->
                        cameraConfigurationFactory.create(
                            camera = cameraConfiguration.camera,
                            cameraMode = cameraConfiguration.cameraMode,
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        cameraController.unbind()

        cameraExecutor.shutdown()

        cameraSoundsUtils.release()
    }

    fun onLaunch(launchRequest: LaunchRequest) {
        this.launchRequest = launchRequest
        _inSingleCaptureMode.value = launchRequest.singleCapture
    }

    /**
     * Initialize [cameraConfiguration].
     */
    suspend fun initializeCameraConfiguration(): InitializationResult {
        var cameraMode = launchRequest.cameraMode ?: preferencesRepository.lastCameraMode.value
        val cameraFacing = launchRequest.cameraFacing ?: preferencesRepository.lastCameraFacing.value

        if (cameraMode == CameraMode.VIDEO && !isVideoRecordingAvailable()) {
            // If an app asked for a video we have to bail out
            if (inSingleCaptureMode.value) {
                return InitializationResult.VIDEO_UNSUPPORTED
            }
            // Fallback to photo mode
            cameraMode = CameraMode.PHOTO
        }

        val camera = getSuitableCamera(
            cameraMode = cameraMode,
            cameraFacing = cameraFacing,
        ) ?: return InitializationResult.NO_CAMERA

        val cameraConfiguration = cameraConfigurationFactory.create(
            camera = camera,
            cameraMode = cameraMode,
        )

        _cameraConfiguration.value = cameraConfiguration

        return InitializationResult.INITIALIZED
    }

    /**
     * Get whether we can restart the camera.
     */
    fun canRestartCamera() = captureStateMachine.isIdle

    fun startCountdown() = captureStateMachine.startCountdown()

    fun endCountdown() = captureStateMachine.endCountdown()

    /**
     * Select another camera.
     *
     * @param camera The new camera
     */
    fun selectCamera(
        camera: Camera,
    ) = updateConfiguration<CameraConfiguration> { cameraConfiguration ->
        cameraConfigurationFactory.create(
            camera = camera,
            cameraMode = cameraConfiguration.cameraMode,
        )
    }

    fun takePhoto() = cameraXCapture.takePhoto()

    fun captureVideo() = cameraXCapture.captureVideo()

    fun onVideoRecordingStateButtonPress() = cameraXCapture.onVideoRecordingStateButtonPress()

    /**
     * Flip the camera.
     */
    fun flipCamera() = updateConfiguration<CameraConfiguration> { cameraConfiguration ->
        val cameraForCycling = camerasForCycling.value

        val cameraFacing = cameraForCycling.keys.sortedWith(
            cameraFacingComparator
        ).next(cameraConfiguration.camera.cameraFacing) ?: error("No camera available")

        val camera = when (cameraFacing) {
            CameraFacing.UNKNOWN -> null
            CameraFacing.FRONT -> cameraRepository.mainFrontCamera
            CameraFacing.BACK -> cameraRepository.mainBackCamera
            CameraFacing.EXTERNAL -> null
        } ?: cameraForCycling[cameraFacing]?.firstOrNull() ?: error(
            "Camera with facing $cameraFacing not available"
        )

        preferencesRepository.lastCameraFacing.value = camera.cameraFacing

        emitEvent(Event.FlipCameraAnimation)

        cameraConfigurationFactory.create(
            camera = camera,
            cameraMode = cameraConfiguration.cameraMode,
        )
    }

    fun setCameraMode(
        cameraMode: CameraMode,
    ) = viewModelScope.launch {
        updateConfiguration<CameraConfiguration> { cameraConfiguration ->
            if (cameraConfiguration.cameraMode == cameraMode) {
                return@updateConfiguration cameraConfiguration
            }

            val camera = cameraConfiguration.camera.takeIf {
                it.supportsCameraMode(cameraMode)
            } ?: getSuitableCamera(
                cameraMode,
                cameraConfiguration.camera.cameraFacing,
            ) ?: run {
                Log.e(LOG_TAG, "No camera supports the requested camera mode $cameraMode")
                return@updateConfiguration cameraConfiguration
            }

            preferencesRepository.lastCameraMode.value = cameraMode

            cameraConfigurationFactory.create(
                camera = camera,
                cameraMode = cameraMode,
            )
        }
    }

    /**
     * Switch to the previous camera mode.
     */
    fun previousCameraMode() = cameraMode.value.previous()?.let { setCameraMode(it) }

    /**
     * Switch to the next camera mode.
     */
    fun nextCameraMode() = cameraMode.value.next()?.let { setCameraMode(it) }

    fun cycleFlashMode(forceTorch: Boolean) = flashController.cycleFlashMode(forceTorch)

    fun setForceModeHelpShown(forceTorchHelpShown: Boolean) =
        flashController.setForceModeHelpShown(forceTorchHelpShown)

    /**
     * Cycle to the next grid mode.
     */
    fun cycleGridMode() {
        gridMode.value.next()?.let {
            preferencesRepository.lastGridMode.value = it
        }
    }

    /**
     * Toggle the timer mode.
     */
    fun toggleTimerMode() {
        timerMode.value.next()?.let {
            preferencesRepository.timerMode.value = it
        }
    }

    /**
     * Cycle the extension mode.
     */
    fun cycleExtensionMode() =
        updateConfiguration<CameraConfiguration.Photo> { cameraConfiguration ->
            val supportedExtensionModes = cameraConfiguration.camera.supportedExtensionModes
            val extensionMode = supportedExtensionModes.toList().sorted().next(
                cameraConfiguration.extensionMode
            ) ?: error("No extension mode supported by the camera")

            preferencesRepository.photoEffect.value = extensionMode

            cameraConfiguration.copy(
                extensionMode = extensionMode,
            )
        }

    /**
     * Cycle the photo aspect ratio.
     */
    fun cyclePhotoAspectRatio() =
        updateConfiguration<CameraConfiguration.Photo> { cameraConfiguration ->
            val newAspectRatio = when (cameraConfiguration.photoAspectRatio) {
                AspectRatio.RATIO_4_3 -> AspectRatio.RATIO_16_9
                AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_4_3
                else -> AspectRatio.RATIO_4_3
            }

            preferencesRepository.photoAspectRatio.value = newAspectRatio

            cameraConfiguration.copy(
                photoAspectRatio = newAspectRatio,
            )
        }

    /**
     * Cycle the video quality.
     */
    fun cycleVideoQuality() =
        updateConfiguration<CameraConfiguration.Video> { cameraConfiguration ->
            val videoQuality =
                cameraConfiguration.camera.supportedVideoQualities.keys.toList()
                    .sortedWith { a, b ->
                        listOf(Quality.SD, Quality.HD, Quality.FHD, Quality.QHD, Quality.UHD).let {
                            it.indexOf(a) - it.indexOf(b)
                        }
                    }.next(cameraConfiguration.videoQuality) ?: error(
                    "No video quality supported by the camera"
                )

            val videoQualityInfo = cameraConfiguration.camera.supportedVideoQualities[
                videoQuality
            ] ?: error("Video quality not supported")

            val videoFrameRate = cameraConfiguration.videoFrameRate.takeIf {
                videoQualityInfo.supportedFrameRates.contains(it)
            } ?: videoQualityInfo.supportedFrameRates.firstOrNull()

            val videoDynamicRange = cameraConfiguration.videoDynamicRange.takeIf {
                videoQualityInfo.supportedDynamicRanges.contains(it)
            } ?: videoQualityInfo.supportedDynamicRanges.firstOrNull() ?: error(
                "No video dynamic range supported by the camera"
            )

            preferencesRepository.videoQuality.value = videoQuality

            cameraConfiguration.copy(
                videoQuality = videoQuality,
                videoFrameRate = videoFrameRate,
                videoDynamicRange = videoDynamicRange,
            )
        }

    /**
     * Cycle the video frame rate.
     */
    fun cycleVideoFrameRate() =
        updateConfiguration<CameraConfiguration.Video> { cameraConfiguration ->
            val videoQualityInfo = cameraConfiguration.camera.supportedVideoQualities[
                cameraConfiguration.videoQuality
            ] ?: error(
                "Camera ${cameraConfiguration.camera.cameraId} does not support video quality ${cameraConfiguration.videoQuality}"
            )

            val videoFrameRate = videoQualityInfo.supportedFrameRates.toList().sorted().next(
                cameraConfiguration.videoFrameRate
            )

            preferencesRepository.videoFrameRate.value = videoFrameRate

            cameraConfiguration.copy(
                videoFrameRate = videoFrameRate,
            )
        }

    /**
     * Cycle the video dynamic range.
     */
    fun cycleVideoDynamicRange() =
        updateConfiguration<CameraConfiguration.Video> { cameraConfiguration ->
            val videoQualityInfo = cameraConfiguration.camera.supportedVideoQualities[
                cameraConfiguration.videoQuality
            ] ?: error(
                "Camera ${cameraConfiguration.camera.cameraId} does not support video quality ${cameraConfiguration.videoQuality}"
            )

            val videoDynamicRange = videoQualityInfo.supportedDynamicRanges.toList().next(
                cameraConfiguration.videoDynamicRange
            ) ?: error("No video dynamic range supported by the camera")

            preferencesRepository.videoDynamicRange.value = videoDynamicRange

            cameraConfiguration.copy(
                videoDynamicRange = videoDynamicRange,
            )
        }

    /**
     * Toggle the video microphone status.
     */
    fun toggleVideoMicrophoneEnabled() = setVideoMicrophoneEnabled(!videoMicMode.value)

    /**
     * Set whether the video microphone is enabled.
     *
     * @param videoMicrophoneEnabled Whether the video microphone should be enabled
     */
    @Suppress("MissingPermission")
    fun setVideoMicrophoneEnabled(videoMicrophoneEnabled: Boolean) {
        cameraXCapture.setAudioEnabled(videoMicrophoneEnabled)

        this.videoMicMode.value = videoMicrophoneEnabled

        preferencesRepository.videoMicMode.value = videoMicrophoneEnabled
    }

    /**
     * Set the desired exposure compensation range.
     *
     * @param exposureCompensationLevel A value between 0 and 1, with 0.5 being 0 EV
     */
    fun setExposureCompensationLevel(exposureCompensationLevel: Float) {
        require(exposureCompensationLevel in 0f..1f) {
            "Exposure compensation level must be between 0 and 1, got $exposureCompensationLevel"
        }

        this.exposureCompensationLevel.value = exposureCompensationLevel
    }

    fun smoothZoom(zoomRatio: Float) = cameraXZoom.smoothZoom(zoomRatio)

    fun resetZoom() = cameraXZoom.resetZoom()

    fun zoomIn() = cameraXZoom.zoomIn()

    fun zoomOut() = cameraXZoom.zoomOut()

    fun setSaveLocation(saveLocation: Boolean?) {
        preferencesRepository.saveLocation.value = saveLocation
    }

    fun onQrDialogDismissed() {
        qrImageAnalyzer.dismissResult()
    }

    fun getHardwareKeyAction(
        hardwareKey: HardwareKey,
    ) = preferencesRepository.hardwareKeyActionPreferences[
        hardwareKey
    ]?.value ?: hardwareKey.defaultAction

    fun getHardwareKeyInvert(
        hardwareKey: HardwareKey,
    ) = hardwareKey.isTwoWayKey && preferencesRepository.hardwareKeyInvertPreferences[
        hardwareKey
    ]?.value ?: false

    fun fileExists(uri: Uri) = mediaRepository.fileExists(uri)

    /**
     * Get whether or not any camera currently supports video mode.
     */
    private suspend fun isVideoRecordingAvailable() = cameraRepository.cameras.first().any {
        it.supportsCameraMode(CameraMode.VIDEO)
    }

    private fun currentUiState() = CameraUiState(
        cameraMode = cameraMode.value,
        cameraState = cameraState.value,
        inSingleCaptureMode = inSingleCaptureMode.value,
        screenRotation = screenRotation.value,
        lenses = lenses.value,
        capturedMedia = capturedMedia.value,
        supportedFlashModes = supportedFlashModes.value,
        flashMode = flashMode.value,
        isFlashButtonEnabled = isFlashButtonEnabled.value,
        gridMode = gridMode.value,
        timerMode = timerMode.value,
        levelerEnabled = levelerEnabled.value,
        fullScreenBrightness = fullScreenBrightness.value,
        thermalStatus = thermalStatus.value,
        zoomState = zoomState.value,
        exposureCompensationRangeToLevel = exposureCompensationRangeToLevel.value,
        isShutterButtonEnabled = isShutterButtonEnabled.value,
        photoAspectRatio = photoAspectRatio.value,
        photoEffect = photoEffect.value,
        isPhotoEffectButtonVisible = isPhotoEffectButtonVisible.value,
        videoQuality = videoQuality.value,
        isVideoQualityButtonEnabled = isVideoQualityButtonEnabled.value,
        videoFrameRate = videoFrameRate.value,
        isVideoFrameRateButtonEnabled = isVideoFrameRateButtonEnabled.value,
        videoDynamicRange = videoDynamicRange.value,
        isVideoDynamicRangeButtonEnabled = isVideoDynamicRangeButtonEnabled.value,
        videoMicMode = videoMicMode.value,
        isVideoMicButtonEnabled = isVideoMicButtonEnabled.value,
        videoRecordingDuration = videoRecordingDuration.value,
        canFlipCamera = canFlipCamera.value,
        islandItems = islandItems.value,
    )

    /**
     * Emit a new event to let the activity handle it.
     */
    private fun emitEvent(event: Event) = viewModelScope.launch {
        _event.emit(event)
    }

    /**
     * Get a suitable [Camera] for the provided [CameraFacing] and the current [CameraMode].
     *
     * @param cameraMode The requested [CameraMode]
     * @param cameraFacing The requested [CameraFacing]
     * @return A [Camera] that is compatible with the provided configuration or null
     */
    private suspend fun getSuitableCamera(
        cameraMode: CameraMode,
        cameraFacing: CameraFacing,
    ): Camera? {
        val compatibleCameras = cameraRepository.cameras.first()
            .filter { camera ->
                camera.supportsCameraMode(cameraMode)
            }

        return compatibleCameras.firstOrNull { camera ->
            camera.cameraFacing == cameraFacing
        } ?: compatibleCameras.firstOrNull()
    }

    /**
     * Reconfigure the camera session. This helper method will also handle concurrency.
     *
     * @param block A block that will be executed with the current [CameraConfiguration] and should
     *   return a new one.
     */
    private inline fun <reified T : CameraConfiguration> updateConfiguration(
        block: (T) -> CameraConfiguration,
    ): Boolean {
        if (!canRestartCamera()) {
            return false
        }

        if (!rebindMutex.tryLock()) {
            return false
        }

        return try {
            val currentCameraConfiguration = _cameraConfiguration.value ?: error(
                "Camera configuration is null"
            )

            T::class.safeCast(currentCameraConfiguration)?.let {
                val newCameraConfiguration = block(it)

                _cameraConfiguration.value = newCameraConfiguration

                true
            } ?: false
        } finally {
            rebindMutex.unlock()
        }
    }

    enum class InitializationResult {
        INITIALIZED,
        NO_CAMERA,
        VIDEO_UNSUPPORTED,
    }

    companion object {
        private val LOG_TAG = CameraViewModel::class.simpleName!!

        private val cameraFacingComparator = Comparator.comparingInt<CameraFacing> {
            when (it) {
                CameraFacing.UNKNOWN -> 3
                CameraFacing.FRONT -> 1
                CameraFacing.BACK -> 0
                CameraFacing.EXTERNAL -> 2
            }
        }
    }
}
