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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.api.flags.ApiFlagAction
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantSet
import com.android.tools.metalava.reporter.Issues

/** Base [SelectedApi] class for use on [SelectableItem]s created from sources. */
internal sealed class SourceSelectedApi<S : SelectableItem>(
    internal val selectedApiUpdater: SelectedApiUpdater,
    internal val item: S,
) : SelectedApi() {
    /**
     * The parent [SourceSelectedApi], used for propagating information up to the parent
     * [SourceSelectedApi].
     *
     * e.g. A package belongs in the API surfaces of all its top level child classes. That requires
     * the child classes propagate information about the API surfaces to which they belong up to the
     * parent package.
     *
     * This is the [SelectableItem.selectedApi] for [item]'s [SelectableItem.parent]. If the latter
     * is `null`, i.e. [item] is the root package then this will refer to [item]. That avoids having
     * to check this for `null` every time it is used at the expense of have a cycle at the top.
     *
     * The cycle should not be an issue as while packages are hierarchical when it comes to hiding
     * them they are otherwise flat. That means a [PackageSourceSelectedApi] will never try and
     * propagate information to its parent. So, the root [PackageSourceSelectedApi] will never use
     * its [parent].
     *
     * Initialized in [initialize] which is called after creation but before the object is stored
     * anywhere so it is impossible for this to be accessed before [initialize] has been called so
     * there is no need to check is this has been initialized before using it.
     */
    internal lateinit var parent: SourceSelectedApi<*>

    /**
     * Indicates whether the associated [SelectableItem] is accessible as part of an API.
     *
     * An item is accessible if its enclosing item (parent) is accessible and its visibility level
     * allows API access.
     */
    var accessible: Boolean = false
        internal set

    /**
     * Indicates whether the associated [SelectableItem] is explicitly hidden (e.g. via `@hide` or a
     * hide annotation, or inherited from an enclosing item that was explicitly hidden).
     */
    var explicitlyHidden: Boolean = false
        internal set

    /**
     * Indicates whether the associated [SelectableItem] has a doc only annotation.
     *
     * Initialized by [SelectedApiUpdater.updateSelectedApi] called from [updateFromSelectableItem].
     */
    var docOnly: Boolean = false
        internal set

    /**
     * Indicates whether the associated [SelectableItem] has a removed annotation.
     *
     * Initialized by [SelectedApiUpdater.updateSelectedApi] called from [updateFromSelectableItem].
     */
    var removed: Boolean = false
        internal set

    /**
     * Indicates whether the associated [SelectableItem] is being reverted.
     *
     * Initialized by [SelectedApiUpdater.updateSelectedApi] called from [updateFromSelectableItem].
     */
    override var revert: Boolean = false

    /**
     * The [SelectableItem] from the previously released API that matches this item, if this item is
     * being reverted.
     *
     * Initialized by [SelectedApiUpdater.updateSelectedApi] called from [updateFromSelectableItem].
     */
    override var revertItem: SelectableItem? = null

    /**
     * The maximum valid [ApiFlagAction] (by lifecycle permanence: `REVERT < KEEP < FINALIZE`)
     * allowed for `@FlaggedApi` annotations on [item] and any items it encloses.
     *
     * Initialized from `parent.maxValidFlagAction` by [SelectedApiUpdater.updateSelectedApi] (which
     * defaults to [ApiFlagAction.FINALIZE] at the root package) and lowered by
     * [SelectedApiUpdater.checkFlaggedApi] if [item] itself has a `@FlaggedApi` annotation with a
     * less permanent [ApiFlagAction]. This allows enclosed items with `@FlaggedApi` annotations to
     * immediately detect whether any enclosing `@FlaggedApi` is in a conflicting (less permanent)
     * state (`action > parent.maxValidFlagAction`) without walking the ancestor chain.
     *
     * Unlike [flaggedApiAnnotation], this is never cleared once set, so subsequent enclosed items
     * with conflicting `@FlaggedApi` annotations will still detect the conflict even after the
     * ancestor's annotation has been reported.
     */
    var maxValidFlagAction: ApiFlagAction = ApiFlagAction.FINALIZE
        internal set

    /**
     * The non-finalized `@FlaggedApi` [AnnotationItem] directly on [item] if it has not yet had
     * [Issues.INVALID_FLAG_NESTING] reported on it, or `null` otherwise.
     *
     * Set by [SelectedApiUpdater.checkFlaggedApi] when a `@FlaggedApi` annotation with an action
     * other than [ApiFlagAction.FINALIZE] is found on [item], and cleared to `null` by
     * [SelectedApiUpdater.reportConflictingOuterFlags] once [Issues.INVALID_FLAG_NESTING] has been
     * reported on it. Clearing this ensures that an outer `@FlaggedApi` annotation is reported at
     * most once, even if it encloses multiple conflicting `@FlaggedApi` annotations.
     */
    var flaggedApiAnnotation: AnnotationItem? = null
        internal set

    /**
     * The [ApiVariantSet] for the [item].
     *
     * This is initialized in [initialize] which must have been called and which must initialize
     * this before it is accessed.
     */
    override var itemApiVariants = ApiVariantSet.EMPTY

    override var contentApiVariants = ApiVariantSet.EMPTY

    /**
     * The [ApiVariantSet] that will be inherited by [SelectableItem]s enclosed within [item].
     *
     * This is initialized in [initialize] which must have been called and which must initialize
     * this before it is accessed.
     *
     * This is tracked separately to [itemApiVariants] for a couple of reasons:
     * * Non-recursive show annotations can include an item in a surface without automatically
     *   including enclosed items.
     * * [itemApiVariants] can be modified by enclosed items, e.g. a package's [itemApiVariants] is
     *   the aggregate of all its classes.
     */
    var inheritableApiVariants = ApiVariantSet.EMPTY

    /** Checks to see if the associated [SelectableItem] contains any removed annotations. */
    override fun hasRemovedAnnotation() = removed

    final override fun initialize() {
        // Initialize the parent first.
        parent =
            item.parent().let { parentItem ->
                if (parentItem == null) {
                    // Initialize inheritableApiVariants for the root package to the default variant
                    // set so that any unannotated items will inherit the correct surfaces. This is
                    // done here as otherwise this will be accessed in [updateFromSelectableItem]
                    // before it is initialized.
                    inheritableApiVariants = selectedApiUpdater.defaultVariantSet
                    accessible = true
                    // Use this as its own parent to avoid having to make parent nullable.
                    this
                } else {
                    parentItem.selectedApi as? SourceSelectedApi<*>
                        // This error should never happen as all items in a codebase use the same
                        // SelectedApi factory.
                        ?: error("Incompatible selectable items for $item and $parentItem")
                }
            }

        // Perform any item specific initialization.
        itemSpecificInitialization()

        // Update deprecated status from Javadoc for items that are part of the API surface and are
        // emitted. Since Javadoc parsing is expensive, defer checking and updating the deprecation
        // status until an item is determined to be part of an API surface, and only for emitted
        // items (avoiding Javadoc parsing for non-emitted items like classpath dependencies or
        // skipped packages).
        if (item.emit && itemApiVariants.isNotEmpty()) {
            item.updateDeprecatedFromJavadocIfNeeded()
        }
    }

    /**
     * Updates the deprecated status of this item from Javadoc if needed.
     *
     * In Java, an item can be deprecated using the `@deprecated` Javadoc tag or the `@Deprecated`
     * annotation. This method checks the Javadoc documentation for a `@deprecated` tag and, if
     * found, marks the item as deprecated in its modifiers.
     *
     * This check is deferred from initialization to avoid the overhead of parsing documentation for
     * every item when constructing the codebase model.
     */
    private fun SelectableItem.updateDeprecatedFromJavadocIfNeeded() {
        // Only Java items can get deprecated status from javadoc.
        if (sourceLanguage != SourceLanguage.JAVA) return

        // If the item is already deprecated then no point in checking javadoc, at least not here.
        if (modifiers.isDeprecated()) return

        // If the documentation does not have an @deprecated block then the item is not deprecated.
        if (documentation?.hasBlockTagOfType("deprecated") != true) return

        // The item is deprecated.
        mutateModifiers { setDeprecated(true) }
    }

    /** Update this from information in [item]. */
    fun updateFromSelectableItem() {
        selectedApiUpdater.updateSelectedApi(this, parent)
    }

    /** Adopt the status from [other]. */
    fun adoptStatusFrom(other: SourceSelectedApi<*>) {
        itemApiVariants = other.itemApiVariants
        inheritableApiVariants = other.inheritableApiVariants
        docOnly = other.docOnly
        removed = other.removed
        revert = other.revert
        revertItem = other.revertItem
        explicitlyHidden = other.explicitlyHidden
    }

    override fun addItemApiVariant(value: ApiVariant) {
        error("Cannot update itemApiVariants in $this")
    }

    override fun snapshot(original: SelectedApi) {
        error("Cannot populate $this in a snapshot")
    }

    /**
     * Perform any item specific initialization.
     *
     * This is called after [parent] has been initialized, and it is the responsibility of this to
     * call [updateFromSelectableItem] to update the state before accessing the
     * [SelectableItem.selectedApi] of any enclosed items.
     */
    abstract fun itemSpecificInitialization()

    /** Propagate the [childVariants] to this parent item. */
    abstract fun propagateFromChild(childVariants: ApiVariantSet)

    /**
     * Returns whether the children of this [SourceSelectedApi] are completely hidden, i.e. in all
     * API surfaces.
     *
     * If it returns `true` then all the children of the [item] will be marked as hidden in all API
     * surfaces.
     */
    abstract fun areChildrenCompletelyHidden(): Boolean

    override fun toString() = buildString {
        append("SourceSelectedApi(")

        append("item=")
        append(item)
        append(", accessible=")
        append(accessible)
        append(", itemApiVariants=")
        append(itemApiVariants.formatFor(selectedApiUpdater.apiSurfaces))
        append(", inheritableApiVariants=")
        append(inheritableApiVariants.formatFor(selectedApiUpdater.apiSurfaces))
        append(", contentApiVariants=")
        append(contentApiVariants.formatFor(selectedApiUpdater.apiSurfaces))
        if (superClassApiVariants.isNotEmpty()) {
            append(", superClassApiVariants=")
            append(superClassApiVariants.formatFor(selectedApiUpdater.apiSurfaces))
        }
        if (superMethodApiVariants.isNotEmpty()) {
            append(", superMethodApiVariants=")
            append(superMethodApiVariants.formatFor(selectedApiUpdater.apiSurfaces))
        }
        if (elidableApiVariants.isNotEmpty()) {
            append(", elidableApiVariants=")
            append(elidableApiVariants.formatFor(selectedApiUpdater.apiSurfaces))
        }
        append(", revert=")
        append(revert)
        append(", revertItem=")
        append(revertItem)
        append(")")
    }
}
