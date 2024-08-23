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

import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.item.DefaultTypeParameterItem
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.kotlin.asJava.elements.KotlinLightTypeParameterBuilder
import org.jetbrains.kotlin.asJava.elements.KtLightDeclaration
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtTypeParameter

internal class PsiTypeParameterItem(
    override val codebase: PsiBasedCodebase,
    private val psiClass: PsiTypeParameter,
    name: String,
    modifiers: DefaultModifierList
) :
    DefaultTypeParameterItem(
        codebase = codebase,
        itemLanguage = psiClass.itemLanguage,
        modifiers = modifiers,
        name = name,
        isReified = isReified(psiClass)
    ),
    TypeParameterItem,
    PsiItem {

    override fun psi() = psiClass

    override fun createVariableTypeItem(): VariableTypeItem {
        return codebase.globalTypeItemFactory.getVariableTypeForTypeParameter(this)
    }

    companion object {
        fun create(codebase: PsiBasedCodebase, psiClass: PsiTypeParameter): PsiTypeParameterItem {
            val simpleName = psiClass.name!!
            val modifiers = PsiModifierItem.create(codebase, psiClass)

            return PsiTypeParameterItem(
                codebase = codebase,
                psiClass = psiClass,
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
