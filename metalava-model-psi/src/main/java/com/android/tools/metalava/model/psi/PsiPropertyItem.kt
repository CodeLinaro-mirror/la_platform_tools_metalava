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
import org.jetbrains.kotlin.psi.KtDeclaration

internal class PsiPropertyItem
private constructor(
    override val codebase: PsiBasedCodebase,
    private val ktDeclaration: KtDeclaration,
    modifiers: BaseModifierList,
    // This needs to be passed in because the documentation may come from the property, or it may
    // come from the getter method.
    documentationFactory: ItemDocumentationFactory,
    name: String,
    containingClass: ClassItem,
    type: TypeItem,
    getter: MethodItem?,
    setter: MethodItem?,
    constructorParameter: ParameterItem?,
    backingField: FieldItem?
) :
    DefaultPropertyItem(
        codebase = codebase,
        fileLocation = PsiFileLocation(ktDeclaration),
        itemLanguage = ktDeclaration.itemLanguage,
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

    override fun psi() = ktDeclaration

    companion object {
        /**
         * Creates a new property item for the [ktDeclaration], given a [name] and relationships to
         * other items.
         *
         * Kotlin's properties consist of up to four other declarations: Their accessor functions,
         * primary constructor parameter, and a backing field. These relationships are useful for
         * resolving documentation and exposing the model correctly in Kotlin stubs.
         *
         * Most properties have a [getter], but those that are available through field access in
         * Java (e.g. `const val` and [JvmField] properties) or are not accessible from Java (e.g.
         * private properties and non-constructor value class properties) do not.
         *
         * Mutable `var` properties usually have a [setter], but properties with a private default
         * setter may use direct field access instead.
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
            ktDeclaration: KtDeclaration,
            containingClass: ClassItem,
            typeItemFactory: PsiTypeItemFactory,
            name: String,
            getter: PsiMethodItem?,
            setter: PsiMethodItem? = null,
            constructorParameter: PsiParameterItem? = null,
            backingField: PsiFieldItem? = null,
        ): PsiPropertyItem? {
            val type =
                getter?.returnType()
                    ?: typeItemFactory.getTypeForKtElement(ktDeclaration) ?: return null
            val modifiers =
                PsiModifierItem.createForProperty(codebase, ktDeclaration, getter, setter)
            if (modifiers.isFinal() && containingClass.modifiers.isFinal()) {
                // The containing class is final, so it is implied that every property is final as
                // well. No need to apply 'final' to each property. (This is done for methods too.)
                modifiers.setFinal(false)
            }

            val property =
                PsiPropertyItem(
                    codebase = codebase,
                    ktDeclaration = ktDeclaration,
                    modifiers = modifiers,
                    documentationFactory = PsiItemDocumentation.factory(ktDeclaration, codebase),
                    name = name,
                    containingClass = containingClass,
                    type = type,
                    getter = getter,
                    setter = setter,
                    constructorParameter = constructorParameter,
                    backingField = backingField
                )
            getter?.property = property
            setter?.property = property
            constructorParameter?.property = property
            backingField?.property = property
            return property
        }
    }
}
