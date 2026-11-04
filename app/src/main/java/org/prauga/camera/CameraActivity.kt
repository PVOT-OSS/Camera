/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
import org.prauga.camera.ext.slide
import org.prauga.camera.ext.slideDown
import org.prauga.camera.ext.updateBarsVisibility
import org.prauga.camera.models.Event
import org.prauga.camera.models.LaunchRequest
import org.prauga.camera.models.MediaType
import org.prauga.camera.models.Permission
import org.prauga.camera.models.PermissionState
import org.prauga.camera.ui.GalleryLauncher
import org.prauga.camera.ui.HardwareKeyHandler
import org.prauga.camera.ui.camera.CameraAlerts
import org.prauga.camera.ui.camera.ModeSelector
import org.prauga.camera.ui.camera.PrimaryBar
import org.prauga.camera.ui.camera.SecondaryBottomBar
import org.prauga.camera.ui.camera.SecondaryTopBar
import org.prauga.camera.ui.camera.ViewfinderGestures
import org.prauga.camera.ui.camera.ViewfinderOverlays
import org.prauga.camera.ui.dialogs.LocationPermissionsDialog
import org.prauga.camera.ui.dialogs.QrBottomSheetDialog
import org.prauga.camera.ui.views.CameraModeSelectorLayout
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
    private val cameraModeSelectorLayout by lazy { findViewById<CameraModeSelectorLayout>(R.id.cameraModeSelectorLayout) }
    private val capturePreviewLayout by lazy { findViewById<CapturePreviewLayout>(R.id.capturePreviewLayout) }
    private val mainLayout by lazy { findViewById<ConstraintLayout>(R.id.mainLayout) }
    private val screenFlashView by lazy { findViewById<ScreenFlashView>(R.id.screenFlashView) }
    private val viewFinder by lazy { findViewById<PreviewView>(R.id.viewFinder) }

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
                        val permissionState = permissionsManager.requestPermission(
                            Permission.LOCATION
                        )
                        viewModel.setSaveLocation(permissionState == PermissionState.GRANTED)
                    }
                } else {
                    viewModel.setSaveLocation(false)
                }
            }
        }
    }

    private val secondaryTopBar by lazy {
        SecondaryTopBar(mainLayout, viewModel, onSettings = ::openSettings)
    }

    private val secondaryBottomBar by lazy {
        SecondaryBottomBar(mainLayout, viewModel, onPro = { secondaryTopBar.slide() })
    }

    private val primaryBar by lazy {
        PrimaryBar(
            mainLayout,
            viewModel,
            galleryLauncher,
            onShutter = ::onShutter,
            onGallery = { lifecycleScope.launch { galleryLauncher.openGallery() } },
        )
    }

    private val modeSelector by lazy { ModeSelector(mainLayout, viewModel) }

    private val viewfinderOverlays by lazy { ViewfinderOverlays(mainLayout, viewModel, viewFinder) }

    private val cameraAlerts by lazy { CameraAlerts(mainLayout, viewModel, this) }

    private val viewfinderGestures by lazy {
        ViewfinderGestures(viewFinder, viewModel) {
            // Reset exposure level to 0 EV
            viewModel.setExposureCompensationLevel(0.5f)

            viewfinderOverlays.showExposureSlider()

            secondaryTopBar.slideDown()
        }
    }

    private val hardwareKeyHandler by lazy {
        HardwareKeyHandler(
            viewModel,
            onShutter = { primaryBar.performShutterClick() },
            onFocus = {
                viewfinderGestures.viewFinderTouchEvent = null
                viewFinder.performClick()
            },
            isMicButtonEnabled = { secondaryTopBar.isMicButtonEnabled },
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
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightNavigationBars = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
            && keyguardManager.isKeyguardLocked
        ) {
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

            cameraModeSelectorLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom
                leftMargin = insets.left
                rightMargin = insets.right
            }

            capturePreviewLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom
                leftMargin = insets.left
                rightMargin = insets.right
            }

            windowInsets
        }

        // Observe manual focus
        viewfinderGestures.attach()

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
        qrBottomSheetDialog.setOnDismissListener {
            viewModel.onQrDialogDismissed()
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                permissionsManager.withPermissionGranted(Permission.CAMERA) {
                    loadData()
                }
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
            permissionsManager.permissionStateFlow(
                Permission.CAMERA
            ).collectLatest { permissionState ->
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
                            ).show()
                            finish()
                        }
                    }
                }
            }
        }

        // Initialize stuff after camera permissions are granted
        lifecycleScope.launch {
            queueSetupWithCameraPermissions()
        }
    }

    override fun onDestroy() {
        // Detach CameraController from ScreenFlashView
        screenFlashView.setController(null)

        // Detach CameraController from PreviewView
        viewFinder.controller = null

        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?) = when (capturePreviewLayout.isVisible) {
        true -> super.onKeyDown(keyCode, event)
        false -> when (hardwareKeyHandler.onKeyDown(keyCode, event)) {
            HardwareKeyHandler.Result.HANDLED -> true
            HardwareKeyHandler.Result.DEFAULT -> super.onKeyDown(keyCode, event)
            null -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?) = when (capturePreviewLayout.isVisible) {
        true -> super.onKeyUp(keyCode, event)
        false -> when (hardwareKeyHandler.onKeyUp(keyCode)) {
            HardwareKeyHandler.Result.HANDLED -> true
            HardwareKeyHandler.Result.DEFAULT -> super.onKeyDown(keyCode, event)
            null -> super.onKeyUp(keyCode, event)
        }
    }

    /**
     * This is a method that can be overridden to set the initial camera mode and facing.
     * It's gonna have priority over shared preferences and intents.
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
                        primaryBar.performShutterClick()
                    }
                }
            }
        }

        with(secondaryTopBar) { bind() }
        with(secondaryBottomBar) { bind() }
        with(primaryBar) { bind() }
        with(modeSelector) { bind() }
        with(viewfinderOverlays) { bind() }
        with(cameraAlerts) { bind() }

        launch {
            viewModel.event.collect { event ->
                when (event) {
                    is Event.NoCamera -> noCamera()

                    is Event.ShowForceTorchHelp -> cameraAlerts.showForceTorchHelp()

                    is Event.FlipCameraAnimation ->
                        primaryBar.startFlipAnimation()

                    is Event.PhotoCapture -> when (val status = event.status) {
                        is PhotoCaptureStatus.CaptureStarted -> {
                            viewFinder.foreground = ColorDrawable(Color.BLACK)
                            ValueAnimator.ofInt(0, 255, 0).apply {
                                addUpdateListener { anim ->
                                    viewFinder.foreground.alpha = anim.animatedValue as Int
                                }
                            }.start()
                        }

                        is PhotoCaptureStatus.ImageSaved -> {
                            if (!viewModel.uiState.value.inSingleCaptureMode) {
                                onCapturedMedia(status.savedUri)
                            } else {
                                status.savedUri?.let {
                                    openCapturePreview(it, MediaType.PHOTO)
                                }
                                status.photoOutputStream?.use {
                                    openCapturePreview(
                                        ByteArrayInputStream(
                                            it.toByteArray()
                                        )
                                    )
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
            viewModel.uiState.select { it.screenRotation }.collectLatest { screenRotation ->
                // Rotate capture preview
                capturePreviewLayout.setScreenRotation(screenRotation)
            }
        }

        launch {
            viewModel.uiState.select { it.fullScreenBrightness }.collectLatest { fullScreenBrightness ->
                window.attributes = window.attributes.apply {
                    screenBrightness = when (fullScreenBrightness) {
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
                        if (videoRecordEvent.hasValidData) {
                            if (!viewModel.uiState.value.inSingleCaptureMode) {
                                onCapturedMedia(videoRecordEvent.outputUri)
                            } else {
                                openCapturePreview(
                                    videoRecordEvent.outputUri,
                                    MediaType.VIDEO,
                                )
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
                        ).show()
                        finish()
                        return@collect
                    }
                }

                initialized = true
            }
        }
    }

    /**
     * Rebind cameraProvider use cases
     */
    private fun bindCameraUseCases(cameraConfiguration: CameraConfiguration) {
        require(viewModel.canRestartCamera()) {
            "Trying to rebind camera while camera is not in IDLE state"
        }

        viewfinderOverlays.onRebind()

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
     * When the user took a photo or a video and confirmed it, its URI gets sent back to the
     * app that sent the intent and closes the camera.
     */
    private fun sendIntentResultAndExit(input: Any) {
        val (resultCode, data) = CaptureIntentResult.build(contentResolver, intent, input)
        setResult(resultCode, data)
        finish()
    }

    private fun onShutter() {
        // Shutter animation
        when (viewModel.uiState.value.cameraMode) {
            CameraMode.PHOTO -> primaryBar.startShutterAnimation(PrimaryBar.ShutterAnimation.PhotoCapture)
            CameraMode.VIDEO -> {
                if (viewfinderOverlays.cancelCountDown()) {
                    viewModel.endCountdown()
                    primaryBar.startShutterAnimation(PrimaryBar.ShutterAnimation.VideoEnd)
                    return
                }
                if (viewModel.uiState.value.cameraState == CameraState.IDLE) {
                    primaryBar.startShutterAnimation(PrimaryBar.ShutterAnimation.VideoStart)
                }
            }

            else -> {}
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

        viewfinderOverlays.startCountDown(timerModeSeconds) {
            viewModel.endCountdown()
            runnable()
        }
    }

    /**
     * Show a toast warning the user that no camera is available and close the activity.
     */
    private fun noCamera() {
        Toast.makeText(
            this, R.string.error_no_cameras_available, Toast.LENGTH_LONG
        ).show()
        finish()
    }

    /**
     * Method called when a new media have been successfully captured and saved.
     */
    private fun onCapturedMedia(item: Uri?) {
        primaryBar.updateGalleryButton(item, true)
        galleryLauncher.onCapturedMedia(item)
    }

}
