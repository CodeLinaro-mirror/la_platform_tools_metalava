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
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.javaUnescapeString
import com.android.tools.metalava.model.parser.ParseException
import com.android.tools.metalava.model.parser.Tokenizer
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.TypeItemParser
import java.nio.file.Path

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
     * @param valueUseSite the [ValueUseSite] for which this will provide a [Value].
     */
    fun providerFor(
        typeItem: TypeItem,
        text: String,
        valueUseSite: ValueUseSite,
    ): CombinedValueProvider = CachingValueProvider(this, typeItem, text, valueUseSite)

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
        implementationValue: String,
        valueUseSite: ValueUseSite
    ) = parse(optionalTypeItem, implementationValue)

    /** Parse the [text] to provide a [Value] of the [optionalTypeItem]. */
    fun parse(optionalTypeItem: TypeItem?, text: String): Value? =
        when {
            text.isEmpty() -> null
            text[0] == '{' -> {
                // The text looks like it is an array literal that could contain multiple values so
                // it will require splitting into separate parts, so create a Tokenizer to do that.
                val tokenizer = Tokenizer(Path.of("unknown"), text.toCharArray())
                parseWithTokenizer(optionalTypeItem, tokenizer)
            }
            optionalTypeItem is ArrayTypeItem -> {
                // The type is an array so this is an example of not having to add curly braces
                // around a
                // single value in an annotation attribute. Create a value for the component type
                // and
                // then wrap it in an ArrayValue.
                val singleValue = parseArrayElementValue(optionalTypeItem.componentType, text)
                createArrayValue(listOf(singleValue))
            }
            else -> {
                parseArrayElementValue(optionalTypeItem, text)
            }
        }

    /** Parse a [Value] of the [optionalTypeItem] from [tokenizer]. */
    private fun parseWithTokenizer(optionalTypeItem: TypeItem?, tokenizer: Tokenizer) =
        when (val token = tokenizer.requireToken()) {
            "{" -> {
                val componentType = (optionalTypeItem as? ArrayTypeItem)?.componentType
                val elements = buildList {
                    while (true) {
                        // The next token could be the end of the array or a value.
                        // TODO(b/354633349): Handle annotations in arrays.
                        val valueToken = tokenizer.requireToken()

                        // If it is the end of the array (because the array is empty) then break
                        // out.
                        if (valueToken == "}") break

                        // Parse the token as a value and add it to the list.
                        val element = parseArrayElementValue(componentType, valueToken)
                        add(element)

                        // The next token should be either a `,` or a `}`.
                        when (val separator = tokenizer.requireToken()) {
                            "," -> continue
                            "}" -> break
                            else ->
                                throw ParseException("Expected ',' or '}' but found '$separator'")
                        }
                    }
                }
                createArrayValue(elements)
            }
            else -> {
                throw ParseException("Expected '{' but found '$token'")
            }
        }

    /** Parse the [text] to provide an [ArrayElementValue] of the [optionalTypeItem]. */
    private fun parseArrayElementValue(
        optionalTypeItem: TypeItem?,
        text: String
    ): ArrayElementValue {
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
            first == '+' || first == '-' || first.isDigit() ->
                return parseNumber(optionalTypeItem, text)
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

        // Check to see if it looks like a field reference.
        fieldReferencePattern.matchEntire(text)?.let { matchResult ->

            // Get the class and field. The pattern requires both so it is safe to assume they are
            // both available.
            val className = matchResult.groups[CLASS_NAME_GROUP_INDEX]!!.value
            val fieldName = matchResult.groups[FIELD_NAME_GROUP_INDEX]!!.value

            // Parse the class name to a type.
            val classTypeItem =
                typeItemParser.obtainTypeFromString(
                    className,
                    TypeParameterScope.empty,
                    ContextNullability.forceNonNull,
                ) as ClassTypeItem

            // Resolve the class type item to a ClassItem and find its FieldItem. If no such
            // FieldItem exists then assume it is an enum constant as without a field there is no
            // constant value.
            return classTypeItem
                .asClass()
                // Search through the super class and interface hierarchy to find the field.
                ?.findField(
                    fieldName,
                    includeSuperClasses = true,
                    includeInterfaces = true,
                )
                ?.let { fieldItem -> createFieldReferenceValue(fieldItem) }
                ?: createEnumConstantValue(classTypeItem, fieldName)
        }

        throw ValueProviderException("Unknown token <$text> of $optionalTypeItem")
    }

    /**
     * Parse a number from [text].
     *
     * @param optionalTypeItem the optional [TypeItem], if present then the parsed value will be
     *   converted to be appropriate for this [TypeItem].
     * @param text the text to parse.
     */
    private fun parseNumber(
        optionalTypeItem: TypeItem?,
        text: String,
    ): ConstantValue {
        // Handle hexadecimal numbers first as they could end with a 'f' which would be treated as
        // a float below.
        if (text.startsWith("0x")) {
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
                val long = text.substring(0, text.length - 1).toLong()
                return createLiteralValue(optionalTypeItem, long)
            }
            'F',
            'f' -> {
                val float = text.substring(0, text.length - 1).toFloat()
                return createLiteralValue(optionalTypeItem, float)
            }
        }

        // Try parsing as a long first. This will cover bytes, ints, longs, and shorts.
        text.toLongOrNull()?.let { long ->
            // Cast down to an int if allowed as an integer number without a trailing L or l is
            // treated as an integer in source.
            if (long in Int.MIN_VALUE..Int.MAX_VALUE) {
                return createLiteralValue(optionalTypeItem, long.toInt())
            } else {
                // Otherwise, rely on createLiteralValue(...) to do appropriate non-lossy casting to
                // match the optional type item.
                return createLiteralValue(optionalTypeItem, long)
            }
        }

        // Try parsing as a double. This will cover floats too.
        text.toDoubleOrNull()?.let { double ->
            if (
                optionalTypeItem is PrimitiveTypeItem &&
                    optionalTypeItem.kind == PrimitiveTypeItem.Primitive.FLOAT
            ) {
                return createLiteralValue(optionalTypeItem, double.toFloat())
            } else {
                return createLiteralValue(optionalTypeItem, double)
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
        private val knownSpecialValues =
            mapOf(
                "false" to false,
                "true" to true,
            ) + specialFloats.flatMap { (value, alternatives) -> alternatives.map { it to value } }

        /** Pattern to match a field, including a class literal of the form `<class>.class`. */
        internal val fieldReferencePattern =
            Regex("""([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*)\.([a-zA-Z0-9_]+)""")

        /** Index of class name group in [fieldReferencePattern]. */
        private const val CLASS_NAME_GROUP_INDEX = 1

        /** Index of field name group in [fieldReferencePattern]. */
        private const val FIELD_NAME_GROUP_INDEX = 2
    }
}
