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

import com.android.tools.metalava.model.ConstructorItem

/**
 * Selected API class for constructors, ensuring canonical record constructors inherit the parent
 * class's API variants.
 */
internal class ConstructorSourceSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: ConstructorItem,
) : MemberSourceSelectedApi<ConstructorItem>(selectedApiUpdater, item) {

    override fun itemSpecificInitialization() {
        // Record components are not separately selectable so need special initialization.
        if (item.isCanonicalRecordComponentConstructor) {
            initializeRecordComponent(item.recordComponentRelationship!!)
            return
        }

        super.itemSpecificInitialization()
    }
}
