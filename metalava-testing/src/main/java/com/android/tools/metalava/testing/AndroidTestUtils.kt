/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.metalava.testing

import java.io.File

/**
 * The default API level used in tests.
 *
 * Set to 34 as that was the first version in which the `java.lang.Record` class was added.
 */
private const val API_LEVEL = 34

/**
 * Check to see if this file is the top level metalava directory by looking for a `metalava-model`
 * directory.
 */
private fun File.isMetalavaRootDir(): Boolean = resolve("metalava-model").isDirectory

/** Get a [File] for the public `android.jar` of [API_LEVEL]. */
fun getAndroidJar(): File {
    val localFile = getPrebuiltsSdkDir().resolve("$API_LEVEL/public/android.jar")
    if (!localFile.exists()) {
        error("Missing ${localFile.absolutePath} file in the SDK")
    }

    return localFile
}

/** Get a [File] for the [apiSurface] `android.txt` of the specified [apiLevel]. */
fun getAndroidTxt(apiLevel: Int = API_LEVEL, apiSurface: String = "public"): File {
    val localFile = getPrebuiltsSdkDir().resolve("$apiLevel/$apiSurface/api/android.txt")
    if (!localFile.exists()) {
        error("Missing ${localFile.absolutePath} file in the SDK")
    }

    return localFile
}

private fun getMetalavaDir(): File {
    // This is either running in tools/metalava or tools/metalava/subproject-dir and we need to look
    // in prebuilts/sdk, so first find tools/metalava then resolve relative to that.
    val cwd = File("").absoluteFile
    val metalavaDir =
        if (cwd.isMetalavaRootDir()) cwd
        else {
            val parent = cwd.parentFile
            if (parent.isMetalavaRootDir()) parent
            else {
                throw IllegalArgumentException("Could not find metalava-model in $cwd")
            }
        }
    return metalavaDir
}

/** The `prebuilts/sdk` directory used for testing. */
fun getPrebuiltsSdkDir(): File = getMetalavaDir().resolve("test-data/prebuilts-sdk")
