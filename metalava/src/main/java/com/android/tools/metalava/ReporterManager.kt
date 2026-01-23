/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.metalava.cli.common.IssueReportingOptions
import com.android.tools.metalava.cli.common.SourceOptions
import com.android.tools.metalava.cli.common.Verbosity
import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions
import com.android.tools.metalava.cli.lint.ApiLintOptions
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.reporter.Baseline
import com.android.tools.metalava.reporter.DefaultReporter
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reportable
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.ReporterEnvironment
import java.io.PrintWriter
import java.util.function.Predicate

/** Managers [Reporter] instances and their corresponding [Baseline]s. */
class ReporterManager(
    reporterEnvironment: ReporterEnvironment,
    apiLintOptions: ApiLintOptions,
    compatibilityCheckOptions: CompatibilityCheckOptions,
    generalReportingOptions: GeneralReportingOptions,
    issueReportingOptions: IssueReportingOptions,
    private val sourceOptions: SourceOptions,
) {
    /** [Reporter] that will redirect [Issues.Issue] depending on their [Issues.Category]. */
    val reporter: Reporter

    private val allReporters: List<DefaultReporter>

    private val allBaselines: List<Baseline>

    init {
        val reportableFilter = createReporterPredicate()
        // Initialize the reporters.
        val baseline = generalReportingOptions.baseline
        val reporterUnknown =
            createReporter(
                reporterEnvironment,
                issueReportingOptions,
                baseline = baseline,
                errorMessage = null,
                reportableFilter,
            )

        val reporterApiLint =
            createReporter(
                reporterEnvironment,
                issueReportingOptions,
                baseline = apiLintOptions.baseline ?: baseline,
                errorMessage = apiLintOptions.errorMessage,
                reportableFilter,
            )

        // [Reporter] for "check-compatibility:*:released".
        // i.e.
        //      [ARG_CHECK_COMPATIBILITY_API_RELEASED] and
        //      [ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED].
        val reporterCompatibilityReleased =
            createReporter(
                reporterEnvironment,
                issueReportingOptions,
                baseline = compatibilityCheckOptions.baseline ?: baseline,
                errorMessage = compatibilityCheckOptions.errorMessage,
                reportableFilter,
            )

        // A Reporter that will redirect issues to the appropriate reporter based on the issue's
        // Category.
        reporter =
            CategoryRedirectingReporter(
                defaultReporter = reporterUnknown,
                apiLintReporter = reporterApiLint,
                compatibilityReporter = reporterCompatibilityReleased,
            )

        // Build "all baselines" and "all reporters"

        // Baselines are nullable, so selectively add to the list.
        allBaselines =
            listOfNotNull(baseline, apiLintOptions.baseline, compatibilityCheckOptions.baseline)

        // Reporters are non-null.
        allReporters =
            listOf(
                reporterUnknown,
                reporterApiLint,
                reporterCompatibilityReleased,
            )
    }

    /**
     * Create an optional [Reportable] predicate that will ignore issues from (i.e. return false
     * for) [Item]s that do not match the [SourceOptions.apiPackageFilter] filter. If no filter is
     * provided then this will be `null`.
     */
    fun createReporterPredicate() =
        sourceOptions.apiPackageFilter?.let { packageFilter ->
            Predicate<Reportable> { reportable ->
                // If we are only emitting some packages (--stub-packages), don't report
                // issues from other packages
                (reportable as? Item)?.let { item ->
                    val pkg = (item as? PackageItem) ?: item.containingPackage()
                    pkg == null || packageFilter.matches(pkg)
                } ?: true
            }
        }

    /**
     * Create a [Reporter] that checks for known issues in [baseline] and prints [errorMessage], if
     * provided, when errors have been reported.
     */
    private fun createReporter(
        reporterEnvironment: ReporterEnvironment,
        issueReportingOptions: IssueReportingOptions,
        baseline: Baseline?,
        errorMessage: String?,
        reportableFilter: Predicate<Reportable>?,
    ) =
        DefaultReporter(
            environment = reporterEnvironment,
            issueConfiguration = issueReportingOptions.issueConfiguration,
            baseline = baseline,
            errorMessage = errorMessage,
            reportableFilter = reportableFilter,
            config = issueReportingOptions.reporterConfig,
        )

    /** Write custom error messages for any [Reporter] with errors. */
    fun writeErrorMessages(stderr: PrintWriter) {
        allReporters.forEach { it.writeErrorMessage(stderr) }
    }

    /** Write all the saved reports from all the [Reporter]s. */
    fun writeSavedReports() {
        allReporters.forEach { it.writeSavedReports() }
    }

    /** Return `true` if any [Reporter] has errors. */
    fun hasAnyErrors() = allReporters.any { it.hasErrors() }

    /** Repeat [max] number of errors to the [stderr]. */
    fun repeatErrors(stderr: PrintWriter, max: Int) {
        stderr.println("Error: $PROGRAM_NAME detected the following problems:")
        val totalErrors = allReporters.sumOf { it.errorCount }
        var remainingCap = max
        var totalShown = 0
        allReporters.forEach {
            val numShown = it.printErrors(stderr, remainingCap)
            remainingCap -= numShown
            totalShown += numShown
        }
        if (totalShown < totalErrors) {
            stderr.println(
                "${totalErrors - totalShown} more error(s) omitted. Search the log for 'error:' to find all of them."
            )
        }
    }

    /** Close all the [Baseline]s. */
    fun closeAllBaselines(verbosity: Verbosity, stdout: PrintWriter) {
        // Update and close all baseline files.
        allBaselines.forEach { baseline ->
            if (verbosity.verbose) {
                baseline.dumpStats(stdout)
            }
            if (baseline.close()) {
                if (!verbosity.quiet) {
                    stdout.println("$PROGRAM_NAME wrote updated baseline to ${baseline.updateFile}")
                }
            }
        }
    }
}
