/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.processing

class BurstMetadata(
    val colorFilterArrangement: Int,
    val blackLevel: FloatArray,
    val whiteLevel: Float,
    val noiseProfile: Array<DoubleArray>,
    val whiteBalance: FloatArray,
    val colorTransform: FloatArray,
    val lensShading: LensShading?,
    val faces: List<IntArray>,
)

class LensShading(val columns: Int, val rows: Int, val gains: FloatArray)
