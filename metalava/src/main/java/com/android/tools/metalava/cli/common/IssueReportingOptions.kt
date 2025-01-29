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

package com.android.tools.metalava.cli.common

import com.android.tools.metalava.reporter.DefaultReporter
import com.android.tools.metalava.reporter.ERROR_WHEN_NEW_SUFFIX
import com.android.tools.metalava.reporter.IssueConfiguration
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Severity
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo

const val ARG_ERROR = "--error"
const val ARG_ERROR_WHEN_NEW = "--error-when-new"
const val ARG_WARNING = "--warning"
const val ARG_HIDE = "--hide"
const val ARG_ERROR_CATEGORY = "--error-category"
const val ARG_ERROR_WHEN_NEW_CATEGORY = "--error-when-new-category"
const val ARG_WARNING_CATEGORY = "--warning-category"
const val ARG_HIDE_CATEGORY = "--hide-category"

const val ARG_WARNINGS_AS_ERRORS = "--warnings-as-errors"

const val ARG_REPORT_EVEN_IF_SUPPRESSED = "--report-even-if-suppressed"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val REPORTING_OPTIONS_GROUP = "Issue Reporting"

class IssueReportingOptions(
    commonOptions: CommonOptions = CommonOptions(),
) :
    OptionGroup(
        name = REPORTING_OPTIONS_GROUP,
        help =
            """
            Options that control which issues are reported, the severity of the reports, how, when
            and where they are reported.

            See `metalava help issues` for more help including a table of the available issues and
            their category and default severity.
        """
                .trimIndent()
    ) {

    /** The [IssueConfiguration] that is configured by these options. */
    val issueConfiguration = IssueConfiguration()

    init {
        // Create a Clikt option for handling the issue options and updating them as a side effect.
        // This needs to be a single option for handling all the issue options in one go because the
        // order of processing them matters and Clikt will collate all the values for all the
        // options together before processing them so something like this would never work if they
        // were treated as separate options.
        //     --hide Foo --error Bar --hide Bar --error Foo
        //
        // When processed immediately that is equivalent to:
        //     --hide Bar --error Foo
        //
        // However, when processed after grouping they would be equivalent to one of the following
        // depending on which was processed last:
        //     --hide Foo,Bar
        //     --error Bar,Foo
        //
        // Instead, this creates a single Clikt option to handle all the issue options, but they are
        // still collated before processing.
        //
        // Having a single Clikt option with lots of different option names does make the help hard
        // to read as it produces a single line with all the option names on it. So, this uses a
        // mechanism that will cause `MetalavaHelpFormatter` to split the option into multiple
        // separate options purely for help purposes.
        val issueOption =
            compositeSideEffectOption(
                // Create one side effect option per label.
                ConfigLabel.entries.map { label ->
                    sideEffectOption(label.optionName, help = label.help) { optionValue ->
                        // if `--hide id1,id2` was supplied on the command line then this will split
                        // it into ["id1", "id2"]
                        val values = optionValue.split(",")

                        // Update the configuration immediately
                        for (value in values) {
                            val trimmed = value.trim()
                            label.setAspectForId(issueConfiguration, trimmed)
                        }
                    }
                }
            )

        // Register the option so that Clikt will process it.
        registerOption(issueOption)
    }

    private val warningsAsErrors: Boolean by
        option(
                ARG_WARNINGS_AS_ERRORS,
                help =
                    """
                        Promote all warnings to errors.
                    """
                        .trimIndent()
            )
            .flag()

    /** Writes a list of all errors, even if they were suppressed in baseline or via annotation. */
    private val reportEvenIfSuppressedFile by
        option(
                ARG_REPORT_EVEN_IF_SUPPRESSED,
                help =
                    """
                        Write all issues into the given file, even if suppressed (via annotation or
                        baseline) but not if hidden (by '$ARG_HIDE' or '$ARG_HIDE_CATEGORY').
                    """
                        .trimIndent(),
            )
            .newOrExistingFile()

    /** When non-0, metalava repeats all the errors at the end of the run, at most this many. */
    val repeatErrorsMax by
        option(
                ARG_REPEAT_ERRORS_MAX,
                metavar = "<n>",
                help = """When specified, repeat at most N errors before finishing."""
            )
            .int()
            .restrictTo(min = 0)
            .default(0)

    internal val reporterConfig by
        lazy(LazyThreadSafetyMode.NONE) {
            val reportEvenIfSuppressedWriter = reportEvenIfSuppressedFile?.printWriter()

            DefaultReporter.Config(
                warningsAsErrors = warningsAsErrors,
                outputReportFormatter = TerminalReportFormatter.forTerminal(commonOptions.terminal),
                reportEvenIfSuppressedWriter = reportEvenIfSuppressedWriter,
            )
        }
}

