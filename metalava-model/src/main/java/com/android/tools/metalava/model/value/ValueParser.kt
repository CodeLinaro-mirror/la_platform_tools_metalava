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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.javaUnescapeString
import com.android.tools.metalava.model.type.TypeItemParser

/**
 * Parser for the string representation of [Value]s that is used in a signature file or an
 * annotation created from a string.
 */
class ValueParser(
    private val typeItemParser: TypeItemParser,
) : ValueFactory, ImplementationValueToModelFactory<String> {

    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] of [typeItem] from
     * [text].
     *
     * @param typeItem the required type for the value, e.g. [MethodItem.returnType] or
     *   [FieldItem.type].
     * @param text the String value to be parsed.
     */
    fun providerFor(typeItem: TypeItem, text: String): CombinedValueProvider =
        CachingValueProvider(this, typeItem, text)

    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] for attribute
     * [attributeName] of [annotationItem] from [text].
     *
     * @param annotationItem the containing [AnnotationItem].
     * @param attributeName the name of the attribute whose value it will provide.
     * @param text the String value to be parsed.
     */
    fun providerForAnnotationValue(
        annotationItem: AnnotationItem,
        attributeName: String,
        text: String
    ): CombinedValueProvider =
        CachingAnnotationValueProvider(
            this,
            annotationItem,
            attributeName,
            text,
        )

    override fun implementationValueToModelValue(
        optionalTypeItem: TypeItem?,
        implementationValue: String
    ) = parse(optionalTypeItem, implementationValue)

    /** Parse the [text] to provide a [Value] of the [optionalTypeItem]. */
    fun parse(optionalTypeItem: TypeItem?, text: String): Value? {
        if (text.isEmpty()) return null
        knownSpecialValues[text]?.let { value ->
            return createLiteralValue(optionalTypeItem, value)
        }

        val first = text.first()
        when {
            first == '"' -> {
                val last = text.last()
                if (last != '"') error("string '$text' starts with \" but does not end with \"")
                val string = javaUnescapeString(text.substring(1, text.length - 1))
                return createLiteralValue(optionalTypeItem, string)
            }
            first == '\'' -> {
                val last = text.last()
                if (last != '\'') error("character \"$text\" starts with ' but does not end with '")
                val string = javaUnescapeString(text.substring(1, text.length - 1))
                if (string.length != 1)
                    error(
                        "character \"$text\" should contain a single character but contains ${string.length}"
                    )
                val char = string[0]
                return createLiteralValue(optionalTypeItem, char)
            }
            first == '+' -> {
                return parseNumber(optionalTypeItem, text.substring(1), UnaryOperator.PLUS)
            }
            first == '-' -> {
                return parseNumber(optionalTypeItem, text.substring(1), UnaryOperator.MINUS)
            }
            first.isDigit() -> return parseNumber(optionalTypeItem, text, null)
        }

        // If the text ends with `.class` then it is a class literal of the form `<type>.class` so
        // remove the `.class` suffix and then parse the remaining `<type>` using `typeItemParser`.
        text.removeSuffix(".class").let { typeString ->
            if (typeString != text) {
                val classLiteralTypeItem =
                    typeItemParser.obtainTypeFromString(typeString, TypeParameterScope.empty)
                return createClassObjectValue(classLiteralTypeItem)
            }
        }

        throw ValueProviderException("Unknown token <$text> of $optionalTypeItem")
    }

    /** The possible unary operators that may appear at the start of a number. */
    private enum class UnaryOperator(val text: String) {
        PLUS("+"),
        MINUS("-"),
    }

    /** Apply this optional [UnaryOperator] to [magnitude]. */
    private fun UnaryOperator?.evaluate(magnitude: Long) =
        if (this == UnaryOperator.MINUS) -magnitude else magnitude

    /** Apply this optional [UnaryOperator] to [magnitude]. */
    private fun UnaryOperator?.evaluate(magnitude: Float) =
        if (this == UnaryOperator.MINUS) -magnitude else magnitude

    /** Apply this optional [UnaryOperator] to [magnitude]. */
    private fun UnaryOperator?.evaluate(magnitude: Double) =
        if (this == UnaryOperator.MINUS) -magnitude else magnitude

    /**
     * Parse a number from [text].
     *
     * @param optionalTypeItem the optional [TypeItem], if present then the parsed value will be
     *   converted to be appropriate for this [TypeItem].
     * @param text the text to parse.
     * @param unaryOperator the optional [UnaryOperator] to apply after parsing.
     */
    private fun parseNumber(
        optionalTypeItem: TypeItem?,
        text: String,
        unaryOperator: UnaryOperator?,
    ): Value {
        // Handle hexadecimal numbers first as they could end with a 'f' which would be treated as
        // a float below.
        if (text.startsWith("0x")) {
            require(unaryOperator == null) {
                "Hexadecimal values cannot have a leading sign character but has '${unaryOperator!!.text}'"
            }
            // Remove the leading "0x"
            val withoutLeading0x = text.substring(2)

            // Parse as long as a number like 0xFFFFFFFF is parsed as a positive number and will
            // fail because the largest positive int is 0x80000000. So, parse as long and then cast
            // down to an int. That is done explicitly here rather than rely on the casting done by
            // createLiteralValue(...) as it will fail because this cast will be lossy for numbers
            // larger than the largest positive int. They will become negative numbers. However,
            // that is what the original number was so it is ok.
            val int = withoutLeading0x.toLong(16).toInt()
            return createLiteralValue(optionalTypeItem, int)
        }

        // Check the last character to see if it indicated the type of the number.
        when (text.last()) {
            'L',
            'l' -> {
                val magnitude = text.substring(0, text.length - 1).toLong()
                val withSign = unaryOperator.evaluate(magnitude)
                return createLiteralValue(optionalTypeItem, withSign)
            }
            'F',
            'f' -> {
                val magnitude = text.substring(0, text.length - 1).toFloat()
                val withSign = unaryOperator.evaluate(magnitude)
                return createLiteralValue(optionalTypeItem, withSign)
            }
        }

        // Try parsing as a long first. This will cover bytes, ints, longs, and shorts.
        text.toLongOrNull()?.let { longMagnitude ->
            val longWithSign = unaryOperator.evaluate(longMagnitude)
            // Cast down to an int if allowed as an integer number without a trailing L or l is
            // treated as an integer in source.
            if (longWithSign in Int.MIN_VALUE..Int.MAX_VALUE) {
                return createLiteralValue(optionalTypeItem, longWithSign.toInt())
            } else {
                // Otherwise, rely on createLiteralValue(...) to do appropriate non-lossy casting to
                // match the optional type item.
                return createLiteralValue(optionalTypeItem, longWithSign)
            }
        }

        // Try parsing as a double. This will cover floats too.
        text.toDoubleOrNull()?.let { doubleMagnitude ->
            val doubleWithSign = unaryOperator.evaluate(doubleMagnitude)
            if (
                optionalTypeItem is PrimitiveTypeItem &&
                    optionalTypeItem.kind == PrimitiveTypeItem.Primitive.FLOAT
            ) {
                return createLiteralValue(optionalTypeItem, doubleWithSign.toFloat())
            } else {
                return createLiteralValue(optionalTypeItem, doubleWithSign)
            }
        }

        throw ValueProviderException("Unsupported numeric value <$text> of $optionalTypeItem")
    }

    companion object {
        /** The default instance of this. */
        val DEFAULT = ValueParser(TypeItemParser.forValueParser(ClassResolver.THROWING))

        /**
         * Map of all the different string representations of various special floating point
         * numbers.
         */
        private val specialFloats =
            mapOf(
                Double.NaN to
                    listOf(
                        "(0.0/0.0)",
                        "0.0 / 0.0",
                        "Double.NaN",
                        "java.lang.Double.NaN",
                        "kotlin.jvm.internal.DoubleCompanionObject.NaN",
                    ),
                Double.NEGATIVE_INFINITY to
                    listOf(
                        "(-1.0/0.0)",
                        "-1.0 / 0.0",
                        "Double.NEGATIVE_INFINITY",
                        "java.lang.Double.NEGATIVE_INFINITY",
                        "kotlin.jvm.internal.DoubleCompanionObject.NEGATIVE_INFINITY",
                    ),
                Double.POSITIVE_INFINITY to
                    listOf(
                        "(1.0/0.0)",
                        "1.0 / 0.0",
                        "Double.POSITIVE_INFINITY",
                        "java.lang.Double.POSITIVE_INFINITY",
                        "kotlin.jvm.internal.DoubleCompanionObject.POSITIVE_INFINITY",
                    ),
                Float.NaN to
                    listOf(
                        "(0.0f/0.0f)",
                        "0.0f / 0.0",
                        "Float.NaN",
                        "java.lang.Float.NaN",
                        "kotlin.jvm.internal.FloatCompanionObject.NaN",
                    ),
                Float.NEGATIVE_INFINITY to
                    listOf(
                        "(-1.0f/0.0f)",
                        "-1.0f / 0.0",
                        "-1.0F / 0.0",
                        "Float.NEGATIVE_INFINITY",
                        "java.lang.Float.NEGATIVE_INFINITY",
                        "kotlin.jvm.internal.FloatCompanionObject.NEGATIVE_INFINITY",
                    ),
                Float.POSITIVE_INFINITY to
                    listOf(
                        "(1.0f/0.0f)",
                        "1.0f / 0.0",
                        "Float.POSITIVE_INFINITY",
                        "java.lang.Float.POSITIVE_INFINITY",
                        "kotlin.jvm.internal.FloatCompanionObject.POSITIVE_INFINITY",
                    ),
            )

        /** A map of all the known special values. */
        val knownSpecialValues =
            mapOf(
                "false" to false,
                "true" to true,
            ) + specialFloats.flatMap { (value, alternatives) -> alternatives.map { it to value } }
    }
}
