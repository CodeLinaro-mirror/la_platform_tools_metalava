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
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.item.DefaultPropertyItem
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner

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
    backingField: FieldItem?,
    receiver: TypeItem?,
    typeParameterList: TypeParameterList,
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
        receiver = receiver,
        typeParameterList = typeParameterList,
    ),
    PropertyItem,
    PsiItem {

    override fun psi() = ktDeclaration

    companion object {
        /**
         * Creates a new property item for the [ktDeclaration], given relationships to other items.
         *
         * Kotlin's properties consist of up to four other declarations: Their accessor functions,
         * primary constructor parameter, and a backing field. These relationships are useful for
         * resolving documentation and exposing the model correctly in Kotlin stubs.
         *
         * Most properties have a getter, but those that are available through field access in Java
         * (e.g. `const val` and [JvmField] properties) or are not accessible from Java (e.g.
         * private properties and non-constructor value class properties) do not.
         *
         * Mutable `var` properties usually have a setter, but properties with a private default
         * setter may use direct field access instead.
         *
         * The [accessors] should contain the getter and setter, if they exist. It may also contain
         * other accessors, like data class component methods.
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
            containingTypeItemFactory: PsiTypeItemFactory,
            accessors: List<PsiMethodItem>,
            constructorParameter: PsiParameterItem? = null,
            backingField: PsiFieldItem? = null,
        ): PsiPropertyItem? {
            val name = ktDeclaration.name ?: return null

            val (typeParameterList, typeItemFactory) =
                PsiTypeParameterList.create(
                    codebase,
                    containingTypeItemFactory,
                    "property $name",
                    ktDeclaration as? KtTypeParameterListOwner
                )

            // Compute the type of the receiver, if there is one. This will be used to find the
            // right accessors for the property.
            val receiverType =
                (ktDeclaration as? KtProperty)?.receiverTypeReference?.let {
                    typeItemFactory.getTypeForKtElement(it)
                }

            // Determine which accessors are the getter and setter.
            val getter = findGetter(accessors, receiverType)
            val setter = findSetter(accessors, receiverType)

            val type =
                getter?.returnType()
                    ?: typeItemFactory.getTypeForKtElement(ktDeclaration)
                    ?: return null
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
                    backingField = backingField,
                    receiver = receiverType,
                    typeParameterList = typeParameterList,
                )
            getter?.property = property
            setter?.property = property
            constructorParameter?.property = property
            backingField?.property = property
            return property
        }

        /**
         * Given [allAccessors] for a property ([PsiMethodItem]s] for which the source element is
         * the same [KtProperty]/[KtParameter]), finds the getter.
         */
        private fun findGetter(
            allAccessors: List<PsiMethodItem>,
            propertyReceiverType: PsiTypeItem?
        ): PsiMethodItem? {
            return if (propertyReceiverType == null) {
                // No receiver, so the getter has no parameter. Make sure not to find a data class
                // component method.
                allAccessors.singleOrNull {
                    it.parameters().isEmpty() && !it.name().startsWith("component")
                }
            } else {
                // If there's a receiver, the getter should have the receiver type as its parameter.
                allAccessors.singleOrNull {
                    it.parameters().singleOrNull()?.type() == propertyReceiverType
                }
                    // Work around a psi bug where value class extension property accessors don't
                    // include the receiver (b/385148821). This strategy does not always work, which
                    // is why the one above is used in most cases: the getter for a property
                    // parameter's source element will be a KtParameter, and the getter for a simple
                    // property declaration with no custom getter declaration will be a KtProperty,
                    // not a KtPropertyAccessor.
                    ?: allAccessors.singleOrNull {
                        (it.psiMethod.sourceElement as? KtPropertyAccessor)?.isGetter == true
                    }
            }
        }

        /** Like [findGetter], but finds the property setter instead. */
        private fun findSetter(
            allAccessors: List<PsiMethodItem>,
            propertyReceiverType: PsiTypeItem?
        ): PsiMethodItem? {
            return if (propertyReceiverType == null) {
                // No receiver, the setter has one parameter.
                allAccessors.singleOrNull { it.parameters().size == 1 }
            } else {
                // The setter has a receiver parameter in addition to the normal setter parameter.
                allAccessors.singleOrNull {
                    it.parameters().size == 2 && it.parameters()[0].type() == propertyReceiverType
                }
                    // Work around a psi bug, see the equivalent [findGetter] case for details.
                    ?: allAccessors.singleOrNull {
                        (it.psiMethod.sourceElement as? KtPropertyAccessor)?.isSetter == true
                    }
            }
        }
    }
}
