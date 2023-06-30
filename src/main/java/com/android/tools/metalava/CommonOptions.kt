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
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.deprecated
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch

val colorDefaultValue: Boolean =
    System.getenv("TERM")?.startsWith("xterm") ?: (System.getenv("COLORTERM") != null)

const val ARG_QUIET = "--quiet"
const val ARG_VERBOSE = "--verbose"
const val ARG_COLOR = "--color"
const val ARG_NO_COLOR = "--no-color"
const val ARG_NO_BANNER = "--no-banner"

// Unicode Next Line (NEL) character which forces Clikt to insert a new line instead of just
// collapsing the `\n` into adjacent spaces. Acts like an HTML <br/>.
private const val NEL = "\u0085"

enum class Verbosity(val quiet: Boolean = false, val verbose: Boolean = false) {
    /** Whether to report warnings and other diagnostics along the way. */
    QUIET(quiet = true),

    /** Standard output level. */
    NORMAL,

    /** Whether to report extra diagnostics along the way. */
    VERBOSE(verbose = true)
}

/** Options that are common to all metalava sub-commands. */
class CommonOptions : OptionGroup() {

    /** Whether output should use terminal capabilities */
    val terminal by
        option(
                help =
                    """
                Determine whether to use terminal capabilities to colorize and otherwise style the
                output.
            """
                        .trimIndent(),
            )
            .switch(
                ARG_COLOR to stylingTerminal,
                ARG_NO_COLOR to plainTerminal,
            )
            .default(
                if (colorDefaultValue) stylingTerminal else plainTerminal,
                defaultForHelp = "true if \$TERM starts with `xterm` or \$COLORTERM is set",
            )

    val noBanner by
        option(ARG_NO_BANNER, help = "A banner is never output so this has no effect")
            .flag(default = true)
            .deprecated(
                "WARNING: option `$ARG_NO_BANNER` is deprecated; it has no effect please remove",
                tagValue = "please remove"
            )

    val verbosity: Verbosity by
        option(
                help =
                    """
            Set the verbosity of the output.$NEL
                $ARG_QUIET - Only include vital output.$NEL
                $ARG_VERBOSE - Include extra diagnostic output.$NEL
            """
                        .trimIndent()
            )
            .switch(
                ARG_QUIET to Verbosity.QUIET,
                ARG_VERBOSE to Verbosity.VERBOSE,
            )
            .default(Verbosity.NORMAL, defaultForHelp = "Neither $ARG_QUIET or $ARG_VERBOSE")
}

/**
 * A default instance of [CommonOptions]
 *
 * This is needed because it is an error to attempt to access a CLI property before it has been
 * correctly parsed.
 *
 * It is safe to reuse this as once they have been initialized the properties are read only.
 */
val defaultCommonOptions by lazy {
    // A fake command that is used to correctly initialize the CommonOptions.
    class FakeCommand : CliktCommand() {
        val commonOptions by CommonOptions()

        override fun run() {}
    }
    val command = FakeCommand()

    // Initialize the options properly and return them.
    command.parse(emptyArray())
    command.commonOptions
}
