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
import com.android.tools.metalava.model.testing.value.annotationValue
import com.android.tools.metalava.model.testing.value.annotationValueFromSource
import com.android.tools.metalava.model.testing.value.arrayValue
import com.android.tools.metalava.model.testing.value.arrayValueFromAny
import com.android.tools.metalava.model.testing.value.classObjectValue
import com.android.tools.metalava.model.testing.value.fieldReferenceValue
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

            val ANNOTATION_ATTRIBUTE_NAME_VALUE_WITHOUT_SPACES =
                LabelledConfig(
                    "annotation-attribute-name-value-without-spaces",
                    ValueStringConfiguration(
                        annotationAttributeNameValueSeparator =
                            AnnotationAttributeNameValueSeparator.WITHOUT_SPACES,
                    )
                )

            val ANNOTATION_CLASS_RENAMER =
                LabelledConfig(
                    "annotation-class-renamer",
                    ValueStringConfiguration(
                        annotationQualifiedNameGetter = { annotationItem ->
                            annotationItem.qualifiedName.replace(
                                "android.annotation.",
                                "androidx.annotation."
                            )
                        }
                    )
                )

            val CLASS_OBJECT_VALUE_SOURCE =
                LabelledConfig(
                    "class-object-value-as-source",
                    ValueStringConfiguration(classObjectValueFormat = ClassObjectValueFormat.SOURCE)
                )

            val NON_LITERAL_FLOAT_SUFFIX =
                LabelledConfig(
                    "non-literal-float-suffix",
                    ValueStringConfiguration(nonLiteralFloatSuffix = 'F')
                )

            val NON_LITERAL_INT_HEX =
                LabelledConfig(
                    "non-literal-int-hex",
                    ValueStringConfiguration(nonLiteralIntFormat = IntFormat.HEXADECIMAL)
                )

            val SPECIAL_VALUES =
                LabelledConfig(
                    "special-values",
                    ValueStringConfiguration(
                        specialValues =
                            mapOf(
                                DoubleValue.NaN to "DoubleValue.NaN",
                                DoubleValue.NEGATIVE_INFINITY to "DoubleValue.NEGATIVE_INFINITY",
                                DoubleValue.POSITIVE_INFINITY to "DoubleValue.POSITIVE_INFINITY",
                                FloatValue.NaN to "FloatValue.NaN",
                                FloatValue.NEGATIVE_INFINITY to "FloatValue.NEGATIVE_INFINITY",
                                FloatValue.POSITIVE_INFINITY to "FloatValue.POSITIVE_INFINITY",
                                IntValue.MIN_VALUE to "Integer.MIN_VALUE",
                                IntValue.MAX_VALUE to "Integer.MAX_VALUE",
                            )
                    ),
                )

            val TREAT_AS_INT =
                LabelledConfig(
                    "treat-as-int",
                    ValueStringConfiguration(
                        treatAsIntIfOriginallySpecifiedAsInt = true,
                    )
                )

            val TREAT_AS_INT_AND_NON_LITERAL_INT_HEX =
                LabelledConfig(
                    "treat-as-int/non-literal-int-hex",
                    ValueStringConfiguration(
                        nonLiteralIntFormat = IntFormat.HEXADECIMAL,
                        treatAsIntIfOriginallySpecifiedAsInt = true,
                    )
                )

            val TREAT_AS_INT_UNWRAP_SINGLE_ARRAY_ELEMENT =
                LabelledConfig(
                    "treat-as-int/unwrap",
                    ValueStringConfiguration(
                        treatAsIntIfOriginallySpecifiedAsInt = true,
                        singleArrayElementFormat = SingleArrayElementFormat.UNWRAP,
                    ),
                )

            val UNSORTED_ATTRIBUTES =
                LabelledConfig(
                    "unsorted",
                    ValueStringConfiguration(sortAnnotationAttributes = false)
                )

            val UNWRAP_SINGLE_ARRAY_ELEMENT =
                LabelledConfig(
                    "unwrap",
                    ValueStringConfiguration(
                        singleArrayElementFormat = SingleArrayElementFormat.UNWRAP,
                    )
                )

            val VALUE_LANGUAGE_KOTLIN =
                LabelledConfig(
                    "value-language-kotlin",
                    ValueStringConfiguration(valueLanguage = ValueLanguage.KOTLIN)
                )
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
                // ****************************** Annotations ******************************
                testCasesForValue(
                    value = annotationValueFromSource("@test.pkg.Anno"),
                    expectedDefaultValueString = "@test.pkg.Anno",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.VALUE_LANGUAGE_KOTLIN,
                        "test.pkg.Anno()"
                    )

                    verifyConfigMatchesDefault(
                        LabelledConfig.ANNOTATION_ATTRIBUTE_NAME_VALUE_WITHOUT_SPACES
                    )

                    // Renaming android.annotation. to androidx.annotation does not affect this
                    // annotation.
                    verifyConfigMatchesDefault(LabelledConfig.ANNOTATION_CLASS_RENAMER)
                },
                testCasesForValue(
                    value = annotationValueFromSource("@test.pkg.Anno(intValue = 1)"),
                    expectedDefaultValueString = "@test.pkg.Anno(intValue = 1)",
                    expectedDefaultDebugString = "@test.pkg.Anno(intValue = IntValue(1))",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.VALUE_LANGUAGE_KOTLIN,
                        "test.pkg.Anno(intValue = 1)"
                    )

                    verifyConfigChangesOutput(
                        LabelledConfig.ANNOTATION_ATTRIBUTE_NAME_VALUE_WITHOUT_SPACES,
                        "@test.pkg.Anno(intValue=1)"
                    )
                },
                testCasesForValue(
                    // An annotation with an explicit value attribute
                    value = annotationValueFromSource("@test.pkg.Anno(value = 1)"),
                    expectedDefaultValueString = "@test.pkg.Anno(1)",
                    expectedDefaultDebugString = "@test.pkg.Anno(IntValue(1))",
                ) {
                    verifyConfigMatchesDefault(
                        LabelledConfig.ANNOTATION_ATTRIBUTE_NAME_VALUE_WITHOUT_SPACES
                    )
                },
                testCasesForValue(
                    // An annotation with an implicit value attribute
                    value = annotationValueFromSource("@test.pkg.Anno(1)"),
                    expectedDefaultValueString = "@test.pkg.Anno(1)",
                    expectedDefaultDebugString = "@test.pkg.Anno(IntValue(1))",
                ) {
                    verifyConfigMatchesDefault(
                        LabelledConfig.ANNOTATION_ATTRIBUTE_NAME_VALUE_WITHOUT_SPACES
                    )
                },
                testCasesForValue(
                    // An annotation with an explicit value attribute and another attribute.
                    value = annotationValueFromSource("@test.pkg.Anno(value = 1, intValue = 3)"),
                    expectedDefaultValueString = "@test.pkg.Anno(intValue = 3, value = 1)",
                    expectedDefaultDebugString =
                        "@test.pkg.Anno(intValue = IntValue(3), value = IntValue(1))",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.UNSORTED_ATTRIBUTES,
                        "@test.pkg.Anno(value = 1, intValue = 3)"
                    )

                    verifyConfigChangesOutput(
                        LabelledConfig.ANNOTATION_ATTRIBUTE_NAME_VALUE_WITHOUT_SPACES,
                        "@test.pkg.Anno(intValue=3, value=1)"
                    )
                },
                testCasesForValue(
                    // Create an annotation with attribute in non-alphabetical order.
                    value =
                        annotationValueFromSource("@test.pkg.Anno(longValue = 1L, intValue = 1)"),
                    expectedDefaultValueString = "@test.pkg.Anno(intValue = 1, longValue = 1L)",
                    expectedDefaultDebugString =
                        "@test.pkg.Anno(intValue = IntValue(1), longValue = LongValue(1L))",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.UNSORTED_ATTRIBUTES,
                        "@test.pkg.Anno(longValue = 1L, intValue = 1)"
                    )
                },
                testCasesForValue(
                    value =
                        annotationValue(
                            "test.pkg.Anno",
                            "nested" to annotationValueFromSource("@other.pkg.OtherAnno")
                        ),
                    expectedDefaultValueString = "@test.pkg.Anno(nested = @other.pkg.OtherAnno)",
                    expectedDefaultDebugString =
                        "@test.pkg.Anno(nested = AnnotationValue(@other.pkg.OtherAnno))",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.VALUE_LANGUAGE_KOTLIN,
                        "test.pkg.Anno(nested = other.pkg.OtherAnno())"
                    )
                },
                testCasesForValue(
                    value =
                        annotationValue(
                            "android.annotation.Test",
                            "nested" to annotationValue("android.annotation.Nested"),
                        ),
                    expectedDefaultValueString =
                        "@android.annotation.Test(nested = @android.annotation.Nested)",
                    expectedDefaultDebugString =
                        "@android.annotation.Test(nested = AnnotationValue(@android.annotation.Nested))",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.ANNOTATION_CLASS_RENAMER,
                        "@androidx.annotation.Test(nested = @androidx.annotation.Nested)"
                    )
                },
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
                    expectedDefaultDebugString = "{IntValue(1)}",
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
                    expectedDefaultDebugString = "{StringValue(\"single\")}",
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
                    expectedDefaultDebugString = "{IntValue(1), IntValue(2), IntValue(3)}",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.UNWRAP_SINGLE_ARRAY_ELEMENT)
                },
                testCasesForValue(
                    valueLabel = "strings",
                    value = arrayValueFromAny("first", "second", "third"),
                    expectedDefaultValueString = "{\"first\", \"second\", \"third\"}",
                    expectedDefaultDebugString =
                        "{StringValue(\"first\"), StringValue(\"second\"), StringValue(\"third\")}",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.UNWRAP_SINGLE_ARRAY_ELEMENT)
                },
                testCasesForValue(
                    valueLabel = "array of long as int",
                    value = arrayValue(primitiveValueForKind(Primitive.LONG, 3)),
                    expectedDefaultValueString = "{3L}",
                    expectedDefaultDebugString = "{LongValue(3L,asInt)}",
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
                    expectedDefaultDebugString = "-128,nonLiteral",
                ),
                testCasesForValue(
                    "byte as int",
                    value = primitiveValueForKind(Primitive.BYTE, 10),
                    expectedDefaultValueString = "10",
                    expectedDefaultDebugString = "10,asInt",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.TREAT_AS_INT)

                    // Even when treating this as an int it should not be formatted using
                    // hexadecimal because it was not a non-literal.
                    verifyConfigMatchesDefault(LabelledConfig.TREAT_AS_INT_AND_NON_LITERAL_INT_HEX)
                },
                testCasesForValue(
                    "byte as non-literal int",
                    value = primitiveValueForKind(Primitive.BYTE, -20),
                    expectedDefaultValueString = "-20",
                    expectedDefaultDebugString = "-20,asInt,nonLiteral",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.TREAT_AS_INT)

                    // When treating this as an int it should be formatted using hexadecimal because
                    // it was a non-literal.
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT_AND_NON_LITERAL_INT_HEX,
                        expectedValueString = "0xffffffec"
                    )
                },
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
                    value =
                        classObjectValue(
                            arrayTypeItem(primitiveTypeForKind(Primitive.INT)),
                            sourceExpression = "IntArray::class"
                        ),
                    expectedDefaultValueString = "int[].class",
                ) {
                    // The value being tested has a source expression so this configuration will
                    // produce a different result than the default.
                    verifyConfigChangesOutput(
                        LabelledConfig.CLASS_OBJECT_VALUE_SOURCE,
                        "IntArray::class"
                    )
                },
                testCasesForValue(
                    value = classObjectValue(arrayTypeItem(arrayTypeItem(stringType()))),
                    expectedDefaultValueString = "java.lang.String[][].class",
                ) {
                    // The value being tested does not have a source expression so this
                    // configuration will not produce a different result than the default.
                    verifyConfigMatchesDefault(LabelledConfig.CLASS_OBJECT_VALUE_SOURCE)
                },
                // ****************************** Constant Fields ******************************
                testCasesForValue(
                    value = fieldReferenceValue("test.pkg.AClass", "FIELD"),
                    expectedDefaultValueString = "test.pkg.AClass.FIELD",
                ),
                testCasesForValue(
                    value = fieldReferenceValue("test.pkg.AClass", "FIELD", literalValue(2)),
                    expectedDefaultValueString = "test.pkg.AClass.FIELD",
                ),
                // ********************************* Doubles *********************************
                testCasesForValue(
                    value = literalValue(0.0),
                    expectedDefaultValueString = "0.0",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.SPECIAL_VALUES)

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
                    expectedDefaultDebugString = "(0.0/0.0),nonLiteral",
                ) {
                    verifyConfigChangesOutput(LabelledConfig.SPECIAL_VALUES, "DoubleValue.NaN")
                },
                testCasesForValue(
                    value = literalValue(Double.NEGATIVE_INFINITY),
                    expectedDefaultValueString = "(-1.0/0.0)",
                    expectedDefaultDebugString = "(-1.0/0.0),nonLiteral",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.SPECIAL_VALUES,
                        "DoubleValue.NEGATIVE_INFINITY"
                    )
                },
                testCasesForValue(
                    value = literalValue(Double.POSITIVE_INFINITY),
                    expectedDefaultValueString = "(1.0/0.0)",
                    expectedDefaultDebugString = "(1.0/0.0),nonLiteral",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.SPECIAL_VALUES,
                        "DoubleValue.POSITIVE_INFINITY"
                    )
                },
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

                    // Even when treating this as an int it should not be formatted using
                    // hexadecimal because it was not a non-literal.
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT_AND_NON_LITERAL_INT_HEX,
                        expectedValueString = "3"
                    )
                },
                testCasesForValue(
                    "double as non-literal int",
                    value =
                        primitiveValueForKind(
                            Primitive.DOUBLE,
                            -7,
                            nonLiteralInSource = true,
                        ),
                    expectedDefaultValueString = "-7.0",
                    expectedDefaultDebugString = "-7.0,asInt,nonLiteral",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT,
                        expectedValueString = "-7",
                    )

                    // When treating this as an int it should be formatted using hexadecimal because
                    // it was a non-literal.
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT_AND_NON_LITERAL_INT_HEX,
                        expectedValueString = "0xfffffff9"
                    )
                },
                // ********************************* Enum *********************************
                testCasesForValue(
                    value = fieldReferenceValue("test.pkg.EnumClass", "VALUE1"),
                    expectedDefaultValueString = "test.pkg.EnumClass.VALUE1",
                ),
                // ********************************* Floats *********************************
                testCasesForValue(
                    value = literalValue(0.0f),
                    expectedDefaultValueString = "0.0f",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.SPECIAL_VALUES)

                    verifyConfigMatchesDefault(LabelledConfig.TREAT_AS_INT)
                },
                testCasesForValue(
                    value = literalValue(Float.MAX_VALUE),
                    expectedDefaultValueString = "3.4028235E38f",
                ),
                testCasesForValue(
                    value = literalValue(Float.MIN_VALUE),
                    expectedDefaultValueString = "1.4E-45f",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.NON_LITERAL_FLOAT_SUFFIX)
                },
                testCasesForValue(
                    value = literalValue(-1.4f),
                    expectedDefaultValueString = "-1.4f",
                    expectedDefaultDebugString = "-1.4f,nonLiteral",
                ) {
                    verifyConfigChangesOutput(LabelledConfig.NON_LITERAL_FLOAT_SUFFIX, "-1.4F")
                },
                testCasesForValue(
                    value = literalValue(7.9f, nonLiteralInSource = true),
                    expectedDefaultValueString = "7.9f",
                    expectedDefaultDebugString = "7.9f,nonLiteral",
                ) {
                    verifyConfigChangesOutput(LabelledConfig.NON_LITERAL_FLOAT_SUFFIX, "7.9F")
                },
                testCasesForValue(
                    value = literalValue(Float.NaN),
                    expectedDefaultValueString = "(0.0f/0.0f)",
                    expectedDefaultDebugString = "(0.0f/0.0f),nonLiteral",
                ) {
                    verifyConfigChangesOutput(LabelledConfig.SPECIAL_VALUES, "FloatValue.NaN")
                },
                testCasesForValue(
                    value = literalValue(Float.NEGATIVE_INFINITY),
                    expectedDefaultValueString = "(-1.0f/0.0f)",
                    expectedDefaultDebugString = "(-1.0f/0.0f),nonLiteral",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.SPECIAL_VALUES,
                        "FloatValue.NEGATIVE_INFINITY"
                    )
                },
                testCasesForValue(
                    value = literalValue(Float.POSITIVE_INFINITY),
                    expectedDefaultValueString = "(1.0f/0.0f)",
                    expectedDefaultDebugString = "(1.0f/0.0f),nonLiteral",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.SPECIAL_VALUES,
                        "FloatValue.POSITIVE_INFINITY"
                    )
                },
                testCasesForValue(
                    "float as int",
                    value = primitiveValueForKind(Primitive.FLOAT, 3),
                    expectedDefaultValueString = "3.0f",
                    expectedDefaultDebugString = "3.0f,asInt",
                ) {
                    verifyConfigChangesOutput(LabelledConfig.TREAT_AS_INT, "3")

                    // Even when treating this as an int it should not be formatted using
                    // hexadecimal because it was not a non-literal.
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT_AND_NON_LITERAL_INT_HEX,
                        expectedValueString = "3"
                    )
                },
                testCasesForValue(
                    "float as non-literal int",
                    value =
                        primitiveValueForKind(
                            Primitive.FLOAT,
                            -7,
                            nonLiteralInSource = true,
                        ),
                    expectedDefaultValueString = "-7.0f",
                    expectedDefaultDebugString = "-7.0f,asInt,nonLiteral",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT,
                        expectedValueString = "-7",
                    )

                    // When treating this as an int it should be formatted using hexadecimal because
                    // it was a non-literal.
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT_AND_NON_LITERAL_INT_HEX,
                        expectedValueString = "0xfffffff9"
                    )
                },
                // ********************************* Ints *********************************
                testCasesForValue(
                    value = literalValue(0),
                    expectedDefaultValueString = "0",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.NON_LITERAL_INT_HEX)

                    verifyConfigMatchesDefault(LabelledConfig.SPECIAL_VALUES)
                },
                testCasesForValue(
                    value = IntValue.MAX_VALUE,
                    expectedDefaultValueString = "2147483647",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.NON_LITERAL_INT_HEX)

                    verifyConfigChangesOutput(LabelledConfig.SPECIAL_VALUES, "Integer.MAX_VALUE")
                },
                testCasesForValue(
                    value = IntValue.MIN_VALUE,
                    expectedDefaultValueString = "-2147483648",
                    expectedDefaultDebugString = "-2147483648,nonLiteral",
                ) {
                    // Negative ints are considered as being a unary minus expression of the
                    // absolute value.
                    verifyConfigChangesOutput(LabelledConfig.NON_LITERAL_INT_HEX, "0x80000000")

                    verifyConfigChangesOutput(LabelledConfig.SPECIAL_VALUES, "Integer.MIN_VALUE")
                },
                testCasesForValue(
                    value = literalValue(892536243, nonLiteralInSource = true),
                    expectedDefaultValueString = "892536243",
                    expectedDefaultDebugString = "892536243,nonLiteral",
                ) {
                    verifyConfigChangesOutput(LabelledConfig.NON_LITERAL_INT_HEX, "0x353305b3")
                },
                testCasesForValue(
                    value = primitiveValueForKind(Primitive.INT, 10L),
                    expectedDefaultValueString = "10",
                    expectedDefaultDebugString = "10,!asInt",
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
                    expectedDefaultDebugString = "-9223372036854775808L,nonLiteral",
                ),
                testCasesForValue(
                    "long as int",
                    value = primitiveValueForKind(Primitive.LONG, 3),
                    expectedDefaultValueString = "3L",
                    expectedDefaultDebugString = "3L,asInt",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT,
                        expectedValueString = "3",
                    )

                    // Even when treating this as an int it should not be formatted using
                    // hexadecimal because it was not a non-literal.
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT_AND_NON_LITERAL_INT_HEX,
                        expectedValueString = "3",
                    )
                },
                testCasesForValue(
                    "long as non-literal int",
                    value = primitiveValueForKind(Primitive.LONG, -37),
                    expectedDefaultValueString = "-37L",
                    expectedDefaultDebugString = "-37L,asInt,nonLiteral",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT,
                        expectedValueString = "-37",
                    )

                    // When treating this as an int it should be formatted using hexadecimal because
                    // it was a non-literal.
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT_AND_NON_LITERAL_INT_HEX,
                        expectedValueString = "0xffffffdb"
                    )
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
                    expectedDefaultDebugString = "-32768,nonLiteral",
                ),
                testCasesForValue(
                    "short as int",
                    value = primitiveValueForKind(Primitive.SHORT, 3),
                    expectedDefaultValueString = "3",
                    expectedDefaultDebugString = "3,asInt",
                ) {
                    verifyConfigMatchesDefault(LabelledConfig.TREAT_AS_INT)

                    // Even when treating this as an int it should not be formatted using
                    // hexadecimal because it was not a non-literal.
                    verifyConfigMatchesDefault(LabelledConfig.TREAT_AS_INT_AND_NON_LITERAL_INT_HEX)
                },
                testCasesForValue(
                    "short as non-literal int",
                    value = primitiveValueForKind(Primitive.SHORT, -111),
                    expectedDefaultValueString = "-111",
                    expectedDefaultDebugString = "-111,asInt,nonLiteral",
                ) {
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT,
                        expectedValueString = "-111",
                    )

                    // When treating this as an int it should be formatted using hexadecimal because
                    // it was a non-literal.
                    verifyConfigChangesOutput(
                        LabelledConfig.TREAT_AS_INT_AND_NON_LITERAL_INT_HEX,
                        expectedValueString = "0xffffff91"
                    )
                },
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
