/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model

/**
 * Modifiers for a [TypeItem], analogous to [ModifierList]s for [Item]s. Contains type-use
 * annotation information.
 */
class TypeModifiers
private constructor(
    /** The type-use annotations applied to the owning type. */
    val annotations: List<AnnotationItem>,

    /** The nullability of the type. */
    val nullability: TypeNullability,
) {
    /**
     * Return a [TypeModifiers] instance identical to this one except its
     * [TypeModifiers.nullability] and [TypeModifiers.annotations] properties are the same as the
     * [nullability] and [annotations] parameters respectively.
     *
     * If the parameters are the same as this instance's properties then it will just return this
     * instance, otherwise it will return a new instance.
     */
    fun substitute(
        nullability: TypeNullability = this.nullability,
        annotations: List<AnnotationItem> = this.annotations,
    ): TypeModifiers =
        if (nullability != this.nullability || annotations != this.annotations)
            TypeModifiers(annotations, nullability)
        else this

    /** Return an instance of this with all the [annotations] removed. */
    fun withoutAnnotations(): TypeModifiers = emptyModifiers(nullability)

    /** Whether the [nullability] is [TypeNullability.NULLABLE]. */
    val isNullable
        get() = nullability == TypeNullability.NULLABLE

    /** Whether the [nullability] is [TypeNullability.NONNULL]. */
    val isNonNull
        get() = nullability == TypeNullability.NONNULL

    /** Whether the [nullability] is [TypeNullability.PLATFORM]. */
    val isPlatformNullability
        get() = nullability == TypeNullability.PLATFORM

    companion object {
        /** Get an empty (no annotations) [TypeModifiers] for [typeNullability]. */
        internal fun emptyModifiers(typeNullability: TypeNullability) =
            emptyModifiersByNullability[typeNullability.ordinal]

        /**
         * A list of empty [DefaultTypeModifiers] instances, one for each [TypeNullability] indexed
         * by [TypeNullability.ordinal].
         */
        private val emptyModifiersByNullability =
            buildList<TypeModifiers> {
                for (typeNullability in TypeNullability.entries) {
                    add(TypeModifiers(emptyList(), typeNullability))
                }
            }

        /** A set of empty, non-null [TypeModifiers] for sharing. */
        val emptyNonNullModifiers = emptyModifiers(TypeNullability.NONNULL)

        /** A set of empty, platform [TypeModifiers] for sharing. */
        val emptyPlatformModifiers = emptyModifiers(TypeNullability.PLATFORM)

        /** A set of empty, undefined [TypeModifiers] for sharing. */
        val emptyUndefinedModifiers = emptyModifiers(TypeNullability.UNDEFINED)

        /** Create a [DefaultTypeModifiers]. */
        fun create(
            annotations: List<AnnotationItem>,
            nullability: TypeNullability,
        ): TypeModifiers =
            // If the annotations are empty then use one of the predefined instances.
            if (annotations.isEmpty()) {
                emptyModifiers(nullability)
            } else {
                TypeModifiers(annotations, nullability)
            }
    }
}

/** An enum representing the possible nullness values of a type. */
enum class TypeNullability(
    /** Kotlin nullability suffix. */
    val suffix: String,
    /**
     * Indicates whether this [TypeNullability] is a known type, i.e. nullable or non-null, or an
     * unknown type, i.e. platform or undefined.
     */
    val known: Boolean,
) {
    /**
     * Nullability for a type that is annotated non-null, is primitive, or defined as non-null in
     * Kotlin.
     */
    NONNULL("", known = true),

    /** Nullability for a type that is annotated nullable or defined as nullable in Kotlin. */
    NULLABLE("?", known = true),

    /** Nullability for a Java type without a specified nullability. */
    PLATFORM("!", known = false),

    /**
     * The nullability for a type without defined nullness. Examples include:
     * - A Kotlin type variable with inherited nullability.
     * - Wildcard types (nullness is defined through the bounds of the wildcard).
     */
    UNDEFINED("", known = false),
    ;

    companion object {
        /** Given a nullness [annotation], returns the corresponding [TypeNullability]. */
        fun ofAnnotation(annotation: AnnotationItem): TypeNullability {
            return if (isNullableAnnotation(annotation.qualifiedName)) {
                NULLABLE
            } else if (isNonNullAnnotation(annotation.qualifiedName)) {
                NONNULL
            } else {
                throw IllegalStateException("Not a nullness annotation: $annotation")
            }
        }
    }
}