/** The different configurable aspects of [IssueConfiguration]. */
private enum class ConfigurableAspect {
    /** A single issue needs configuring. */
    ISSUE {
        override fun setAspectSeverityForId(
            configuration: IssueConfiguration,
            optionName: String,
            severity: Severity,
            id: String
        ) {
            val issue =
                Issues.findIssueById(id) ?: cliError("Unknown issue id: '$optionName' '$id'")

            configuration.setSeverity(issue, severity)
        }
    },
    /** A whole category of issues needs configuring. */
    CATEGORY {
        override fun setAspectSeverityForId(
            configuration: IssueConfiguration,
            optionName: String,
            severity: Severity,
            id: String
        ) {
            try {
                val issues = Issues.findCategoryById(id).let { Issues.findIssuesByCategory(it) }

                issues.forEach { configuration.setSeverity(it, severity) }
            } catch (e: Exception) {
                throw MetalavaCliException("Option $optionName is invalid: ${e.message}", cause = e)
            }
        }
    };

    /** Configure the [IssueConfiguration] appropriately. */
    abstract fun setAspectSeverityForId(
        configuration: IssueConfiguration,
        optionName: String,
        severity: Severity,
        id: String
    )
}

/** The different labels that can be used on the command line. */
private enum class ConfigLabel(
    val optionName: String,
    /** The [Severity] which this label corresponds to. */
    val severity: Severity,
    val aspect: ConfigurableAspect,
    val help: String
) {
    ERROR(
        ARG_ERROR,
        Severity.ERROR,
        ConfigurableAspect.ISSUE,
        "Report issues of the given id as errors.",
    ),
    ERROR_WHEN_NEW(
        ARG_ERROR_WHEN_NEW,
        Severity.WARNING_ERROR_WHEN_NEW,
        ConfigurableAspect.ISSUE,
        """
            Report issues of the given id as warnings in existing code and errors in new code. The
            latter behavior relies on infrastructure that handles checking changes to the code
            detecting the ${ERROR_WHEN_NEW_SUFFIX.trim()} text in the output and preventing the
            change from being made.
        """,
    ),
    WARNING(
        ARG_WARNING,
        Severity.WARNING,
        ConfigurableAspect.ISSUE,
        "Report issues of the given id as warnings.",
    ),
    HIDE(
        ARG_HIDE,
        Severity.HIDDEN,
        ConfigurableAspect.ISSUE,
        "Hide/skip issues of the given id.",
    ),
    ERROR_CATEGORY(
        ARG_ERROR_CATEGORY,
        Severity.ERROR,
        ConfigurableAspect.CATEGORY,
        "Report all issues in the given category as errors.",
    ),
    ERROR_WHEN_NEW_CATEGORY(
        ARG_ERROR_WHEN_NEW_CATEGORY,
        Severity.WARNING_ERROR_WHEN_NEW,
        ConfigurableAspect.CATEGORY,
        "Report all issues in the given category as errors-when-new.",
    ),
    WARNING_CATEGORY(
        ARG_WARNING_CATEGORY,
        Severity.WARNING,
        ConfigurableAspect.CATEGORY,
        "Report all issues in the given category as warnings.",
    ),
    HIDE_CATEGORY(
        ARG_HIDE_CATEGORY,
        Severity.HIDDEN,
        ConfigurableAspect.CATEGORY,
        "Hide/skip all issues in the given category.",
    );

    /** Configure the aspect identified by [id] into the [configuration]. */
    fun setAspectForId(configuration: IssueConfiguration, id: String) {
        aspect.setAspectSeverityForId(configuration, optionName, severity, id)
    }
}
