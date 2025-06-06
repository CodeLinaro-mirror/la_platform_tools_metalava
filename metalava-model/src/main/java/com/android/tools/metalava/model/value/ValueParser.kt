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

import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.javaUnescapeString
import com.android.tools.metalava.model.parser.ParseException
import com.android.tools.metalava.model.parser.TokenPurpose
import com.android.tools.metalava.model.parser.Tokenizer
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.TypeItemParser
import com.android.tools.metalava.reporter.FileLocation
import java.nio.file.Path

/**
 * Parser for the string representation of [Value]s that is used in a signature file or an
 * annotation created from a string.
 */
class ValueParser(
    private val annotationContext: AnnotationContext,
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
     * [attributeName] of [annotationClassName] from [text].
     *
     * @param annotationClassName the containing [AnnotationItem]'s qualified class name.
     * @param attributeName the name of the attribute whose value it will provide.
     * @param text the String value to be parsed.
     */
    private fun providerForAnnotationValue(
        annotationClassName: String,
        attributeName: String,
        text: String
    ) =
        CachingAnnotationValueProvider(this, attributeName, text) {
            annotationContext.resolveClass(annotationClassName)
        }

    override fun implementationValueToModelValue(
        optionalTypeItem: TypeItem?,
        implementationValue: String,
        valueUseSite: ValueUseSite
    ) =
        when (valueUseSite) {
            ValueUseSite.ANNOTATION -> {
                // For annotations convert to any Value.
                parse(optionalTypeItem, implementationValue)
            }
            ValueUseSite.FIELD -> {
                // For fields convert to ConstantValues if possible, otherwise throw an exception.
                parseConstant(optionalTypeItem, implementationValue)
                    ?: unknownToken(optionalTypeItem, implementationValue)
            }
        }

    /** Parse the [text] to provide a [Value] of the [optionalTypeItem]. */
    fun parse(optionalTypeItem: TypeItem?, text: String): Value? =
        when {
            text.isEmpty() -> null
            text[0] == '{' -> {
                // The text looks like it is an array literal that could contain multiple values so
                // it will require splitting into separate parts, so create a Tokenizer to do that.
                val tokenizer = tokenizerOf(text)
                parseWithTokenizer(optionalTypeItem, tokenizer)
            }
            optionalTypeItem is ArrayTypeItem -> {
                // The type is an array so this is an example of not having to add curly braces
                // around a single value in an annotation attribute. Create a value for the
                // component type and then wrap it in an ArrayValue.
                val singleValue = parseArrayElementValue(optionalTypeItem.componentType, text)
                createArrayValue(listOf(singleValue), wasUnwrappedInSource = true)
            }
            else -> {
                parseArrayElementValue(optionalTypeItem, text)
            }
        }

    /** Create a [Tokenizer] of [text]. */
    private fun tokenizerOf(text: String) = Tokenizer(Path.of("unknown"), text.toCharArray())

    /** Parse a [Value] of the [optionalTypeItem] from [tokenizer]. */
    private fun parseWithTokenizer(optionalTypeItem: TypeItem?, tokenizer: Tokenizer) =
        when (val token = tokenizer.requireToken()) {
            "{" -> {
                val componentType = (optionalTypeItem as? ArrayTypeItem)?.componentType
                val elements = buildList {
                    while (true) {
                        // The next token could be the end of the array or a value.
                        val valueToken = tokenizer.requireToken(purpose = TokenPurpose.VALUE)

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
        // Try and parse the constants first as they will be more prevalent and the following code
        // is more expensive.
        parseConstant(optionalTypeItem, text)?.let {
            return it
        }

        // If the text matches the pattern then extract the `<type>`, parse using `typeItemParser`
        // and wrap in a `ClassObjectValue`.
        classLiteralPattern.matchEntire(text)?.let { matchResult ->
            // Get the type string. The pattern requires it so it is safe to assume it is available.
            val typeString = matchResult.groups[TYPE_GROUP_INDEX]!!.value
            val classLiteralTypeItem =
                typeItemParser.obtainTypeFromString(typeString, TypeParameterScope.empty)
            return createClassObjectValue(classLiteralTypeItem, text)
        }

        // Check to see if it looks like a field reference.
        fieldReferencePattern.matchEntire(text)?.let { matchResult ->

            // Get the class and field. The class name is optional but the field name is required so
            // it is safe to assume it is available.
            val className = matchResult.groups[CLASS_NAME_GROUP_INDEX]?.value ?: ""
            val fieldName = matchResult.groups[FIELD_NAME_GROUP_INDEX]!!.value

            // If there was an explicit conversion function call on the field reference then make
            // sure to track that.
            val explicitConversionTo =
                matchResult.groups[OPTIONAL_CONVERSION_FUNCTION_NAME_GROUP_INDEX]?.value?.let {
                    conversionFunctionName ->
                    PrimitiveTypeItem.Primitive.forKotlinNumericConversionFunctionName(
                        conversionFunctionName
                    )
                }

            // Parse the class name to a type.
            val classTypeItem =
                typeItemParser.obtainTypeFromString(
                    className,
                    TypeParameterScope.empty,
                    ContextNullability.forceNonNull,
                ) as ClassTypeItem

            val qualifiedClassName = classTypeItem.qualifiedName
            return createFieldReferenceValueWithDeferredConstantValue(
                annotationContext,
                qualifiedClassName,
                fieldName,
                optionalTypeItem,
                explicitConversionTo = explicitConversionTo,
            )
        }

        // Handle a Java style annotation value which starts with an '@'.
        val first = text.first()
        if (first == '@') {
            parseAnnotationValue(text)?.let {
                return it
            } ?: unknownToken(optionalTypeItem, text)
        }

        // Check to see if it is a Kotlin annotation value, which looks like a constructor call for
        // the annotation class.
        annotationConstructorPattern.matchAt(text, 0)?.let {
            parseAnnotationValue(text)?.let {
                return it
            } ?: unknownToken(optionalTypeItem, text)
        }

        unknownToken(optionalTypeItem, text)
    }

    /** Parse the [text] to provide a [ConstantValue] of the [optionalTypeItem]. */
    private fun parseConstant(optionalTypeItem: TypeItem?, text: String): ConstantValue? {

        knownSpecialValues[text]?.let { value ->
            return value.convertToType(optionalTypeItem)
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

        return null
    }

    /** Throw an exception when [text] cannot be parsed. */
    private fun unknownToken(optionalTypeItem: TypeItem?, text: String): Nothing =
        throw ValueProviderException("Unknown token <$text> of $optionalTypeItem")

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
            // Check for a binary exponent as that means it is a hex floating point number.
            if (text.any { it == 'p' || it == 'P' }) {
                // Floating point hex value.
                val last = text.last()
                val number =
                    if (last == 'f') {
                        text.substring(0, text.length - 1).toFloat()
                    } else {
                        text.toDouble()
                    }
                return createLiteralValue(
                    optionalTypeItem,
                    number,
                    // Hexadecimal floating point numbers can only be present in the signature file
                    // if they were present in the source.
                    nonLiteralInSource = false,
                )
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
            return createLiteralValue(
                optionalTypeItem,
                int,
                // AnnotationItem.toSource() will use format ints obtained from literals as decimals
                // and ints obtained from complex expressions as decimals so treat hexadecimals as
                // if they are not literals. That should allow signature files to be read and then
                // written out again without changing the formatting.
                nonLiteralInSource = true
            )
        }

        // Check the last character to see if it indicated the type of the number.
        when (val suffix = text.last()) {
            'L',
            'l' -> {
                val long = text.substring(0, text.length - 1).toLong()
                return createLiteralValue(optionalTypeItem, long)
            }
            'F',
            'f' -> {
                val float = text.substring(0, text.length - 1).toFloat()
                // AnnotationItem.toSource() uses 'F' as the suffix for floats obtained from
                // expressions and 'f' for those obtained from literals.
                val nonLiteralInSource = suffix == 'F'
                return createLiteralValue(optionalTypeItem, float, nonLiteralInSource)
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

    /** Parse [text] to produce an [AnnotationValue], if possible. */
    private fun parseAnnotationValue(text: String): AnnotationValue? {
        val annotationItem = parseAnnotationItem(text) ?: return null
        return createAnnotationValue(annotationItem)
    }

    /** Parse [text] to produce an [AnnotationItem], if possible. */
    fun parseAnnotationItem(text: String): AnnotationItem? {
        val tokenizer = tokenizerOf(text)

        // Parse the annotation item from the tokenizer.
        val annotationItem = parseAnnotationItem(tokenizer)

        // Make sure that all the significant text was consumed.
        tokenizer.getToken()?.let { token ->
            error(
                "Expected to consume all the contents of `$text` but did not, next token is '$token', remainder is '${tokenizer.remainder()}'"
            )
        }

        return annotationItem
    }

    /**
     * Parse stream of tokens produced by [tokenizer] to create an [AnnotationItem], if possible.
     *
     * On entry [tokenizer] next token must be the annotation's class name, optionally prefixed with
     * an `@`. On exit, the next token will be the one after the annotation.
     */
    private fun parseAnnotationItem(tokenizer: Tokenizer): AnnotationItem? {
        // May start with an '@', the remainder is the annotation class name.
        val annotationClassName =
            tokenizer.requireToken().let { token ->
                if (token[0] == '@') token.substring(1) else token
            }

        val token = tokenizer.getToken()
        val attributes =
            when (token) {
                "(" -> {
                    parseAnnotationAttributes(annotationClassName, tokenizer).also {
                        require(tokenizer.current == ")") {
                            "Expected ')' but found ${tokenizer.current}"
                        }
                    }
                }
                else -> emptyList()
            }

        return DefaultAnnotationItem.createWithAttributes(
            annotationContext,
            FileLocation.UNKNOWN,
            annotationClassName,
            attributes
        )
    }

    /**
     * Parse stream of tokens produced by [tokenizer] to create a list of [AnnotationAttribute]s for
     * [annotationClassName].
     *
     * On entry [tokenizer]'s [Tokenizer.current] must be `(`. On exit, it will be the matching `)`.
     */
    private fun parseAnnotationAttributes(
        annotationClassName: String,
        tokenizer: Tokenizer
    ): List<AnnotationAttribute> {
        require(tokenizer.current == "(") { "Expected '(' but found ${tokenizer.current}" }

        // At this point there are a number of possibilities:
        // * ")", i.e. close parenthesis for an empty list of annotation attributes.
        // * <any value> (which is equivalent to 'value=<any value>').
        // * <attribute-name>=<any value>.
        tokenizer.requireToken(purpose = TokenPurpose.VALUE).let { token ->
            // A minor optimization to avoid creating a new mutable list only for it to be empty.
            if (token == ")") return emptyList()
        }

        return buildList {
            do {
                // At this point there are three possibilities for the token:
                // * `)` - closing of the attribute list after a trailing `,`.
                // * <any value> (which is equivalent to 'value=<any value>').
                // * <attribute-name>=<any value>.
                val token = tokenizer.current
                if (token == ")") return@buildList

                // Differentiate between them by checking the next token. If it is = then it is
                // the second option, otherwise it is the first. After this the tokenizer.current
                // must be the next token after the value.
                val nextToken = tokenizer.requireToken()
                val (attributeName, valueText) =
                    if (nextToken == "=") {
                        // Get the next token as a value.
                        val valueToken = tokenizer.requireToken(purpose = TokenPurpose.VALUE)
                        // Get the next token ready in the tokenizer.
                        tokenizer.requireToken()
                        // Pair up the attribute name and value text.
                        token to valueToken
                    } else {
                        // Pair up the default attribute name and the value text.
                        ANNOTATION_ATTR_VALUE to token
                    }

                // Patch the value if necessary.
                val patchedValue =
                    if (tokenizer.current == "-") {
                        // TODO(b/354633349): Temporary workaround that is needed because some
                        //  historical files from `prebuilts/sdk` have expressions like
                        //  `0x400000000 - 1`. Those files have been fixed downstream but the
                        //  `prebuilts/sdk` repository is not modifiable in aosp/metalava-main.
                        val expectingOne =
                            tokenizer.requireToken(purpose = TokenPurpose.VALUE) == "1"
                        require(expectingOne) {
                            """Expected "... - 1" but found "... - $expectingOne""""
                        }

                        // Get the next token ready in the tokenizer.
                        tokenizer.requireToken()

                        (Integer.decode(valueText) - 1).toString()
                    } else valueText

                // At this point there are two possibilities:
                // * ",", i.e. the separator between this and the next attribute.
                // * "," followed by ")", i.e. an unnecessary comma following by the closing
                //   parenthesis of the attribute list.
                // * ")", i.e. close parenthesis for the list of annotation attributes.
                when (val separator = tokenizer.current) {
                    "," -> {
                        // Get the next token which should be a value ready in the tokenizer but
                        // could also be a close parenthesis.
                        tokenizer.requireToken(purpose = TokenPurpose.VALUE)
                    }
                    ")" -> {
                        // Nothing to do, will break out next time around the loop but this case
                        // allows the else clause to throw an error.
                    }
                    else ->
                        throw ValueProviderException(
                            "Unknown token <$separator>, expected one of `,` or `)`"
                        )
                }

                // Get Value provider.
                val valueProvider =
                    providerForAnnotationValue(
                        annotationClassName,
                        attributeName,
                        patchedValue,
                    )

                // Add the attribute to the list.
                add(
                    DefaultAnnotationAttribute(
                        attributeName,
                        valueProvider,
                    )
                )
            } while (true)
        }
    }

    companion object {
        /** The default instance of this. */
        val DEFAULT =
            ValueParser(
                // Any attempts to resolve an annotation's class in order to determine the type of
                // its attributes will return null which will prevent any conversion of values to
                // the correct type but still allow annotations to be parsed correctly.
                AnnotationContext.DEFAULT_RESOLVE_NULL,
                TypeItemParser.forValueParser(ClassResolver.THROWING),
            )

        /**
         * Map of all the different string representations of various special floating point
         * numbers.
         */
        private val specialFloats =
            mapOf(
                DoubleValue.NaN to
                    listOf(
                        "(0.0/0.0)",
                        "0.0 / 0.0",
                        "Double.NaN",
                        "java.lang.Double.NaN",
                        "kotlin.jvm.internal.DoubleCompanionObject.NaN",
                    ),
                DoubleValue.NEGATIVE_INFINITY to
                    listOf(
                        "(-1.0/0.0)",
                        "-1.0 / 0.0",
                        "Double.NEGATIVE_INFINITY",
                        "java.lang.Double.NEGATIVE_INFINITY",
                        "kotlin.jvm.internal.DoubleCompanionObject.NEGATIVE_INFINITY",
                    ),
                DoubleValue.POSITIVE_INFINITY to
                    listOf(
                        "(1.0/0.0)",
                        "1.0 / 0.0",
                        "Double.POSITIVE_INFINITY",
                        "java.lang.Double.POSITIVE_INFINITY",
                        "kotlin.jvm.internal.DoubleCompanionObject.POSITIVE_INFINITY",
                    ),
                FloatValue.NaN to
                    listOf(
                        "(0.0f/0.0f)",
                        "0.0f / 0.0",
                        "Float.NaN",
                        "java.lang.Float.NaN",
                        "kotlin.jvm.internal.FloatCompanionObject.NaN",
                    ),
                FloatValue.NEGATIVE_INFINITY to
                    listOf(
                        "(-1.0f/0.0f)",
                        "-1.0f / 0.0",
                        "-1.0F / 0.0",
                        "Float.NEGATIVE_INFINITY",
                        "java.lang.Float.NEGATIVE_INFINITY",
                        "kotlin.jvm.internal.FloatCompanionObject.NEGATIVE_INFINITY",
                    ),
                FloatValue.POSITIVE_INFINITY to
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
                "false" to BooleanValue.FALSE,
                "true" to BooleanValue.TRUE,
            ) + specialFloats.flatMap { (value, alternatives) -> alternatives.map { it to value } }

        /**
         * Pattern to match a class literal of the following forms:
         * * <type>.class - Java form.
         * * <type>::class - Kotlin form used in annotations.
         * * <type>::class.java - Kotlin form used in fields. It matches this for legacy reasons but
         *   fields should not be class literals as they are not constants.
         *
         * Where `<type>` can be a primitive, class type or array type.
         *
         * The pattern matches a possibly qualified identifier, followed by type information like a
         * type argument list (e.g. in `java.util.List<*>`, or `Array<String>`) or array dimensions
         * (e.g. `int[]`) following by either `::class`, `::class.java` or `.class`.
         */
        internal val classLiteralPattern =
            Regex("""(([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*)[^:.]*)(::class(\.java)?|\.class)""")

        /** Index of type group in [classLiteralPattern]. */
        private const val TYPE_GROUP_INDEX = 1

        /**
         * Pattern to match a field, including a class literal of the form `<class>.class` and an
         * unqualified field of the form `FIELD`.
         *
         * This also matches an optional call to a numeric conversion function, e.g. `Int.toLong()`.
         */
        internal val fieldReferencePattern =
            Regex(
                """(?:([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*)\.)?([a-zA-Z0-9_]+)(?:\.(to(?:Byte|Double|Float|Int|Long|Short))\(\))?"""
            )

        /** Index of class name group in [fieldReferencePattern]. */
        private const val CLASS_NAME_GROUP_INDEX = 1

        /** Index of field name group in [fieldReferencePattern]. */
        private const val FIELD_NAME_GROUP_INDEX = 2

        /** Index of optional conversion function name group in [fieldReferencePattern]. */
        private const val OPTIONAL_CONVERSION_FUNCTION_NAME_GROUP_INDEX = 3

        /**
         * Pattern to match a Kotlin style annotation value which looks like a constructor call for
         * the annotation's class.
         */
        internal val annotationConstructorPattern =
            Regex("""([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)*)\(""")
    }
}
