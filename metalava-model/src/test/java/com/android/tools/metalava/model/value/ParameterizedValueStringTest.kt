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

package com.android.tools.metalava.model.value

import com.android.tools.metalava.model.testing.value.arrayValueFromAny
import com.android.tools.metalava.model.testing.value.literalValue
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.ExitPoint
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Tests for [Value.toValueString]. */
@RunWith(Parameterized::class)
class ParameterizedValueStringTest {
    @Parameterized.Parameter(0) lateinit var testCase: TestCase

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [testCase] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { testCase.entryPointCallerTracker }

    class TestCase(
        /** The name of the test. */
        private val name: String,

        /** The value to test. */
        private val value: Value,

        /**
         * The [LabelledConfig] whose [LabelledConfig.valueStringConfiguration] is passed to
         * [Value.toValueString].
         */
        private val config: LabelledConfig,

        /**
         * The expected value returned from [Value.toValueString] when passed the
         * [ValueStringConfiguration] from [config].
         */
        private val expectedString: String,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString() = name

        /** Run the test. */
        fun runTest() {
            assertEquals(expectedString, value.toValueString(config.valueStringConfiguration))
        }
    }

    /**
     * A wrapper around a [valueStringConfiguration] that adds a [label] for use in the
     * [TestCase.name].
     */
    class LabelledConfig(
        val label: String,
        val valueStringConfiguration: ValueStringConfiguration
    ) {
        companion object {
            val DEFAULT = LabelledConfig("default", ValueStringConfiguration.DEFAULT)

            val UNWRAP_SINGLE_ARRAY_ELEMENT =
                LabelledConfig("unwrap", ValueStringConfiguration(unwrapSingleArrayElement = true))
        }
    }

