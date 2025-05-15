/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.apilevels.VersionedSignatureApi.Companion.stringsToBashBraceExpansion
import java.io.File

/**
 * Supports updating [Api] with information from the version of the API that is defined in [jar].
 *
 * The [updater] is responsible for updating the [Api].
 */
class VersionedJarApi(
    val files: List<File>,
    updater: ApiHistoryUpdater,
    private val filter: ((String) -> Boolean)? = null,
) : VersionedApi(updater) {
    override fun updateApi(api: Api) {
        for (file in files) {
            api.readJar(file, updater, filter)
        }
    }

    override fun toString(): String {
        // Compute the string representation of the files. Listing a number of potentially long
        // files all on one line can make it difficult to debug. As the files are likely to contain
        // common prefixes and suffixes, e.g. `prebuilts/sdk/28/public/api/android.txt` and
        // `prebuilts/sdk/28/system/api/android.txt` this replaces it with a string that uses bash
        // brace expansion syntax so it would generate all the original if used in bash, e.g.
        // `prebuilts/sdk/28/{public,system}/api/android.txt`.
        val filesAsString = stringsToBashBraceExpansion(files.map { it.path })
        return "VersionedJarApi(jar=$filesAsString, updater=$updater)"
    }
}
