/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.reporter.FileLocation

class DefaultConstructorItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    modifiers: DefaultModifierList,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    name: String,
    containingClass: ClassItem,
    typeParameterList: TypeParameterList,
    returnType: ClassTypeItem,
    parameterItemsFactory: ParameterItemsFactory,
    throwsTypes: List<ExceptionTypeItem>,
    private val implicitConstructor: Boolean,
) :
    DefaultMethodItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = itemLanguage,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = variantSelectorsFactory,
        name = name,
        containingClass = containingClass,
        typeParameterList = typeParameterList,
        returnType = returnType,
        parameterItemsFactory = parameterItemsFactory,
        throwsTypes = throwsTypes,
    ),
    ConstructorItem {

    override var superConstructor: ConstructorItem? = null

    override fun isConstructor(): Boolean = true

    override fun isImplicitConstructor() = implicitConstructor

    companion object {
        fun createDefaultConstructor(
            codebase: DefaultCodebase,
            itemLanguage: ItemLanguage,
            variantSelectorsFactory: ApiVariantSelectorsFactory,
            containingClass: ClassItem,
        ): ConstructorItem {
            val name = containingClass.simpleName()
            val modifiers = DefaultModifierList(codebase, DefaultModifierList.PACKAGE_PRIVATE, null)
            modifiers.setVisibilityLevel(containingClass.modifiers.getVisibilityLevel())

            val ctorItem =
                DefaultConstructorItem(
                    codebase = codebase,
                    // Use the location of the containing class for the default constructor.
                    fileLocation = containingClass.fileLocation,
                    itemLanguage = itemLanguage,
                    modifiers = modifiers,
                    documentationFactory = ItemDocumentation.NONE_FACTORY,
                    variantSelectorsFactory = variantSelectorsFactory,
                    name = name,
                    containingClass = containingClass,
                    typeParameterList = TypeParameterList.NONE,
                    returnType = containingClass.type(),
                    parameterItemsFactory = { emptyList() },
                    throwsTypes = emptyList(),
                    // This is not an implicit constructor as it was not created by the compiler.
                    implicitConstructor = false,
                )
            return ctorItem
        }
    }
}
