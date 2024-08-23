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

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.DefaultModifierList.Companion.PACKAGE_PRIVATE
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.uast.UMethod

internal class PsiConstructorItem
private constructor(
    codebase: PsiBasedCodebase,
    psiMethod: PsiMethod,
    fileLocation: FileLocation = PsiFileLocation(psiMethod),
    containingClass: PsiClassItem,
    name: String,
    modifiers: DefaultModifierList,
    documentationFactory: ItemDocumentationFactory,
    parameterItemsFactory: ParameterItemsFactory,
    returnType: ClassTypeItem,
    typeParameterList: TypeParameterList,
    throwsTypes: List<ExceptionTypeItem>,
    val implicitConstructor: Boolean = false,
    override val isPrimary: Boolean = false
) :
    PsiCallableItem(
        codebase = codebase,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        psiMethod = psiMethod,
        fileLocation = fileLocation,
        containingClass = containingClass,
        name = name,
        returnType = returnType,
        parameterItemsFactory = parameterItemsFactory,
        typeParameterList = typeParameterList,
        throwsTypes = throwsTypes,
    ),
    ConstructorItem {

    override fun isImplicitConstructor(): Boolean = implicitConstructor

    override var superConstructor: ConstructorItem? = null

    /** Override to specialize the return type. */
    override fun returnType() = super.returnType() as ClassTypeItem

    /** Override to make sure that [type] is a [ClassTypeItem]. */
    override fun setType(type: TypeItem) {
        super.setType(type as ClassTypeItem)
    }

    companion object {
        internal fun create(
            codebase: PsiBasedCodebase,
            containingClass: PsiClassItem,
            psiMethod: PsiMethod,
            enclosingClassTypeItemFactory: PsiTypeItemFactory,
        ): PsiConstructorItem {
            assert(psiMethod.isConstructor)
            val name = psiMethod.name
            val modifiers = PsiModifierItem.create(codebase, psiMethod)
            // Create the TypeParameterList for this before wrapping any of the other types used by
            // it as they may reference a type parameter in the list.
            val (typeParameterList, constructorTypeItemFactory) =
                PsiTypeParameterList.create(
                    codebase,
                    enclosingClassTypeItemFactory,
                    "constructor $name",
                    psiMethod
                )
            val constructor =
                PsiConstructorItem(
                    codebase = codebase,
                    psiMethod = psiMethod,
                    containingClass = containingClass,
                    name = name,
                    documentationFactory = PsiItemDocumentation.factory(psiMethod, codebase),
                    modifiers = modifiers,
                    parameterItemsFactory = { containingCallable ->
                        parameterList(containingCallable, constructorTypeItemFactory)
                    },
                    returnType = containingClass.type(),
                    implicitConstructor = false,
                    isPrimary = (psiMethod as? UMethod)?.isPrimaryConstructor ?: false,
                    typeParameterList = typeParameterList,
                    throwsTypes = throwsTypes(psiMethod, constructorTypeItemFactory),
                )
            return constructor
        }

        fun createDefaultConstructor(
            codebase: PsiBasedCodebase,
            containingClass: PsiClassItem,
            psiClass: PsiClass,
        ): PsiConstructorItem {
            val name = psiClass.name!!

            val factory = JavaPsiFacade.getInstance(psiClass.project).elementFactory
            val psiMethod = factory.createConstructor(name, psiClass)
            val modifiers = DefaultModifierList(codebase, PACKAGE_PRIVATE, null)
            modifiers.setVisibilityLevel(containingClass.modifiers.getVisibilityLevel())

            val item =
                PsiConstructorItem(
                    codebase = codebase,
                    psiMethod = psiMethod,
                    // Use the location of the containing class for the implicit default
                    // constructor.
                    fileLocation = containingClass.fileLocation,
                    containingClass = containingClass,
                    name = name,
                    documentationFactory = ItemDocumentation.NONE_FACTORY,
                    modifiers = modifiers,
                    parameterItemsFactory = { emptyList() },
                    returnType = containingClass.type(),
                    implicitConstructor = true,
                    typeParameterList = TypeParameterList.NONE,
                    throwsTypes = emptyList(),
                )
            return item
        }

        private val UMethod.isPrimaryConstructor: Boolean
            get() = sourcePsi is KtPrimaryConstructor || sourcePsi is KtClassOrObject
    }
}
