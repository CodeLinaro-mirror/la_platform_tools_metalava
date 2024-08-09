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
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.findClosestEnclosingNonEmptyPackage
import com.android.tools.metalava.reporter.FileLocation

class DefaultPackageItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    modifiers: DefaultModifierList,
    documentation: ItemDocumentation,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    private val qualifiedName: String,
) :
    DefaultItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = itemLanguage,
        modifiers = modifiers,
        documentation = documentation,
        variantSelectorsFactory = variantSelectorsFactory,
    ),
    PackageItem {

    private val topClasses = mutableListOf<ClassItem>()

    override fun qualifiedName(): String = qualifiedName

    override fun topLevelClasses(): List<ClassItem> = topClasses.toList()

    // N.A. a package cannot be contained in a class
    override fun containingClass(): ClassItem? = null

    private lateinit var containingPackageField: PackageItem

    override fun containingPackage(): PackageItem? {
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
