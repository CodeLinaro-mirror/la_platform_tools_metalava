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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.TypeParameterList
import com.google.turbine.binder.sym.MethodSymbol

internal class TurbineConstructorItem(
    codebase: TurbineBasedCodebase,
    private val name: String,
    methodSymbol: MethodSymbol,
    containingClass: TurbineClassItem,
    returnType: TurbineTypeItem,
    modifiers: TurbineModifierItem,
    typeParameters: TypeParameterList,
    documentation: String,
) :
    TurbineMethodItem(
        codebase,
        methodSymbol,
        containingClass,
        returnType,
        modifiers,
        typeParameters,
        documentation
    ),
    ConstructorItem {

    override fun name(): String = name

    override var superConstructor: ConstructorItem? = null

    override fun isConstructor(): Boolean = true

    internal fun setReturnType(type: TurbineTypeItem) {
        returnType = type
    }

    companion object {
        fun createDefaultConstructor(
            codebase: TurbineBasedCodebase,
            containingClass: TurbineClassItem,
            symbol: MethodSymbol
        ): TurbineConstructorItem {
            val name = containingClass.simpleName()
            val modifiers = TurbineModifierItem(codebase, DefaultModifierList.PACKAGE_PRIVATE, null)
            modifiers.setVisibilityLevel(containingClass.modifiers.getVisibilityLevel())
            val parameters = TurbineTypeParameterList(codebase)
            parameters.typeParameters = emptyList()

            val ctorItem =
                TurbineConstructorItem(
                    codebase,
                    name,
                    symbol,
                    containingClass,
                    containingClass.toType(),
                    modifiers,
                    parameters,
                    "",
                )
            modifiers.setOwner(ctorItem)
            ctorItem.parameters = emptyList()
            ctorItem.throwsClassNames = emptyList()
            ctorItem.setThrowsTypes()
            return ctorItem
        }
    }
}
