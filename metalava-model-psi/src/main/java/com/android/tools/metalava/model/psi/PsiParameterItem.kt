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

import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterBindings
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.item.DefaultParameterItem
import com.android.tools.metalava.model.psi.PsiMethodItem.Companion.isKotlinProperty
import com.android.tools.metalava.model.type.MethodFingerprint
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter

internal class PsiParameterItem
internal constructor(
    override val psiCodebase: PsiBasedCodebase,
    internal val psiParameter: PsiParameter,
    modifiers: BaseModifierList,
    name: String,
    publicName: String?,
    containingCallable: PsiCallableItem,
    parameterIndex: Int,
    type: TypeItem,
    hasDefaultValue: Boolean,
) :
    DefaultParameterItem(
        codebase = psiCodebase,
        fileLocation = PsiFileLocation.fromPsiElement(psiParameter),
        sourceLanguage = psiParameter.sourceLanguage,
        modifiers = modifiers,
        name = name,
        publicName = publicName,
        containingCallable = containingCallable,
        parameterIndex = parameterIndex,
        type = type,
        hasDefaultValue = hasDefaultValue,
    ),
    PsiItem {

    override fun psi() = psiParameter

    override fun duplicate(
        containingCallable: CallableItem,
        typeVariableMap: TypeParameterBindings
    ) =
        PsiParameterItem(
            psiCodebase = psiCodebase,
            psiParameter = psiParameter,
            modifiers = modifiers,
            name = name(),
            publicName = publicName,
            containingCallable = containingCallable as PsiCallableItem,
            parameterIndex = parameterIndex,
            type = type().convertType(typeVariableMap) as PsiTypeItem,
            hasDefaultValue = hasDefaultValue(),
        )

    companion object {
        internal fun create(
            codebase: PsiBasedCodebase,
            containingCallable: PsiCallableItem,
            fingerprint: MethodFingerprint,
            psiParameter: PsiParameter,
            parameterIndex: Int,
            enclosingMethodTypeItemFactory: PsiTypeItemFactory,
            psiMethod: PsiMethod,
            containingCallableModifiers: BaseModifierList,
        ): PsiParameterItem {
            val name = psiParameter.name
            val modifiers = createParameterModifiers(codebase, psiParameter)
            val type =
                enclosingMethodTypeItemFactory.getMethodParameterType(
                    underlyingParameterType = PsiTypeInfo(psiParameter.type, psiParameter),
                    itemAnnotations = modifiers.annotations(),
                    fingerprint = fingerprint,
                    parameterIndex = parameterIndex,
                    isVarArg = psiParameter.type is PsiEllipsisType,
                )
            val parameter =
                PsiParameterItem(
                    psiCodebase = codebase,
                    psiParameter = psiParameter,
                    modifiers = modifiers,
                    name = name,
                    publicName =
                        getPublicName(
                            psiParameter,
                            parameterIndex,
                            fingerprint.parameterCount,
                            psiMethod,
                            containingCallableModifiers,
                        ),
                    containingCallable = containingCallable,
                    parameterIndex = parameterIndex,
                    type = type,
                    hasDefaultValue =
                        PsiParameterDefaultValue.compute(psiParameter, parameterIndex),
                )
            return parameter
        }

        private fun createParameterModifiers(
            codebase: PsiBasedCodebase,
            psiParameter: PsiParameter
        ): MutableModifierList {
            val modifiers = PsiModifierItem.create(codebase, psiParameter)
            // Method parameters don't have a visibility level; they are visible to anyone that can
            // call their method. However, Kotlin constructors sometimes appear to specify the
            // visibility of a constructor parameter by putting visibility inside the constructor
            // signature. This is really to indicate that the matching property should have the
            // mentioned visibility.
            // If the method parameter seems to specify a visibility level, we correct it back to
            // the default, here, to ensure we don't attempt to incorrectly emit this information
            // into a signature file.
            modifiers.setVisibilityLevel(VisibilityLevel.PACKAGE_PRIVATE)
            return modifiers
        }
    }
}

/**
 * Get the public name of a parameter.
 *
 * @param psiParameter The [PsiParameter] to find the name of.
 * @param parameterIndex The index of this parameter in the containing callable.
 * @param parameterCount The total number of parameters of the containing callable.
 * @param psiMethod The containing [PsiMethod] of the parameter.
 * @param containingCallableModifiers The modifiers of the containing callable.
 */
internal fun getPublicName(
    psiParameter: PsiParameter,
    parameterIndex: Int,
    parameterCount: Int,
    psiMethod: PsiMethod,
    containingCallableModifiers: BaseModifierList,
): String? {
    if (psiParameter.isKotlin()) {
        // Omit names of some special parameters in Kotlin. None of these parameters may be set
        // through Kotlin keyword arguments, so there's no need to track their names for
        // compatibility. This also helps avoid signature file churn if PSI or the compiler change
        // what name they're using for these parameters.

        // Receiver parameter of extension function
        // Note receiver parameter used to be named $receiver in previous UAST versions, now it is
        // $this$functionName
        if (parameterIndex == 0 && psiParameter.name.startsWith("\$this\$")) {
            return null
        }
        // Property setter parameter
        if (isKotlinProperty(psiMethod)) {
            return null
        }
        // Continuation parameter of suspend function (the final parameter of a suspend function is
        // the continuation).
        if (containingCallableModifiers.isSuspend() && parameterCount - 1 == parameterIndex) {
            return null
        }
        return psiParameter.name
    }

    return null
}
