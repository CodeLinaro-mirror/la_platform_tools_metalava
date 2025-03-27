/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.item.DefaultPackageItem
import com.android.tools.metalava.model.item.DefaultTypeAliasItem
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtTypeAlias

internal class PsiTypeAliasItem
private constructor(
    override val codebase: PsiBasedCodebase,
    private val ktTypeAlias: KtTypeAlias,
    fileLocation: FileLocation,
    modifiers: BaseModifierList,
    aliasedType: TypeItem,
    qualifiedName: String,
    typeParameterList: TypeParameterList,
    containingPackage: DefaultPackageItem,
) :
    DefaultTypeAliasItem(
        codebase,
        fileLocation,
        modifiers,
        PsiItemDocumentation.factory(ktTypeAlias, codebase),
        ApiVariantSelectors.MUTABLE_FACTORY,
        aliasedType,
        qualifiedName,
        typeParameterList,
        containingPackage,
    ),
    PsiItem {

    override fun psi(): PsiElement {
        return ktTypeAlias
    }

    companion object {
        /**
         * Tries to create a [PsiTypeAliasItem] from the [ktTypeAlias]. May return null if the
         * qualified name or type of the type alias can't be resolved.
         */
        fun create(ktTypeAlias: KtTypeAlias, codebase: PsiBasedCodebase): PsiTypeAliasItem? {
            val qualifiedName = ktTypeAlias.getClassId()?.asFqNameString() ?: return null

            val (typeParameterList, typeItemFactory) =
                PsiTypeParameterList.create(
                    codebase,
                    codebase.globalTypeItemFactory,
                    "typealias $qualifiedName",
                    ktTypeAlias
                )
            val aliasedType = typeItemFactory.getTypeForKtElement(ktTypeAlias) ?: return null

            val packageName = qualifiedName.substringBeforeLast(".")
            val containingPackage = codebase.findOrCreatePackage(packageName)

            val modifiers = PsiModifierItem.createForKtDeclaration(codebase, ktTypeAlias)

            return PsiTypeAliasItem(
                codebase = codebase,
                ktTypeAlias = ktTypeAlias,
                fileLocation = PsiFileLocation.fromPsiElement(ktTypeAlias),
                modifiers = modifiers,
                aliasedType = aliasedType,
                qualifiedName = qualifiedName,
                typeParameterList = typeParameterList,
                containingPackage = containingPackage,
            )
        }
    }
}
