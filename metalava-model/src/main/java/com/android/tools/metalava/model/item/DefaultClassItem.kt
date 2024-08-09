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

import com.android.tools.metalava.model.ApiVariantSelectorsFactory
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.computeAllInterfaces
import com.android.tools.metalava.model.type.DefaultResolvedClassTypeItem
import com.android.tools.metalava.reporter.FileLocation

abstract class DefaultClassItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    modifiers: DefaultModifierList,
    documentation: ItemDocumentation,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    private val source: SourceFile?,
    final override val classKind: ClassKind,
    private val containingClass: ClassItem?,
    private val qualifiedName: String,
    private val simpleName: String,
    private val fullName: String,
    final override val typeParameterList: TypeParameterList,
) :
    DefaultItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = itemLanguage,
        modifiers = modifiers,
        documentation = documentation,
        variantSelectorsFactory = variantSelectorsFactory,
    ),
    ClassItem {

    final override fun getSourceFile() = source

    final override fun containingClass() = containingClass

    final override fun qualifiedName() = qualifiedName

    final override fun simpleName() = simpleName

    final override fun fullName() = fullName

    final override fun hasTypeVariables(): Boolean = typeParameterList.isNotEmpty()

    /** Must only be used by [type] to cache its result. */
    private lateinit var cachedType: ClassTypeItem

    final override fun type(): ClassTypeItem {
        if (!::cachedType.isInitialized) {
            cachedType = DefaultResolvedClassTypeItem.createForClass(this)
        }
        return cachedType
    }

    /**
     * The optional, mutable super class [ClassTypeItem].
     *
     * This could be a constructor val apart from the fact that Text needs to mutate the super class
     * type of an existing class when merging classes that are defined in multiple API surfaces.
     */
    private var superClassType: ClassTypeItem? = null

    final override fun superClassType(): ClassTypeItem? = superClassType

    /** Set the super class [ClassTypeItem]. */
    fun setSuperClassType(superClassType: ClassTypeItem?) {
        this.superClassType = superClassType
    }

    private var interfaceTypes = emptyList<ClassTypeItem>()

    final override fun interfaceTypes(): List<ClassTypeItem> = interfaceTypes

    final override fun setInterfaceTypes(interfaceTypes: List<ClassTypeItem>) {
        this.interfaceTypes = interfaceTypes
    }

    /** Cache of the results of calling [cacheAllInterfaces]. */
    private var cacheAllInterfaces: List<ClassItem>? = null

    final override fun allInterfaces(): Sequence<ClassItem> {
        if (cacheAllInterfaces == null) {
            cacheAllInterfaces = computeAllInterfaces()
        }

        return cacheAllInterfaces!!.asSequence()
    }

    /** The mutable list of nested [ClassItem] that backs [nestedClasses]. */
    private val mutableNestedClasses = mutableListOf<ClassItem>()

    final override fun nestedClasses(): List<ClassItem> = mutableNestedClasses

    /** Add a nested class to this class. */
    fun addNestedClass(classItem: ClassItem) {
        mutableNestedClasses.add(classItem)
    }
}
