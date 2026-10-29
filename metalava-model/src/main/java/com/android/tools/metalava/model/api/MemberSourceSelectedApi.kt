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

import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.api.surface.ApiVariantSet
import com.android.tools.metalava.reporter.Issues

/** Base [SelectedApi] class for source [MemberItem]s. */
internal open class MemberSourceSelectedApi<M : MemberItem>(
    selectedApiUpdater: SelectedApiUpdater,
    item: M,
) : SourceSelectedApi<M>(selectedApiUpdater, item) {

    /** This does not initialize [inheritableApiVariants] as [MemberItem]s do not have children. */
    override fun itemSpecificInitialization() {
        updateFromSelectableItem()

        // Propagate information from this to the parent, i.e. the containing class.
        parent.propagateFromChild(itemApiVariants)
    }

    override fun propagateFromChild(childVariants: ApiVariantSet) =
        throw NotImplementedError("class members do not have any children")

    override fun areChildrenCompletelyHidden() =
        throw NotImplementedError("class members do not have any children")

    /**
     * Initializes the [SelectedApi] state for a member related to a record component (such as a
     * record component getter method or canonical constructor).
     *
     * Record components are an indivisible part of a record class and cannot be hidden or assigned
     * to different API surfaces independently from their containing record class.
     *
     * @param recordComponentRelationship A description of the relationship between this member and
     *   the record component (e.g. `"record component getter"` or `"canonical constructor"`), used
     *   when reporting issues.
     */
    protected fun initializeRecordComponent(recordComponentRelationship: String) {
        // Update this member's SelectedApi state (including originallyHidden, accessible, and
        // initial variants) from its annotations and documentation.
        updateFromSelectableItem()

        // If the containing record class is part of an API surface, but this component member
        // was marked as hidden (resulting in empty API variants), report an error because record
        // components cannot be hidden independently of the record class.
        if (parent.itemApiVariants.isNotEmpty() && itemApiVariants.isEmpty()) {
            item.codebase.reporter.report(
                Issues.HIDING_RECORD_COMPONENT,
                item,
                "Cannot hide $recordComponentRelationship ${item.describe()} as it is an indivisible part of a record class"
            )
        }

        // Make sure that the component is in the same surfaces as the containing class, ensuring
        // its API surface membership strictly matches the record class.
        itemApiVariants = parent.itemApiVariants
    }
}
