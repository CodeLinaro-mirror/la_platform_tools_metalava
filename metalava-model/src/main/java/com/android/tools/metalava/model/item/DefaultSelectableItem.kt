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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ApiVariantSelectorsFactory
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.Showability
import com.android.tools.metalava.model.api.surface.ApiVariantSet
import com.android.tools.metalava.model.api.surface.MutableApiVariantSet
import com.android.tools.metalava.reporter.FileLocation

abstract class DefaultSelectableItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    modifiers: BaseModifierList,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
) :
    DefaultItem(
        codebase,
        fileLocation,
        itemLanguage,
        modifiers,
        documentationFactory,
    ),
    SelectableItem {

    final override var selectedApiVariants: ApiVariantSet = codebase.apiSurfaces.emptyVariantSet
        private set

    override fun mutateSelectedApiVariants(mutator: MutableApiVariantSet.() -> Unit) {
        val mutable = selectedApiVariants.toMutable()
        mutable.mutator()
        selectedApiVariants = mutable.toImmutable()
    }

    final override var emit =
        // Do not emit expect declarations in APIs.
        !modifiers.isExpect()

    /**
     * Create an [ApiVariantSelectors] appropriate for this [SelectableItem].
     *
     * The leaking of `this` is safe as the implementations do not access anything that has not been
     * initialized.
     */
    override val variantSelectors = @Suppress("LeakingThis") variantSelectorsFactory(this)

    /**
     * Manually delegate to [ApiVariantSelectors.originallyHidden] as property delegates are
     * expensive.
     */
    final override val originallyHidden
        get() = variantSelectors.originallyHidden

    /** Manually delegate to [ApiVariantSelectors.hidden] as property delegates are expensive. */
    final override val hidden
        get() = variantSelectors.hidden

    /** Manually delegate to [ApiVariantSelectors.removed] as property delegates are expensive. */
    final override val removed: Boolean
        get() = variantSelectors.removed

    final override val showability: Showability
        get() = variantSelectors.showability
}
