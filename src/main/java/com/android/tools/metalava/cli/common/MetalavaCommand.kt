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

import com.android.tools.metalava.cli.clikt.allHelpParams
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoSuchOption
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.HelpFormatter.ParameterHelp
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import java.io.PrintWriter

const val ARG_VERSION = "--version"

/**
 * Main metalava command.
 *
 * If no subcommand is specified in the arguments that are passed to [parse] then this will invoke
 * the [defaultCommand] passing in all the arguments not already consumed by Clikt options.
 */
internal open class MetalavaCommand(
    private val stdout: PrintWriter,
    private val stderr: PrintWriter,

    /**
     * The factory for the default command to run when no subcommand is provided on the command
     * line.
     *
     * The command itself does not appear in the help but any options that it provides do. The first
     * part is achieved by not adding the command to the list of subcommands, and by ensuring that
     * when an exception is processed that requests help for the default command that it reports the
     * help for this command instead.
     *
     * The second part is achieved by appending the list of [ParameterHelp] from the default
     * subcommand to the list from this, filtering out any which are not needed (see
     * [excludeArgumentsWithNoHelp]).
     */
    defaultCommandFactory: (CommonOptions) -> CliktCommand,

    /** Provider for additional non-Clikt usage information. */
    private val nonCliktUsageProvider: ((Terminal, Int) -> String)? = null,
) :
    CliktCommand(
        // Gather all the options and arguments into a list so that they can be handled by some
        // non-Clikt option processor which it is assumed that this command has iff this is passed a
        // non-null nonCliktUsageProvider.
        treatUnknownOptionsAsArgs = nonCliktUsageProvider != null,
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
            helpFormatter =
                LegacyHelpFormatter(
                    { common.terminal },
                    localization,
                    ::mergeDefaultParameterHelp,
                    nonCliktUsageProvider ?: { _, _ -> "" },
                )

            // Disable argument file expansion (i.e. @argfile) as it causes issues with some uses
            // that prefix annotation names with `@`, e.g. `--show-annotation @foo.Show`.
            expandArgumentFiles = false
        }

        // Print the version number if requested.
        versionOption(
            // Use a fake version here to avoid loading the `/version.properties` file unless
            // needed.
            "fake-version",
            names = setOf(ARG_VERSION),
            message = { "$commandName version: ${Version.VERSION}" },
        )
    }

    /** Group of common options. */
    val common by CommonOptions()

    /** The default command to run when no subcommand is provided on the command line. */
    private val defaultCommand: CliktCommand = defaultCommandFactory(common)

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

    /** Process the command, handling [MetalavaCliException]s. */
    fun process(args: Array<String>): Int {
        var exitCode = 0
        try {
            processThrowCliException(args)
        } catch (e: MetalavaCliException) {
            stdout.flush()
            stderr.flush()

            val prefix =
                if (e.exitCode != 0) {
                    "Aborting: "
                } else {
                    ""
                }

            if (e.stderr.isNotBlank()) {
                stderr.println("\n${prefix}${e.stderr}")
            }
            if (e.stdout.isNotBlank()) {
                stdout.println("\n${prefix}${e.stdout}")
            }
            exitCode = e.exitCode
        }

        return exitCode
    }

    /** Process the command, throwing [MetalavaCliException]s. */
    fun processThrowCliException(args: Array<String>) {
        try {
            parse(args)
        } catch (e: PrintHelpMessage) {
            // If the command in the message is the default command then use this command instead as
            // the default command should not appear in the help.
            val command = if (e.command == defaultCommand) this else e.command
            throw MetalavaCliException(
                stdout = command.getFormattedHelp(),
                exitCode = if (e.error) 1 else 0
            )
        } catch (e: PrintMessage) {
            throw MetalavaCliException(stdout = e.message ?: "", exitCode = if (e.error) 1 else 0)
        } catch (e: NoSuchOption) {
            correctContextInUsageError(e)
            val message = createUsageErrorMessage(e)
            throw MetalavaCliException(stderr = message, exitCode = e.statusCode)
        } catch (e: UsageError) {
            correctContextInUsageError(e)
            val message = e.helpMessage()
            throw MetalavaCliException(stderr = message, exitCode = e.statusCode)
        }
    }

    /**
     * Ensure that any [UsageError] thrown by the [defaultCommand] is output as if it came from this
     * command.
     *
     * This is needed because the [defaultCommand] is an implementation detail and so should not be
     * visible to callers of this command.
     */
    private fun correctContextInUsageError(e: UsageError) {
        // If no subcommand was specified (in which case the default command was used) then
        // update the exception to use the current context so that it will display the help for
        // this command, not the default command.
        if (e.context?.command == defaultCommand) {
            e.context = currentContext
        }
    }

    /**
     * Merge help for command line parameters provided by the [defaultCommand] into the list of
     * parameters provided by this command.
     *
     * This is needed because the [defaultCommand] is an implementation detail and so should not be
     * visible to callers of this command.
     */
    private fun mergeDefaultParameterHelp(parameters: List<ParameterHelp>): List<ParameterHelp> {
        return if (currentContext.command === this)
            parameters +
                defaultCommand.allHelpParams(currentContext).filter(::excludeArgumentsWithNoHelp)
        else {
            parameters
        }
    }

    /**
     * Exclude any arguments that do not provide [ParameterHelp.Argument.help] from the list of
     * arguments for which help will be displayed. That allows the [defaultCommand] to use an
     * argument property to collate any unknown options (just like [flags] does here) without that
     * appearing in the help, duplicating the help for [flags].
     */
    private fun excludeArgumentsWithNoHelp(p: ParameterHelp): Boolean {
        if (p is ParameterHelp.Argument) {
            return p.help != ""
        }

        return true
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
                val helpParams = it.command.allHelpParams(currentContext)
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

            // Get any remaining arguments/options that were not handled by Clikt.
            val remainingArgs = flags.toTypedArray()

            // No sub-command was provided so use the default subcommand.
            defaultCommand.parse(remainingArgs, currentContext)
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

/** The [PrintWriter] to use for error output from the command. */
val CliktCommand.stderr: PrintWriter
    get() {
        val metalavaConsole = currentContext.console as MetalavaConsole
        return metalavaConsole.stderr
    }

/** The [PrintWriter] to use for non-error output from the command. */
val CliktCommand.stdout: PrintWriter
    get() {
        val metalavaConsole = currentContext.console as MetalavaConsole
        return metalavaConsole.stdout
    }

val CliktCommand.commonOptions
    // Retrieve the CommonOptions that is made available by the containing MetalavaCommand.
    get() = currentContext.findObject<CommonOptions>()

val CliktCommand.terminal
    // Retrieve the terminal from the CommonOptions.
    get() = commonOptions?.terminal ?: plainTerminal
