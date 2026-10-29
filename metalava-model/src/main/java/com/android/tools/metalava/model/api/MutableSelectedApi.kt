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

import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantSet

/**
 * A [SelectedApi] that that stores [itemApiVariants], [contentApiVariants], etc and can be mutated;
 * for use by signature files.
 */
internal open class MutableSelectedApi<S : SelectableItem>(
    protected val item: S,
) : SelectedApi() {
    override var itemApiVariants = ApiVariantSet.EMPTY

    override var contentApiVariants = ApiVariantSet.EMPTY

    override var superClassApiVariants = ApiVariantSet.EMPTY

    override var elidableApiVariants = ApiVariantSet.EMPTY

    override val revert: Boolean
        get() = false

    override val revertItem: SelectableItem?
        get() = null

    override fun initialize() {}

    override fun addItemApiVariant(value: ApiVariant) {
        if (value !in itemApiVariants) {
            // An item must not belong to multiple API surfaces, but can belong to multiple variants
            // of the same surface (e.g. CORE and REMOVED). Therefore, only add this variant if the
            // item does not yet belong to any surface or if this variant is part of the same
            // surface it already belongs to.
            if (itemApiVariants.isEmpty() || itemApiVariants.containsAny(value.surface)) {
                itemApiVariants += value
            }
        }
    }

    override fun snapshot(original: SelectedApi) {
        error("Cannot populate $this in a snapshot")
    }
}
