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

package com.android.tools.metalava.cli.signature.migration

import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.cli.signature.ARG_USE_SAME_FORMAT_AS
import com.android.tools.metalava.cli.signature.SignatureFormatOptions
import com.android.tools.metalava.cli.signature.readSignatureFiles
import com.android.tools.metalava.cli.signature.writeSignatureFile
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

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
     * Compute the output [com.android.tools.metalava.model.text.FileFormat] to use.
     *
     * Returns [targetFormat] unless [preserveStructure] is `true` in which case this will use
     * [FileStructurePreserver] to create a [com.android.tools.metalava.model.text.FileFormat] from
     * [targetFormat] with settings from [currentFormat] needed to preserve the structure.
     *
     * @param currentFormat the current [com.android.tools.metalava.model.text.FileFormat] for the
     *   file.
     * @param targetFormat the target [com.android.tools.metalava.model.text.FileFormat] to which
     *   the file is to be reformatted.
     * @param codebase the [com.android.tools.metalava.model.Codebase] loaded from the signature
     *   file. Used to determine whether a property setting affects the structure of the file.
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
