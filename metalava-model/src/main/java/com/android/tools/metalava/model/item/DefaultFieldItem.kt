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
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.reporter.FileLocation

class DefaultFieldItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    modifiers: DefaultModifierList,
    documentationFactory: ItemDocumentationFactory,
    name: String,
    containingClass: ClassItem,
    private var type: TypeItem,
    private val isEnumConstant: Boolean,
    override val fieldValue: FieldValue?,
) :
    DefaultMemberItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = itemLanguage,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = variantSelectorsFactory,
        name = name,
        containingClass = containingClass,
    ),
    FieldItem {

    override var inheritedFrom: ClassItem? = null

    override fun type(): TypeItem = type

    override fun setType(type: TypeItem) {
        this.type = type
    }

    override fun duplicate(targetContainingClass: ClassItem) =
        DefaultFieldItem(
                codebase = codebase,
                fileLocation = fileLocation,
                itemLanguage = itemLanguage,
                variantSelectorsFactory = variantSelectors::duplicate,
                modifiers = modifiers.duplicate(),
                documentationFactory = documentation::duplicate,
                name = name(),
                containingClass = targetContainingClass,
                type = type,
                isEnumConstant = isEnumConstant,
                fieldValue = fieldValue,
            )
            .also { duplicated -> duplicated.inheritedFrom = containingClass() }

    override fun initialValue(requireConstant: Boolean) = fieldValue?.initialValue(requireConstant)

    override fun isEnumConstant(): Boolean = isEnumConstant
}
