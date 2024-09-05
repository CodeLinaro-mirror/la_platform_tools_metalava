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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.item.CodebaseAssembler
import com.android.tools.metalava.model.item.DefaultItemFactory

internal class TextCodebaseAssembler(
    private val codebase: TextCodebase,
) : CodebaseAssembler {

    /** Creates [Item] instances for this. */
    override val itemFactory =
        DefaultItemFactory(
            codebase = codebase,
            // Signature files do not contain information about whether an item was originally
            // created from Java or Kotlin.
            defaultItemLanguage = ItemLanguage.UNKNOWN,
            // Signature files have already been separated by API surface variants, so they can use
            // the same immutable ApiVariantSelectors.
            defaultVariantSelectorsFactory = ApiVariantSelectors.IMMUTABLE_FACTORY,
        )

    fun initialize() {
        // Make sure that it has a root package.
        val rootPackage = itemFactory.createPackageItem(qualifiedName = "")
        codebase.addPackage(rootPackage)
    }

    override fun createClassFromUnderlyingModel(qualifiedName: String): ClassItem? {
        TODO("Not yet implemented")
    }
}
