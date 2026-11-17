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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WellKnownTypes.JAVA_LANG_OBJECT_NON_NULL_TYPE
import com.android.tools.metalava.model.WellKnownTypes.JAVA_LANG_OBJECT_PLATFORM_TYPE
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.parser.SharedLexer
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.parser.TokenType
import com.android.tools.metalava.model.value.ValueParser

/**
 * Recursive-descent parser for [TypeItem]s that consumes [Token]s from a [TokenStream] produced by
 * [SharedLexer] (or `SignatureFileLexer`).
 *
 * @param annotationContext context for resolving annotations and classes.
 * @param unqualifiedClassHandler responsible for determining how to handle unqualified types.
 * @param kotlinStyleNulls whether Kotlin-style nulls (`?` for nullable, `!` for platform, and no
 *   suffix for non-null) are supported.
 * @param errorReporter channel for reporting recoverable errors found while parsing.
 * @param unshortenAnnotations whether short annotation names (e.g. `@NonNull`) should be expanded
 *   to their fully qualified names when parsing types.
 */
open class DefaultTypeItemParser(
    val annotationContext: AnnotationContext,
    private val unqualifiedClassHandler: UnqualifiedClassHandler,
    val kotlinStyleNulls: Boolean = false,
    private val errorReporter: TypeItemParserErrorReporter = TypeItemParserErrorReporter.THROWING,
    private val unshortenAnnotations: Boolean = false,
) : TypeItemParser {
    /** Parser for type-use annotations. */
    private val valueParser by
        lazy(LazyThreadSafetyMode.NONE) {
            ValueParser(
                annotationContext,
                this,
            )
        }

    /** A [TypeItem] representing `java.lang.Object`, suitable for general use. */
    private val objectType =
        if (kotlinStyleNulls) JAVA_LANG_OBJECT_NON_NULL_TYPE else JAVA_LANG_OBJECT_PLATFORM_TYPE

    /**
     * Parses [type] into a [TypeItem] in the context of the type parameters from
     * [typeParameterScope], if applicable.
     *
     * @param type the raw type string to tokenize and parse.
     * @param typeParameterScope the in-scope type parameters for resolving [VariableTypeItem]s.
     * @param contextNullability contextual nullability constraints (such as forced non-null for
     *   supertype clauses) to apply after parsing.
     * @param offset the 0-based character offset of [type] within the original source.
     */
    override fun obtainTypeFromString(
        type: String,
        typeParameterScope: TypeParameterScope,
        contextNullability: ContextNullability,
        offset: Int,
    ): TypeItem {
        val typeItem =
            parseTypeWithContextNullability(
                type = type,
                typeParameterScope = typeParameterScope,
                contextNullability = contextNullability,
                offset = offset,
            )
        return applyContextNullability(typeItem, contextNullability)
    }

    override fun typeParameterStrings(typeString: String?): List<String> =
        Companion.typeParameterStrings(typeString)

    /**
     * Parses a [TypeItem] directly from [tokens] (backed by [sourceText]) in the context of
     * [typeParameterScope].
     *
     * Unlike [obtainTypeFromString], this does not require the type to consume the entire stream;
     * tokens following the type (such as parameter names, method names, or closing delimiters) are
     * left in [tokens] for the caller to consume.
     *
     * @param tokens the [TokenStream] positioned at the start of the type.
     * @param sourceText the full source text backing [tokens] (used for slicing substrings by token
     *   offsets).
     * @param typeParameterScope the in-scope type parameters for resolving [VariableTypeItem]s.
     * @param contextNullability contextual nullability constraints to apply after parsing.
     * @param unshortenAnnotations whether short annotation names (e.g. `@NonNull`) should be
     *   expanded to their fully qualified names.
     */
    fun obtainTypeFromStream(
        tokens: TokenStream,
        sourceText: String,
        typeParameterScope: TypeParameterScope = TypeParameterScope.empty,
        contextNullability: ContextNullability = ContextNullability.none,
        unshortenAnnotations: Boolean = this.unshortenAnnotations,
    ): TypeItem {
        val forceClassToBeNonNull =
            contextNullability.forcedNullability == TypeNullability.NONNULL || kotlinStyleNulls
        val typeItem =
            parseTypeFromStream(
                tokens,
                sourceText = sourceText,
                typeParameterScope = typeParameterScope,
                leadingAnnotations = emptyList(),
                forceClassToBeNonNull = forceClassToBeNonNull,
                unshortenAnnotations = unshortenAnnotations,
                expectEndOfStream = false,
                offset = 0,
            )
        return applyContextNullability(typeItem, contextNullability)
    }

    /**
     * Post-processes a parsed [typeItem] to apply any forced or inferred nullability from
     * [contextNullability].
     *
     * For [ArrayTypeItem]s, if [ContextNullability.forcedComponentNullability] is specified (for
     * example, annotation attributes that return arrays cannot have nullable components), the
     * component type's nullability is updated first before computing the outer type's nullability.
     */
    private fun applyContextNullability(
        typeItem: TypeItem,
        contextNullability: ContextNullability,
    ): TypeItem {
        var result = typeItem

        // Check if the type is an array and its component nullability needs to be updated based on
        // the context.
        val forcedComponentNullability = contextNullability.forcedComponentNullability
        if (
            result is ArrayTypeItem &&
                forcedComponentNullability != null &&
                forcedComponentNullability != result.componentType.modifiers.nullability
        ) {
            result =
                result.substitute(
                    componentType = result.componentType.substitute(forcedComponentNullability),
                )
        }

        // Check if the type's nullability needs to be updated based on the context.
        val typeNullability = result.modifiers.nullability
        val actualTypeNullability =
            contextNullability.compute(typeNullability, result.modifiers.annotations)
        return if (actualTypeNullability != typeNullability) {
            result.substitute(actualTypeNullability)
        } else result
    }

    /**
     * Parse [type] and return a [TypeItem], in the context of type parameters from
     * [typeParameterScope], if applicable.
     *
     * Used internally, as it has an extra [annotations] parameter that allows the annotations on
     * array components to be correctly associated with the correct component. They are optional
     * leading type-use annotations that have already been removed from the array's type string.
     *
     * This will also map [contextNullability] to a [Boolean] that controls whether a
     * [ClassTypeItem] is forced to be non-null, taking into account [kotlinStyleNulls].
     */
    private fun parseTypeWithContextNullability(
        type: String,
        typeParameterScope: TypeParameterScope,
        annotations: List<AnnotationItem> = emptyList(),
        contextNullability: ContextNullability = ContextNullability.none,
        unshortenAnnotations: Boolean = this.unshortenAnnotations,
        offset: Int = 0,
    ): TypeItem {
        // Class types used as super types, i.e. in an extends or implements list are forced to be
        // [TypeNullability.NONNULL], just as they would be if kotlinStyleNulls was true. Use the
        // same cache key for both so that they reuse cached types where possible.
        val forceClassToBeNonNull =
            contextNullability.forcedNullability == TypeNullability.NONNULL || kotlinStyleNulls

        return if (unshortenAnnotations != this.unshortenAnnotations) {
            parseTypeFromStream(
                tokens = SharedLexer(type).tokenize(),
                sourceText = type,
                typeParameterScope = typeParameterScope,
                leadingAnnotations = annotations,
                forceClassToBeNonNull = forceClassToBeNonNull,
                unshortenAnnotations = unshortenAnnotations,
                expectEndOfStream = true,
                offset = offset,
            )
        } else {
            parseType(type, typeParameterScope, annotations, forceClassToBeNonNull, offset)
        }
    }

    /**
     * Converts [type] to a [TypeItem] in the context of [typeParameterScope].
     *
     * @param type the type string to parse.
     * @param typeParameterScope the in-scope type parameters.
     * @param annotations leading type-use annotations already detached from an enclosing array.
     * @param forceClassToBeNonNull if `true`, forces an outermost [ClassTypeItem] without a
     *   nullability suffix or nullness annotation to have [TypeNullability.NONNULL].
     * @param offset the 0-based character offset of [type] within the original source.
     */
    protected open fun parseType(
        type: String,
        typeParameterScope: TypeParameterScope,
        annotations: List<AnnotationItem> = emptyList(),
        forceClassToBeNonNull: Boolean = false,
        offset: Int = 0,
    ): TypeItem =
        parseTypeFromStream(
            tokens = SharedLexer(type).tokenize(),
            sourceText = type,
            typeParameterScope = typeParameterScope,
            leadingAnnotations = annotations,
            forceClassToBeNonNull = forceClassToBeNonNull,
            unshortenAnnotations = unshortenAnnotations,
            expectEndOfStream = true,
            offset = offset,
        )

    /**
     * Creates a [TypeModifiers] from [annotations] and nullability information.
     *
     * If [knownNullability] is `null` then this will compute a non-null [TypeNullability] as
     * follows:
     *
     * If [kotlinStyleNulls] is `true` then this will use the first non-null [TypeNullability] found
     * in the following steps:
     * 1. [defaultNullability]
     * 2. [TypeNullability.NONNULL].
     *
     * Otherwise, it will use the first non-null [TypeNullability] found in the following steps:
     * 1. The [TypeNullability] of a [AnnotationItem.isNullnessAnnotation] annotation in
     *    [annotations].
     * 2. [defaultNullability]
     * 3. [TypeNullability.PLATFORM].
     */
    private fun createModifiers(
        annotations: List<AnnotationItem>,
        knownNullability: TypeNullability?,
        defaultNullability: TypeNullability? = null,
    ): TypeModifiers {
        // Use the known nullability if provided; otherwise infer from nullness annotations or fall
        // back to the default nullability for the current format.
        val nullability =
            knownNullability
                ?: if (kotlinStyleNulls) {
                    defaultNullability ?: TypeNullability.NONNULL
                } else {
                    annotations
                        .firstOrNull { it.isNullnessAnnotation() }
                        ?.let { TypeNullability.ofAnnotation(it) }
                        ?: defaultNullability
                        ?: TypeNullability.PLATFORM
                }

        return TypeModifiers.create(annotations, nullability)
    }

    /**
     * Represents a nested `. @Anno Inner<T>` class segment in a qualified class type.
     *
     * @property annotations type-use annotations placed immediately after `.` before [name].
     * @property name the simple name of the nested class.
     * @property typeArgSlices type argument slices `<...>` applied to this nested class segment.
     */
    private class ClassSegment(
        val annotations: List<AnnotationItem>,
        val name: String,
        val typeArgSlices: List<TypeString>,
    )

    /**
     * Represents a single array dimension `Annotation* ('[' ']' | '...') NullabilitySuffix?`.
     *
     * @property annotations type-use annotations preceding `[` or `...` for this dimension.
     * @property isVarargs `true` if this dimension was written with `...`, `false` if `[]`.
     * @property nullToken optional Kotlin-style nullability suffix token (`?` or `!`) after `[]` or
     *   `...`, or [Token.NONE] if absent.
     * @property endOffset exclusive end character offset of this array dimension in `sourceText`.
     */
    private class ArrayDimension(
        val annotations: List<AnnotationItem>,
        val isVarargs: Boolean,
        val nullToken: Token,
        val endOffset: Int,
    )

    /**
     * Parses a [TypeItem] from [tokens], collecting any leading type-use annotations and
     * dispatching to [parseWildcard] or [parseNonWildcard].
     */
    private fun parseTypeFromStream(
        tokens: TokenStream,
        sourceText: String,
        typeParameterScope: TypeParameterScope,
        leadingAnnotations: List<AnnotationItem>,
        forceClassToBeNonNull: Boolean,
        unshortenAnnotations: Boolean,
        expectEndOfStream: Boolean,
        offset: Int,
    ): TypeItem {
        // Consume any leading `@Anno` tokens and combine them with annotations passed down from an
        // enclosing array type.
        val annotationsFromStream = parseAnnotations(tokens, sourceText, unshortenAnnotations)
        val allLeadingAnnotations =
            if (leadingAnnotations.isEmpty()) {
                annotationsFromStream
            } else if (annotationsFromStream.isEmpty()) {
                leadingAnnotations
            } else {
                leadingAnnotations + annotationsFromStream
            }

        // A leading `?` always starts a wildcard type (`?`, `? extends T`, or `? super T`), even if
        // the bound itself is an array type (e.g. `? extends String[]`).
        if (tokens.peekType() == SharedTokenType.QUESTION) {
            return parseWildcard(
                tokens,
                sourceText,
                typeParameterScope,
                allLeadingAnnotations,
                unshortenAnnotations,
                offset,
            )
        }

        return parseNonWildcard(
            tokens,
            sourceText,
            typeParameterScope,
            allLeadingAnnotations,
            forceClassToBeNonNull,
            unshortenAnnotations,
            expectEndOfStream,
            offset,
        )
    }

    /**
     * Parses a wildcard type starting with `?` (`?`, `? extends Bound`, or `? super Bound`).
     *
     * If `?` is followed by unexpected tokens (e.g. `? blah`), reports an error to [errorReporter],
     * consumes the unexpected tokens up to the next type boundary, and falls back to an unbounded
     * wildcard.
     */
    private fun parseWildcard(
        tokens: TokenStream,
        sourceText: String,
        typeParameterScope: TypeParameterScope,
        annotations: List<AnnotationItem>,
        unshortenAnnotations: Boolean,
        offset: Int,
    ): WildcardTypeItem {
        val questionToken = tokens.consume()
        // Wildcard types always have UNDEFINED nullability.
        val typeModifiers = createModifiers(annotations, TypeNullability.UNDEFINED)

        return when (tokens.peekType()) {
            // Unbounded wildcard `?` at the end of a type or type argument list: uses an implicit
            // java.lang.Object extends bound.
            SharedTokenType.EOF,
            SharedTokenType.COMMA,
            SharedTokenType.ANGLE_CLOSE -> {
                TypeItem.createWildcardType(typeModifiers, objectType, null)
            }
            // Upper-bounded wildcard `? extends Bound`.
            SharedTokenType.EXTENDS -> {
                tokens.consume()
                val extendsBound =
                    parseWildcardBound(
                        tokens,
                        sourceText,
                        typeParameterScope,
                        unshortenAnnotations,
                        offset,
                    )
                TypeItem.createWildcardType(typeModifiers, extendsBound, null)
            }
            // Lower-bounded wildcard `? super Bound`: also carries an implicit java.lang.Object
            // extends bound.
            SharedTokenType.SUPER -> {
                tokens.consume()
                val superBound =
                    parseWildcardBound(
                        tokens,
                        sourceText,
                        typeParameterScope,
                        unshortenAnnotations,
                        offset,
                    )
                TypeItem.createWildcardType(typeModifiers, objectType, superBound)
            }
            // Malformed wildcard: report an error and recover as an unbounded wildcard.
            else -> {
                val lastToken = consumeUntilTypeBoundary(tokens)
                val wildcardText =
                    sourceText.substring(questionToken.startOffset, lastToken.endOffset)
                errorReporter.report(
                    "Type starts with \"?\" but doesn't appear to be wildcard: $wildcardText",
                    offset + questionToken.startOffset,
                )
                TypeItem.createWildcardType(typeModifiers, objectType, null)
            }
        }
    }

    /**
     * Scans the tokens of a wildcard bound up to the enclosing `,`, `>`, `)`, or `EOF` and parses
     * them as a [ReferenceTypeItem].
     */
    private fun parseWildcardBound(
        tokens: TokenStream,
        sourceText: String,
        typeParameterScope: TypeParameterScope,
        unshortenAnnotations: Boolean,
        offset: Int,
    ): ReferenceTypeItem {
        val startOffset = tokens.peek().startOffset
        val lastToken = consumeUntilTypeBoundary(tokens)
        val boundType = sourceText.substring(startOffset, lastToken.endOffset)
        return parseTypeWithContextNullability(
            boundType,
            typeParameterScope,
            unshortenAnnotations = unshortenAnnotations,
            offset = offset + startOffset,
        )
            as ReferenceTypeItem
    }

    /**
     * Parses a non-wildcard type: a [VariableTypeItem], [PrimitiveTypeItem], [ClassTypeItem], or
     * [ArrayTypeItem].
     */
    private fun parseNonWildcard(
        tokens: TokenStream,
        sourceText: String,
        typeParameterScope: TypeParameterScope,
        leadingAnnotations: List<AnnotationItem>,
        forceClassToBeNonNull: Boolean,
        unshortenAnnotations: Boolean,
        expectEndOfStream: Boolean,
        offset: Int,
    ): TypeItem {
        val baseStartOffset = tokens.peek().startOffset
        val firstToken = tokens.consume()
        var baseEndOffset = firstToken.endOffset

        // Fast path: single-identifier base type (not followed by '.' or '<').
        // Handles primitives (`int`), type variables (`T`), unqualified classes (`String`), and
        // arrays thereof (`int[]`, `String[]`).
        if (
            tokens.peekType() != SharedTokenType.DOT &&
                tokens.peekType() != SharedTokenType.ANGLE_OPEN
        ) {
            val baseNullToken = matchNullabilityToken(tokens)
            val baseSliceEnd =
                if (baseNullToken != Token.NONE) baseNullToken.endOffset else baseEndOffset

            // If followed by array dimensions (`@Anno []`, `[]`, or `...`), slice the base type and
            // delegate to parseArrayType.
            if (isArrayDimensionStart(tokens)) {
                val baseType = sourceText.substring(baseStartOffset, baseSliceEnd)
                return parseArrayType(
                    tokens,
                    sourceText,
                    baseType,
                    typeParameterScope,
                    leadingAnnotations,
                    unshortenAnnotations,
                    baseStartOffset,
                    offset,
                )
            }

            val nullability =
                resolveNullability(
                    sourceText,
                    baseNullToken,
                    baseStartOffset,
                    baseSliceEnd,
                    offset,
                )
            val simpleName = firstToken.text(sourceText)

            // 1. Check if it is a type variable in scope first. If a type parameter in Kotlin
            // shadows a primitive or class name (e.g. `<int>` or `<String>`), it must be resolved
            // as a type variable rather than a primitive or class.
            typeParameterScope.findTypeParameter(simpleName)?.let { param ->
                val modifiers =
                    createModifiers(
                        leadingAnnotations,
                        nullability,
                        defaultNullability = TypeNullability.UNDEFINED,
                    )
                return TypeItem.createVariableType(modifiers, param)
            }

            // 2. Check if it is a primitive type.
            asPrimitive(
                    sourceText,
                    simpleName,
                    leadingAnnotations,
                    nullability,
                    baseStartOffset,
                    baseSliceEnd,
                    offset,
                )
                ?.let {
                    return it
                }

            // 3. Otherwise, it is an unqualified class type (or Kotlin `dynamic`).
            val qualifiedName =
                if (simpleName == "dynamic") {
                    // Kotlin `dynamic` types are currently represented as a ClassTypeItem
                    // (b/495459207).
                    simpleName
                } else {
                    unqualifiedClassHandler.handleUnqualifiedType(
                        errorReporter,
                        simpleName,
                        offset + baseStartOffset,
                    )
                }
            val defaultNullability = if (forceClassToBeNonNull) TypeNullability.NONNULL else null
            val classModifiers =
                createModifiers(leadingAnnotations, nullability, defaultNullability)
            return TypeItem.createClassType(
                classModifiers,
                qualifiedName,
                emptyList(),
                null,
            )
        }

        // Qualified and/or parameterized class type.
        val outerRawName: String
        var outerAnnotations: List<AnnotationItem> = emptyList()

        // If the first identifier is lowercase (a package segment), consume `.pkg` segments until
        // we reach the first class name (which contains an uppercase character or is preceded by a
        // type-use annotation such as `java.lang.@NonNull String`).
        if (!firstToken.containsUpperCase(sourceText) && tokens.peekType() == SharedTokenType.DOT) {
            var nameBuilder: StringBuilder? = null
            while (tokens.peekType() == SharedTokenType.DOT) {
                val dotToken = tokens.consume() // consume '.'
                val annos = parseAnnotations(tokens, sourceText, unshortenAnnotations)
                val nextIdent = tokens.consume()
                if (
                    nameBuilder != null ||
                        annos.isNotEmpty() ||
                        dotToken.startOffset != baseEndOffset ||
                        nextIdent.startOffset != dotToken.endOffset
                ) {
                    if (nameBuilder == null) {
                        nameBuilder =
                            StringBuilder(sourceText.length)
                                .append(
                                    sourceText,
                                    baseStartOffset,
                                    baseEndOffset,
                                )
                    }
                    nameBuilder
                        .append('.')
                        .append(sourceText, nextIdent.startOffset, nextIdent.endOffset)
                }
                if (annos.isNotEmpty()) {
                    outerAnnotations = annos
                }
                baseEndOffset = nextIdent.endOffset
                if (annos.isNotEmpty() || nextIdent.containsUpperCase(sourceText)) {
                    break
                }
            }
            outerRawName =
                nameBuilder?.toString() ?: sourceText.substring(baseStartOffset, baseEndOffset)
        } else {
            outerRawName = firstToken.text(sourceText)
        }

        // If the outer class is parameterized (`Outer<P1>`), scan its type argument slices.
        var outerTypeArgSlices: List<TypeString> = emptyList()
        var lastSegmentHadTypeArgs = false
        if (tokens.peekType() == SharedTokenType.ANGLE_OPEN) {
            val (argSlices, angleEndOffset) = scanTypeArguments(tokens, sourceText)
            outerTypeArgSlices = argSlices
            baseEndOffset = angleEndOffset
            lastSegmentHadTypeArgs = true
        }

        // Parse any nested class segments (`.Inner`, `. @Anno Inner<P2>`, etc.).
        var nestedSegments: MutableList<ClassSegment>? = null
        var lastSegmentStartOffset = baseStartOffset
        while (tokens.peekType() == SharedTokenType.DOT) {
            tokens.consume() // consume '.'
            lastSegmentStartOffset = tokens.peek().startOffset
            val innerAnnotations = parseAnnotations(tokens, sourceText, unshortenAnnotations)
            val innerIdent = tokens.consume()
            baseEndOffset = innerIdent.endOffset
            var innerTypeArgSlices: List<TypeString> = emptyList()
            lastSegmentHadTypeArgs = false
            if (tokens.peekType() == SharedTokenType.ANGLE_OPEN) {
                val (argSlices, angleEndOffset) = scanTypeArguments(tokens, sourceText)
                innerTypeArgSlices = argSlices
                baseEndOffset = angleEndOffset
                lastSegmentHadTypeArgs = true
            }
            if (nestedSegments == null) {
                nestedSegments = mutableListOf()
            }
            nestedSegments.add(
                ClassSegment(
                    innerAnnotations,
                    innerIdent.text(sourceText),
                    innerTypeArgSlices,
                )
            )
        }

        // Check for unexpected trailing tokens immediately following a `<...>` type argument list
        // (e.g. `Comparable<test.pkg.Foo>blah2`).
        if (
            lastSegmentHadTypeArgs && expectEndOfStream && !isValidAfterBaseType(tokens.peekType())
        ) {
            val remainderStart = baseEndOffset
            val lastUnexpected = consumeUntilTypeBoundary(tokens)
            val fullText = sourceText.substring(lastSegmentStartOffset, lastUnexpected.endOffset)
            val remainderText = sourceText.substring(remainderStart, lastUnexpected.endOffset)
            errorReporter.report(
                "Could not parse type `$fullText`. Found unexpected string after type parameters: $remainderText",
                offset + remainderStart,
            )
        }

        val baseNullToken = matchNullabilityToken(tokens)
        val baseSliceEnd =
            if (baseNullToken != Token.NONE) baseNullToken.endOffset else baseEndOffset

        // If followed by array dimensions, slice the base type and delegate to parseArrayType.
        if (isArrayDimensionStart(tokens)) {
            val baseType = sourceText.substring(baseStartOffset, baseSliceEnd)
            return parseArrayType(
                tokens,
                sourceText,
                baseType,
                typeParameterScope,
                leadingAnnotations,
                unshortenAnnotations,
                baseStartOffset,
                offset,
            )
        }

        val nullability =
            resolveNullability(
                sourceText,
                baseNullToken,
                baseStartOffset,
                baseSliceEnd,
                offset,
            )

        // Resolve the outer class's qualified name and parse its type arguments.
        val outerQualifiedName =
            if (outerRawName.contains('.')) {
                outerRawName
            } else {
                unqualifiedClassHandler.handleUnqualifiedType(
                    errorReporter,
                    outerRawName,
                    offset + baseStartOffset,
                )
            }
        val outerTypeArgs =
            outerTypeArgSlices.map { arg ->
                parseTypeWithContextNullability(
                    arg.type,
                    typeParameterScope,
                    unshortenAnnotations = unshortenAnnotations,
                    offset = offset + arg.offset,
                )
                    as TypeArgumentTypeItem
            }

        // If there are nested class segments (e.g. `Outer.Inner`), the outer class is always
        // non-null and leading annotations apply to the innermost nested class instead.
        val hasNested = !nestedSegments.isNullOrEmpty()
        val defaultNullability = if (forceClassToBeNonNull) TypeNullability.NONNULL else null
        val outerModifiers =
            if (hasNested) {
                createModifiers(outerAnnotations, TypeNullability.NONNULL)
            } else {
                createModifiers(
                    leadingAnnotations + outerAnnotations,
                    nullability,
                    defaultNullability,
                )
            }

        var currentClassType =
            TypeItem.createClassType(
                outerModifiers,
                outerQualifiedName,
                outerTypeArgs,
                null,
            )

        // Build each nested ClassTypeItem in order from outer to inner, linking each to its
        // enclosing `currentClassType`.
        if (nestedSegments != null) {
            val lastIndex = nestedSegments.lastIndex
            for (i in 0..lastIndex) {
                val segment = nestedSegments[i]
                val isLast = i == lastIndex
                // Enclosing nested classes are non-null; the innermost nested class receives
                // `leadingAnnotations` and `nullability`.
                val segmentModifiers =
                    if (!isLast) {
                        createModifiers(segment.annotations, TypeNullability.NONNULL)
                    } else {
                        createModifiers(
                            leadingAnnotations + segment.annotations,
                            nullability,
                            defaultNullability,
                        )
                    }
                val segmentTypeArgs =
                    segment.typeArgSlices.map { arg ->
                        parseTypeWithContextNullability(
                            arg.type,
                            typeParameterScope,
                            unshortenAnnotations = unshortenAnnotations,
                            offset = offset + arg.offset,
                        )
                            as TypeArgumentTypeItem
                    }
                val nestedQualifiedName = "${currentClassType.qualifiedName}.${segment.name}"
                currentClassType =
                    TypeItem.createClassType(
                        segmentModifiers,
                        nestedQualifiedName,
                        segmentTypeArgs,
                        currentClassType,
                    )
            }
        }

        return currentClassType
    }

    /**
     * Attempts to match [name] as a primitive type name, returning a [PrimitiveTypeItem] or `null`
     * if [name] is not a primitive type.
     *
     * Reports an error to [errorReporter] if a non-`NONNULL` [nullability] suffix was applied to a
     * primitive type.
     */
    private fun asPrimitive(
        sourceText: String,
        name: String,
        annotations: List<AnnotationItem>,
        nullability: TypeNullability?,
        startOffset: Int,
        endOffset: Int,
        offset: Int,
    ): PrimitiveTypeItem? {
        val kind =
            when (name) {
                "byte" -> PrimitiveTypeItem.Primitive.BYTE
                "char" -> PrimitiveTypeItem.Primitive.CHAR
                "double" -> PrimitiveTypeItem.Primitive.DOUBLE
                "float" -> PrimitiveTypeItem.Primitive.FLOAT
                "int" -> PrimitiveTypeItem.Primitive.INT
                "long" -> PrimitiveTypeItem.Primitive.LONG
                "short" -> PrimitiveTypeItem.Primitive.SHORT
                "boolean" -> PrimitiveTypeItem.Primitive.BOOLEAN
                "void" -> PrimitiveTypeItem.Primitive.VOID
                else -> return null
            }
        if (nullability != null && nullability != TypeNullability.NONNULL) {
            val original = sourceText.substring(startOffset, endOffset)
            errorReporter.report(
                "Invalid nullability suffix on primitive: $original",
                offset + startOffset,
            )
        }
        // Primitives are always non-null.
        val typeModifiers = createModifiers(annotations, TypeNullability.NONNULL)
        return TypeItem.createPrimitiveType(typeModifiers, kind)
    }

    /**
     * Parses one or more array dimensions (`Annotation* ('[' ']' | '...') NullabilitySuffix?`)
     * following [baseType] and wraps the parsed component type in [ArrayTypeItem]s.
     *
     * Note on ordering:
     * - **Annotations**: In Java/Metalava syntax (e.g. `@A String @B [] @C []`), `@A` applies to
     *   `String`, `@B` applies to the outer 2D array `String[][]`, and `@C` applies to the inner 1D
     *   array `String[]` (`dimensions[size - 1 - i].annotations`).
     * - **Kotlin-style nullability suffixes**: In Metalava signature syntax (e.g. `String! []!
     *   []?`), nullability suffixes appear from innermost to outermost (`dimensions[i]`).
     */
    private fun parseArrayType(
        tokens: TokenStream,
        sourceText: String,
        baseType: String,
        typeParameterScope: TypeParameterScope,
        leadingAnnotations: List<AnnotationItem>,
        unshortenAnnotations: Boolean,
        baseStartOffset: Int,
        offset: Int,
    ): ArrayTypeItem {
        // Consume all consecutive array dimensions (`@Anno []?` or `@Anno ...?`).
        val dimensions = mutableListOf<ArrayDimension>()
        while (isArrayDimensionStart(tokens)) {
            val dimAnnotations = parseAnnotations(tokens, sourceText, unshortenAnnotations)
            var dimEndOffset: Int
            val isVarargs: Boolean
            if (tokens.peekType() == SharedTokenType.ELLIPSIS) {
                dimEndOffset = tokens.consume().endOffset
                isVarargs = true
            } else {
                dimEndOffset = tokens.consume().endOffset // consume '['
                if (tokens.peekType() == SharedTokenType.BRACKET_CLOSE) {
                    dimEndOffset = tokens.consume().endOffset // consume ']'
                }
                isVarargs = false
            }
            val dimNullToken = matchNullabilityToken(tokens)
            if (dimNullToken != Token.NONE) {
                dimEndOffset = dimNullToken.endOffset
            }
            dimensions.add(
                ArrayDimension(
                    dimAnnotations,
                    isVarargs,
                    dimNullToken,
                    dimEndOffset,
                )
            )
        }

        val lastIndex = dimensions.lastIndex
        val dimensionNullabilities = arrayOfNulls<TypeNullability>(dimensions.size)
        // Resolve the outermost array dimension's nullability first (matching TypeItemParser error
        // reporting order), then resolve inner dimensions from right to left.
        for (i in lastIndex downTo 0) {
            dimensionNullabilities[i] =
                resolveNullability(
                    sourceText,
                    dimensions[i].nullToken,
                    baseStartOffset,
                    dimensions[i].endOffset,
                    offset,
                )
        }

        // Parse the deepest non-array component type, passing `leadingAnnotations` (which precede
        // the component type in Java syntax, e.g. `@A String[]`) so they attach to the component.
        val deepComponentType =
            parseTypeWithContextNullability(
                baseType,
                typeParameterScope,
                leadingAnnotations,
                unshortenAnnotations = unshortenAnnotations,
                offset = offset + baseStartOffset,
            )

        // Build nested ArrayTypeItems from the innermost 1D array outward to the N-D array.
        var currentType: TypeItem = deepComponentType
        val size = dimensions.size
        for (i in 0 until size) {
            // Dimension annotations are ordered outer-to-inner in Java syntax (`@Outer [] @Inner
            // []`), whereas nullability suffixes are ordered inner-to-outer (`[]! []?`).
            val dimAnnotations = dimensions[size - 1 - i].annotations
            val dimNullability = dimensionNullabilities[i]
            val isVarargs = dimensions[i].isVarargs
            val modifiers = createModifiers(dimAnnotations, dimNullability)
            currentType = TypeItem.createArrayType(modifiers, currentType, isVarargs)
        }
        return currentType as ArrayTypeItem
    }

    /** Consumes and parses any consecutive `@Annotation`s at the current position in [tokens]. */
    private fun parseAnnotations(
        tokens: TokenStream,
        sourceText: String,
        unshortenAnnotations: Boolean,
    ): List<AnnotationItem> {
        if (tokens.peekType() != SharedTokenType.AT) return emptyList()
        val list = mutableListOf<AnnotationItem>()
        while (tokens.peekType() == SharedTokenType.AT) {
            valueParser
                .parseAnnotationItem(tokens, sourceText, unshorten = unshortenAnnotations)
                ?.let { list.add(it) }
        }
        return list
    }

    /** Returns `true` if the token's slice in [sourceText] contains an uppercase character. */
    private fun Token.containsUpperCase(sourceText: String): Boolean {
        for (i in startOffset until endOffset) {
            if (sourceText[i].isUpperCase()) return true
        }
        return false
    }

    /**
     * Consumes and returns the next token if it is a nullability suffix (`?` or `!`), or returns
     * [Token.NONE] otherwise.
     */
    private fun matchNullabilityToken(tokens: TokenStream): Token {
        val type = tokens.peekType()
        return if (type == SharedTokenType.QUESTION || type == SharedTokenType.EXCLAMATION) {
            tokens.consume()
        } else {
            Token.NONE
        }
    }

    /**
     * Converts a matched [nullToken] (`?` or `!`) into a [TypeNullability].
     *
     * If [kotlinStyleNulls] is `false`, reports an error for the slice `[startOffset, endOffset)`
     * and falls back to [TypeNullability.PLATFORM].
     */
    private fun resolveNullability(
        sourceText: String,
        nullToken: Token,
        startOffset: Int,
        endOffset: Int,
        offset: Int,
    ): TypeNullability? {
        if (nullToken == Token.NONE) return null
        return if (kotlinStyleNulls) {
            if (nullToken.type == SharedTokenType.QUESTION) {
                TypeNullability.NULLABLE
            } else {
                TypeNullability.PLATFORM
            }
        } else {
            val typeSlice = sourceText.substring(startOffset, endOffset)
            errorReporter.report(
                "Format does not support Kotlin-style null type syntax: $typeSlice",
                offset + nullToken.startOffset,
            )
            TypeNullability.PLATFORM
        }
    }

    /**
     * Returns `true` if the next token in [tokens] starts an array dimension (`@`, `[`, or `...`).
     */
    private fun isArrayDimensionStart(tokens: TokenStream): Boolean {
        val type = tokens.peekType()
        return type == SharedTokenType.AT ||
            type == SharedTokenType.BRACKET_OPEN ||
            type == SharedTokenType.ELLIPSIS
    }

    /** Returns `true` if [type] can validly follow a parameterized class type. */
    private fun isValidAfterBaseType(type: TokenType): Boolean =
        type == SharedTokenType.EOF ||
            type == SharedTokenType.QUESTION ||
            type == SharedTokenType.EXCLAMATION ||
            type == SharedTokenType.AT ||
            type == SharedTokenType.BRACKET_OPEN ||
            type == SharedTokenType.ELLIPSIS ||
            type == SharedTokenType.COMMA ||
            type == SharedTokenType.ANGLE_CLOSE

    /**
     * Consumes and discards tokens from [tokens] up to the end of the current type (stopping before
     * an unbalanced `,`, `>`, `)`, or `EOF`), returning the last consumed [Token].
     */
    private fun consumeUntilTypeBoundary(tokens: TokenStream): Token {
        var lastToken = tokens.peek()
        var angleDepth = 0
        var parenDepth = 0
        while (tokens.peekType() != SharedTokenType.EOF) {
            val nextType = tokens.peekType()
            if (
                angleDepth == 0 &&
                    parenDepth == 0 &&
                    (nextType == SharedTokenType.COMMA ||
                        nextType == SharedTokenType.ANGLE_CLOSE ||
                        nextType == SharedTokenType.PAREN_CLOSE)
            ) {
                break
            }
            val token = tokens.consume()
            lastToken = token
            when (token.type) {
                SharedTokenType.ANGLE_OPEN -> angleDepth++
                SharedTokenType.ANGLE_CLOSE -> if (angleDepth > 0) angleDepth--
                SharedTokenType.PAREN_OPEN -> parenDepth++
                SharedTokenType.PAREN_CLOSE -> if (parenDepth > 0) parenDepth--
            }
        }
        return lastToken
    }

    companion object {
        /**
         * Breaks a string representing type parameters into a list of the type parameter strings.
         *
         * E.g. `"<A, B, C>"` -> `["A", "B", "C"]` and `"<List<A>, B>"` -> `["List<A>", "B"]`.
         */
        fun typeParameterStrings(typeString: String?): List<String> =
            typeParameterStringsWithRemainder(typeString).first

        /**
         * Breaks a string representing type parameters into a list of the type parameter strings,
         * and also returns the remainder of the string after the closing `>`.
         *
         * E.g. `"<A, B, C>.Inner"` -> `Pair(["A", "B", "C"], ".Inner")`
         */
        fun typeParameterStringsWithRemainder(typeString: String?): Pair<List<String>, String?> {
            val s = typeString ?: return Pair(emptyList(), null)
            val tokens = SharedLexer(s).tokenize()
            if (tokens.peekType() != SharedTokenType.ANGLE_OPEN) {
                return Pair(emptyList(), s)
            }
            val (list, endOffset) = scanTypeArguments(tokens, s)
            val remainder =
                if (endOffset == -1 || endOffset == s.length) {
                    null
                } else {
                    s.substring(endOffset)
                }
            return Pair(list.map { it.type }, remainder)
        }

        /**
         * Consumes a `<...>` type argument/parameter list from [tokens] and returns the list of
         * [TypeString]s sliced from [sourceText] along with the exclusive end offset of the closing
         * `>` (or `-1` if unclosed).
         */
        private fun scanTypeArguments(
            tokens: TokenStream,
            sourceText: String,
        ): Pair<List<TypeString>, Int> {
            val openAngle = tokens.consume() // consume '<'
            var endOffset = openAngle.endOffset
            val args = mutableListOf<TypeString>()
            var angleDepth = 1
            var parenDepth = 0
            var argStartOffset = -1
            var argEndOffset = -1

            while (angleDepth > 0 && tokens.peekType() != SharedTokenType.EOF) {
                val token = tokens.consume()
                endOffset = token.endOffset
                when (token.type) {
                    // Track parentheses depth so commas inside `@Anno(a = 1, b = 2)` do not split
                    // type arguments.
                    SharedTokenType.PAREN_OPEN -> {
                        if (argStartOffset == -1) argStartOffset = token.startOffset
                        argEndOffset = token.endOffset
                        parenDepth++
                    }
                    SharedTokenType.PAREN_CLOSE -> {
                        if (parenDepth > 0) parenDepth--
                        if (argStartOffset == -1) argStartOffset = token.startOffset
                        argEndOffset = token.endOffset
                    }
                    // Track nested angle bracket depth for nested generic types (`List<Map<A,
                    // B>>`).
                    SharedTokenType.ANGLE_OPEN -> {
                        if (argStartOffset == -1) argStartOffset = token.startOffset
                        argEndOffset = token.endOffset
                        angleDepth++
                    }
                    SharedTokenType.ANGLE_CLOSE -> {
                        angleDepth--
                        if (angleDepth == 0) {
                            // Reached the closing `>` of the outermost type argument list.
                            if (argStartOffset != -1) {
                                args.add(
                                    TypeString(
                                        sourceText.substring(argStartOffset, argEndOffset),
                                        argStartOffset,
                                    )
                                )
                            }
                            return Pair(args, endOffset)
                        } else {
                            if (argStartOffset == -1) argStartOffset = token.startOffset
                            argEndOffset = token.endOffset
                        }
                    }
                    SharedTokenType.COMMA -> {
                        if (angleDepth == 1 && parenDepth == 0) {
                            // Top-level comma separating type arguments.
                            if (argStartOffset != -1) {
                                args.add(
                                    TypeString(
                                        sourceText.substring(argStartOffset, argEndOffset),
                                        argStartOffset,
                                    )
                                )
                                argStartOffset = -1
                                argEndOffset = -1
                            }
                        } else {
                            if (argStartOffset == -1) argStartOffset = token.startOffset
                            argEndOffset = token.endOffset
                        }
                    }
                    else -> {
                        if (argStartOffset == -1) argStartOffset = token.startOffset
                        argEndOffset = token.endOffset
                    }
                }
            }
            return Pair(args, -1)
        }
    }
}

/**
 * Pairs a sliced [type] string with its 0-based character [offset] in the enclosing source text.
 */
data class TypeString(
    val type: String,
    val offset: Int = 0,
)
