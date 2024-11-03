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

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.item.DefaultPropertyItem
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement

internal class PsiPropertyItem
private constructor(
    override val codebase: PsiBasedCodebase,
    private val psiMethod: PsiMethod,
    modifiers: BaseModifierList,
    // This needs to be passed in because the documentation may come from the property, or it may
    // come from the getter method.
    documentationFactory: ItemDocumentationFactory,
    name: String,
    containingClass: ClassItem,
    type: TypeItem,
    getter: MethodItem,
    setter: MethodItem?,
    constructorParameter: ParameterItem?,
    backingField: FieldItem?
) :
    DefaultPropertyItem(
        codebase = codebase,
        fileLocation = PsiFileLocation(psiMethod),
        itemLanguage = psiMethod.itemLanguage,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
        name = name,
        containingClass = containingClass,
        type = type,
        getter = getter,
        setter = setter,
        constructorParameter = constructorParameter,
        backingField = backingField,
    ),
    PropertyItem,
    PsiItem {

    override fun psi() = psiMethod

    companion object {
        /**
         * Creates a new property item, given a [name], [type] and relationships to other items.
         *
         * Kotlin's properties consist of up to four other declarations: Their accessor functions,
         * primary constructor parameter, and a backing field. These relationships are useful for
         * resolving documentation and exposing the model correctly in Kotlin stubs.
         *
         * Metalava currently requires all properties to have a [getter]. It does not currently
         * support private, `const val`, or [JvmField] properties. Mutable `var` properties usually
         * have a [setter], but properties with a private default setter may use direct field access
         * instead.
         *
         * Properties declared in the primary constructor of a class have an associated
         * [constructorParameter]. This relationship is important for resolving docs which may exist
         * on the constructor parameter.
         *
         * Most properties on classes without a custom getter have a [backingField] to hold their
         * value. This is private except for [JvmField] properties.
         */
        internal fun create(
            codebase: PsiBasedCodebase,
            containingClass: ClassItem,
            name: String,
            type: TypeItem,
            getter: PsiMethodItem,
            setter: PsiMethodItem? = null,
            constructorParameter: PsiParameterItem? = null,
            backingField: PsiFieldItem? = null
        ): PsiPropertyItem {
            val psiMethod = getter.psiMethod
            // Get the appropriate element from which to retrieve the documentation.
            val psiElement =
                when (val sourcePsi = getter.sourcePsi) {
                    is KtPropertyAccessor -> sourcePsi.property
                    else -> sourcePsi ?: psiMethod
                }
            val modifiers = PsiModifierItem.create(codebase, psiMethod)
            // Alas, annotations whose target is property won't be bound to anywhere in LC/UAST,
            // if the property doesn't need a backing field. Same for unspecified use-site target.
            // To preserve such annotations, our last resort is to examine source PSI directly.
            if (backingField == null) {
                val ktProperty = (getter.sourcePsi as? KtPropertyAccessor)?.property
                val annotations =
                    ktProperty?.annotationEntries?.mapNotNull {
                        val useSiteTarget = it.useSiteTarget?.getAnnotationUseSiteTarget()
                        if (
                            useSiteTarget == null ||
                                useSiteTarget == AnnotationUseSiteTarget.PROPERTY
                        ) {
                            it.toUElement() as? UAnnotation
                        } else null
                    }
                annotations?.forEach { uAnnotation ->
                    val annotationItem =
                        UAnnotationItem.create(codebase, uAnnotation) ?: return@forEach
                    if (annotationItem !in modifiers.annotations()) {
                        modifiers.addAnnotation(annotationItem)
                    }
                }
            }
            val property =
                PsiPropertyItem(
                    codebase = codebase,
                    psiMethod = psiMethod,
                    modifiers = modifiers,
                    documentationFactory = PsiItemDocumentation.factory(psiElement, codebase),
                    name = name,
                    containingClass = containingClass,
                    type = type,
                    getter = getter,
                    setter = setter,
                    constructorParameter = constructorParameter,
                    backingField = backingField
                )
            getter.property = property
            setter?.property = property
            constructorParameter?.property = property
            backingField?.property = property
            return property
        }
    }
}
