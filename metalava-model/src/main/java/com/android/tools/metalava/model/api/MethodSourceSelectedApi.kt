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

import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiSurfacePredicate
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantSet
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.reporter.Issues

/**
 * Selected API class for methods, ensuring record component getter methods inherit the parent
 * class's API variants.
 */
internal class MethodSourceSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: MethodItem,
) : MemberSourceSelectedApi<MethodItem>(selectedApiUpdater, item) {

    override var elidableApiVariants = ApiVariantSet.EMPTY

    override fun itemSpecificInitialization() {
        // Record components are not separately selectable so need special initialization.
        if (item.isRecordComponentGetter) {
            initializeRecordComponent(item.recordComponentRelationship!!)
            return
        }

        super.itemSpecificInitialization()

        // If the parent is not part of any API then the method cannot be either.
        if (parent.itemApiVariants.isEmpty()) return

        // Unlike classes and fields, methods implicitly inherit API surface membership from the
        // methods they override (e.g. an unannotated or @Hide method implementing a public
        // interface method). If this method did not directly specify any API variants, find the
        // widest API surface inherited from overridden super methods.
        if (itemApiVariants.isEmpty()) {
            val apiSurfaces = selectedApiUpdater.apiSurfaces
            val parentSurface = parent.itemApiVariants.widestSurfaceFor(apiSurfaces) ?: return
            var maxSuperSurface: ApiSurface? = null
            var maxSuperVariants = ApiVariantSet.EMPTY

            for (superMethod in item.superMethods()) {
                val superVariants = superMethod.selectedApi.itemApiVariants
                val superSurface = superVariants.narrowestSurfaceFor(apiSurfaces) ?: continue

                // Do not inherit removed or doconly status from overridden methods.
                val superCoreVariant = superSurface.variantFor(ApiVariantType.CORE)
                if (superCoreVariant !in superVariants) {
                    continue
                }

                // A method can only inherit API variants from a super method in a narrower API
                // surface than the containing class.
                if (superSurface >= parentSurface) {
                    continue
                }

                // Find the widest API surface among the super methods.
                if (maxSuperSurface == null || superSurface > maxSuperSurface) {
                    maxSuperSurface = superSurface
                    maxSuperVariants = superVariants
                }

                // Stop searching if maxSuperSurface is at least as wide as the main surface being
                // generated.
                if (maxSuperSurface >= apiSurfaces.main) {
                    break
                }
            }

            // Adopt the API variants from the super method in the widest surface found.
            if (maxSuperSurface != null) {
                itemApiVariants = maxSuperVariants
            } else {
                handleHidingApiMethodOverride()
            }
        }

        computeElidableApiVariants()
    }

    /**
     * Handles an attempt by this method to hide an override of a method that is already part of the
     * API (or was removed from it) by unhiding it (adopting the overridden method's API variants)
     * and reporting an issue.
     *
     * It is an error to attempt to hide a method in a class if the method overrides an API method
     * in a superclass or interface, unless hiding from a narrower API surface (e.g. a public class
     * hiding a system API override).
     */
    private fun handleHidingApiMethodOverride() {
        val apiSurfaces = selectedApiUpdater.apiSurfaces
        val filterReference = ApiSurfacePredicate.wholeCoreApi(apiSurfaces.main)
        val removedFilterPredicate =
            ApiSurfacePredicate.apiFilters(
                    ApiType.REMOVED,
                    apiSurfaces.main,
                )
                .emit

        // Check to see if the method overrides an API method, if so unhide it and report an issue.
        if (
            !handleHidingApiMethodOverride(filterReference) { method, overriddenMethod ->
                "Attempting to hide ${method.describe()} which overrides ${overriddenMethod.describe()} which is already part of the API"
            }
        ) {
            // Check to see if the method overrides a method that was previously part of the API
            // but has since been removed.
            handleHidingApiMethodOverride(removedFilterPredicate) { method, overriddenMethod ->
                "Attempting to hide ${method.describe()} which overrides ${overriddenMethod.describe()} which was part of the API but has now been removed"
            }
        }
    }

    /**
     * Handles an attempt by this method to hide an override of an ancestor method that matches
     * [predicate].
     *
     * If such an overridden method is found, this unhides the method (when their erased parameter
     * types match) by adopting the overridden method's [itemApiVariants], reports an
     * [Issues.HIDING_API_METHOD_OVERRIDE] issue if appropriate, and returns `true`. Otherwise, this
     * returns `false`.
     */
    private inline fun handleHidingApiMethodOverride(
        predicate: FilterPredicate,
        reportMessageProvider: (MethodItem, MethodItem) -> String,
    ): Boolean {
        // Find an ancestor method that matches the predicate; if none exists, this method is not
        // hiding an API method override.
        val overriddenMethod = item.findPredicateSuperMethod(predicate) ?: return false

        // If the erased parameter types match (which may not be true when overriding a generic
        // method with specialized type arguments), unhide this method by adopting the overridden
        // method's API variants.
        if (MethodItem.overridesMethod(item, overriddenMethod)) {
            itemApiVariants = overriddenMethod.selectedApi.itemApiVariants
        }

        // Report an issue unless:
        // - The method is being reverted (e.g. via a disabled `@FlaggedApi`), as it was hidden by
        //   the revert rather than an explicit `@hide`.
        // - The issue is suppressed.
        // - The overridden method comes from the classpath, as it may not be possible to determine
        //   if a method in a jar matches a specific API version.
        // - A final class is hiding a protected method from its superclass, as there is no way to
        //   call a method of a final class through a protected method of the superclass.
        if (
            !revert &&
                !item.codebase.reporter.isSuppressed(Issues.HIDING_API_METHOD_OVERRIDE) &&
                overriddenMethod.origin != ClassOrigin.CLASS_PATH &&
                !(item.containingClass().modifiers.isFinal() &&
                    overriddenMethod.modifiers.isProtected())
        ) {
            item.codebase.reporter.report(
                Issues.HIDING_API_METHOD_OVERRIDE,
                item,
                reportMessageProvider(item, overriddenMethod),
            )
        }
        return true
    }

    /**
     * Compute which [ApiVariant]s this method is an elidable override in.
     *
     * A method is an elidable override in an [ApiVariant] if an ancestor method with the same
     * functional signature is already present in the reference API of that variant, meaning this
     * override does not introduce new API surface and does not need to be emitted in signature
     * files.
     *
     * Preconditions:
     * - [itemApiVariants] must be initialized and non-empty.
     * - If [SelectedApiUpdater.addAdditionalOverrides] is true and this method is required for text
     *   stubs (via [MethodItem.isRequiredOverridingMethodForTextStub]), it cannot be elided in any
     *   variant.
     */
    private fun computeElidableApiVariants() {
        if (
            selectedApiUpdater.addAdditionalOverrides &&
                item.isRequiredOverridingMethodForTextStub()
        ) {
            return
        }

        val elidableVariants = buildList {
            for (variant in selectedApiUpdater.apiSurfaces.variants) {
                if (variant.type != ApiVariantType.CORE && variant.type != ApiVariantType.REMOVED) {
                    continue
                }

                val referenceMask = referenceMaskFor(variant)
                val duplicateSuper = findDuplicateSuperMethod(item, item, referenceMask) ?: continue

                // For a REMOVED variant, this method is only an elidable override if this method
                // or the duplicate super method has a removed variant/annotation. If neither has a
                // removed status, the override is purely in the core API and not part of the
                // removed API.
                if (variant.type == ApiVariantType.REMOVED) {
                    val itemHasRemoved =
                        hasRemovedAnnotation() ||
                            selectedApiUpdater.apiSurfaces.all.any {
                                it.variantFor(ApiVariantType.REMOVED) in itemApiVariants
                            }
                    val superHasRemoved =
                        duplicateSuper.selectedApi.hasRemovedAnnotation() ||
                            selectedApiUpdater.apiSurfaces.all.any {
                                it.variantFor(ApiVariantType.REMOVED) in
                                    duplicateSuper.selectedApi.itemApiVariants
                            }
                    if (!itemHasRemoved && !superHasRemoved) {
                        continue
                    }
                }

                add(variant)
            }
        }

        if (elidableVariants.isNotEmpty()) {
            elidableApiVariants = selectedApiUpdater.apiSurfaces.createVariantSet(elidableVariants)
        }
    }

    /**
     * Computes a bitmask representing the reference API for the given [variant].
     *
     * For a [ApiVariantType.CORE] variant, the reference API includes the core variants of the
     * variant's surface and all surfaces it extends/includes.
     *
     * For a [ApiVariantType.REMOVED] variant, the reference API includes both the core and removed
     * variants of the variant's surface and all surfaces it extends/includes.
     */
    private fun referenceMaskFor(variant: ApiVariant): Int {
        var mask = 0
        for (surface in variant.surface.includedSurfaces) {
            mask = mask or surface.variantFor(ApiVariantType.CORE).bitMask
            if (variant.type == ApiVariantType.REMOVED) {
                mask = mask or surface.variantFor(ApiVariantType.REMOVED).bitMask
            }
        }
        return mask
    }

    /**
     * Recursively checks if this method has a duplicate super method in the reference API.
     *
     * Traverses the super method hierarchy starting from [current]. If an ancestor method belongs
     * to the reference API (matching [referenceMask]), we check if its signature matches [item] via
     * [MethodItem.sameSignature]. If it matches, [item] is elidable.
     *
     * If [item] is abstract and the super method is concrete, [item] is explicitly re-abstracting a
     * concrete method. We stop traversing that branch because finding an earlier abstract ancestor
     * (e.g. in an interface) should not cause [item] to be elided.
     *
     * If the super method is not in the reference API or does not match, we recursively search its
     * super methods.
     */
    private fun findDuplicateSuperMethod(
        item: MethodItem,
        current: MethodItem,
        referenceMask: Int,
    ): MethodItem? {
        val superMethods = current.superMethods()
        for (superMethod in superMethods) {
            // Check if this super method is included in the reference API.
            if (superMethod.selectedApi.itemApiVariants.bits and referenceMask != 0) {
                // If it is in the API and has the exact same signature, we found a duplicate
                // super method that allows this item to be elided.
                if (
                    MethodItem.sameSignature(
                        item,
                        superMethod,
                        addAdditionalOverrides = selectedApiUpdater.addAdditionalOverrides,
                    )
                ) {
                    return superMethod
                }
                // If item is abstract and this included super method is concrete, item is
                // explicitly re-abstracting a concrete method. Do not search further up this
                // inheritance path because finding an abstract ancestor (e.g. in a grandparent
                // interface) would incorrectly cause item to be elided.
                if (item.modifiers.isAbstract() && !superMethod.modifiers.isAbstract()) {
                    continue
                }
            }
            // If the super method is not in the API (e.g. from an inaccessible class or interface),
            // or if it did not match, recursively search its super methods for an ancestor in the
            // API.
            val found = findDuplicateSuperMethod(item, superMethod, referenceMask)
            if (found != null) {
                return found
            }
        }
        return null
    }
}
