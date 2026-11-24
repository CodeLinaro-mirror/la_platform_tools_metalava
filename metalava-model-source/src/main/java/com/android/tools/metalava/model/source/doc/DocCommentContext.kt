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
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.flags.FlagState
import com.android.tools.metalava.model.api.surface.ReferenceCheckResult
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
     * Check whether a documentation reference to [referencedItem] is valid from the documented
     * item, reporting any invalid reference (such as `HiddenDocReference`) to [reporter].
     *
     * @param referencedItem The resolved target [SelectableItem] referenced by the doc tag.
     * @param reporter Location-specific reporter for the reference tag in the doc comment.
     * @param conditionFlagState Any conditional [FlagState] enclosing the reference (for example,
     *   from `{@if (flag(...))}`, `@if (flag(...))`, or `@deprecated` on a newly deprecated
     *   `@FlaggedApi` item).
     * @return The [ReferenceCheckResult] indicating whether the reference is valid or why it is
     *   invalid.
     */
    fun checkReference(
        referencedItem: SelectableItem,
        reporter: LocationSpecificReporter,
        conditionFlagState: FlagState = FlagState.NONE,
    ): ReferenceCheckResult

    /**
     * Additional [FlagState] guarding `@deprecated` block tag sections on the documented item
     * (evaluated lazily when checking references inside `@deprecated` sections).
     */
    val deprecatedSectionFlagState: FlagState

    /**
     * Return a [DocCommentContext] that includes [flagState] in the active conditional [FlagState]
     * passed to [checkReference] (or `this` if [flagState] is [FlagState.NONE]).
     */
    fun withConditionFlagState(flagState: FlagState): DocCommentContext =
        if (flagState === FlagState.NONE) this
        else ScopedDocCommentContext(this, conditionFlagState = flagState)

    /**
     * Return a [DocCommentContext] for a `@deprecated` block tag section that lazily combines
     * [deprecatedSectionFlagState] when [checkReference] is invoked.
     */
    fun forDeprecatedSection(): DocCommentContext =
        ScopedDocCommentContext(this, inDeprecatedSection = true)

    /**
     * Resolve the qualified class name to emit in documentation references for [classItem].
     *
     * Defaults to [ClassItem.qualifiedName], unless [classItem] is an annotation class that is
     * mapped to a different annotation in documentation stubs (for example,
     * `android.annotation.Nullable` mapped to `androidx.annotation.Nullable`).
     */
    fun resolveClassReferenceName(classItem: ClassItem): String = classItem.qualifiedName()

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

    /**
     * The optional [ClassItem] that contains this documentation.
     *
     * The value returned depends on the [SelectableItem] this documents:
     * * For a [PackageItem] this will return `null`.
     * * For a [ClassItem] this will just return the [ClassItem] itself.
     * * For a [MemberItem] this will return [MemberItem.containingClass].
     */
    val containingClassItem: ClassItem?

    /**
     * Whether the documented item originates from Kotlin source.
     *
     * KDoc uses `[...]` links rather than Javadoc `{@link ...}` tags, and Kotlin stubs are not
     * supported, so Javadoc reference resolution is skipped when `true`.
     */
    val isKotlin: Boolean
        get() = false

    /** Provides support for parsing type references within Javadoc content. */
    val docTypeParser: DocTypeParser
}

/**
 * Flat delegating [DocCommentContext] that combines any active `@if` / `{@if}` [conditionFlagState]
 * and (when [inDeprecatedSection] is `true`, evaluated lazily at [checkReference] time)
 * [DocCommentContext.deprecatedSectionFlagState].
 */
private class ScopedDocCommentContext(
    private val delegate: DocCommentContext,
    private val conditionFlagState: FlagState = FlagState.NONE,
    private val inDeprecatedSection: Boolean = false,
) : DocCommentContext by delegate {
    override fun checkReference(
        referencedItem: SelectableItem,
        reporter: LocationSpecificReporter,
        conditionFlagState: FlagState,
    ): ReferenceCheckResult {
        var effectiveState = this.conditionFlagState combineWith conditionFlagState
        if (inDeprecatedSection) {
            effectiveState = effectiveState combineWith delegate.deprecatedSectionFlagState
        }
        return delegate.checkReference(referencedItem, reporter, effectiveState)
    }

    override fun withConditionFlagState(flagState: FlagState): DocCommentContext {
        if (flagState === FlagState.NONE) return this
        val combined = this.conditionFlagState combineWith flagState
        if (combined == this.conditionFlagState) return this
        return ScopedDocCommentContext(delegate, combined, inDeprecatedSection)
    }

    override fun forDeprecatedSection(): DocCommentContext =
        if (inDeprecatedSection) this
        else ScopedDocCommentContext(delegate, conditionFlagState, inDeprecatedSection = true)
}
