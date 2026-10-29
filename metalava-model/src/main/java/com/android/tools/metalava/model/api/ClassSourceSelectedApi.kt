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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.api.surface.ApiVariantSet

/** Base [SelectedApi] class for source [ClassItem]s. */
internal class ClassSourceSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: ClassItem,
) : SourceSelectedApi<ClassItem>(selectedApiUpdater, item) {
    override var superClassApiVariants = ApiVariantSet.EMPTY

    override fun itemSpecificInitialization() {
        updateFromSelectableItem()

        // Propagate variants to the containing package, skipping any intermediate nested classes
        // as they will be flattened when generating signature files.
        propagateToContainingPackage(itemApiVariants)

        // A class must be included in delta signature files for a wider API surface if its
        // superclass belongs to that surface, even if this class belongs to a narrower surface, in
        // order to reveal the class hierarchy.
        item.superClass()?.let { superClass ->
            val superClassVariants = superClass.selectedApi.itemApiVariants
            val apiSurfaces = selectedApiUpdater.apiSurfaces

            // Get the narrowest surface to which the super class belongs and the widest surface to
            // which this class belongs.
            val superClassSurface = superClassVariants.narrowestSurfaceFor(apiSurfaces)
            val itemSurface = itemApiVariants.widestSurfaceFor(apiSurfaces)
            if (superClassSurface == null || itemSurface == null) return@let

            // If the super class' surface is wider than this class' surface then add the super
            // class' variants to this class' super class variants so that this class will be
            // included, but only for variant types that this class also belongs to.
            if (superClassSurface > itemSurface) {
                // Translate this class's variants from its surface to the super class's surface.
                // This acts as a mask containing only the variant types that this class belongs to,
                // but situated in the super class's surface.
                val superVariantsMask =
                    itemApiVariants.moveVariantsBetweenSurfaces(itemSurface, superClassSurface)

                // Only add super class variants whose types match this class's own variant types
                // (e.g. only inherit system(C) if this class has public(C)).
                superClassApiVariants += superClassVariants.intersectionWith(superVariantsMask)
            }
        }
    }

    /** Propagate [childVariants] to the containing package of this. */
    private fun propagateToContainingPackage(childVariants: ApiVariantSet) {
        // Classes that are not emitted (e.g. from the class path) must not propagate their variants
        // to the containing package.
        if (!item.emit) return

        // Find the enclosing package. This purposely skips classes as variants must not be
        // propagated from nested classes to their containing class as that is unnecessary for
        // signature file generation where nested classes are flattened.
        var ancestor: SourceSelectedApi<*> = parent
        while (ancestor !is PackageSourceSelectedApi) {
            ancestor = ancestor.parent
        }

        // Propagate information from this to the enclosing package. This is necessary to ensure
        // that the package and its classes are correctly included in a signature file.
        ancestor.propagateFromChild(childVariants)
    }

    override fun propagateFromChild(childVariants: ApiVariantSet) {
        // Include every variant to which the child belongs except those to which this already
        // belongs.
        val propagateVariants = childVariants - itemApiVariants

        // If there are no variants to propagate then return immediately.
        if (propagateVariants.isEmpty()) return

        if (item.isFileFacade) {
            // A file facade class only belongs to the surfaces to which its members belong.
            itemApiVariants += propagateVariants
        } else {
            // Add them to the class.
            contentApiVariants += propagateVariants
        }

        // Propagate to containing package.
        propagateToContainingPackage(propagateVariants)
    }

    override fun areChildrenCompletelyHidden() =
        if (item.isFileFacade) explicitlyHidden else itemApiVariants.isEmpty()
}
