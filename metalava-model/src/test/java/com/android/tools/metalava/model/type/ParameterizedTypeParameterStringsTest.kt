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

import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParameterizedTypeParameterStringsTest {

    @Parameterized.Parameter(0) lateinit var parserProvider: TypeItemParserProvider<TestCase>

    @Parameterized.Parameter(1) lateinit var testCase: TestCase

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestCase] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { testCase.entryPointCallerTracker }

    data class TestCase
    @EntryPoint
    constructor(
        val typeString: String?,
        val expected: List<String>,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString() = typeString?.ifEmpty { "\"\"" } ?: "null"
    }

    companion object {
        private val basicCases =
            listOf(
                TestCase(
                    typeString = null,
                    expected = emptyList(),
                ),
                TestCase(
                    typeString = "",
                    expected = emptyList(),
                ),
                TestCase(
                    typeString = "<X>",
                    expected = listOf("X"),
                ),
                TestCase(
                    typeString = "<ABC,DEF extends T>",
                    expected = listOf("ABC", "DEF extends T"),
                ),
                TestCase(
                    typeString = "<T extends java.lang.Comparable<? super T>>",
                    expected = listOf("T extends java.lang.Comparable<? super T>"),
                ),
                TestCase(
                    typeString = "<java.util.List<java.lang.String>[]>",
                    expected = listOf("java.util.List<java.lang.String>[]"),
                ),
            )

        private val annotatedCases =
            listOf(
                TestCase(
                    typeString = "<java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer>",
                    expected =
                        listOf("java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer"),
                ),
                TestCase(
                    typeString = "<@test.pkg.C String>",
                    expected = listOf("@test.pkg.C String"),
                ),
                TestCase(
                    typeString =
                        "<java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer, @test.pkg.C String>",
                    expected =
                        listOf(
                            "java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer",
                            "@test.pkg.C String",
                        ),
                ),
            )

        private val allTestCases = basicCases + annotatedCases

        private val parserProviders =
            listOf(
                TypeItemParserProvider(
                    "Default",
                    ::DefaultTypeItemParser,
                    allTestCases,
                ),
            )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}: {1}")
        fun params() = parserProviders.toTestParameters()
    }

    @Test
    fun `Test typeParameterStrings`() {
        val typeParser = parserProvider.createParser()
        assertEquals(testCase.expected, typeParser.typeParameterStrings(testCase.typeString))
    }
}
