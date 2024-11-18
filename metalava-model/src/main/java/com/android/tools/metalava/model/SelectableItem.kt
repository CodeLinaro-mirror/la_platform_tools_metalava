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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantSet
import com.android.tools.metalava.model.api.surface.MutableApiVariantSet

/**
 * An [Item] that can be selected to be a part of an API in its own right.
 *
 * e.g. a [MethodItem] is selectable because while a method's [MethodItem.containingClass] has to be
 * part of the same API just because the [ClassItem] is selected does not mean that a [MethodItem]
 * has to be.
 *
 * Conversely, a [ParameterItem] is not selectable because it cannot be selected on its own, it is
 * an indivisible part of the [ParameterItem.containingCallable].
 */
interface SelectableItem : Item {
    /** The [ApiVariant]s for which this [Item] has been selected. */
    val selectedApiVariants: ApiVariantSet

    /**
     * Mutate [selectedApiVariants].
     *
     * Provides a [MutableApiVariantSet] of the [selectedApiVariants] that can be modified by
     * [mutator]. Once the mutator exits [selectedApiVariants] will be updated. The
     * [MutableApiVariantSet] must not be accessed from outside [mutator].
     */
    fun mutateSelectedApiVariants(mutator: MutableApiVariantSet.() -> Unit)

    /** Whether this element will be printed in the signature file */
    var emit: Boolean

    /**
     * Whether this element was originally hidden with @hide/@Hide. The [hidden] property tracks
     * whether it is *actually* hidden, since elements can be unhidden via show annotations, etc.
     *
     * @see variantSelectors
     */
    val originallyHidden: Boolean

    /**
     * Whether this element has been hidden with @hide/@Hide (or after propagation, in some
     * containing class/pkg)
     *
     * @see variantSelectors
     */
    val hidden: Boolean

    /**
     * Tracks the properties that determine whether this [Item] will be selected for each API
     * variant.
     *
     * @see originallyHidden
     * @see hidden
     * @see removed
     */
    val variantSelectors: ApiVariantSelectors

    /**
     * Recursive check to see if this item or any of its parents (containing class, containing
     * package) are hidden
     */
    fun hidden(): Boolean {
        return hidden || parent()?.hidden() ?: false
    }

    /**
     * Whether this element has been removed with @removed/@Remove (or after propagation, in some
     * containing class)
     *
     * @see variantSelectors
     */
    val removed: Boolean

    /** True if this item is either hidden or removed */
    fun isHiddenOrRemoved(): Boolean = hidden || removed

    /** Determines whether this item will be shown as part of the API or not. */
    val showability: Showability

    /**
     * Returns true if this item has any show annotations.
     *
     * See [Showability.show]
     */
    fun hasShowAnnotation(): Boolean = showability.show()

    /** Returns true if this modifier list contains any hide annotations */
    fun hasHideAnnotation(): Boolean = codebase.annotationManager.hasHideAnnotations(modifiers)

    /** Override to specialize return type. */
    override fun findCorrespondingItemIn(
        codebase: Codebase,
        superMethods: Boolean,
        duplicate: Boolean,
    ): SelectableItem?
}