    companion object {
        /**
         * Create a [TestCase] for [value] with the [expectedDefaultString] and optionally invoke
         * [body] to create additional [TestCase]s for the same [Value].
         *
         * @param expectedDefaultString The expected value returned from `Value.toValueString()`,
         *   i.e. with [ValueStringConfiguration.DEFAULT].
         */
        @EntryPoint
        internal fun testCasesForValue(
            valueLabel: String? = null,
            value: Value,
            expectedDefaultString: String,
            body: (TestCaseBuilder.() -> Unit)? = null,
        ) = buildList {
            TestCaseBuilder(this, valueLabel, value, expectedDefaultString).let { builder ->
                builder.verifyConfigMatchesDefault(LabelledConfig.DEFAULT)
                if (body != null) {
                    builder.buildTestCases(body)
                }
            }
        }

        /**
         * Builder for [TestCase]s.
         *
         * @param testCases the list of [TestCase]s to update.
         * @param valueLabel the optional label to use if the [value]'s [Value.toValueString] is not
         *   helpful.
         * @param value the [Value] whose [Value.toValueString] is being tested.
         * @param expectedDefaultString The expected value returned from `Value.toValueString()`,
         *   i.e. with [ValueStringConfiguration.DEFAULT].
         */
        internal class TestCaseBuilder(
            private val testCases: MutableList<TestCase>,
            valueLabel: String? = null,
            private val value: Value,
            private val expectedDefaultString: String
        ) {
            private val prefix = "${value.kind},${valueLabel ?: value.toValueString()}"

            private fun addTestCase(config: LabelledConfig, expectedString: String) {
                testCases.add(TestCase("$prefix,${config.label}", value, config, expectedString))
            }

            /**
             * Add a [TestCase] that verifies that passing [LabelledConfig.valueStringConfiguration]
             * to [Value.toValueString] results in the same value as if the
             * [ValueStringConfiguration.DEFAULT] was used, i.e. [expectedDefaultString].
             */
            @EntryPoint
            fun verifyConfigMatchesDefault(config: LabelledConfig) {
                addTestCase(config, expectedDefaultString)
            }

            /**
             * Add a [TestCase] that verifies that passing [LabelledConfig.valueStringConfiguration]
             * to [Value.toValueString] results in [expectedString].
             */
            @EntryPoint
            fun verifyConfigChangesOutput(config: LabelledConfig, expectedString: String) {
                addTestCase(config, expectedString)
            }

            /** Separated out as required by [ExitPoint]. */
            @ExitPoint
            fun buildTestCases(body: TestCaseBuilder.() -> Unit) {
                body()
            }
        }

        private val testCases =
            listOf(
                // ********************************* Arrays *********************************
                testCasesForValue(
                    value = arrayValueFromAny(),
                    expectedDefaultString = "{}",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.UNWRAP_SINGLE_ARRAY_ELEMENT)
                },
                testCasesForValue(
                    valueLabel = "single integer",
                    value = arrayValueFromAny(1),
                    expectedDefaultString = "{1}",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.UNWRAP_SINGLE_ARRAY_ELEMENT,
                        expectedString = "1",
                    )
                },
                testCasesForValue(
                    valueLabel = "single string",
                    value = arrayValueFromAny("single"),
                    expectedDefaultString = "{\"single\"}",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.UNWRAP_SINGLE_ARRAY_ELEMENT,
                        expectedString = "\"single\"",
                    )
                },
                testCasesForValue(
                    valueLabel = "integers",
                    value = arrayValueFromAny(1, 2, 3),
                    expectedDefaultString = "{1, 2, 3}",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.UNWRAP_SINGLE_ARRAY_ELEMENT)
                },
                testCasesForValue(
                    valueLabel = "strings",
                    value = arrayValueFromAny("first", "second", "third"),
                    expectedDefaultString = "{\"first\", \"second\", \"third\"}",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.UNWRAP_SINGLE_ARRAY_ELEMENT)
                },
                // ********************************* Booleans *********************************
                testCasesForValue(
                    value = literalValue(true),
                    expectedDefaultString = "true",
                ),
                testCasesForValue(
                    value = literalValue(false),
                    expectedDefaultString = "false",
                ),
                // ********************************* Bytes *********************************
                testCasesForValue(
                    value = literalValue(0.toByte()),
                    expectedDefaultString = "0",
                ),
                testCasesForValue(
                    value = literalValue(Byte.MAX_VALUE),
                    expectedDefaultString = "127",
                ),
                testCasesForValue(
                    value = literalValue(Byte.MIN_VALUE),
                    expectedDefaultString = "-128",
                ),
                // ********************************* Chars *********************************
                testCasesForValue(
                    value = literalValue('a'),
                    expectedDefaultString = "'a'",
                ),
                testCasesForValue(
                    value = literalValue('\t'),
                    expectedDefaultString = "'\\t'",
                ),
                testCasesForValue(
                    value = literalValue('\n'),
                    expectedDefaultString = "'\\n'",
                ),
                testCasesForValue(
                    value = literalValue('\u1245'),
                    expectedDefaultString = "'\\u1245'",
                ),
                // ********************************* Doubles *********************************
                testCasesForValue(
                    value = literalValue(0.0),
                    expectedDefaultString = "0.0",
                ),
                testCasesForValue(
                    value = literalValue(Double.MAX_VALUE),
                    expectedDefaultString = "1.7976931348623157E308",
                ),
                testCasesForValue(
                    value = literalValue(Double.MIN_VALUE),
                    expectedDefaultString = "4.9E-324",
                ),
                testCasesForValue(
                    value = literalValue(Double.NaN),
                    expectedDefaultString = "NaN",
                ),
                testCasesForValue(
                    value = literalValue(Double.NEGATIVE_INFINITY),
                    expectedDefaultString = "-Infinity",
                ),
                testCasesForValue(
                    value = literalValue(Double.POSITIVE_INFINITY),
                    expectedDefaultString = "Infinity",
                ),
                // ********************************* Floats *********************************
                testCasesForValue(
                    value = literalValue(0.0f),
                    expectedDefaultString = "0.0f",
                ),
                testCasesForValue(
                    value = literalValue(Float.MAX_VALUE),
                    expectedDefaultString = "3.4028235E38f",
                ),
                testCasesForValue(
                    value = literalValue(Float.MIN_VALUE),
                    expectedDefaultString = "1.4E-45f",
                ),
                testCasesForValue(
                    value = literalValue(Float.NaN),
                    expectedDefaultString = "NaN",
                ),
                testCasesForValue(
                    value = literalValue(Float.NEGATIVE_INFINITY),
                    expectedDefaultString = "-Infinity",
                ),
                testCasesForValue(
                    value = literalValue(Float.POSITIVE_INFINITY),
                    expectedDefaultString = "Infinity",
                ),
                // ********************************* Ints *********************************
                testCasesForValue(
                    value = literalValue(0),
                    expectedDefaultString = "0",
                ),
                testCasesForValue(
                    value = literalValue(Int.MAX_VALUE),
                    expectedDefaultString = "2147483647",
                ),
                testCasesForValue(
                    value = literalValue(Int.MIN_VALUE),
                    expectedDefaultString = "-2147483648",
                ),
                // ********************************* Longs *********************************
                testCasesForValue(
                    value = literalValue(0L),
                    expectedDefaultString = "0L",
                ),
                testCasesForValue(
                    value = literalValue(Long.MAX_VALUE),
                    expectedDefaultString = "9223372036854775807L",
                ),
                testCasesForValue(
                    value = literalValue(Long.MIN_VALUE),
                    expectedDefaultString = "-9223372036854775808L",
                ),
                // ********************************* Shorts *********************************
                testCasesForValue(
                    value = literalValue(0.toShort()),
                    expectedDefaultString = "0",
                ),
                testCasesForValue(
                    value = literalValue(Short.MAX_VALUE),
                    expectedDefaultString = "32767",
                ),
                testCasesForValue(
                    value = literalValue(Short.MIN_VALUE),
                    expectedDefaultString = "-32768",
                ),
                // ********************************* Strings *********************************
                testCasesForValue(
                    value = literalValue("string"),
                    expectedDefaultString = "\"string\"",
                ),
                testCasesForValue(
                    value = literalValue("str\ting\n"),
                    expectedDefaultString = "\"str\\ting\\n\"",
                ),
                testCasesForValue(
                    value = literalValue("str\u89EFing"),
                    expectedDefaultString = "\"str\\u89efing\"",
                ),
            )

        /** Supply the list of creation tests as the parameters for this test class. */
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = testCases.flatten()
    }

    @Test
    fun `toValueString test`() {
        testCase.runTest()
    }
}
