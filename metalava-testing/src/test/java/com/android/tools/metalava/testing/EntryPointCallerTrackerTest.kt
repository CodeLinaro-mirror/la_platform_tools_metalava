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

package com.android.tools.metalava.testing

import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.fail
import org.junit.Assert.assertThrows
import org.junit.Test

class EntryPointCallerTrackerTest {

    /**
     * The name of this class, used in [Throwable.dump] to remove any stack elements before the
     * first call into this class.
     */
    private val thisClassPrefix = javaClass.name

    /**
     * Example test data.
     *
     * This is used instead of just [EntryPointCallerTracker] as this is more like how it will
     * actually be used.
     */
    data class TestData(val name: String) {
        internal val entryPointCallerTracker = EntryPointCallerTracker()
    }

    /** A simple test infrastructure entry point. */
    @EntryPoint fun testInfraSimpleEntryPoint() = TestData("with entry point")

    /** A test infrastructure entry point that will call another entry point. */
    @EntryPoint fun testInfraReEntrantEntryPoint() = testInfraSimpleEntryPoint()

    /**
     * A test infrastructure entry point that will call out to non-test infrastructure code in
     * [body] via [testInfraExitPoint].
     */
    @EntryPoint
    fun testInfraExitThenReEnterEntryPoint(body: () -> TestData) = testInfraExitPoint(body)

    /**
     * A test infrastructure exit point that will call out to non-test infrastructure code in
     * [body].
     */
    @ExitPoint fun testInfraExitPoint(body: () -> TestData) = body()

    /**
     * A test infrastructure method that tries to be both entry and exit point which is not allowed.
     */
    @EntryPoint
    @ExitPoint
    fun testInfraCannotBeBothExitAndEntryPoint() = testInfraSimpleEntryPoint()

    /**
     * Dump the [Throwable] state for testing in [checkTrackerBehavior].
     *
     * Removes parts of the stack trace to make it more stable, e.g.
     * * line numbers, for obvious reasons.
     * * lambda numbers, e.g. (`$5` from `lambda$5`). These are removed as the number is based on
     *   the lambdas created in the source file so adding a lambda can change the numbers used
     *   elsewhere in the same file.
     * * Anything below the first call into this class as that depends on how the test is run, e.g.
     *   in the IDE, on the command line, etc.
     */
    private fun Throwable.dump() = buildString {
        append(this@dump.javaClass.name)
        append(": ")
        append(message)
        append("\n")
        val stackTraceCopy = stackTrace
        val firstCallIntoThisClass =
            stackTraceCopy.indexOfLast { it.className.startsWith(thisClassPrefix) }
        val trimmed = stackTraceCopy.sliceArray(0..firstCallIntoThisClass)
        for (stackTraceElement in trimmed) {
            append("  at ")
            append(stackTraceElement.className)
            append(".")
            // Remove the lambda number to make tests more stable.
            append(stackTraceElement.methodName.replaceAfter("lambda", ""))
            append("\n")
        }
    }

    /**
     * Check the tracker behavior used in [testData] when a test failure occurs.
     *
     * @param testData the [TestData] whose [TestData.entryPointCallerTracker] is tested.
     * @param expectedDump the expected result of calling [Throwable.dump] on the exception that is
     *   thrown.
     */
    private fun checkTrackerBehavior(
        testData: TestData,
        expectedDump: String,
        failure: () -> Unit = { fail("Fail") },
    ) {
        val tracker = testData.entryPointCallerTracker
        val exception = assertThrows(Throwable::class.java) { tracker.runTest(failure) }

        assertEquals(expectedDump.trimIndent(), exception.dump().trimEnd())
    }

    @Test
    fun `Test cannot be both entry and exit point`() {
        // Verify that `ExitPoint` and `EntryPoint` cannot both be provided.
        val testData = testInfraCannotBeBothExitAndEntryPoint()
        val tracker = testData.entryPointCallerTracker
        val exception =
            assertThrows(IllegalStateException::class.java) { tracker.runTest { fail("Fail") } }

        assertEquals(
            "public final com.android.tools.metalava.testing.EntryPointCallerTrackerTest${'$'}TestData com.android.tools.metalava.testing.EntryPointCallerTrackerTest.testInfraCannotBeBothExitAndEntryPoint() has both @EntryPoint and @ExitPoint, pick one",
            exception.message
        )
    }

