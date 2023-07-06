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

import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.output.Localization

const val MAX_LINE_WIDTH = 120

/** Metalava specific implementation of [CliktHelpFormatter]. */
internal open class MetalavaHelpFormatter(
    terminalSupplier: () -> Terminal,
    localization: Localization,
) :
    CliktHelpFormatter(
        localization = localization,
        showDefaultValues = true,
        showRequiredTag = true,
        maxWidth = MAX_LINE_WIDTH,
        // The following value was chosen to produce the same indentation for option descriptions
        // as is produced by Options.usage.
        maxColWidth = 41,
    ) {

    /**
     * Property for accessing the [Terminal] instance that should be used to style (or not) help
     * text.
     */
    protected val terminal: Terminal by lazy { terminalSupplier() }

    override fun formatHelp(
        prolog: String,
        epilog: String,
        parameters: List<HelpFormatter.ParameterHelp>,
        programName: String
    ): String {
        // Color the program name, there is no override to do that.
        val formattedProgramName = terminal.colorize(programName, TerminalColor.BLUE)

        // Use the default help format.
        return super.formatHelp(prolog, epilog, parameters, formattedProgramName)
    }

    override fun renderArgumentName(name: String): String {
        return terminal.bold(super.renderArgumentName(name))
    }

    override fun renderOptionName(name: String): String {
        return terminal.bold(super.renderOptionName(name))
    }

    override fun renderSectionTitle(title: String): String {
        return terminal.colorize(super.renderSectionTitle(title), TerminalColor.YELLOW)
    }

    override fun renderSubcommandName(name: String): String {
        return terminal.bold(super.renderSubcommandName(name))
    }
}
