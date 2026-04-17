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

package com.android.tools.metalava.cli.signature

import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.map
import com.android.tools.metalava.model.text.FileFormat
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option

const val ARG_FORMAT = "--format"
const val ARG_USE_SAME_FORMAT_AS = "--use-same-format-as"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val SIGNATURE_FORMAT_OUTPUT_GROUP = "Signature Format Output"

class SignatureFormatOptions(
    /** If true then the `migrating` property is allowed, otherwise it is not allowed at all. */
    private val migratingAllowed: Boolean = false,
) :
    OptionGroup(
        name = SIGNATURE_FORMAT_OUTPUT_GROUP,
        help =
            """
                Options controlling the format of the generated signature files.

                See `metalava help signature-file-formats` for more information.
            """
                .trimIndent()
    ) {

    private val formatDefaults by
        option(
                "--format-defaults",
                metavar = "<defaults>",
                help =
                    """
                        Specifies defaults for format properties.

                        A comma separated list of `<property>=<value>` assignments where
                        `<property>` is one of the following:
                        '${FileFormat.defaultableProperties().joinToString(separator = "', '")}'.

                        See `metalava help signature-file-formats` for more information on the
                        properties.
                    """
                        .trimIndent(),
            )
            .convert { defaults -> FileFormat.parseDefaults(defaults) }

    /** Apply optional defaults specified in [formatDefaults] to [base]. */
    fun applyDefaultsTo(base: FileFormat) = base.copy(formatDefaults = formatDefaults)

    /** The output format version being used */
    private val formatSpecifier by
        option(
                ARG_FORMAT,
                metavar = "<specifier>",
                help =
                    """
                        Specifies the output signature file format.

                        <specifier> - which has the following syntax:
                        ```
                        <version>[:<property>=<value>[,<property>=<value>]*]
                        ```

                        See `metalava help signature-file-formats` for more help including a list
                        of the available `<version>`s and `<property>=<value>`s.
                    """
                        .trimIndent(),
            )
            .convert { specifier ->
                FileFormat.parseSpecifier(
                    specifier = specifier,
                    migratingAllowed = migratingAllowed,
                )
            }
            .default(FileFormat.V2, defaultForHelp = "2.0")

    private val useSameFormatAs by
        option(
                ARG_USE_SAME_FORMAT_AS,
                help =
                    """
                        Specifies that the output format should be the same as the format used in
                        the specified file. It is an error if the file does not exist. If the file
                        is empty then this will behave as if it was not specified. If the file is
                        not a valid signature file then it will fail. Otherwise, the format read
                        from the file will be used.

                        If this is specified (and the file is not empty) then this will be used in
                        preference to most of the other options in this group. Those options will be
                        validated but otherwise ignored.

                        The intention is that the other options will be used to specify the default
                        for new empty API files (e.g. created using `touch`) while this option is
                        used to specify the format for generating updates to the existing non-empty
                        files.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .map { file ->
                file?.reader(Charsets.UTF_8)?.use { FileFormat.parseHeader(file.toPath(), it) }
            }

    private val formatOverrides by
        option(
            "--format-overrides",
            metavar = "<overrides>",
            help =
                """
                    Specifies overrides for format properties. Intended for use with
                    $ARG_USE_SAME_FORMAT_AS to change individual properties within a signature file.

                    A comma separated list of `<property>=<value>` assignments where `<property>`
                    can be any property supported by signature file formats.

                    See `metalava help signature-file-formats` for more information on the
                    properties.
                """,
        )

    /** Apply optional overrides specified in [formatOverrides] to [base]. */
    private fun applyOverridesTo(base: FileFormat) =
        formatOverrides?.let { overrides -> FileFormat.parseOverrides(base, overrides) } ?: base

    /**
     * The [FileFormat] produced by merging all the format related options into one cohesive set of
     * format related properties. It combines the defaults
     */
    val fileFormat: FileFormat by
        lazy(LazyThreadSafetyMode.NONE) {
            // Check the useSameFormatAs first and if it is not specified (or is an empty file) then
            // fall back to the other options.
            val format = useSameFormatAs ?: formatSpecifier

            // Apply any overrides.
            val withOverrides = applyOverridesTo(format)

            // Apply any additional defaults.
            applyDefaultsTo(withOverrides)
        }
}
