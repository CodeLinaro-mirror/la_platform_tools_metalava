/*
 * Copyright (C) 2018 The Android Open Source Project
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

/** An issue configuration is a set of overrides for severities for various [Issues.Issue] */
class IssueConfiguration {
    private val overrides = mutableMapOf<Issues.Issue, Severity>()

    /**
     * Map from [Severity] obtained from the [Issues.Issue] to the [Severity] to pass to the
     * [Reporter].
     */
    var severityMap = emptyMap<Severity, Severity>()

    /**
     * Returns the severity of the given issue, taking into account any [overrides] and the
     * [severityMap].
     */
    fun getSeverity(issue: Issues.Issue): Severity {
        var severityOfIssue = getSeverityOfIssue(issue)

        // Map severity to a different severity if needed, e.g. map WARNING to ERROR.
        return severityMap[severityOfIssue] ?: severityOfIssue
    }

    /** Returns the severity of the given issue, taking into account any [overrides]. */
    private fun getSeverityOfIssue(issue: Issues.Issue): Severity {
        val severity = overrides[issue] ?: issue.defaultLevel

        if (severity == Severity.INHERIT) {
            return getSeverityOfIssue(issue.parent!!)
        }
        return severity
    }

    fun setSeverity(issue: Issues.Issue, severity: Severity) {
        check(severity != Severity.INHERIT)
        overrides[issue] = severity
    }

    fun setSeverityIfNotAlreadyOverridden(issue: Issues.Issue, severity: Severity) {
        check(severity != Severity.INHERIT)
        if (issue !in overrides) {
            overrides[issue] = severity
        }
    }

    /** Set the severity of the given issue to [Severity.ERROR] */
    fun error(issue: Issues.Issue) {
        setSeverity(issue, Severity.ERROR)
    }

    /** Set the severity of the given issue to [Severity.HIDDEN] */
    fun hide(issue: Issues.Issue) {
        setSeverity(issue, Severity.HIDDEN)
    }
}