    @Test
    fun `Test does nothing if no exceptions thrown`() {
        val testData = testInfraCannotBeBothExitAndEntryPoint()
        val tracker = testData.entryPointCallerTracker
        tracker.run { // Works ok.
        }
    }

    @Test
    fun `Test does nothing if no init cause`() {
        // Verify that if the AssertionError has a cause that the tracker does not change its stack
        // trace.
        checkTrackerBehavior(
            TestData("no entry point"),
            """
                java.lang.AssertionError: Fail
                  at com.android.tools.metalava.testing.EntryPointCallerTrackerTest.Test_does_nothing_if_no_init_cause${'$'}lambda
                  at com.android.tools.metalava.testing.EntryPointCallerTracker.runTest
                  at com.android.tools.metalava.testing.EntryPointCallerTrackerTest.checkTrackerBehavior${'$'}lambda
                  at org.junit.Assert.assertThrows
                  at org.junit.Assert.assertThrows
                  at com.android.tools.metalava.testing.EntryPointCallerTrackerTest.checkTrackerBehavior
                  at com.android.tools.metalava.testing.EntryPointCallerTrackerTest.Test does nothing if no init cause
            """
        ) {
            val cause = Exception("Cause")
            throw AssertionError("Fail", cause)
        }
    }

    @Test
    fun `Test no entry point`() {
        // Verify that `EntryPointCallerTracker.<init>` is treated as an entry point and removed
        // from the stack trace.
        checkTrackerBehavior(
            TestData("no entry point"),
            """
                java.lang.AssertionError: Fail
                  at com.android.tools.metalava.testing.EntryPointCallerTrackerTest${'$'}TestData.<init>
                  at com.android.tools.metalava.testing.EntryPointCallerTrackerTest.Test no entry point
            """
        )
    }

    @Test
    fun `Test not an assertion error`() {
        // Verify that `EntryPointCallerTracker.<init>` is treated as an entry point and removed
        // from the stack trace.
        checkTrackerBehavior(
            TestData("no entry point"),
            """
                java.io.IOException: I/O exception
                  at com.android.tools.metalava.testing.EntryPointCallerTrackerTest${'$'}TestData.<init>
                  at com.android.tools.metalava.testing.EntryPointCallerTrackerTest.Test not an assertion error
            """
        ) {
            throw IOException("I/O exception")
        }
    }

    @Test
    fun `Test simple entry point`() {
        // Verify that `testInfraSimpleEntryPoint()` is treated as the entry point and the top of
        // the stack trace refers to this method's call to it.
        checkTrackerBehavior(
            testInfraSimpleEntryPoint(),
            """
                java.lang.AssertionError: Fail
                  at com.android.tools.metalava.testing.EntryPointCallerTrackerTest.Test simple entry point
            """
        )
    }

    @Test
    fun `Test re-entrant entry point`() {
        // Verify that `testInfraReEntrantEntryPoint()` is treated as the entry point and the top of
        // the stack trace refers to this method's call to it and not the call that it makes to
        // `testInfraSimpleEntryPoint()`.
        checkTrackerBehavior(
            testInfraReEntrantEntryPoint(),
            """
                java.lang.AssertionError: Fail
                  at com.android.tools.metalava.testing.EntryPointCallerTrackerTest.Test re-entrant entry point
            """
        )
    }

    @Test
    fun `Test exit then re-enter entry point`() {
        // Verify that the call to `testInfraSimpleEntryPoint()` from the lambda supplied to
        // `testInfraExitThenReEnterEntryPoint()` is treated as the entry point and the top of the
        // stack trace refers to the lambda's call to it and not this method's call to
        // `testInfraExitThenReEnterEntryPoint()`.
        checkTrackerBehavior(
            testInfraExitThenReEnterEntryPoint { testInfraSimpleEntryPoint() },
            """
                java.lang.AssertionError: Fail
                  at com.android.tools.metalava.testing.EntryPointCallerTrackerTest.Test_exit_then_re_enter_entry_point${'$'}lambda
                  at com.android.tools.metalava.testing.EntryPointCallerTrackerTest.testInfraExitPoint
                  at com.android.tools.metalava.testing.EntryPointCallerTrackerTest.testInfraExitThenReEnterEntryPoint
                  at com.android.tools.metalava.testing.EntryPointCallerTrackerTest.Test exit then re-enter entry point
            """
        )
    }
}
