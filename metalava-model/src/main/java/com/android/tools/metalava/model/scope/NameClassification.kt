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

package com.android.tools.metalava.model.scope

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ReferencableItem
import com.android.tools.metalava.model.ReferencableMethodSet
import com.android.tools.metalava.model.TypeParameterItem

/**
 * Classification of names used by the Java compiler, specifically with regard to those that are
 * used in https://docs.oracle.com/javase/specs/jls/se21/html/jls-6.html#jls-6.5.2.
 *
 * This is used to provide contextual information about the name based on where it is used. e.g.
 * * in `@see <reference>`, the `<reference>` can refer to any [ReferencableItem] so is classified
 *   as [NameClassification.AMBIGUOUS].
 * * in `@see <qualifier>.<simple-name>`, the `<qualifier>` can refer only to [ReferencableItem]s
 *   that can qualify another name, i.e. [PackageItem] or [ClassItem]s.
 * * in `@throws <reference>`, the `<reference>` can refer only to a named type, i.e. [ClassItem] or
 *   [TypeParameterItem] so is classified as [NameClassification.TYPE].
 * * in `{@if (flag(<reference>)) ...}`, the `<reference>` can refer only to a [FieldItem] so is
 *   classified as [NameClassification.FIELD].
 */
enum class NameClassification(
    val packages: Boolean = false,
    val classes: Boolean = false,
    val typeParameters: Boolean = false,
    val fields: Boolean = false,
    val methods: Boolean = false,
    val nameDescriptionPrefix: String,
) {
    /** The name is ambiguous and could refer to any [ReferencableItem]. */
    AMBIGUOUS(
        packages = true,
        classes = true,
        typeParameters = true,
        fields = true,
        methods = true,
        nameDescriptionPrefix = "",
    ),

    /**
     * The name is being used to qualify another name, e.g. before the `.` in a qualified name. That
     * means that it must refer to either a [PackageItem] or a [ClassItem].
     */
    QUALIFIER(
        packages = true,
        classes = true,
        nameDescriptionPrefix = "a package or class called ",
    ),

    /** A type name, i.e. one that can only reference to a [ClassItem] or [TypeParameterItem]. */
    TYPE(
        classes = true,
        typeParameters = true,
        nameDescriptionPrefix = "a class or type parameter called ",
    ),

    /** A class name, i.e. one that can only reference to a [ClassItem]. */
    CLASS(
        classes = true,
        nameDescriptionPrefix = "a class called ",
    ),

    /** A field name, i.e. one that can only reference a [FieldItem]. */
    FIELD(
        fields = true,
        nameDescriptionPrefix = "a field called ",
    ),

    /** A method name, i.e. one that can only reference a [ReferencableMethodItemSet]. */
    METHOD_SET(
        methods = true,
        nameDescriptionPrefix = "a method called ",
    ),
    ;

    /**
     * Run [body] if [packages] is `true` returning the result which must be `null` or a
     * [PackageItem].
     *
     * Used by code that resolves names to control whether it searches for packages or not.
     */
    inline fun findPackage(body: () -> PackageItem?) = if (packages) body() else null

    /**
     * Run [body] if [classes] is `true` returning the result which must be `null` or a [ClassItem].
     *
     * Used by code that resolves names to control whether it searches for classes or not.
     */
    inline fun findClass(body: () -> ClassItem?) = if (classes) body() else null

    /**
     * Run [body] if [typeParameters] is `true` returning the result which must be `null` or a
     * [TypeParameterItem].
     *
     * Used by code that resolves names to control whether it searches for type parameters or not.
     */
    inline fun findTypeParameter(body: () -> TypeParameterItem?) =
        if (typeParameters) body() else null

    /**
     * Run [body] if [fields] is `true` returning the result which must be `null` or a [FieldItem].
     *
     * Used by code that resolves names to control whether it searches for fields or not.
     */
    inline fun findField(body: () -> FieldItem?) = if (fields) body() else null

    /**
     * Run [body] if [methods] is `true` returning the result which must be `null` or a
     * [ReferencableMethodSet].
     *
     * Used by code that resolves names to control whether it searches for methods or not.
     */
    inline fun findMethodSet(body: () -> ReferencableMethodSet?) = if (methods) body() else null

    /** Describe the name. */
    fun describeName(name: String) = "$nameDescriptionPrefix'$name'"
}
