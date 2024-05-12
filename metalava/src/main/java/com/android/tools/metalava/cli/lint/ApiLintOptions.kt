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

import com.android.tools.metalava.cli.common.existingFile
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

const val ARG_API_LINT = "--api-lint"
const val ARG_API_LINT_PREVIOUS_API = "--api-lint-previous-api"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val API_LINT_GROUP = "Api Lint"

class ApiLintOptions :
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

    internal val apiLintPreviousApi: File? by
        option(
                ARG_API_LINT_PREVIOUS_API,
                help =
                    """
                        An API signature file that defines a previously released API. API Lint 
                        issues found in that API will be ignored.
                     """
                        .trimIndent(),
            )
            .existingFile()
}
