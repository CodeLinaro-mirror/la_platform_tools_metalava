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

package com.android.tools.metalava.model

/**
 * Well known types, e.g. those used as defaults for missing bounds or implicit types for language
 * constructs like enums and annotation.
 */
object WellKnownTypes {
    /** Get an instance of a well known type called [name] with [typeNullability]. */
    private fun wellKnownType(name: String, typeNullability: TypeNullability) =
        TypeItem.createClassType(
            TypeModifiers.emptyModifiers(typeNullability),
            name,
            emptyList(),
            outerClassType = null,
            isValueClassType = false,
        )

    /** Get a [TypeNullability.NONNULL] [ClassTypeItem] for [this] name. */
    internal fun String.nonNullClassType() = wellKnownType(this, TypeNullability.NONNULL)

    /** Get a [TypeNullability.NULLABLE] [ClassTypeItem] for [this] name. */
    internal fun String.nullableClassType() = wellKnownType(this, TypeNullability.NULLABLE)

    /** Get a [TypeNullability.PLATFORM] [ClassTypeItem] for [this] name. */
    internal fun String.platformClassType() = wellKnownType(this, TypeNullability.PLATFORM)

    val JAVA_LANG_ANNOTATION_NON_NULL_TYPE = JAVA_LANG_ANNOTATION.nonNullClassType()

    val JAVA_LANG_ENUM_NON_NULL_TYPE = JAVA_LANG_ENUM.nonNullClassType()

    val JAVA_LANG_OBJECT_PLATFORM_TYPE = JAVA_LANG_OBJECT.platformClassType()
    val JAVA_LANG_OBJECT_NON_NULL_TYPE = JAVA_LANG_OBJECT.nonNullClassType()
    val JAVA_LANG_OBJECT_NULLABLE_TYPE = JAVA_LANG_OBJECT.nullableClassType()

    /** The default type parameter bounds when none is provided in Kotlin source. */
    private val DEFAULT_JAVA_TYPE_PARAMETER_BOUNDS = listOf(JAVA_LANG_OBJECT_PLATFORM_TYPE)

    /** The default type parameter bounds when none is provided in Kotlin source. */
    private val DEFAULT_KOTLIN_TYPE_PARAMETER_BOUNDS = listOf(JAVA_LANG_OBJECT_NULLABLE_TYPE)

    /**
     * Get the default [TypeParameterItem.typeBounds] depending on whether this is [forKotlin] or
     * not.
     */
    fun defaultTypeParameterBounds(forKotlin: Boolean) =
        if (forKotlin) {
            DEFAULT_KOTLIN_TYPE_PARAMETER_BOUNDS
        } else {
            DEFAULT_JAVA_TYPE_PARAMETER_BOUNDS
        }
}
