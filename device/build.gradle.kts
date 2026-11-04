/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins { id("camera.android.library") }

android { namespace = "org.prauga.camera.device" }

dependencies {
    api(project(":camera"))

    testImplementation(libs.junit)
}
