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

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.type.TypeItemParser

/**
 * Parser for the string representation of [Value]s that is used in a signature file or an
 * annotation created from a string.
 */
interface ValueParser {
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
    ): CombinedValueProvider

    /** Parse the [text] to provide a [Value] of the [optionalTypeItem]. */
    fun parse(optionalTypeItem: TypeItem?, text: String): Value?

    /** Parse [text] to produce an [AnnotationItem], if possible. */
    fun parseAnnotationItem(text: String, unshorten: Boolean = false): AnnotationItem?

    /**
     * Parses a single `@QualifiedName` or `@QualifiedName(...)` (or without `@`) annotation from
     * [tokens] (backed by [sourceText]) to create an [AnnotationItem], if possible.
     *
     * On exit, [tokens] is positioned at the token immediately following the annotation.
     */
    fun parseAnnotationItem(
        tokens: TokenStream,
        sourceText: String,
        unshorten: Boolean = false,
    ): AnnotationItem?

    /**
     * Companion object providing factory and utility functions that delegate to
     * [DefaultValueParser].
     */
    companion object {
        /** The default instance of [ValueParser]. */
        val DEFAULT: ValueParser by lazy {
            invoke(
                // Any attempts to resolve an annotation's class in order to determine the type of
                // its attributes will return null which will prevent any conversion of values to
                // the correct type but still allow annotations to be parsed correctly.
                AnnotationContext.DEFAULT_RESOLVE_NULL,
                TypeItemParser.forValueParser(ClassResolver.THROWING),
            )
        }

        /** Creates and returns a [DefaultValueParser] as a [ValueParser]. */
        operator fun invoke(
            annotationContext: AnnotationContext,
            typeItemParser: TypeItemParser,
        ): ValueParser = DefaultValueParser(annotationContext, typeItemParser)
    }
}
