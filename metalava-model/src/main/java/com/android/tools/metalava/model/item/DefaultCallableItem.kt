/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.tools.metalava.model.CallableBody
import com.android.tools.metalava.model.CallableBodyFactory
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.reporter.FileLocation

/**
 * A lamda that given a [CallableItem] will create a list of [ParameterItem]s for it.
 *
 * This is called from within the constructor of the [ParameterItem.containingCallable] and can only
 * access the [CallableItem.name] (to identify callables that have special nullability rules) and
 * store a reference to it in [ParameterItem.containingCallable]. In particularly, it must not
 * access [CallableItem.parameters] as that will not yet have been initialized when this is called.
 */
typealias ParameterItemsFactory = (CallableItem) -> List<ParameterItem>

abstract class DefaultCallableItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    modifiers: DefaultModifierList,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    name: String,
    containingClass: ClassItem,
    override val typeParameterList: TypeParameterList,
    returnType: TypeItem,
    parameterItemsFactory: ParameterItemsFactory,
    internal val throwsTypes: List<ExceptionTypeItem>,
    callableBodyFactory: CallableBodyFactory,
) :
    DefaultMemberItem(
        codebase,
        fileLocation,
        itemLanguage,
        modifiers,
        documentationFactory,
        variantSelectorsFactory,
        name,
        containingClass,
    ),
    CallableItem {

    /** Make it readable to subclasses but not writable. */
    protected var returnType: TypeItem = returnType
        private set

    /**
     * Create the [ParameterItem] list during initialization of this callable to allow them to
     * contain an immutable reference to this object.
     *
     * The leaking of `this` to `parameterItemsFactory` is ok as implementations follow the rules
     * explained in the documentation of [ParameterItemsFactory].
     */
    @Suppress("LeakingThis") internal val parameters = parameterItemsFactory(this)

    override fun returnType(): TypeItem = returnType

    override fun setType(type: TypeItem) {
        returnType = type
    }

    final override fun parameters(): List<ParameterItem> = parameters

    final override fun throwsTypes(): List<ExceptionTypeItem> = throwsTypes

    /**
     * Create the [CallableBody] during initialization of this callable to allow it to contain an
     * immutable reference to this object.
     *
     * The leaking of `this` to `callableBodyFactory` is ok as implementations follow the rules
     * explained in the documentation of [CallableBodyFactory].
     */
    final override val body: CallableBody = callableBodyFactory(@Suppress("LeakingThis") this)
}
