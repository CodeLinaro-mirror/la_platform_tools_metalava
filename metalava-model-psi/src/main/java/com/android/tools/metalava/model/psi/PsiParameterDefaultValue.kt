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

import com.android.tools.metalava.model.ParameterKind
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.contextParameters
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.uast.UMethod

internal object PsiParameterDefaultValue {
    /** Determines whether a [psiParameter] has a default value. */
    @OptIn(KaExperimentalApi::class)
    fun compute(
        psiParameter: PsiParameter,
        parameterIndex: Int,
        kind: ParameterKind,
        psiMethod: PsiMethod
    ): Boolean {
        // Only Kotlin value parameters can have a default value defined
        if (psiParameter.isKotlin() && kind == ParameterKind.VALUE) {
            val containingUMethod = psiMethod as? UMethod ?: return false

            // The compiler-generated data class copy method has all optional parameters.
            if (isDataClassCopyMethod(containingUMethod)) {
                return true
            }

            val ktFunction = (containingUMethod?.sourcePsi as? KtFunction) ?: return false
            analyze(ktFunction) {
                val function =
                    if (ktFunction.hasActualModifier()) {
                        ktFunction.symbol.getExpectsForActual().singleOrNull()
                    } else {
                        ktFunction.symbol
                    }
                if (function !is KaFunctionSymbol) return false
                val symbol = getKtParameterSymbol(function, parameterIndex, psiParameter.name)
                return symbol is KaValueParameterSymbol && symbol.hasDefaultValue
            }
        }

        return false
    }

    /** Returns whether the [uMethod] is the generated copy function of a data class. */
    private fun isDataClassCopyMethod(uMethod: UMethod?): Boolean {
        if (uMethod?.name != "copy") return false
        // The source psi for the generated copy function is the constructor (for a copy method
        // defined in source, the psi would be the source method).
        return when (val sourcePsi = uMethod.sourcePsi) {
            is KtPrimaryConstructor -> sourcePsi.containingClass()?.isData() ?: false
            else -> false
        }
    }

    /**
     * Finds the [KaParameterSymbol] from the [functionSymbol] based on [parameterIndex] and [name].
     * This is only meant to find value parameters. The provided [parameterIndex] should be based on
     * the jvm method signature for the function, where the parameter list has context parameters,
     * the receiver if it exists, value parameters, and the continuation parameter if it exists.
     */
    @OptIn(KaExperimentalApi::class) // for context parameters
    private fun getKtParameterSymbol(
        functionSymbol: KaFunctionSymbol,
        parameterIndex: Int,
        name: String,
    ): KaParameterSymbol? {
        // Look for a value parameter, offset the index based on receiver and context parameters
        val parameters = functionSymbol.valueParameters
        val receiverParameterCount = if (functionSymbol.isExtension) 1 else 0
        val index = parameterIndex - receiverParameterCount - functionSymbol.contextParameters.size

        // Quick lookup first which usually works
        if (index >= 0) {
            val parameter = parameters[index]
            if (parameter.name.asString() == name) {
                return parameter
            }
        }

        for (parameter in parameters) {
            if (parameter.name.asString() == name) {
                return parameter
            }
        }

        // Fallback to handle scenario where the real parameter names are hidden by
        // UAST (see UastKotlinPsiParameter which replaces parameter names to p$index)
        if (index >= 0) {
            return parameters[index]
        }

        return null
    }
}
