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

import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getUastParentOfType

internal object PsiParameterDefaultValue {
    /** Determines whether a [psiParameter] has a default value. */
    @OptIn(KaExperimentalApi::class)
    fun compute(psiParameter: PsiParameter, parameterIndex: Int): Boolean {
        if (psiParameter.isKotlin()) {
            val containingUMethod = psiParameter.getUastParentOfType<UMethod>()

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
        // The source psi for the generated copy function is the constructor for K2, the class for
        // K1 (for a copy method defined in source, the psi would be the source method).
        return when (val sourcePsi = uMethod.sourcePsi) {
            is KtClass -> sourcePsi.isData()
            is KtPrimaryConstructor -> sourcePsi.containingClass()?.isData() ?: false
            else -> false
        }
    }

    private fun getKtParameterSymbol(
        functionSymbol: KaFunctionSymbol,
        parameterIndex: Int,
        name: String,
    ): KaParameterSymbol? {
        // If a function is an extension, the first parameter is the receiver.
        if (parameterIndex == 0 && functionSymbol.isExtension) {
            return functionSymbol.receiverParameter
        }

        // Perform matching based on parameter names, because indices won't work in the
        // presence of @JvmOverloads where UAST generates multiple permutations of the
        // method from the same KtParameters array.
        val parameters = functionSymbol.valueParameters

        val index = if (functionSymbol.isExtension) parameterIndex - 1 else parameterIndex
        val isSuspend = (functionSymbol as? KaNamedFunctionSymbol)?.isSuspend == true
        if (isSuspend && index >= parameters.size) {
            // suspend functions have continuation as a last parameter, which is not
            // defined in the symbol
            return null
        }

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
