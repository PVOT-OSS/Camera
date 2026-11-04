/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import android.content.ContentResolver
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.util.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Camera2RawCapture
private constructor(
    private val contentResolver: ContentResolver,
    private val scope: CoroutineScope,
    private val characteristics: CameraCharacteristics,
    handler: Handler,
    size: Size,
) {
    private class Pending(
        val timestamp: Long,
        val exifOrientation: Int,
        val onDone: (Uri?, Exception?) -> Unit,
    ) {
        var image: Image? = null
        var result: TotalCaptureResult? = null
    }

    val imageReader: ImageReader =
        ImageReader.newInstance(size.width, size.height, ImageFormat.RAW_SENSOR, 2).apply {
            setOnImageAvailableListener(::onImageAvailable, handler)
        }

    private var pending: Pending? = null

    fun expect(timestamp: Long, jpegOrientation: Int, onDone: (Uri?, Exception?) -> Unit) {
        pending?.image?.close()
        pending = Pending(timestamp, exifOrientation(jpegOrientation), onDone)
    }

    fun onCaptureCompleted(result: TotalCaptureResult) {
        val pending = pending ?: return
        pending.result = result
        writeWhenReady(pending)
    }

    fun onCaptureFailed() {
        val pending = pending ?: return
        this.pending = null
        pending.image?.close()
        pending.onDone(null, null)
    }

    fun close() {
        pending?.image?.close()
        pending = null
        imageReader.close()
    }

    private fun onImageAvailable(imageReader: ImageReader) {
        val image = imageReader.acquireNextImage() ?: return
        val pending =
            pending
                ?: run {
                    image.close()
                    return
                }
        pending.image?.close()
        pending.image = image
        writeWhenReady(pending)
    }

    private fun writeWhenReady(pending: Pending) {
        val image = pending.image ?: return
        val result = pending.result ?: return
        this.pending = null

        scope.launch(Dispatchers.IO) {
            try {
                val uri =
                    Camera2MediaStore.savePhoto(contentResolver, MIME_TYPE, pending.timestamp) {
                        output ->
                        DngCreator(characteristics, result).use { dngCreator ->
                            dngCreator.setOrientation(pending.exifOrientation).writeImage(output, image)
                        }
                    }
                pending.onDone(uri, null)
            } catch (e: Exception) {
                pending.onDone(null, e)
            } finally {
                image.close()
            }
        }
    }

    companion object {
        private const val MIME_TYPE = "image/x-adobe-dng"

        fun exifOrientation(jpegOrientation: Int) =
            when (jpegOrientation) {
                90 -> 6
                180 -> 3
                270 -> 8
                else -> 1
            }

        fun create(
            contentResolver: ContentResolver,
            scope: CoroutineScope,
            characteristics: CameraCharacteristics,
            handler: Handler,
        ): Camera2RawCapture? {
            val sizes =
                characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(ImageFormat.RAW_SENSOR)
                    ?.takeIf { it.isNotEmpty() } ?: return null

            val pixelArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val preCorrectionArray =
                characteristics.get(
                    CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE
                )
            val size =
                sizes.firstOrNull {
                    (it.width == preCorrectionArray?.width() &&
                        it.height == preCorrectionArray.height()) ||
                        (it.width == pixelArraySize?.width && it.height == pixelArraySize.height)
                } ?: sizes.maxBy { it.width.toLong() * it.height }

            return Camera2RawCapture(contentResolver, scope, characteristics, handler, size)
        }
    }
}
