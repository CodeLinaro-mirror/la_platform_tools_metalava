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
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterBindings
import com.android.tools.metalava.reporter.FileLocation

const val UNKNOWN_DEFAULT_VALUE = "__unknown_default_value__"

internal class TextParameterItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    modifiers: DefaultModifierList,
    private val name: String,
    private val publicName: String?,
    private val containingMethod: MethodItem,
    override val parameterIndex: Int,
    private var type: TypeItem,
    private val hasDefaultValue: Boolean,
    private var defaultValueBody: String? = UNKNOWN_DEFAULT_VALUE,
) :
    DefaultItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = ItemLanguage.UNKNOWN,
        modifiers = modifiers,
        documentation = ItemDocumentation.NONE,
        variantSelectorsFactory = ApiVariantSelectors.IMMUTABLE_FACTORY,
    ),
    ParameterItem {

    override fun name(): String = name

    override fun publicName(): String? = publicName

    override fun containingMethod(): MethodItem = containingMethod

    override fun isVarArgs(): Boolean = modifiers.isVarArg()

    override fun type(): TypeItem = type

    override fun setType(type: TypeItem) {
        this.type = type
    }

    override fun hasDefaultValue(): Boolean = hasDefaultValue

    override fun isDefaultValueKnown(): Boolean = defaultValueBody != UNKNOWN_DEFAULT_VALUE

    override fun defaultValue(): String? = defaultValueBody

    override fun equals(other: Any?) = equalsToItem(other)

    override fun hashCode() = hashCodeForItem()

    internal fun duplicate(
        containingMethod: MethodItem,
        typeVariableMap: TypeParameterBindings,
    ): TextParameterItem {
        return TextParameterItem(
            codebase,
            fileLocation,
            modifiers.duplicate(),
            name,
            publicName,
            containingMethod,
            parameterIndex,
            type.convertType(typeVariableMap),
            hasDefaultValue,
            defaultValueBody,
        )
    }
}
