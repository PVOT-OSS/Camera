/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

object JpegExif {
    private const val ORIENTATION_TAG = 0x0112
    private const val SHORT_TYPE = 3

    private val HORIZONTAL_FLIP = mapOf(
        1 to 2,
        2 to 1,
        3 to 4,
        4 to 3,
        5 to 8,
        6 to 7,
        7 to 6,
        8 to 5,
    )

    fun flipHorizontally(jpeg: ByteArray): Boolean {
        val offset = orientationOffset(jpeg) ?: return false
        val (position, bigEndian) = offset

        val orientation = readShort(jpeg, position, bigEndian)
        val flipped = HORIZONTAL_FLIP[orientation] ?: HORIZONTAL_FLIP.getValue(1)
        writeShort(jpeg, position, flipped, bigEndian)

        return true
    }

    fun orientation(jpeg: ByteArray) = orientationOffset(jpeg)?.let { (position, bigEndian) ->
        readShort(jpeg, position, bigEndian)
    }

    private fun orientationOffset(jpeg: ByteArray): Pair<Int, Boolean>? {
        if (jpeg.size < 4 || u8(jpeg, 0) != 0xFF || u8(jpeg, 1) != 0xD8) {
            return null
        }

        var position = 2
        while (position + 4 <= jpeg.size && u8(jpeg, position) == 0xFF) {
            val marker = u8(jpeg, position + 1)
            val length = readShort(jpeg, position + 2, true)
            if (marker == 0xDA || length < 2) {
                return null
            }

            if (marker == 0xE1 && isExif(jpeg, position + 4)) {
                return ifd0Orientation(jpeg, position + 10, position + 2 + length)
            }

            position += 2 + length
        }

        return null
    }

    private fun isExif(jpeg: ByteArray, position: Int) = position + 6 <= jpeg.size
            && jpeg[position] == 'E'.code.toByte()
            && jpeg[position + 1] == 'x'.code.toByte()
            && jpeg[position + 2] == 'i'.code.toByte()
            && jpeg[position + 3] == 'f'.code.toByte()
            && jpeg[position + 4] == 0.toByte()
            && jpeg[position + 5] == 0.toByte()

    private fun ifd0Orientation(jpeg: ByteArray, tiff: Int, end: Int): Pair<Int, Boolean>? {
        if (tiff + 8 > end) {
            return null
        }

        val bigEndian = when {
            u8(jpeg, tiff) == 'M'.code && u8(jpeg, tiff + 1) == 'M'.code -> true
            u8(jpeg, tiff) == 'I'.code && u8(jpeg, tiff + 1) == 'I'.code -> false
            else -> return null
        }

        val ifd0 = tiff + readInt(jpeg, tiff + 4, bigEndian)
        if (ifd0 + 2 > end) {
            return null
        }

        val entries = readShort(jpeg, ifd0, bigEndian)
        for (index in 0 until entries) {
            val entry = ifd0 + 2 + index * 12
            if (entry + 12 > end) {
                return null
            }
            if (readShort(jpeg, entry, bigEndian) == ORIENTATION_TAG
                && readShort(jpeg, entry + 2, bigEndian) == SHORT_TYPE
            ) {
                return (entry + 8) to bigEndian
            }
        }

        return null
    }

    private fun u8(bytes: ByteArray, position: Int) = bytes[position].toInt() and 0xFF

    private fun readShort(bytes: ByteArray, position: Int, bigEndian: Boolean) = when (bigEndian) {
        true -> (u8(bytes, position) shl 8) or u8(bytes, position + 1)
        false -> (u8(bytes, position + 1) shl 8) or u8(bytes, position)
    }

    private fun readInt(bytes: ByteArray, position: Int, bigEndian: Boolean) = when (bigEndian) {
        true -> (readShort(bytes, position, true) shl 16) or readShort(bytes, position + 2, true)
        false -> (readShort(bytes, position + 2, false) shl 16) or readShort(bytes, position, false)
    }

    private fun writeShort(bytes: ByteArray, position: Int, value: Int, bigEndian: Boolean) {
        val high = (value shr 8 and 0xFF).toByte()
        val low = (value and 0xFF).toByte()
        when (bigEndian) {
            true -> {
                bytes[position] = high
                bytes[position + 1] = low
            }

            false -> {
                bytes[position] = low
                bytes[position + 1] = high
            }
        }
    }
}
