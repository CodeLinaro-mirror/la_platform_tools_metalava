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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.testing.TemporaryFolderOwner
import java.io.File
import kotlin.test.Test
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class ExtensionSdkJarReaderTest : TemporaryFolderOwner {

    /** Provides access to temporary files. */
    @get:Rule override val temporaryFolder = TemporaryFolder()

    @Test
    fun `Verify findExtensionSdkJarFiles`() {
        val root = buildFileStructure {
            dir("1") {
                dir("public") {
                    emptyFile("foo.jar")
                    emptyFile("bar.jar")
                }
            }
            dir("2") {
                dir("public") {
                    emptyFile("foo.jar")
                    emptyFile("bar.jar")
                    emptyFile("baz.jar")
                }
            }
        }

        val expected =
            mapOf(
                "foo" to
                    listOf(
                        MatchedPatternFile(
                            File(root, "1/public/foo.jar"),
                            ApiVersion.fromLevel(1),
                            module = "foo",
                        ),
                        MatchedPatternFile(
                            File(root, "2/public/foo.jar"),
                            ApiVersion.fromLevel(2),
                            module = "foo",
                        ),
                    ),
                "bar" to
                    listOf(
                        MatchedPatternFile(
                            File(root, "1/public/bar.jar"),
                            ApiVersion.fromLevel(1),
                            module = "bar",
                        ),
                        MatchedPatternFile(
                            File(root, "2/public/bar.jar"),
                            ApiVersion.fromLevel(2),
                            module = "bar",
                        ),
                    ),
                "baz" to
                    listOf(
                        MatchedPatternFile(
                            File(root, "2/public/baz.jar"),
                            ApiVersion.fromLevel(2),
                            module = "baz",
                        ),
                    ),
            )
        val actual = ExtensionSdkJarReader("public").findExtensionSdkJarFiles(root)
        assertEquals(expected, actual)
    }
}
