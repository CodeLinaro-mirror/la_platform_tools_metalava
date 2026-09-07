/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.metalava.cli.common.ARG_SOURCE_FILES
import com.android.tools.metalava.cli.common.CommonBaselineOptions
import com.android.tools.metalava.cli.common.CommonOptions
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.common.IssueReportingOptions
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.SourceOptions
import com.android.tools.metalava.cli.common.commonOptions
import com.android.tools.metalava.cli.common.executionEnvironment
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.registerPostCommandAction
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.cli.common.tracer
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions
import com.android.tools.metalava.cli.lint.ApiLintOptions
import com.android.tools.metalava.cli.multiplatform.MultiplatformOptions
import com.android.tools.metalava.cli.signature.SignatureFormatOptions
import com.android.tools.metalava.reporter.Baseline
import com.android.tools.metalava.reporter.DEFAULT_BASELINE_NAME
import com.android.tools.metalava.reporter.Reporter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import java.io.File

/**
 * A command that is passed to [com.android.tools.metalava.cli.common.MetalavaCommand] when the main
 * metalava functionality needs to be run when no subcommand is provided.
 */
class MainCommand(
    commonOptions: CommonOptions,
    executionEnvironment: ExecutionEnvironment,
) :
    MetalavaSubCommand(
        help = "The default sub-command that is run if no sub-command is specified.",
    ) {

    /** Property into which all the arguments (and unknown options) are gathered. */
    private val additionalSourceFiles by
        argument(
                name = "source-files",
                help = "Additional source files to append to $ARG_SOURCE_FILES",
            )
            .existingFile()
            .multiple()

    internal val sourceOptions: SourceOptions by
        SourceOptions(
            executionEnvironment = executionEnvironment,
            additionalSourceFilesProvider = { additionalSourceFiles },
        )

    internal val nullabilityValidationOptions by
        NullabilityValidationOptions(
            reporterSupplier = { reporterManager.reporter },
        )

    /** Issue reporter configuration. */
    private val issueReportingOptions by
        IssueReportingOptions(
            commonOptions,
            issuesConfigProvider = { configFileOptions.config.issues },
        )

    private val commonBaselineOptions by
        CommonBaselineOptions(
            sourceOptions = sourceOptions,
            issueReportingOptions = issueReportingOptions,
        )

    /** General reporter options. */
    private val generalReportingOptions by
        GeneralReportingOptions(
            executionEnvironment = executionEnvironment,
            commonBaselineOptions = commonBaselineOptions,
            defaultBaselineFileProvider = { getDefaultBaselineFile() },
        )

    private val configFileOptions by ConfigFileOptions()

    private val apiSelectionOptions: ApiSelectionOptions by
        ApiSelectionOptions(
            apiSurfacesConfigProvider = { configFileOptions.config.apiSurfaces },
        )

    /** API lint options. */
    private val apiLintOptions by
        ApiLintOptions(
            executionEnvironment = executionEnvironment,
            commonBaselineOptions = commonBaselineOptions,
        )

    /** Multiplatform codebase options. */
    private val multiplatformOptions by MultiplatformOptions()

    /** Compatibility check options. */
    private val compatibilityCheckOptions by
        CompatibilityCheckOptions(
            executionEnvironment = executionEnvironment,
            commonBaselineOptions = commonBaselineOptions,
        )

    /** Signature file options. */
    private val signatureFileOptions by SignatureFileOptions()

    /** Signature format options. */
    private val signatureFormatOptions by SignatureFormatOptions()

    /** Stub generation options. */
    private val stubGenerationOptions by StubGenerationOptions()

    /** Api levels generation options. */
    private val apiLevelsGenerationOptions by
        ApiLevelsGenerationOptions(
            executionEnvironment = executionEnvironment,
            earlyOptions = commonOptions,
            apiSurfacesProvider = { apiSelectionOptions.apiSurfaces },
        )

    /** Miscellaneous options. */
    internal val miscellaneousOptions by
        MiscellaneousOptions(
            reporterSupplier = { reporterManager.reporter },
        )

    /** Manages the [Reporter]s and [Baseline]s. */
    val reporterManager by
        lazy(LazyThreadSafetyMode.NONE) {
            ReporterManager(
                executionEnvironment.reporterEnvironment,
                apiLintOptions,
                compatibilityCheckOptions,
                generalReportingOptions,
                issueReportingOptions,
                sourceOptions,
            )
        }

    override fun run() {
        // Make sure to flush out the baseline files, close files and write any final messages.
        registerPostCommandAction {
            // Close all the baselines.
            reporterManager.closeAllBaselines(commonOptions.verbosity, stdout)

            issueReportingOptions.reporterConfig.reportEvenIfSuppressedWriter?.close()

            // Show failure messages, if any.
            reporterManager.writeErrorMessages(stderr)
        }

        // Perform any necessary initialization.
        initializeOptionGroups()

        val sourceModelProvider =
            // Use the [SourceModelProvider] specified by the [TestEnvironment], if any.
            executionEnvironment.testEnvironment?.sourceModelProvider
                // Otherwise, use the one specified on the command line, or the default.
                ?: sourceOptions.sourceModelProvider

        try {
            sourceModelProvider
                .createEnvironmentManager(executionEnvironment.disableStderrDumping())
                .use { environmentManager ->
                    val driver =
                        Driver(
                            executionEnvironment,
                            tracer,
                            environmentManager,
                            reporterManager.reporter,
                            commonOptions.verbosity,
                            miscellaneousOptions,
                            apiLevelsGenerationOptions,
                            apiLintOptions,
                            apiSelectionOptions,
                            compatibilityCheckOptions,
                            configFileOptions,
                            issueReportingOptions,
                            multiplatformOptions,
                            nullabilityValidationOptions,
                            signatureFileOptions,
                            signatureFormatOptions,
                            sourceOptions,
                            stubGenerationOptions,
                        )
                    tracer.trace("processFlags") { driver.processFlags() }
                }
        } finally {
            // Write all saved reports. Do this even if the previous code threw an exception.
            reporterManager.writeSavedReports()
        }

        if (reporterManager.hasAnyErrors() && !commonBaselineOptions.passBaselineUpdates) {
            // Repeat the errors at the end to make it easy to find the actual problems.
            if (issueReportingOptions.repeatErrorsMax > 0) {
                reporterManager.repeatErrors(stderr, issueReportingOptions.repeatErrorsMax)
            }

            // Make sure that the process exits with an error code.
            throw MetalavaCliException(exitCode = -1)
        }
    }

    /** Initialize any option groups that require it. */
    private fun initializeOptionGroups() {
        // Make sure that any config files are processed.
        configFileOptions.config
    }

    /**
     * Produce a default file name for the baseline. It's normally "baseline.txt", but can be
     * prefixed by show annotations; e.g. @TestApi -> test-baseline.txt, @SystemApi ->
     * system-baseline.txt, etc.
     *
     * Note because the default baseline file is not explicitly set in the command line, this file
     * would trigger a --strict-input-files violation. To avoid that, always explicitly pass a
     * baseline file.
     */
    private fun getDefaultBaselineFile(): File? {
        val sourcePath = sourceOptions.sourcePath
        if (sourcePath.isNotEmpty() && sourcePath[0].path.isNotBlank()) {
            // Create the file name.
            val fileName = buildString {
                // Prefix with the API surface name, if provided.
                apiSelectionOptions.apiSurfaceName?.let { apiSurfaceName ->
                    append(apiSurfaceName)
                    append("-")
                }
                append(DEFAULT_BASELINE_NAME)
            }

            var base = sourcePath[0]
            // Convention: in AOSP, signature files are often in sourcepath/api: let's place
            // baseline files there too
            val api = File(base, "api")
            if (api.isDirectory) {
                base = api
            }
            return File(base, fileName)
        } else {
            return null
        }
    }
}
