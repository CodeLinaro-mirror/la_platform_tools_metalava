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

package com.android.tools.metalava.cli.common

import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.android.tools.metalava.testing.signature
import com.android.tools.metalava.testing.source
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviouslyReleasedApiTest : TemporaryFolderOwner {

    @get:Rule override val temporaryFolder = TemporaryFolder()

    companion object {
        private const val OPTION_NAME = "--files"
    }

    @Test
    fun `check no files`() {
        val previouslyReleasedApi =
            PreviouslyReleasedApi.optionalPreviouslyReleasedApi(OPTION_NAME, emptyList())
        assertThat(previouslyReleasedApi).isNull()
    }

    @Test
    fun `check multiple signature files`() {
        val file1 =
            signature("released1.txt", "// Signature format: 2.0\n")
                .createFile(temporaryFolder.root)
        val file2 =
            signature("released2.txt", "// Signature format: 2.0\n")
                .createFile(temporaryFolder.root)

        val previouslyReleasedApi =
            PreviouslyReleasedApi.optionalPreviouslyReleasedApi(OPTION_NAME, listOf(file1, file2))
        assertThat(previouslyReleasedApi)
            .isEqualTo(SignatureBasedApi.fromFiles(listOf(file1, file2)))
    }

    /**
     * Create a fake jar file. It is ok that it is not actually a jar file as its contents are not
     * read.
     */
    private fun fakeJar(name: String) = source(name, "PK...").createFile(temporaryFolder.root)

    @Test
    fun `check jar file`() {
        val jarFile = fakeJar("some.jar")
        val exception =
            assertThrows(IllegalStateException::class.java) {
                PreviouslyReleasedApi.optionalPreviouslyReleasedApi(OPTION_NAME, listOf(jarFile))
            }
        assertThat(exception.message)
            .isEqualTo(
                "$OPTION_NAME: Can no longer check compatibility against jar files like $jarFile please use equivalent signature files"
            )
    }

    @Test
    fun `check multiple jar files`() {
        val jarFile1 = fakeJar("some.jar")
        val jarFile2 = fakeJar("another.jar")
        val exception =
            assertThrows(IllegalStateException::class.java) {
                PreviouslyReleasedApi.optionalPreviouslyReleasedApi(
                    OPTION_NAME,
                    listOf(jarFile1, jarFile2)
                )
            }
        assertThat(exception.message)
            .isEqualTo(
                "$OPTION_NAME: Can no longer check compatibility against jar files like $jarFile1, $jarFile2 please use equivalent signature files"
            )
    }

    @Test
    fun `check mixture of signature and jar`() {
        val jarFile = fakeJar("some.jar")
        val signatureFile =
            signature("removed.txt", "// Signature format: 2.0\n").createFile(temporaryFolder.root)

        val exception =
            assertThrows(IllegalStateException::class.java) {
                PreviouslyReleasedApi.optionalPreviouslyReleasedApi(
                    OPTION_NAME,
                    listOf(jarFile, signatureFile)
                )
            }

        assertThat(exception.message)
            .isEqualTo(
                "$OPTION_NAME: Can no longer check compatibility against jar files like $jarFile please use equivalent signature files"
            )
    }
}
