/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.metalava.model.ApiVariantSelectorsFactory
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.reporter.FileLocation

open class DefaultPackageItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    modifiers: BaseModifierList,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    private val qualifiedName: String,
    val containingPackage: PackageItem?,
    override val overviewDocumentation: ResourceFile?,
) :
    DefaultItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = itemLanguage,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = variantSelectorsFactory,
    ),
    PackageItem {

    init {
        // Newly created package's always have `emit = false` as they should only be emitted if they
        // have at least one class that has `emit = true`. That will be updated, if necessary, when
        // adding a class to the package.
        emit = false
    }

    private val topClasses = mutableListOf<ClassItem>()

    final override fun qualifiedName(): String = qualifiedName

    final override fun topLevelClasses(): List<ClassItem> =
        // Return a copy to avoid a ConcurrentModificationException.
        topClasses.toList()

    // N.A. a package cannot be contained in a class
    override fun containingClass(): ClassItem? = null

    final override fun containingPackage(): PackageItem? {
        return containingPackage
    }

    fun addTopClass(classItem: ClassItem) {
        topClasses.add(classItem)
    }
}
