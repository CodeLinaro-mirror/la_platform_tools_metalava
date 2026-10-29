/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.cli.util

import com.android.tools.metalava.ARG_API
import com.android.tools.metalava.ARG_REMOVED_API
import com.android.tools.metalava.cli.signature.ARG_FORMAT
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.text.assertSignatureFilesMatch
import com.android.tools.metalava.testing.TemporaryFolderOwner
import java.io.File
import kotlin.io.readText
import org.junit.Assert.assertTrue

/** [TestOptionGroup] for the signature files expected to be generated for an API surface. */
fun TemporaryFolderOwner.signatureOptions(
    expectedApiSignature: String?,
    expectedRemovedApiSignature: String? = null,
    format: FileFormat = FileFormat.V5,
    name: String = "public",
): TestOptionGroup {
    val apiFile: File = getOrCreateFile("$name-api.txt")
    val removedApiFile: File?

    return object : TestOptionGroup() {
        override val args: Array<String> =
            buildList {
                    add("$ARG_FORMAT=${format.specifier()}")
                    add(ARG_API)
                    add(apiFile.path)

                    if (expectedRemovedApiSignature != null) {
                        removedApiFile = newFile("$name-removed.txt")
                        add(ARG_REMOVED_API)
                        add(removedApiFile.path)
                    } else {
                        removedApiFile = null
                    }
                }
                .toTypedArray()

        override fun check() {
            if (expectedApiSignature != null) {
                assertTrue(
                    "${apiFile.path} does not exist even though $ARG_API was used",
                    apiFile.exists()
                )
                assertSignatureFilesMatch(
                    expectedApiSignature,
                    apiFile.readText(),
                    expectedFormat = format
                )
                // Make sure we can read back the files we write
                ApiFile.parseApi(SignatureFile.fromFiles(apiFile), Codebase.Config.NOOP)
            }
            if (expectedRemovedApiSignature != null) {
                assertTrue(
                    "${removedApiFile!!.path} does not exist even though $ARG_REMOVED_API was used",
                    removedApiFile.exists()
                )
                assertSignatureFilesMatch(
                    expectedRemovedApiSignature,
                    removedApiFile.readText(),
                    expectedFormat = format
                )
                // Make sure we can read back the files we write
                ApiFile.parseApi(SignatureFile.fromFiles(removedApiFile), Codebase.Config.NOOP)
            }
        }
    }
}
