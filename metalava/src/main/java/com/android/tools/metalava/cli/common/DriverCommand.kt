/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.metalava.ApiLevelsGenerationOptions
import com.android.tools.metalava.ApiSelectionOptions
import com.android.tools.metalava.ConfigFileOptions
import com.android.tools.metalava.Driver
import com.android.tools.metalava.GeneralReportingOptions
import com.android.tools.metalava.MiscellaneousOptions
import com.android.tools.metalava.NullabilityValidationOptions
import com.android.tools.metalava.ReporterManager
import com.android.tools.metalava.SignatureFileOptions
import com.android.tools.metalava.StubGenerationOptions
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions
import com.android.tools.metalava.cli.lint.ApiLintOptions
import com.android.tools.metalava.cli.multiplatform.MultiplatformOptions
import com.android.tools.metalava.cli.signature.SignatureFormatOptions
import com.android.tools.metalava.model.source.EnvironmentManager
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.ADD_ADDITIONAL_OVERRIDES
import com.android.tools.metalava.trace
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import java.io.File

/** [MetalavaSubCommand] which invokes [Driver]. */
abstract class DriverCommand(
    commonOptions: CommonOptions,
    executionEnvironment: ExecutionEnvironment,
    help: String
) : MetalavaSubCommand(help = help) {
    /** A lambda to allow lazily accessing the [ConfigFileOptions]. */
    protected abstract val configFileOptionsProvider: () -> ConfigFileOptions

    /** A lambda to allow lazily accessing the [SourceOptions]. */
    protected abstract val sourceOptionsProvider: () -> SourceOptions

    protected val nullabilityValidationOptions by NullabilityValidationOptions()

    /** Issue reporter configuration. */
    protected val issueReportingOptions by IssueReportingOptions()

    protected val commonBaselineOptions by CommonBaselineOptions()

    /** General reporter options. */
    protected val generalReportingOptions by GeneralReportingOptions()

    protected val apiSelectionOptions: ApiSelectionOptions by ApiSelectionOptions()

    /** API lint options. */
    protected val apiLintOptions by ApiLintOptions()

    /** Multiplatform codebase options. */
    protected val multiplatformOptions by MultiplatformOptions()

    /** Compatibility check options. */
    protected val compatibilityCheckOptions by CompatibilityCheckOptions()

    /** Signature file options. */
    protected val signatureFileOptions by SignatureFileOptions()

    /** Signature format options. */
    protected val signatureFormatOptions by SignatureFormatOptions()

    /** Stub generation options. */
    protected val stubGenerationOptions by StubGenerationOptions()

    /** Api levels generation options. */
    protected val apiLevelsGenerationOptions by
        ApiLevelsGenerationOptions(
            executionEnvironment = executionEnvironment,
            earlyOptions = commonOptions,
        )

    /** Miscellaneous options. */
    protected val miscellaneousOptions by MiscellaneousOptions()

    protected abstract fun getDefaultBaselineFile(): File?

    /** Runs [Driver] using the [environmentManager] and all specified options. */
    protected fun runAndReportIssues(environmentManager: EnvironmentManager) {
        val sourceOptions = sourceOptionsProvider()
        val configFileOptions = configFileOptionsProvider()

        val computedIssueReportingOptions =
            issueReportingOptions.compute(commonOptions, configFileOptions.config.issues)
        val computedCommonBaselineOptions =
            commonBaselineOptions.compute(sourceOptions, computedIssueReportingOptions)

        val generalBaseline =
            generalReportingOptions.computeBaseline(
                executionEnvironment,
                computedCommonBaselineOptions
            ) {
                getDefaultBaselineFile()
            }
        // Manages the [Reporter]s and [Baseline]s.
        val reporterManager =
            ReporterManager(
                executionEnvironment.reporterEnvironment,
                apiLintOptions,
                compatibilityCheckOptions,
                generalBaseline,
                computedIssueReportingOptions,
                sourceOptions,
                executionEnvironment,
                computedCommonBaselineOptions
            )

        // Make sure to flush out the baseline files, close files and write any final messages.
        registerPostCommandAction {
            // Close all the baselines.
            reporterManager.closeAllBaselines(commonOptions.verbosity, stdout)

            computedIssueReportingOptions.reporterConfig.reportEvenIfSuppressedWriter?.close()

            // Show failure messages, if any.
            reporterManager.writeErrorMessages(stderr)
        }
        try {
            val computedSignatureFormatOptions = signatureFormatOptions.compute()
            val driver =
                Driver(
                    executionEnvironment,
                    tracer,
                    environmentManager,
                    reporterManager.reporter,
                    commonOptions.verbosity,
                    miscellaneousOptions.compute(reporterManager.reporter),
                    apiLevelsGenerationOptions,
                    apiLintOptions.compute(),
                    apiSelectionOptions.compute(
                        configFileOptions.config.apiSurfaces,
                        addAdditionalOverrides =
                            computedSignatureFormatOptions.fileFormat[ADD_ADDITIONAL_OVERRIDES],
                    ),
                    compatibilityCheckOptions.compute(),
                    configFileOptions,
                    computedIssueReportingOptions,
                    multiplatformOptions,
                    nullabilityValidationOptions.compute(reporterManager.reporter),
                    signatureFileOptions,
                    computedSignatureFormatOptions,
                    sourceOptions,
                    stubGenerationOptions,
                )
            tracer.trace("processFlags") { driver.processFlags() }
        } finally {
            // Write all saved reports. Do this even if the previous code threw an exception.
            reporterManager.writeSavedReports()
        }

        if (reporterManager.hasAnyErrors() && !computedCommonBaselineOptions.passBaselineUpdates) {
            // Repeat the errors at the end to make it easy to find the actual problems.
            if (issueReportingOptions.repeatErrorsMax > 0) {
                reporterManager.repeatErrors(stderr, issueReportingOptions.repeatErrorsMax)
            }

            // Make sure that the process exits with an error code.
            throw MetalavaCliException(exitCode = -1)
        }
    }
}
