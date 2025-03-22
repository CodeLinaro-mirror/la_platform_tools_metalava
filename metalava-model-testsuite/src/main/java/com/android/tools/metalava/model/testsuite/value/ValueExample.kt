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

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.testsuite.value.ValueExample.Companion.NO_INITIAL_FIELD_VALUE

/**
 * Encapsulates information about a value example.
 *
 * This will be useful for a number of different tests around values.
 */
class ValueExample(
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
    expectedLegacySource: Expectation<String>,

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
    val expectedLegacySource =
        // If the field is not a constant then wrap it in an Expectation that will enforce that
        // fields only have constant values.
        if (isConstant) expectedLegacySource else NotConstantFieldExpectation(expectedLegacySource)

    /** The suffix to add to class names to make them specific to this example. */
    val classSuffix = name.replace(" ", "_")

    /** True if this is supported to be a field constant. */
    private val isConstant
        get() = javaType in constantTypeNames

    companion object {
        /**
         * A special value used for fields for whom [FieldItem.legacyInitialValue] returns `null`.
         */
        internal const val NO_INITIAL_FIELD_VALUE = "NO INITIAL FIELD VALUE"

        /** Names of constant types used in [ValueExample.javaType]. */
        private val constantTypeNames = buildSet {
            for (kind in PrimitiveTypeItem.Primitive.entries) {
                add(kind.primitiveName)
            }
            add("String")
        }

        /**
         * The list of all [ValueExample]s that could be tested across [ProducerKind] and
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

                            annotationToSource =
                                "@test.pkg.OtherAnnotation(" +
                                    "classType=void.class," +
                                    " enumType=test.pkg.TestEnum.DEFAULT," +
                                    " intType=1," +
                                    " stringType=\"default\"," +
                                    " stringArrayType={}" +
                                    ")"
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
                            fieldWriteWithSemicolon = "120"
                        },
                ),
                // Check a unicode char.
                ValueExample(
                    name = "char unicode",
                    javaType = "char",
                    javaExpression = "'\\u2912'",
                    expectedLegacySource =
                        expectations {
                            common = "'\\u2912'"
                            // TODO(b/354633349): Should have surrounding quotes and use the
                            //   `\uABCD` form.
                            fieldValue = "⤒"
                            jar { attributeValue = "'⤒'" }
                            fieldWriteWithSemicolon = "10514"
                        },
                ),
                // Check char escaped.
                ValueExample(
                    name = "char escaped",
                    javaType = "char",
                    javaExpression = "'\\t'",
                    expectedLegacySource =
                        expectations {
                            // This seems like the best representation. Quoted and escaped.
                            common = "'\\t'"
                            // TODO(b/354633349): Should have surrounding quotes and use the
                            //   `\uABCD` form.
                            fieldValue = "\t"
                            fieldWriteWithSemicolon = "9"
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
                        },
                ),
                // Check an array class literal.
                ValueExample(
                    name = "class array literal",
                    javaType = "Class<?>",
                    javaExpression = "List[].class",
                    javaImports = listOf("java.util.List"),
                    expectedLegacySource =
                        expectations {
                            common = "java.util.List[].class"
                            source {
                                // TODO(b/354633349): Fully qualified is better.
                                common = "List[].class"
                                attributeDefaultValue = "java.util.List[].class"
                            }
                        },
                ),
                // Check a primitive class literal.
                ValueExample(
                    name = "class void primitive class",
                    javaType = "Class<?>",
                    javaExpression = "void.class",
                    expectedLegacySource = expectations { common = "void.class" },
                ),
                // Check a primitive wrapper class literal.
                ValueExample(
                    name = "class void wrapper class",
                    javaType = "Class<?>",
                    javaExpression = "Void.class",
                    expectedLegacySource =
                        expectations {
                            common = "java.lang.Void.class"
                            source {
                                // TODO(b/354633349): Fully qualified is better unless java.lang
                                //   prefix is removed.
                                attributeValue = "Void.class"
                                annotationToSource = "Void.class"
                            }
                        },
                ),
                // Check a primitive array class literal.
                ValueExample(
                    name = "class int array literal",
                    javaType = "Class<?>",
                    javaExpression = "int[].class",
                    expectedLegacySource = expectations { common = "int[].class" },
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
                                annotationToSource = "3"
                            }
                        },
                ),
                // Check a simple double with exponent
                ValueExample(
                    name = "double with exponent",
                    javaType = "double",
                    javaExpression = "7e10",
                    expectedLegacySource =
                        expectations {
                            common = "7.0E10"

                            source { attributeValue = "7e10" }
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
                                annotationToSource = "java.lang.Double.NaN"
                                fieldValue = "NaN"
                                fieldWriteWithSemicolon = "(0.0/0.0)"
                            }

                            jar {
                                attributeDefaultValue = "(0.0/0.0)"
                                attributeValue = "0.0d / 0.0"
                                annotationToSource = "0.0 / 0.0"
                                fieldValue = NO_INITIAL_FIELD_VALUE
                                fieldWriteWithSemicolon = NO_INITIAL_FIELD_VALUE
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
                                annotationToSource = "java.lang.Double.POSITIVE_INFINITY"
                                fieldValue = "Infinity"
                                fieldWriteWithSemicolon = "(1.0/0.0)"
                            }

                            jar {
                                attributeDefaultValue = "(1.0/0.0)"
                                attributeValue = "1.0 / 0.0"
                                annotationToSource = "1.0 / 0.0"
                                fieldValue = NO_INITIAL_FIELD_VALUE
                                fieldWriteWithSemicolon = NO_INITIAL_FIELD_VALUE
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
                                annotationToSource = "java.lang.Double.NEGATIVE_INFINITY"
                                fieldValue = "-Infinity"
                                fieldWriteWithSemicolon = "(-1.0/0.0)"
                            }

                            jar {
                                attributeDefaultValue = "(-1.0/0.0)"
                                attributeValue = "-1.0 / 0.0"
                                annotationToSource = "-1.0 / 0.0"
                                fieldValue = NO_INITIAL_FIELD_VALUE
                                fieldWriteWithSemicolon = NO_INITIAL_FIELD_VALUE
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
                                annotationToSource = "3"
                            }

                            jar {
                                // TODO(b/354633349): Consistency is good.
                                common = "3.0f"
                            }

                            // TODO(b/354633349): Consistency is good.
                            fieldValue = "3.0"
                            fieldWriteWithSemicolon = "3.0f"
                        },
                ),
                // Check a simple float with exponent
                ValueExample(
                    name = "float with exponent",
                    javaType = "float",
                    javaExpression = "7e10f",
                    expectedLegacySource =
                        expectations {
                            common = "7.0E10f"

                            source { attributeValue = "7e10f" }

                            fieldValue = "7.0E10"
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
                            annotationToSource = "3.141f"

                            jar {
                                // TODO(b/354633349): Consistency is good.
                                common = "3.141f"
                            }

                            // TODO(b/354633349): Consistency is good.
                            fieldValue = "3.141"
                            fieldWriteWithSemicolon = "3.141f"
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
                                annotationToSource = "java.lang.Float.NaN"
                                fieldValue = "NaN"
                                fieldWriteWithSemicolon = "(0.0f/0.0f)"
                            }

                            jar {
                                attributeDefaultValue = "(0.0/0.0)"
                                attributeValue = "0.0f / 0.0"
                                annotationToSource = "0.0f / 0.0"
                                fieldValue = NO_INITIAL_FIELD_VALUE
                                fieldWriteWithSemicolon = NO_INITIAL_FIELD_VALUE
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
                                annotationToSource = "java.lang.Float.POSITIVE_INFINITY"
                                fieldValue = "Infinity"
                                fieldWriteWithSemicolon = "(1.0f/0.0f)"
                            }

                            jar {
                                attributeDefaultValue = "(1.0/0.0)"
                                attributeValue = "1.0f / 0.0"
                                annotationToSource = "1.0f / 0.0"
                                fieldValue = NO_INITIAL_FIELD_VALUE
                                fieldWriteWithSemicolon = NO_INITIAL_FIELD_VALUE
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
                                annotationToSource = "java.lang.Float.NEGATIVE_INFINITY"
                                fieldValue = "-Infinity"
                                fieldWriteWithSemicolon = "(-1.0f/0.0f)"
                            }

                            jar {
                                attributeDefaultValue = "(-1.0/0.0)"
                                attributeValue = "-1.0f / 0.0"
                                annotationToSource = "-1.0F / 0.0"
                                fieldValue = NO_INITIAL_FIELD_VALUE
                                fieldWriteWithSemicolon = NO_INITIAL_FIELD_VALUE
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

                                annotationToSource = "0x11"
                            }
                        },
                ),
                // Check an int with a unary minus.
                ValueExample(
                    name = "int negative",
                    javaType = "int",
                    javaExpression = "-17",
                    expectedLegacySource =
                        expectations {
                            common = "-17"

                            annotationToSource = "0xffffffef"
                        },
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
                                annotationToSource = "1000"
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
                ValueExample(
                    name = "String escaped",
                    javaType = "String",
                    javaExpression = "\"str\\ning\"",
                    expectedLegacySource =
                        expectations {
                            common = "\"str\\ning\""
                            // TODO(b/354633349): Should have surrounding quotes and newline should
                            //   be escaped.
                            fieldValue = "str\ning"
                        },
                ),
                // Check a simple string array.
                ValueExample(
                    name = "String array",
                    javaType = "String[]",
                    javaExpression = "{\"string1\", \"string2\"}",
                    expectedLegacySource = expectations { common = "{\"string1\", \"string2\"}" },
                ),
                // Check passing a single value to an array type.
                ValueExample(
                    name = "String array with single string",
                    javaType = "String[]",
                    javaExpression = "\"string\"",
                    // Fields that are of type String[] cannot be given a solitary string like an
                    // annotation attribute can.
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
                                // TODO(b/354633349): Should have surrounding quotes, if not
                                //   a field reference.
                                fieldValue = "constant"
                                // TODO(b/354633349): Should probably be a field reference, at least
                                //   in some cases.
                                fieldWriteWithSemicolon = "\"constant\""
                            }
                        },
                )
            )

        /**
         * The list of [ValueExample]s that will be tested across [ProducerKind] and
         * [ValueUseSite]s.
         */
        internal val valueExamples =
            allValueExamples
                .filter { it.testThis }
                .let { filtered -> filtered.ifEmpty { allValueExamples } }
    }
}

/**
 * An [Expectation] that wraps another [Expectation] and return [NO_INITIAL_FIELD_VALUE] for fields.
 *
 * This is used when a [ValueExample] is not a constant and so a field that uses it will not have a
 * value.
 */
class NotConstantFieldExpectation(private val delegate: Expectation<String>) : Expectation<String> {
    override fun expectationFor(
        producerKind: ProducerKind,
        valueUseSite: ValueUseSite,
        codebase: Codebase
    ): String {
        return when (valueUseSite) {
            ValueUseSite.FIELD_VALUE,
            ValueUseSite.FIELD_WRITE_WITH_SEMICOLON -> {
                NO_INITIAL_FIELD_VALUE
            }
            else -> delegate.expectationFor(producerKind, valueUseSite, codebase)
        }
    }
}
