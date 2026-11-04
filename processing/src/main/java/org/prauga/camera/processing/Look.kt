/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.processing

import org.json.JSONArray
import org.json.JSONObject

class Look(
    val matrix: FloatArray,
    val lumaCurve: FloatArray,
    val tint: Array<FloatArray>,
    val chroma: Float,
    val sigmas: FloatArray,
    val gains: FloatArray,
    val thresholds: FloatArray,
) {
    companion object {
        val IDENTITY =
            Look(
                matrix = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
                lumaCurve = floatArrayOf(0f, 1f),
                tint = Array(3) { floatArrayOf(1f, 1f) },
                chroma = 1f,
                sigmas = floatArrayOf(),
                gains = floatArrayOf(),
                thresholds = floatArrayOf(),
            )

        fun parse(json: String): Look {
            val root = JSONObject(json)
            val detail = root.getJSONObject("detail")
            val sigmas = floats(detail.getJSONArray("sigmas"))
            return Look(
                matrix =
                    root.getJSONArray("matrix").let { rows ->
                        FloatArray(9) { rows.getJSONArray(it / 3).getDouble(it % 3).toFloat() }
                    },
                lumaCurve = floats(root.getJSONArray("luma_curve")),
                tint =
                    root.getJSONArray("tint").let { tint ->
                        Array(3) { floats(tint.getJSONArray(it)) }
                    },
                chroma = root.optDouble("chroma", 1.0).toFloat(),
                sigmas = sigmas,
                gains = floats(detail.getJSONArray("gains")),
                thresholds =
                    detail.optJSONArray("thresholds")?.let(::floats) ?: FloatArray(sigmas.size),
            )
        }

        private fun floats(array: JSONArray) =
            FloatArray(array.length()) { array.getDouble(it).toFloat() }
    }
}
