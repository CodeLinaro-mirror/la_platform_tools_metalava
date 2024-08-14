/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability

/** Modifiers for a [TypeItem]. */
class DefaultTypeModifiers(
    override val annotations: List<AnnotationItem>,
    override val nullability: TypeNullability,
) : TypeModifiers {

    override fun substitute(
        nullability: TypeNullability,
        annotations: List<AnnotationItem>,
    ): TypeModifiers =
        if (nullability != this.nullability || annotations != this.annotations)
            DefaultTypeModifiers(annotations, nullability)
        else this

    companion object {
        /** A set of empty, non-null [TypeModifiers] for sharing. */
        val emptyNonNullModifiers: TypeModifiers =
            DefaultTypeModifiers(emptyList(), TypeNullability.NONNULL)

        /** A set of empty, nullable [TypeModifiers] for sharing. */
        val emptyNullableModifiers: TypeModifiers =
            DefaultTypeModifiers(emptyList(), TypeNullability.NULLABLE)

        /** A set of empty, platform [TypeModifiers] for sharing. */
        val emptyPlatformModifiers: TypeModifiers =
            DefaultTypeModifiers(emptyList(), TypeNullability.PLATFORM)

        /** A set of empty, undefined [TypeModifiers] for sharing. */
        val emptyUndefinedModifiers: TypeModifiers =
            DefaultTypeModifiers(emptyList(), TypeNullability.UNDEFINED)

        /**
         * Create a [DefaultTypeModifiers].
         *
         * If [knownNullability] is `null` then this will compute nullability from the
         * [annotations], if any, and if not then default to platform nullness.
         */
        fun create(
            annotations: List<AnnotationItem>,
            knownNullability: TypeNullability? = null,
        ): TypeModifiers {
            // Use the known nullability, or find if there is a nullness annotation on the type,
            // defaulting to platform nullness if not.
            val nullability =
                knownNullability
                    ?: annotations
                        .firstOrNull { it.isNullnessAnnotation() }
                        ?.let { TypeNullability.ofAnnotation(it) }
                        ?: TypeNullability.PLATFORM

            // If the annotations are empty then use one of the predefined instances.
            if (annotations.isEmpty()) {
                return when (nullability) {
                    TypeNullability.NONNULL -> emptyNonNullModifiers
                    TypeNullability.NULLABLE -> emptyNullableModifiers
                    TypeNullability.PLATFORM -> emptyPlatformModifiers
                    TypeNullability.UNDEFINED -> emptyUndefinedModifiers
                }
            }

            return DefaultTypeModifiers(annotations, nullability)
        }
    }
}
