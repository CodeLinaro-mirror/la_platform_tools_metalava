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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultConstructorItem
import com.android.tools.metalava.reporter.FileLocation

internal open class TurbineClassItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    modifiers: DefaultModifierList,
    documentation: ItemDocumentation,
    source: SourceFile?,
    classKind: ClassKind,
    containingClass: ClassItem?,
    qualifiedName: String,
    simpleName: String,
    fullName: String,
    typeParameterList: TypeParameterList,
) :
    DefaultClassItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = ItemLanguage.JAVA,
        modifiers = modifiers,
        documentation = documentation,
        variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
        source = source,
        classKind = classKind,
        containingClass = containingClass,
        qualifiedName = qualifiedName,
        simpleName = simpleName,
        fullName = fullName,
        typeParameterList = typeParameterList,
    ) {

    override fun createDefaultConstructor(): ConstructorItem {
        return DefaultConstructorItem.createDefaultConstructor(
            codebase = codebase,
            itemLanguage = ItemLanguage.JAVA,
            variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
            containingClass = this,
        )
    }
}
