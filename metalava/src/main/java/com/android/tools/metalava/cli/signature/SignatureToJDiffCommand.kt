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

import com.android.tools.metalava.JDiffXmlWriter
import com.android.tools.metalava.cli.common.DefaultSignatureFileLoader
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.cli.common.tracer
import com.android.tools.metalava.createFilteringVisitorForJDiffWriter
import com.android.tools.metalava.createOutputFileFromCodebaseFragment
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.text.SnapshotDeltaMaker
import com.android.tools.metalava.reporter.BasicReporter
import com.android.tools.metalava.trace
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option

class SignatureToJDiffCommand :
    MetalavaSubCommand(
        help =
            """
                Convert an API signature file into a file in the JDiff XML format.
            """
                .trimIndent()
    ) {

    private val formatForLegacyFiles by
        option(
                "--format-for-legacy-files",
                metavar = "<format-specifier>",
                help =
                    """
                        Optional format to use when reading legacy, i.e. no longer supported, format
                        versions. Forces the signature file to be parsed as if it was in this
                        format.

                        This is provided primarily to allow version 1.0 files, which had no header,
                        to be parsed as if they were 2.0 files (by specifying
                        `--format-for-legacy-files=2.0`) so that version 1.0 files can still be read
                        even though metalava no longer supports version 1.0 files specifically. That
                        is effectively what metalava did anyway before it removed support for
                        version 1.0 files so should work reasonably well.

                        Applies to both `--base-api` and `<api-file>`.
                    """
                        .trimIndent()
            )
            .convert { specifier -> FileFormat.parseSpecifier(specifier) }

    private val baseApiFile by
        option(
                "--base-api",
                metavar = "<base-api-file>",
                help =
                    """
                        Optional base API file. If provided then the output will only include API
                        items that are not in this file.
                    """
                        .trimIndent()
            )
            .existingFile()

    private val apiFile by
        argument(
                name = "<api-file>",
                help =
                    """
                        API signature file to convert to the JDiff XML format.
                    """
                        .trimIndent()
            )
            .existingFile()

    private val xmlFile by
        argument(
                name = "<xml-file>",
                help =
                    """
                        Output JDiff XML format file.
                    """
                        .trimIndent()
            )
            .newFile()

    override fun run() {
        val annotationManager = DefaultAnnotationManager()
        val codebaseConfig =
            Codebase.Config(
                annotationManager = annotationManager,
                reporter = BasicReporter(stderr),
            )
        val signatureFileLoader =
            DefaultSignatureFileLoader(
                codebaseConfig = codebaseConfig,
                formatForLegacyFiles = formatForLegacyFiles,
            )

        val signatureApi = signatureFileLoader.load(SignatureFile.fromFiles(apiFile))

        val baseFile = baseApiFile

        val signatureFragment =
            CodebaseFragment.create(signatureApi) { delegate ->
                createFilteringVisitorForJDiffWriter(
                    delegate,
                    // Signature files are always pre-filtered so require no filtering.
                    apiFilters = null,
                )
            }

        val outputFragment =
            if (baseFile != null) {
                // Convert base on a diff
                val baseApi = signatureFileLoader.load(SignatureFile.fromFiles(baseFile))
                SnapshotDeltaMaker.createDelta(
                    baseApi,
                    signatureFragment,
                    checkMemberItemEquivalence = false,
                    allowClassModifierChanges = false,
                )
            } else {
                signatureFragment
            }

        // See JDiff's XMLToAPI#nameAPI
        val apiName = xmlFile.nameWithoutExtension.replace(' ', '_')
        tracer.trace("createOutputFileFromCodebaseFragment JDiff") {
            createOutputFileFromCodebaseFragment(
                outputFragment,
                xmlFile,
            ) { printWriter ->
                JDiffXmlWriter(
                    writer = printWriter,
                    apiName = apiName,
                )
            }
        }
    }
}
