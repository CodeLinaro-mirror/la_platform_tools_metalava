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

import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.cli.signature.SIGNATURE_FORMAT_OUTPUT_GROUP
import com.android.tools.metalava.model.text.EmitFileHeader
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

const val ARG_API = "--api"
const val ARG_REMOVED_API = "--removed-api"

const val ARG_DELETE_EMPTY_REMOVED_SIGNATURES = "--delete-empty-removed-signatures"

class SignatureFileOptions :
    OptionGroup(
        name = "Signature File Output",
        help =
            """
                Options controlling the signature file output. The format of the generated file is
                determined by the options in the `$SIGNATURE_FORMAT_OUTPUT_GROUP` section.
            """
                .trimIndent()
    ) {

    /** If set, a file to write an API file to. */
    val apiFile by
        option(
                ARG_API,
                help =
                    """
                        Output file into which the API signature will be generated. If this is not
                        specified then no API signature file will be created.
                    """
                        .trimIndent()
            )
            .newFile()

    /** If set, a file to write an API file containing APIs that have been removed. */
    val removedApiFile by
        option(
                ARG_REMOVED_API,
                help =
                    """
                        Output file into which the API signatures for removed APIs will be
                        generated. If this is not specified then no removed API signature file will
                        be created.
                    """
                        .trimIndent()
            )
            .newFile()

    /** If generating a removed signature file, and it is empty, delete it */
    val deleteEmptyRemovedSignatures by
        option(
                ARG_DELETE_EMPTY_REMOVED_SIGNATURES,
                help = "Deletes removed signature files if they are empty.",
            )
            .flag()

    /** Whether to include the signature file format version header in removed signature files */
    val includeSignatureFormatVersionRemoved: EmitFileHeader
        get() =
            if (deleteEmptyRemovedSignatures) {
                EmitFileHeader.IF_NONEMPTY_FILE
            } else {
                EmitFileHeader.ALWAYS
            }
}
