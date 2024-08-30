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

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterBindings
import com.android.tools.metalava.reporter.FileLocation

internal class DefaultParameterItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    modifiers: DefaultModifierList,
    private val name: String,
    private val publicNameProvider: PublicNameProvider,
    private val containingCallable: CallableItem,
    override val parameterIndex: Int,
    private var type: TypeItem,
    override val defaultValue: DefaultValue,
) :
    DefaultItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = itemLanguage,
        modifiers = modifiers,
        documentationFactory = ItemDocumentation.NONE_FACTORY,
        variantSelectorsFactory = ApiVariantSelectors.IMMUTABLE_FACTORY,
    ),
    ParameterItem {

    override fun name(): String = name

    override fun publicName(): String? = publicNameProvider(this)

    override fun containingCallable(): CallableItem = containingCallable

    override fun isVarArgs(): Boolean = modifiers.isVarArg()

    override fun type(): TypeItem = type

    override fun setType(type: TypeItem) {
        this.type = type
    }

    override fun hasDefaultValue(): Boolean = defaultValue.hasDefaultValue()

    override fun isDefaultValueKnown(): Boolean = defaultValue.isDefaultValueKnown()

    override fun defaultValueAsString(): String? = defaultValue.value()

    override fun duplicate(
        containingCallable: CallableItem,
        typeVariableMap: TypeParameterBindings,
    ) =
        DefaultParameterItem(
            codebase,
            fileLocation,
            itemLanguage,
            modifiers.duplicate(),
            name(),
            publicNameProvider,
            containingCallable,
            parameterIndex,
            type().convertType(typeVariableMap),
            defaultValue,
        )
}
