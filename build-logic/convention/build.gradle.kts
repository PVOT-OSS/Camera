/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins { `kotlin-dsl` }

dependencies { compileOnly(libs.android.gradlePlugin) }

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "camera.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
    }
}
