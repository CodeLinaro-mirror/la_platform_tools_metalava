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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultTypeItem
import com.android.tools.metalava.model.LambdaTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.util.TypeConversionUtil

/** Represents a type backed by PSI */
internal sealed class PsiTypeItem(
    val psiType: PsiType,
    modifiers: TypeModifiers,
    val kotlinTypeInfo: KotlinTypeInfo?,
) : DefaultTypeItem(modifiers) {
    /** Whether the [psiType] is originally a value class type. */
    fun isValueClassType(): Boolean = kotlinTypeInfo?.isValueClassType() ?: false

    /** Returns `true` if `this` type can be assigned from `other` without unboxing the other. */
    override fun isAssignableFromWithoutUnboxing(other: TypeItem): Boolean {
        if (other !is PsiTypeItem) return super.isAssignableFromWithoutUnboxing(other)
        if (this is PrimitiveTypeItem && other !is PrimitiveTypeItem) {
            return false
        }
        return TypeConversionUtil.isAssignable(psiType, other.psiType)
    }
}

/** A [PsiTypeItem] backed by a [PsiPrimitiveType]. */
internal class PsiPrimitiveTypeItem(
    psiType: PsiType,
    override val kind: PrimitiveTypeItem.Primitive,
    modifiers: TypeModifiers,
    kotlinTypeInfo: KotlinTypeInfo?,
) : PrimitiveTypeItem, PsiTypeItem(psiType, modifiers, kotlinTypeInfo) {
    @Deprecated(
        "implementation detail of this class",
        replaceWith = ReplaceWith("substitute(modifiers)"),
    )
    override fun duplicate(modifiers: TypeModifiers): PsiPrimitiveTypeItem =
        PsiPrimitiveTypeItem(
            psiType = psiType,
            kind = kind,
            modifiers = modifiers,
            kotlinTypeInfo = kotlinTypeInfo,
        )
}

/** A [PsiTypeItem] backed by a [PsiArrayType]. */
internal class PsiArrayTypeItem(
    psiType: PsiType,
    override val componentType: TypeItem,
    override val isVarargs: Boolean,
    modifiers: TypeModifiers,
    kotlinTypeInfo: KotlinTypeInfo?,
) : ArrayTypeItem, PsiTypeItem(psiType, modifiers, kotlinTypeInfo) {
    @Deprecated(
        "implementation detail of this class",
        replaceWith = ReplaceWith("substitute(modifiers, componentType)"),
    )
    override fun duplicate(modifiers: TypeModifiers, componentType: TypeItem): ArrayTypeItem =
        PsiArrayTypeItem(
            psiType = psiType,
            componentType = componentType,
            isVarargs = isVarargs,
            modifiers = modifiers,
            kotlinTypeInfo = kotlinTypeInfo,
        )
}

/** A [PsiTypeItem] backed by a [PsiClassType] that does not represent a type variable. */
internal open class PsiClassTypeItem(
    protected val codebase: Codebase,
    psiType: PsiType,
    final override val qualifiedName: String,
    final override val arguments: List<TypeArgumentTypeItem>,
    final override val outerClassType: ClassTypeItem?,
    modifiers: TypeModifiers,
    kotlinTypeInfo: KotlinTypeInfo?,
) : ClassTypeItem, PsiTypeItem(psiType, modifiers, kotlinTypeInfo) {
    override val className: String = ClassTypeItem.computeClassName(qualifiedName)

    private val asClassCache by
        lazy(LazyThreadSafetyMode.NONE) { codebase.resolveClass(qualifiedName) }

    override fun asClass() = asClassCache

    override fun isFunctionalType(): Boolean {
        return LambdaUtil.isFunctionalType(psiType)
    }

    @Deprecated(
        "implementation detail of this class",
        replaceWith = ReplaceWith("substitute(modifiers, outerClassType, arguments)"),
    )
    override fun duplicate(
        modifiers: TypeModifiers,
        outerClassType: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>
    ): ClassTypeItem =
        PsiClassTypeItem(
            codebase = codebase,
            psiType = psiType,
            qualifiedName = qualifiedName,
            arguments = arguments,
            outerClassType = outerClassType,
            modifiers = modifiers,
            kotlinTypeInfo = kotlinTypeInfo,
        )
}

internal class PsiLambdaTypeItem(
    codebase: Codebase,
    psiType: PsiType,
    qualifiedName: String,
    arguments: List<TypeArgumentTypeItem>,
    outerClassType: ClassTypeItem?,
    modifiers: TypeModifiers,
    override val isSuspend: Boolean,
    override val receiverType: TypeItem?,
    override val parameterTypes: List<TypeItem>,
    override val returnType: TypeItem,
    kotlinTypeInfo: KotlinTypeInfo?,
) :
    PsiClassTypeItem(
        codebase = codebase,
        psiType = psiType,
        qualifiedName = qualifiedName,
        arguments = arguments,
        outerClassType = outerClassType,
        modifiers = modifiers,
        kotlinTypeInfo = kotlinTypeInfo,
    ),
    LambdaTypeItem {

    @Deprecated(
        "implementation detail of this class",
        replaceWith = ReplaceWith("substitute(modifiers, outerClassType, arguments)"),
    )
    override fun duplicate(
        modifiers: TypeModifiers,
        outerClassType: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>
    ): LambdaTypeItem {
        return PsiLambdaTypeItem(
            codebase = codebase,
            psiType = psiType,
            qualifiedName = qualifiedName,
            arguments = arguments,
            outerClassType = outerClassType,
            modifiers = modifiers,
            isSuspend = isSuspend,
            receiverType = receiverType,
            parameterTypes = parameterTypes,
            returnType = returnType,
            kotlinTypeInfo = kotlinTypeInfo,
        )
    }
}

/** A [PsiTypeItem] backed by a [PsiClassType] that represents a type variable.e */
internal class PsiVariableTypeItem(
    psiType: PsiType,
    modifiers: TypeModifiers,
    override val asTypeParameter: TypeParameterItem,
    kotlinTypeInfo: KotlinTypeInfo?,
) : VariableTypeItem, PsiTypeItem(psiType, modifiers, kotlinTypeInfo) {

    override val name: String = asTypeParameter.name()

    @Deprecated(
        "implementation detail of this class",
        replaceWith = ReplaceWith("substitute(modifiers)"),
    )
    override fun duplicate(modifiers: TypeModifiers): PsiVariableTypeItem =
        PsiVariableTypeItem(
            psiType = psiType,
            modifiers = modifiers,
            asTypeParameter = asTypeParameter,
            kotlinTypeInfo = kotlinTypeInfo,
        )
}

/** A [PsiTypeItem] backed by a [PsiWildcardType]. */
internal class PsiWildcardTypeItem(
    psiType: PsiType,
    override val extendsBound: ReferenceTypeItem?,
    override val superBound: ReferenceTypeItem?,
    modifiers: TypeModifiers,
    kotlinTypeInfo: KotlinTypeInfo?,
) : WildcardTypeItem, PsiTypeItem(psiType, modifiers, kotlinTypeInfo) {
    @Deprecated(
        "implementation detail of this class",
        replaceWith = ReplaceWith("substitute(modifiers, extendsBound, superBound)")
    )
    override fun duplicate(
        modifiers: TypeModifiers,
        extendsBound: ReferenceTypeItem?,
        superBound: ReferenceTypeItem?
    ): WildcardTypeItem =
        PsiWildcardTypeItem(
            psiType = psiType,
            extendsBound = extendsBound,
            superBound = superBound,
            modifiers = modifiers,
            kotlinTypeInfo = kotlinTypeInfo,
        )
}
