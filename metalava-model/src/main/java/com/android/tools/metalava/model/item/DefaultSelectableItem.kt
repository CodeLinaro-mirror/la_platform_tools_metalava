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

import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.api.SelectedApi
import com.android.tools.metalava.reporter.FileLocation

internal sealed class DefaultSelectableItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    sourceLanguage: SourceLanguage,
    modifiers: BaseModifierList,
    documentationFactory: ItemDocumentationFactory,
    override var targetLanguages: Set<TargetLanguage>,
) :
    DefaultItem(
        codebase,
        fileLocation,
        sourceLanguage,
        modifiers,
    ),
    SelectableItem {
    /**
     * Create a [ItemDocumentation] appropriate for this [Item].
     *
     * The leaking of `this` is safe as the implementations do not access anything that has not been
     * initialized.
     *
     * If this is private then it cannot be included in an API so its documentation is irrelevant.
     * In that case this ignores its [ItemDocumentationFactory] and uses `null` instead.
     */
    final override val documentation =
        if (modifiers.isPrivate()) null
        else @Suppress("LeakingThis") documentationFactory.create(this)

    private lateinit var _selectedApi: SelectedApi

    /** Create a [SelectedApi] appropriate for this [SelectableItem] on demand. */
    final override val selectedApi: SelectedApi
        get() {
            if (!::_selectedApi.isInitialized) {
                // Create the instance and store in the field straight away before initialization.
                // This is needed because initialize() may reenter this method and if it is not set
                // before calling initialize() it will overflow the stack.
                val factory = (codebase as DefaultCodebase).selectedApiFactory
                _selectedApi = factory(this)

                // Initialize the instance.
                _selectedApi.initialize()
            }
            return _selectedApi
        }

    // Default to true, may be updated later
    final override var emit = true
}
