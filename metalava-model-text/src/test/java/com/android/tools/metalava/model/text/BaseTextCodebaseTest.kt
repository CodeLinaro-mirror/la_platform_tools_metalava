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

package com.android.tools.metalava.model.text

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.reporter.RecordingReporter
import javax.annotation.CheckReturnValue
import kotlin.test.assertEquals

/**
 * Base class for text test classes that parse signature files to create a [Codebase] that can then
 * be introspected.
 */
abstract class BaseTextCodebaseTest : BaseModelTest() {

    /** A [CodebaseContext] wrapper that records and provides access to any reported issues. */
    class RecordingCodebaseContext(
        private val delegate: CodebaseContext,
        private val recordingReporter: RecordingReporter
    ) : CodebaseContext by delegate {
        /**
         * The reported issues, with any test specific directories replaced with fixed symbols.
         *
         * Accessing this will remove any issues from the [recordingReporter] and it is the caller's
         * responsibility to check the returned value.
         */
        @get:CheckReturnValue
        val reportedIssues
            get() = removeTestSpecificDirectories(recordingReporter.removeIssues())
    }

    /** Run a single signature test with a set of signature files. */
    fun runSignatureTest(
        vararg sources: TestFile,
        test: RecordingCodebaseContext.() -> Unit,
    ) {
        // Create a recorder.
        val recordingReporter = RecordingReporter()
        val testFixture = TestFixture(reporter = recordingReporter)

        runCodebaseTest(
            inputSet(*sources),
            testFixture = testFixture,
            test = {
                val recordingContext = RecordingCodebaseContext(delegate = this, recordingReporter)
                recordingContext.test()
            }
        )

        // Make sure that any unchecked issues will cause the test to fail.
        assertEquals("", recordingReporter.issues, message = "Unexpected issues were reported")
    }
}
