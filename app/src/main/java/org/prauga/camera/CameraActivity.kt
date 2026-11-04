/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera

import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.ExperimentalZeroShutterLag
import androidx.camera.view.PreviewView
import androidx.camera.view.ScreenFlashView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.prauga.camera.camera2.Camera2PreviewTarget
import org.prauga.camera.camerax.CameraXPreviewTarget
import org.prauga.camera.camerax.CameraXScreenFlash
import org.prauga.camera.capture.PhotoCaptureStatus
import org.prauga.camera.capture.RecordingEvent
import org.prauga.camera.capture.RoutingCameraSession
import org.prauga.camera.capture.models.CameraConfiguration
import org.prauga.camera.capture.models.CameraMode
import org.prauga.camera.capture.models.CameraState
import org.prauga.camera.ext.select
import org.prauga.camera.ext.updateBarsVisibility
import org.prauga.camera.models.Event
import org.prauga.camera.models.LaunchRequest
import org.prauga.camera.models.MediaType
import org.prauga.camera.models.Permission
import org.prauga.camera.models.PermissionState
import org.prauga.camera.ui.GalleryLauncher
import org.prauga.camera.ui.HardwareKeyHandler
import org.prauga.camera.ui.camera.CameraAlerts
import org.prauga.camera.ui.camera.ViewfinderGestures
import org.prauga.camera.ui.camera.ViewfinderOverlays
import org.prauga.camera.ui.compose.CameraScreen
import org.prauga.camera.ui.compose.CameraScreenActions
import org.prauga.camera.ui.compose.CameraScreenState
import org.prauga.camera.ui.compose.CameraTheme
import org.prauga.camera.ui.dialogs.LocationPermissionsDialog
import org.prauga.camera.ui.dialogs.QrBottomSheetDialog
import org.prauga.camera.ui.views.CapturePreviewLayout
import org.prauga.camera.utils.CaptureIntentResult
import org.prauga.camera.utils.PermissionsManager
import org.prauga.camera.utils.ShortcutsUtils
import org.prauga.camera.viewmodels.CameraViewModel

@OptIn(ExperimentalCamera2Interop::class, ExperimentalZeroShutterLag::class)
open class CameraActivity : AppCompatActivity(R.layout.activity_camera) {
    // View models
    private val viewModel by viewModels<CameraViewModel>()

    // Views
    private val cameraScreen by lazy { findViewById<ComposeView>(R.id.cameraScreen) }
    private val capturePreviewLayout by lazy {
        findViewById<CapturePreviewLayout>(R.id.capturePreviewLayout)
    }
    private val mainLayout by lazy { findViewById<ConstraintLayout>(R.id.mainLayout) }
    private val screenFlashView by lazy { findViewById<ScreenFlashView>(R.id.screenFlashView) }
    private val viewfinderLayout by lazy {
        layoutInflater.inflate(R.layout.viewfinder_layout, mainLayout, false)
    }
    private val viewFinder by lazy { viewfinderLayout.findViewById<PreviewView>(R.id.viewFinder) }

    // System services
    private val keyguardManager by lazy { getSystemService(KeyguardManager::class.java) }

    private val galleryLauncher by lazy { GalleryLauncher(this, viewModel::fileExists) }

    // QR
    private val qrBottomSheetDialog by lazy { QrBottomSheetDialog(this) }

    // Permissions
    private val permissionsManager = PermissionsManager(this)

    private val locationPermissionsDialog by lazy {
        LocationPermissionsDialog(this).also {
            it.onResultCallback = { result ->
                if (result) {
                    lifecycleScope.launch {
                        val permissionState =
                            permissionsManager.requestPermission(Permission.LOCATION)
                        viewModel.setSaveLocation(permissionState == PermissionState.GRANTED)
                    }
                } else {
                    viewModel.setSaveLocation(false)
                }
            }
        }
    }

    private val screenState = CameraScreenState()

    private var countdownJob: Job? = null
    private var burstJob: Job? = null
    private var quickTakeJob: Job? = null
    private var quickTakeActive = false

