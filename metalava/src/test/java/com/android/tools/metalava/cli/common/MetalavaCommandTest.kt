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

import com.android.tools.metalava.Driver
import com.android.tools.metalava.testing.getNoopTracer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class MetalavaCommandTest :
    BaseCommandTest<MetalavaCommand>({ executionEnvironment ->
        MetalavaCommand(
            executionEnvironment = executionEnvironment,
            tracer = getNoopTracer(),
        )
    }) {

    /**
     * Ensure that the [CommonOptions.terminal] can be accessed before the options has been
     * initialized.
     *
     * This uses the fact that argument files are expanded before options are initialized. So, a
     * missing argument file will report an error before any options have been processed.
     */
    @Test
    fun `Test error handling when invalid command line`() {
        val args = listOf(ARG_NO_COLOR, "@invalid.file")

        val (executionEnvironment, stdout, stderr) = ExecutionEnvironment.forTest()
        val command =
            MetalavaCommand(
                executionEnvironment = executionEnvironment,
                tracer = getNoopTracer(),
            )
        try {
            command.processThrowCliException(args.toTypedArray())
        } catch (e: MetalavaCliException) {
            assertEquals(
                """
            Usage: metalava [options] [flags]...

            Error: invalid.file not found
        """
                    .trimIndent(),
                e.message
            )
        }

        assertEquals("", stderr.toString())
        assertEquals("", stdout.toString())

        // Make sure that the unsafeTerminal property has not been initialized as otherwise this
        // is
        // not testing what how the error handling works in that case.
        val thrown =
            assertThrows(IllegalStateException::class.java) { command.common.unsafeTerminal }
        assertEquals("Cannot read from option delegate before parsing command line", thrown.message)
    }

    /**
     * Ensure that the [CommonOptions.terminal] can be accessed before the options has been
     * initialized.
     */
    @Test
    fun `Test error handling when invalid option in an argument file`() {
        val file = temporaryFolder.newFile("invalid-argument")
        file.writeText("--invalid-argument")

        val args = listOf(ARG_NO_COLOR, "@${file.path}")

        val (executionEnvironment, stdout, stderr) = ExecutionEnvironment.forTest()

        val subCommand =
            object : CliktCommand(name = "sub") {
                override fun run() {
                    TODO("Not yet implemented")
                }
            }
        val command =
            MetalavaCommand(
                executionEnvironment = executionEnvironment,
                defaultCommandName = subCommand.commandName,
                tracer = getNoopTracer(),
            )
        command.subcommands(subCommand)

        try {
            command.processThrowCliException(args.toTypedArray())
        } catch (e: MetalavaCliException) {
            assertEquals(
                """
            Usage: metalava sub

            Error: Got unexpected extra argument (--invalid-argument)
        """
                    .trimIndent(),
                e.message
            )
        }

        assertEquals("", stderr.toString())
        assertEquals("", stdout.toString())
    }

    @Test
    fun `Test print stack trace`() {
        val args = listOf(ARG_NO_COLOR, "--print-stack-trace", "fail")

        val (executionEnvironment, _, stderr) = ExecutionEnvironment.forTest()

        val command =
            MetalavaCommand(
                executionEnvironment = executionEnvironment,
                tracer = getNoopTracer(),
            )
        command.subcommands(FailCommand())
        command.process(args.toTypedArray())

        val pattern =
            """\Qcom.android.tools.metalava.cli.common.MetalavaCliException: fail
            |	at com.android.tools.metalava.cli.common.MetalavaCliExceptionKt.cliError\E\([^)]+\)\Q
            |	at com.android.tools.metalava.cli.common.MetalavaCommandTest${"$"}FailCommand.run\E\([^)]+\)
            |	at .*
            |	at .*
            |	at .*
        """
                .trimMargin()
        val output = stderr.toString()
        if (!pattern.toRegex().matchesAt(output, 0)) {
            val separator = "=".repeat(80)
            fail(
                """
Expected output to match this pattern:
$separator
$pattern
$separator

but the following output does not match:
$separator
$output
$separator
                """
            )
        }
    }

    private class FailCommand : CliktCommand() {
        override fun run() {
            cliError("fail")
        }
    }

    @Test
    fun `Test version`() {
        commandTest {
            args += listOf(ARG_NO_COLOR, "--version")

            expectedStderr = ""
            expectedStdout = "metalava version: 1.0.0-alpha16"
        }
    }

    @Test
    fun `Test help with no sub-command`() {
        val (executionEnvironment, stdout, stderr) = ExecutionEnvironment.forTest()
        val exitCode = Driver.run(executionEnvironment, arrayOf(ARG_NO_COLOR, "--help"))
        assertEquals(0, exitCode)
        assertEquals("", stderr.toString())
        assertEquals(
            """
Usage: metalava [options] [flags]... <sub-command>? ...

  Extracts metadata from source code to generate artifacts such as the signature files, the SDK stub files, external
  annotations etc.

Options:
  --version                                  Show the version and exit
  --print-stack-trace                        Print the stack trace of any exceptions that will cause metalava to exit.
                                             (default: no stack trace)
  --quiet, --verbose                         Set the verbosity of the output.
                                             --quiet - Only include vital output.
                                             --verbose - Include extra diagnostic output.
                                             (default: Neither --quiet or --verbose)
  --trace-file TEXT                          Set the location where the trace should be written to.
  --color, --no-color                        Determine whether to use terminal capabilities to colorize and otherwise
                                             style the output. (default: true if ${'$'}TERM starts with `xterm` or ${'$'}COLORTERM
                                             is set)
  --no-banner                                A banner is never output so this has no effect (deprecated: please remove)
  -h, --help                                 Show this message and exit

Arguments:
  flags                                      See below.

Sub-commands:
  main                                       The default sub-command that is run if no sub-command is specified.
  android-jars-to-signatures                 Rewrite the signature files in the `prebuilts/sdk` directory in the Android
                                             source tree.
  flag-report                                Generates a flag report
  help                                       Provides help for general metalava concepts.
  jar-to-jdiff                               Convert a jar file into a file in the JDiff XML format.
  list-flags                                 List flags referenced in signature files.
  merge-signatures                           Merge multiple signature files together into a single file.
  signature-cat                              Cats signature files.
  signature-migrate                          Migrates signature files to a new format.
  signature-reformat                         Reformats signature files.
  signature-to-dex                           Convert API signature files into a file containing a list of DEX
                                             signatures.
  signature-to-jdiff                         Convert an API signature file into a file in the JDiff XML format.
  version                                    Show the version
  multi-surface                              Command that sets up state to run operations for multiple API surfaces.
            """
                .trimIndent(),
            stdout.toString().trim(),
        )
    }
}
