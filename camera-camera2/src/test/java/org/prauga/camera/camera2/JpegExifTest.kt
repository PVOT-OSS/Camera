/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.camera2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JpegExifTest {
    private fun jpeg(orientation: Int, bigEndian: Boolean): ByteArray {
        fun short(value: Int) =
            if (bigEndian) {
                listOf(value shr 8, value and 0xFF)
            } else {
                listOf(value and 0xFF, value shr 8)
            }

        fun int(value: Int) =
            if (bigEndian) {
                short(value shr 16) + short(value and 0xFFFF)
            } else {
                short(value and 0xFFFF) + short(value shr 16)
            }

        val tiff =
            (if (bigEndian) listOf(0x4D, 0x4D) else listOf(0x49, 0x49)) +
                short(42) +
                int(8) +
                short(1) +
                short(0x0112) +
                short(3) +
                int(1) +
                short(orientation) +
                short(0) +
                int(0)
        val exif = listOf(0x45, 0x78, 0x69, 0x66, 0, 0) + tiff
        val app1 =
            listOf(0xFF, 0xE1) + listOf((exif.size + 2) shr 8, (exif.size + 2) and 0xFF) + exif

        return (listOf(0xFF, 0xD8) + app1 + listOf(0xFF, 0xDA, 0, 2, 0xFF, 0xD9))
            .map { it.toByte() }
            .toByteArray()
    }

    @Test
    fun flipsTheOrientationInPlace() {
        listOf(false, true).forEach { bigEndian ->
            mapOf(1 to 2, 6 to 7, 8 to 5, 3 to 4).forEach { (from, to) ->
                val bytes = jpeg(from, bigEndian)
                assertTrue(JpegExif.flipHorizontally(bytes))
                assertEquals(to, JpegExif.orientation(bytes))
            }
        }
    }

    @Test
    fun keepsTheSize() {
        val bytes = jpeg(6, bigEndian = false)
        val size = bytes.size
        JpegExif.flipHorizontally(bytes)
        assertEquals(size, bytes.size)
    }

    @Test
    fun ignoresAJpegWithoutExif() {
        val bytes =
            listOf(0xFF, 0xD8, 0xFF, 0xDA, 0, 2, 0xFF, 0xD9).map { it.toByte() }.toByteArray()
        assertFalse(JpegExif.flipHorizontally(bytes))
        assertNull(JpegExif.orientation(bytes))
    }
}
