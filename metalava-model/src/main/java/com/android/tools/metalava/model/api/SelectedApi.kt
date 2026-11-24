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

package com.android.tools.metalava.model.api

import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.flags.AdditionFlagState
import com.android.tools.metalava.model.api.flags.FlagState
import com.android.tools.metalava.model.api.flags.optionalFlagName
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantSet
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.model.item.DefaultSelectableItem

/** Provides access to the [ApiVariantSet] to which a specific [SelectableItem] belongs. */
sealed class SelectedApi {
    /** The [ApiVariantSet] for the [SelectableItem]. */
    abstract val itemApiVariants: ApiVariantSet

    /** The [ApiVariantSet] for child items. */
    abstract val contentApiVariants: ApiVariantSet

    /**
     * The [ApiVariantSet] inherited from the super class of a [ClassItem].
     *
     * This is always empty by default except for [ClassItem]s.
     *
     * **Why this is needed:** When a class belongs to a narrower API surface than its super class
     * (e.g. a public class extending a `SystemApi` class), the class must also be included in the
     * wider API surface so that the type hierarchy in the wider API surface remains complete and
     * consistent. Tracking these variants separately from [contentApiVariants] (which tracks
     * variants from child items) allows distinguishing between variants introduced by enclosed
     * members and those introduced by the class hierarchy.
     *
     * **How it is set:** Initialized for [ClassItem]s in
     * [ClassSourceSelectedApi.itemSpecificInitialization]. If the class has a super class whose
     * narrowest API surface is wider than this class's widest API surface, the super class's
     * variants are masked to match this class's variant types (e.g. only inherit `system(C)` if
     * this class has `public(C)`) and added to this set.
     */
    open val superClassApiVariants: ApiVariantSet
        get() = ApiVariantSet.EMPTY

    /** Indicates whether the associated [SelectableItem] is being reverted. */
    abstract val revert: Boolean

    /**
     * The [ApiVariantSet] for which this method is an elidable override.
     *
     * This is always empty by default except for [MethodItem]s.
     */
    open val elidableApiVariants: ApiVariantSet
        get() = ApiVariantSet.EMPTY

    /**
     * The [SelectableItem] from the previously released API that matches this item, if this item is
     * to be reverted.
     */
    abstract val revertItem: SelectableItem?

    /**
     * The effective `@FlaggedApi` [FlagState] of the associated [SelectableItem], combining the
     * item's own `@FlaggedApi` annotation and removal state with that of any enclosing
     * [ClassItem]s.
     */
    abstract val flagState: FlagState

    /**
     * Additional `@FlaggedApi` [FlagState] guarding `@deprecated` documentation on the associated
     * [SelectableItem].
     *
     * When a previously released, non-deprecated item is newly deprecated under an unfinalized
     * `@FlaggedApi` annotation (directly or inherited from an enclosing class), the item itself has
     * [AdditionFlagState.Finalized] in [flagState] (because the item and its main documentation
     * remain in the API if the flag is reverted), whereas `CodebaseSnapshotTaker` strips its
     * `@deprecated` block tag on revert. This property captures that addition [FlagState] so
     * documentation references inside `@deprecated` to APIs guarded by an unfinalized `@FlaggedApi`
     * are treated as guarded, and returns [FlagState.NONE] in all other cases (including when the
     * item was already deprecated in the previously released API).
     */
    abstract val deprecatedFlagState: FlagState

    /**
     * Whether any `@FlaggedApi` annotation directly on the associated [SelectableItem] should be
     * ignored when computing [flagState] (e.g. when a `@RemovedFromApi @FlaggedApi` annotation on a
     * method override was invalidated because the overridden method remains active in the API).
     */
    protected open val ignoreOwnFlaggedApi: Boolean
        get() = false

    /**
     * Computes the effective [FlagState] for [item], combining its own state with that of any
     * visible enclosing [ClassItem].
     */
    protected fun computeFlagState(item: SelectableItem): FlagState {
        val ownState = computeOwnFlagState(item)
        val containingClass = item.containingClass()
        return if (
            containingClass != null && containingClass.selectedApi.itemApiVariants.isNotEmpty()
        ) {
            ownState combineWith containingClass.selectedApi.flagState
        } else {
            ownState
        }
    }

