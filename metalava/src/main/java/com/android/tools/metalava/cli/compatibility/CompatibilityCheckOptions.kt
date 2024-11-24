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

import com.android.tools.metalava.SignatureFileCache
import com.android.tools.metalava.cli.common.BaselineOptionsMixin
import com.android.tools.metalava.cli.common.CommonBaselineOptions
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.common.PreviouslyReleasedApi
import com.android.tools.metalava.cli.common.allowStructuredOptionName
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.map
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.visitors.ApiType
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.unique
import java.io.File

const val ARG_CHECK_COMPATIBILITY_API_RELEASED = "--check-compatibility:api:released"
const val ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED = "--check-compatibility:removed:released"
const val ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED = "--error-message:compatibility:released"

const val ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED = "--baseline:compatibility:released"
const val ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED =
    "--update-baseline:compatibility:released"
const val ARG_API_COMPAT_ANNOTATION = "--api-compat-annotation"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val COMPATIBILITY_CHECK_GROUP = "Compatibility Checks"

class CompatibilityCheckOptions(
    executionEnvironment: ExecutionEnvironment = ExecutionEnvironment(),
    commonBaselineOptions: CommonBaselineOptions = CommonBaselineOptions(),
) :
    OptionGroup(
        name = COMPATIBILITY_CHECK_GROUP,
        help =
            """
                Options controlling which, if any, compatibility checks are performed against a
                previously released API.
            """
                .trimIndent(),
    ) {

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

    internal val apiCompatAnnotations: Set<String> by
        option(
                ARG_API_COMPAT_ANNOTATION,
                help =
                    """
                        Specify an annotation important for API compatibility.

                        Adding/removing this annotation will be considered an incompatible change.
                        The fully qualified name of the annotation should be passed.
                    """
                        .trimIndent(),
                metavar = "<annotation>",
            )
            .multiple()
            .unique()

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

    private val baselineOptionsMixin =
        BaselineOptionsMixin(
            containingGroup = this,
            executionEnvironment,
            baselineOptionName = ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED,
            updateBaselineOptionName = ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED,
            issueType = "compatibility",
            description = "compatibility:released",
            commonBaselineOptions = commonBaselineOptions,
        )

    internal val baseline by baselineOptionsMixin::baseline

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
        /** The last signature file, if any, defining the previously released API. */
        val lastSignatureFile by previouslyReleasedApi::lastSignatureFile

        companion object {
            /** Create a [CheckRequest] if [files] is not empty, otherwise return `null`. */
            internal fun optionalCheckRequest(files: List<File>, apiType: ApiType) =
                PreviouslyReleasedApi.optionalPreviouslyReleasedApi(
                        checkCompatibilityOptionForApiType(apiType),
                        files,
                        apiVariantType =
                            when (apiType) {
                                ApiType.REMOVED -> ApiVariantType.REMOVED
                                else -> ApiVariantType.CORE
                            },
                    )
                    ?.let { previouslyReleasedApi -> CheckRequest(previouslyReleasedApi, apiType) }

            private fun checkCompatibilityOptionForApiType(apiType: ApiType) =
                "--check-compatibility:${apiType.flagName}:released"
        }

        override fun toString(): String {
            // This is only used when reporting progress.
            return "${checkCompatibilityOptionForApiType(apiType)} $previouslyReleasedApi"
        }
    }

    /**
     * The list of [CheckRequest] instances that need to be performed on the API being generated.
     */
    val compatibilityChecks by
        lazy(LazyThreadSafetyMode.NONE) { listOfNotNull(checkReleasedApi, checkReleasedRemoved) }

    /**
     * The optional Codebase corresponding to [compatibilityChecks].
     *
     * This is used to provide the previously released API needed for `--revert-annotation`.
     */
    fun previouslyReleasedCodebase(signatureFileCache: SignatureFileCache): Codebase? =
        compatibilityChecks
            .map { it.previouslyReleasedApi }
            .reduceOrNull { p1, p2 -> p1.combine(p2) }
            ?.load({ signatureFileCache.load(it) })
}
