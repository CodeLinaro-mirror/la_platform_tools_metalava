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
import com.github.ajalt.clikt.core.GroupableOption
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

/**
 * Manages options needed to create a [Baseline] object.
 *
 * Ideally, this would just be a plain [OptionGroup] that is added to another [OptionGroup] that
 * needs a [Baseline]. Unfortunately, Clikt does not support [OptionGroup]s inside of [OptionGroup]s
 * so instead this just adds the options to the [containingGroup].
 *
 * @param containingGroup the [OptionGroup] to which this will add options.
 */
class BaselineOptionsMixin(
    private val containingGroup: OptionGroup,
    executionEnvironment: ExecutionEnvironment,
    baselineOptionName: String,
    updateBaselineOptionName: String,
    defaultBaselineFileProvider: () -> File? = { null },
    issueType: String,
    description: String,
    commonBaselineOptions: CommonBaselineOptions,
) : ParameterHolder {

    override fun registerOption(option: GroupableOption) {
        // Register the option with the actual [OptionGroup].
        containingGroup.registerOption(option)
    }

    private val baselineFile by
        containingGroup
            .option(
                baselineOptionName,
                help =
                    """
                        An optional baseline file that contains a list of known $issueType issues
                        which should be ignored. If this does not exist and
                        $updateBaselineOptionName is not specified then it will be created and
                        populated with all the known $issueType issues.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .allowStructuredOptionName()

    private val updateBaselineFile by
        containingGroup
            .option(
                updateBaselineOptionName,
                help =
                    """
                        An optional file into which a list of the latest $issueType issues found
                        will be written. If $baselineOptionName is specified then any issues listed
                        in there will be copied into this file; that minimizes the amount of churn
                        in the baseline file when updating by not removing legacy issues that have
                        been fixed. If $ARG_DELETE_EMPTY_BASELINES is specified and this baseline is
                        empty then the file will be deleted.
                    """
                        .trimIndent(),
            )
            .newOrExistingFile()
            .allowStructuredOptionName()

    val baseline by
        lazy(LazyThreadSafetyMode.NONE) {
            Baseline.Builder()
                .apply {
                    this.description = description
                    file = baselineFile ?: defaultBaselineFileProvider()
                    updateFile = updateBaselineFile
                    headerComment =
                        if (executionEnvironment.isBuildingAndroid())
                            "// See tools/metalava/API-LINT.md for how to update this file.\n\n"
                        else ""
                }
                .build(commonBaselineOptions.baselineConfig)
        }
}
