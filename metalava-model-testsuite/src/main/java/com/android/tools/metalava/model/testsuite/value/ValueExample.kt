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

import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.arrayTypeItem
import com.android.tools.metalava.model.testing.classTypeItem
import com.android.tools.metalava.model.testing.primitiveTypeForKind
import com.android.tools.metalava.model.testing.value.annotationValue
import com.android.tools.metalava.model.testing.value.arrayValueFromAny
import com.android.tools.metalava.model.testing.value.classObjectValue
import com.android.tools.metalava.model.testing.value.fieldReferenceValue
import com.android.tools.metalava.model.testing.value.literalValue
import com.android.tools.metalava.model.testing.value.primitiveValueForKind
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueUseSite
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerTracker
import java.util.EnumSet
import kotlin.reflect.KClass

/**
 * Encapsulates information about a value example.
 *
 * This will be useful for a number of different tests around values.
 */
class ValueExample
@EntryPoint
constructor(
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

    /**
     * The Kotlin type.
     *
     * Primitive types are different between Java and Kotlin, as are some other types, but most
     * custom types are the same so default to [javaType].
     */
    val kotlinType: String = javaType,

    /**
     * The Kotlin expression.
     *
     * Some Kotlin expressions are different to Java but many are the same so default to
     * [javaExpression].
     */
    val kotlinExpression: String = javaExpression,

    /**
     * The Kotlin type for use in annotations.
     *
     * Kotlin automatically maps between [KClass] and [Class] when creating, using and reading
     * annotations so annotations it must use [KClass] not [Class].
     */
    val kotlinTypeForAnnotation: String = if (kotlinType == "Class<*>") "KClass<*>" else kotlinType,

    /**
     * The Kotlin expression for use in annotations.
     *
     * Kotlin automatically maps between [KClass] and [Class] when creating, using and reading
     * annotations so annotations it must use `<class>::class` not `<class>::class.java`.
     */
    val kotlinExpressionForAnnotation: String = kotlinExpression.substringBefore(".java"),

    /**
     * The optional Kotlin imports.
     *
     * Automatically adds `kotlin.reflect.KClass` if needed.
     */
    val kotlinImports: List<String> =
        if (kotlinTypeForAnnotation == "KClass<*>") javaImports + "kotlin.reflect.KClass"
        else javaImports,

    /**
     * The signature type, defaults to [javaType] as signature files generally use Java types and
     * values but needs to be overridden in some cases, e.g. to use qualified types in place of
     * unqualified types as signature files requires most types to be qualified.
     */
    val signatureType: String = javaType,

    /**
     * The signature expression, defaults to [javaExpression] as signature files generally use Java
     * types and values but needs to be overridden in some cases, e.g. to use qualified types in
     * place of unqualified types as signature files requires most types to be qualified.
     */
    val signatureExpression: String = javaExpression,

    /**
     * The set of [LegacyValueUseSite]s in which this example will be tested; defaults to all of
     * them.
     */
    val suitableFor: Set<LegacyValueUseSite> = allLegacyValueUseSites,

    /** The set of [InputFormat]s for which this example is valid. */
    val validForInputFormats: Set<InputFormat> = allInputFormats,

    /**
     * The legacy string representation of [javaExpression].
     *
     * This may differ by [ProducerKind] and [LegacyValueUseSite].
     */
    private val expectedLegacySource: Expectation<String?>,

    /**
     * Kotlin source expressions can have a different representation than the same source expression
     * in Java.
     *
     * Rather than make [Expectation] support another dimension on top of [LegacyValueUseSite] and
     * [ProducerKind] for the few cases where there are differences, it is handled by having this
     * Kotlin specific expectation sit alongside and default to [expectedLegacySource].
     */
    private val expectedKotlinLegacySource: Expectation<String?> = expectedLegacySource,

    /**
     * The legacy value of [javaExpression].
     *
     * This may differ by [ProducerKind] and [LegacyValueUseSite].
     */
    private val expectedLegacyValue: Expectation<Any?>? = null,

    /**
     * Kotlin source expressions can produce different values than the same source expression in
     * Java.
     *
     * Rather than make [Expectation] support another dimension on top of [LegacyValueUseSite] and
     * [ProducerKind] for the few cases where there are differences, it is handled by having this
     * Kotlin specific expectation sit alongside and default to [expectedLegacyValue].
     */
    private val expectedKotlinLegacyValue: Expectation<Any?>? = expectedLegacyValue,

    /**
     * The expected [Value] for this case.
     *
     * This may differ by [ProducerKind] and [LegacyValueUseSite].
     */
    val expectedValue: Expectation<Value?>,

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
    /**
     * Record the stack trace of the creation of this which can be used to provide a stack trace to
     * the creator of this instance in the event of a test failure.
     */
    val entryPointCallerTracker = EntryPointCallerTracker()

    /**
     * Enforces that field values are constant.
     *
     * If the example is not for a constant type then this returns an [Expectation] that will
     * replace the expected value for fields with `null`. Otherwise, this just returns this
     * [Expectation].
     */
    private fun <T : Any> Expectation<T?>.enforceFieldValuesAreConstant(): Expectation<T?> =
        if (isConstant) this else TransformFieldExpectation(this, { null })

    /** Get the expected legacy source for [inputFormat]. */
    fun expectedLegacySourceFor(inputFormat: InputFormat) =
        when (inputFormat) {
            InputFormat.KOTLIN ->
                // Kotlin overrides the standard expectations.
                expectedKotlinLegacySource.fallBackTo(expectedLegacySource)
            else -> expectedLegacySource
        }.enforceFieldValuesAreConstant()

    /** Get the expected legacy value for [inputFormat]. */
    fun expectedLegacyValueFor(inputFormat: InputFormat) =
        when (inputFormat) {
            InputFormat.KOTLIN ->
                // Kotlin overrides the standard expectations.
                if (expectedLegacyValue == null) expectedKotlinLegacyValue
                else expectedKotlinLegacyValue?.fallBackTo(expectedLegacyValue)
            else -> expectedLegacyValue
        }?.enforceFieldValuesAreConstant()

    /** The suffix to add to class names to make them specific to this example. */
    val classSuffix = name.replace(' ', '_').replace('-', '_')

    /** True if this is supported to be a field constant. */
    internal val isConstant
        get() = javaType in constantTypeNames

    companion object {
        /** Names of constant types used in [ValueExample.javaType]. */
        private val constantTypeNames = buildSet {
            for (kind in Primitive.entries) {
                add(kind.primitiveName)
            }
            add("String")
        }

        /** All the [InputFormat]s. */
        private val allInputFormats = EnumSet.allOf(InputFormat::class.java)

        /** All except Kotlin. */
        private val notValidForKotlin = EnumSet.complementOf(EnumSet.of(InputFormat.KOTLIN))

        /** All except Signature. */
        private val notValidForSignature = EnumSet.complementOf(EnumSet.of(InputFormat.SIGNATURE))

        /** Only Java. */
        private val onlyValidForJava = EnumSet.of(InputFormat.JAVA)

        /**
         * The list of all [ValueExample]s that could be tested across [ProducerKind] and
         * [LegacyValueUseSite]s.
         */
        private val allValueExamples =
            listOf(
                // Check an annotation literal.
                ValueExample(
                    name = "annotation",
                    javaType = "OtherAnnotation",
                    javaExpression = "@OtherAnnotation(intType = 1)",
                    // Must fully qualify most classes in signature files.
                    signatureType = "test.pkg.OtherAnnotation",
                    signatureExpression = "@test.pkg.OtherAnnotation(intType = 1)",
                    expectedLegacySource =
                        expectations {
                            common = "@test.pkg.OtherAnnotation(intType = 1)"
                            source { attributeValue = "@OtherAnnotation(intType = 1)" }
                            annotationToSource = "@test.pkg.OtherAnnotation(intType=1)"
                        },
                    expectedKotlinLegacySource =
                        expectations {
                            common = "OtherAnnotation(intType = 1)"

                            source { attributeDefaultValue = "test.pkg.OtherAnnotation(intType=1)" }
                        },
                    expectedLegacyValue =
                        expectations {
                            common = "@test.pkg.OtherAnnotation(intType = 1)"
                            source { common = "@OtherAnnotation(intType = 1)" }
                        },
                    expectedKotlinLegacyValue =
                        expectations { common = "OtherAnnotation(intType = 1)" },
                    // Annotation literals cannot be used in fields.
                    suitableFor = allLegacyValueUseSitesExceptFields,
                    expectedValue =
                        expectations {
                            common =
                                annotationValue(
                                    "test.pkg.OtherAnnotation",
                                    "intType" to literalValue(1),
                                )
                        },
                ),
                ValueExample(
                    name = "annotation - no attributes",
                    javaType = "test.pkg.OtherAnnotation",
                    javaExpression = "@test.pkg.OtherAnnotation",
                    // Kotlin must supply parentheses as it treats an annotation as an annotation
                    // class constructor call.
                    kotlinExpression = "test.pkg.OtherAnnotation()",
                    expectedLegacySource =
                        expectations {
                            common = "@test.pkg.OtherAnnotation"

                            annotationToSource = "@test.pkg.OtherAnnotation"
                        },
                    expectedKotlinLegacySource =
                        expectations {
                            common = "test.pkg.OtherAnnotation()"

                            source { attributeDefaultValue = "test.pkg.OtherAnnotation()" }
                        },
                    expectedLegacyValue = expectations { common = "@test.pkg.OtherAnnotation" },
                    expectedKotlinLegacyValue =
                        expectations { common = "test.pkg.OtherAnnotation()" },
                    // Annotation literals cannot be used in fields.
                    suitableFor = allLegacyValueUseSitesExceptFields,
                    expectedValue =
                        expectations { common = annotationValue("test.pkg.OtherAnnotation") },
                ),
                ValueExample(
                    name = "annotation - multiple attributes",
                    javaType = "test.pkg.OtherAnnotation",
                    javaExpression = "@test.pkg.OtherAnnotation(stringType=\"one\", intType=3)",
                    expectedLegacySource =
                        expectations {
                            common = "@test.pkg.OtherAnnotation(stringType = \"one\", intType = 3)"

                            annotationToSource =
                                "@test.pkg.OtherAnnotation(stringType=\"one\", intType=3)"
                            source {
                                attributeValue =
                                    "@test.pkg.OtherAnnotation(stringType=\"one\", intType=3)"
                            }
                        },
                    expectedKotlinLegacySource =
                        expectations {
                            common = "test.pkg.OtherAnnotation(stringType=\"one\", intType=3)"

                            annotationToSource = "test.pkg.OtherAnnotation(\"one\", 3)"
                        },
                    expectedLegacyValue =
                        expectations {
                            common = "@test.pkg.OtherAnnotation(stringType=\"one\", intType=3)"
                            jar {
                                common =
                                    "@test.pkg.OtherAnnotation(stringType = \"one\", intType = 3)"
                            }
                        },
                    expectedKotlinLegacyValue =
                        expectations {
                            common = "test.pkg.OtherAnnotation(stringType=\"one\", intType=3)"
                        },
                    // Annotation literals cannot be used in fields.
                    suitableFor = allLegacyValueUseSitesExceptFields,
                    expectedValue =
                        expectations {
                            common =
                                annotationValue(
                                    "test.pkg.OtherAnnotation",
                                    "intType" to literalValue(3),
                                    "stringType" to literalValue("one"),
                                )
                        },
                ),
                ValueExample(
                    name = "annotation - single value",
                    javaType = "test.pkg.SingleValueAnnotation",
                    javaExpression = "@test.pkg.SingleValueAnnotation(\"text\")",
                    expectedLegacySource =
                        expectations { common = "@test.pkg.SingleValueAnnotation(\"text\")" },
                    expectedKotlinLegacySource =
                        expectations { common = "test.pkg.SingleValueAnnotation(\"text\")" },
                    expectedLegacyValue =
                        expectations { common = "@test.pkg.SingleValueAnnotation(\"text\")" },
                    expectedKotlinLegacyValue =
                        expectations { common = "test.pkg.SingleValueAnnotation(\"text\")" },
                    // Annotation literals cannot be used in fields.
                    suitableFor = allLegacyValueUseSitesExceptFields,
                    expectedValue =
                        expectations {
                            common =
                                annotationValue(
                                    "test.pkg.SingleValueAnnotation",
                                    "value" to literalValue("text"),
                                )
                        },
                ),
                // Check a simple boolean true value.
                ValueExample(
                    name = "boolean true",
                    javaType = "boolean",
                    javaExpression = "true",
                    kotlinType = "Boolean",
                    expectedLegacySource = expectations { common = "true" },
                    expectedLegacyValue = expectations { common = true },
                    expectedValue = expectations { common = literalValue(true) },
                ),
                // Check a simple boolean false value.
                ValueExample(
                    name = "boolean false",
                    javaType = "boolean",
                    javaExpression = "false",
                    kotlinType = "Boolean",
                    expectedLegacySource = expectations { common = "false" },
                    expectedLegacyValue = expectations { common = false },
                    expectedValue = expectations { common = literalValue(false) },
                ),
                // Check a simple byte.
                ValueExample(
                    name = "byte",
                    javaType = "byte",
                    javaExpression = "116",
                    kotlinType = "Byte",
                    expectedLegacySource = expectations { common = "116" },
                    expectedLegacyValue =
                        expectations {
                            common = 116.toByte()
                            attributeValue = 116
                        },
                    expectedKotlinLegacyValue = expectations { attributeValue = 116.toByte() },
                    expectedValue = expectations { common = literalValue(116.toByte()) },
                ),
                // Check a simple char.
                ValueExample(
                    name = "char",
                    javaType = "char",
                    javaExpression = "'x'",
                    kotlinType = "Char",
                    signatureExpression = "120",
                    expectedLegacySource =
                        expectations {
                            common = "'x'"
                            fieldWriteWithSemicolon = "120"
                        },
                    expectedKotlinLegacySource = expectations { attributeDefaultValue = "\"x\"" },
                    expectedLegacyValue = expectations { common = 'x' },
                    expectedValue = expectations { common = literalValue('x') },
                ),
                // Check a unicode char.
                ValueExample(
                    name = "char unicode",
                    javaType = "char",
                    javaExpression = "'\\u2912'",
                    kotlinType = "Char",
                    signatureExpression = "10514",
                    expectedLegacySource =
                        expectations {
                            common = "'\\u2912'"
                            jar { attributeValue = "'⤒'" }
                            fieldWriteWithSemicolon = "10514"
                        },
                    expectedKotlinLegacySource =
                        expectations { attributeDefaultValue = "\"\\u2912\"" },
                    expectedLegacyValue = expectations { common = '⤒' },
                    expectedValue = expectations { common = literalValue('\u2912') },
                ),
                // Check char escaped.
                ValueExample(
                    name = "char escaped",
                    javaType = "char",
                    javaExpression = "'\\t'",
                    kotlinType = "Char",
                    signatureExpression = "9",
                    expectedLegacySource =
                        expectations {
                            // This seems like the best representation. Quoted and escaped.
                            common = "'\\t'"
                            fieldWriteWithSemicolon = "9"
                        },
                    expectedKotlinLegacySource = expectations { attributeDefaultValue = "\"\\t\"" },
                    expectedLegacyValue = expectations { common = '\t' },
                    expectedValue = expectations { common = literalValue('\t') },
                ),
                // Check a class literal for a basic class.
                ValueExample(
                    name = "class literal - basic class",
                    javaType = "Class<?>",
                    javaExpression = "BitSet.class",
                    javaImports = listOf("java.util.BitSet"),
                    kotlinType = "Class<*>",
                    kotlinExpression = "BitSet::class.java",
                    signatureExpression = "java.util.BitSet.class",
                    expectedLegacySource =
                        expectations {
                            common = "java.util.BitSet.class"
                            source {
                                // TODO(b/354633349): Fully qualified is better.
                                common = "BitSet.class"
                                attributeDefaultValue = "java.util.BitSet.class"
                            }
                        },
                    expectedKotlinLegacySource = expectations { common = "BitSet::class" },
                    expectedLegacyValue = expectations { common = "java.util.BitSet" },
                    expectedValue =
                        expectations {
                            common = classObjectValue(classTypeItem("java.util.BitSet"))
                        },
                ),
                // Check a class literal for a generic class.
                ValueExample(
                    name = "class literal - generic class",
                    javaType = "Class<?>",
                    javaExpression = "List.class",
                    javaImports = listOf("java.util.List"),
                    kotlinType = "Class<*>",
                    kotlinExpression = "List::class.java",
                    signatureExpression = "java.util.List.class",
                    expectedLegacySource =
                        expectations {
                            common = "java.util.List.class"
                            source {
                                // TODO(b/354633349): Fully qualified is better.
                                common = "List.class"
                                attributeDefaultValue = "java.util.List.class"
                            }
                        },
                    expectedKotlinLegacySource = expectations { common = "List::class" },
                    expectedLegacyValue = expectations { common = "java.util.List" },
                    expectedKotlinLegacyValue = expectations { common = "java.util.List<?>" },
                    expectedValue =
                        expectations { common = classObjectValue(classTypeItem("java.util.List")) },
                ),
                // Check an array of a basic class literal.
                ValueExample(
                    name = "class literal - array of basic class",
                    javaType = "Class<?>",
                    javaExpression = "BitSet[].class",
                    javaImports = listOf("java.util.BitSet"),
                    kotlinType = "Class<*>",
                    kotlinExpression = "Array<BitSet>::class.java",
                    signatureExpression = "java.util.BitSet[].class",
                    expectedLegacySource =
                        expectations {
                            common = "java.util.BitSet[].class"
                            source {
                                // TODO(b/354633349): Fully qualified is better.
                                common = "BitSet[].class"
                                attributeDefaultValue = "java.util.BitSet[].class"
                            }
                        },
                    expectedKotlinLegacySource =
                        expectations { source { common = "Array<BitSet>::class" } },
                    expectedLegacyValue = expectations { common = "java.util.BitSet[]" },
                    expectedValue =
                        expectations {
                            common =
                                classObjectValue(arrayTypeItem(classTypeItem("java.util.BitSet")))
                        },
                ),
                // Check an array of a generic class literal.
                ValueExample(
                    name = "class literal - array of generic class",
                    javaType = "Class<?>",
                    javaExpression = "List[].class",
                    javaImports = listOf("java.util.List"),
                    // While Kotlin can correctly map a `List[].class` instance from a Java
                    // annotation it has no way of representing it in the source.
                    validForInputFormats = notValidForKotlin,
                    signatureExpression = "java.util.List[].class",
                    expectedLegacySource =
                        expectations {
                            common = "java.util.List[].class"
                            source {
                                // TODO(b/354633349): Fully qualified is better.
                                common = "List[].class"
                                attributeDefaultValue = "java.util.List[].class"
                            }
                        },
                    expectedLegacyValue = expectations { common = "java.util.List[]" },
                    expectedValue =
                        expectations {
                            common =
                                classObjectValue(arrayTypeItem(classTypeItem("java.util.List")))
                        },
                ),
                // Check a primitive void class literal.
                ValueExample(
                    name = "class literal - void primitive",
                    javaType = "Class<?>",
                    javaExpression = "void.class",
                    // While Kotlin can correctly map a `void.class` instance from a Java annotation
                    // it has no way of representing it in the source.
                    validForInputFormats = notValidForKotlin,
                    expectedLegacySource = expectations { common = "void.class" },
                    expectedLegacyValue = expectations { common = "void" },
                    expectedValue =
                        expectations {
                            common = classObjectValue(primitiveTypeForKind(Primitive.VOID))
                        },
                ),
                // Check a primitive void wrapper class literal.
                ValueExample(
                    name = "class literal - void wrapper",
                    javaType = "Class<?>",
                    javaExpression = "Void.class",
                    kotlinType = "Class<*>",
                    kotlinExpression = "java.lang.Void::class.java",
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
                    expectedKotlinLegacySource = expectations { common = "java.lang.Void::class" },
                    expectedLegacyValue = expectations { common = "java.lang.Void" },
                    expectedValue =
                        expectations { common = classObjectValue(classTypeItem("java.lang.Void")) },
                ),
                ValueExample(
                    name = "class literal - int primitive",
                    javaType = "Class<?>",
                    javaExpression = "int.class",
                    kotlinType = "Class<*>",
                    kotlinExpression = "Int::class.java",
                    expectedLegacySource = expectations { common = "int.class" },
                    expectedKotlinLegacySource = expectations { common = "Int::class" },
                    expectedLegacyValue = expectations { common = "int" },
                    expectedKotlinLegacyValue = expectations { common = "java.lang.Integer" },
                    expectedValue =
                        expectations {
                            common = classObjectValue(primitiveTypeForKind(Primitive.INT))
                        },
                ),
                ValueExample(
                    name = "class literal - int wrapper",
                    javaType = "Class<?>",
                    javaExpression = "Integer.class",
                    kotlinType = "Class<*>",
                    kotlinExpression = "Integer::class.java",
                    expectedLegacySource =
                        expectations {
                            common = "java.lang.Integer.class"
                            source {
                                // TODO(b/354633349): Fully qualified is better unless java.lang
                                //   prefix is removed.
                                attributeValue = "Integer.class"
                                annotationToSource = "Integer.class"
                            }
                        },
                    expectedKotlinLegacySource = expectations { common = "Integer::class" },
                    expectedLegacyValue = expectations { common = "java.lang.Integer" },
                    expectedValue =
                        expectations {
                            common = classObjectValue(classTypeItem("java.lang.Integer"))
                        },
                ),
                // Check a primitive array class literal.
                ValueExample(
                    name = "class literal - int array",
                    javaType = "Class<?>",
                    javaExpression = "int[].class",
                    kotlinType = "Class<*>",
                    kotlinExpression = "IntArray::class.java",
                    expectedLegacySource = expectations { common = "int[].class" },
                    expectedKotlinLegacySource = expectations { common = "IntArray::class" },
                    expectedLegacyValue = expectations { common = "int[]" },
                    expectedValue =
                        expectations {
                            common =
                                classObjectValue(arrayTypeItem(primitiveTypeForKind(Primitive.INT)))
                        },
                ),
                // Check a simple double.
                ValueExample(
                    name = "double",
                    javaType = "double",
                    javaExpression = "3.141",
                    kotlinType = "Double",
                    expectedLegacySource = expectations { common = "3.141" },
                    expectedLegacyValue = expectations { common = 3.141 },
                    expectedValue = expectations { common = literalValue(3.141) },
                ),
                // Check a simple double with int
                ValueExample(
                    name = "double with int",
                    javaType = "double",
                    javaExpression = "3",
                    kotlinType = "Double",
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
                    expectedLegacyValue =
                        expectations {
                            common = 3.0
                            source { attributeValue = 3 }
                        },
                    expectedKotlinLegacyValue = expectations { source { common = 3 } },
                    expectedValue =
                        expectations {
                            // Expect a double value created from an int.
                            common = primitiveValueForKind(Primitive.DOUBLE, 3)
                            jar { common = literalValue(3.0) }
                        },
                ),
                // Check a simple double with exponent
                ValueExample(
                    name = "double with exponent",
                    javaType = "double",
                    javaExpression = "7e10",
                    kotlinType = "Double",
                    expectedLegacySource =
                        expectations {
                            common = "7.0E10"

                            source { attributeValue = "7e10" }
                        },
                    expectedLegacyValue = expectations { common = 7e10 },
                    expectedValue = expectations { common = literalValue(7e10) },
                ),
                // Check a special double - Nan.
                ValueExample(
                    name = "double NaN",
                    javaType = "double",
                    javaExpression = "Double.NaN",
                    kotlinType = "Double",
                    expectedLegacySource =
                        expectations {
                            fieldWriteWithSemicolon = "(0.0/0.0)"

                            // TODO(b/354633349): Every single use has a different representation!?
                            //   Ideally, this should just `java.lang.Double.NaN` when that is how
                            //   it is referenced in the source and some expression like `(0.0/0.0)`
                            //   when it is defined like that, e.g. on `java.lang.Double.NaN`
                            //   itself.
                            source {
                                attributeDefaultValue = "java.lang.Double.NaN"
                                attributeValue = "Double.NaN"
                                annotationToSource = "java.lang.Double.NaN"
                            }

                            jar {
                                attributeDefaultValue = "(0.0/0.0)"
                                attributeValue = "0.0d / 0.0"
                                annotationToSource = "0.0 / 0.0"
                            }
                        },
                    expectedKotlinLegacySource =
                        expectations {
                            attributeDefaultValue = "kotlin.jvm.internal.DoubleCompanionObject.NaN"
                            annotationToSource = "kotlin.jvm.internal.DoubleCompanionObject.NaN"
                        },
                    expectedLegacyValue =
                        expectations {
                            common = Double.NaN
                            jar { fieldValue = null }
                        },
                    expectedValue = expectations { common = literalValue(Double.NaN) },
                ),
                // Check a special double - +infinity.
                ValueExample(
                    name = "double positive infinity",
                    javaType = "double",
                    javaExpression = "Double.POSITIVE_INFINITY",
                    kotlinType = "Double",
                    expectedLegacySource =
                        expectations {
                            fieldWriteWithSemicolon = "(1.0/0.0)"

                            // TODO(b/354633349): Every single use has a different representation!?
                            //   Ideally, this should just `java.lang.Double.NaN` when that is how
                            //   it is referenced in the source and some expression like `(1.0/0.0)`
                            //   when it is defined like that, e.g. on
                            //   `java.lang.Double.POSITIVE_INFINITY` itself.
                            source {
                                attributeDefaultValue = "java.lang.Double.POSITIVE_INFINITY"
                                attributeValue = "Double.POSITIVE_INFINITY"
                                annotationToSource = "java.lang.Double.POSITIVE_INFINITY"
                            }

                            jar {
                                attributeDefaultValue = "(1.0/0.0)"
                                attributeValue = "1.0 / 0.0"
                                annotationToSource = "1.0 / 0.0"
                            }
                        },
                    expectedKotlinLegacySource =
                        expectations {
                            attributeDefaultValue =
                                "kotlin.jvm.internal.DoubleCompanionObject.POSITIVE_INFINITY"
                            annotationToSource =
                                "kotlin.jvm.internal.DoubleCompanionObject.POSITIVE_INFINITY"
                        },
                    expectedLegacyValue =
                        expectations {
                            common = Double.POSITIVE_INFINITY
                            jar { fieldValue = null }
                        },
                    expectedValue =
                        expectations { common = literalValue(Double.POSITIVE_INFINITY) },
                ),
                ValueExample(
                    name = "double negative infinity",
                    javaType = "double",
                    javaExpression = "Double.NEGATIVE_INFINITY",
                    kotlinType = "Double",
                    expectedLegacySource =
                        expectations {
                            fieldWriteWithSemicolon = "(-1.0/0.0)"

                            // TODO(b/354633349): Every single use has a different representation!?
                            //   Ideally, this should just `java.lang.Double.NaN` when that is how
                            //   it is referenced in the source and some expression like `(1.0/0.0)`
                            //   when it is defined like that, e.g. on
                            //   `java.lang.Double.NEGATIVE_INFINITY` itself.
                            source {
                                attributeDefaultValue = "java.lang.Double.NEGATIVE_INFINITY"
                                attributeValue = "Double.NEGATIVE_INFINITY"
                                annotationToSource = "java.lang.Double.NEGATIVE_INFINITY"
                            }

                            jar {
                                attributeDefaultValue = "(-1.0/0.0)"
                                attributeValue = "-1.0 / 0.0"
                                annotationToSource = "-1.0 / 0.0"
                            }
                        },
                    expectedKotlinLegacySource =
                        expectations {
                            attributeDefaultValue =
                                "kotlin.jvm.internal.DoubleCompanionObject.NEGATIVE_INFINITY"
                            annotationToSource =
                                "kotlin.jvm.internal.DoubleCompanionObject.NEGATIVE_INFINITY"
                        },
                    expectedLegacyValue =
                        expectations {
                            common = Double.NEGATIVE_INFINITY
                            jar { fieldValue = null }
                        },
                    expectedValue =
                        expectations { common = literalValue(Double.NEGATIVE_INFINITY) },
                ),
                ValueExample(
                    name = "double - negative max",
                    javaType = "double",
                    javaExpression = "-1.7976931348623157E308",
                    kotlinType = "Double",
                    expectedLegacySource = expectations { common = "-1.7976931348623157E308" },
                    expectedLegacyValue = expectations { common = -1.7976931348623157E308 },
                    expectedValue = expectations { common = literalValue(-Double.MAX_VALUE) },
                ),
                ValueExample(
                    name = "double - hex",
                    javaType = "double",
                    javaExpression = "0x1p3",
                    // Kotlin does not support hex floating point numbers.
                    validForInputFormats = notValidForKotlin,
                    expectedLegacySource =
                        expectations {
                            common = "8.0"
                            source { attributeValue = "0x1p3" }
                        },
                    expectedLegacyValue = expectations { common = 8.0 },
                    expectedValue = expectations { common = literalValue(8.0) },
                ),
                // Check an enum literal.
                ValueExample(
                    name = "enum",
                    javaType = "TestEnum",
                    javaExpression = "TestEnum.VALUE1",
                    // Must fully qualify most classes in signature files.
                    signatureType = "test.pkg.TestEnum",
                    signatureExpression = "test.pkg.TestEnum.VALUE1",
                    expectedLegacySource =
                        expectations {
                            common = "test.pkg.TestEnum.VALUE1"
                            source {
                                // TODO(b/354633349): Fully qualified is better.
                                attributeValue = "TestEnum.VALUE1"
                            }
                        },
                    // Intentionally do not test the value of this because it returns an internal,
                    // model specific object.
                    //   expectedLegacyValue = expectations {},
                    expectedValue =
                        expectations {
                            common = fieldReferenceValue("test.pkg.TestEnum", "VALUE1")
                        },
                ),
                // Check a statically imported enum literal.
                ValueExample(
                    name = "enum - static import",
                    javaType = "TestEnum",
                    javaExpression = "VALUE1",
                    javaImports = listOf("static test.pkg.TestEnum.VALUE1"),
                    kotlinImports = listOf("test.pkg.TestEnum.VALUE1"),
                    // Signature files does not support unqualified fields.
                    validForInputFormats = notValidForSignature,
                    expectedLegacySource =
                        expectations {
                            common = "test.pkg.TestEnum.VALUE1"
                            source {
                                // TODO(b/354633349): Fully qualified is better.
                                attributeValue = "VALUE1"
                            }
                        },
                    // Intentionally do not test the value of this because it returns an internal,
                    // model specific object.
                    //   expectedLegacyValue = expectations {},
                    expectedValue =
                        expectations {
                            common = fieldReferenceValue("test.pkg.TestEnum", "VALUE1")
                        },
                ),
                ValueExample(
                    name = "field - generic class constant",
                    javaType = "String",
                    javaExpression = "GenericClass.STRING_CONSTANT",
                    signatureExpression = "test.pkg.GenericClass.STRING_CONSTANT",
                    expectedLegacySource =
                        expectations {
                            common = "\"constant\""

                            source {
                                common = "test.pkg.GenericClass.STRING_CONSTANT"
                                // TODO(b/354633349): Fully qualified is better.
                                attributeValue = "GenericClass.STRING_CONSTANT"
                                // TODO(b/354633349): Should probably be a field reference, at least
                                //   in some cases.
                                fieldWriteWithSemicolon = "\"constant\""
                            }
                        },
                    expectedKotlinLegacySource =
                        expectations {
                            annotationToSource = "test.pkg.GenericClass.Companion.STRING_CONSTANT"
                        },
                    expectedLegacyValue = expectations { common = "constant" },
                    expectedValue =
                        expectations {
                            common =
                                fieldReferenceValue(
                                    "test.pkg.GenericClass",
                                    "STRING_CONSTANT",
                                    literalValue("constant")
                                )
                            jar {
                                // The compiler will always inline a constant field value.
                                common = literalValue("constant")
                            }
                        },
                ),
                // Check the behavior of using an int constant field with a long value.
                ValueExample(
                    name = "field - long with int constant",
                    javaType = "long",
                    javaExpression = "Constants.INT_CONSTANT",
                    kotlinType = "Long",
                    signatureExpression = "test.pkg.Constants.INT_CONSTANT",
                    expectedLegacySource =
                        expectations {
                            common = "37L"

                            source {
                                annotationToSource = "test.pkg.Constants.INT_CONSTANT"
                                attributeDefaultValue = "test.pkg.Constants.INT_CONSTANT"
                                // TODO(b/354633349): Fully qualified is better.
                                attributeValue = "Constants.INT_CONSTANT"
                            }
                        },
                    expectedLegacyValue =
                        expectations {
                            common = 37
                            source { fieldValue = 37L }
                            jar {
                                // The compiler will always inline a constant field value using the
                                // correct type.
                                common = 37L
                            }
                        },
                    expectedKotlinLegacyValue = expectations { fieldValue = 37 },
                    expectedValue =
                        expectations {
                            common =
                                fieldReferenceValue(
                                    "test.pkg.Constants",
                                    "INT_CONSTANT",
                                    primitiveValueForKind(Primitive.LONG, 37)
                                )
                            jar {
                                // The compiler will always inline a constant field value using the
                                // correct type.
                                common = literalValue(37L)
                            }
                        },
                ),
                // Check a simple float with int
                ValueExample(
                    name = "float with int",
                    javaType = "float",
                    javaExpression = "3",
                    kotlinType = "Float",
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

                            fieldWriteWithSemicolon = "3.0f"
                        },
                    expectedLegacyValue =
                        expectations {
                            common = 3.0f
                            source { attributeValue = 3 }
                        },
                    expectedKotlinLegacyValue = expectations { source { common = 3 } },
                    expectedValue =
                        expectations {
                            // Expect a double value created from an int.
                            common = primitiveValueForKind(Primitive.FLOAT, 3)
                            jar { common = literalValue(3.0f) }
                        },
                ),
                // Check a simple float with exponent
                ValueExample(
                    name = "float with exponent",
                    javaType = "float",
                    javaExpression = "7e10f",
                    kotlinType = "Float",
                    expectedLegacySource =
                        expectations {
                            common = "7.0E10f"

                            source { attributeValue = "7e10f" }
                        },
                    expectedKotlinLegacySource = expectations { attributeDefaultValue = "7.0E10" },
                    expectedLegacyValue = expectations { common = 7.0E10f },
                    expectedValue = expectations { common = literalValue(7e10f) },
                ),
                // Check a simple float with upper F.
                ValueExample(
                    name = "float with upper F",
                    javaType = "float",
                    javaExpression = "3.141F",
                    kotlinType = "Float",
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

                            fieldWriteWithSemicolon = "3.141f"
                        },
                    expectedKotlinLegacySource = expectations { attributeDefaultValue = "3.141" },
                    expectedLegacyValue = expectations { common = 3.141f },
                    expectedValue = expectations { common = literalValue(3.141F) },
                ),
                // Check a simple float with lower F.
                ValueExample(
                    name = "float with lower f",
                    javaType = "float",
                    javaExpression = "3.141f",
                    kotlinType = "Float",
                    expectedLegacySource = expectations { common = "3.141f" },
                    expectedLegacyValue = expectations { common = 3.141f },
                    expectedKotlinLegacySource = expectations { attributeDefaultValue = "3.141" },
                    expectedValue = expectations { common = literalValue(3.141f) },
                ),
                // Check a special float - Nan.
                ValueExample(
                    name = "float NaN",
                    javaType = "float",
                    javaExpression = "Float.NaN",
                    kotlinType = "Float",
                    expectedLegacySource =
                        expectations {
                            fieldWriteWithSemicolon = "(0.0f/0.0f)"

                            // TODO(b/354633349): Every single use has a different representation!?
                            //   Ideally, this should just `java.lang.Float.NaN` when that is how it
                            //   is referenced in the source and some expression like `(0.0f/0.0f)`
                            //   when it is defined like that, e.g. on `java.lang.Float.NaN` itself.
                            source {
                                attributeDefaultValue = "java.lang.Float.NaN"
                                attributeValue = "Float.NaN"
                                annotationToSource = "java.lang.Float.NaN"
                            }

                            jar {
                                attributeDefaultValue = "(0.0/0.0)"
                                attributeValue = "0.0f / 0.0"
                                annotationToSource = "0.0f / 0.0"
                            }
                        },
                    expectedKotlinLegacySource =
                        expectations {
                            attributeDefaultValue = "kotlin.jvm.internal.FloatCompanionObject.NaN"
                            annotationToSource = "kotlin.jvm.internal.FloatCompanionObject.NaN"
                        },
                    expectedLegacyValue =
                        expectations {
                            common = Float.NaN
                            jar {
                                attributeValue = Double.NaN
                                fieldValue = null
                            }
                        },
                    expectedKotlinLegacyValue =
                        expectations { source { attributeValue = Double.NaN } },
                    expectedValue = expectations { common = literalValue(Float.NaN) },
                ),
                // Check a special float - +infinity.
                ValueExample(
                    name = "float positive infinity",
                    javaType = "float",
                    javaExpression = "Float.POSITIVE_INFINITY",
                    kotlinType = "Float",
                    expectedLegacySource =
                        expectations {
                            fieldWriteWithSemicolon = "(1.0f/0.0f)"

                            // TODO(b/354633349): Every single use has a different representation!?
                            //   Ideally, this should just `java.lang.Float.NaN` when that is how it
                            //   is referenced in the source and some expression like `(1.0f/0.0f)`
                            //   when it is defined like that, e.g. on
                            //   `java.lang.Float.POSITIVE_INFINITY` itself.
                            source {
                                attributeDefaultValue = "java.lang.Float.POSITIVE_INFINITY"
                                attributeValue = "Float.POSITIVE_INFINITY"
                                annotationToSource = "java.lang.Float.POSITIVE_INFINITY"
                            }

                            jar {
                                attributeDefaultValue = "(1.0/0.0)"
                                attributeValue = "1.0f / 0.0"
                                annotationToSource = "1.0f / 0.0"
                            }
                        },
                    expectedKotlinLegacySource =
                        expectations {
                            attributeDefaultValue =
                                "kotlin.jvm.internal.FloatCompanionObject.POSITIVE_INFINITY"
                            annotationToSource =
                                "kotlin.jvm.internal.FloatCompanionObject.POSITIVE_INFINITY"
                        },
                    expectedLegacyValue =
                        expectations {
                            common = Float.POSITIVE_INFINITY
                            jar {
                                attributeValue = Double.POSITIVE_INFINITY
                                fieldValue = null
                            }
                        },
                    expectedKotlinLegacyValue =
                        expectations { source { attributeValue = Double.POSITIVE_INFINITY } },
                    expectedValue = expectations { common = literalValue(Float.POSITIVE_INFINITY) },
                ),
                ValueExample(
                    name = "float negative infinity",
                    javaType = "float",
                    javaExpression = "Float.NEGATIVE_INFINITY",
                    kotlinType = "Float",
                    expectedLegacySource =
                        expectations {
                            fieldWriteWithSemicolon = "(-1.0f/0.0f)"

                            // TODO(b/354633349): Every single use has a different representation!?
                            //   Ideally, this should just `java.lang.Float.NaN` when that is how it
                            //   is referenced in the source and some expression like `(1.0f/0.0f)`
                            //   when it is defined like that, e.g. on
                            //   `java.lang.Float.NEGATIVE_INFINITY` itself.
                            source {
                                attributeDefaultValue = "java.lang.Float.NEGATIVE_INFINITY"
                                attributeValue = "Float.NEGATIVE_INFINITY"
                                annotationToSource = "java.lang.Float.NEGATIVE_INFINITY"
                            }

                            jar {
                                attributeDefaultValue = "(-1.0/0.0)"
                                attributeValue = "-1.0f / 0.0"
                                annotationToSource = "-1.0F / 0.0"
                            }
                        },
                    expectedKotlinLegacySource =
                        expectations {
                            attributeDefaultValue =
                                "kotlin.jvm.internal.FloatCompanionObject.NEGATIVE_INFINITY"
                            annotationToSource =
                                "kotlin.jvm.internal.FloatCompanionObject.NEGATIVE_INFINITY"
                        },
                    expectedLegacyValue =
                        expectations {
                            common = Float.NEGATIVE_INFINITY
                            jar {
                                attributeValue = Double.NEGATIVE_INFINITY
                                fieldValue = null
                            }
                        },
                    expectedKotlinLegacyValue =
                        expectations { source { attributeValue = Double.NEGATIVE_INFINITY } },
                    expectedValue = expectations { common = literalValue(Float.NEGATIVE_INFINITY) },
                ),
                ValueExample(
                    name = "float - hex",
                    javaType = "float",
                    javaExpression = "0x1p3f",
                    // Kotlin does not support hex floating point numbers.
                    validForInputFormats = notValidForKotlin,
                    expectedLegacySource =
                        expectations {
                            common = "8.0f"
                            source { attributeValue = "0x1p3f" }
                        },
                    expectedLegacyValue = expectations { common = 8.0f },
                    expectedValue = expectations { common = literalValue(8.0f) },
                ),
                // Check a simple int.
                ValueExample(
                    name = "int",
                    javaType = "int",
                    javaExpression = "17",
                    kotlinType = "Int",
                    expectedLegacySource = expectations { common = "17" },
                    expectedLegacyValue = expectations { common = 17 },
                    expectedValue = expectations { common = literalValue(17) },
                ),
                // Check an int with a unary plus.
                ValueExample(
                    name = "int positive",
                    javaType = "int",
                    javaExpression = "+17",
                    kotlinType = "Int",
                    expectedLegacySource =
                        expectations {
                            common = "17"
                            source {
                                // TODO(b/354633349): The leading + is unnecessary.
                                attributeValue = "+17"

                                annotationToSource = "0x11"
                            }
                        },
                    expectedLegacyValue = expectations { common = 17 },
                    expectedValue = expectations { common = literalValue(17) },
                ),
                // Check an int with a unary minus.
                ValueExample(
                    name = "int negative",
                    javaType = "int",
                    javaExpression = "-17",
                    kotlinType = "Int",
                    expectedLegacySource =
                        expectations {
                            common = "-17"

                            annotationToSource = "0xffffffef"
                        },
                    expectedLegacyValue = expectations { common = -17 },
                    expectedValue = expectations { common = literalValue(-17) },
                ),
                // Check an int with a complex expression
                ValueExample(
                    name = "int - complex",
                    javaType = "int",
                    javaExpression = "('_'<<24)|('P'<<16)|('N'<<8)|'G'",
                    kotlinType = "Int",
                    kotlinExpression =
                        "('_'.code shl 24) or ('P'.code shl 16) or ('N'.code shl 8) or 'G'.code",
                    // TODO(b/354633349): Only valid for Java, Kotlin is inconsistent and signature
                    //   files do not have complex expressions at all.
                    validForInputFormats = onlyValidForJava,
                    expectedLegacySource =
                        expectations {
                            common = "1599098439"

                            source {
                                annotationToSource = "0x5f504e47"
                                attributeValue = "('_'<<24)|('P'<<16)|('N'<<8)|'G'"
                            }
                        },
                    expectedKotlinLegacySource =
                        expectations {
                            source {
                                annotationToSource = "0x5f000000 | 0x500000 | 0x4e00 | 'G'.code"
                                attributeValue =
                                    "('_'.code shl 24) or ('P'.code shl 16) or ('N'.code shl 8) or 'G'.code"
                            }
                        },
                    expectedLegacyValue = expectations { common = 1599098439 },
                    expectedKotlinLegacyValue =
                        expectations { source { attributeValue = 1599098439 } },
                    expectedValue = expectations { common = literalValue(1599098439) },
                ),
                // Check a simple long with an integer value.
                ValueExample(
                    name = "long with int",
                    javaType = "long",
                    javaExpression = "1000",
                    kotlinType = "Long",
                    expectedLegacySource =
                        expectations {
                            // TODO(b/354633349): Consistency is good. It's not clear what the best
                            //  way of formatting this is. Add a trailing L to make it clear it is a
                            //  long when parsing the signature file even if the annotation
                            //  definition is not available or only add it when strictly necessary.
                            common = "1000L"
                            source {
                                attributeDefaultValue = "1000"
                                attributeValue = "1000"
                                annotationToSource = "1000"
                            }
                        },
                    expectedKotlinLegacySource = expectations { annotationToSource = "1000L" },
                    expectedLegacyValue =
                        expectations {
                            common = 1000L
                            source { attributeValue = 1000 }
                        },
                    expectedKotlinLegacyValue = expectations { source { attributeValue = 1000L } },
                    expectedValue =
                        expectations {
                            // Expect a long value created from an int.
                            common = primitiveValueForKind(Primitive.LONG, 1000)
                            jar { common = literalValue(1000L) }
                        },
                ),
                // Check a simple long with an upper case suffix.
                ValueExample(
                    name = "long with upper L",
                    javaType = "long",
                    javaExpression = "10000000000L",
                    kotlinType = "Long",
                    expectedLegacySource = expectations { common = "10000000000L" },
                    expectedKotlinLegacySource =
                        expectations { attributeDefaultValue = "10000000000" },
                    expectedLegacyValue = expectations { common = 10000000000L },
                    expectedValue = expectations { common = literalValue(10000000000L) },
                ),
                // Check a simple long with a lower case suffix.
                ValueExample(
                    name = "long with lower l",
                    javaType = "long",
                    javaExpression = "10000000000l",
                    kotlinType = "Long",
                    // Kotlin does not support using a lower case l as a suffix for long, presumably
                    // because it looks too similar to a number 1.
                    validForInputFormats = notValidForKotlin,
                    expectedLegacySource =
                        expectations {
                            common = "10000000000L"

                            source {
                                // TODO(b/354633349): Consistency is good.
                                attributeValue = "10000000000l"
                            }
                        },
                    expectedLegacyValue = expectations { common = 10000000000L },
                    expectedValue = expectations { common = literalValue(10000000000L) },
                ),
                ValueExample(
                    name = "long - min with suffix",
                    javaType = "long",
                    javaExpression = "-9223372036854775808L",
                    expectedLegacySource = expectations { common = "-9223372036854775808L" },
                    // Kotlin does not support specifying -9223372036854775808L as a literal.
                    // See https://youtrack.jetbrains.com/issue/KT-4749.
                    validForInputFormats = notValidForKotlin,
                    expectedLegacyValue = expectations { common = Long.MIN_VALUE },
                    expectedValue = expectations { common = literalValue(Long.MIN_VALUE) },
                ),
                // Check a simple short with a lower case suffix.
                ValueExample(
                    name = "short",
                    javaType = "short",
                    javaExpression = "32000",
                    kotlinType = "Short",
                    expectedLegacySource = expectations { common = "32000" },
                    expectedLegacyValue =
                        expectations {
                            common = 32000.toShort()

                            attributeValue = 32000
                        },
                    expectedKotlinLegacyValue = expectations { attributeValue = 32000.toShort() },
                    expectedValue = expectations { common = literalValue(32000.toShort()) },
                ),
                // Check a simple string.
                ValueExample(
                    name = "String",
                    javaType = "String",
                    javaExpression = "\"string\"",
                    expectedLegacySource = expectations { common = "\"string\"" },
                    expectedLegacyValue = expectations { common = "string" },
                    expectedValue = expectations { common = literalValue("string") },
                ),
                ValueExample(
                    name = "String escaped",
                    javaType = "String",
                    javaExpression = "\"str\\ning\"",
                    expectedLegacySource = expectations { common = "\"str\\ning\"" },
                    expectedLegacyValue = expectations { common = "str\ning" },
                    expectedValue = expectations { common = literalValue("str\ning") },
                ),
                // Check an empty array.
                ValueExample(
                    name = "array - empty",
                    javaType = "int[]",
                    javaExpression = "{}",
                    kotlinType = "IntArray",
                    kotlinExpression = "[]",
                    // Literal arrays are only allowed in annotations not fields.
                    suitableFor = allLegacyValueUseSitesExceptFields,
                    expectedLegacySource = expectations { common = "{}" },
                    expectedKotlinLegacySource =
                        expectations {
                            attributeValue = "[]"
                            // TODO(b/354633349): Fix this, it should not be an empty string.
                            attributeDefaultValue = ""
                        },
                    expectedLegacyValue = expectations { common = emptyArray<Int>() },
                    expectedValue = expectations { common = arrayValueFromAny() },
                ),
                ValueExample(
                    name = "array - single",
                    javaType = "int[]",
                    javaExpression = "{1}",
                    kotlinType = "IntArray",
                    kotlinExpression = "[1]",
                    // Literal arrays are only allowed in annotations not fields.
                    suitableFor = allLegacyValueUseSitesExceptFields,
                    expectedLegacySource = expectations { common = "{1}" },
                    expectedKotlinLegacySource =
                        expectations {
                            attributeValue = "[1]"
                            attributeDefaultValue = "{1}"
                        },
                    expectedLegacyValue = expectations { common = arrayOf(1) },
                    expectedValue = expectations { common = arrayValueFromAny(1) },
                ),
                // Check a simple string array.
                ValueExample(
                    name = "String array",
                    javaType = "String[]",
                    javaExpression = "{\"string1\", \"string2\"}",
                    kotlinType = "Array<String>",
                    kotlinExpression = "[\"string1\", \"string2\"]",
                    // Literal arrays are only allowed in annotations not fields.
                    suitableFor = allLegacyValueUseSitesExceptFields,
                    expectedLegacySource = expectations { common = "{\"string1\", \"string2\"}" },
                    expectedKotlinLegacySource =
                        expectations { attributeValue = "[\"string1\", \"string2\"]" },
                    expectedLegacyValue = expectations { common = arrayOf("string1", "string2") },
                    expectedValue =
                        expectations { common = arrayValueFromAny("string1", "string2") },
                ),
                // Check passing a single value to an array type.
                ValueExample(
                    name = "String array with single string",
                    javaType = "String[]",
                    javaExpression = "\"string\"",
                    kotlinType = "Array<String>",
                    // Fields that are of type String[] cannot be given a solitary string like an
                    // annotation attribute can.
                    suitableFor = allLegacyValueUseSitesExceptFields,
                    expectedLegacySource =
                        expectations {
                            common = "\"string\""

                            jar { common = "{\"string\"}" }
                        },
                    expectedLegacyValue =
                        expectations {
                            common = arrayOf("string")
                            source { common = "string" }
                        },
                    expectedValue = expectations { common = arrayValueFromAny("string") },
                ),
                ValueExample(
                    name = "String using constant",
                    javaType = "String",
                    javaExpression = "Constants.STRING_CONSTANT",
                    // Must fully qualify most classes in signature files.
                    signatureExpression = "test.pkg.Constants.STRING_CONSTANT",
                    expectedLegacySource =
                        expectations {
                            common = "\"constant\""

                            source {
                                common = "test.pkg.Constants.STRING_CONSTANT"
                                // TODO(b/354633349): Fully qualified is better.
                                attributeValue = "Constants.STRING_CONSTANT"
                                // TODO(b/354633349): Should probably be a field reference, at least
                                //   in some cases.
                                fieldWriteWithSemicolon = "\"constant\""
                            }
                        },
                    expectedLegacyValue = expectations { common = "constant" },
                    expectedValue =
                        expectations {
                            common =
                                fieldReferenceValue(
                                    "test.pkg.Constants",
                                    "STRING_CONSTANT",
                                    literalValue("constant")
                                )
                            jar {
                                // The compiler will always inline a constant field value.
                                common = literalValue("constant")
                            }
                        },
                ),
                ValueExample(
                    name = "method call",
                    javaType = "String",
                    javaExpression = "System.getProperty(\"PROPERTY\")",
                    // Only suitable for use in fields as annotations cannot use non-constant
                    // methods.
                    suitableFor = allFieldLegacyValueUseSites,
                    // Signature never has a method call for a value.
                    validForInputFormats = notValidForSignature,
                    expectedLegacySource = expectations { common = null },
                    expectedLegacyValue = expectations { common = null },
                    expectedValue = expectations { common = null },
                ),
                // Null value
                ValueExample(
                    name = "null",
                    javaType = "String",
                    javaExpression = "null",
                    // Only suitable for use in fields as annotations cannot have `null` values.
                    suitableFor = allFieldLegacyValueUseSites,
                    // Signature never has a null field value.
                    validForInputFormats = notValidForSignature,
                    expectedLegacySource = expectations { common = null },
                    expectedLegacyValue = expectations { common = null },
                    expectedValue = expectations { common = null },
                ),
                // Check a constant value used with a non-constant type that should result in a null
                // value.
                ValueExample(
                    name = "null - constant value with non-constant type",
                    javaType = "java.lang.CharSequence",
                    javaExpression = "\"string\"",
                    // Only suitable for use in fields. Annotations cannot use a CharSequence type.
                    suitableFor = allFieldLegacyValueUseSites,
                    // Signature never has a null field value.
                    validForInputFormats = notValidForSignature,
                    expectedLegacySource = expectations { common = null },
                    expectedLegacyValue = expectations { common = null },
                    expectedValue = expectations { common = null },
                ),
                // Check an expression that results in a primitive value via an intermediate
                // non-constant expression results in a null value.
                ValueExample(
                    name = "null - intermediate non-constant expression",
                    javaType = "int",
                    javaExpression = "(new int[]{1})[0]",
                    kotlinType = "Int",
                    kotlinExpression = "arrayOf(1)[0]",
                    // Only suitable for use in fields. Annotations cannot use a non-constant
                    // expression.
                    suitableFor = allFieldLegacyValueUseSites,
                    // Signature never has a null field value.
                    validForInputFormats = notValidForSignature,
                    expectedLegacySource = expectations { common = null },
                    expectedLegacyValue = expectations { common = null },
                    expectedValue = expectations { common = null },
                ),
                // Check an expression that creates a fixed size array.
                ValueExample(
                    name = "null - fixed array",
                    javaType = "int[]",
                    javaExpression = "new int[0]",
                    kotlinType = "IntArray",
                    kotlinExpression = "emptyArray()",
                    // Only suitable for use in fields. Annotations cannot use `new ...`
                    // expressions.
                    suitableFor = allFieldLegacyValueUseSites,
                    // Signature never has a null field value.
                    validForInputFormats = notValidForSignature,
                    expectedLegacySource = expectations { common = null },
                    expectedLegacyValue = expectations { common = null },
                    expectedValue = expectations { common = null },
                ),
            )

        /**
         * The list of [ValueExample]s that will be tested across [ProducerKind] and
         * [LegacyValueUseSite]s.
         */
        internal val valueExamples =
            allValueExamples
                .filter { it.testThis }
                .let { filtered -> filtered.ifEmpty { allValueExamples } }
    }
}

/**
 * An [Expectation] that will apply [transform] to expected values returned from [delegate] for
 * [LegacyValueUseSite.FIELD_VALUE] and [LegacyValueUseSite.FIELD_WRITE_WITH_SEMICOLON].
 */
class TransformFieldExpectation<T : Any>(
    private val delegate: Expectation<T?>,
    private val transform: (T) -> T? = { it }
) : Expectation<T?> {
    override fun expectationFor(
        producerKind: ProducerKind,
        legacyValueUseSite: LegacyValueUseSite
    ) =
        delegate.expectationFor(producerKind, legacyValueUseSite)?.let { expected ->
            if (legacyValueUseSite.valueUseSite == ValueUseSite.FIELD)
                expected.let { transform(it) }
            else expected
        }

    override fun hasExpectationFor(
        producerKind: ProducerKind,
        legacyValueUseSite: LegacyValueUseSite
    ) =
        if (legacyValueUseSite.valueUseSite == ValueUseSite.FIELD) true
        else delegate.hasExpectationFor(producerKind, legacyValueUseSite)
}
