/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.visitors

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.Showability

/**
 * Predicate that decides if the given member should be considered part of an API surface area. To
 * make the most accurate decision, it searches for signals on the member, all containing classes,
 * and all containing packages.
 */
class ApiPredicate(
    /**
     * Set if the value of [SelectableItem.removed] should be ignored. That is, this predicate will
     * assume that all encountered members match the "removed" requirement.
     *
     * This is typically useful when generating "removed.txt", when it's okay to reference both
     * current and removed APIs.
     */
    private val ignoreRemoved: Boolean = false,

    /**
     * Set what the value of [SelectableItem.removed] must be equal to in order for a member to
     * match.
     *
     * This is typically useful when generating "removed.txt", when you only want to match members
     * that have actually been removed.
     */
    private val matchRemoved: Boolean = false,

    /** Whether we should include doc-only items */
    private val includeDocOnly: Boolean = false,

    /**
     * Whether to include API surfaces that contribute to the one currently being generated.
     *
     * See [AnnotationItem.isShowForStubPurposes].
     */
    private val includeContributingSurfaces: Boolean = true,

    /** Configuration that may be provided by command line options. */
    config: Config,
) : FilterPredicate {
    /**
     * Set if the value of [SelectableItem.hasShowAnnotation] should be ignored. That is, this
     * predicate will assume that all encountered members match the "shown" requirement.
     *
     * When [includeContributingSurfaces] is true, the predicate matches items across the whole API
     * surface (e.g. for stub generation, reference resolution, or ProGuard keep file generation),
     * so it uses [Config.ignoreShownForWholeApiSurface] which accounts for whether unannotated
     * items are part of the target surface or any surface it extends.
     *
     * When [includeContributingSurfaces] is false, the predicate matches items strictly within the
     * target API surface delta (i.e. for signature file generation), so it uses
     * [Config.ignoreShown] which only considers whether unannotated items are part of the target
     * surface itself.
     */
    private val ignoreShown: Boolean =
        if (includeContributingSurfaces) {
            config.ignoreShownForWholeApiSurface
        } else {
            config.ignoreShown
        }

    /**
     * Whether overriding methods essential for compiling the stubs should be considered as APIs or
     * not.
     */
    private val addAdditionalOverrides: Boolean = config.addAdditionalOverrides

    /**
     * Contains configuration for [ApiPredicate] that can, or at least could, come from command line
     * options.
     */
    data class Config(
        /**
         * Set if the value of [SelectableItem.hasShowAnnotation] should be ignored. That is, this
         * predicate will assume that all encountered members match the "shown" requirement.
         *
         * This is set to true when the current API surface includes items by default, i.e. they do
         * not require a show annotation to be included in the API surface.
         */
        val ignoreShown: Boolean = true,

        /**
         * The value to use for [ignoreShown] when matching the whole API surface.
         *
         * This is set to true when the current API surface (or an API surface that it extends)
         * includes unannotated items, so that unannotated items are matched across the whole API
         * surface.
         */
        val ignoreShownForWholeApiSurface: Boolean = true,

        /**
         * Whether overriding methods essential for compiling the stubs should be considered as APIs
         * or not.
         */
        val addAdditionalOverrides: Boolean = false,
    )

    override fun test(item: SelectableItem): Boolean {
        // non-class, i.e., (literally) member declaration w/o emit flag, e.g., due to `expect`
        // Some [ClassItem], e.g., JvmInline, java.lang.* classes, may not set the emit flag.
        if (item !is ClassItem && !item.emit) {
            return false
        }

        val visibleForAdditionalOverridePurpose =
            if (addAdditionalOverrides) {
                item is MethodItem && item.isRequiredOverridingMethodForTextStub()
            } else {
                false
            }

        val itemSelectors = item.variantSelectors

        // If the item or any of its containing classes are inaccessible then ignore it.
        if (!itemSelectors.accessible) return false

        val hidden = itemSelectors.hidden && !visibleForAdditionalOverridePurpose
        if (hidden) return false

        // If a class item's parent class is an api-only annotation marked class,
        // the item should be marked visible as well, in order to provide
        // information about the correct class hierarchy that was concealed for
        // less restricted APIs.
        // Only the class definition is marked visible, and class attributes are
        // not affected.
        if (
            item is ClassItem &&
                item.superClass()?.let {
                    it.hasShowAnnotation() && !it.includeOnlyForStubPurposes()
                } == true
        ) {
            return itemSelectors.removed == matchRemoved
        }

        // If an item is only included for stub generation purposes (i.e. all of its show
        // annotations are marked as show-for-stub-purposes, such as annotations for a base API
        // surface), ignore it when generating signature files.
        // This check must come after the superclass check above so that any affected subclass whose
        // superclass belongs to the target API surface is still included to accurately preserve the
        // class hierarchy, even if the subclass itself is marked only for stub purposes.
        if (!includeContributingSurfaces && item.includeOnlyForStubPurposes()) {
            return false
        }

        // If docOnly items are not included and this item is docOnly then ignore it.
        if (!includeDocOnly && itemSelectors.docOnly) return false

        // If removed status is not ignored and this item's status does not match what is required
        // then ignore this item.
        if (!ignoreRemoved && itemSelectors.removed != matchRemoved) return false

        if (!ignoreShown && !hasShowAnnotation(item)) {
            return false
        }

        // If any containing class is hidden then ignore this item.
        if (item.anyContainingClass { it.hidden }) {
            return false
        }

        return true
    }

    /**
     * Check if any containing class of this item matches [predicate], traversing from the innermost
     * containing class out to the top-level class.
     */
    private inline fun SelectableItem.anyContainingClass(
        predicate: (ClassItem) -> Boolean,
    ): Boolean {
        var cls = containingClass()
        while (cls != null) {
            if (predicate(cls)) return true
            cls = cls.containingClass()
        }
        return false
    }

    /**
     * Check whether this item has a recursive show annotation that affects nested items.
     *
     * See [Showability.showRecursive].
     */
    private fun SelectableItem.hasRecursiveShow() = showability.showRecursive()

    /** Check if this item or any of its containing classes or packages has a show annotation. */
    private fun hasShowAnnotation(item: SelectableItem): Boolean {
        if (item.hasShowAnnotation()) return true

        if (item.anyContainingClass { it.hasRecursiveShow() }) return true

        // Traverse up the package hierarchy to check if this item belongs to a shown package.
        var showPackage = item.containingPackage()
        while (showPackage != null) {
            // If an intermediate package is hidden, it prevents any show annotations on its
            // parent packages from propagating down to this item.
            if (showPackage.hidden) {
                break
            }
            if (showPackage.hasRecursiveShow()) return true
            showPackage = showPackage.containingPackage()
        }

        return false
    }
}
