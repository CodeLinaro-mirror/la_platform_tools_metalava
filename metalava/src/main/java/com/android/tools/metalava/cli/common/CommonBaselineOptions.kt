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

import com.android.tools.metalava.reporter.Baseline
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

const val ARG_DELETE_EMPTY_BASELINES = "--delete-empty-baselines"
const val ARG_PASS_BASELINE_UPDATES = "--pass-baseline-updates"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val BASELINE_OPTIONS_GROUP = "Baseline Files"

class CommonBaselineOptions() :
    MetalavaOptionGroup(
        name = BASELINE_OPTIONS_GROUP,
        help =
            """
                Options that provide general control over baseline files.
            """
                .trimIndent()
    ) {
    private val deleteEmptyBaselines by
        option(
                ARG_DELETE_EMPTY_BASELINES,
                help =
                    """
                    If true, if after updating a baseline file is empty then it will be deleted.
                """
                        .trimIndent()
            )
            .flag()

    private val passBaselineUpdates by
        option(
                ARG_PASS_BASELINE_UPDATES,
                help =
                    """
                        Normally, encountering errors will fail the build, even when updating
                        baselines. This flag will record issues in baseline files but otherwise
                        ignore them so that all the baselines in the source tree can be updated in
                        one go.
                    """
                        .trimIndent()
            )
            .flag()

    /**
     * Returns a [ComputedCommonBaselineOptions] instance based on the current state of the options.
     */
    fun compute(
        sourceOptions: SourceOptions = SourceOptions(),
        issueReportingOptions: ComputedIssueReportingOptions,
    ): ComputedCommonBaselineOptions {
        return ComputedCommonBaselineOptions(
            deleteEmptyBaselines = deleteEmptyBaselines,
            passBaselineUpdates = passBaselineUpdates,
            sourceOptions = sourceOptions,
            issueReportingOptions = issueReportingOptions,
        )
    }
}

/** Options related to baselines and additional values computed based on those options. */
class ComputedCommonBaselineOptions(
    private val deleteEmptyBaselines: Boolean,
    /** If updating baselines, don't fail the build */
    internal val passBaselineUpdates: Boolean,
    sourceOptions: SourceOptions,
    issueReportingOptions: ComputedIssueReportingOptions,
) {
    internal val baselineConfig by
        lazy(LazyThreadSafetyMode.NONE) {
            Baseline.Config(
                issueConfiguration = issueReportingOptions.issueConfiguration,
                deleteEmptyBaselines = deleteEmptyBaselines,
                sourcePath = sourceOptions.sourcePath,
            )
        }
}
