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

import com.android.tools.metalava.cli.common.HARD_NEWLINE
import com.android.tools.metalava.cli.common.MetalavaOptionGroup
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.map
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.PropertyMap
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option

const val ARG_FORMAT = "--format"
const val ARG_USE_SAME_FORMAT_AS = "--use-same-format-as"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val SIGNATURE_FORMAT_OUTPUT_GROUP = "Signature Format Output"

class SignatureFormatOptions(
    /** If true then the `migrating` property is allowed, otherwise it is not allowed at all. */
    private val migratingAllowed: Boolean = false,
    /**
     * The default [FileFormat], or `null` if the caller supplies a fallback format when neither
     * [ARG_FORMAT] nor [ARG_USE_SAME_FORMAT_AS] is specified.
     */
    private val defaultFileFormat: FileFormat? = FileFormat.V2,
) :
    MetalavaOptionGroup(
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
                        PLACEHOLDER

                        See `metalava help signature-file-formats` for more information on the
                        properties.
                    """
                        .trimIndent()
                        .replace(
                            "PLACEHOLDER",
                            FileFormat.defaultableProperties().joinToString("") {
                                "$HARD_NEWLINE* `$it`"
                            }
                        ),
            )
            .convert { defaults -> FileFormat.parseDefaults(defaults) }

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
                helpTags =
                    if (defaultFileFormat != null) {
                        mapOf(HelpFormatter.Tags.DEFAULT to defaultFileFormat.specifier())
                    } else {
                        emptyMap()
                    },
            )
            .convert { specifier ->
                FileFormat.parseSpecifier(
                    specifier = specifier,
                    migratingAllowed = migratingAllowed,
                )
            }

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

    /**
     * Returns a [ComputedSignatureFormatOptions] instance based on the current state of the
     * options.
     */
    fun compute(): ComputedSignatureFormatOptions {
        return ComputedSignatureFormatOptions(
            formatDefaults,
            formatSpecifier ?: defaultFileFormat,
            useSameFormatAs,
            formatOverrides,
        )
    }
}

/**
 * Options related to signature file format and additional values computed based on those options.
 */
class ComputedSignatureFormatOptions(
    private val formatDefaults: PropertyMap?,
    private val formatSpecifier: FileFormat?,
    private val useSameFormatAs: FileFormat?,
    private val formatOverrides: String?,
) {
    /**
     * The [FileFormat] produced by merging all the format related options into one cohesive set of
     * format related properties, defaulting to [FileFormat.V2] if no format was specified.
     */
    val fileFormat: FileFormat by lazy(LazyThreadSafetyMode.NONE) { fileFormat { FileFormat.V2 } }

    /**
     * Compute the [FileFormat] by merging all the format related options, falling back to
     * [fallbackFormatProvider] if neither [ARG_USE_SAME_FORMAT_AS] (on a non-empty file) nor
     * [ARG_FORMAT] was specified.
     */
    fun fileFormat(fallbackFormatProvider: () -> FileFormat): FileFormat {
        // Check useSameFormatAs first, then formatSpecifier, and finally fall back to the
        // caller-supplied default format if neither was specified.
        val format = useSameFormatAs ?: formatSpecifier ?: fallbackFormatProvider()

        // Apply any overrides.
        val withOverrides = applyOverridesTo(format)

        // Apply any additional defaults.
        return applyDefaultsTo(withOverrides)
    }

    /** Apply optional defaults specified in [formatDefaults] to [base]. */
    fun applyDefaultsTo(base: FileFormat) = base.copy(formatDefaults = formatDefaults)

    /** Apply optional overrides specified in [formatOverrides] to [base]. */
    private fun applyOverridesTo(base: FileFormat) =
        formatOverrides?.let { overrides -> FileFormat.parseOverrides(base, overrides) } ?: base
}
