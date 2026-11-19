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

import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.api.surface.ApiVariantSet

/** Base [SelectedApi] class for source [PackageItem]s. */
internal class PackageSourceSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: PackageItem,
) : SourceSelectedApi<PackageItem>(selectedApiUpdater, item) {
    /** Whether the classes in [item] have had their [SelectedApi] initialized. */
    private var classesInitialized = false

    override val itemApiVariants: ApiVariantSet
        get() {
            if (item.emit && !classesInitialized) {
                classesInitialized = true
                for (cls in item.allClasses()) {
                    cls.selectedApi.contentApiVariants
                }
            }
            return rawItemApiVariants
        }

    override fun itemSpecificInitialization() {
        updateFromSelectableItem()

        if (item.emit) {
            // Emitted packages do not belong to an API surface in their own right. They belong to
            // the union of the API surfaces to which their contained emitted classes belong. So,
            // reset this to empty to ignore the default values.
            rawItemApiVariants = ApiVariantSet.EMPTY
        } else if (!explicitlyHidden && rawItemApiVariants.isEmpty()) {
            // Non-emitted packages (e.g. classpath packages) never have variants propagated from
            // their classes because ClassSourceSelectedApi.propagateToContainingPackage ignores
            // non-emitted classes. When unannotatedApiSurface is null, updateFromSelectableItem()
            // leaves rawItemApiVariants empty for unannotated packages while keeping
            // inheritableApiVariants empty so emitted subpackages do not inherit variants. Set
            // rawItemApiVariants to classPathDefaultVariantSet so non-emitted packages are still
            // recognized as belonging to the base API surface.
            rawItemApiVariants = selectedApiUpdater.classPathDefaultVariantSet
        }
        rawContentApiVariants = ApiVariantSet.EMPTY
    }

    override fun propagateFromChild(childVariants: ApiVariantSet) {
        // Packages are not included in API surfaces directly. Instead, a package is included in an
        // API surface if one of its child classes is included in the API surface. So, add all the
        // child variants to this package's variant set.
        rawItemApiVariants += childVariants
    }

    /**
     * A package never hides its children (i.e. classes) as a package's API variants is the union of
     * all its children classes' API variants.
     */
    override fun areChildrenCompletelyHidden() = false
}