    /**
     * Computes [deprecatedFlagState] for [item].
     *
     * Only applies when [item] was previously released without deprecation, is not currently being
     * reverted, and is now effectively deprecated (matching the exact condition under which
     * `CodebaseSnapshotTaker` strips `@deprecated` when reverting).
     */
    protected fun computeDeprecatedFlagState(item: SelectableItem): FlagState {
        if (revertItem != null || !wasPreviouslyReleased() || wasPreviouslyDeprecated()) {
            return FlagState.NONE
        }
        if (!item.effectivelyDeprecated) {
            return FlagState.NONE
        }

        val isRemoved = itemApiVariants.isNotEmpty() && itemApiVariants.isHiddenOrRemoved()
        val directAnnotation = if (ignoreOwnFlaggedApi) null else findDirectFlaggedApi(item)
        return computeAdditionFlagState(
            item,
            directAnnotation,
            isRemoved,
            includeSuperMethods = false,
        )
    }

    /**
     * Computes the [FlagState] contributed by [item] (including any addition `@FlaggedApi`
     * inherited from a previously released enclosing class or overridden [MethodItem.superMethods]
     * when [item] itself is unreleased).
     */
    private fun computeOwnFlagState(item: SelectableItem): FlagState {
        // An item is in `RemovalFlagState.Removed` when all of its API variants are `REMOVED`
        // (i.e. it is non-empty and `isHiddenOrRemoved()` is true).
        val isRemoved = itemApiVariants.isNotEmpty() && itemApiVariants.isHiddenOrRemoved()
        var state = if (isRemoved) FlagState.REMOVED else FlagState.NONE

        // If the item is reverting to a previously released item, its `@FlaggedApi` annotations do
        // not apply.
        if (revertItem != null) return state

        val directAnnotation = if (ignoreOwnFlaggedApi) null else findDirectFlaggedApi(item)

        // `@RemovedFromApi` + `@FlaggedApi` schedules removal (checked independently of
        // `wasPreviouslyReleased()` because scheduled removal applies to released APIs).
        if (hasRemovedAnnotation() && directAnnotation != null) {
            val flagName = directAnnotation.optionalFlagName
            if (!flagName.isNullOrEmpty()) {
                state =
                    state combineWith
                        FlagState.removalFlag(flagName, directAnnotation.apiFlag?.action)
            }
        }

        // An addition `@FlaggedApi` only applies if `item` was not previously released.
        if (!wasPreviouslyReleased()) {
            state = state combineWith computeAdditionFlagState(item, directAnnotation, isRemoved)
        }

        return state
    }

    /**
     * Computes the addition [FlagState] from [item]'s [directAnnotation] (if non-removal), or else
     * from any compatible enclosing class's `@FlaggedApi` annotation, optionally combined with any
     * addition flags required by overridden [MethodItem.superMethods] when [includeSuperMethods] is
     * `true`.
     *
     * Used both for [computeOwnFlagState] on unreleased items (with `includeSuperMethods = true`)
     * and for [computeDeprecatedFlagState] on previously released items that are newly deprecated
     * under `@FlaggedApi` (with `includeSuperMethods = false`, since an unreleased super method's
     * `@FlaggedApi` does not revert a previously released overriding method's deprecation when its
     * enclosing superinterface or superclass is reverted).
     */
    private fun computeAdditionFlagState(
        item: SelectableItem,
        directAnnotation: AnnotationItem?,
        isRemoved: Boolean,
        includeSuperMethods: Boolean = true,
    ): FlagState {
        var state = FlagState.NONE
        val additionAnnotation =
            if (!hasRemovedAnnotation() && directAnnotation != null) {
                directAnnotation
            } else {
                findInheritedEnclosingFlaggedApi(item, isRemoved)
            }
        if (additionAnnotation != null) {
            val flagName = additionAnnotation.optionalFlagName
            if (!flagName.isNullOrEmpty()) {
                state =
                    state combineWith
                        FlagState.additionFlag(flagName, additionAnnotation.apiFlag?.action)
            }
        }

        // Combine with any addition flags required by overridden super methods so both unflagged
        // overrides of `@FlaggedApi` methods and overrides annotated with an additional
        // `@FlaggedApi` require the overridden super methods' flags as well.
        if (includeSuperMethods && item is MethodItem) {
            val superAddition = computeSuperMethodsAdditionFlagState(item)
            if (superAddition !== AdditionFlagState.Finalized) {
                state =
                    state combineWith
                        FlagState(
                            addition = superAddition,
                        )
            }
        }

        return state
    }

