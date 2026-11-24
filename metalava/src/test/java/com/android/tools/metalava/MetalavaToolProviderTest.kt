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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.ARG_NO_COLOR
import com.android.tools.metalava.cli.common.ARG_WARNING
import com.android.tools.metalava.cli.common.Version
import com.android.tools.metalava.cli.compatibility.ARG_CHECK_COMPATIBILITY_API_RELEASED
import com.android.tools.metalava.testing.BaseTemporaryFolderOwner
import java.io.PrintWriter
import java.io.StringWriter
import java.util.spi.ToolProvider
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MetalavaToolProviderTest : BaseTemporaryFolderOwner() {
    @Test
    fun `Test findFirst and run version`() {
        val toolProvider = ToolProvider.findFirst(PROGRAM_NAME).orElse(null)
        assertIs<MetalavaToolProvider>(toolProvider)
        assertEquals(PROGRAM_NAME, toolProvider.name())

        val stdout = StringWriter()
        val stderr = StringWriter()
        val exitCode =
            toolProvider.run(
                PrintWriter(stdout),
                PrintWriter(stderr),
                ARG_NO_COLOR,
                "--version",
            )
        assertEquals(0, exitCode)
        assertEquals("", stderr.toString())
        assertEquals("\nmetalava version: ${Version.VERSION}\n", stdout.toString())
    }

    @Test
    fun `Test run with invalid option writes to err and returns non-zero`() {
        val toolProvider = ToolProvider.findFirst(PROGRAM_NAME).get()

        val stdout = StringWriter()
        val stderr = StringWriter()
        val exitCode =
            toolProvider.run(
                PrintWriter(stdout),
                PrintWriter(stderr),
                ARG_NO_COLOR,
                "--invalid-option",
            )
        assertEquals(1, exitCode)
        assertEquals("", stdout.toString())
        assertTrue(
            stderr
                .toString()
                .trim()
                .startsWith("Aborting: Error: no such option: \"--invalid-option\""),
            "Unexpected stderr: $stderr",
        )
    }

    @Test
    fun `Test run routes reporter warnings to out and errors to err`() {
        val toolProvider = ToolProvider.findFirst(PROGRAM_NAME).get()

        val releasedApi =
            temporaryFolder.newFile("released.txt").apply {
                writeText(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Bar {
                          }
                          public class Foo {
                          }
                        }
                    """
                        .trimIndent()
                )
            }
        val currentApi =
            temporaryFolder.newFile("current.txt").apply {
                writeText(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method public void added();
                          }
                        }
                    """
                        .trimIndent()
                )
            }

        val stdout = StringWriter()
        val stderr = StringWriter()
        val exitCode =
            toolProvider.run(
                PrintWriter(stdout),
                PrintWriter(stderr),
                ARG_NO_COLOR,
                currentApi.path,
                ARG_CHECK_COMPATIBILITY_API_RELEASED,
                releasedApi.path,
                ARG_WARNING,
                "AddedMethod",
            )
        assertEquals(-1, exitCode)
        assertEquals(
            "${currentApi.path}:4: warning: Added method test.pkg.Foo.added() [AddedMethod]\n",
            stdout.toString(),
        )
        assertEquals(
            """
                ${releasedApi.path}:3: error: Binary breaking change: Removed class test.pkg.Bar [RemovedClass]

                Aborting: Found compatibility problems checking the public API (${currentApi.path}) against the API in ${releasedApi.path}

            """
                .trimIndent(),
            stderr.toString(),
        )
    }
}
