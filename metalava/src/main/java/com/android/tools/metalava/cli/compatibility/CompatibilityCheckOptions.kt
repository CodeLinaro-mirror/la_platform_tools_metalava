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

package com.android.tools.metalava.cli.compatibility

import com.android.SdkConstants
import com.android.tools.metalava.ApiType
import com.android.tools.metalava.SignatureFileCache
import com.android.tools.metalava.cli.common.allowStructuredOptionName
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.map
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.text.SignatureFile
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

const val ARG_CHECK_COMPATIBILITY_API_RELEASED = "--check-compatibility:api:released"
const val ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED = "--check-compatibility:removed:released"
const val ARG_CHECK_COMPATIBILITY_BASE_API = "--check-compatibility:base"
const val ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED = "--error-message:compatibility:released"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val COMPATIBILITY_CHECK_GROUP = "Compatibility Checks"

class CompatibilityCheckOptions :
    OptionGroup(
        name = COMPATIBILITY_CHECK_GROUP,
        help =
            """
                Options controlling which, if any, compatibility checks are performed against a
                previously released API.
            """
                .trimIndent(),
    ) {

    internal val baseApiForCompatCheck: File? by
        option(
                ARG_CHECK_COMPATIBILITY_BASE_API,
                help =
                    """
                        When performing a compat check, use the provided signature file as a base
                        api, which is treated as part of the API being checked. This allows us to
                        compute the full API surface from a partial API surface (e.g. the current
                         @SystemApi txt file), which allows us to recognize when an API is moved
                         from the partial API to the base API and avoid incorrectly flagging this
                     """
                        .trimIndent(),
            )
            .existingFile()
            .allowStructuredOptionName()

    private val checkReleasedApi: CheckRequest? by
        option(
                ARG_CHECK_COMPATIBILITY_API_RELEASED,
                help =
                    """
                        Check compatibility of the previously released API.

                        When multiple files are provided any files that are a delta on another file
                        must come after the other file, e.g. if `system` is a delta on `public` then
                        `public` must come first, then `system`. Or, in other words, they must be
                        provided in order from the narrowest API to the widest API.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .multiple()
            .allowStructuredOptionName()
            .map { CheckRequest.optionalCheckRequest(it, ApiType.PUBLIC_API) }

    private val checkReleasedRemoved: CheckRequest? by
        option(
                ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED,
                help =
                    """
                        Check compatibility of the previously released but since removed APIs.

                        When multiple files are provided any files that are a delta on another file
                        must come after the other file, e.g. if `system` is a delta on `public` then
                        `public` must come first, then `system`. Or, in other words, they must be
                        provided in order from the narrowest API to the widest API.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .multiple()
            .allowStructuredOptionName()
            .map { CheckRequest.optionalCheckRequest(it, ApiType.REMOVED) }

    /**
     * If set, metalava will show this error message when "check-compatibility:*:released" fails.
     * (i.e. [ARG_CHECK_COMPATIBILITY_API_RELEASED] and [ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED])
     */
    internal val errorMessage: String? by
        option(
                ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED,
                help =
                    """
                        If set, this is output when errors are detected in
                        $ARG_CHECK_COMPATIBILITY_API_RELEASED or
                        $ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED.
                    """
                        .trimIndent(),
                metavar = "<message>",
            )
            .allowStructuredOptionName()

    /** A previously released API. */
    sealed interface PreviouslyReleasedApi {
        /** The set of files defining the previously released API. */
        val files: List<File>

        /** Load the files into a list of [Codebase]s. */
        fun load(
            jarLoader: (File) -> Codebase,
            signatureLoader: (SignatureFile) -> Codebase,
        ): List<Codebase>
    }

    /** A previously released API defined by jar files. */
    data class JarBasedApi(override val files: List<File>) : PreviouslyReleasedApi {
        override fun load(
            jarLoader: (File) -> Codebase,
            signatureLoader: (SignatureFile) -> Codebase,
        ) = files.map { jarLoader(it) }
    }

    /**
     * A previously released API defined by signature files.
     *
     * If a single file is provided then it may be a full API or a delta on another API. If multiple
     * files are provided then they are expected to be provided in order from the narrowest API to
     * the widest API, where all but the first files are deltas on the preceding file.
     */
    data class SignatureBasedApi(val signatureFiles: List<SignatureFile>) : PreviouslyReleasedApi {

        override val files: List<File> = signatureFiles.map { it.file }

        override fun load(
            jarLoader: (File) -> Codebase,
            signatureLoader: (SignatureFile) -> Codebase,
        ) = signatureFiles.map { signatureLoader(it) }

        companion object {
            fun fromFiles(files: List<File>): SignatureBasedApi {
                val lastIndex = files.size - 1
                return SignatureBasedApi(
                    files.mapIndexed { index, file ->
                        SignatureFile(
                            file,
                            // The last file is assumed to be for the current API surface.
                            forCurrentApiSurface = index == lastIndex,
                        )
                    }
                )
            }
        }
    }

    /**
     * Encapsulates information needed to perform a compatibility check of the current API being
     * generated against a previously released API.
     */
    data class CheckRequest(
        /**
         * The previously released API with which the API being generated must be compatible.
         *
         * Each file is either a jar file (i.e. has an extension of `.jar`), or otherwise is a
         * signature file. The latter's extension is not checked because while it usually has an
         * extension of `.txt`, for legacy reasons Metalava will treat any file without a `,jar`
         * extension as if it was a signature file.
         */
        val previouslyReleasedApi: PreviouslyReleasedApi,

        /** The part of the API to be checked. */
        val apiType: ApiType,
    ) {
        /** The files defining the previously released API. */
        val files by previouslyReleasedApi::files

        companion object {
            /** Create a [CheckRequest] if [files] is not empty, otherwise return `null`. */
            internal fun optionalCheckRequest(files: List<File>, apiType: ApiType) =
                if (files.isEmpty()) null
                else {
                    // Partition the files into jar and non-jar files, the latter are assumed to be
                    // signature files.
                    val (jarFiles, signatureFiles) =
                        files.partition { it.path.endsWith(SdkConstants.DOT_JAR) }
                    when {
                        jarFiles.isEmpty() ->
                            CheckRequest(SignatureBasedApi.fromFiles(signatureFiles), apiType)
                        signatureFiles.isEmpty() -> CheckRequest(JarBasedApi(jarFiles), apiType)
                        else ->
                            throw IllegalStateException(
                                "${checkCompatibilityOptionForApiType(apiType)}: Cannot mix jar files (e.g. ${jarFiles.first()}) and signature files (e.g. ${signatureFiles.first()})"
                            )
                    }
                }

            private fun checkCompatibilityOptionForApiType(apiType: ApiType) =
                "--check-compatibility:${apiType.flagName}:released"
        }

        /** Load the previously released API [files] in as a list of [Codebase]s. */
        fun loadPreviouslyReleasedApi(
            jarLoader: (File) -> Codebase,
            signatureLoader: (SignatureFile) -> Codebase,
        ) = previouslyReleasedApi.load(jarLoader, signatureLoader)

        override fun toString(): String {
            // This is only used when reporting progress.
            return checkCompatibilityOptionForApiType(apiType) + " ${files.joinToString(",")}"
        }
    }

    /**
     * The list of [CheckRequest] instances that need to be performed on the API being generated.
     */
    val compatibilityChecks by
        lazy(LazyThreadSafetyMode.NONE) { listOfNotNull(checkReleasedApi, checkReleasedRemoved) }

    /**
     * The list of [Codebase]s corresponding to [compatibilityChecks].
     *
     * This is used to provide the previously released API needed for `--revert-annotation`. It does
     * not support jar files.
     */
    fun previouslyReleasedCodebases(signatureFileCache: SignatureFileCache): List<Codebase> =
        compatibilityChecks.flatMap {
            it.previouslyReleasedApi.load(
                {
                    throw IllegalStateException(
                        "Unexpected file $it: jar files do not work with --revert-annotation"
                    )
                },
                { signatureFileCache.load(it) }
            )
        }
}