    /**
     * Find any `@FlaggedApi` annotation directly on [target] (or on its exposed backing field when
     * [target] is a Kotlin [PropertyItem] annotated with `@field:FlaggedApi`).
     */
    private fun findDirectFlaggedApi(target: SelectableItem): AnnotationItem? =
        target.modifiers.findAnnotation(ANDROID_FLAGGED_API)
            ?: (target as? PropertyItem)
                ?.backingField
                ?.takeIf { it.modifiers.hasApiVisibility() }
                ?.modifiers
                ?.findAnnotation(ANDROID_FLAGGED_API)

    /**
     * Walk up [item]'s enclosing classes to find the nearest visible enclosing class with a
     * compatible non-removal `@FlaggedApi` annotation.
     *
     * Even when an enclosing class was previously released (so its own [flagState] is
     * [FlagState.NONE]), [SelectedApiUpdater], `JavaStubWriter`, and `SignatureWriter` propagate
     * its `@FlaggedApi` annotation to newly added unflagged members and nested classes enclosed
     * within it — including those in wider extending API surfaces (e.g. `@SystemApi` inside a
     * `public` class) or `@doconly` variants.
     */
    private fun findInheritedEnclosingFlaggedApi(
        item: SelectableItem,
        isRemoved: Boolean,
    ): AnnotationItem? {
        var cls = item.containingClass()
        while (cls != null) {
            val clsSelectedApi = cls.selectedApi
            val clsVariants = clsSelectedApi.itemApiVariants

            // Hidden enclosing classes belong to no API variants and do not propagate flags.
            if (clsVariants.isEmpty()) break

            // Match removed items only against enclosing classes with a `REMOVED` variant (on any
            // surface) so `@removed` members do not inherit addition flags from `CORE`-only
            // classes, and match non-removed items against any active (`CORE` or `DOC_ONLY`)
            // enclosing class across surfaces.
            val compatibleVariants =
                if (isRemoved) {
                    clsVariants.containsAny(ApiVariantSet.ALL_REMOVED)
                } else {
                    !clsVariants.isHiddenOrRemoved()
                }

            // Skip enclosing classes whose `@FlaggedApi` is a removal flag or was invalidated.
            if (
                compatibleVariants &&
                    !clsSelectedApi.hasRemovedAnnotation() &&
                    !clsSelectedApi.ignoreOwnFlaggedApi
            ) {
                val annotation = findDirectFlaggedApi(cls)
                if (annotation != null) return annotation
            }
            cls = cls.containingClass()
        }
        return null
    }

    /**
     * Compute the [AdditionFlagState] inherited by [method] from its overridden
     * [MethodItem.superMethods].
     *
     * If at least one overridden active API super method is [AdditionFlagState.Finalized], returns
     * [AdditionFlagState.Finalized] because [MethodSourceSelectedApi] keeps the override in the API
     * as long as any overridden API method remains active. Otherwise, combines the addition states
     * of all overridden active API super methods (recursing across hidden or package-private
     * intermediate classes).
     */
    private fun computeSuperMethodsAdditionFlagState(method: MethodItem): AdditionFlagState {
        var combinedFlagged: AdditionFlagState? = null

        /**
         * Recursively accumulates the [AdditionFlagState] of active API methods overridden by
         * [current], returning `true` to short-circuit as soon as a [AdditionFlagState.Finalized]
         * super method is found.
         */
        fun visitSuperMethods(current: MethodItem): Boolean {
            for (superMethod in current.superMethods()) {
                val superSelectedApi = superMethod.selectedApi
                val superVariants = superSelectedApi.itemApiVariants
                if (superVariants.isEmpty()) {
                    // Hidden or package-private intermediate class; continue searching up its
                    // super methods, matching MethodSourceSelectedApi.
                    if (visitSuperMethods(superMethod)) return true
                    continue
                }
                if (superVariants.isHiddenOrRemoved()) {
                    continue
                }
                val superAddition = superSelectedApi.flagState.addition
                if (superAddition === AdditionFlagState.Finalized) {
                    combinedFlagged = AdditionFlagState.Finalized
                    return true
                }
                combinedFlagged =
                    if (combinedFlagged == null) {
                        superAddition
                    } else {
                        combinedFlagged!! combineWith superAddition
                    }
            }
            return false
        }

        visitSuperMethods(method)
        return combinedFlagged ?: AdditionFlagState.Finalized
    }

