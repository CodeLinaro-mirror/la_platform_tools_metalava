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

package com.android.tools.metalava.model.testsuite.value

import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.testsuite.value.CommonParameterizedValueTest.CodebaseProducer

/**
 * Encapsulates information about a value example.
 *
 * This will be useful for a number of different tests around values.
 */
data class ValueExample(
    /**
     * The name of the example.
     *
     * This is used both in the type name and as the attribute name in an annotation class so it
     * must be unique across all [ValueExample]s.
     */
    val name: String,

    /** The java type. */
    val javaType: String,

    /** The java expression for the value. */
    val javaExpression: String,

    /** The optional java imports. */
    val javaImports: List<String> = emptyList(),

    /** The set of [ValueUseSite]s in which this example will be tested; defaults to all of them. */
    val suitableFor: Set<ValueUseSite> = allValueUseSites,

    /**
     * The legacy string representation of [javaExpression].
     *
     * This may differ by [ProducerKind] and [ValueUseSite].
     */
    val expectedLegacySource: Expectation<String>,

    /**
     * Controls which [ValueExample]s in [allValueExamples] are run.
     *
     * When all the [ValueExample]s have this set to `false` (the default) then they are all tests.
     * If any [ValueExample] has this set to `true` then only the ones with this set to `true` are
     * tested. Care must be taken to ensure that this is not set to `true` in uploaded changes.
     *
     * This is added to simplify adding a new [ValueExample] or working on an existing
     * [ValueExample] by limiting the ones which will be tested to save time and reduce the noise of
     * failing tests.
     */
    val testThis: Boolean = false,
) {
    /** The name of the annotation attribute that this example will use in any generated classes. */
    val attributeName = name.replace(" ", "_") + "_attr"

    /** The name of the field that this example will use in any generated classes. */
    val fieldName = attributeName.uppercase()

    companion object {
        /** A special value used for fields for whom [FieldItem.initialValue] returns `null`. */
        internal const val NO_INITIAL_FIELD_VALUE = "NO INITIAL FIELD VALUE"

        /**
         * The list of all [ValueExample]s that could be tested across [CodebaseProducer] and
         * [ValueUseSite]s.
         */
        private val allValueExamples =
            listOf(
                // Check an annotation literal.
                ValueExample(
                    name = "annotation",
                    javaType = "OtherAnnotation",
                    javaExpression = "@OtherAnnotation(intType = 1)",
                    expectedLegacySource =
                        expectations {
                            common = "@test.pkg.OtherAnnotation(intType = 1)"
                            source { common = "@OtherAnnotation(intType = 1)" }
                            // TODO(b/354633349): Missing attributes.
                            attributeDefaultValue = "@test.pkg.OtherAnnotation"
                        },
                    // Annotation literals cannot be used in fields.
                    suitableFor = allValueUseSitesExceptFields,
                ),
                // Check a simple boolean true value.
                ValueExample(
                    name = "boolean true",
                    javaType = "boolean",
                    javaExpression = "true",
                    expectedLegacySource = expectations { common = "true" },
                ),
                // Check a simple boolean false value.
                ValueExample(
                    name = "boolean false",
                    javaType = "boolean",
                    javaExpression = "false",
                    expectedLegacySource = expectations { common = "false" },
                ),
                // Check a simple byte.
                ValueExample(
                    name = "byte",
                    javaType = "byte",
                    javaExpression = "116",
                    expectedLegacySource = expectations { common = "116" },
                ),
                // Check a simple char.
                ValueExample(
                    name = "char",
                    javaType = "char",
                    javaExpression = "'x'",
                    expectedLegacySource =
                        expectations {
                            common = "'x'"
                            // TODO(b/354633349): Should have surrounding quotes.
                            fieldValue = "x"
                        },
                ),
                // Check a unicode char.
                ValueExample(
                    name = "char unicode",
                    javaType = "char",
                    javaExpression = "'\\u2912'",
                    expectedLegacySource =
                        expectations {
                            // TODO(b/354633349): Should probably use the `\uABCD` form.
                            common = "'⤒'"
                            // TODO(b/354633349): Should have surrounding quotes and use the
                            // `\uABCD` form.
                            fieldValue = "⤒"

                            // These are correct.
                            attributeDefaultValue = "'\\u2912'"
                            source { attributeValue = "'\\u2912'" }
                        },
                ),
                // Check a class literal.
                ValueExample(
                    name = "class",
                    javaType = "Class<?>",
                    javaExpression = "List.class",
                    javaImports = listOf("java.util.List"),
                    expectedLegacySource =
                        expectations {
                            common = "java.util.List.class"
                            source {
                                // TODO(b/354633349): Fully qualified is better.
                                common = "List.class"
                                attributeDefaultValue = "java.util.List.class"
                            }
                            // TODO(b/354633349): Try and make this consistent.
                            fieldValue = NO_INITIAL_FIELD_VALUE
                        },
                ),
                // Check a simple double.
                ValueExample(
                    name = "double",
                    javaType = "double",
                    javaExpression = "3.141",
                    expectedLegacySource = expectations { common = "3.141" },
                ),
                // Check a simple double with int
                ValueExample(
                    name = "double with int",
                    javaType = "double",
                    javaExpression = "3",
                    expectedLegacySource =
                        expectations {
                            // TODO(b/354633349): Consistency is good. It's not clear what the best
                            //  way of formatting this is. Add a trailing F to make it clear it is a
                            //  double when parsing the signature file even if the annotation
                            //  definition is not available or only add it when strictly necessary.
                            common = "3.0"

                            source {
                                // TODO(b/354633349): Consistency is good.
                                attributeDefaultValue = "3"
                                attributeValue = "3"
                            }
                        },
                ),
                // Check a special double - Nan.
                ValueExample(
                    name = "double NaN",
                    javaType = "double",
                    javaExpression = "Double.NaN",
                    expectedLegacySource =
                        expectations {
                            // TODO(b/354633349): Every single use has a different representation!?
                            //   Ideally, this should just `java.lang.Double.NaN` when that is how
                            //   it is referenced in the source and some expression like `(0.0/0.0)`
                            //   when it is defined like that, e.g. on `java.lang.Double.NaN`
                            //   itself.
                            source {
                                attributeDefaultValue = "java.lang.Double.NaN"
                                attributeValue = "Double.NaN"
                                fieldValue = "NaN"
                            }

                            jar {
                                attributeDefaultValue = "(0.0/0.0)"
                                attributeValue = "0.0d / 0.0"
                                fieldValue = NO_INITIAL_FIELD_VALUE
                            }
                        },
                ),
                // Check a special double - +infinity.
                ValueExample(
                    name = "double positive infinity",
                    javaType = "double",
                    javaExpression = "Double.POSITIVE_INFINITY",
                    expectedLegacySource =
                        expectations {
                            // TODO(b/354633349): Every single use has a different representation!?
                            //   Ideally, this should just `java.lang.Double.NaN` when that is how
                            //   it is referenced in the source and some expression like `(1.0/0.0)`
                            //   when it is defined like that, e.g. on
                            //   `java.lang.Double.POSITIVE_INFINITY` itself.
                            source {
                                attributeDefaultValue = "java.lang.Double.POSITIVE_INFINITY"
                                attributeValue = "Double.POSITIVE_INFINITY"
                                fieldValue = "Infinity"
                            }

                            jar {
                                attributeDefaultValue = "(1.0/0.0)"
                                attributeValue = "1.0 / 0.0"
                                fieldValue = NO_INITIAL_FIELD_VALUE
                            }
                        },
                ),
                ValueExample(
                    name = "double negative infinity",
                    javaType = "double",
                    javaExpression = "Double.NEGATIVE_INFINITY",
                    expectedLegacySource =
                        expectations {
                            // TODO(b/354633349): Every single use has a different representation!?
                            //   Ideally, this should just `java.lang.Double.NaN` when that is how
                            //   it is referenced in the source and some expression like `(1.0/0.0)`
                            //   when it is defined like that, e.g. on
                            //   `java.lang.Double.NEGATIVE_INFINITY` itself.
                            source {
                                attributeDefaultValue = "java.lang.Double.NEGATIVE_INFINITY"
                                attributeValue = "Double.NEGATIVE_INFINITY"
                                fieldValue = "-Infinity"
                            }

                            jar {
                                attributeDefaultValue = "(-1.0/0.0)"
                                attributeValue = "-1.0 / 0.0"
                                fieldValue = NO_INITIAL_FIELD_VALUE
                            }
                        },
                ),
                // Check an enum literal.
                ValueExample(
                    name = "enum",
                    javaType = "TestEnum",
                    javaExpression = "TestEnum.VALUE1",
                    expectedLegacySource =
                        expectations {
                            common = "test.pkg.TestEnum.VALUE1"
                            source {
                                // TODO(b/354633349): Fully qualified is better.
                                attributeValue = "TestEnum.VALUE1"
                            }
                            // TODO(b/354633349): Try and make this consistent.
                            fieldValue = NO_INITIAL_FIELD_VALUE
                        },
                ),
                // Check a simple float with int
                ValueExample(
                    name = "float with int",
                    javaType = "float",
                    javaExpression = "3",
                    expectedLegacySource =
                        expectations {
                            // TODO(b/354633349): Consistency is good. It's not clear what the best
                            //  way of formatting this is. Add a trailing F to make it clear it is a
                            //  float when parsing the signature file even if the annotation
                            //  definition is not available or only add it when strictly necessary.
                            common = "3.0F"

                            source {
                                // TODO(b/354633349): Consistency is good.
                                attributeDefaultValue = "3"
                                attributeValue = "3"
                            }

                            jar {
                                // TODO(b/354633349): Consistency is good.
                                common = "3.0f"
                            }

                            // TODO(b/354633349): Consistency is good.
                            fieldValue = "3.0"
                        },
                ),
                // Check a simple float with upper F.
                ValueExample(
                    name = "float with upper F",
                    javaType = "float",
                    javaExpression = "3.141F",
                    expectedLegacySource =
                        expectations {
                            common = "3.141F"

                            // TODO(b/354633349): Consistency is good.
                            attributeDefaultValue = "3.141f"

                            jar {
                                // TODO(b/354633349): Consistency is good.
                                common = "3.141f"
                            }

                            // TODO(b/354633349): Consistency is good.
                            fieldValue = "3.141"
                        },
                ),
                // Check a simple float with lower F.
                ValueExample(
                    name = "float with lower f",
                    javaType = "float",
                    javaExpression = "3.141f",
                    expectedLegacySource =
                        expectations {
                            common = "3.141f"

                            // TODO(b/354633349): Consistency is good.
                            fieldValue = "3.141"
                        },
                ),
                // Check a special float - Nan.
                ValueExample(
                    name = "float NaN",
                    javaType = "float",
                    javaExpression = "Float.NaN",
                    expectedLegacySource =
                        expectations {
                            // TODO(b/354633349): Every single use has a different representation!?
                            //   Ideally, this should just `java.lang.Float.NaN` when that is how it
                            //   is referenced in the source and some expression like `(0.0f/0.0f)`
                            //   when it is defined like that, e.g. on `java.lang.Float.NaN` itself.
                            source {
                                attributeDefaultValue = "java.lang.Float.NaN"
                                attributeValue = "Float.NaN"
                                fieldValue = "NaN"
                            }

                            jar {
                                attributeDefaultValue = "(0.0/0.0)"
                                attributeValue = "0.0f / 0.0"
                                fieldValue = NO_INITIAL_FIELD_VALUE
                            }
                        },
                ),
                // Check a special float - +infinity.
                ValueExample(
                    name = "float positive infinity",
                    javaType = "float",
                    javaExpression = "Float.POSITIVE_INFINITY",
                    expectedLegacySource =
                        expectations {
                            // TODO(b/354633349): Every single use has a different representation!?
                            //   Ideally, this should just `java.lang.Float.NaN` when that is how it
                            //   is referenced in the source and some expression like `(1.0f/0.0f)`
                            //   when it is defined like that, e.g. on
                            //   `java.lang.Float.POSITIVE_INFINITY` itself.
                            source {
                                attributeDefaultValue = "java.lang.Float.POSITIVE_INFINITY"
                                attributeValue = "Float.POSITIVE_INFINITY"
                                fieldValue = "Infinity"
                            }

                            jar {
                                attributeDefaultValue = "(1.0/0.0)"
                                attributeValue = "1.0f / 0.0"
                                fieldValue = NO_INITIAL_FIELD_VALUE
                            }
                        },
                ),
                ValueExample(
                    name = "float negative infinity",
                    javaType = "float",
                    javaExpression = "Float.NEGATIVE_INFINITY",
                    expectedLegacySource =
                        expectations {
                            // TODO(b/354633349): Every single use has a different representation!?
                            //   Ideally, this should just `java.lang.Float.NaN` when that is how it
                            //   is referenced in the source and some expression like `(1.0f/0.0f)`
                            //   when it is defined like that, e.g. on
                            //   `java.lang.Float.NEGATIVE_INFINITY` itself.
                            source {
                                attributeDefaultValue = "java.lang.Float.NEGATIVE_INFINITY"
                                attributeValue = "Float.NEGATIVE_INFINITY"
                                fieldValue = "-Infinity"
                            }

                            jar {
                                attributeDefaultValue = "(-1.0/0.0)"
                                attributeValue = "-1.0f / 0.0"
                                fieldValue = NO_INITIAL_FIELD_VALUE
                            }
                        },
                ),
                // Check a simple int.
                ValueExample(
                    name = "int",
                    javaType = "int",
                    javaExpression = "17",
                    expectedLegacySource = expectations { common = "17" },
                ),
                // Check an int with a unary plus.
                ValueExample(
                    name = "int positive",
                    javaType = "int",
                    javaExpression = "+17",
                    expectedLegacySource =
                        expectations {
                            common = "17"
                            source {
                                // TODO(b/354633349): The leading + is unnecessary.
                                attributeValue = "+17"
                            }
                        },
                ),
                // Check an int with a unary minus.
                ValueExample(
                    name = "int negative",
                    javaType = "int",
                    javaExpression = "-17",
                    expectedLegacySource = expectations { common = "-17" },
                ),
                // Check a simple long with an integer value.
                ValueExample(
                    name = "long with int",
                    javaType = "long",
                    javaExpression = "1000",
                    expectedLegacySource =
                        expectations {
                            // TODO(b/354633349): Consistency is good. It's not clear what the best
                            //  way of formatting this is. Add a trailing L to make it clear it is a
                            //  long when parsing the signature file even if the annotation
                            //  definition is not available or only add it when strictly necessary.
                            common = "1000L"
                            fieldValue = "1000"
                            source {
                                attributeDefaultValue = "1000"
                                attributeValue = "1000"
                            }
                        },
                ),
                // Check a simple long with an upper case suffix.
                ValueExample(
                    name = "long with upper L",
                    javaType = "long",
                    javaExpression = "10000000000L",
                    expectedLegacySource =
                        expectations {
                            common = "10000000000L"
                            // TODO(b/354633349): Consistency is good.
                            fieldValue = "10000000000"
                        },
                ),
                // Check a simple long with a lower case suffix.
                ValueExample(
                    name = "long with lower l",
                    javaType = "long",
                    javaExpression = "10000000000l",
                    expectedLegacySource =
                        expectations {
                            common = "10000000000L"
                            // TODO(b/354633349): Consistency is good.
                            fieldValue = "10000000000"

                            source {
                                // TODO(b/354633349): Consistency is good.
                                attributeValue = "10000000000l"
                            }
                        },
                ),
                // Check a simple short with a lower case suffix.
                ValueExample(
                    name = "short",
                    javaType = "short",
                    javaExpression = "32000",
                    expectedLegacySource = expectations { common = "32000" },
                ),
                // Check a simple string.
                ValueExample(
                    name = "String",
                    javaType = "String",
                    javaExpression = "\"string\"",
                    expectedLegacySource =
                        expectations {
                            common = "\"string\""
                            // TODO(b/354633349): Should have surrounding quotes.
                            fieldValue = "string"
                        },
                ),
                // Check a simple string array.
                ValueExample(
                    name = "String array",
                    javaType = "String[]",
                    javaExpression = "{\"string1\", \"string2\"}",
                    expectedLegacySource =
                        expectations {
                            common = "{\"string1\", \"string2\"}"

                            // This is ok as an array is not a constant.
                            fieldValue = NO_INITIAL_FIELD_VALUE
                        },
                ),
                // Check passing a single value to an array type.
                ValueExample(
                    name = "String array with single string",
                    javaType = "String[]",
                    javaExpression = "\"string\"",
                    suitableFor = allValueUseSitesExceptFields,
                    expectedLegacySource =
                        expectations {
                            common = "\"string\""

                            jar { common = "{\"string\"}" }
                        },
                ),
                ValueExample(
                    name = "String using constant",
                    javaType = "String",
                    javaExpression = "Constants.STRING_CONSTANT",
                    expectedLegacySource =
                        expectations {
                            common = "\"constant\""

                            jar {
                                // TODO(b/354633349): Should have surrounding quotes.
                                fieldValue = "constant"
                            }

                            source {
                                common = "test.pkg.Constants.STRING_CONSTANT"
                                // TODO(b/354633349): Fully qualified is better.
                                attributeValue = "Constants.STRING_CONSTANT"
                                // TODO(b/354633349): Should at least be the constant string, if not
                                //   a field reference.
                                fieldValue = NO_INITIAL_FIELD_VALUE
                            }
                        },
                )
            )

        /**
         * The list of [ValueExample]s that will be tested across [CodebaseProducer] and
         * [ValueUseSite]s.
         */
        internal val valueExamples =
            allValueExamples
                .filter { it.testThis }
                .let { filtered -> filtered.ifEmpty { allValueExamples } }
    }
}
