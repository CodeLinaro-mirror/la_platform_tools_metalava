/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.text.SignatureFile
import java.io.File

/** A previously released API. */
sealed interface PreviouslyReleasedApi {

    /** The last signature file, if any, defining the previously released API. */
    val lastSignatureFile: File?

    /** Load the files into a list of [Codebase]s. */
    fun load(signatureFileLoader: (List<SignatureFile>) -> Codebase): Codebase

    /**
     * Combine this with [other] and return the result.
     *
     * This simply involves creating another [PreviouslyReleasedApi] that contains all the signature
     * files from this plus all the signature files from [other].
     */
    fun combine(other: PreviouslyReleasedApi): PreviouslyReleasedApi

    override fun toString(): String

    companion object {
        /**
         * Create an optional [PreviouslyReleasedApi] instance from the list of [files] passed to
         * the option [optionName].
         *
         * If [files] is empty then this returns `null`. If [files] contains any `.jar` files then
         * it is an error. Otherwise, this will assume all the files are signature files and return
         * [SignatureBasedApi] that wraps a list of [SignatureFile]s. files.
         */
        internal fun optionalPreviouslyReleasedApi(
            optionName: String,
            files: List<File>,
            onlyUseLastForMainApiSurface: Boolean = true,
            apiVariantType: ApiVariantType = ApiVariantType.CORE,
        ): PreviouslyReleasedApi? =
            if (files.isEmpty()) null
            else {
                // Extract the jar files, if any.
                val jarFiles = files.filter { it.path.endsWith(SdkConstants.DOT_JAR) }
                if (jarFiles.isNotEmpty())
                    error(
                        "$optionName: Can no longer check compatibility against jar files like ${jarFiles.joinToString()} please use equivalent signature files"
                    )

                SignatureBasedApi.fromFiles(
                    files,
                    onlyUseLastForMainApiSurface,
                    apiVariantType,
                )
            }
    }
}

/**
 * A previously released API defined by signature files.
 *
 * If a single file is provided then it may be a full API or a delta on another API. If multiple
 * files are provided then they are expected to be provided in order from the narrowest API to the
 * widest API, where all but the first files are deltas on the preceding file.
 */
data class SignatureBasedApi(val signatureFiles: List<SignatureFile>) : PreviouslyReleasedApi {

    override val lastSignatureFile = signatureFiles.last().file

    override fun load(
        signatureFileLoader: (List<SignatureFile>) -> Codebase,
    ) = signatureFileLoader(signatureFiles)

    override fun combine(other: PreviouslyReleasedApi) =
        SignatureBasedApi(signatureFiles + (other as SignatureBasedApi).signatureFiles)

    override fun toString(): String {
        return signatureFiles.joinToString(",") { it.file.path }
    }

    companion object {
        fun fromFiles(
            files: List<File>,
            onlyUseLastForMainApiSurface: Boolean = true,
            apiVariantType: ApiVariantType = ApiVariantType.CORE,
        ): SignatureBasedApi {
            val lastIndex = files.size - 1
            return SignatureBasedApi(
                SignatureFile.fromFiles(
                    files,
                    apiVariantTypeChooser = { apiVariantType },
                    forMainApiSurfacePredicate = { index, _ ->
                        // The last file is assumed to be for the main API surface.
                        !onlyUseLastForMainApiSurface || index == lastIndex
                    },
                )
            )
        }
    }
}
