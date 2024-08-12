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

import com.android.tools.metalava.reporter.DefaultReportFormatter
import com.android.tools.metalava.reporter.Report
import com.android.tools.metalava.reporter.ReportFormatter
import com.android.tools.metalava.reporter.Severity

/** Formats a [Report] for output. */
internal class TerminalReportFormatter
private constructor(
    /** Whether output should be colorized */
    val terminal: Terminal = plainTerminal,
) : DefaultReportFormatter() {

    override fun beginImportantSection(builder: StringBuilder) {
        builder.append(terminal.attributes(bold = true))
    }

    override fun beginSeverity(builder: StringBuilder, severity: Severity) {
        when (severity) {
            Severity.INFO -> {
                builder.append(terminal.attributes(foreground = TerminalColor.CYAN))
            }
            Severity.WARNING,
            Severity.WARNING_ERROR_WHEN_NEW -> {
                builder.append(terminal.attributes(foreground = TerminalColor.YELLOW))
            }
            Severity.ERROR -> {
                builder.append(terminal.attributes(foreground = TerminalColor.RED))
            }
            else -> {}
        }
    }

    override fun endSeverity(builder: StringBuilder) {
        builder.append(terminal.reset())
    }

    companion object {
        fun forTerminal(terminal: Terminal): ReportFormatter = TerminalReportFormatter(terminal)
    }
}
