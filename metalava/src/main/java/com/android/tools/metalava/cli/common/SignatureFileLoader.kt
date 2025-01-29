/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.cli.common

import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.ApiParseException
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile

/** Supports loading [SignatureFile]s into a [Codebase] using an optional [ClassResolver]. */
interface SignatureFileLoader {
    /** Load [signatureFiles] into a [Codebase] using the optional [classResolver]. */
    fun load(signatureFiles: List<SignatureFile>, classResolver: ClassResolver? = null): Codebase
}

/**
 * Helper object to load signature files and rethrow any [ApiParseException] as a
 * [MetalavaCliException].
 */
class DefaultSignatureFileLoader(
    private val codebaseConfig: Codebase.Config,
    private val formatForLegacyFiles: FileFormat? = null,
) : SignatureFileLoader {

    override fun load(
        signatureFiles: List<SignatureFile>,
        classResolver: ClassResolver?,
    ): Codebase {
        require(signatureFiles.isNotEmpty()) { "files must not be empty" }

        try {
            return ApiFile.parseApi(
                signatureFiles = signatureFiles,
                codebaseConfig = codebaseConfig,
                classResolver = classResolver,
                formatForLegacyFiles = formatForLegacyFiles,
            )
        } catch (ex: ApiParseException) {
            cliError("Unable to parse signature file: ${ex.message}")
        }
    }
}
