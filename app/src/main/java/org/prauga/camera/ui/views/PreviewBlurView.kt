/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.ui.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import org.prauga.camera.ext.stackBlur

/** Display a blurred viewfinder snapshot during camera rebind. */
class PreviewBlurView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AppCompatImageView(context, attrs, defStyleAttr) {
    var previewBitmap: () -> Bitmap? = { null }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(RenderEffect.createBlurEffect(RADIUS, RADIUS, Shader.TileMode.REPEAT))
        }
    }

    fun freeze() {
        previewBitmap()?.also {
            setImageBitmap(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    it
                } else {
                    it.stackBlur(RADIUS.toInt())
                }
            )
        } ?: setImageResource(android.R.color.black)
    }

    companion object {
        private const val RADIUS = 25f
    }
}
