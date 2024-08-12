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

import com.android.tools.metalava.model.ApiVariantSelectorsFactory
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.computeSuperMethods
import com.android.tools.metalava.model.updateCopiedMethodState
import com.android.tools.metalava.reporter.FileLocation

open class DefaultMethodItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    modifiers: DefaultModifierList,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    name: String,
    containingClass: ClassItem,
    typeParameterList: TypeParameterList,
    returnType: TypeItem,
    parameterItemsFactory: ParameterItemsFactory,
    throwsTypes: List<ExceptionTypeItem>,
    private val annotationDefault: String = "",
) :
    DefaultCallableItem(
        codebase,
        fileLocation,
        itemLanguage,
        modifiers,
        documentationFactory,
        variantSelectorsFactory,
        name,
        containingClass,
        typeParameterList,
        returnType,
        parameterItemsFactory,
        throwsTypes,
    ),
    MethodItem {

    override var inheritedFrom: ClassItem? = null

    override fun isExtensionMethod(): Boolean = false // java does not support extension methods

    override fun defaultValue() = annotationDefault

    private lateinit var superMethodList: List<MethodItem>

    /**
     * Super methods for a given method M with containing class C are calculated as follows:
     * 1) Superclass Search: Traverse the class hierarchy, starting from C's direct superclass, and
     *    add the first method that matches M's signature to the list.
     * 2) Interface Supermethod Search: For each direct interface implemented by C, check if it
     *    contains a method matching M's signature. If found, return that method. If not,
     *    recursively apply this method to the direct interfaces of the current interface.
     *
     * Note: This method's implementation is based on MethodItem.matches method which only checks
     * that name and parameter list types match. Parameter names, Return types and Throws list types
     * are not matched
     */
    override fun superMethods(): List<MethodItem> {
        if (!::superMethodList.isInitialized) {
            superMethodList = computeSuperMethods()
        }
        return superMethodList
    }

    @Deprecated("This property should not be accessed directly.")
    override var _requiresOverride: Boolean? = null

    override fun duplicate(targetContainingClass: ClassItem): MethodItem {
        val typeVariableMap = targetContainingClass.mapTypeVariables(containingClass())
        val duplicated =
            DefaultMethodItem(
                codebase = codebase,
                fileLocation = fileLocation,
                itemLanguage = itemLanguage,
                modifiers = modifiers.duplicate(),
                documentationFactory = documentation::duplicate,
                variantSelectorsFactory = variantSelectors::duplicate,
                name = name(),
                containingClass = targetContainingClass,
                typeParameterList = typeParameterList,
                returnType = returnType.convertType(typeVariableMap),
                parameterItemsFactory = { containingCallable ->
                    // Duplicate the parameters
                    parameters.map { it.duplicate(containingCallable, typeVariableMap) }
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
}
