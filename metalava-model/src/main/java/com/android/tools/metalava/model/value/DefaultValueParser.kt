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

package com.android.tools.metalava.model.value

import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.javaUnescapeString
import com.android.tools.metalava.model.parser.ParseException
import com.android.tools.metalava.model.parser.SharedLexer
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.TypeItemParser
import com.android.tools.metalava.reporter.FileLocation

/**
 * Recursive-descent parser for [Value]s and [AnnotationItem]s that consumes [Token]s from a
 * [TokenStream] produced by [SharedLexer] (or `SignatureFileLexer`).
 */
class DefaultValueParser(
    private val annotationContext: AnnotationContext,
    private val typeItemParser: TypeItemParser,
) : ValueParser, ValueFactory, ImplementationValueToModelFactory<String> {

    override fun providerFor(
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
        text: String,
    ) =
        CachingAnnotationValueProvider(this, attributeName, text) {
            annotationContext.resolveClass(annotationClassName)
        }

    override fun implementationValueToModelValue(
        optionalTypeItem: TypeItem?,
        implementationValue: String,
        valueUseSite: ValueUseSite,
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

    override fun parse(optionalTypeItem: TypeItem?, text: String): Value? {
        if (text.isEmpty()) return null
        val tokens = SharedLexer(text).tokenize()
        return parseFromStream(
            optionalTypeItem,
            tokens,
            text,
            expectEndOfStream = true,
        )
    }

    /**
     * Parse a [Value] of the [optionalTypeItem] directly from [tokens] (backed by [sourceText]).
     *
     * @param optionalTypeItem the optional target [TypeItem] for the value.
     * @param tokens the [TokenStream] positioned at the start of the value.
     * @param sourceText the full source text backing [tokens].
     * @param expectEndOfStream if `true`, requires that the parsed value consumes all tokens up to
     *   [SharedTokenType.EOF].
     */
    fun parseFromStream(
        optionalTypeItem: TypeItem?,
        tokens: TokenStream,
        sourceText: String,
        expectEndOfStream: Boolean = false,
    ): Value? =
        when {
            tokens.peekType() == SharedTokenType.EOF -> null
            tokens.peekType() == SharedTokenType.BRACE_OPEN -> {
                parseArrayValue(optionalTypeItem, tokens, sourceText, expectEndOfStream)
            }
            optionalTypeItem is ArrayTypeItem -> {
                // The type is an array so this is an example of not having to add curly braces
                // around a single value in an annotation attribute. Create a value for the
                // component type and then wrap it in an ArrayValue.
                val singleValue =
                    parseArrayElementValue(
                        optionalTypeItem.componentType,
                        tokens,
                        sourceText,
                        expectEndOfStream = expectEndOfStream,
                    )
                createArrayValue(listOf(singleValue), wasUnwrappedInSource = true)
            }
            else -> {
                parseArrayElementValue(
                    optionalTypeItem,
                    tokens,
                    sourceText,
                    expectEndOfStream = expectEndOfStream,
                )
            }
        }

    /** Parse a `{ ... }` [ArrayValue] of the [optionalTypeItem] from [tokens]. */
    private fun parseArrayValue(
        optionalTypeItem: TypeItem?,
        tokens: TokenStream,
        sourceText: String,
        expectEndOfStream: Boolean,
    ): ArrayValue {
        val openToken = tokens.consume()
        if (openToken.type != SharedTokenType.BRACE_OPEN) {
            throw ParseException("Expected '{' but found '${openToken.text(sourceText)}'")
        }

        val componentType = (optionalTypeItem as? ArrayTypeItem)?.componentType
        val elements =
            if (tokens.match(SharedTokenType.BRACE_CLOSE)) {
                emptyList()
            } else {
                buildList {
                    while (!tokens.match(SharedTokenType.BRACE_CLOSE)) {
                        val element =
                            parseArrayElementValue(
                                componentType,
                                tokens,
                                sourceText,
                                expectEndOfStream = false,
                            )
                        add(element)

                        when (tokens.peekType()) {
                            SharedTokenType.COMMA -> {
                                tokens.consume()
                            }
                            SharedTokenType.BRACE_CLOSE -> {
                                tokens.consume()
                                break
                            }
                            else -> {
                                val separator = tokens.peek().text(sourceText)
                                throw ParseException("Expected ',' or '}' but found '$separator'")
                            }
                        }
                    }
                }
            }

        if (expectEndOfStream && tokens.peekType() != SharedTokenType.EOF) {
            unknownToken(optionalTypeItem, sourceText)
        }

        return createArrayValue(elements)
    }

    /** Parse an [ArrayElementValue] of the [optionalTypeItem] from [tokens]. */
    private fun parseArrayElementValue(
        optionalTypeItem: TypeItem?,
        tokens: TokenStream,
        sourceText: String,
        expectEndOfStream: Boolean,
    ): ArrayElementValue {
        val peekType = tokens.peekType()
        return when {
            peekType == SharedTokenType.STRING_LITERAL -> {
                val token = tokens.consume()
                if (expectEndOfStream && tokens.peekType() != SharedTokenType.EOF) {
                    unknownToken(optionalTypeItem, sourceText)
                }
                parseStringLiteral(optionalTypeItem, token.text(sourceText))
            }
            peekType == SharedTokenType.CHAR_LITERAL -> {
                val token = tokens.consume()
                if (expectEndOfStream && tokens.peekType() != SharedTokenType.EOF) {
                    unknownToken(optionalTypeItem, sourceText)
                }
                parseCharLiteral(optionalTypeItem, token.text(sourceText))
            }
            peekType == SharedTokenType.PAREN_OPEN ||
                peekType == SharedTokenType.PLUS ||
                peekType == SharedTokenType.MINUS ||
                peekType == SharedTokenType.NUMBER_LITERAL -> {
                val constant =
                    parseNumberOrSpecialFloatDivision(optionalTypeItem, tokens, sourceText)
                        ?: unknownToken(optionalTypeItem, sourceText)
                if (expectEndOfStream && tokens.peekType() != SharedTokenType.EOF) {
                    unknownToken(optionalTypeItem, sourceText)
                }
                constant
            }
            peekType == SharedTokenType.AT -> {
                val annotationItem =
                    parseAnnotationItem(tokens, sourceText, unshorten = false)
                        ?: unknownToken(optionalTypeItem, sourceText)
                if (expectEndOfStream) {
                    ensureEndOfStreamForAnnotation(tokens, sourceText)
                }
                createAnnotationValue(annotationItem)
            }
            peekType.canBeIdentifier -> {
                parseIdentifierLedElementValue(
                    optionalTypeItem,
                    tokens,
                    sourceText,
                    expectEndOfStream = expectEndOfStream,
                )
            }
            else -> unknownToken(optionalTypeItem, sourceText)
        }
    }

    /**
     * Parses an identifier-led [ArrayElementValue] from [tokens]:
     * - Boolean literals (`true`, `false`)
     * - Named special float constants (`Double.NaN`, `java.lang.Double.NaN`, etc.)
     * - Class literals (`<type>.class`, `<type>::class`, `<type>::class.java`)
     * - Kotlin-style annotation constructor calls (`QualifiedName(...)`)
     * - Field references (`(QualifiedClassName.)?FieldName(.toXxx())?`)
     */
    private fun parseIdentifierLedElementValue(
        optionalTypeItem: TypeItem?,
        tokens: TokenStream,
        sourceText: String,
        expectEndOfStream: Boolean,
    ): ArrayElementValue {
        val firstIdent = tokens.consume()
        val startOffset = firstIdent.startOffset
        var endOffset = firstIdent.endOffset
        var secondPenultEndOffset = -1
        var penultEndOffset = -1
        var penultIdent = firstIdent
        var lastIdentToken = firstIdent

        // Consume dot-separated identifier segments (`pkg.Outer.Inner.FIELD` or `pkg.Foo.class`).
        while (tokens.peekType() == SharedTokenType.DOT) {
            tokens.consume() // consume '.'
            if (!tokens.peekType().canBeIdentifier) {
                unknownToken(optionalTypeItem, sourceText)
            }
            val nextIdent = tokens.consume()
            secondPenultEndOffset = penultEndOffset
            penultEndOffset = endOffset
            penultIdent = lastIdentToken
            lastIdentToken = nextIdent
            endOffset = nextIdent.endOffset
        }

        val nextType = tokens.peekType()

        // 1. Simple or qualified Java class literal `<type>.class` without generics or brackets.
        if (
            penultEndOffset != -1 &&
                lastIdentToken.type == SharedTokenType.CLASS &&
                nextType != SharedTokenType.ANGLE_OPEN &&
                nextType != SharedTokenType.BRACKET_OPEN &&
                nextType != SharedTokenType.PAREN_OPEN &&
                nextType != SharedTokenType.DOUBLE_COLON
        ) {
            if (expectEndOfStream && nextType != SharedTokenType.EOF) {
                unknownToken(optionalTypeItem, sourceText)
            }
            val typeString = sourceText.substring(startOffset, penultEndOffset)
            val fullText = sourceText.substring(startOffset, endOffset)
            return createClassLiteralValue(typeString, fullText)
        }

        // 2. Class literal with generics `<...>`, array brackets `[]`, or Kotlin `::class(.java)?`.
        if (
            nextType == SharedTokenType.ANGLE_OPEN ||
                nextType == SharedTokenType.BRACKET_OPEN ||
                nextType == SharedTokenType.DOUBLE_COLON
        ) {
            var typeEndOffset = endOffset
            var matchedDotClass = false

            while (true) {
                when (tokens.peekType()) {
                    SharedTokenType.ANGLE_OPEN -> {
                        tokens.consume()
                        var angleDepth = 1
                        while (angleDepth > 0 && tokens.peekType() != SharedTokenType.EOF) {
                            val token = tokens.consume()
                            if (token.type == SharedTokenType.ANGLE_OPEN) {
                                angleDepth++
                            } else if (token.type == SharedTokenType.ANGLE_CLOSE) {
                                angleDepth--
                            }
                            typeEndOffset = token.endOffset
                        }
                        if (angleDepth != 0) {
                            unknownToken(optionalTypeItem, sourceText)
                        }
                    }
                    SharedTokenType.BRACKET_OPEN -> {
                        tokens.consume()
                        if (tokens.peekType() != SharedTokenType.BRACKET_CLOSE) {
                            unknownToken(optionalTypeItem, sourceText)
                        }
                        typeEndOffset = tokens.consume().endOffset
                    }
                    SharedTokenType.DOT -> {
                        tokens.consume()
                        if (tokens.peekType() == SharedTokenType.CLASS) {
                            endOffset = tokens.consume().endOffset
                            matchedDotClass = true
                            break
                        } else if (tokens.peekType().canBeIdentifier) {
                            typeEndOffset = tokens.consume().endOffset
                        } else {
                            unknownToken(optionalTypeItem, sourceText)
                        }
                    }
                    else -> break
                }
            }

            if (!matchedDotClass) {
                if (
                    !tokens.match(SharedTokenType.DOUBLE_COLON) ||
                        tokens.peekType() != SharedTokenType.CLASS
                ) {
                    unknownToken(optionalTypeItem, sourceText)
                }
                endOffset = tokens.consume().endOffset // consume 'class'

                // Optional `.java` suffix on Kotlin class literals (`<type>::class.java`).
                if (tokens.peekType() == SharedTokenType.DOT) {
                    tokens.consume() // consume '.'
                    if (tokens.peek().text(sourceText) != "java") {
                        unknownToken(optionalTypeItem, sourceText)
                    }
                    endOffset = tokens.consume().endOffset // consume 'java'
                }
            }

            if (expectEndOfStream && tokens.peekType() != SharedTokenType.EOF) {
                unknownToken(optionalTypeItem, sourceText)
            }

            val typeString = sourceText.substring(startOffset, typeEndOffset)
            val fullText = sourceText.substring(startOffset, endOffset)
            return createClassLiteralValue(typeString, fullText)
        }

        val lastIdent = lastIdentToken.text(sourceText)

        // 3. Followed by `(`: either a Kotlin numeric conversion call `.toXxx()` on a field
        // reference, or a Kotlin-style annotation constructor call `QualifiedName(...)`.
        if (nextType == SharedTokenType.PAREN_OPEN) {
            val conversionKind =
                if (penultEndOffset != -1) {
                    PrimitiveTypeItem.Primitive.forKotlinNumericConversionFunctionName(lastIdent)
                } else {
                    null
                }

            if (conversionKind != null) {
                tokens.consume() // consume '('
                if (!tokens.match(SharedTokenType.PAREN_CLOSE)) {
                    unknownToken(optionalTypeItem, sourceText)
                }
                if (expectEndOfStream && tokens.peekType() != SharedTokenType.EOF) {
                    unknownToken(optionalTypeItem, sourceText)
                }
                val fieldName = penultIdent.text(sourceText)
                val className =
                    if (secondPenultEndOffset != -1) {
                        sourceText.substring(startOffset, secondPenultEndOffset)
                    } else {
                        ""
                    }
                return createFieldReference(
                    className = className,
                    fieldName = fieldName,
                    optionalTypeItem = optionalTypeItem,
                    explicitConversionTo = conversionKind,
                )
            }

            // Kotlin-style annotation constructor call `QualifiedName(...)`.
            val annotationClassName = sourceText.substring(startOffset, endOffset)
            val attributes = parseAnnotationAttributes(annotationClassName, tokens, sourceText)
            if (expectEndOfStream) {
                ensureEndOfStreamForAnnotation(tokens, sourceText)
            }
            val annotationItem =
                AnnotationItem.createWithAttributes(
                    annotationContext,
                    FileLocation.UNKNOWN,
                    annotationClassName,
                    attributes,
                ) ?: unknownToken(optionalTypeItem, sourceText)
            return createAnnotationValue(annotationItem)
        }

        // 4. Plain simple or dot-qualified identifier: check boolean/special float constants first,
        // otherwise treat as a field reference.
        if (expectEndOfStream && nextType != SharedTokenType.EOF) {
            unknownToken(optionalTypeItem, sourceText)
        }

        val fullName =
            if (penultEndOffset == -1) {
                lastIdent
            } else {
                sourceText.substring(startOffset, endOffset)
            }

        knownNamedConstantValues[fullName]?.let { constantValue ->
            return constantValue.convertToType(optionalTypeItem)
        }

        val fieldName = lastIdent
        val className =
            if (penultEndOffset != -1) {
                sourceText.substring(startOffset, penultEndOffset)
            } else {
                ""
            }
        return createFieldReference(
            className = className,
            fieldName = fieldName,
            optionalTypeItem = optionalTypeItem,
            explicitConversionTo = null,
        )
    }

    /** Creates a [ClassObjectValue] by parsing [typeString] via [typeItemParser]. */
    private fun createClassLiteralValue(
        typeString: String,
        fullText: String,
    ): ClassObjectValue {
        val classLiteralTypeItem =
            typeItemParser.obtainTypeFromString(
                typeString,
                TypeParameterScope.empty,
                ContextNullability.forceNonNull,
            )
        return createClassObjectValue(classLiteralTypeItem, fullText)
    }

    /**
     * Creates a [FieldReferenceValue] (or normalized constant value) for [className].[fieldName].
     */
    private fun createFieldReference(
        className: String,
        fieldName: String,
        optionalTypeItem: TypeItem?,
        explicitConversionTo: PrimitiveTypeItem.Primitive?,
    ): ArrayElementValue {
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

    /** Parse the [text] to provide a [ConstantValue] of the [optionalTypeItem]. */
    private fun parseConstant(optionalTypeItem: TypeItem?, text: String): ConstantValue? {
        if (text.isEmpty()) return null
        val tokens = SharedLexer(text).tokenize()
        return parseConstantFromStream(optionalTypeItem, tokens, text)
    }

    /**
     * Parses a [ConstantValue] of the [optionalTypeItem] from [tokens], requiring the entire stream
     * to be consumed. Returns `null` if [tokens] does not represent a constant value.
     */
    private fun parseConstantFromStream(
        optionalTypeItem: TypeItem?,
        tokens: TokenStream,
        sourceText: String,
    ): ConstantValue? {
        val peekType = tokens.peekType()
        val constant =
            when {
                peekType == SharedTokenType.STRING_LITERAL -> {
                    val token = tokens.consume()
                    parseStringLiteral(optionalTypeItem, token.text(sourceText))
                }
                peekType == SharedTokenType.CHAR_LITERAL -> {
                    val token = tokens.consume()
                    parseCharLiteral(optionalTypeItem, token.text(sourceText))
                }
                peekType == SharedTokenType.PAREN_OPEN ||
                    peekType == SharedTokenType.PLUS ||
                    peekType == SharedTokenType.MINUS ||
                    peekType == SharedTokenType.NUMBER_LITERAL -> {
                    parseNumberOrSpecialFloatDivision(optionalTypeItem, tokens, sourceText)
                }
                peekType.canBeIdentifier -> {
                    val firstIdent = tokens.consume()
                    val startOffset = firstIdent.startOffset
                    var endOffset = firstIdent.endOffset
                    while (tokens.peekType() == SharedTokenType.DOT) {
                        tokens.consume()
                        if (!tokens.peekType().canBeIdentifier) {
                            return null
                        }
                        endOffset = tokens.consume().endOffset
                    }
                    if (tokens.peekType() != SharedTokenType.EOF) {
                        return null
                    }
                    val fullName = sourceText.substring(startOffset, endOffset)
                    knownNamedConstantValues[fullName]?.convertToType(optionalTypeItem)
                }
                else -> null
            } ?: return null

        return if (tokens.peekType() == SharedTokenType.EOF) constant else null
    }

    /** Parses a double-quoted [text] token into a String [ConstantValue]. */
    private fun parseStringLiteral(optionalTypeItem: TypeItem?, text: String): ConstantValue {
        if (text.length < 2 || text.last() != '"') {
            error("string '$text' starts with \" but does not end with \"")
        }
        val string = javaUnescapeString(text.substring(1, text.length - 1))
        return createLiteralValue(optionalTypeItem, string)
    }

    /** Parses a single-quoted [text] token into a Char [ConstantValue]. */
    private fun parseCharLiteral(optionalTypeItem: TypeItem?, text: String): ConstantValue {
        if (text.length < 2 || text.last() != '\'') {
            error("character \"$text\" starts with ' but does not end with '")
        }
        val string = javaUnescapeString(text.substring(1, text.length - 1))
        if (string.length != 1) {
            error(
                "character \"$text\" should contain a single character but contains ${string.length}"
            )
        }
        val char = string[0]
        return createLiteralValue(optionalTypeItem, char)
    }

    /**
     * Parses either a numeric literal (with optional leading `+`/`-` and optional `- 1` suffix) or
     * a special floating-point division-by-zero expression (`(0.0/0.0)`, `-1.0f / 0.0`, etc.).
     */
    private fun parseNumberOrSpecialFloatDivision(
        optionalTypeItem: TypeItem?,
        tokens: TokenStream,
        sourceText: String,
    ): ConstantValue? {
        if (tokens.peekType() == SharedTokenType.PAREN_OPEN) {
            tokens.consume() // consume '('
            val isNegative = tokens.match(SharedTokenType.MINUS)
            if (tokens.peekType() != SharedTokenType.NUMBER_LITERAL) return null
            val numerator = tokens.consume().text(sourceText)
            if (!tokens.match(SharedTokenType.SLASH)) return null
            if (tokens.peekType() != SharedTokenType.NUMBER_LITERAL) return null
            val denominator = tokens.consume().text(sourceText)
            if (!tokens.match(SharedTokenType.PAREN_CLOSE)) return null

            val specialFloat =
                when {
                    !isNegative && numerator == "0.0" && denominator == "0.0" -> DoubleValue.NaN
                    isNegative && numerator == "1.0" && denominator == "0.0" ->
                        DoubleValue.NEGATIVE_INFINITY
                    !isNegative && numerator == "1.0" && denominator == "0.0" ->
                        DoubleValue.POSITIVE_INFINITY
                    !isNegative && numerator == "0.0f" && denominator == "0.0f" -> FloatValue.NaN
                    isNegative && numerator == "1.0f" && denominator == "0.0f" ->
                        FloatValue.NEGATIVE_INFINITY
                    !isNegative && numerator == "1.0f" && denominator == "0.0f" ->
                        FloatValue.POSITIVE_INFINITY
                    else -> return null
                }
            return specialFloat.convertToType(optionalTypeItem)
        }

        val signToken =
            if (
                tokens.peekType() == SharedTokenType.PLUS ||
                    tokens.peekType() == SharedTokenType.MINUS
            ) {
                tokens.consume()
            } else {
                Token.NONE
            }

        if (tokens.peekType() != SharedTokenType.NUMBER_LITERAL) return null
        val numToken = tokens.consume()

        // Check for unparenthesized special float division: `0.0 / 0.0`, `-1.0f / 0.0`, etc.
        if (tokens.peekType() == SharedTokenType.SLASH) {
            tokens.consume() // consume '/'
            if (tokens.peekType() != SharedTokenType.NUMBER_LITERAL) return null
            val denominator = tokens.consume().text(sourceText)
            if (denominator != "0.0") return null

            val isNegative = signToken != Token.NONE && signToken.type == SharedTokenType.MINUS
            val hasPlus = signToken != Token.NONE && signToken.type == SharedTokenType.PLUS
            if (hasPlus) return null

            val numerator = numToken.text(sourceText)
            val specialFloat =
                when {
                    !isNegative && numerator == "0.0" -> DoubleValue.NaN
                    isNegative && numerator == "1.0" -> DoubleValue.NEGATIVE_INFINITY
                    !isNegative && numerator == "1.0" -> DoubleValue.POSITIVE_INFINITY
                    !isNegative && numerator == "0.0f" -> FloatValue.NaN
                    isNegative && (numerator == "1.0f" || numerator == "1.0F") ->
                        FloatValue.NEGATIVE_INFINITY
                    !isNegative && numerator == "1.0f" -> FloatValue.POSITIVE_INFINITY
                    else -> return null
                }
            return specialFloat.convertToType(optionalTypeItem)
        }

        val numberText =
            if (signToken == Token.NONE) {
                numToken.text(sourceText)
            } else if (signToken.endOffset == numToken.startOffset) {
                sourceText.substring(signToken.startOffset, numToken.endOffset)
            } else {
                signToken.text(sourceText) + numToken.text(sourceText)
            }

        return parseNumber(optionalTypeItem, numberText)
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
                nonLiteralInSource = true,
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

    override fun parseAnnotationItem(text: String, unshorten: Boolean): AnnotationItem? {
        val tokens = SharedLexer(text).tokenize()
        val annotationItem = parseAnnotationItem(tokens, text, unshorten)
        ensureEndOfStreamForAnnotation(tokens, text)
        return annotationItem
    }

    /**
     * Verifies that all tokens in [tokens] have been consumed after parsing an annotation from
     * [sourceText].
     */
    private fun ensureEndOfStreamForAnnotation(tokens: TokenStream, sourceText: String) {
        if (tokens.peekType() != SharedTokenType.EOF) {
            val token = tokens.peek()
            val remainder = sourceText.substring(token.endOffset)
            error(
                "Expected to consume all the contents of `$sourceText` but did not, next token is '${token.text(sourceText)}', remainder is '$remainder'"
            )
        }
    }

    override fun parseAnnotationItem(
        tokens: TokenStream,
        sourceText: String,
        unshorten: Boolean,
    ): AnnotationItem? {
        // Consume optional leading '@'.
        tokens.match(SharedTokenType.AT)

        if (!tokens.peekType().canBeIdentifier) {
            return null
        }

        // Parse simple or dot-qualified annotation class name.
        val firstIdent = tokens.consume()
        val startOffset = firstIdent.startOffset
        var endOffset = firstIdent.endOffset
        var nameBuilder: StringBuilder? = null

        while (tokens.peekType() == SharedTokenType.DOT) {
            val dotToken = tokens.consume() // consume '.'
            val nextIdent = tokens.consume()
            if (
                nameBuilder != null ||
                    dotToken.startOffset != endOffset ||
                    nextIdent.startOffset != dotToken.endOffset
            ) {
                if (nameBuilder == null) {
                    nameBuilder = StringBuilder().append(sourceText, startOffset, endOffset)
                }
                nameBuilder
                    .append('.')
                    .append(sourceText, nextIdent.startOffset, nextIdent.endOffset)
            }
            endOffset = nextIdent.endOffset
        }

        val possiblyShortenedAnnotationClassName =
            nameBuilder?.toString() ?: sourceText.substring(startOffset, endOffset)

        // Unshorten, if necessary.
        val annotationClassName =
            if (unshorten) {
                AnnotationItem.unshortenAnnotation(possiblyShortenedAnnotationClassName)
            } else {
                possiblyShortenedAnnotationClassName
            }

        val attributes =
            if (tokens.peekType() == SharedTokenType.PAREN_OPEN) {
                parseAnnotationAttributes(annotationClassName, tokens, sourceText)
            } else {
                emptyList()
            }

        return AnnotationItem.createWithAttributes(
            annotationContext,
            FileLocation.UNKNOWN,
            annotationClassName,
            attributes,
        )
    }

    /**
     * Parses a `(...)` attribute list from [tokens] (backed by [sourceText]) to create a list of
     * [AnnotationAttribute]s for [annotationClassName].
     *
     * On entry, `tokens.peekType()` must be [SharedTokenType.PAREN_OPEN]. On exit, the matching
     * closing [SharedTokenType.PAREN_CLOSE] has been consumed.
     */
    private fun parseAnnotationAttributes(
        annotationClassName: String,
        tokens: TokenStream,
        sourceText: String,
    ): List<AnnotationAttribute> {
        val openToken = tokens.consume()
        require(openToken.type == SharedTokenType.PAREN_OPEN) {
            "Expected '(' but found ${openToken.text(sourceText)}"
        }

        // Empty attribute list `()`.
        if (tokens.match(SharedTokenType.PAREN_CLOSE)) {
            return emptyList()
        }

        return buildList {
            while (!tokens.match(SharedTokenType.PAREN_CLOSE)) {
                if (tokens.peekType() == SharedTokenType.EOF) {
                    throw ParseException("Unexpected end of file")
                }

                // An attribute is either `<attribute-name> = <value>` or `<value>` (implicit
                // `value` attribute).
                val firstToken = tokens.consume()
                val attributeName: String
                val firstValueToken: Token
                if (
                    firstToken.type.canBeIdentifier && tokens.peekType() == SharedTokenType.EQUALS
                ) {
                    tokens.consume() // consume '='
                    attributeName = firstToken.text(sourceText)
                    if (tokens.peekType() == SharedTokenType.EOF) {
                        throw ParseException("Unexpected end of file")
                    }
                    firstValueToken = tokens.consume()
                } else {
                    attributeName = ANNOTATION_ATTR_VALUE
                    firstValueToken = firstToken
                }

                // Scan the value tokens up to the next top-level `,` or `)` at nesting depth 0.
                val valueStartOffset = firstValueToken.startOffset
                var valueEndOffset = firstValueToken.endOffset
                var parenDepth = if (firstValueToken.type == SharedTokenType.PAREN_OPEN) 1 else 0
                var braceDepth = if (firstValueToken.type == SharedTokenType.BRACE_OPEN) 1 else 0
                var angleDepth = if (firstValueToken.type == SharedTokenType.ANGLE_OPEN) 1 else 0
                var bracketDepth =
                    if (firstValueToken.type == SharedTokenType.BRACKET_OPEN) 1 else 0

                while (tokens.peekType() != SharedTokenType.EOF) {
                    val nextType = tokens.peekType()
                    if (
                        parenDepth == 0 &&
                            braceDepth == 0 &&
                            angleDepth == 0 &&
                            bracketDepth == 0 &&
                            (nextType == SharedTokenType.COMMA ||
                                nextType == SharedTokenType.PAREN_CLOSE)
                    ) {
                        break
                    }
                    val token = tokens.consume()
                    valueEndOffset = token.endOffset
                    when (token.type) {
                        SharedTokenType.PAREN_OPEN -> parenDepth++
                        SharedTokenType.PAREN_CLOSE -> if (parenDepth > 0) parenDepth--
                        SharedTokenType.BRACE_OPEN -> braceDepth++
                        SharedTokenType.BRACE_CLOSE -> if (braceDepth > 0) braceDepth--
                        SharedTokenType.ANGLE_OPEN -> angleDepth++
                        SharedTokenType.ANGLE_CLOSE -> if (angleDepth > 0) angleDepth--
                        SharedTokenType.BRACKET_OPEN -> bracketDepth++
                        SharedTokenType.BRACKET_CLOSE -> if (bracketDepth > 0) bracketDepth--
                    }
                }

                if (parenDepth > 0 || braceDepth > 0 || angleDepth > 0 || bracketDepth > 0) {
                    throw ParseException("Unexpected end of file")
                }

                val valueText = sourceText.substring(valueStartOffset, valueEndOffset)

                // Consume separator `,` or require closing `)` on next iteration.
                when (tokens.peekType()) {
                    SharedTokenType.COMMA -> {
                        tokens.consume()
                    }
                    SharedTokenType.PAREN_CLOSE -> {
                        // Will be consumed by the while loop condition on the next iteration.
                    }
                    else -> {
                        val separator = tokens.peek().text(sourceText)
                        throw ValueProviderException(
                            "Unknown token <$separator>, expected one of `,` or `)`"
                        )
                    }
                }

                val valueProvider =
                    providerForAnnotationValue(
                        annotationClassName,
                        attributeName,
                        valueText,
                    )

                add(
                    AnnotationAttribute.createLazyAttribute(
                        attributeName,
                        valueProvider,
                    )
                )
            }
        }
    }

    companion object {
        /**
         * Map of named special floating-point constants (excluding division expressions, which are
         * parsed directly from tokens in [parseNumberOrSpecialFloatDivision]).
         */
        private val namedSpecialFloats =
            mapOf(
                DoubleValue.NaN to
                    listOf(
                        "Double.NaN",
                        "java.lang.Double.NaN",
                        "kotlin.jvm.internal.DoubleCompanionObject.NaN",
                    ),
                DoubleValue.NEGATIVE_INFINITY to
                    listOf(
                        "Double.NEGATIVE_INFINITY",
                        "java.lang.Double.NEGATIVE_INFINITY",
                        "kotlin.jvm.internal.DoubleCompanionObject.NEGATIVE_INFINITY",
                    ),
                DoubleValue.POSITIVE_INFINITY to
                    listOf(
                        "Double.POSITIVE_INFINITY",
                        "java.lang.Double.POSITIVE_INFINITY",
                        "kotlin.jvm.internal.DoubleCompanionObject.POSITIVE_INFINITY",
                    ),
                FloatValue.NaN to
                    listOf(
                        "Float.NaN",
                        "java.lang.Float.NaN",
                        "kotlin.jvm.internal.FloatCompanionObject.NaN",
                    ),
                FloatValue.NEGATIVE_INFINITY to
                    listOf(
                        "Float.NEGATIVE_INFINITY",
                        "java.lang.Float.NEGATIVE_INFINITY",
                        "kotlin.jvm.internal.FloatCompanionObject.NEGATIVE_INFINITY",
                    ),
                FloatValue.POSITIVE_INFINITY to
                    listOf(
                        "Float.POSITIVE_INFINITY",
                        "java.lang.Float.POSITIVE_INFINITY",
                        "kotlin.jvm.internal.FloatCompanionObject.POSITIVE_INFINITY",
                    ),
            )

        /** Map of all known named constant values (booleans and named special floats). */
        private val knownNamedConstantValues: Map<String, LiteralValue<*>> =
            mapOf(
                "false" to BooleanValue.FALSE,
                "true" to BooleanValue.TRUE,
            ) +
                namedSpecialFloats.flatMap { (value, alternatives) ->
                    alternatives.map { it to value }
                }
    }
}
