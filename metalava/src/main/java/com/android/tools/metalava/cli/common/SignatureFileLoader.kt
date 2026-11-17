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

import androidx.tracing.Tracer
import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.ApiParseException
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.text.SignatureFileConsumer
import com.android.tools.metalava.trace

/** Supports loading [SignatureFile]s into a [Codebase] using an optional [ClassPathResolver]. */
interface SignatureFileLoader {
    /** Load [signatureFiles] into a [Codebase] using the optional [classPathResolver]. */
    fun load(
        signatureFiles: List<SignatureFile>,
        classPathResolver: ClassPathResolver? = null
    ): Codebase

    /** Loads [signatureFiles] into a [MultiplatformCodebase]. */
    fun loadMultiplatform(
        signatureFiles: List<SignatureFile>,
    ): MultiplatformCodebase

    /** Parses [signatureFiles] and passes each declaration to [consumer]. */
    fun consume(
        signatureFiles: List<SignatureFile>,
        consumer: SignatureFileConsumer,
    )

    companion object {
        /** A [SignatureFileLoader] that will throw an exception when called. */
        val THROWING =
            object : SignatureFileLoader {
                override fun load(
                    signatureFiles: List<SignatureFile>,
                    classPathResolver: ClassPathResolver?
                ) = throwError()

                override fun loadMultiplatform(
                    signatureFiles: List<SignatureFile>
                ): MultiplatformCodebase = throwError()

                override fun consume(
                    signatureFiles: List<SignatureFile>,
                    consumer: SignatureFileConsumer,
                ) = throwError()

                private fun throwError(): Nothing =
                    error("Throwing signature file loader cannot load signature files")
            }
    }
}

/**
 * Helper object to load signature files and rethrow any [ApiParseException] as a
 * [MetalavaCliException].
 */
class DefaultSignatureFileLoader(
    private val codebaseConfig: Codebase.Config,
    private val formatForLegacyFiles: FileFormat? = null,
    private val tracer: Tracer? = null,
) : SignatureFileLoader {

    override fun load(
        signatureFiles: List<SignatureFile>,
        classPathResolver: ClassPathResolver?,
    ): Codebase {
        require(signatureFiles.isNotEmpty()) { "files must not be empty" }

        try {
            val block = {
                ApiFile.parseApi(
                    signatureFiles = signatureFiles,
                    codebaseConfig = codebaseConfig,
                    classPathResolver = classPathResolver,
                    formatForLegacyFiles = formatForLegacyFiles,
                )
            }
            return tracer?.trace("signatureFileLoader.load") { block() } ?: block()
        } catch (ex: ApiParseException) {
            cliError("Unable to parse signature file: ${ex.message}")
        }
    }

    override fun loadMultiplatform(signatureFiles: List<SignatureFile>): MultiplatformCodebase {
        require(signatureFiles.isNotEmpty()) { "files must not be empty" }

        try {
            val block = {
                ApiFile.parseMultiplatformApi(
                    signatureFiles = signatureFiles,
                    codebaseConfig = codebaseConfig,
                )
            }
            return tracer?.trace("signatureFileLoader.loadMultiplatform") { block() } ?: block()
        } catch (ex: ApiParseException) {
            cliError("Unable to parse signature files: ${ex.message}")
        }
    }

    override fun consume(
        signatureFiles: List<SignatureFile>,
        consumer: SignatureFileConsumer,
    ) {
        require(signatureFiles.isNotEmpty()) { "files must not be empty" }

        try {
            val block = {
                ApiFile.consumeApi(
                    signatureFiles = signatureFiles,
                    consumer = consumer,
                    formatForLegacyFiles = formatForLegacyFiles,
                )
            }
            tracer?.trace("signatureFileLoader.consume") { block() } ?: block()
        } catch (ex: ApiParseException) {
            cliError("Unable to parse signature file: ${ex.message}")
        }
    }
}
