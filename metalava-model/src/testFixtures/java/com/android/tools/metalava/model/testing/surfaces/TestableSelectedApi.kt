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

package com.android.tools.metalava.model.testing.surfaces

import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.SelectableItem

/**
 * Traverses this [Codebase] ensuring that [SelectableItem.selectedApi] is initialized correctly.
 *
 * [SelectableItem.selectedApi] instances are initialized on demand and initializing it for child
 * items can change the variants for the parent. This ensures that they are correctly initialized.
 */
fun Codebase.initializeSelectedApiInstances() {
    accept(
        object :
            BaseItemVisitor(
                preserveClassNesting = true,
                visitParameterItems = false,
            ) {
            override fun visitSelectableItem(item: SelectableItem) {
                item.selectedApi
            }
        }
    )
}
