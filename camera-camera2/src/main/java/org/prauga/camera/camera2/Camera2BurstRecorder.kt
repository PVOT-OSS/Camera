/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class Camera2BurstRecorder
private constructor(
    context: Context,
    private val scope: CoroutineScope,
    private val handler: Handler,
    private val characteristics: CameraCharacteristics,
    width: Int,
    height: Int,
) {
    private val outputDirectory = context.getExternalFilesDir(DIRECTORY)
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val imageReader: ImageReader =
        ImageReader.newInstance(width, height, ImageFormat.RAW_SENSOR, FRAMES + 1).apply {
            setOnImageAvailableListener(::onImageAvailable, handler)
        }

    private var burst: Burst? = null

    private class Burst(val directory: File, val exifOrientation: Int) {
        val results = mutableMapOf<Long, TotalCaptureResult>()
        val images = mutableMapOf<Long, Image>()
        val frames = JSONArray()
        val gyro = StringBuilder("timestamp_ns,x,y,z\n")
        var started = 0
        var written = 0
    }

    private val gyroscopeListener =
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                burst
                    ?.gyro
                    ?.append(
                        "${event.timestamp},${event.values[0]},${event.values[1]},${event.values[2]}\n"
                    )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

    fun start(jpegOrientation: Int) {
        burst?.let {
            Log.w(LOG_TAG, "Dropping an unfinished burst in ${it.directory}")
            it.images.values.forEach { image -> image.close() }
        }

        val name = SimpleDateFormat(DIRECTORY_FORMAT, Locale.US).format(System.currentTimeMillis())
        val directory = File(outputDirectory, name).apply { mkdirs() }

        burst = Burst(directory, Camera2RawCapture.exifOrientation(jpegOrientation))

        gyroscope?.let {
            try {
                sensorManager.registerListener(
                    gyroscopeListener,
                    it,
                    GYROSCOPE_SAMPLING_PERIOD_US,
                    handler,
                )
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Unable to record the gyroscope", e)
            }
        }
    }

    fun onCaptureCompleted(result: TotalCaptureResult) {
        val burst = burst ?: return
        val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return

        burst.results[timestamp] = result
        writeReadyFrames(burst)
    }

    fun close() {
        sensorManager?.unregisterListener(gyroscopeListener)
        burst?.images?.values?.forEach { it.close() }
        burst = null

        imageReader.close()
    }

    private fun onImageAvailable(imageReader: ImageReader) {
        val image = imageReader.acquireNextImage() ?: return
        val burst =
            burst
                ?: run {
                    image.close()
                    return
                }

        burst.images[image.timestamp] = image
        writeReadyFrames(burst)
    }

    private fun writeReadyFrames(burst: Burst) {
        val ready = burst.images.keys.filter { it in burst.results }
        for (timestamp in ready) {
            val image = burst.images.remove(timestamp) ?: continue
            val result = burst.results.remove(timestamp) ?: continue
            val index = burst.started++

            burst.frames.put(frameMetadata(index, result))

            scope.launch(Dispatchers.IO) {
                try {
                    File(burst.directory, "frame-%02d.dng".format(Locale.US, index))
                        .outputStream()
                        .use { output ->
                            DngCreator(characteristics, result)
                                .setOrientation(burst.exifOrientation)
                                .writeImage(output, image)
                        }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Unable to write frame $index", e)
                } finally {
                    image.close()
                }

                handler.post { onFrameWritten(burst) }
            }
        }
    }

    private fun onFrameWritten(burst: Burst) {
        burst.written++
        if (burst.written < FRAMES) {
            return
        }

        sensorManager?.unregisterListener(gyroscopeListener)
        if (this.burst === burst) {
            this.burst = null
        }

        scope.launch(Dispatchers.IO) {
            File(burst.directory, "metadata.json")
                .writeText(
                    JSONObject()
                        .put("frames", burst.frames)
                        .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                        .put("fingerprint", Build.FINGERPRINT)
                        .toString(2)
                )
            File(burst.directory, "gyro.csv").writeText(burst.gyro.toString())

            Log.i(LOG_TAG, "Recorded $FRAMES frames to ${burst.directory}")
        }
    }

    private fun frameMetadata(index: Int, result: TotalCaptureResult) =
        JSONObject().apply {
            put("index", index)
            put("timestampNs", result.get(CaptureResult.SENSOR_TIMESTAMP))
            put("exposureTimeNs", result.get(CaptureResult.SENSOR_EXPOSURE_TIME))
            put("frameDurationNs", result.get(CaptureResult.SENSOR_FRAME_DURATION))
            put("sensitivity", result.get(CaptureResult.SENSOR_SENSITIVITY))
            put(
                "postRawSensitivityBoost",
                result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST),
            )
            put("focusDistance", result.get(CaptureResult.LENS_FOCUS_DISTANCE)?.toDouble())
            result.get(CaptureResult.SENSOR_NOISE_PROFILE)?.let { noiseProfile ->
                put(
                    "noiseProfile",
                    JSONArray(noiseProfile.map { JSONArray(listOf(it.first, it.second)) }),
                )
            }
            result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL)?.let { blackLevel ->
                put("blackLevel", JSONArray(blackLevel.map { it.toDouble() }))
            }
            result.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL)?.let { put("whiteLevel", it) }
            result.get(CaptureResult.COLOR_CORRECTION_GAINS)?.let { gains ->
                put(
                    "colorCorrectionGains",
                    JSONArray(
                        listOf(gains.red, gains.greenEven, gains.greenOdd, gains.blue).map {
                            it.toDouble()
                        }
                    ),
                )
            }
            result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP)?.let { map ->
                put(
                    "lensShadingMap",
                    JSONObject().apply {
                        put("columns", map.columnCount)
                        put("rows", map.rowCount)
                        put(
                            "gains",
                            JSONArray(
                                FloatArray(map.gainFactorCount)
                                    .also { map.copyGainFactors(it, 0) }
                                    .map { it.toDouble() }
                            ),
                        )
                    },
                )
            }
        }


    companion object {
        private const val LOG_TAG = "Camera2BurstRecorder"
        private const val DIRECTORY = "bursts"
        private const val DIRECTORY_FORMAT = "yyyyMMdd-HHmmss-SSS"
        private const val GYROSCOPE_SAMPLING_PERIOD_US = 5000

        const val FRAMES = 8

        fun create(
            context: Context,
            scope: CoroutineScope,
            handler: Handler,
            characteristics: CameraCharacteristics,
        ): Camera2BurstRecorder? {
            val capabilities =
                characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?: return null
            if (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW !in capabilities) {
                return null
            }

            val size =
                characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.RAW_SENSOR)
                    ?.maxByOrNull { it.width.toLong() * it.height } ?: return null

            return Camera2BurstRecorder(
                context,
                scope,
                handler,
                characteristics,
                size.width,
                size.height,
            )
        }
    }
}
