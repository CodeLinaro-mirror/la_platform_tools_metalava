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

package com.android.tools.metalava.model.source.doc

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.source.javadoc.ExprBuilderContext
import com.android.tools.metalava.model.source.javadoc.ExprContext
import com.android.tools.metalava.reporter.LocationSpecificReporter

/**
 * Provides contextual information from the surrounding model for use when processing a
 * [DocComment].
 *
 * This purposely does not include [DocumentationIssueReporter] as there are multiple instances of
 * that created at different levels within the [DocComment] whereas this applies to the whole
 * [DocComment].
 */
internal interface DocCommentContext : ExprBuilderContext, ExprContext {
    /**
     * The [DocCommentMutationListener] whose [DocCommentMutationListener.docCommentMutated] must be
     * invoked when the [DocComment] is changed.
     */
    val mutationListener: DocCommentMutationListener

    /**
     * Compute the ordinal value for parameter [name] in the list of all `@param` tags.
     *
     * The `@param` tags can be used for type and callable parameters, sometimes in the same list.
     * They should be in the following order:
     * 1. `@param` tags for type parameters in type parameter list order.
     * 2. `@param` tags for unknown type parameters.
     * 3. `@param` tags for callable parameters in parameter list order.
     * 4. `@param` tags for unknown callable parameters.
     *
     * @param name will be wrapped inside `<...>` if it is a type parameter, otherwise it is a
     *   callable parameter.
     */
    fun ordinalInParamsList(name: String): Int

    /**
     * Check to see whether the comment is on an overriding method and so may require insertion of
     * `{@inheritDoc}` tags when appending content to preserve the developer's intended behavior.
     *
     * @return `true` if the commented [SelectableItem] is a [MethodItem] that has at least one
     *   [MethodItem.superMethods].
     */
    fun isOverridingMethod(): Boolean

    /** Fully qualify the Javadoc [comment]. */
    fun fullyQualifyComment(comment: String): String

    /**
     * Resolve [typeName] (which may be a reference to a class or a type parameter) to a
     * [TypeReference], if possible.
     */
    fun resolveThrowableType(reporter: LocationSpecificReporter, typeName: String): TypeReference?

    /**
     * Resolve [sourceReference] (which may be a reference to a package, class, type parameter,
     * constructor, method, or field) to a [ResolvedReference], if possible.
     */
    fun resolveReference(
        reporter: LocationSpecificReporter,
        sourceReference: String
    ): ResolvedReference?

    /**
     * The optional [ClassItem] that contains this documentation.
     *
     * The value returned depends on the [SelectableItem] this documents:
     * * For a [PackageItem] this will return `null`.
     * * For a [ClassItem] this will just return the [ClassItem] itself.
     * * For a [MemberItem] this will return [MemberItem.containingClass].
     */
    val containingClassItem: ClassItem?
}

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
}

/** A reference to a [PackageItem]. */
data class PackageReference(private val qualifiedName: String) : ResolvedReference {
    override val fullyQualifiedForm: String
        get() = qualifiedName
}

/** Base for references to type, i.e. classes and type parameters. */
sealed interface TypeReference : ResolvedReference

/** A reference to a [ClassItem]. */
data class ClassReference(private val qualifiedName: String) : TypeReference {
    override val fullyQualifiedForm: String
        get() = qualifiedName
}

/** A reference to a [TypeParameterItem]. */
data class TypeParameterReference(private val name: String) : TypeReference {
    override val fullyQualifiedForm: String
        get() = name
}

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
