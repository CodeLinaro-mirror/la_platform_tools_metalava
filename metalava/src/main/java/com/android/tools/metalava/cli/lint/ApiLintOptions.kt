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

package com.android.tools.metalava.cli.lint

import com.android.tools.metalava.cli.common.BaselineOptionsMixin
import com.android.tools.metalava.cli.common.CommonBaselineOptions
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.common.PreviouslyReleasedApi
import com.android.tools.metalava.cli.common.allowStructuredOptionName
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option

const val ARG_API_LINT = "--api-lint"
const val ARG_API_LINT_PREVIOUS_API = "--api-lint-previous-api"
const val ARG_ERROR_MESSAGE_API_LINT = "--error-message:api-lint"

const val ARG_BASELINE_API_LINT = "--baseline:api-lint"
const val ARG_UPDATE_BASELINE_API_LINT = "--update-baseline:api-lint"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val API_LINT_GROUP = "Api Lint"

class ApiLintOptions(
    executionEnvironment: ExecutionEnvironment = ExecutionEnvironment(),
    commonBaselineOptions: CommonBaselineOptions = CommonBaselineOptions(),
) :
    OptionGroup(
        name = API_LINT_GROUP,
        help =
            """
                Options controlling API linting.
            """
                .trimIndent(),
    ) {

    internal val apiLintEnabled: Boolean by
        option(
                ARG_API_LINT,
                help =
                    """
                        Check API for Android API best practices.
                    """
                        .trimIndent(),
            )
            .flag()

    internal val apiLintPreviousApis by
        option(
                ARG_API_LINT_PREVIOUS_API,
                help =
                    """
                        An API signature file that defines, albeit maybe only partially, a
                        previously released API.

                        If the API surface extends another API surface then this must include all
                        the corresponding signature files in order from the outermost API surface
                        that does not extend any API surface to the innermost one that represents
                        the API surface being generated.

                        API Lint issues found in the previously released API will be ignored.
                     """
                        .trimIndent(),
            )
            .existingFile()
            .multiple()

    /**
     * The optional [PreviouslyReleasedApi]. If provided then only API lint issues which are new
     * since that API was released will be reported.
     */
    internal val previouslyReleasedApi by
        lazy(LazyThreadSafetyMode.NONE) {
            PreviouslyReleasedApi.optionalPreviouslyReleasedApi(
                ARG_API_LINT_PREVIOUS_API,
                apiLintPreviousApis
            )
        }

    /**
     * If set, metalava will show this error message when "API lint" (i.e. [ARG_API_LINT]) fails.
     */
    internal val errorMessage: String? by
        option(
                ARG_ERROR_MESSAGE_API_LINT,
                help =
                    """
                    If set, this is output when errors are detected in $ARG_API_LINT.
                """
                        .trimIndent(),
                metavar = "<message>",
            )
            .default(DefaultLintErrorMessage, defaultForHelp = "")
            .allowStructuredOptionName()

    private val baselineOptionsMixin =
        BaselineOptionsMixin(
            containingGroup = this,
            executionEnvironment,
            baselineOptionName = ARG_BASELINE_API_LINT,
            updateBaselineOptionName = ARG_UPDATE_BASELINE_API_LINT,
            issueType = "API lint",
            description = "api-lint",
            commonBaselineOptions = commonBaselineOptions,
        )

    internal val baseline by baselineOptionsMixin::baseline
}
