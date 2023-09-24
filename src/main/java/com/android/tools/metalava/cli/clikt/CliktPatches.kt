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

package com.android.tools.metalava.cli.clikt

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.output.HelpFormatter.ParameterHelp
import com.github.ajalt.clikt.parameters.arguments.Argument

/**
 * Get a list of all the parameter related help information for another command.
 *
 * @param context the [Context] to use to provide default metavar names when needed.
 */
fun CliktCommand.allHelpParams(context: Context): List<ParameterHelp> {
    return registeredOptions().mapNotNull { it.parameterHelp(context) } +
        registeredArguments().mapNotNull { it.parameterHelp(context) } +
        registeredParameterGroups().mapNotNull { it.parameterHelp(context) } +
        registeredSubcommands().map { it.parameterHelp() }
}

/**
 * Add a method to get a [ParameterHelp] instance from a [CliktCommand].
 *
 * Other classes that contribute to the help provide `parameterHelp` methods that return an instance
 * of the appropriate sub-class of [ParameterHelp], e.g. [Argument.parameterHelp].
 */
fun CliktCommand.parameterHelp(): ParameterHelp {
    return ParameterHelp.Subcommand(commandName, shortHelp(), helpTags)
}

/** The help displayed in the commands list when this command is used as a subcommand. */
fun CliktCommand.shortHelp(): String =
    Regex("""\s*(?:```)?\s*(.+)""").find(commandHelp)?.groups?.get(1)?.value ?: ""
