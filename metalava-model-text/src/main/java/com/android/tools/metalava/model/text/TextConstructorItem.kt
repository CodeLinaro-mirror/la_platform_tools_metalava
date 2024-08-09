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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.reporter.FileLocation

internal class TextConstructorItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    modifiers: DefaultModifierList,
    name: String,
    containingClass: TextClassItem,
    typeParameterList: TypeParameterList,
    returnType: ClassTypeItem,
    parameterItemsFactory: ParameterItemsFactory,
    throwsTypes: List<ExceptionTypeItem>,
) :
    TextMethodItem(
        codebase = codebase,
        fileLocation = fileLocation,
        modifiers = modifiers,
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

    companion object {
        fun createDefaultConstructor(
            codebase: DefaultCodebase,
            containingClass: TextClassItem,
        ): TextConstructorItem {
            val name = containingClass.simpleName()
            val modifiers = DefaultModifierList(codebase, DefaultModifierList.PACKAGE_PRIVATE, null)
            modifiers.setVisibilityLevel(containingClass.modifiers.getVisibilityLevel())

            val ctorItem =
                TextConstructorItem(
                    codebase = codebase,
                    // Use the location of the containing class for the implicit default
                    // constructor.
                    fileLocation = containingClass.fileLocation,
                    modifiers = modifiers,
                    name = name,
                    containingClass = containingClass,
                    typeParameterList = TypeParameterList.NONE,
                    returnType = containingClass.type(),
                    parameterItemsFactory = { emptyList() },
                    throwsTypes = emptyList(),
                )
            return ctorItem
        }
    }
}
