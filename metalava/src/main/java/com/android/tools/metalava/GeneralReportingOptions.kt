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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.BaselineOptionsMixin
import com.android.tools.metalava.cli.common.CommonBaselineOptions
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.reporter.Reporter
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import java.io.File

const val ARG_BASELINE = "--baseline"
const val ARG_UPDATE_BASELINE = "--update-baseline"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val GENERAL_REPORTER_OPTIONS_GROUP = "General Reporting"

/**
 * Options related to the general [Reporter], i.e. not one specific to say API linting or
 * compatibility checks.
 */
class GeneralReportingOptions(
    executionEnvironment: ExecutionEnvironment = ExecutionEnvironment(),
    commonBaselineOptions: CommonBaselineOptions = CommonBaselineOptions(),
    defaultBaselineFileProvider: () -> File? = { null },
) :
    OptionGroup(
        name = GENERAL_REPORTER_OPTIONS_GROUP,
        help =
            """
                Options that control the reporting of general issues, i.e. not API lint or
                compatibility check issues.
            """
                .trimIndent()
    ) {

    private val baselineOptionsMixin =
        BaselineOptionsMixin(
            containingGroup = this,
            executionEnvironment,
            baselineOptionName = ARG_BASELINE,
            updateBaselineOptionName = ARG_UPDATE_BASELINE,
            defaultBaselineFileProvider = defaultBaselineFileProvider,
            issueType = "general",
            description = "base",
            commonBaselineOptions = commonBaselineOptions,
        )

    internal val baseline by baselineOptionsMixin::baseline
}
