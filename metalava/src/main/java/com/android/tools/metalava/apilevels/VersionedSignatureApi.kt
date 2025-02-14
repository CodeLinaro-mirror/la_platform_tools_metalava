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

import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.snapshot.NonFilteringDelegatingVisitor
import com.android.tools.metalava.model.text.SignatureFile
import java.io.File

/**
 * Supports updating [Api] with information from the [apiVersion] of the API that is defined in the
 * signature [files].
 */
class VersionedSignatureApi(
    private val signatureFileLoader: SignatureFileLoader,
    private val files: List<File>,
    updater: ApiHistoryUpdater,
) : VersionedApi(updater) {

    init {
        require(files.isNotEmpty()) { "files must contain at least one file" }
    }

    override fun updateApi(api: Api) {
        val codebase = signatureFileLoader.load(SignatureFile.fromFiles(files))
        val codebaseFragment = CodebaseFragment.create(codebase, ::NonFilteringDelegatingVisitor)
        addApisFromCodebase(api, updater, codebaseFragment)
    }

    override fun toString(): String {
        // Compute the string representation of the files. Listing a number of potentially long
        // files all on one line can make it difficult to debug. As the files are likely to contain
        // common prefixes and suffixes, e.g. `prebuilts/sdk/28/public/api/android.txt` and
        // `prebuilts/sdk/28/system/api/android.txt` this replaces it with a string that uses bash
        // brace expansion syntax so it would generate all the original if used in bash, e.g.
        // `prebuilts/sdk/28/{public,system}/api/android.txt`.
        val filesAsString = stringsToBashBraceExpansion(files.map { it.path })
        return "VersionedSignatureApi(files=$filesAsString, updater=$updater)"
    }

    companion object {
        /** Generate a bash string expansion that will generate [strings]. */
        internal fun stringsToBashBraceExpansion(strings: List<String>) =
            if (strings.size == 1) {
                strings.first()
            } else {
                val commonPrefix = strings.reduce { p1, p2 -> p1.commonPrefixWith(p2) }
                val commonSuffix = strings.reduce { p1, p2 -> p1.commonSuffixWith(p2) }
                buildString {
                    append(commonPrefix)
                    append("{")
                    strings.joinTo(this, ",") {
                        it.removePrefix(commonPrefix).removeSuffix(commonSuffix)
                    }
                    append("}")
                    append(commonSuffix)
                }
            }
    }
}
