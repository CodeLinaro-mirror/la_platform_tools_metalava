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
    override fun itemSpecificInitialization() {
        updateFromSelectableItem()

        // Packages do not belong to an API surface in their own right. They belong to the union of
        // the API surfaces to which their contained classes belong. So, reset this to empty to
        // ignore the default values.
        itemApiVariants = ApiVariantSet.EMPTY
        contentApiVariants = ApiVariantSet.EMPTY
    }

    override fun propagateFromChild(childVariants: ApiVariantSet) {
        // Packages are not included in API surfaces directly. Instead, a package is included in an
        // API surface if one of its child classes is included in the API surface. So, add all the
        // child variants to this package's variant set.
        itemApiVariants += childVariants
    }

    /**
     * A package never hides its children (i.e. classes) as a package's API variants is the union of
     * all its children classes' API variants.
     */
    override fun areChildrenCompletelyHidden() = false
}
