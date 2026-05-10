/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.InheritableItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.RecordComponentItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.duplicatingFactory
import com.android.tools.metalava.model.scope.NameClassification
import com.android.tools.metalava.model.scope.ReferencableNameScope
import com.android.tools.metalava.reporter.FileLocation

internal class DefaultPropertyItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    sourceLanguage: SourceLanguage,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    modifiers: BaseModifierList,
    override val name: String,
    containingClass: ClassItem,
    type: TypeItem,
    override val getter: MethodItem?,
    override val setter: MethodItem?,
    override val constructorParameter: ParameterItem?,
    override val backingField: FieldItem?,
    override val receiver: TypeItem?,
    override val typeParameterList: TypeParameterList,
    override val setterVisibility: VisibilityLevel?,
    override val recordComponentIndex: Int,
) :
    DefaultMemberItem(
        codebase,
        fileLocation,
        sourceLanguage,
        // Properties can only be used directly from Kotlin. They are used from Java through their
        // accessors and/or backing field.
        targetLanguages = TargetLanguageSet.KOTLIN_ONLY,
        modifiers,
        documentationFactory,
        variantSelectorsFactory,
        name,
        containingClass,
    ),
    PropertyItem,
    RecordComponentItem {

    private var _type = type

    override fun type(): TypeItem = _type

    override val type: TypeItem
        get() = _type

    override fun setType(type: TypeItem) {
        this._type = type
    }

    override val containingScope: ReferencableNameScope?
        get() =
            // Fallback to the containing class.
            containingClass()

    override fun resolveReferencableItemBySimpleName(
        simpleName: String,
        nameClassification: NameClassification,
        isFirstSimpleName: Boolean
    ) =
        // Property does not define a name scope.
        null

    override var inheritedFrom: ClassItem? = null

    override fun duplicate(targetContainingClass: ClassItem): InheritableItem {
        return DefaultPropertyItem(
                codebase = codebase,
                fileLocation = fileLocation,
                sourceLanguage = sourceLanguage,
                documentationFactory = documentation.duplicatingFactory(),
                variantSelectorsFactory = variantSelectors::duplicate,
                modifiers = modifiers,
                name = name(),
                containingClass = targetContainingClass,
                type = type,
                getter = null,
                setter = null,
                constructorParameter = null,
                backingField = null,
                receiver = receiver,
                typeParameterList = typeParameterList,
                setterVisibility = setterVisibility,
                recordComponentIndex = recordComponentIndex,
            )
            .also { duplicated -> duplicated.inheritedFrom = containingClass() }
    }
}
