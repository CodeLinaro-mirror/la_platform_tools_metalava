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
 * A [SelectedApi] that stores [itemApiVariants], [contentApiVariants], and [superClassApiVariants]
 * without requiring a [SelectedApiUpdater] or parent hierarchy.
 *
 * Used for snapshot codebases where variants are copied from the original codebase.
 */
internal class SnapshotSelectedApi : SelectedApi() {
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
        error("Cannot update itemApiVariants in $this")
    }

    override fun snapshot(original: SelectedApi) {
        itemApiVariants = original.itemApiVariants
        contentApiVariants = original.contentApiVariants
        superClassApiVariants = original.superClassApiVariants
        elidableApiVariants = original.elidableApiVariants
    }
}
