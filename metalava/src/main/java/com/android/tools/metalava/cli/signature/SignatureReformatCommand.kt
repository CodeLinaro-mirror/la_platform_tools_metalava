/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.text.CustomizableProperty
import com.android.tools.metalava.model.text.EmitFileHeader
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.PrintWriter
import java.io.StringWriter

class SignatureReformatCommand :
    MetalavaSubCommand(
        help =
            """
                Reformats signature files.

                Reformats each signature file to use the specified format.

                The purpose of this is, by working in conjunction with the $ARG_USE_SAME_FORMAT_AS
                option, to simplify the process for updating signature files from one version to the
                next. It assumes a number of things:

                1. That API signature files are checked into some version control system and need to
                be updated to reflect changes to the API. If they are not then this is not needed.

                2. The build uses the $ARG_USE_SAME_FORMAT_AS to pass the checked in API signature
                file so that its format will be used as the output for the file that the build
                generates to replace it.

                If those assumptions are met then updating the format version of the API file (and
                its corresponding removed API file if needed) simply involves:

                1. Running this command on the API file specifying the required format. That will
                reformat it according to the new format.

                2. If this changes the format to one that will include new information that was
                previously not recorded in the signature file then the signature file must be
                regenerated from the sources in order to include the new information.
            """
                .trimIndent(),
        printHelpOnEmptyArgs = false,
    ) {

    private val formatOptions by SignatureFormatOptions(migratingAllowed = true)

    private val preserveStructure by
        option(
                "--preserve-structure",
                help =
                    """
                        Preserve the structure of the file while changing the format.

                        Changes the format of the signature file while preserving the properties
                        from the previous version of the signature file preserving the signature
                        file structure.

                        `--format-defaults` should have the same value as that used when the
                        signature files was last updated to ensure that the structure is preserved.
                    """,
            )
            .flag()

    private val files by
        argument(
                name = "<files>",
                help =
                    """
                        Signature files to reformat.
                    """,
            )
            .existingFile()
            .multiple(required = true)

    /**
     * Compute the output [FileFormat] to use.
     *
     * Returns [targetFormat] unless [preserveStructure] is `true` in which case this will use
     * [FileStructurePreserver] to create a [FileFormat] from [targetFormat] with settings from
     * [currentFormat] needed to preserve the structure.
     *
     * @param currentFormat the current [FileFormat] for the file.
     * @param targetFormat the target [FileFormat] to which the file is to be reformatted.
     * @param codebase the [Codebase] loaded from the signature file. Used to determine whether a
     *   property setting affects the structure of the file.
     */
    private fun computeOutputFormat(
        currentFormat: FileFormat,
        targetFormat: FileFormat,
        codebase: Codebase,
    ) =
        if (preserveStructure) {
            // Make sure to apply any defaults provided to the current format to ensure it is the
            // same format as was used to create the current signature file.
            val currentFormatWithDefaults = formatOptions.applyDefaultsTo(currentFormat)

            // Compute structure preserving format.
            FileStructurePreserver(currentFormatWithDefaults, targetFormat, codebase)
                .computeFormat()
        } else {
            targetFormat
        }

    override fun run() {
        // Get the target format for the signature files.
        val targetFormat = formatOptions.fileFormat

        for (file in files) {
            // Read the current format from the file header, if none could be found then the file is
            // empty and intentionally has no file format header so leave it unchanged.
            val currentFormat =
                file.reader().use { reader -> FileFormat.parseHeader(file.toPath(), reader) }
            if (currentFormat == null) continue

            val codebase = readSignatureFiles(SignatureFile.fromFiles(file), stderr)

            // Compute the output format to use when writing out this file.
            val outputFormat = computeOutputFormat(currentFormat, targetFormat, codebase)

            file.printWriter().use { writer -> writeSignatureFile(codebase, outputFormat, writer) }
        }
    }
}

/**
 * Encapsulates logic to compute the [FileFormat] that will preserve the file structure of
 * [codebase] formatted with [currentFormat] while setting as many of the properties in
 * [targetFormat] as possible.
 */
private class FileStructurePreserver(
    private val currentFormat: FileFormat,
    private val targetFormat: FileFormat,
    private val codebase: Codebase,
) {
    /**
     * The current contents of the signature file against which the effect of property settings will
     * be compared. Initialized lazily as it can be expensive to compute and may not be needed.
     */
    private val currentContents by
        lazy(LazyThreadSafetyMode.NONE) { codebase.toSignatureNoHeader(currentFormat) }

    /**
     * Format this [Codebase] as a signature file using [format] but without the header, returning
     * the [String] result.
     */
    private fun Codebase.toSignatureNoHeader(format: FileFormat): String {
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { printWriter ->
            writeSignatureFile(this, format, printWriter, emitFileHeader = EmitFileHeader.NEVER)
        }
        return stringWriter.toString()
    }

    /**
     * Check to see whether this should preserve the property from [currentFormat] or see if it can
     * use the property from [targetFormat] instead.
     */
    private fun <T> copyPropertyPreservingStructure(
        builder: FileFormat.Builder,
        property: CustomizableProperty<T>,
    ) {
        // Get the current value of the property, including any defaults. If it is not set
        // then it will have no impact on the resulting format so return.
        val currentValue = currentFormat[property] ?: return

        // Get the target value, including any defaults.
        val targetValue = targetFormat[property]
        if (targetValue == null) {
            // The target value is null so use the value from the current file.
            builder[property] = currentValue
        } else if (targetValue != currentValue) {
            // The target value is different from the current value so see whether it needs
            // to be changed.
            if (property.defaultable) {
                // Defaultable properties should only be specified if needed. Check to see
                // if its value has any impact on the generated signature file.

                // Build a [FileFormat] that is the same as the [currentFormat] but with
                // its [property] set to the [targetFormat]'s value.
                val currentFormatWithTargetValue =
                    currentFormat.buildCopy { this[property] = targetValue }

                // Generate the signature contents with that format.
                val contentsWithProperty =
                    codebase.toSignatureNoHeader(currentFormatWithTargetValue)

                // If the contents are different with the target value then keep the current
                // value.
                if (currentContents != contentsWithProperty) {
                    builder[property] = currentValue
                }
            } else {
                // Always keep the current value for non-defaultable properties as they are
                // significant even if they do not affect the current signature file.
                // e.g. if the current sets `kotlin-style-nulls=no` and the target sets
                // `kotlin-style-nulls=yes` this cannot just use the latter even if it has
                // no impact on the current structure that is a fundamental change in the
                // information that will be recorded.
                builder[property] = currentValue
            }
        }
    }

    /**
     * Compute a [FileFormat] that will preserve the structure of the original [currentFormat] while
     * incorporating changes from [targetFormat].
     *
     * This assumes that every property difference between [currentFormat] and [targetFormat] could
     * result in structural changes.
     *
     * The [codebase] is used to determine whether a specific property setting affects the file
     * structure by generated signature file contents with and without that setting and comparing
     * the result.
     */
    fun computeFormat() =
        // Create a new FileFormat based on the [targetFormat] with properties copied from
        // [currentFormat] where necessary.
        targetFormat.buildCopy {
            // Iterate over all the properties checking to see if the [targetFormat] value needs to
            // be replaced with the [currentFormat] value.
            for (property in CustomizableProperty.entries) {
                copyPropertyPreservingStructure(this, property)
            }
        }
}
