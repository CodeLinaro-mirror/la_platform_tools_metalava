/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.text.SignatureFile
import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VersionedSignatureApiTest {
    companion object {}

    @Test
    fun `Empty files`() {
        val fakeSignatureFileLoader =
            object : SignatureFileLoader {
                override fun load(
                    signatureFiles: List<SignatureFile>,
                    classResolver: ClassResolver?
                ): Codebase {
                    error("fake")
                }
            }

        val versionOne = ApiVersion.fromLevel(1)
        val updater = ApiHistoryUpdater.forApiVersion(versionOne)

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                VersionedSignatureApi(fakeSignatureFileLoader, emptyList(), updater)
            }
        assertEquals("files must contain at least one file", exception.message)
    }

    private fun checkStringsToBraceExpansion(
        strings: List<String>,
        expectedBraceExpansion: String
    ) {
        val braceExpansion = VersionedSignatureApi.stringsToBashBraceExpansion(strings)
        assertEquals(expectedBraceExpansion, braceExpansion)
    }

    @Test
    fun `Single file - toString`() {
        val strings = listOf("single-path")
        checkStringsToBraceExpansion(strings, "single-path")
    }

    @Test
    fun `Common suffix but no common prefix`() {
        val strings = listOf("alpha/common", "beta/common")
        checkStringsToBraceExpansion(strings, "{alph,bet}a/common")
    }

    @Test
    fun `Common prefix but no common suffix`() {
        val strings = listOf("common/alpha", "common/epsilon")
        checkStringsToBraceExpansion(strings, "common/{alpha,epsilon}")
    }

    @Test
    fun `No common prefix or suffix`() {
        val strings = listOf("alpha/beta", "gamma/epsilon")
        checkStringsToBraceExpansion(strings, "{alpha/beta,gamma/epsilon}")
    }

    @Test
    fun `Common prefix and common suffix`() {
        val strings =
            listOf(
                "prebuilts/sdk/28/public/api/android.txt",
                "prebuilts/sdk/28/system/api/android.txt",
            )
        checkStringsToBraceExpansion(strings, "prebuilts/sdk/28/{public,system}/api/android.txt")
    }
}
