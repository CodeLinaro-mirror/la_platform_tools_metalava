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
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.item.DefaultMethodItem
import com.android.tools.metalava.model.item.ParameterItemsFactory
import com.android.tools.metalava.model.psi.PsiCallableItem.Companion.parameterList
import com.android.tools.metalava.model.psi.PsiCallableItem.Companion.throwsTypes
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.PsiAnnotationMethod
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UAnnotationMethod
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.kotlin.KotlinUMethodWithFakeLightDelegateBase
import org.jetbrains.uast.toUElement

internal class PsiMethodItem(
    override val codebase: PsiBasedCodebase,
    override val psiMethod: PsiMethod,
    fileLocation: FileLocation = PsiFileLocation(psiMethod),
    // Takes ClassItem as this may be duplicated from a PsiBasedCodebase on the classpath into a
    // TextClassItem.
    containingClass: ClassItem,
    name: String,
    modifiers: DefaultModifierList,
    documentationFactory: ItemDocumentationFactory,
    returnType: TypeItem,
    parameterItemsFactory: ParameterItemsFactory,
    typeParameterList: TypeParameterList,
    throwsTypes: List<ExceptionTypeItem>,
) :
    DefaultMethodItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = psiMethod.itemLanguage,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
        name = name,
        containingClass = containingClass,
        typeParameterList = typeParameterList,
        returnType = returnType,
        parameterItemsFactory = parameterItemsFactory,
        throwsTypes = throwsTypes,
        callableBodyFactory = { PsiCallableBody(it as PsiCallableItem) },
    ),
    PsiCallableItem {

    override var property: PsiPropertyItem? = null

    override fun isExtensionMethod(): Boolean {
        if (isKotlin()) {
            val ktParameters =
                ((psiMethod as? UMethod)?.sourcePsi as? KtNamedFunction)?.valueParameters
                    ?: return false
            return ktParameters.size < parameters().size
        }

        return false
    }

    override fun isKotlinProperty(): Boolean {
        return psiMethod is UMethod &&
            (psiMethod.sourcePsi is KtProperty ||
                psiMethod.sourcePsi is KtPropertyAccessor ||
                psiMethod.sourcePsi is KtParameter &&
                    (psiMethod.sourcePsi as KtParameter).hasValOrVar())
    }

    override fun defaultValue(): String {
        return when (psiMethod) {
            is UAnnotationMethod -> {
                psiMethod.uastDefaultValue?.let { codebase.printer.toSourceString(it) } ?: ""
            }
            is PsiAnnotationMethod -> {
                psiMethod.defaultValue?.let { codebase.printer.toSourceExpression(it, this) }
                    ?: super.defaultValue()
            }
            else -> super.defaultValue()
        }
    }

    override fun duplicate(targetContainingClass: ClassItem): PsiMethodItem {
        // If duplicating within the same codebase type then map the type variables, otherwise do
        // not. That is because this can end up substituting a `TypeItem` implementation of one
        // type in place of a `PsiTypeItem` which can cause casting issues, e.g. in
        // `PsiParameterItem` which expects its type as `PsiTypeItem`. Falling back to not mapping
        // will not cause any significant issues as that is what was done before.
        // TODO(b/324196754): Fix this. It is not clear if this causes problems outside tests, it
        //  does not seem to break Android build.
        val typeVariableMap =
            if (codebase.javaClass === targetContainingClass.codebase.javaClass)
                targetContainingClass.mapTypeVariables(containingClass())
            else emptyMap()

        return PsiMethodItem(
                codebase,
                psiMethod,
                fileLocation,
                targetContainingClass,
                name(),
                modifiers.duplicate(),
                documentation::duplicate,
                returnType.convertType(typeVariableMap),
                { methodItem ->
                    parameters().map {
                        (it as PsiParameterItem).duplicate(methodItem, typeVariableMap)
                    }
                },
                typeParameterList,
                throwsTypes(),
            )
            .also { duplicated ->
                duplicated.inheritedFrom = containingClass()

                duplicated.updateCopiedMethodState()
            }
    }

    /* Call corresponding PSI utility method -- if I can find it!
    override fun matches(other: MethodItem): Boolean {
        if (other !is PsiMethodItem) {
            return super.matches(other)
        }

        // TODO: Find better API: this also checks surrounding class which we don't want!
        return psiMethod.isEquivalentTo(other.psiMethod)
    }
    */

    companion object {
        /**
         * Create a [PsiMethodItem].
         *
         * The [containingClass] is not necessarily a [PsiClassItem] as this is used to implement
         * [MethodItem.duplicate] as well as create [PsiMethodItem] from the underlying Psi source
         * model.
         */
        internal fun create(
            codebase: PsiBasedCodebase,
            containingClass: ClassItem,
            psiMethod: PsiMethod,
            enclosingClassTypeItemFactory: PsiTypeItemFactory,
        ): PsiMethodItem {
            assert(!psiMethod.isConstructor)
            // UAST workaround: @JvmName for UMethod with fake LC PSI
            // TODO: https://youtrack.jetbrains.com/issue/KTIJ-25133
            val name =
                if (psiMethod is KotlinUMethodWithFakeLightDelegateBase<*>) {
                    psiMethod.sourcePsi
                        ?.annotationEntries
                        ?.find { annoEntry ->
                            val text = annoEntry.typeReference?.text ?: return@find false
                            JvmName::class.qualifiedName?.contains(text) == true
                        }
                        ?.toUElement(UAnnotation::class.java)
                        ?.takeIf {
                            // Above `find` deliberately avoids resolution and uses verbatim text.
                            // Below, we need annotation value anyway, but just double-check
                            // if the converted annotation is indeed the resolved @JvmName
                            it.qualifiedName == JvmName::class.qualifiedName
                        }
                        ?.findAttributeValue("name")
                        ?.evaluate() as? String
                        ?: psiMethod.name
                } else {
                    psiMethod.name
                }
            val modifiers = PsiModifierItem.create(codebase, psiMethod)

            if (containingClass.classKind == ClassKind.INTERFACE) {
                // All interface methods are implicitly public (except in Java 1.9, where they can
                // be private.
                if (!modifiers.isPrivate()) {
                    modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
                }
            }

            // Create the TypeParameterList for this before wrapping any of the other types used by
            // it as they may reference a type parameter in the list.
            val (typeParameterList, methodTypeItemFactory) =
                PsiTypeParameterList.create(
                    codebase,
                    enclosingClassTypeItemFactory,
                    "method $name",
                    psiMethod
                )
            val fingerprint = MethodFingerprint(psiMethod.name, psiMethod.parameters.size)
            val isAnnotationElement = containingClass.isAnnotationType() && !modifiers.isStatic()
            val returnType =
                methodTypeItemFactory.getMethodReturnType(
                    underlyingReturnType = PsiTypeInfo(psiMethod.returnType!!, psiMethod),
                    itemAnnotations = modifiers.annotations(),
                    fingerprint = fingerprint,
                    isAnnotationElement = isAnnotationElement,
                )

            val method =
                PsiMethodItem(
                    codebase = codebase,
                    psiMethod = psiMethod,
                    containingClass = containingClass,
                    name = name,
                    documentationFactory = PsiItemDocumentation.factory(psiMethod, codebase),
                    modifiers = modifiers,
                    returnType = returnType,
                    parameterItemsFactory = { containingCallable ->
                        parameterList(
                            codebase,
                            psiMethod,
                            containingCallable as PsiCallableItem,
                            methodTypeItemFactory,
                        )
                    },
                    typeParameterList = typeParameterList,
                    throwsTypes = throwsTypes(psiMethod, methodTypeItemFactory),
                )
            if (modifiers.isFinal() && containingClass.modifiers.isFinal()) {
                // The containing class is final, so it is implied that every method is final as
                // well.
                // No need to apply 'final' to each method. (We do it here rather than just in the
                // signature emit code since we want to make sure that the signature comparison
                // methods with super methods also consider this method non-final.)
                modifiers.setFinal(false)
            }

            return method
        }
    }
}
