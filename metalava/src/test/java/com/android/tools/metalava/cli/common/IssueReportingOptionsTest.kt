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

import com.android.tools.metalava.reporter.IssueConfiguration
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Severity
import org.junit.Assert.assertEquals
import org.junit.Test

val ISSUE_REPORTING_OPTIONS_HELP =
    """
Issue Reporting:

  Options that control which issues are reported, the severity of the reports, how, when and where they are reported.

  See `metalava help issues` for more help including a table of the available issues and their category and default
  severity.

  --error <id>                               Report issues of the given id as errors.
  --error-when-new <id>                      Report issues of the given id as warnings in existing code and errors in
                                             new code. The latter behavior relies on infrastructure that handles
                                             checking changes to the code detecting the (ErrorWhenNew) text in the
                                             output and preventing the change from being made.
  --warning <id>                             Report issues of the given id as warnings.
  --hide <id>                                Hide/skip issues of the given id.
  --error-category <name>                    Report all issues in the given category as errors.
  --error-when-new-category <name>           Report all issues in the given category as errors-when-new.
  --warning-category <name>                  Report all issues in the given category as warnings.
  --hide-category <name>                     Hide/skip all issues in the given category.
  --warnings-as-errors                       Promote all warnings to errors.
  --report-even-if-suppressed <file>         Write all issues into the given file, even if suppressed (via annotation or
                                             baseline) but not if hidden (by '--hide' or '--hide-category').
  --repeat-errors-max <n>                    When specified, repeat at most N errors before finishing. (default: 0)
    """
        .trimIndent()

class IssueReportingOptionsTest :
    BaseOptionGroupTest<IssueReportingOptions>(
        ISSUE_REPORTING_OPTIONS_HELP,
    ) {

    override fun createOptions() = IssueReportingOptions()

    @Test
    fun `Test issue severity options`() {
        runTest(
            "--hide",
            "StartWithLower",
            "--warning",
            "StartWithUpper",
            "--error",
            "ArrayReturn"
        ) {
            val issueConfiguration = options.issueConfiguration

            assertEquals(Severity.HIDDEN, issueConfiguration.getSeverity(Issues.START_WITH_LOWER))
            assertEquals(Severity.WARNING, issueConfiguration.getSeverity(Issues.START_WITH_UPPER))
            assertEquals(Severity.ERROR, issueConfiguration.getSeverity(Issues.ARRAY_RETURN))
        }
    }

    @Test
    fun `Test multiple issue severity options`() {
        // Purposely includes some whitespace as that is something callers of metalava do.
        runTest("--hide", "StartWithLower ,StartWithUpper, ArrayReturn") {
            val issueConfiguration = options.issueConfiguration
            assertEquals(Severity.HIDDEN, issueConfiguration.getSeverity(Issues.START_WITH_LOWER))
            assertEquals(Severity.HIDDEN, issueConfiguration.getSeverity(Issues.START_WITH_UPPER))
            assertEquals(Severity.HIDDEN, issueConfiguration.getSeverity(Issues.ARRAY_RETURN))
        }
    }

    @Test
    fun `Test issue severity options with inheriting issues`() {
        runTest("--error", "RemovedClass") {
            val issueConfiguration = options.issueConfiguration
            assertEquals(Severity.ERROR, issueConfiguration.getSeverity(Issues.REMOVED_CLASS))
            assertEquals(
                Severity.ERROR,
                issueConfiguration.getSeverity(Issues.REMOVED_DEPRECATED_CLASS)
            )
        }
    }

    @Test
    fun `Test issue severity options with case insensitive names`() {
        runTest("--hide", "arrayreturn") {
            assertEquals("Unknown issue id: '--hide' 'arrayreturn'", stderr)

            // Make sure that the ARRAY_RETURN severity was not changed.
            val issueConfiguration = options.issueConfiguration
            assertEquals(
                Issues.ARRAY_RETURN.defaultLevel,
                issueConfiguration.getSeverity(Issues.ARRAY_RETURN)
            )
        }
    }

    @Test
    fun `Test issue severity options with non-existing issue`() {
        runTest("--hide", "ThisIssueDoesNotExist") {
            assertEquals("Unknown issue id: '--hide' 'ThisIssueDoesNotExist'", stderr)
        }
    }

    @Test
    fun `Test options process in order`() {
        // Interleave and change the order so that if all the hide options are processed before all
        // the error options (or vice versa) they would result in different behavior.
        runTest(
            "--hide",
            "UnavailableSymbol",
            "--error",
            "HiddenSuperclass",
            "--hide",
            "HiddenSuperclass",
            "--error",
            "UnavailableSymbol",
        ) {

            // Make sure the two issues both default to warning.
            val baseConfiguration = IssueConfiguration()
            assertEquals(Severity.WARNING, baseConfiguration.getSeverity(Issues.HIDDEN_SUPERCLASS))
            assertEquals(Severity.WARNING, baseConfiguration.getSeverity(Issues.UNAVAILABLE_SYMBOL))

            // Now make sure the issues fine.
            val issueConfiguration = options.issueConfiguration
            assertEquals(Severity.HIDDEN, issueConfiguration.getSeverity(Issues.HIDDEN_SUPERCLASS))
            assertEquals(Severity.ERROR, issueConfiguration.getSeverity(Issues.UNAVAILABLE_SYMBOL))
        }
    }

    @Test
    fun `Test issue category`() {
        runTest(ARG_HIDE_CATEGORY, "Compatibility") {
            assertEquals("", stdout)
            assertEquals("", stderr)

            // Make sure the two issues both default to warning.
            val defaults = IssueConfiguration()
            assertEquals(Severity.ERROR, defaults.getSeverity(Issues.ADD_SEALED))
            assertEquals(Severity.ERROR, defaults.getSeverity(Issues.CHANGED_CLASS))

            // Now make sure the issues are hidden.
            val issueConfiguration = options.issueConfiguration
            assertEquals(Severity.HIDDEN, issueConfiguration.getSeverity(Issues.ADD_SEALED))
            assertEquals(Severity.HIDDEN, issueConfiguration.getSeverity(Issues.CHANGED_CLASS))
        }
    }

    @Test
    fun `Test invalid category`() {
        // Category names should start with an upper case letter.
        runTest(ARG_HIDE_CATEGORY, "compatibility") {
            assertEquals("", stdout)
            assertEquals(
                "Option --hide-category is invalid: Unknown category: 'compatibility', expected one of Compatibility, Documentation, ApiLint, Unknown",
                stderr
            )
        }
    }
}
