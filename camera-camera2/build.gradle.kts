/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("camera.android.library")
}

android {
    namespace = "org.prauga.camera.camera2"
}

dependencies {
    api(project(":camera"))
    api(project(":device"))

    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
