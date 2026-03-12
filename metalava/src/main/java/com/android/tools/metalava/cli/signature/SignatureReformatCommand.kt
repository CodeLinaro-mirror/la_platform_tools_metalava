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
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate

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
            file.printWriter().use { writer -> writeSignatureFile(codebase, targetFormat, writer) }
        }
    }
}
