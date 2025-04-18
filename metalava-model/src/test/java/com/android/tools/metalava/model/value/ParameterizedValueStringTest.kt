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

import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.testing.arrayTypeItem
import com.android.tools.metalava.model.testing.primitiveTypeForKind
import com.android.tools.metalava.model.testing.stringType
import com.android.tools.metalava.model.testing.value.arrayValue
import com.android.tools.metalava.model.testing.value.arrayValueFromAny
import com.android.tools.metalava.model.testing.value.classObjectValue
import com.android.tools.metalava.model.testing.value.constantFieldValue
import com.android.tools.metalava.model.testing.value.enumConstantValue
import com.android.tools.metalava.model.testing.value.literalValue
import com.android.tools.metalava.model.testing.value.primitiveValueForKind
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
        private val expectedValueString: String,

        /**
         * The expected value returned from [Value.toString] when passed the
         * [ValueStringConfiguration] from [config].
         */
        private val expectedDebugString: String,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString() = name

        /** Run the test. */
        fun runTest() {
            assertEquals(expectedValueString, value.toValueString(config.valueStringConfiguration))
        }

        /** Run the debug test. */
        fun runDebugTest() {
            @Suppress("DEPRECATION") assertEquals(expectedDebugString, value.debugStringForValue())
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

            val TREAT_AS_INT =
                LabelledConfig(
                    "treat-as-int",
                    ValueStringConfiguration(
                        treatAsIntIfOriginallySpecifiedAsInt = true,
                    )
                )

            val TREAT_AS_INT_UNWRAP_SINGLE_ARRAY_ELEMENT =
                LabelledConfig(
                    "treat-as-int/unwrap",
                    ValueStringConfiguration(
                        treatAsIntIfOriginallySpecifiedAsInt = true,
                        unwrapSingleArrayElement = true,
                    ),
                )

            val UNWRAP_SINGLE_ARRAY_ELEMENT =
                LabelledConfig("unwrap", ValueStringConfiguration(unwrapSingleArrayElement = true))
        }
    }

    companion object {
        /**
         * Create a [TestCase] for [value] with the [expectedDefaultValueString] and optionally
         * invoke [body] to create additional [TestCase]s for the same [Value].
         *
         * @param expectedDefaultValueString The expected value returned from [Value.toValueString],
         *   i.e. with [ValueStringConfiguration.DEFAULT].
         * @param expectedDefaultDebugString The expected value returned from
         *   [Value.debugStringForValue].
         */
        @EntryPoint
        internal fun testCasesForValue(
            valueLabel: String? = null,
            value: Value,
            expectedDefaultValueString: String,
            expectedDefaultDebugString: String = expectedDefaultValueString,
            body: (TestCaseBuilder.() -> Unit)? = null,
        ) = buildList {
            TestCaseBuilder(
                    this,
                    valueLabel,
                    value,
                    expectedDefaultValueString,
                    expectedDefaultDebugString,
                )
                .let { builder ->
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
         * @param expectedDefaultValueString The expected value returned from
         *   `Value.toValueString()`, i.e. with [ValueStringConfiguration.DEFAULT].
         */
        internal class TestCaseBuilder(
            private val testCases: MutableList<TestCase>,
            valueLabel: String? = null,
            private val value: Value,
            private val expectedDefaultValueString: String,
            private val expectedDefaultDebugString: String,
        ) {
            private val prefix = "${value.kind},${valueLabel ?: value.toValueString()}"

            private fun addTestCase(
                config: LabelledConfig,
                expectedValueString: String,
                expectedDebugString: String,
            ) {
                testCases.add(
                    TestCase(
                        "$prefix,${config.label}",
                        value,
                        config,
                        expectedValueString,
                        expectedDebugString,
                    )
                )
            }

            /**
             * Add a [TestCase] that verifies that passing [LabelledConfig.valueStringConfiguration]
             * to [Value.toValueString] results in the same value as if the
             * [ValueStringConfiguration.DEFAULT] was used, i.e. [expectedDefaultValueString].
             */
            @EntryPoint
            fun verifyConfigMatchesDefault(config: LabelledConfig) {
                addTestCase(
                    config,
                    expectedDefaultValueString,
                    expectedDefaultDebugString,
                )
            }

            /**
             * Add a [TestCase] that verifies that passing [LabelledConfig.valueStringConfiguration]
             * to [Value.toValueString] results in [expectedValueString].
             */
            @EntryPoint
            fun verifyConfigChangesOutput(config: LabelledConfig, expectedValueString: String) {
                // Changing the configuration should not affect the debug string.
                addTestCase(config, expectedValueString, expectedDefaultDebugString)
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
                    expectedDefaultValueString = "{}",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.UNWRAP_SINGLE_ARRAY_ELEMENT)
                },
                testCasesForValue(
                    valueLabel = "single integer",
                    value = arrayValueFromAny(1),
                    expectedDefaultValueString = "{1}",
                    expectedDefaultDebugString = "{DefaultIntValue(1)}",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.UNWRAP_SINGLE_ARRAY_ELEMENT,
                        expectedValueString = "1",
                    )
                },
                testCasesForValue(
                    valueLabel = "single string",
                    value = arrayValueFromAny("single"),
                    expectedDefaultValueString = "{\"single\"}",
                    expectedDefaultDebugString = "{DefaultStringValue(\"single\")}",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.UNWRAP_SINGLE_ARRAY_ELEMENT,
                        expectedValueString = "\"single\"",
                    )
                },
                testCasesForValue(
                    valueLabel = "integers",
                    value = arrayValueFromAny(1, 2, 3),
                    expectedDefaultValueString = "{1, 2, 3}",
                    expectedDefaultDebugString =
                        "{DefaultIntValue(1), DefaultIntValue(2), DefaultIntValue(3)}",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.UNWRAP_SINGLE_ARRAY_ELEMENT)
                },
                testCasesForValue(
                    valueLabel = "strings",
                    value = arrayValueFromAny("first", "second", "third"),
                    expectedDefaultValueString = "{\"first\", \"second\", \"third\"}",
                    expectedDefaultDebugString =
                        "{DefaultStringValue(\"first\"), DefaultStringValue(\"second\"), DefaultStringValue(\"third\")}",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.UNWRAP_SINGLE_ARRAY_ELEMENT)
                },
                testCasesForValue(
                    valueLabel = "array of long as int",
                    value = arrayValue(primitiveValueForKind(Primitive.LONG, 3)),
                    expectedDefaultValueString = "{3L}",
                    expectedDefaultDebugString = "{DefaultLongValue(3L,asInt)}",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT_UNWRAP_SINGLE_ARRAY_ELEMENT,
                        expectedValueString = "3",
                    )
                },
                // ********************************* Booleans *********************************
                testCasesForValue(
                    value = literalValue(true),
                    expectedDefaultValueString = "true",
                ),
                testCasesForValue(
                    value = literalValue(false),
                    expectedDefaultValueString = "false",
                ),
                // ********************************* Bytes *********************************
                testCasesForValue(
                    value = literalValue(0.toByte()),
                    expectedDefaultValueString = "0",
                ),
                testCasesForValue(
                    value = literalValue(Byte.MAX_VALUE),
                    expectedDefaultValueString = "127",
                ),
                testCasesForValue(
                    value = literalValue(Byte.MIN_VALUE),
                    expectedDefaultValueString = "-128",
                ),
                // ********************************* Chars *********************************
                testCasesForValue(
                    value = literalValue('a'),
                    expectedDefaultValueString = "'a'",
                ),
                testCasesForValue(
                    value = literalValue('\t'),
                    expectedDefaultValueString = "'\\t'",
                ),
                testCasesForValue(
                    value = literalValue('\n'),
                    expectedDefaultValueString = "'\\n'",
                ),
                testCasesForValue(
                    value = literalValue('\u1245'),
                    expectedDefaultValueString = "'\\u1245'",
                ),
                // ********************************* Classes *********************************
                testCasesForValue(
                    value = classObjectValue(primitiveTypeForKind(Primitive.VOID)),
                    expectedDefaultValueString = "void.class",
                ),
                testCasesForValue(
                    value = classObjectValue(primitiveTypeForKind(Primitive.INT)),
                    expectedDefaultValueString = "int.class",
                ),
                testCasesForValue(
                    value = classObjectValue(stringType()),
                    expectedDefaultValueString = "java.lang.String.class",
                ),
                testCasesForValue(
                    value = classObjectValue(arrayTypeItem(primitiveTypeForKind(Primitive.INT))),
                    expectedDefaultValueString = "int[].class",
                ),
                testCasesForValue(
                    value = classObjectValue(arrayTypeItem(arrayTypeItem(stringType()))),
                    expectedDefaultValueString = "java.lang.String[][].class",
                ),
                // ****************************** Constant Fields ******************************
                testCasesForValue(
                    value = constantFieldValue("test.pkg.AClass", "FIELD"),
                    expectedDefaultValueString = "test.pkg.AClass.FIELD",
                ),
                testCasesForValue(
                    value = constantFieldValue("test.pkg.AClass", "FIELD", literalValue(2)),
                    expectedDefaultValueString = "test.pkg.AClass.FIELD",
                ),
                // ********************************* Doubles *********************************
                testCasesForValue(
                    value = literalValue(0.0),
                    expectedDefaultValueString = "0.0",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.TREAT_AS_INT)
                },
                testCasesForValue(
                    value = literalValue(Double.MAX_VALUE),
                    expectedDefaultValueString = "1.7976931348623157E308",
                ),
                testCasesForValue(
                    value = literalValue(Double.MIN_VALUE),
                    expectedDefaultValueString = "4.9E-324",
                ),
                testCasesForValue(
                    value = literalValue(Double.NaN),
                    expectedDefaultValueString = "(0.0/0.0)",
                ),
                testCasesForValue(
                    value = literalValue(Double.NEGATIVE_INFINITY),
                    expectedDefaultValueString = "(-1.0/0.0)",
                ),
                testCasesForValue(
                    value = literalValue(Double.POSITIVE_INFINITY),
                    expectedDefaultValueString = "(1.0/0.0)",
                ),
                testCasesForValue(
                    "double as int",
                    value = primitiveValueForKind(Primitive.DOUBLE, 3),
                    expectedDefaultValueString = "3.0",
                    expectedDefaultDebugString = "3.0,asInt",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT,
                        expectedValueString = "3",
                    )
                },
                // ********************************* Enum *********************************
                testCasesForValue(
                    value = enumConstantValue("test.pkg.EnumClass", "VALUE1"),
                    expectedDefaultValueString = "test.pkg.EnumClass.VALUE1",
                ),
                // ********************************* Floats *********************************
                testCasesForValue(
                    value = literalValue(0.0f),
                    expectedDefaultValueString = "0.0f",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.TREAT_AS_INT)
                },
                testCasesForValue(
                    value = literalValue(Float.MAX_VALUE),
                    expectedDefaultValueString = "3.4028235E38f",
                ),
                testCasesForValue(
                    value = literalValue(Float.MIN_VALUE),
                    expectedDefaultValueString = "1.4E-45f",
                ),
                testCasesForValue(
                    value = literalValue(Float.NaN),
                    expectedDefaultValueString = "(0.0f/0.0f)",
                ),
                testCasesForValue(
                    value = literalValue(Float.NEGATIVE_INFINITY),
                    expectedDefaultValueString = "(-1.0f/0.0f)",
                ),
                testCasesForValue(
                    value = literalValue(Float.POSITIVE_INFINITY),
                    expectedDefaultValueString = "(1.0f/0.0f)",
                ),
                testCasesForValue(
                    "float as int",
                    value = primitiveValueForKind(Primitive.FLOAT, 3),
                    expectedDefaultValueString = "3.0f",
                    expectedDefaultDebugString = "3.0f,asInt",
                ) {
                    verifyConfigChangesOutput(LabelledConfig.TREAT_AS_INT, "3")
                },
                // ********************************* Ints *********************************
                testCasesForValue(
                    value = literalValue(0),
                    expectedDefaultValueString = "0",
                ),
                testCasesForValue(
                    value = literalValue(Int.MAX_VALUE),
                    expectedDefaultValueString = "2147483647",
                ),
                testCasesForValue(
                    value = literalValue(Int.MIN_VALUE),
                    expectedDefaultValueString = "-2147483648",
                ),
                // ********************************* Longs *********************************
                testCasesForValue(
                    value = literalValue(0L),
                    expectedDefaultValueString = "0L",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.TREAT_AS_INT)
                },
                testCasesForValue(
                    value = literalValue(Long.MAX_VALUE),
                    expectedDefaultValueString = "9223372036854775807L",
                ),
                testCasesForValue(
                    value = literalValue(Long.MIN_VALUE),
                    expectedDefaultValueString = "-9223372036854775808L",
                ),
                testCasesForValue(
                    "long as int",
                    value = primitiveValueForKind(Primitive.LONG, 3),
                    expectedDefaultValueString = "3L",
                    expectedDefaultDebugString = "3L,asInt",
                ) {
                    verifyConfigChangesOutput(LabelledConfig.TREAT_AS_INT, "3")
                },
                // ********************************* Shorts *********************************
                testCasesForValue(
                    value = literalValue(0.toShort()),
                    expectedDefaultValueString = "0",
                ),
                testCasesForValue(
                    value = literalValue(Short.MAX_VALUE),
                    expectedDefaultValueString = "32767",
                ),
                testCasesForValue(
                    value = literalValue(Short.MIN_VALUE),
                    expectedDefaultValueString = "-32768",
                ),
                // ********************************* Strings *********************************
                testCasesForValue(
                    value = literalValue("string"),
                    expectedDefaultValueString = "\"string\"",
                ),
                testCasesForValue(
                    value = literalValue("str\ting\n"),
                    expectedDefaultValueString = "\"str\\ting\\n\"",
                ),
                testCasesForValue(
                    value = literalValue("str\u89EFing"),
                    expectedDefaultValueString = "\"str\\u89efing\"",
                ),
            )

        /** Supply the list of creation tests as the parameters for this test class. */
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = testCases.flatten()
    }

    @Test
    fun `toValueString test`() {
        testCase.runTest()
    }

    @Test
    fun `toString test`() {
        testCase.runDebugTest()
    }
}
