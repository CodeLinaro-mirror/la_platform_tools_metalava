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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.value.ValueParser

/** Parses and caches types within an [AnnotationContext]. */
interface TypeItemParser {
    /**
     * Creates or retrieves from the cache a [TypeItem] representing [type], in the context of the
     * type parameters from [typeParameterScope], if applicable.
     *
     * @param offset the 0-based character offset of [type] within the original source.
     */
    fun obtainTypeFromString(
        type: String,
        typeParameterScope: TypeParameterScope = TypeParameterScope.empty,
        contextNullability: ContextNullability = ContextNullability.none,
        offset: Int = 0,
    ): TypeItem

    /**
     * Breaks a string representing type parameters into a list of the type parameter strings.
     *
     * E.g. `"<A, B, C>"` -> `["A", "B", "C"]` and `"<List<A>, B>"` -> `["List<A>", "B"]`.
     */
    fun typeParameterStrings(typeString: String?): List<String>

    /** Companion object providing factory functions that delegate to [DefaultTypeItemParser]. */
    companion object {
        /** Creates and returns a [DefaultTypeItemParser] as a [TypeItemParser]. */
        operator fun invoke(
            annotationContext: AnnotationContext,
            unqualifiedClassHandler: UnqualifiedClassHandler,
            kotlinStyleNulls: Boolean = false,
            errorReporter: TypeItemParserErrorReporter = TypeItemParserErrorReporter.THROWING,
        ): TypeItemParser =
            DefaultTypeItemParser(
                annotationContext,
                unqualifiedClassHandler,
                kotlinStyleNulls,
                errorReporter,
            )

        /**
         * Returns a [TypeItemParser] suitable for use by the [ValueParser].
         *
         * It does not support kotlin style nulls, or annotations and treats unqualified types as if
         * they were qualified.
         */
        fun forValueParser(
            classResolver: ClassResolver,
            errorReporter: TypeItemParserErrorReporter = TypeItemParserErrorReporter.THROWING,
        ): TypeItemParser {
            val annotationContext =
                object : AnnotationContext, ClassResolver by classResolver {
                    override val annotationManager
                        get() = error("Annotations not supported")
                }

            return invoke(
                annotationContext,
                UnqualifiedClassHandler.PREFIX_WITH_JAVA_LANG,
                kotlinStyleNulls = false,
                errorReporter,
            )
        }
    }
}
