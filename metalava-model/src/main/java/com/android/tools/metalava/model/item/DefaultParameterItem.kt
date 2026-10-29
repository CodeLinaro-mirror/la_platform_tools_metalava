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

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.ParameterKind
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeItemConverter
import com.android.tools.metalava.reporter.FileLocation

internal class DefaultParameterItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    sourceLanguage: SourceLanguage,
    modifiers: BaseModifierList,
    private val name: String,
    protected val publicName: String?,
    private val containingItem: MemberItem,
    override val parameterIndex: Int,
    private var type: TypeItem,
    private val hasDefaultValue: Boolean,
    override val kind: ParameterKind,
) :
    DefaultItem(
        codebase = codebase,
        fileLocation = fileLocation,
        sourceLanguage = sourceLanguage,
        modifiers = modifiers,
    ),
    ParameterItem {

    init {
        // Set the varargs modifier to true if the type is a varargs.
        type.let { if (it is ArrayTypeItem && it.isVarargs) mutateModifiers { setVarArg(true) } }
    }

    override fun name(): String = name

    override fun publicName(): String? = publicName

    override fun parent(): MemberItem = containingItem

    private val containingCallable: CallableItem? = containingItem as? CallableItem

    override fun containingCallable(): CallableItem? = containingCallable

    override fun type(): TypeItem = type

    override fun setType(type: TypeItem) {
        this.type = type
    }

    override fun hasDefaultValue(): Boolean = hasDefaultValue

    override var property: PropertyItem? = null

    override fun duplicate(
        containingItem: MemberItem,
        typeConverter: TypeItemConverter,
        newParameterIndex: Int,
    ): ParameterItem =
        DefaultParameterItem(
            codebase,
            fileLocation,
            sourceLanguage,
            modifiers,
            name(),
            publicName,
            containingItem,
            newParameterIndex,
            typeConverter(type()),
            hasDefaultValue(),
            kind,
        )
}
