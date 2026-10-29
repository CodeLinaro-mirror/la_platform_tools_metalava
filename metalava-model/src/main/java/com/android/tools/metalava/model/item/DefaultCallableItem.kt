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

import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeItemConverter
import com.android.tools.metalava.model.TypeParameterBindings
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.scope.NameClassification
import com.android.tools.metalava.model.scope.ReferencableNameScope
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

internal sealed class DefaultCallableItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    sourceLanguage: SourceLanguage,
    targetLanguages: Set<TargetLanguage>,
    modifiers: BaseModifierList,
    documentationFactory: ItemDocumentationFactory,
    name: String,
    containingClass: ClassItem,
    override val typeParameterList: TypeParameterList,
    returnType: TypeItem,
    parameterItemsFactory: ParameterItemsFactory,
    internal val throwsTypes: List<ExceptionTypeItem>,
) :
    DefaultMemberItem(
        codebase,
        fileLocation,
        sourceLanguage,
        targetLanguages,
        modifiers,
        documentationFactory,
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

    override val containingScope: ReferencableNameScope?
        get() =
            // Fallback to the containing class.
            containingClass()

    override fun resolveReferencableItemBySimpleName(
        simpleName: String,
        nameClassification: NameClassification,
        isFirstSimpleName: Boolean
    ) =
        // Check for type parameters.
        nameClassification.findTypeParameter { typeParameterList.find { it.name() == simpleName } }

    companion object {
        /**
         * Create a [TypeItemConverter] wrapper around this [TypeParameterBindings]. Use the
         * identity function if the map is empty.
         */
        fun TypeParameterBindings.toTypeConverter(): TypeItemConverter =
            if (isEmpty()) { type -> type } else { type -> type.convertType(this) }
    }
}
