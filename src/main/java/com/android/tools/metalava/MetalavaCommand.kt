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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.versionOption
import java.io.PrintWriter

const val ARG_VERSION = "--version"

val BANNER: String =
    """
                _        _
 _ __ ___   ___| |_ __ _| | __ ___   ____ _
| '_ ` _ \ / _ \ __/ _` | |/ _` \ \ / / _` |
| | | | | |  __/ || (_| | | (_| |\ V / (_| |
|_| |_| |_|\___|\__\__,_|_|\__,_| \_/ \__,_|
"""
        .trimIndent()

/** Main metalava command. */
class MetalavaCommand(
    private val stdout: PrintWriter,
    private val stderr: PrintWriter,
) :
    CliktCommand(
        // Gather all the options and arguments into a list so that they can be passed to Options().
        treatUnknownOptionsAsArgs = true,
        // Call run on this command even if no sub-command is provided.
        invokeWithoutSubcommand = true,
    ) {
    init {
        context {
            console = MetalavaConsole(stdout, stderr)

            // Disable help so that Options can print it instead.
            helpOptionNames = emptySet()
        }

        // Print the version number if requested.
        versionOption(
            Version.VERSION,
            names = setOf(ARG_VERSION),
            message = { "$commandName version: $it" },
        )

        subcommands(
            VersionCommand(),
        )
    }

    /** Group of common options. */
    val common by CommonOptions()

    /** Property into which all the arguments (and unknown options) are gathered. */
    private val flags by argument().multiple()

    /** Process the command. */
    fun process(args: Array<String>) {
        try {
            parse(args)
        } catch (e: PrintHelpMessage) {
            throw DriverException(
                stdout = e.command.getFormattedHelp(),
                exitCode = if (e.error) 1 else 0
            )
        } catch (e: PrintMessage) {
            throw DriverException(stdout = e.message ?: "", exitCode = if (e.error) 1 else 0)
        }
    }

    /**
     * Perform this command's actions.
     *
     * This is called after the command line parameters are parsed. If one of the sub-commands is
     * invoked then this is called before the sub-commands parameters are parsed.
     */
    override fun run() {
        // Print the banner if needed.
        if (!common.verbosity.quiet && !common.noBanner) {
            stdout.println(common.terminal.colorize(BANNER.trimIndent(), TerminalColor.BLUE))
            stdout.flush()
        }

        // Make the CommonOptions available to all sub-commands.
        currentContext.obj = common

        val subcommand = currentContext.invokedSubcommand
        if (subcommand == null) {
            val remainingArgs = flags.toTypedArray()
            options = Options(remainingArgs, stdout, stderr, common)

            maybeActivateSandbox()

            processFlags()
        }
    }
}
