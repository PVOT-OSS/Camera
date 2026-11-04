/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.prauga.camera.processing.BurstMetadata
import org.prauga.camera.processing.BurstProcessor
import org.prauga.camera.processing.LensShading
import org.prauga.camera.processing.Look
import org.prauga.camera.processing.RawFrame

class Camera2BurstMerger(context: Context, private val characteristics: CameraCharacteristics) {
    private val contentResolver: ContentResolver = context.contentResolver
    private val look = loadLook(context.assets)

    fun merge(
        frames: List<RawFrame>,
        result: TotalCaptureResult,
        jpegOrientation: Int,
        directory: File,
    ): Uri {
        val started = System.currentTimeMillis()
        val processor = BurstProcessor(look)
        val size = processor.outputSize(frames[0])
        val photo = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val base = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)

        processor.process(frames, metadata(result)) { top, rows, width, photoRows, baseRows ->
            photo.setPixels(photoRows, 0, width, 0, top, width, rows)
            base.setPixels(baseRows, 0, width, 0, top, width, rows)
        }

        val orientedBase = rotate(base, jpegOrientation)
        File(directory, BASE_NAME).outputStream().use {
            orientedBase.compress(Bitmap.CompressFormat.JPEG, QUALITY, it)
        }
        orientedBase.recycle()

        val orientedPhoto = rotate(photo, jpegOrientation)
        val uri =
            Camera2MediaStore.savePhoto(contentResolver, MIME_TYPE, started, SUFFIX) {
                orientedPhoto.compress(Bitmap.CompressFormat.JPEG, QUALITY, it)
            }
        orientedPhoto.recycle()

        Log.i(
            LOG_TAG,
            "Merged ${frames.size} frames in ${System.currentTimeMillis() - started} ms to $uri",
        )
        return uri
    }

    private fun metadata(result: TotalCaptureResult): BurstMetadata {
        val blackPattern = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
        val blackLevel =
            result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL)?.takeIf { it.size == 4 }
                ?: FloatArray(4) {
                    blackPattern?.getOffsetForIndex(it and 1, it shr 1)?.toFloat() ?: 0f
                }
        val whiteLevel =
            (result.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL)
                    ?: characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)
                    ?: 1023)
                .toFloat()

        val noise = result.get(CaptureResult.SENSOR_NOISE_PROFILE)
        val noiseProfile =
            Array(4) { position ->
                noise?.getOrNull(position)?.let { doubleArrayOf(it.first, it.second) }
                    ?: doubleArrayOf(DEFAULT_NOISE_SCALE, DEFAULT_NOISE_OFFSET)
            }

        val gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS)
        val whiteBalance =
            if (gains != null) {
                floatArrayOf(gains.red, gains.greenEven, gains.greenOdd, gains.blue)
            } else {
                floatArrayOf(1f, 1f, 1f, 1f)
            }

        val transform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)
        val colorTransform =
            FloatArray(9) {
                transform?.getElement(it % 3, it / 3)?.toFloat() ?: if (it % 4 == 0) 1f else 0f
            }

        val shading =
            result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP)?.let { map ->
                LensShading(
                    map.columnCount,
                    map.rowCount,
                    FloatArray(map.gainFactorCount).also { map.copyGainFactors(it, 0) },
                )
            }

        val faces =
            result
                .get(CaptureResult.STATISTICS_FACES)
                ?.filter { it.score >= MIN_FACE_SCORE }
                ?.map {
                    intArrayOf(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom)
                }
                .orEmpty()

        return BurstMetadata(
            colorFilterArrangement =
                characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
                    ?: 0,
            blackLevel = blackLevel,
            whiteLevel = whiteLevel,
            noiseProfile = noiseProfile,
            whiteBalance = whiteBalance,
            colorTransform = colorTransform,
            lensShading = shading,
            faces = faces,
        )
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) {
            return bitmap
        }
        val rotated =
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                Matrix().apply { postRotate(degrees.toFloat()) },
                false,
            )
        bitmap.recycle()
        return rotated
    }

    companion object {
        private const val LOG_TAG = "Camera2BurstMerger"
        private const val MIME_TYPE = "image/jpeg"
        private const val SUFFIX = "-merged"
        private const val BASE_NAME = "base.jpg"
        private const val QUALITY = 95
        private const val MIN_FACE_SCORE = 50
        private const val DEFAULT_NOISE_SCALE = 1e-4
        private const val DEFAULT_NOISE_OFFSET = 1e-6

        fun copy(image: Image): RawFrame {
            val plane = image.planes[0]
            val source = plane.buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val copy = ByteBuffer.allocateDirect(source.remaining()).order(ByteOrder.LITTLE_ENDIAN)
            copy.put(source)
            copy.flip()
            return RawFrame(
                copy.asShortBuffer(),
                plane.rowStride / plane.pixelStride,
                image.width,
                image.height,
            )
        }

        private fun loadLook(assets: AssetManager): Look {
            val name = Build.MODEL.lowercase().replace(' ', '-')
            return try {
                assets.open("looks/$name.json").bufferedReader().use { Look.parse(it.readText()) }
            } catch (e: Exception) {
                Log.i(LOG_TAG, "No fitted look for $name, using the identity look")
                Look.IDENTITY
            }
        }
    }
}
