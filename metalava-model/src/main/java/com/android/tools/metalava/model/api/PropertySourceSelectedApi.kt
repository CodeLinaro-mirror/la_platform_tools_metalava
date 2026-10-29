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

import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.api.SurfaceSelectionRule.Effect

/**
 * Selected API class for properties, ensuring properties with an exposed backing field (e.g. `const
 * val` or `@JvmField`) inherit the backing field's API variants and status, and properties with an
 * explicitly hidden private backing field are also marked as hidden.
 */
internal class PropertySourceSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: PropertyItem,
) : MemberSourceSelectedApi<PropertyItem>(selectedApiUpdater, item) {

    override fun itemSpecificInitialization() {
        updateFromSelectableItem()

        // If the property is already hidden (e.g. parent is hidden, property has no API
        // visibility, or has an explicit hide annotation), do not unhide it.
        if (itemApiVariants.isNotEmpty()) {
            item.backingField?.let { backingField ->
                // A property's backing field can take one of two forms:
                // 1. Backing fields with API visibility (e.g. `const val` or `@JvmField`):
                //    These fields are exposed in bytecode and have API visibility. Their
                //    SelectedApi status already reflects their accessibility, enclosing
                //    surfaces, and annotations. The property adopts their status directly.
                // 2. Private backing fields (standard Kotlin properties):
                //    These are private and thus always lack API visibility, which causes
                //    their SelectedApi status to be inaccessible and have empty API variants
                //    by default. The property itself is represented in the API by its accessors,
                //    so its backing field being inaccessible does not mean the property should
                //    be hidden. However, if the private backing field has an explicit hide
                //    annotation (e.g. `@field:Hide` or `@field:RestrictTo`), that intent should
                //    hide the property as well. Therefore, we explicitly check for hide
                //    annotations rather than relying on the backing field's accessibility or
                //    empty API variants.
                if (backingField.modifiers.hasApiVisibility()) {
                    val fieldSelectedApi = backingField.selectedApi as? SourceSelectedApi<*>
                    if (fieldSelectedApi != null) {
                        adoptStatusFrom(fieldSelectedApi)
                    }
                } else if (isExplicitlyHidden(backingField)) {
                    selectedApiUpdater.markAsHidden(this, revert = false)
                }
            }
        }

        // Propagate information from this to the parent, i.e. the containing class.
        parent.propagateFromChild(itemApiVariants)
    }

    /**
     * Check whether this [FieldItem] is explicitly hidden via a hide annotation and is not shown
     * via a show annotation.
     */
    private fun isExplicitlyHidden(field: FieldItem): Boolean {
        var hide = false
        for (annotationItem in field.modifiers.annotations()) {
            annotationItem.surfaceData?.let { surfaceData ->
                when (surfaceData.effect) {
                    Effect.SHOW -> return false
                    Effect.HIDE -> hide = true
                    else -> {}
                }
            }
        }
        return hide
    }
}
