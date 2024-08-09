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
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.computeSuperMethods
import com.android.tools.metalava.model.item.DefaultMemberItem
import com.android.tools.metalava.model.updateCopiedMethodState
import com.android.tools.metalava.reporter.FileLocation
import java.util.function.Predicate

/**
 * A lamda that given a [MethodItem] will create a list of [ParameterItem]s for it.
 *
 * This is called from within the constructor of the [ParameterItem.containingMethod] and can only
 * access the [MethodItem.name] (to identify methods that have special nullability rules) and store
 * a reference to it in [ParameterItem.containingMethod]. In particularly, it must not access
 * [MethodItem.parameters] as that will not yet have been initialized when this is called.
 */
internal typealias ParameterItemsFactory = (TextMethodItem) -> List<TextParameterItem>

internal open class TextMethodItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    modifiers: DefaultModifierList,
    name: String,
    containingClass: ClassItem,
    override val typeParameterList: TypeParameterList,
    private var returnType: TypeItem,
    parameterItemsFactory: ParameterItemsFactory,
    private val throwsTypes: List<ExceptionTypeItem>,
    private val annotationDefault: String = "",
) :
    DefaultMemberItem(
        codebase,
        fileLocation,
        ItemLanguage.UNKNOWN,
        modifiers,
        ItemDocumentation.NONE,
        ApiVariantSelectors.IMMUTABLE_FACTORY,
        name,
        containingClass,
    ),
    MethodItem {

    /**
     * Create the [ParameterItem] list during initialization of this method to allow them to contain
     * an immutable reference to this object.
     *
     * The leaking of `this` to `parameterItemsFactory` is ok as implementations follow the rules
     * explained in the documentation of [ParameterItemsFactory].
     */
    @Suppress("LeakingThis") private val parameters = parameterItemsFactory(this)

    override fun equals(other: Any?) = equalsToItem(other)

    override fun hashCode() = hashCodeForItem()

    override fun isConstructor(): Boolean = false

    override fun returnType(): TypeItem = returnType

    override fun setType(type: TypeItem) {
        returnType = type
    }

    override fun superMethods(): List<MethodItem> {
        return computeSuperMethods()
    }

    override fun findMainDocumentation(): String = documentation.text

    override fun findPredicateSuperMethod(predicate: Predicate<Item>): MethodItem? = null

    override fun duplicate(targetContainingClass: ClassItem): MethodItem {
        val typeVariableMap = targetContainingClass.mapTypeVariables(containingClass())
        val duplicated =
            TextMethodItem(
                codebase = codebase,
                fileLocation = fileLocation,
                modifiers = modifiers.duplicate(),
                name = name(),
                containingClass = targetContainingClass,
                typeParameterList = typeParameterList,
                returnType = returnType.convertType(typeVariableMap),
                parameterItemsFactory = { methodItem ->
                    // Duplicate the parameters
                    parameters.map { it.duplicate(methodItem, typeVariableMap) }
                },
                throwsTypes = throwsTypes,
                annotationDefault = annotationDefault,
            )
        duplicated.inheritedFrom = containingClass()

        // Preserve flags that may have been inherited (propagated) from surrounding packages
        if (targetContainingClass.hidden) {
            duplicated.hidden = true
        }
        if (targetContainingClass.removed) {
            duplicated.removed = true
        }
        if (targetContainingClass.docOnly) {
            duplicated.docOnly = true
        }

        duplicated.updateCopiedMethodState()

        return duplicated
    }

    override fun throwsTypes(): List<ExceptionTypeItem> = this.throwsTypes

    override fun parameters(): List<ParameterItem> = parameters

    override fun isExtensionMethod(): Boolean = codebase.unsupported()

    override var inheritedFrom: ClassItem? = null

    @Deprecated("This property should not be accessed directly.")
    override var _requiresOverride: Boolean? = null

    override fun defaultValue(): String {
        return annotationDefault
    }
}
