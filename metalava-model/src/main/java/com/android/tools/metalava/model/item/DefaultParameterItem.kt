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
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterBindings
import com.android.tools.metalava.reporter.FileLocation

open class DefaultParameterItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    modifiers: DefaultModifierList,
    private val name: String,
    protected val publicNameProvider: PublicNameProvider,
    private val containingCallable: CallableItem,
    override val parameterIndex: Int,
    private var type: TypeItem,
    defaultValueFactory: DefaultValueFactory,
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

    init {
        // Set the varargs modifier to true if the type is a varargs.
        type.let { if (it is ArrayTypeItem && it.isVarargs) mutateModifiers { setVarArg(true) } }
    }

    /**
     * Create the [DefaultValue] during initialization of this parameter to allow it to contain an
     * immutable reference to this object.
     */
    final override val defaultValue = defaultValueFactory(this)

    final override fun name(): String = name

    final override fun publicName(): String? = publicNameProvider(this)

    final override fun containingCallable(): CallableItem = containingCallable

    final override fun type(): TypeItem = type

    final override fun setType(type: TypeItem) {
        this.type = type
    }

    final override fun hasDefaultValue(): Boolean = defaultValue.hasDefaultValue()

    final override fun isDefaultValueKnown(): Boolean = defaultValue.isDefaultValueKnown()

    final override fun defaultValueAsString(): String? = defaultValue.value()

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
            defaultValue::duplicate,
        )
}
