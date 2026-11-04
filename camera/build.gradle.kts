/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins { id("camera.android.library") }

android { namespace = "org.prauga.camera.capture" }

dependencies {
    api(libs.androidx.lifecycle.common)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
