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

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultConstructorItem
import com.android.tools.metalava.reporter.FileLocation
import java.util.function.Predicate

internal open class TextClassItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation = FileLocation.UNKNOWN,
    modifiers: DefaultModifierList,
    classKind: ClassKind = ClassKind.CLASS,
    containingClass: ClassItem?,
    qualifiedName: String = "",
    simpleName: String = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1),
    fullName: String = simpleName,
    typeParameterList: TypeParameterList = TypeParameterList.NONE,
) :
    DefaultClassItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = ItemLanguage.UNKNOWN,
        modifiers = modifiers,
        documentation = ItemDocumentation.NONE,
        variantSelectorsFactory = ApiVariantSelectors.IMMUTABLE_FACTORY,
        source = null,
        classKind = classKind,
        containingClass = containingClass,
        qualifiedName = qualifiedName,
        simpleName = simpleName,
        fullName = fullName,
        typeParameterList = typeParameterList,
    ) {

    override fun filteredSuperClassType(predicate: Predicate<Item>): ClassTypeItem? {
        // No filtering in signature files: we assume signature APIs
        // have already been filtered and all items should match.
        // This lets us load signature files and rewrite them using updated
        // output formats etc.
        return superClassType()
    }

    override fun createDefaultConstructor(): ConstructorItem {
        return DefaultConstructorItem.createDefaultConstructor(
            codebase = codebase,
            itemLanguage = ItemLanguage.UNKNOWN,
            variantSelectorsFactory = ApiVariantSelectors.IMMUTABLE_FACTORY,
            containingClass = this,
        )
    }
}
