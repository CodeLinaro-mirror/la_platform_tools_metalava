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
import com.android.tools.metalava.model.api.surface.ApiVariant

/**
 * A [MutableSelectedApi] for a [ClassItem].
 *
 * Manages API variants for a class loaded from a signature file and coordinates propagation of API
 * variants to its containing package and from its members:
 * - When an API variant is added to this class, it is propagated to the containing package.
 * - When a member within this class has an API variant added that this class does not belong to,
 *   that variant is propagated to [contentApiVariants] (or [itemApiVariants] for file facades) and
 *   up to the containing package.
 */
internal class MutableClassSelectedApi(
    item: ClassItem,
) : MutableSelectedApi<ClassItem>(item) {
    override fun addItemApiVariant(value: ApiVariant) {
        val wasEmpty = itemApiVariants.isEmpty()
        super.addItemApiVariant(value)
        // Propagate to containing package only when the class first receives an API variant.
        if (wasEmpty && itemApiVariants.isNotEmpty()) {
            propagateToContainingPackage(value)
        }
    }

    /**
     * Propagate an [ApiVariant] from a member contained within this class.
     *
     * If the class does not already belong to [value]:
     * - For a file facade, the class represents a file rather than an actual class, so its
     *   [itemApiVariants] is the union of its members' variants.
     * - For regular classes, the class itself is not part of that API surface, but contains members
     *   that are, so [value] is added to [contentApiVariants].
     *
     * In either case, [value] is also propagated to the containing package.
     */
    fun propagateFromMember(value: ApiVariant) {
        if (value !in itemApiVariants) {
            if (item.isFileFacade) {
                itemApiVariants += value
            } else {
                contentApiVariants += value
            }
            propagateToContainingPackage(value)
        }
    }

    /** Propagate [value] to the containing package's [MutablePackageSelectedApi]. */
    private fun propagateToContainingPackage(value: ApiVariant) {
        val containingPackage = item.containingPackage()
        (containingPackage.selectedApi as? MutablePackageSelectedApi)?.addItemApiVariant(value)
    }
}
