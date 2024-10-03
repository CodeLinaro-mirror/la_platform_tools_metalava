/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.type.MethodFingerprint
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.uast.UMethod

internal interface PsiCallableItem : CallableItem, PsiItem {

    override fun psi() = psiMethod

    val psiMethod: PsiMethod

    companion object {
        /**
         * Create a list of [PsiParameterItem]s.
         *
         * The [codebase] and [psiMethod] parameters are added here, rather than retrieving from
         * [containingCallable]'s [PsiCallableItem.codebase] and [PsiCallableItem.psi] properties
         * respectively, because at the time this is called [containingCallable] is in the process
         * of being initialized and those properties have not yet been initialized.
         */
        internal fun parameterList(
            codebase: PsiBasedCodebase,
            psiMethod: PsiMethod,
            containingCallable: PsiCallableItem,
            enclosingTypeItemFactory: PsiTypeItemFactory,
            psiParameters: List<PsiParameter> = psiMethod.psiParameters,
        ): List<PsiParameterItem> {
            val fingerprint = MethodFingerprint(containingCallable.name(), psiParameters.size)
            return psiParameters.mapIndexed { index, parameter ->
                PsiParameterItem.create(
                    codebase,
                    containingCallable,
                    fingerprint,
                    parameter,
                    index,
                    enclosingTypeItemFactory
                )
            }
        }

        internal fun throwsTypes(
            psiMethod: PsiMethod,
            enclosingTypeItemFactory: PsiTypeItemFactory,
        ): List<ExceptionTypeItem> {
            val throwsClassTypes = psiMethod.throwsList.referencedTypes
            if (throwsClassTypes.isEmpty()) {
                return emptyList()
            }

            return throwsClassTypes
                // Convert the PsiType to an ExceptionTypeItem and wrap it in a ThrowableType.
                .map { psiType -> enclosingTypeItemFactory.getExceptionType(PsiTypeInfo(psiType)) }
                // We're sorting the names here even though outputs typically do their own sorting,
                // since for example the MethodItem.sameSignature check wants to do an
                // element-by-element comparison to see if the signature matches, and that should
                // match overrides even if they specify their elements in different orders.
                .sortedWith(ExceptionTypeItem.fullNameComparator)
        }
    }
}

/** Get the [PsiParameter]s for a [PsiMethod]. */
val PsiMethod.psiParameters: List<PsiParameter>
    get() = if (this is UMethod) uastParameters else parameterList.parameters.toList()
