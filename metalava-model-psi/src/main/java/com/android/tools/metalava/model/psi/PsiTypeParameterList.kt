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

import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeParameterListAndFactory
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypeParameterListOwner
import org.jetbrains.kotlin.asJava.toPsiTypeParameters
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner

internal object PsiTypeParameterList {

    fun create(
        codebase: PsiBasedCodebase,
        enclosingTypeItemFactory: PsiTypeItemFactory,
        scopeDescription: String,
        psiOwner: PsiTypeParameterListOwner
    ): TypeParameterListAndFactory<PsiTypeItemFactory> {
        return create(
            codebase,
            enclosingTypeItemFactory,
            scopeDescription,
            psiOwner.typeParameterList?.typeParameters?.asList()
        )
    }

    /**
     * Generates a [PsiTypeParameterList] from the type parameters of the [ktOwner]. Converts each
     * [KtTypeParameter] to a [PsiTypeParameter] using [toPsiTypeParameters], which gets a psi
     * version of the type parameter for each psi element that uses the type parameter.
     *
     * For properties, this is the getter and optionally setter. Properties only can have type
     * parameters if they have a receiver, and properties without receivers can't have backing
     * fields, so they must have getters.
     */
    fun create(
        codebase: PsiBasedCodebase,
        enclosingTypeItemFactory: PsiTypeItemFactory,
        scopeDescription: String,
        ktOwner: KtTypeParameterListOwner?
    ): TypeParameterListAndFactory<PsiTypeItemFactory> {
        return create(
            codebase,
            enclosingTypeItemFactory,
            scopeDescription,
            ktOwner?.typeParameters?.mapNotNull { it.toPsiTypeParameters().singleOrNull() }
        )
    }

    private fun create(
        codebase: PsiBasedCodebase,
        enclosingTypeItemFactory: PsiTypeItemFactory,
        scopeDescription: String,
        psiTypeParameters: List<PsiTypeParameter>?
    ): TypeParameterListAndFactory<PsiTypeItemFactory> {
        if (psiTypeParameters.isNullOrEmpty()) {
            return TypeParameterListAndFactory(TypeParameterList.NONE, enclosingTypeItemFactory)
        }

        return DefaultTypeParameterList.createTypeParameterItemsAndFactory(
            enclosingTypeItemFactory,
            scopeDescription,
            psiTypeParameters,
            { PsiTypeParameterItem.create(codebase, it) },
            // Create bounds and store it in the [PsiTypeParameterItem.bounds] property.
            { typeItemFactory, psiTypeParameter ->
                val refs = psiTypeParameter.extendsList.referencedTypes
                if (refs.isEmpty()) {
                    emptyList()
                } else {
                    refs.mapNotNull { typeItemFactory.getBoundsType(PsiTypeInfo(it)) }
                }
            },
        )
    }
}
