/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.item.DefaultTypeParameterItem
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.kotlin.asJava.elements.KotlinLightTypeParameterBuilder
import org.jetbrains.kotlin.asJava.elements.KtLightDeclaration
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtTypeParameter

internal class PsiTypeParameterItem(
    override val codebase: PsiBasedCodebase,
    private val psiTypeParameter: PsiTypeParameter,
    name: String,
    modifiers: BaseModifierList
) :
    DefaultTypeParameterItem(
        codebase = codebase,
        modifiers = modifiers,
        name = name,
        isReified = isReified(psiTypeParameter),
    ) {
    fun psi() = psiTypeParameter

    override fun createVariableTypeItem(): VariableTypeItem {
        return codebase.globalTypeItemFactory.getVariableTypeForTypeParameter(this)
    }

    companion object {
        fun create(
            codebase: PsiBasedCodebase,
            psiTypeParameter: PsiTypeParameter,
        ): PsiTypeParameterItem {
            val simpleName = psiTypeParameter.name!!
            val modifiers = PsiModifierItem.create(codebase, psiTypeParameter)

            return PsiTypeParameterItem(
                codebase = codebase,
                psiTypeParameter = psiTypeParameter,
                name = simpleName,
                modifiers = modifiers
            )
        }

        fun isReified(element: PsiTypeParameter?): Boolean {
            element ?: return false
            // TODO(jsjeon): Handle PsiElementWithOrigin<*> when available
            if (
                element is KtLightDeclaration<*, *> &&
                    element.kotlinOrigin is KtTypeParameter &&
                    element.kotlinOrigin?.text?.startsWith(KtTokens.REIFIED_KEYWORD.value) == true
            ) {
                return true
            } else if (
                element is KotlinLightTypeParameterBuilder &&
                    element.origin.text.startsWith(KtTokens.REIFIED_KEYWORD.value)
            ) {
                return true
            }
            return false
        }
    }
}