    private val viewfinderOverlays by lazy {
        ViewfinderOverlays(viewfinderLayout, viewModel, viewFinder)
    }

    private val cameraAlerts by lazy { CameraAlerts(mainLayout, viewModel, this) }

    private val viewfinderGestures by lazy {
        ViewfinderGestures(
            viewFinder,
            viewModel,
            onFocus = { x, y -> screenState.showFocus(Offset(x, y), locked = false) },
            onLockFocus = { x, y ->
                viewModel.lockFocus(x, y)
                viewModel.setExposureCompensationLevel(0.5f)
                screenState.showFocus(Offset(x, y), locked = true)
            },
            onExposureDrag = { delta ->
                viewModel.uiState.value.exposureCompensationRangeToLevel?.let { (_, level) ->
                    viewModel.setExposureCompensationLevel((level + delta).coerceIn(0f, 1f))
                    screenState.exposureKey++
                }
            },
            canAdjustExposure = {
                screenState.focusPoint != null &&
                    viewModel.uiState.value.exposureCompensationRangeToLevel != null
            },
        ) {
            // Reset exposure level to 0 EV
            viewModel.setExposureCompensationLevel(0.5f)

            screenState.panel = CameraScreenState.Panel.NONE
        }
    }

    private val hardwareKeyHandler by lazy {
        HardwareKeyHandler(
            viewModel,
            onShutter = ::onShutter,
            onFocus = {
                viewfinderGestures.viewFinderTouchEvent = null
                viewFinder.performClick()
            },
            isMicButtonEnabled = { viewModel.uiState.value.isVideoMicButtonEnabled },
        )
    }

    private val launchRequest by lazy {
        LaunchRequest.fromIntent(intent, overrideInitialCameraMode(), isVoiceInteractionRoot)
    }

    @Suppress("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        enableEdgeToEdge()

        // Hide the status bars
        window.updateBarsVisibility(
            WindowInsetsControllerCompat.BEHAVIOR_DEFAULT,
            statusBars = false,
        )

        // Force dark navigation bar
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
            false

        if (keyguardManager.isKeyguardLocked) {
            setShowWhenLocked(true)

            @Suppress("SourceLockedOrientationActivity")
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // Register shortcuts
        ShortcutsUtils.registerShortcuts(this)

        viewModel.onLaunch(launchRequest)

        // Setup window insets
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            capturePreviewLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom
                leftMargin = insets.left
                rightMargin = insets.right
            }

            windowInsets
        }

        // Observe manual focus
        viewfinderGestures.attach()

        val actions =
            CameraScreenActions(
                onShutter = ::onShutter,
                onQuickTakeStart = ::startQuickTake,
                onQuickTakeLock = { screenState.quickTake = CameraScreenState.QuickTake.LOCKED },
                onQuickTakeStop = ::stopQuickTake,
                onBurstStart = ::startBurst,
                onBurstStop = ::stopBurst,
                onOpenGallery = { lifecycleScope.launch { galleryLauncher.openGallery() } },
                onOpenSettings = ::openSettings,
                canReview = { !keyguardManager.isKeyguardLocked },
            )

        cameraScreen.setContent {
            CameraTheme { CameraScreen(viewModel, screenState, actions, viewfinderLayout) }
        }

        // Set capture preview callback
        capturePreviewLayout.onChoiceCallback = { input ->
            when (input) {
                null -> {
                    capturePreviewLayout.isVisible = false
                }

                is InputStream,
                is Uri -> sendIntentResultAndExit(input)

                else -> throw Exception("Invalid input")
            }
        }

