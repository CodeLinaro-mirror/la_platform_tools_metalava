/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.metalava

import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.SelectableItem

/**
 * Filter that will elide exact duplicate methods that are already included in another
 * superclass/interfaces.
 */
class ElidingPredicate(
    private val wrapped: FilterPredicate,

    /** Whether overriding methods essential for compiling the stubs should be elided or not. */
    private val addAdditionalOverrides: Boolean,
) : FilterPredicate {

    // Returning true means we are keeping this item
    // i.e. when this returns false, we are eliding the item
    override fun test(item: SelectableItem): Boolean {
        // This method should be included, but if it's an exact duplicate
        // override then we can elide it.
        return if (item is MethodItem) {
            val differentSuper =
                item.findPredicateSuperMethod(
                    // This predicate returns true if
                    // the potential super method has same signature
                    FilterPredicate { maybeEqualSuperMethod ->
                        // We're looking for included and perfect signature
                        wrapped.test(maybeEqualSuperMethod) &&
                            maybeEqualSuperMethod is MethodItem &&
                            MethodItem.sameSignature(
                                item,
                                maybeEqualSuperMethod,
                                addAdditionalOverrides = addAdditionalOverrides,
                            )
                    }
                )

            val doNotElideForAdditionalOverridePurpose =
                addAdditionalOverrides && item.isRequiredOverridingMethodForTextStub()

            differentSuper == null || doNotElideForAdditionalOverridePurpose
        } else {
            true
        }
    }
}
