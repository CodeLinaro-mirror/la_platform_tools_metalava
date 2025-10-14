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

package com.android.tools.metalava.reporter

import java.io.File
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Test

private val NULL_REPORTABLE: Reportable? = null

class RecordingReporterTest {
    /** Context object used to supply [reporter] to the lambda in [checkReporter]. */
    private class Context(val reporter: Reporter)

    /**
     * Check output of a [RecordingReporter].
     *
     * Invokes [test] with a [Context] that contains the [Reporter] being tested. Checks the
     * reported issues against the [expectedOutput].
     */
    private fun checkReporter(expectedOutput: String, test: Context.() -> Unit) {
        val reporter = RecordingReporter()
        val context = Context(reporter)
        context.test()
        assertEquals(expectedOutput, reporter.removeIssues())
    }

    @Test
    fun `Test reporter with unknown file location`() {
        checkReporter(
            expectedOutput = "null: error: message [InvalidSyntax]",
        ) {
            reporter.report(Issues.INVALID_SYNTAX, NULL_REPORTABLE, "message", FileLocation.UNKNOWN)
        }
    }

    @Test
    fun `Test reporter with file location with path only`() {
        checkReporter(
            expectedOutput = "file.txt: error: message [InvalidSyntax]",
        ) {
            reporter.report(
                Issues.INVALID_SYNTAX,
                NULL_REPORTABLE,
                "message",
                FileLocation.forFile(File("file.txt"))
            )
        }
    }

    @Test
    fun `Test reporter with file location with path and line number`() {
        checkReporter(
            expectedOutput = "file.txt:9: error: message [InvalidSyntax]",
        ) {
            reporter.report(
                Issues.INVALID_SYNTAX,
                NULL_REPORTABLE,
                "message",
                FileLocation.createLocation(Paths.get("file.txt"), line = 9)
            )
        }
    }

    @Test
    fun `Test reporter with file location with path and line number and character position`() {
        checkReporter(
            expectedOutput = "file.txt:9:12: error: message [InvalidSyntax]",
        ) {
            reporter.report(
                Issues.INVALID_SYNTAX,
                NULL_REPORTABLE,
                "message",
                FileLocation.createLocation(Paths.get("file.txt"), line = 9, characterPosition = 12)
            )
        }
    }
}
