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

package com.android.tools.metalava.model.source.doc

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TypeParameterItem

/**
 * Base for resolved references to some part of the API, e.g. [SelectableItem]s or
 * [TypeParameterItem]s.
 *
 * This allows the caller to differentiate between the different resolved types without depending on
 * [Item]s that would cause issues when taking a snapshot.
 */
sealed interface ResolvedReference : Comparable<ResolvedReference> {
    /** The fully qualified form of the referenced type. */
    val fullyQualifiedForm: String

    /** Format [this] for use as the reference in a reference tag, e.g. `@link`, `@see`. */
    fun formatForTagReference(containingClassName: String?) = fullyQualifiedForm

    override fun compareTo(other: ResolvedReference) =
        fullyQualifiedForm.compareTo(other.fullyQualifiedForm)

    /**
     * Check whether this could possibly rely on the [importedName].
     *
     * Returns `true` if the reference has not been fully resolved and the partially resolved parts
     * contain [importedName] as a separate word.
     */
    fun referenceCouldRelyOnImportedName(importedName: String) = false
}

/** A reference to a [PackageItem]. */
data class PackageReference(private val qualifiedName: String) : ResolvedReference {
    override val fullyQualifiedForm: String
        get() = qualifiedName
}

/** Create a [PackageReference] from a [PackageItem]. */
internal fun PackageItem.toResolvedReference() = PackageReference(qualifiedName())

/** Base for references to type, i.e. classes and type parameters. */
sealed interface TypeReference : ResolvedReference

/** A reference to a [ClassItem]. */
data class ClassReference(private val qualifiedName: String) : TypeReference {
    override val fullyQualifiedForm: String
        get() = qualifiedName
}

/** Create a [ClassReference] from a [ClassItem]. */
internal fun ClassItem.toResolvedReference() = ClassReference(qualifiedName())

/** A reference to a [TypeParameterItem]. */
data class TypeParameterReference(private val name: String) : TypeReference {
    override val fullyQualifiedForm: String
        get() = name
}

/** Create a [TypeParameterReference] from a [TypeParameterItem]. */
internal fun TypeParameterItem.toResolvedReference() = TypeParameterReference(name())

/** Base for references to class members, i.e. fields, constructors, methods. */
sealed interface MemberReference : ResolvedReference

/** A reference to a [FieldItem]. */
data class FieldReference(
    private val qualifiedClassName: String,
    private val memberName: String,
) : MemberReference {
    override val fullyQualifiedForm = "$qualifiedClassName#$memberName"

    override fun formatForTagReference(containingClassName: String?) =
        if (qualifiedClassName == containingClassName) "#$memberName" else fullyQualifiedForm
}

/** Create a [FieldReference] from a [FieldItem]. */
internal fun FieldItem.toResolvedReference() =
    FieldReference(containingClass().qualifiedName(), name())

/** A reference to a [MethodItem]. */
data class MethodReference(
    private val qualifiedClassName: String,
    private val signature: String,
) : MemberReference {
    override val fullyQualifiedForm = "$qualifiedClassName#$signature"

    override fun formatForTagReference(containingClassName: String?) =
        if (qualifiedClassName == containingClassName) "#$signature" else fullyQualifiedForm
}
