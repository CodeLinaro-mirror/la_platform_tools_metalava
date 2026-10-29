/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.reporter.Issues.Issue
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParameterizedSplitNullabilitySuffixTest {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class TestParams
    @EntryPoint
    constructor(
        val type: String,
        val kotlinStyleNulls: Boolean,
        val expectedRemainder: String,
        val expectedNullability: TypeNullability?,
        val expectedIssues: String = "",
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString() = "${type}, kotlinStyleNulls=$kotlinStyleNulls"
    }

    companion object {
        private val params =
            listOf(
                // Kotlin style nulls.
                TestParams(
                    type = "String!",
                    kotlinStyleNulls = true,
                    expectedRemainder = "String",
                    expectedNullability = TypeNullability.PLATFORM,
                ),
                TestParams(
                    type = "String?",
                    kotlinStyleNulls = true,
                    expectedRemainder = "String",
                    expectedNullability = TypeNullability.NULLABLE,
                ),
                TestParams(
                    type = "String",
                    kotlinStyleNulls = true,
                    expectedRemainder = "String",
                    expectedNullability = null,
                ),
                TestParams(
                    type = "T",
                    kotlinStyleNulls = true,
                    expectedRemainder = "T",
                    expectedNullability = null,
                ),

                // Not Kotlin style nulls.
                TestParams(
                    type = "String",
                    kotlinStyleNulls = false,
                    expectedRemainder = "String",
                    expectedNullability = null,
                ),
                TestParams(
                    type = "String!",
                    kotlinStyleNulls = false,
                    expectedRemainder = "String",
                    expectedNullability = TypeNullability.PLATFORM,
                    expectedIssues =
                        "Format does not support Kotlin-style null type syntax: String! [TypeParseError]",
                ),
                TestParams(
                    type = "String?",
                    kotlinStyleNulls = false,
                    expectedRemainder = "String",
                    expectedNullability = TypeNullability.PLATFORM,
                    expectedIssues =
                        "Format does not support Kotlin-style null type syntax: String? [TypeParseError]"
                ),

                // Check that wildcards work with and without kotlin style nulls.
                TestParams(
                    type = "?",
                    kotlinStyleNulls = true,
                    expectedRemainder = "?",
                    expectedNullability = TypeNullability.UNDEFINED,
                ),
                TestParams(
                    type = "?",
                    kotlinStyleNulls = false,
                    expectedRemainder = "?",
                    expectedNullability = null,
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = params
    }

    @Test
    fun `test split nullability`() {
        val collatingErrorReporter = CollatingErrorReporter()
        val result =
            TypeItemParser.splitNullabilitySuffix(
                params.type,
                params.kotlinStyleNulls,
                collatingErrorReporter
            )

        assertEquals(params.expectedRemainder, result.first, message = "remainder")
        assertEquals(params.expectedNullability, result.second, message = "nullability")
        assertEquals(params.expectedIssues, collatingErrorReporter.toString())
    }
}

private class CollatingErrorReporter : TypeItemParserErrorReporter {
    private val list = mutableListOf<Report>()

    private data class Report(
        val issue: Issue,
        val message: String,
    )

    override fun report(issue: Issue, message: String) {
        list.add(Report(issue, message))
    }

    override fun toString(): String {
        list.sortWith(reportComparator)
        return list.joinToString("\n") { report -> "${report.message} [${report.issue.name}]" }
    }

    companion object {
        private val reportComparator =
            compareBy<Report>(
                { it.issue.name },
                { it.message },
            )
    }
}
