/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.metalava.OptionsDelegate
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.newOrExistingFile
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.cli.common.stdin
import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.noOpAnnotationManager
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.text.SignatureWriter
import com.android.tools.metalava.model.text.createFilteringVisitorForSignatures
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.reporter.BasicReporter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.option
import java.io.PrintWriter

class SignatureCatCommand :
    MetalavaSubCommand(
        help =
            """
                Cats signature files.

                Reads signature files either provided on the command line, or in stdin into a
                combined API surface and then writes it out to either the output file provided on
                the command line or to stdout according to the format options. The resulting output
                will be different to the input if the input does not already conform to the selected
                format.
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
                        Signature files to read, if not specified then they stdin is read instead.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .multiple()

    private val outputFile by
        option(
                names = arrayOf("--output-file"),
                help =
                    """
                        File to write the signature output to. If not specified stdout will be used.
                    """
                        .trimIndent()
            )
            .newOrExistingFile()

    override fun run() {
        // Make sure that none of the code called by this command accesses the global `options`
        // property.
        OptionsDelegate.disallowAccess()

        val outputFormat = formatOptions.fileFormat

        val signatureFiles =
            if (files.isEmpty()) {
                // If no files are provided on the command line then read from stdin.
                listOf(SignatureFile.fromStream("<stdin>", stdin))
            } else {
                SignatureFile.fromFiles(files)
            }

        val codebase = read(signatureFiles)
        val outputWriter = outputFile?.printWriter() ?: stdout
        write(codebase, outputFormat, outputWriter)
    }

    private fun read(signatureFiles: List<SignatureFile>) =
        ApiFile.parseApi(
            signatureFiles,
            Codebase.Config(
                annotationManager = noOpAnnotationManager,
                reporter = BasicReporter(stderr),
            ),
        )

    private fun write(codebase: Codebase, outputFormat: FileFormat, printWriter: PrintWriter) {
        val signatureWriter =
            SignatureWriter(
                writer = printWriter,
                fileFormat = outputFormat,
            )

        // Create a visitor suitable for writing signatures. It will ensure correct ordering for
        // signature files for the outputFormat.
        val apiWriter =
            createFilteringVisitorForSignatures(
                delegate = signatureWriter,
                fileFormat = outputFormat,
                apiType = ApiType.ALL,
                preFiltered = true,
                showUnannotated = true,
                apiPredicateConfig = ApiPredicate.Config(),
            )

        codebase.accept(apiWriter)
    }
}