        // Observe QR dialog dismiss
        qrBottomSheetDialog.setOnDismissListener { viewModel.onQrDialogDismissed() }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                permissionsManager.withPermissionGranted(Permission.CAMERA) { loadData() }
            }
        }

        // Also collect location
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                permissionsManager.withPermissionGranted(Permission.LOCATION) {
                    viewModel.location.collect()
                }
            }
        }

        // Check for permissions
        lifecycleScope.launch {
            permissionsManager.permissionStateFlow(Permission.CAMERA).collectLatest {
                permissionState ->
                if (permissionState != PermissionState.GRANTED) {
                    when (permissionsManager.requestPermission(Permission.CAMERA)) {
                        PermissionState.GRANTED -> {
                            // This is a good time to ask the user for location permissions
                            if (viewModel.saveLocation.value == null) {
                                locationPermissionsDialog.show()
                            }
                        }

                        else -> {
                            Toast.makeText(
                                    this@CameraActivity,
                                    R.string.app_permissions_toast,
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                            finish()
                        }
                    }
                }
            }
        }

        // Initialize stuff after camera permissions are granted
        lifecycleScope.launch { queueSetupWithCameraPermissions() }
    }

    override fun onDestroy() {
        // Detach CameraController from ScreenFlashView
        screenFlashView.setController(null)

        // Detach CameraController from PreviewView
        viewFinder.controller = null

        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?) =
        when (capturePreviewLayout.isVisible || screenState.reviewOpen) {
            true -> super.onKeyDown(keyCode, event)
            false ->
                when (hardwareKeyHandler.onKeyDown(keyCode, event)) {
                    HardwareKeyHandler.Result.HANDLED -> true
                    HardwareKeyHandler.Result.DEFAULT -> super.onKeyDown(keyCode, event)
                    null -> super.onKeyDown(keyCode, event)
                }
        }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?) =
        when (capturePreviewLayout.isVisible || screenState.reviewOpen) {
            true -> super.onKeyUp(keyCode, event)
            false ->
                when (hardwareKeyHandler.onKeyUp(keyCode)) {
                    HardwareKeyHandler.Result.HANDLED -> true
                    HardwareKeyHandler.Result.DEFAULT -> super.onKeyDown(keyCode, event)
                    null -> super.onKeyUp(keyCode, event)
                }
        }

    /**
     * This is a method that can be overridden to set the initial camera mode and facing. It's gonna
     * have priority over shared preferences and intents.
     */
    protected open fun overrideInitialCameraMode(): CameraMode? = null

    private fun CoroutineScope.loadData() {
        // Observe preview stream state
        launch {
            viewModel.previewStreaming.collect {
                if (it) {
                    viewfinderOverlays.onPreviewStreaming()

                    // Issue capture if requested via assistant
                    if (launchRequest.captureOnStart) {
                        onShutter()
                    }
                }
            }
        }

        with(viewfinderOverlays) { bind() }
        with(cameraAlerts) { bind() }

        launch {
            viewModel.uiState
                .select { it.capturedMedia }
                .collectLatest { capturedMedia ->
                    if (!galleryLauncher.isKeyguardLocked) {
                        galleryLauncher.galleryUri = capturedMedia.firstOrNull()
                    }
                }
        }

        launch {
            viewModel.event.collect { event ->
                when (event) {
                    is Event.NoCamera -> noCamera()

                    is Event.ShowForceTorchHelp -> cameraAlerts.showForceTorchHelp()

                    is Event.FlipCameraAnimation -> {}

                    is Event.PhotoCapture ->
                        when (val status = event.status) {
                            is PhotoCaptureStatus.CaptureStarted -> screenState.blackoutKey++

                            is PhotoCaptureStatus.ImageSaved -> {
                                if (!viewModel.uiState.value.inSingleCaptureMode) {
                                    onCapturedMedia(status.savedUri)
                                } else {
                                    status.savedUri?.let { openCapturePreview(it, MediaType.PHOTO) }
                                    status.photoOutputStream?.use {
                                        openCapturePreview(ByteArrayInputStream(it.toByteArray()))
                                    }
                                }
                            }

                            is PhotoCaptureStatus.Error -> {
                                // Do nothing
                            }
                        }
                }
            }
        }

        launch {
            viewModel.cameraConfiguration.collectLatest { cameraConfiguration ->
                bindCameraUseCases(cameraConfiguration)
            }
        }

        launch {
            viewModel.uiState
                .select { it.screenRotation }
                .collectLatest { screenRotation ->
                    // Rotate capture preview
                    capturePreviewLayout.setScreenRotation(screenRotation)
                }
        }

        launch {
            viewModel.uiState
                .select { it.fullScreenBrightness }
                .collectLatest { fullScreenBrightness ->
                    window.attributes =
                        window.attributes.apply {
                            screenBrightness =
                                when (fullScreenBrightness) {
                                    true -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                                    false -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                }
                        }
                }
        }

        launch {
            viewModel.videoRecordEvent.collect { videoRecordEvent ->
                when (videoRecordEvent) {
                    is RecordingEvent.Finalized -> {
                        if (quickTakeActive) {
                            quickTakeActive = false
                            screenState.quickTake = CameraScreenState.QuickTake.NONE
                            lifecycleScope.launch {
                                viewModel.uiState.first { it.cameraState == CameraState.IDLE }
                                viewModel.setCameraMode(CameraMode.PHOTO)
                            }
                        }

                        if (videoRecordEvent.hasValidData) {
                            if (!viewModel.uiState.value.inSingleCaptureMode) {
                                onCapturedMedia(videoRecordEvent.outputUri)
                            } else {
                                openCapturePreview(videoRecordEvent.outputUri, MediaType.VIDEO)
                            }
                        }
                    }

                    else -> {}
                }
            }
        }

        launch {
            viewModel.qrResult.collectLatest { qrResult ->
                qrBottomSheetDialog.setQrResult(qrResult)
            }
        }
    }

    private suspend fun queueSetupWithCameraPermissions() {
        var initialized = false

        permissionsManager.permissionStateFlow(Permission.CAMERA).collect { permissionState ->
            if (permissionState == PermissionState.GRANTED && !initialized) {
                // Attach CameraController to PreviewView and ScreenFlashView
                viewModel.attachPreview(
                    RoutingCameraSession.Target(
                        CameraXPreviewTarget(viewFinder, screenFlashView),
                        Camera2PreviewTarget(viewFinder, CameraXScreenFlash(screenFlashView)),
                    )
                )
                screenFlashView.setScreenFlashWindow(window)

                when (viewModel.initializeCameraConfiguration()) {
                    CameraViewModel.InitializationResult.INITIALIZED -> {}

                    CameraViewModel.InitializationResult.NO_CAMERA -> noCamera()

                    CameraViewModel.InitializationResult.VIDEO_UNSUPPORTED -> {
                        Toast.makeText(
                                this@CameraActivity,
                                R.string.camcorder_unsupported_toast,
                                Toast.LENGTH_LONG,
                            )
                            .show()
                        finish()
                        return@collect
                    }
                }

                initialized = true
            }
        }
    }

    /** Rebind cameraProvider use cases */
    private fun bindCameraUseCases(cameraConfiguration: CameraConfiguration) {
        require(viewModel.canRestartCamera()) {
            "Trying to rebind camera while camera is not in IDLE state"
        }

        viewfinderOverlays.onRebind()
        screenState.clearFocus()

        viewModel.bindCamera(cameraConfiguration, this)

        // Restore settings that can be set on the fly
        viewModel.setVideoMicrophoneEnabled(viewModel.uiState.value.videoMicMode)

        // Reset exposure level
        viewModel.setExposureCompensationLevel(0.5f)
    }

    private fun openCapturePreview(uri: Uri, mediaType: MediaType) {
        runOnUiThread {
            capturePreviewLayout.updateSource(uri, mediaType)
            capturePreviewLayout.isVisible = true
        }
    }

    private fun openCapturePreview(photoInputStream: InputStream) {
        runOnUiThread {
            capturePreviewLayout.updateSource(photoInputStream)
            capturePreviewLayout.isVisible = true
        }
    }

    /**
     * When the user took a photo or a video and confirmed it, its URI gets sent back to the app
     * that sent the intent and closes the camera.
     */
    private fun sendIntentResultAndExit(input: Any) {
        val (resultCode, data) = CaptureIntentResult.build(contentResolver, intent, input)
        setResult(resultCode, data)
        finish()
    }

    private fun onShutter() {
        if (cancelCountdown()) {
            return
        }

        if (screenState.quickTake == CameraScreenState.QuickTake.LOCKED) {
            stopQuickTake()
            return
        }

        startTimerAndRun {
            when (viewModel.uiState.value.cameraMode) {
                CameraMode.PHOTO -> viewModel.takePhoto()
                CameraMode.VIDEO -> viewModel.captureVideo()
                else -> {}
            }
        }
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun startTimerAndRun(runnable: () -> Unit) {
        // Allow forcing timer if requested by the assistant
        val timerModeSeconds =
            launchRequest.timerDurationSeconds ?: viewModel.uiState.value.timerMode.seconds

        if (timerModeSeconds <= 0 || !viewModel.canRestartCamera()) {
            runnable()
            return
        }

        viewModel.startCountdown()

        countdownJob =
            lifecycleScope.launch {
                for (second in timerModeSeconds downTo 1) {
                    screenState.countdown = second
                    delay(1000)
                }
                screenState.countdown = null
                countdownJob = null

                viewModel.endCountdown()
                runnable()
            }
    }

    private fun cancelCountdown(): Boolean {
        val job = countdownJob ?: return false

        job.cancel()
        countdownJob = null
        screenState.countdown = null
        viewModel.endCountdown()

        return true
    }

    private fun startBurst() {
        if (viewModel.uiState.value.cameraMode != CameraMode.PHOTO) {
            return
        }

        burstJob?.cancel()
        burstJob =
            lifecycleScope.launch {
                var count = 0
                screenState.burstCount = count
                while (isActive) {
                    viewModel.uiState.first { it.cameraState == CameraState.IDLE }
                    viewModel.takePhoto()
                    screenState.burstCount = ++count
                    withTimeoutOrNull(1000) {
                        viewModel.uiState.first { it.cameraState != CameraState.IDLE }
                    }
                }
            }
    }

    private fun stopBurst() {
        burstJob?.cancel()
        burstJob = null
        screenState.burstCount = null
    }

    private fun startQuickTake() {
        if (
            viewModel.uiState.value.cameraMode != CameraMode.PHOTO || !viewModel.canRestartCamera()
        ) {
            return
        }

        quickTakeActive = true
        screenState.quickTake = CameraScreenState.QuickTake.HOLDING

        quickTakeJob =
            lifecycleScope.launch {
                viewModel.setCameraMode(CameraMode.VIDEO).join()

                val ready =
                    withTimeoutOrNull(QUICK_TAKE_TIMEOUT_MS) {
                        viewModel.uiState.first { it.cameraMode == CameraMode.VIDEO }
                        withTimeoutOrNull(500) { viewModel.previewStreaming.first { !it } }
                        viewModel.previewStreaming.first { it }
                    } != null

                quickTakeJob = null

                if (!ready || screenState.quickTake == CameraScreenState.QuickTake.NONE) {
                    quickTakeActive = false
                    screenState.quickTake = CameraScreenState.QuickTake.NONE
                    viewModel.setCameraMode(CameraMode.PHOTO)
                    return@launch
                }

                viewModel.captureVideo()
            }
    }

    private fun stopQuickTake() {
        if (!quickTakeActive) {
            return
        }

        screenState.quickTake = CameraScreenState.QuickTake.NONE

        if (quickTakeJob == null && viewModel.uiState.value.cameraState.isRecordingVideo) {
            viewModel.captureVideo()
        }
    }

    /** Show a toast warning the user that no camera is available and close the activity. */
    private fun noCamera() {
        Toast.makeText(this, R.string.error_no_cameras_available, Toast.LENGTH_LONG).show()
        finish()
    }

    /** Method called when a new media have been successfully captured and saved. */
    private fun onCapturedMedia(item: Uri?) {
        item?.let {
            screenState.latestMedia = it
            screenState.latestMediaKey++
            galleryLauncher.galleryUri = it
        }
        galleryLauncher.onCapturedMedia(item)
    }

    companion object {
        private const val QUICK_TAKE_TIMEOUT_MS = 3000L
    }
}
