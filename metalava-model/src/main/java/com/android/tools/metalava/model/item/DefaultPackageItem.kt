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
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.findClosestEnclosingNonEmptyPackage
import com.android.tools.metalava.reporter.FileLocation

open class DefaultPackageItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    modifiers: DefaultModifierList,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    private val qualifiedName: String,
    override val overviewDocumentation: String?,
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

    private val topClasses = mutableListOf<ClassItem>()

    final override fun qualifiedName(): String = qualifiedName

    final override fun topLevelClasses(): List<ClassItem> =
        // Return a copy to avoid a ConcurrentModificationException.
        topClasses.toList()

    // N.A. a package cannot be contained in a class
    override fun containingClass(): ClassItem? = null

    // TODO(b/352480646): Make private again.
    lateinit var containingPackageField: PackageItem

    final override fun containingPackage(): PackageItem? {
        return if (qualifiedName.isEmpty()) null
        else {
            if (!::containingPackageField.isInitialized) {
                containingPackageField = codebase.findClosestEnclosingNonEmptyPackage(qualifiedName)
            }
            containingPackageField
        }
    }

    fun addTopClass(classItem: ClassItem) {
        topClasses.add(classItem)
    }
}
