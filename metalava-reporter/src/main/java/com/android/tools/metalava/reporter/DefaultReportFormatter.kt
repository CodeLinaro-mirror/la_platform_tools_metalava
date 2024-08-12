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

/** Formats a [Report] for output. */
open class DefaultReportFormatter protected constructor() : ReportFormatter {

    protected open fun beginImportantSection(builder: StringBuilder) {}

    protected open fun beginSeverity(builder: StringBuilder, severity: Severity) {}

    protected open fun endSeverity(builder: StringBuilder) {}

    protected open fun endImportantSection(builder: StringBuilder) {}

    override fun format(report: Report) = buildString {
        val (severity, relativePath, line, message, id) = report
        beginImportantSection(this)
        relativePath?.let {
            append(it)
            if (line > 0) append(":").append(line)
            append(": ")
        }

        beginSeverity(this, severity)
        when (severity) {
            Severity.INFO -> {
                append("info: ")
            }
            Severity.WARNING,
            Severity.WARNING_ERROR_WHEN_NEW -> {
                append("warning: ")
            }
            Severity.ERROR -> {
                append("error: ")
            }
            Severity.INHERIT,
            Severity.HIDDEN -> {}
        }
        endSeverity(this)
        endImportantSection(this)

        append(message)
        append(severity.messageSuffix)
        id?.let<Issues.Issue, Unit> { append(" [").append(it.name).append("]") }
    }

    companion object {
        val DEFAULT = DefaultReportFormatter()
    }
}