    /**
     * Checks to see if the associated [SelectableItem] contains any removed annotations (or was
     * marked with `@ForRemoval` in a signature file).
     *
     * Unlike membership in a `REMOVED` variant in [itemApiVariants], this only reflects whether the
     * item itself has a removed annotation (it is not inherited by enclosed items) and remains
     * `true` even when the removal is gated by an unfinalized `@FlaggedApi` annotation.
     */
    open fun hasRemovedAnnotation(): Boolean = false

    /**
     * Checks to see whether the associated [SelectableItem] was part of the previously released
     * API.
     */
    open fun wasPreviouslyReleased(): Boolean = false

    /**
     * Checks to see whether the associated [SelectableItem] was effectively deprecated in the
     * previously released API.
     */
    open fun wasPreviouslyDeprecated(): Boolean = false

    /**
     * Initialize this instance.
     *
     * This is called after this has been created and assigned to
     * [DefaultSelectableItem.selectedApi].
     */
    internal abstract fun initialize()

    /**
     * Add [value] to [itemApiVariants].
     *
     * This can only be called on items loaded from signature files.
     */
    abstract fun addItemApiVariant(value: ApiVariant)

    /**
     * Mark that the associated [SelectableItem] has a removed annotation.
     *
     * This can only be called on items loaded from signature files.
     */
    open fun markHasRemovedAnnotation() {
        error("Cannot update hasRemovedAnnotation in $this")
    }

    /**
     * Populate this instance with the state from the [original] [SelectedApi] of the item being
     * snapshotted.
     *
     * This can only be called when creating a snapshot of the codebase using a
     * [SelectedApi.SNAPSHOT_FACTORY].
     */
    abstract fun snapshot(original: SelectedApi)

    companion object {
        /**
         * Return a [SelectedApi] factory that will create [SelectedApi] instances suitable for
         * being populated from a signature file.
         */
        val MUTABLE_FACTORY: (SelectableItem) -> SelectedApi = { item ->
            when (item) {
                is PackageItem -> MutablePackageSelectedApi(item)
                is ClassItem -> MutableClassSelectedApi(item)
                is MemberItem -> MutableMemberSelectedApi(item)
                else -> error("unknown selectable item: $item")
            }
        }

        /**
         * Return a [SelectedApi] factory that will create [SelectedApi] instances suitable for a
         * snapshot [Codebase].
         */
        val SNAPSHOT_FACTORY: (SelectableItem) -> SelectedApi = { SnapshotSelectedApi() }

        /**
         * Create a [SelectedApi] factory that will create [SelectedApi] instances suitable for a
         * [Codebase] created from [config].
         */
        fun sourceFactory(config: Codebase.Config): (SelectableItem) -> SelectedApi {
            // Get the ApiSurfaceSelector that is used by the AnnotationManager.
            val annotationManager = config.annotationManager
            val apiSurfaceSelector = annotationManager.apiSurfaceSelector

            // Create an updater that will be captured by the factory below and will be used by all
            // SelectedApi instances in the Codebase that uses tha factory.
            val selectedApiUpdater =
                SelectedApiUpdater(
                    config.reporter,
                    apiSurfaceSelector,
                    config.previouslyReleasedCodebaseProvider,
                )
            return { item -> createFromSource(selectedApiUpdater, item) }
        }

        /** Create a [SelectedApi] for a source [item]. */
        fun createFromSource(
            selectedApiUpdater: SelectedApiUpdater,
            item: SelectableItem,
        ): SelectedApi =
            when (item) {
                is ClassItem -> ClassSourceSelectedApi(selectedApiUpdater, item)
                is MethodItem -> MethodSourceSelectedApi(selectedApiUpdater, item)
                is ConstructorItem -> ConstructorSourceSelectedApi(selectedApiUpdater, item)
                is PropertyItem -> PropertySourceSelectedApi(selectedApiUpdater, item)
                is MemberItem -> MemberSourceSelectedApi(selectedApiUpdater, item)
                is PackageItem -> PackageSourceSelectedApi(selectedApiUpdater, item)
                else -> error("unknown selectable item: $item")
            }
    }
}
