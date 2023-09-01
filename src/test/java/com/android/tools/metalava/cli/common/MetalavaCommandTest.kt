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

package com.android.tools.metalava.cli.common

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Assert
import org.junit.Test

class MetalavaCommandTest {

    /**
     * Ensure that the [CommonOptions.terminal] can be accessed before the options has been
     * initialized.
     */
    @Test
    fun `Test error handling when invalid command line`() {
        val args = listOf(ARG_NO_COLOR, "@invalid.file")

        val stdout = StringWriter()
        val stderr = StringWriter()
        val command = TestCommand(stdout = PrintWriter(stdout), stderr = PrintWriter(stderr))

        try {
            command.processThrowCliException(args.toTypedArray())
        } catch (e: MetalavaCliException) {
            Assert.assertEquals(
                """
                Usage: test [options] [flags]...

                Error: invalid.file not found
            """
                    .trimIndent(),
                e.message
            )
        }

        Assert.assertEquals("", stderr.toString())
        Assert.assertEquals("", stdout.toString())

        // Make sure that the unsafeTerminal property has not been initialized as otherwise this is
        // not testing what how the error handling works in that case.
        val thrown =
            Assert.assertThrows(IllegalStateException::class.java) { command.common.unsafeTerminal }
        Assert.assertEquals(
            "Cannot read from option delegate before parsing command line",
            thrown.message
        )
    }

    /**
     * A special [MetalavaCommand] which enables @argfiles so that it can supply an invalid argument
     * which will cause Clikt to fail in such a way as to generate some help before initializing the
     */
    private class TestCommand(stdout: PrintWriter, stderr: PrintWriter) :
        MetalavaCommand(stdout, stderr, { NoOpCommand() }) {
        init {
            context { expandArgumentFiles = true }
        }
    }

    private class NoOpCommand : CliktCommand() {
        override fun run() {
            throw IllegalStateException("should never be called")
        }
    }
}
