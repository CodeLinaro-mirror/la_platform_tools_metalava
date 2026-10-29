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
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantSet
import com.android.tools.metalava.model.item.DefaultSelectableItem

/** Provides access to the [ApiVariantSet] to which a specific [SelectableItem] belongs. */
sealed class SelectedApi {
    /** The [ApiVariantSet] for the [SelectableItem]. */
    abstract val itemApiVariants: ApiVariantSet

    /** The [ApiVariantSet] for child items. */
    abstract val contentApiVariants: ApiVariantSet

    /**
     * The [ApiVariantSet] inherited from the super class of a [ClassItem].
     *
     * This is always empty by default except for [ClassItem]s.
     *
     * **Why this is needed:** When a class belongs to a narrower API surface than its super class
     * (e.g. a public class extending a `SystemApi` class), the class must also be included in the
     * wider API surface so that the type hierarchy in the wider API surface remains complete and
     * consistent. Tracking these variants separately from [contentApiVariants] (which tracks
     * variants from child items) allows distinguishing between variants introduced by enclosed
     * members and those introduced by the class hierarchy.
     *
     * **How it is set:** Initialized for [ClassItem]s in
     * [ClassSourceSelectedApi.itemSpecificInitialization]. If the class has a super class whose
     * narrowest API surface is wider than this class's widest API surface, the super class's
     * variants are masked to match this class's variant types (e.g. only inherit `system(C)` if
     * this class has `public(C)`) and added to this set.
     */
    open val superClassApiVariants: ApiVariantSet
        get() = ApiVariantSet.EMPTY

    /** Indicates whether the associated [SelectableItem] is being reverted. */
    abstract val revert: Boolean

    /**
     * The [ApiVariantSet] for which this method is an elidable override.
     *
     * This is always empty by default except for [MethodItem]s.
     */
    open val elidableApiVariants: ApiVariantSet
        get() = ApiVariantSet.EMPTY

    /**
     * The [SelectableItem] from the previously released API that matches this item, if this item is
     * to be reverted.
     */
    abstract val revertItem: SelectableItem?

    /** Checks to see if the associated [SelectableItem] contains any removed annotations. */
    open fun hasRemovedAnnotation(): Boolean = false

    /**
     * Initialize this instance.
     *
     * This is called after this has been created and assigned to
     * [DefaultSelectableItem.selectedApi].
     */
    internal abstract fun initialize()

    /**
     * Add [value] to [itemApiVariants].
     *
     * This can only be called on items loaded from signature files.
     */
    abstract fun addItemApiVariant(value: ApiVariant)

    /**
     * Populate this instance with the state from the [original] [SelectedApi] of the item being
     * snapshotted.
     *
     * This can only be called when creating a snapshot of the codebase using a
     * [SelectedApi.SNAPSHOT_FACTORY].
     */
    abstract fun snapshot(original: SelectedApi)

    companion object {
        /**
         * Return a [SelectedApi] factory that will create [SelectedApi] instances suitable for
         * being populated from a signature file.
         */
        val MUTABLE_FACTORY: (SelectableItem) -> SelectedApi = { item ->
            when (item) {
                is PackageItem -> MutablePackageSelectedApi(item)
                is ClassItem -> MutableClassSelectedApi(item)
                is MemberItem -> MutableMemberSelectedApi(item)
                else -> error("unknown selectable item: $item")
            }
        }

        /**
         * Return a [SelectedApi] factory that will create [SelectedApi] instances suitable for a
         * snapshot [Codebase].
         */
        val SNAPSHOT_FACTORY: (SelectableItem) -> SelectedApi = { SnapshotSelectedApi() }

        /**
         * Create a [SelectedApi] factory that will create [SelectedApi] instances suitable for a
         * [Codebase] created from [config].
         */
        fun sourceFactory(config: Codebase.Config): (SelectableItem) -> SelectedApi {
            // Get the ApiSurfaceSelector that is used by the AnnotationManager.
            val annotationManager = config.annotationManager
            val apiSurfaceSelector = annotationManager.apiSurfaceSelector
            val previouslyReleasedCodebaseProvider = {
                annotationManager.previouslyReleasedCodebase
            }

            // Create an updater that will be captured by the factory below and will be used by all
            // SelectedApi instances in the Codebase that uses tha factory.
            val selectedApiUpdater =
                SelectedApiUpdater(
                    config.reporter,
                    apiSurfaceSelector,
                    previouslyReleasedCodebaseProvider,
                )
            return { item -> createFromSource(selectedApiUpdater, item) }
        }

        /** Create a [SelectedApi] for a source [item]. */
        fun createFromSource(
            selectedApiUpdater: SelectedApiUpdater,
            item: SelectableItem,
        ): SelectedApi =
            when (item) {
                is ClassItem -> ClassSourceSelectedApi(selectedApiUpdater, item)
                is MethodItem -> MethodSourceSelectedApi(selectedApiUpdater, item)
                is ConstructorItem -> ConstructorSourceSelectedApi(selectedApiUpdater, item)
                is PropertyItem -> PropertySourceSelectedApi(selectedApiUpdater, item)
                is MemberItem -> MemberSourceSelectedApi(selectedApiUpdater, item)
                is PackageItem -> PackageSourceSelectedApi(selectedApiUpdater, item)
                else -> error("unknown selectable item: $item")
            }
    }
}
