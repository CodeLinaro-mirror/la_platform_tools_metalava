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

import java.io.BufferedWriter
import java.io.File
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

class BasicReporterTest {
    @Test
    fun `Test flushes output through buffered writer`() {
        val stringWriter = StringWriter()
        // Insert a buffered writer to buffer the output to make sure that the reporter is flushing.
        val bufferedWriter = BufferedWriter(stringWriter)
        val reporter = BasicReporter(bufferedWriter)
        val location = FileLocation.forFile(File("test"))
        reporter.report(Issues.PARSE_ERROR, reportable = null, "error", location)
        assertEquals("test: error: error [ParseError]", stringWriter.toString().trim())
    }
}
