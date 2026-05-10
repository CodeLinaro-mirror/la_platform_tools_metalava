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

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.EmitFileHeader
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.text.SignatureWriter
import com.android.tools.metalava.model.text.createCodebaseFragmentForSignatureFile
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.reporter.BasicReporter
import java.io.PrintWriter

/** Read [signatureFiles] into a single [Codebase], reporting any issues to [issuePrintWriter]. */
internal fun readSignatureFiles(
    signatureFiles: List<SignatureFile>,
    issuePrintWriter: PrintWriter,
) =
    ApiFile.parseApi(
        signatureFiles,
        Codebase.Config(
            reporter = BasicReporter(issuePrintWriter),
        ),
    )

/** Write [codebase] to [printWriter] as a signature file of [outputFormat]. */
internal fun writeSignatureFile(
    codebase: Codebase,
    outputFormat: FileFormat,
    printWriter: PrintWriter,
    emitFileHeader: EmitFileHeader = EmitFileHeader.ALWAYS,
) {
    val signatureWriter =
        SignatureWriter(
            writer = printWriter,
            fileFormat = outputFormat,
            emitHeader = emitFileHeader,
        )

    // Create a visitor suitable for writing signatures. It will ensure correct ordering for
    // signature files for the outputFormat.
    val codebaseFragment =
        createCodebaseFragmentForSignatureFile(
            codebase,
            fileFormat = outputFormat,
            apiType = ApiType.ALL,
            preFiltered = true,
            showUnannotated = true,
            apiPredicateConfig = ApiPredicate.Config(),
        )

    codebaseFragment.accept(signatureWriter)
}
