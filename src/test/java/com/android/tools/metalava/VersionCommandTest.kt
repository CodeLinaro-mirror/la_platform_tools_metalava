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

package com.android.tools.metalava

import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Assert
import org.junit.Test

class VersionCommandTest : DriverTest() {

    @Test
    fun `Test help`() {
        val args = listOf(ARG_NO_COLOR, "version", "--help")

        val stdout = StringWriter()
        val stderr = StringWriter()
        run(
            originalArgs = args.toTypedArray(),
            stdout = PrintWriter(stdout),
            stderr = PrintWriter(stderr)
        )
        Assert.assertEquals("", stderr.toString())
        Assert.assertEquals(
            """
$BANNER

Usage: metalava version [options]

  Show the version

Options:
  -h, -?, --help  Show this message and exit

            """
                .trimIndent(),
            stdout.toString()
        )
    }

    @Test
    fun `Test output`() {
        val args = listOf(ARG_NO_BANNER, "version")

        val stdout = StringWriter()
        val stderr = StringWriter()
        run(
            originalArgs = args.toTypedArray(),
            stdout = PrintWriter(stdout),
            stderr = PrintWriter(stderr)
        )
        Assert.assertEquals("", stderr.toString())
        Assert.assertEquals(
            """
version version: ${Version.VERSION}

            """
                .trimIndent(),
            stdout.toString()
        )
    }
}
