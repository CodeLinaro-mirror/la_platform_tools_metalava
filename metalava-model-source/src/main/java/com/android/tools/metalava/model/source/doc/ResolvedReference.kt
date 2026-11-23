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

import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.api.surface.ReferenceCheckResult
import com.android.tools.metalava.reporter.LocationSpecificReporter

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

    /**
     * The [ReferenceCheckResult] of validating this reference via
     * [DocCommentContext.checkReference], or [ReferenceCheckResult.Valid] if all resolved items
     * were valid.
     */
    val checkResult: ReferenceCheckResult
        get() = ReferenceCheckResult.Valid

    /** Format [this] for use as the reference in a reference tag, e.g. `@link`, `@see`. */
    fun formatForTagReference(containingClassName: String?) = fullyQualifiedForm

    override fun compareTo(other: ResolvedReference) =
        fullyQualifiedForm.compareTo(other.fullyQualifiedForm)
}

/** A reference to a [PackageItem]. */
data class PackageReference(
    private val qualifiedName: String,
    override val checkResult: ReferenceCheckResult = ReferenceCheckResult.Valid,
) : ResolvedReference {
    override val fullyQualifiedForm: String
        get() = qualifiedName

    override fun toString() = "PackageReference(qualifiedName=$qualifiedName)"
}

/**
 * Check whether this [PackageItem] can be referenced from [context] (reporting invalid references
 * to [reporter]) and create a [PackageReference].
 *
 * @param context Documentation context of the referencing item.
 * @param reporter Reporter at the source location of the reference tag.
 */
internal fun PackageItem.toResolvedReference(
    context: DocCommentContext,
    reporter: LocationSpecificReporter,
): PackageReference {
    // PackageReference only retains the package name, not the PackageItem, so check whether this
    // package is accessible in documentation now while the PackageItem is available.
    val checkResult = context.checkReference(this, reporter)

    return PackageReference(qualifiedName(), checkResult = checkResult)
}

/** Base for references to type, i.e. classes and type parameters. */
sealed interface TypeReference : ResolvedReference

/** A reference to a [ClassItem]. */
data class ClassReference(
    private val qualifiedName: String,
    override val checkResult: ReferenceCheckResult = ReferenceCheckResult.Valid,
) : TypeReference {
    override val fullyQualifiedForm: String
        get() = qualifiedName

    override fun toString() = "ClassReference(qualifiedName=$qualifiedName)"
}

/**
 * Check whether this [ClassItem] can be referenced from [context] (reporting invalid references to
 * [reporter]) and create a [ClassReference].
 *
 * @param context Documentation context of the referencing item.
 * @param reporter Reporter at the source location of the reference tag.
 */
internal fun ClassItem.toResolvedReference(
    context: DocCommentContext,
    reporter: LocationSpecificReporter,
): ClassReference {
    // ClassReference only retains the class name, not the ClassItem, so check whether this class is
    // accessible in documentation now while the ClassItem is available.
    val checkResult = context.checkReference(this, reporter)

    return ClassReference(context.resolveClassReferenceName(this), checkResult = checkResult)
}

/** A reference to a [TypeParameterItem]. */
data class TypeParameterReference(private val name: String) : TypeReference {
    override val fullyQualifiedForm: String
        get() = name
}

/**
 * Create a [TypeParameterReference] from a [TypeParameterItem].
 *
 * Unlike [SelectableItem] references, type parameters cannot be hidden independently of their
 * declaring item and therefore do not require reference visibility checking.
 */
internal fun TypeParameterItem.toResolvedReference() = TypeParameterReference(name())

/** Base for references to class members, i.e. fields, constructors, methods. */
sealed interface MemberReference : ResolvedReference

/** A reference to a [FieldItem]. */
data class FieldReference(
    private val qualifiedClassName: String,
    private val memberName: String,
    override val checkResult: ReferenceCheckResult = ReferenceCheckResult.Valid,
) : MemberReference {
    override val fullyQualifiedForm = "$qualifiedClassName#$memberName"

    override fun formatForTagReference(containingClassName: String?) =
        if (qualifiedClassName == containingClassName) "#$memberName" else fullyQualifiedForm

    override fun toString() =
        "FieldReference(qualifiedClassName=$qualifiedClassName, memberName=$memberName)"
}

/**
 * Check whether this [FieldItem] can be referenced from [context] (reporting invalid references to
 * [reporter]) and create a [FieldReference].
 *
 * @param context Documentation context of the referencing item.
 * @param reporter Reporter at the source location of the reference tag.
 */
internal fun FieldItem.toResolvedReference(
    context: DocCommentContext,
    reporter: LocationSpecificReporter,
): FieldReference {
    // FieldReference only retains class and field names, not the FieldItem, so check whether this
    // field is accessible in documentation now while the FieldItem is available.
    val checkResult = context.checkReference(this, reporter)

    return FieldReference(
        context.resolveClassReferenceName(containingClass()),
        name(),
        checkResult = checkResult,
    )
}

/** A reference to a [CallableItem]. */
data class CallableReference(
    private val qualifiedClassName: String,
    private val signature: String,
    override val checkResult: ReferenceCheckResult = ReferenceCheckResult.Valid,
) : MemberReference {
    override val fullyQualifiedForm = "$qualifiedClassName#$signature"

    override fun formatForTagReference(containingClassName: String?) =
        if (qualifiedClassName == containingClassName) "#$signature" else fullyQualifiedForm

    override fun toString() =
        "CallableReference(qualifiedClassName=$qualifiedClassName, signature=$signature)"
}

/** A reference to a URI fragment, i.e. an HTML element with `id="<fragment>" attribute. */
data class UriFragmentReference(
    private val qualifiedClassName: String,
    private val fragmentName: String,
    override val checkResult: ReferenceCheckResult = ReferenceCheckResult.Valid,
) : MemberReference {
    override val fullyQualifiedForm = "$qualifiedClassName##$fragmentName"

    override fun formatForTagReference(containingClassName: String?) =
        if (qualifiedClassName == containingClassName) "##$fragmentName" else fullyQualifiedForm

    override fun toString() =
        "UriFragmentReference(qualifiedClassName=$qualifiedClassName, fragmentName=$fragmentName)"
}
