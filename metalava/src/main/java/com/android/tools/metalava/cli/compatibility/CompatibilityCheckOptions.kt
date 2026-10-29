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

import com.android.tools.metalava.cli.common.BaselineOptionsMixin
import com.android.tools.metalava.cli.common.ComputedCommonBaselineOptions
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.common.MetalavaOptionGroup
import com.android.tools.metalava.cli.common.PreviouslyReleasedApi
import com.android.tools.metalava.cli.common.allowStructuredOptionName
import com.android.tools.metalava.cli.common.enumOption
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.map
import com.android.tools.metalava.cli.compatibility.CheckRequest.CheckType
import com.android.tools.metalava.reporter.Baseline
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.unique

const val ARG_CHECK_COMPATIBILITY = "--check-compatibility"

const val ARG_CHECK_COMPATIBILITY_API_RELEASED = "--check-compatibility:api:released"
const val ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED = "--check-compatibility:removed:released"
const val ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED = "--error-message:compatibility:released"

const val ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED = "--baseline:compatibility:released"
const val ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED =
    "--update-baseline:compatibility:released"
const val ARG_API_COMPAT_ANNOTATION = "--api-compat-annotation"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val COMPATIBILITY_CHECK_GROUP = "Compatibility Checks"

class CompatibilityCheckOptions() :
    MetalavaOptionGroup(
        name = COMPATIBILITY_CHECK_GROUP,
        help =
            """
                Options controlling which, if any, compatibility checks are performed against a
                previously released API.
            """
                .trimIndent(),
    ) {

    private val checkCompatibility: CheckCompatibility by
        enumOption(
            ARG_CHECK_COMPATIBILITY,
            help =
                """
                   Determines whether the $ARG_CHECK_COMPATIBILITY_API_RELEASED and
                   $ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED cause a compatibility check to be
                   performed. This must be set to `disabled` when those options are only provided to
                   supply the previously released API to which flagged APIs are reverted.
                """
                    .trimIndent(),
            enumValueHelpGetter = { it.help },
            default = CheckCompatibility.ENABLED,
        )

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
            .map { CheckRequest.optionalCheckRequest(it, CheckType.PUBLIC_API) }

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
            .map { CheckRequest.optionalCheckRequest(it, CheckType.REMOVED) }

    private val apiCompatAnnotations: Set<String> by
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
            baselineOptionName = ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED,
            updateBaselineOptionName = ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED,
            issueType = "compatibility",
        )

    /** Returns a [Baseline] for compatibility checks, if there is one. */
    internal fun computeBaseline(
        executionEnvironment: ExecutionEnvironment = ExecutionEnvironment(),
        commonBaselineOptions: ComputedCommonBaselineOptions,
    ): Baseline? {
        return baselineOptionsMixin.computeBaseline(
            executionEnvironment,
            description = "compatibility:released",
            commonBaselineOptions = commonBaselineOptions,
        )
    }

    /**
     * Returns a [ComputedCompatibilityCheckOptions] instance based on the current state of the
     * options.
     */
    fun compute(): ComputedCompatibilityCheckOptions {
        return ComputedCompatibilityCheckOptions(
            checkReleasedApi,
            checkReleasedRemoved,
            checkCompatibility,
            apiCompatAnnotations,
        )
    }
}

/**
 * Options related to compatibility checks and additional values computed based on those options.
 */
class ComputedCompatibilityCheckOptions
internal constructor(
    checkReleasedApi: CheckRequest?,
    checkReleasedRemoved: CheckRequest?,
    checkCompatibility: CheckCompatibility?,
    val apiCompatAnnotations: Set<String>,
) {
    /**
     * The list of unfiltered [CheckRequest] instances that need to be performed on the API being
     * generated.
     */
    private val unfilteredCompatibilityChecks: List<CheckRequest> =
        listOfNotNull(checkReleasedApi, checkReleasedRemoved)

    /**
     * The list of [CheckRequest] instances that need to be performed on the API being generated
     * taking into account [checkCompatibility].
     */
    val compatibilityChecks: List<CheckRequest> =
        when (checkCompatibility) {
            CheckCompatibility.ENABLED -> unfilteredCompatibilityChecks
            else -> emptyList()
        }

    /** The optional [PreviouslyReleasedApi]. */
    val previouslyReleasedApi: PreviouslyReleasedApi? =
        unfilteredCompatibilityChecks
            .map { it.previouslyReleasedApi }
            .reduceOrNull { p1, p2 -> p1.combine(p2) }
}

/**
 * Determines whether [compatibilityChecks] returns a list of [checkReleasedApi] and
 * [checkReleasedRemoved] or not.
 */
internal enum class CheckCompatibility(val help: String) {
    ENABLED(
        help = "Compatibility checks are performed.",
    ),
    @Suppress("unused") // Used implicitly by [checkCompatibility]
    DISABLED(
        help = "Compatibility checks are NOT performed.",
    ),
}
