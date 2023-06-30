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
import com.github.ajalt.clikt.core.NoSuchOption
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.parameters.arguments.Argument
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import java.io.PrintWriter

const val ARG_VERSION = "--version"

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
        help =
            """
            Extracts metadata from source code to generate artifacts such as the signature files,
            the SDK stub files, external annotations etc.
        """
                .trimIndent()
    ) {
    init {
        context {
            console = MetalavaConsole(stdout, stderr)

            localization = MetalavaLocalization()

            /**
             * Disable built in help.
             *
             * See [showHelp] for an explanation.
             */
            helpOptionNames = emptySet()

            // Override the help formatter to add in documentation for the legacy flags.
            helpFormatter = LegacyHelpFormatter({ common.terminal }, localization)
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

    /**
     * A custom, non-eager help option that allows [CommonOptions] like [CommonOptions.terminal] to
     * be used when generating the help output.
     *
     * The built-in help option is eager and throws a [PrintHelpMessage] exception which aborts the
     * processing of other options preventing their use when generating the help output.
     *
     * Currently, this does not support `-?` for help as Clikt considers that to be an invalid flag.
     * However, `-?` is still supported for backwards compatibility using a workaround in
     * [showHelpAndExitIfRequested].
     */
    private val showHelp by option("-h", "--help", help = "Show this message and exit").flag()

    /** Property into which all the arguments (and unknown options) are gathered. */
    private val flags by
        argument(
                name = "flags",
                help = "See below.",
            )
            .multiple()

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
        } catch (e: NoSuchOption) {
            val message = createUsageErrorMessage(e)
            throw DriverException(stderr = message, exitCode = e.statusCode)
        }
    }

    /** Get a list of all the parameter related help information. */
    private fun allHelpParams(command: CliktCommand): List<HelpFormatter.ParameterHelp> {
        return command.registeredOptions().mapNotNull { it.parameterHelp(currentContext) } +
            command.registeredArguments().mapNotNull { it.parameterHelp(currentContext) } +
            command.registeredParameterGroups().mapNotNull { it.parameterHelp(currentContext) } +
            command.registeredSubcommands().mapNotNull { it.parameterHelp() }
    }

    /**
     * Create an error message that incorporates the specific usage error as well as providing
     * documentation for all the available options.
     */
    private fun createUsageErrorMessage(e: UsageError): String {
        return buildString {
            val errorContext = e.context ?: currentContext
            e.message?.let { append(errorContext.localization.usageError(it)).append("\n\n") }
            e.context?.let {
                val programName = it.commandNameWithParents().joinToString(" ")
                val helpParams = allHelpParams(it.command)
                val commandHelp = it.helpFormatter.formatHelp("", "", helpParams, programName)
                append(commandHelp)
            }
        }
    }

    /**
     * Perform this command's actions.
     *
     * This is called after the command line parameters are parsed. If one of the sub-commands is
     * invoked then this is called before the sub-commands parameters are parsed.
     */
    override fun run() {
        // Make the CommonOptions available to all sub-commands.
        currentContext.obj = common

        val subcommand = currentContext.invokedSubcommand
        if (subcommand == null) {
            showHelpAndExitIfRequested()

            val remainingArgs = flags.toTypedArray()
            options = Options(remainingArgs, stdout, stderr, common)

            maybeActivateSandbox()

            processFlags()
        }
    }

    /**
     * Show help and exit if requested.
     *
     * Help is requested if [showHelp] is true or [flags] contains `-?` or `-?`.
     */
    private fun showHelpAndExitIfRequested() {
        val remainingArgs = flags.toTypedArray()
        // Output help and exit if requested.
        if (showHelp || remainingArgs.contains("-?")) {
            throw PrintHelpMessage(this)
        }
    }
}

/**
 * Add a method to get a [HelpFormatter.ParameterHelp] instance from a [CliktCommand].
 *
 * Other classes that contribute to the help provide `parameterHelp` methods that return an instance
 * of the appropriate sub-class of [HelpFormatter.ParameterHelp], e.g. [Argument.parameterHelp].
 */
fun CliktCommand.parameterHelp(): HelpFormatter.ParameterHelp? {
    return if (this is MetalavaSubCommand) {
        // Can only work
        parameterHelp()
    } else {
        null
    }
}
