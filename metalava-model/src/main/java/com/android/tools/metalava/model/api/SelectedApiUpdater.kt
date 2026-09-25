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
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.SurfaceSelectionRule.Effect
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantSet
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter

/** Provides support for updating [SourceSelectedApi] instances. */
class SelectedApiUpdater(
    private val reporter: Reporter,
    apiSurfaceSelector: ApiSurfaceSelector,
    previouslyReleasedCodebaseProvider: () -> Codebase?,
) {
    /** The [ApiSurfaces] with which this will associate [SelectableItem]s */
    internal val apiSurfaces = apiSurfaceSelector.apiSurfaces

    /** Whether to include additional overrides when matching method signatures. */
    internal val addAdditionalOverrides = apiSurfaceSelector.addAdditionalOverrides

    /**
     * The default set of variants that are used on unannotated items.
     *
     * If unannotated items are not included in any surface then it is empty, otherwise it is the
     * default variants for the unannotated surface.
     */
    internal val defaultVariantSet =
        apiSurfaceSelector.unannotatedApiSurface?.defaultVariantSet ?: ApiVariantSet.EMPTY

    /** Only check for hidden show annotations if it is not suppressed. */
    private val checkHiddenShowAnnotations = !reporter.isSuppressed(Issues.HIDDEN_SHOW_ANNOTATION)

    /** Check whether this [SelectableItem] has an `@hide` doc tag. */
    private val SelectableItem.hasHideDocTag: Boolean
        get() = documentation?.isHidden == true

    /** Check whether this [SelectableItem] has an `@removed` doc tag. */
    private val SelectableItem.hasRemovedDocTag: Boolean
        get() = documentation?.isRemoved == true

    private val previouslyReleasedCodebase by
        lazy(LazyThreadSafetyMode.NONE) { previouslyReleasedCodebaseProvider() }

    /**
     * Find the item to which [item] will be reverted.
     *
     * Searches the previously released API (if available).
     */
    private fun findRevertItem(item: SelectableItem) =
        findRevertItem(reporter, previouslyReleasedCodebase, item)

    /** Mark [selectedApi] as being hidden. */
    internal fun markAsHidden(selectedApi: SourceSelectedApi<*>, revert: Boolean) {
        selectedApi.revert = revert
        selectedApi.revertItem = null
        // A hidden item does not belong to any API surfaces.
        selectedApi.itemApiVariants = ApiVariantSet.EMPTY
        selectedApi.inheritableApiVariants = ApiVariantSet.EMPTY
    }

    /** Mark this [SourceSelectedApi] as being hidden. */
    private fun SourceSelectedApi<*>.markAsHidden(revert: Boolean) =
        this@SelectedApiUpdater.markAsHidden(this, revert)

    /**
     * Update [selectedApi] with information about [ApiVariant]s to which the
     * [SourceSelectedApi.item] belongs.
     */
    internal fun updateSelectedApi(
        selectedApi: SourceSelectedApi<*>,
        parent: SourceSelectedApi<*>,
    ) {
        // Get the item that owns selectedApi.
        val item = selectedApi.item

        // An item inside an inaccessible enclosing item (or an item without API visibility)
        // is inaccessible and cannot be selected as part of an API surface. An internal item is
        // only accessible if it is annotated with @PublishedApi and that is a show annotation.
        val accessible = parent.accessible && item.modifiers.hasApiVisibility()
        if (!accessible) {
            selectedApi.markAsHidden(revert = false)
            return
        }

        if (item.isAidlClassThatShouldBeHidden()) {
            selectedApi.markAsHidden(revert = false)
            return
        }

        // Mark selectedApi as accessible so that enclosed items can inherit accessibility from it.
        selectedApi.accessible = true

        val enclosingApiVariants = parent.inheritableApiVariants

        // Keep track of the ApiVariants to which the context item belong. Is `null` to avoid
        // creating a MutableApiVariantSet when most items are unannotated.
        var itemApiVariants = ApiVariantSet.EMPTY

        // Keep track of the ApiVariants which the context item's enclosed items will inherit.
        var inheritableApiVariants = ApiVariantSet.EMPTY

        // Indicates whether a hide annotation has been seen. Hide annotations are superseded by
        // show annotations so any processing of a hide annotation is deferred until after all
        // annotations have been checked. Hide annotations are always recursive so this just tracks
        // whether one has been seen.
        var hide = false

        var revert = false

        // Iterate over the annotations, checking to see if any match the surface rules.
        val annotations = item.modifiers.annotations()
        for (annotationItem in annotations) {
            // Ignore any annotation that does not match.
            annotationItem.surfaceData?.let { surfaceData ->
                when (surfaceData.effect) {
                    Effect.SHOW -> {
                        val resultSurface = surfaceData.surface ?: return@let

                        // It is a show annotation so add the context surfaces variants.
                        val resultVariants = resultSurface.defaultVariantSet

                        // Add the surface variants to the variants for the context item.
                        itemApiVariants += resultVariants

                        // If the rule is recursive then add the surface variants to those that are
                        // inherited by enclosed items.
                        if (surfaceData.recursive) {
                            inheritableApiVariants += resultVariants
                        }
                    }
                    Effect.HIDE -> {
                        // A hide annotation was seen.
                        hide = true
                    }
                    Effect.DOC_ONLY -> {
                        // Only track doc only on classes.
                        if (item is ClassItem) {
                            selectedApi.docOnly = true
                        }
                    }
                    Effect.REMOVED -> {
                        selectedApi.removed = true
                    }
                }
            }
                ?: annotationItem.apiFlag?.let { apiFlag ->
                    if (apiFlag.revert) {
                        revert = true
                    }
                }
        }

        // If the parent needs to hide its children then mark this child as hidden and return
        // immediately.
        if (parent.areChildrenCompletelyHidden()) {
            // Check if this item has a show annotation while the parent was explicitly hidden,
            // reporting SHOWING_MEMBER_IN_HIDDEN_CLASS if so.
            if (itemApiVariants.isNotEmpty()) {
                checkParentIsVisible(item, parent)
            }

            // Propagate explicitlyHidden so that if this item is a nested class, its enclosing
            // state is preserved for its own children.
            selectedApi.explicitlyHidden = hide || parent.explicitlyHidden || item.hasHideDocTag

            selectedApi.markAsHidden(revert = false)
            return
        }

        if (!revert) {
            if (item.containingClass()?.isMarkedForRevert() == true) {
                revert = true
            } else if (item is MethodItem) {
                // If any of a method's super methods are part of a unstable API that needs to be
                // reverted then treat the method as if it is too.
                revert = item.superMethods().any { methodItem -> methodItem.isMarkedForRevert() }
            }
        }

        var revertedItem: SelectableItem? = null
        if (revert) {
            revertedItem = findRevertItem(item)
            if (revertedItem == null) {
                // If the item was hidden then neither the context item nor its enclosed items
                // belong to any api variants.
                selectedApi.markAsHidden(revert = true)
                return
            } else {
                // The codebase contains items which are to be reverted to previously released
                // items.
                item.codebase.markContainsRevertedItem()
            }
        }

        // If any annotations matched then check for an overlap.
        if (itemApiVariants.isNotEmpty()) {
            val narrowestSurface = itemApiVariants.narrowestSurfaceFor(apiSurfaces)
            if (
                narrowestSurface != null &&
                    narrowestSurface !== itemApiVariants.widestSurfaceFor(apiSurfaces)
            ) {
                reportOverlappingSurfaces(item)

                // If an item is in multiple surfaces then restrict it to the narrowest surface as
                // that will also make it available in any extending surfaces.
                itemApiVariants = itemApiVariants.intersectionWith(narrowestSurface.variantSet)
                if (inheritableApiVariants.isNotEmpty()) {
                    inheritableApiVariants =
                        inheritableApiVariants.intersectionWith(narrowestSurface.variantSet)
                }
            }

            // Ensure that an item with show annotations is not explicitly marked with @hide.
            if (checkHiddenShowAnnotations) {
                checkEnsureShowAnnotationsAreNotExplicitlyHidden(item)
            }
        }

        // Check to see if any show rules matched; if they had then they would have set
        // itemApiVariants to non-null.
        if (itemApiVariants.isEmpty()) {
            // No show rules matched. Check to see if the context item should be hidden.

            // If no hide annotations were found then check for @hide doc tag or inherited
            // explicitly hidden.
            if (!hide) {
                hide = item.hasHideDocTag || parent.explicitlyHidden
            }

            if (hide) {
                selectedApi.explicitlyHidden = true
                // Mark the selectedApi as being hidden.
                selectedApi.markAsHidden(revert = false)

                // Return immediately to avoid falling through.
                return
            }

            // The context item did not specify the API surfaces to which it belongs so use the
            // enclosing item's API variants for the context item and its enclosed items.
            itemApiVariants =
                if (enclosingApiVariants.isNotEmpty()) {
                    enclosingApiVariants
                } else {
                    ApiVariantSet.EMPTY
                }
            inheritableApiVariants = enclosingApiVariants
        }

        // A file facade class does not belong to any API surfaces directly. Instead, it is only
        // included in surfaces to which its members belong.
        if (item is ClassItem && item.isFileFacade) {
            itemApiVariants = ApiVariantSet.EMPTY
        }

        // Get the API surface to which the item belongs.
        val surface = itemApiVariants.narrowestSurfaceFor(apiSurfaces)
        if (surface != null) {
            // Verify that the item is in only a single surface. That should be guaranteed by the
            // code above that handles overlapping surfaces. However, it is possible that some
            // problems with the enclosing API variants may break that guarantee so verify it here
            require(surface === itemApiVariants.widestSurfaceFor(apiSurfaces)) {
                "$item must not contain multiple surfaces - ${itemApiVariants.formatFor(apiSurfaces)}"
            }

            // If this item is not already removed but is enclosed within a removed parent or has a
            // doc tag of @removed then make it removed.
            if (!selectedApi.removed && (parent.removed || item.hasRemovedDocTag)) {
                selectedApi.removed = true
            }

            // If this item is not already doc-only but is enclosed within a doc-only parent then
            // make it doc-only.
            if (!selectedApi.docOnly && parent.docOnly) {
                selectedApi.docOnly = true
            }

            // If the item is removed or doc-only then update its api variants. The removed state is
            // checked first because removed takes priority over doc-only, i.e. a removed item in a
            // doc-only class will not be documented because it has been removed.
            if (selectedApi.removed) {
                // The item is marked as removed, or a member of a removed class so set the API
                // variants to only contain the removed variant in the target surface.
                val variant = surface.variantFor(ApiVariantType.REMOVED)
                itemApiVariants = apiSurfaces.createVariantSet(variant)
            } else if (selectedApi.docOnly) {
                // The item is marked as doc-only, or a member of a doc-only class so set the API
                // variants to only contain the doc-only variant in the target surface.
                val variant = surface.variantFor(ApiVariantType.DOC_ONLY)
                itemApiVariants = apiSurfaces.createVariantSet(variant)
            }
        }

        // Store the revert state in selectedApi.
        selectedApi.revert = revert
        selectedApi.revertItem = revertedItem

        // If the item was reverted to a previously released item, adopt the API variants from the
        // previously released item rather than the variants computed from this item's annotations.
        val revertedApiVariants = revertedItem?.selectedApi?.itemApiVariants
        val actualItemApiVariants = revertedApiVariants ?: itemApiVariants
        val actualInheritableApiVariants = revertedApiVariants ?: inheritableApiVariants

        // Store the variant sets in selectedApi.
        selectedApi.itemApiVariants = actualItemApiVariants
        selectedApi.inheritableApiVariants = actualInheritableApiVariants
    }

    /**
     * Called when [item] is annotated with multiple show annotations for at least two separate API
     * surfaces.
     *
     * Analyzes the annotations and reports issues instructing which of the annotations should be
     * removed.
     */
    private fun reportOverlappingSurfaces(item: SelectableItem) {
        val annotations = item.modifiers.annotations()

        // Map from matched surface to matched annotations.
        val surfaceToAnnotations =
            annotations
                .mapNotNull { annotationItem ->
                    annotationItem.surfaceData?.showSurface?.let { surface ->
                        surface to annotationItem
                    }
                }
                .groupBy({ it.first }) { it.second }

        // Consistency check to ensure that the caller has detected overlaps correctly.
        if (surfaceToAnnotations.size < 2) {
            error("expected $item to have at least two surfaces")
        }

        // Find the narrowest surface in all the annotations.
        val narrowestSurface = surfaceToAnnotations.keys.min()

        // Get the associated annotation. There must be at least one otherwise there would be no
        // entry in surfaceToAnnotations.
        val narrowestAnnotation = surfaceToAnnotations[narrowestSurface]!!.first()

        // Iterate over all the surface/annotations reporting issues on all but the narrowest
        // surface.
        for ((surface, annotations) in surfaceToAnnotations) {
            // Ignore the narrowest surface.
            if (surface === narrowestSurface) continue

            // Iterate over all the annotations that are for wider surfaces, instructing to remove
            // the annotation.
            for (annotationItem in annotations) {
                reporter.report(
                    Issues.OVERLAPPING_API_SURFACES,
                    item,
                    "Remove $annotationItem from ${item.describe()} as it is superseded by $narrowestAnnotation",
                    annotationItem.fileLocation,
                )
            }
        }
    }

    /**
     * Check to make sure that [item] does not have show annotations without being explicitly
     * hidden.
     */
    private fun checkEnsureShowAnnotationsAreNotExplicitlyHidden(item: SelectableItem) {
        if (
            // Only check for @hide doc tag. Testing for annotations would complicate this
            // because it would be necessary to differentiate between an annotation that hides
            // items from all API surfaces and one that is hiding items that are part of a
            // different API surface.
            //
            // We check the block tag physically (using `hasBlockTagOfType("hide")`) instead of
            // calling `isHidden` because when API surfaces are configured in a config file,
            // `isHidden` returns false for `@hide` Javadoc tags. However, we still want to
            // flag this warning if the developer explicitly included a `@hide` tag.
            item.documentation?.hasBlockTagOfType("hide") == true
        ) {
            item.modifiers
                .annotations()
                // Find the first show annotation.
                .firstOrNull(AnnotationItem::isShowAnnotation)
                ?.let { annotation ->
                    val annotationName = annotation.qualifiedName
                    reporter.report(
                        Issues.HIDDEN_SHOW_ANNOTATION,
                        item,
                        "@$annotationName APIs must not be marked @hide: ${item.describe()}"
                    )
                }
        }
    }

    /**
     * Checks that the parents of a visible [SelectableItem], i.e. one whose parent is a class, are
     * themselves visible and not explicitly hidden.
     */
    private fun checkParentIsVisible(item: SelectableItem, parent: SourceSelectedApi<*>) {
        // Temporarily ignore PropertyItems to match previous behavior.
        if (item is PropertyItem) return

        val parentClass = item.containingClass() ?: return

        // If the parent is not explicitly hidden then everything is fine.
        if (!parent.explicitlyHidden) {
            return
        }

        // Otherwise, find a show annotation to blame it on and report the issue.
        item.modifiers
            .findAnnotation { annotationItem ->
                val showSurface =
                    annotationItem.surfaceData?.showSurface ?: return@findAnnotation false
                showSurface in apiSurfaces.all
            }
            ?.let { violatingAnnotation ->
                reporter.report(
                    Issues.SHOWING_MEMBER_IN_HIDDEN_CLASS,
                    item,
                    "Attempting to unhide ${item.describe()}, but surrounding ${parentClass.describe()} is " +
                        "hidden and should also be annotated with $violatingAnnotation"
                )
            }
    }

    /** Check if this [SelectableItem] is marked to be reverted. */
    private fun SelectableItem.isMarkedForRevert(): Boolean {
        val sourceSelectedApi = selectedApi as SourceSelectedApi<*>
        return sourceSelectedApi.revert
    }

    companion object {
        /**
         * Find the item to which [item] will be reverted.
         *
         * Searches the previously released API (if available).
         */
        fun findRevertItem(
            reporter: Reporter,
            previouslyReleasedCodebase: Codebase?,
            item: SelectableItem,
        ): SelectableItem? =
            previouslyReleasedCodebase.let { codebase ->
                if (codebase == null) {
                    reporter.report(
                        Issues.NO_PREVIOUSLY_RELEASED_API,
                        item,
                        "Cannot revert $item (or any other API item) as no previously released API has been provided"
                    )
                    null
                } else
                    item.findCorrespondingItemIn(
                        codebase,
                        // A method that overrides a method in the API should not be considered to
                        // be hidden as the method can still be called through the overridden
                        // method. This is set to true so that when a method is flagged and the
                        // associated flag is disabled then this will find a method that it
                        // overrides. That will prevent the method from trying to hide the
                        // overridden method.
                        superMethods = true,
                    )
            }
    }
}

/**
 * Workaround: we're pulling in .aidl files from .jar files. These are marked @hide, but since we
 * only see the .class files we don't know that.
 */
private fun SelectableItem.isAidlClassThatShouldBeHidden(): Boolean =
    this is ClassItem &&
        simpleName().startsWith("I") &&
        origin == ClassOrigin.CLASS_PATH &&
        interfaceTypes().any { it.qualifiedName == "android.os.IInterface" }
