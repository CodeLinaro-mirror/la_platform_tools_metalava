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
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem

internal sealed class TurbineTypeItem(
    override val modifiers: TypeModifiers,
) : DefaultTypeItem()

internal class TurbinePrimitiveTypeItem(
    modifiers: TypeModifiers,
    override val kind: Primitive,
) : PrimitiveTypeItem, TurbineTypeItem(modifiers) {
    override fun duplicate(): PrimitiveTypeItem =
        TurbinePrimitiveTypeItem(modifiers.duplicate(), kind)
}

internal class TurbineArrayTypeItem(
    modifiers: TypeModifiers,
    override val componentType: TypeItem,
    override val isVarargs: Boolean,
) : ArrayTypeItem, TurbineTypeItem(modifiers) {
    override fun duplicate(componentType: TypeItem): ArrayTypeItem {
        return TurbineArrayTypeItem(modifiers.duplicate(), componentType, isVarargs)
    }
}

internal class TurbineClassTypeItem(
    private val codebase: Codebase,
    modifiers: TypeModifiers,
    override val qualifiedName: String,
    override val arguments: List<TypeArgumentTypeItem>,
    override val outerClassType: TurbineClassTypeItem?,
) : ClassTypeItem, TurbineTypeItem(modifiers) {
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
    modifiers: TypeModifiers,
    override val asTypeParameter: TypeParameterItem,
) : VariableTypeItem, TurbineTypeItem(modifiers) {
    override val name: String = asTypeParameter.name()

    override fun duplicate(): VariableTypeItem =
        TurbineVariableTypeItem(modifiers.duplicate(), asTypeParameter)
}

internal class TurbineWildcardTypeItem(
    modifiers: TypeModifiers,
    override val extendsBound: ReferenceTypeItem?,
    override val superBound: ReferenceTypeItem?,
) : WildcardTypeItem, TurbineTypeItem(modifiers) {
    override fun duplicate(
        extendsBound: ReferenceTypeItem?,
        superBound: ReferenceTypeItem?
    ): TurbineWildcardTypeItem {
        return TurbineWildcardTypeItem(
            modifiers.duplicate(),
            extendsBound,
            superBound,
        )
    }
}
