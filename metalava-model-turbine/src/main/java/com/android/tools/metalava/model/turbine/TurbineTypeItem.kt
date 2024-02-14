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

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.google.turbine.binder.sym.TyVarSymbol

internal sealed class TurbineTypeItem(
    val codebase: TurbineBasedCodebase,
    override val modifiers: TurbineTypeModifiers,
) : DefaultTypeItem(codebase) {}

internal class TurbinePrimitiveTypeItem(
    codebase: TurbineBasedCodebase,
    modifiers: TurbineTypeModifiers,
    override val kind: Primitive,
) : PrimitiveTypeItem, TurbineTypeItem(codebase, modifiers) {
    override fun duplicate(): PrimitiveTypeItem =
        TurbinePrimitiveTypeItem(codebase, modifiers.duplicate(), kind)
}

internal class TurbineArrayTypeItem(
    codebase: TurbineBasedCodebase,
    modifiers: TurbineTypeModifiers,
    override val componentType: TurbineTypeItem,
    override val isVarargs: Boolean,
) : ArrayTypeItem, TurbineTypeItem(codebase, modifiers) {
    override fun duplicate(componentType: TypeItem): ArrayTypeItem {
        return TurbineArrayTypeItem(
            codebase,
            modifiers.duplicate(),
            componentType as TurbineTypeItem,
            isVarargs
        )
    }
}

internal class TurbineClassTypeItem(
    codebase: TurbineBasedCodebase,
    modifiers: TurbineTypeModifiers,
    override val qualifiedName: String,
    override val arguments: List<TypeArgumentTypeItem>,
    override val outerClassType: TurbineClassTypeItem?,
) : ClassTypeItem, TurbineTypeItem(codebase, modifiers) {
    override val className: String = ClassTypeItem.computeClassName(qualifiedName)

    private val asClassCache by
        lazy(LazyThreadSafetyMode.NONE) { codebase.resolveClass(qualifiedName) }

    override fun asClass() = asClassCache

    override fun duplicate(
        outerClass: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>
    ): ClassTypeItem {
        return TurbineClassTypeItem(
            codebase,
            modifiers.duplicate(),
            qualifiedName,
            arguments,
            outerClass as? TurbineClassTypeItem
        )
    }
}

internal class TurbineVariableTypeItem(
    codebase: TurbineBasedCodebase,
    modifiers: TurbineTypeModifiers,
    private val symbol: TyVarSymbol
) : VariableTypeItem, TurbineTypeItem(codebase, modifiers) {
    override val name: String = symbol.name()
    override val asTypeParameter: TypeParameterItem by lazy { codebase.findTypeParameter(symbol) }

    override fun duplicate(): VariableTypeItem =
        TurbineVariableTypeItem(codebase, modifiers.duplicate(), symbol)
}

internal class TurbineWildcardTypeItem(
    codebase: TurbineBasedCodebase,
    modifiers: TurbineTypeModifiers,
    override val extendsBound: ReferenceTypeItem?,
    override val superBound: ReferenceTypeItem?,
) : WildcardTypeItem, TurbineTypeItem(codebase, modifiers) {
    override fun duplicate(
        extendsBound: ReferenceTypeItem?,
        superBound: ReferenceTypeItem?
    ): TurbineWildcardTypeItem {
        return TurbineWildcardTypeItem(
            codebase,
            modifiers.duplicate(),
            extendsBound,
            superBound,
        )
    }
}
