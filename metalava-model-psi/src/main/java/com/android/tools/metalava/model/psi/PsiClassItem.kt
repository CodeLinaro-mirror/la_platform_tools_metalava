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

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.item.DefaultClassItem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledFile
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.uast.UClass

internal class PsiClassItem
internal constructor(
    override val psiCodebase: PsiBasedCodebase,
    val psiClass: PsiClass,
    modifiers: BaseModifierList,
    documentationFactory: ItemDocumentationFactory,
    classKind: ClassKind,
    containingClass: ClassItem?,
    containingPackage: PackageItem,
    qualifiedName: String,
    typeParameterList: TypeParameterList,
    origin: ClassOrigin,
    superClassType: ClassTypeItem?,
    interfaceTypes: List<ClassTypeItem>
) :
    DefaultClassItem(
        codebase = psiCodebase,
        fileLocation = PsiFileLocation.fromPsiElement(psiClass),
        sourceLanguage = psiClass.sourceLanguage,
        targetLanguages = TargetLanguageSet.ALL,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
        source = null,
        classKind = classKind,
        containingClass = containingClass,
        containingPackage = containingPackage,
        qualifiedName = qualifiedName,
        typeParameterList = typeParameterList,
        origin = origin,
        superClassType = superClassType,
        interfaceTypes = interfaceTypes,
        isFileFacade = isFileFacade(psiClass),
        optionalAliasedType = null,
    ),
    ClassItem,
    PsiItem {

    override fun psi() = psiClass

    override var primaryConstructor: ConstructorItem? = null
        internal set

    override fun sourceFile(): SourceFile? {
        if (isNestedClass()) {
            // Retrieve the SourceFile from the top level class.
            return containingClass()?.sourceFile()
        }

        val containingFile = psiClass.containingFile ?: return null
        if (containingFile is PsiCompiledFile) {
            return null
        }

        return psiCodebase.sourceFileCache.psiSourceFile(containingFile)
    }

    /** Creates a constructor in this class */
    override fun createDefaultConstructor(visibility: VisibilityLevel): PsiConstructorItem {
        return PsiConstructorItem.createDefaultConstructor(psiCodebase, this, psiClass, visibility)
    }

    override fun isMultiFileClass(): Boolean {
        return ((psiClass as? UClass)?.javaPsi as? KtLightClassForFacade)?.multiFileClass ?: false
    }

    companion object {
        /** Whether the [psiClass] is a file-facade class. See [ClassItem.isFileFacade]. */
        fun isFileFacade(psiClass: PsiClass): Boolean {
            return psiClass.isKotlin() &&
                psiClass is UClass &&
                psiClass.javaPsi is KtLightClassForFacade
        }
    }
}
