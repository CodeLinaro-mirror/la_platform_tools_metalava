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

package com.android.tools.metalava.reporter

import com.android.tools.metalava.reporter.Severity.ERROR
import com.android.tools.metalava.reporter.Severity.HIDDEN
import com.android.tools.metalava.reporter.Severity.WARNING
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.file.Path
import java.util.function.Predicate

class DefaultReporter(
    private val environment: ReporterEnvironment,
    private val issueConfiguration: IssueConfiguration,

    /** [Baseline] file associated with this [Reporter]. */
    private val baseline: Baseline? = null,

    /**
     * An error message associated with this [Reporter], which should be shown to the user when
     * metalava finishes with errors.
     */
    private val errorMessage: String? = null,

    /** Filter to hide issues reported on specific types of [Reportable]. */
    private val reportableFilter: Predicate<Reportable>? = null,

    /** Additional config properties. */
    private val config: Config = Config(),
) : Reporter {

    /** A list of [Report] objects containing all the reported issues. */
    private val reports = mutableListOf<Report>()

    private var warningCount = 0

    /**
     * Configuration properties for the reporter.
     *
     * This contains properties that are shared across all instances of [DefaultReporter], except
     * for the bootstrapping reporter. That receives a default instance of this.
     */
    class Config(
        /** If true, treat all warnings as errors */
        val warningsAsErrors: Boolean = false,

        /** Formats the report suitable for use in a file. */
        val fileReportFormatter: ReportFormatter = DefaultReportFormatter.DEFAULT,

        /** Formats the report for output, e.g. to a terminal. */
        val outputReportFormatter: ReportFormatter = fileReportFormatter,

        /**
         * Optional writer to which, if present, all errors, even if they were suppressed in
         * baseline or via annotation, will be written.
         */
        val reportEvenIfSuppressedWriter: PrintWriter? = null,
    )

    /** The number of errors. */
    var errorCount: Int = 0
        private set

    /** Returns whether any errors have been detected. */
    fun hasErrors(): Boolean = errorCount > 0

    override fun report(
        id: Issues.Issue,
        reportable: Reportable?,
        message: String,
        location: FileLocation,
        maximumSeverity: Severity,
    ): Boolean {
        val severity = issueConfiguration.getSeverity(id)
        val upgradedSeverity =
            if (severity == WARNING && config.warningsAsErrors) {
                ERROR
            } else {
                severity
            }

        // Limit the Severity to the maximum allowed.
        val effectiveSeverity = minOf(upgradedSeverity, maximumSeverity)
        if (effectiveSeverity == HIDDEN) {
            return false
        }

        // When selecting a location to use for reporting the issue the location is used in
        // preference to the item because the location is more specific. e.g. if the item is a
        // method then the location may be a line within the body of the method.
        val reportLocation =
            when {
                location.path != null -> location
                else -> reportable?.fileLocation
            }

        val report =
            Report(
                severity = effectiveSeverity,
                // Relativize the path before storing in the Report.
                relativePath = reportLocation?.path?.relativizeLocationPath(),
                line = reportLocation?.line ?: 0,
                message = message,
                issue = id,
            )

        // Optionally write to the --report-even-if-suppressed file.
        reportEvenIfSuppressed(report)

        if (isSuppressed(id, reportable, message)) {
            return false
        }

        // Apply the reportable filter if one is provided.
        if (reportable != null && reportableFilter?.test(reportable) == false) {
            return false
        }

        if (baseline != null) {
            // When selecting a key to use for in checking the baseline the reportable key is used
            // in preference to the location because the reportable key is more stable. e.g. the
            // location key may be for a specific line within a method which would change over time
            // while a key based off a method's would stay the same.
            val baselineKey =
                when {
                    // When available use the baseline key from the reportable.
                    reportable != null -> reportable.baselineKey
                    // Otherwise, use the baseline key from the file location.
                    else -> location.baselineKey
                }

            if (baselineKey != null && baseline.mark(baselineKey, message, id)) return false
        }

        return doReport(report)
    }

    override fun isSuppressed(
        id: Issues.Issue,
        reportable: Reportable?,
        message: String?
    ): Boolean {
        val severity = issueConfiguration.getSeverity(id)
        if (severity == HIDDEN) {
            return true
        }

        reportable ?: return false

        // Suppress the issue if requested for the item.
        return reportable.suppressedIssues().any { suppressMatches(it, id.name, message) }
    }

    private fun suppressMatches(value: String, id: String?, message: String?): Boolean {
        id ?: return false

        if (value == id) {
            return true
        }

        if (
            message != null &&
                value.startsWith(id) &&
                value.endsWith(message) &&
                (value == "$id:$message" || value == "$id: $message")
        ) {
            return true
        }

        return false
    }

    /**
     * Relativize this against the [ReporterEnvironment.rootFolder] if specified.
     *
     * Tests will set [ReporterEnvironment.rootFolder] to the temporary directory so that this can
     * remove that from any paths that are reported to avoid the test having to be aware of the
     * temporary directory.
     */
    private fun Path.relativizeLocationPath(): String {
        // b/255575766: Note that `relativize` requires two paths to compare to have same types:
        // either both of them are absolute paths or both of them are not absolute paths.
        val path = environment.rootFolder.toPath().relativize(this) ?: this
        return path.toString()
    }

    /** Alias to allow method reference to `dispatch` in [report] */
    private fun doReport(report: Report): Boolean {
        val severity = report.severity
        when (severity) {
            ERROR -> errorCount++
            WARNING -> warningCount++
            else -> {}
        }

        reports.add(report)
        return true
    }

    private fun reportEvenIfSuppressed(report: Report): Boolean {
        config.reportEvenIfSuppressedWriter?.println(config.fileReportFormatter.format(report))
        return true
    }

    /** Print all the recorded errors to the given writer. Returns the number of errors printed. */
    fun printErrors(writer: PrintWriter, maxErrors: Int): Int {
        val errors = reports.filter { it.severity == ERROR }.take(maxErrors)
        for (error in errors) {
            val formattedMessage = config.outputReportFormatter.format(error)
            writer.println(formattedMessage)
        }
        return errors.size
    }

    /** Write all reports. */
    fun writeSavedReports() {
        // Sort the reports in place. This will ensure that the errors output in [printErrors] are
        // also sorted in the same order as that is called after this.
        reports.sortWith(reportComparator)

        // Print out all the save reports.
        for (report in reports) {
            val formattedMessage = config.outputReportFormatter.format(report)
            environment.printReport(formattedMessage, report.severity)
        }
    }

    /** Write the error message set to this [Reporter], if any errors have been detected. */
    fun writeErrorMessage(writer: PrintWriter) {
        if (hasErrors()) {
            errorMessage?.let { writer.write(it) }
        }
    }

    fun getBaselineDescription(): String {
        val file = baseline?.file
        return if (file != null) {
            "baseline ${file.path}"
        } else {
            "no baseline"
        }
    }

    companion object {
        private val reportComparator =
            compareBy<Report>(
                { it.relativePath },
                { it.line },
                { it.severity },
                { it.issue?.name },
                { it.message },
            )
    }
}

/**
 * Provides access to information about the environment within which the [Reporter] will be being
 * used.
 */
interface ReporterEnvironment {

    /** Root folder, against which location paths will be relativized to simplify the output. */
    val rootFolder: File

    /** Print the report. */
    fun printReport(message: String, severity: Severity)
}

class DefaultReporterEnvironment(
    val stdout: PrintWriter = PrintWriter(OutputStreamWriter(System.out)),
    val stderr: PrintWriter = PrintWriter(OutputStreamWriter(System.err)),
) : ReporterEnvironment {

    override val rootFolder = File("").absoluteFile

    override fun printReport(message: String, severity: Severity) {
        val output = if (severity == ERROR) stderr else stdout
        output.println(message.trim())
        output.flush()
    }
}
