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
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

/**
 * Manages options needed to create a [Baseline] object.
 *
 * Ideally, this would just be a plain [OptionGroup] that is added to a [CliktCommand] or another
 * [OptionGroup] that needs a [Baseline]. Unfortunately, Clikt does not support [OptionGroup]s
 * inside of [OptionGroup]s so instead this just adds the options to the [containingParameterHolder]
 * (which can be either a [CliktCommand] or an [OptionGroup]).
 *
 * @param containingParameterHolder the [ParameterHolder] (e.g. [CliktCommand] or [OptionGroup]) to
 *   which this will add options.
 */
class BaselineOptionsMixin(
    containingParameterHolder: ParameterHolder,
    baselineOptionName: String,
    updateBaselineOptionName: String,
    issueType: String,
    private val description: String,
) : ParameterHolder by containingParameterHolder {

    private val baselineFile by
        option(
                baselineOptionName,
                help =
                    """
                        An optional baseline file that contains a list of known $issueType issues
                        which should be ignored.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .allowStructuredOptionName()

    private val updateBaselineFile by
        option(
                updateBaselineOptionName,
                help =
                    """
                        An optional file into which a list of the latest $issueType issues found
                        will be written. If $baselineOptionName is specified and is a different
                        file then any issues listed in there will be copied into this file; that
                        minimizes the amount of churn in the baseline file when updating by not
                        removing legacy issues that have been fixed, and any unbaselined issues
                        will still be reported as errors. If $updateBaselineOptionName and
                        $baselineOptionName refer to the same file then the baseline will be
                        updated in place from scratch and no issues will be reported as errors. If
                        $ARG_DELETE_EMPTY_BASELINES is specified and this baseline is empty then
                        the file will be deleted.
                    """
                        .trimIndent(),
            )
            .newOrExistingFile()
            .allowStructuredOptionName()

    /** Returns a [Baseline] based on the provided options, if one exists. */
    fun computeBaseline(
        executionEnvironment: ExecutionEnvironment,
        defaultBaselineFileProvider: () -> File? = { null },
        commonBaselineOptions: ComputedCommonBaselineOptions,
    ): Baseline? =
        computeBaseline(
            executionEnvironment = executionEnvironment,
            defaultBaselineFileProvider = defaultBaselineFileProvider,
            baselineConfig = commonBaselineOptions.baselineConfig,
        )

    /** Returns a [Baseline] based on the provided options, if one exists. */
    fun computeBaseline(
        executionEnvironment: ExecutionEnvironment,
        defaultBaselineFileProvider: () -> File? = { null },
        baselineConfig: Baseline.Config,
    ): Baseline? {
        return Baseline.Builder()
            .apply {
                this.description = this@BaselineOptionsMixin.description
                file = baselineFile ?: defaultBaselineFileProvider()
                updateFile = updateBaselineFile
                headerComment =
                    if (executionEnvironment.isBuildingAndroid())
                        "// See tools/metalava/API-LINT.md for how to update this file.\n\n"
                    else ""
            }
            .build(baselineConfig)
    }
}
