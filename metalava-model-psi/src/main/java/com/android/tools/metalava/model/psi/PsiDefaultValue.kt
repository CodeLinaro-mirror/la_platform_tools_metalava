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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.item.DefaultValue
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastFacade

internal class PsiDefaultValue(private val item: PsiParameterItem) : DefaultValue {

    override fun duplicate(parameter: ParameterItem) =
        PsiDefaultValue(parameter as PsiParameterItem)

    private var defaultValueAsString: String? = null

    private fun defaultValueAsString(): String? {
        if (defaultValueAsString == null) {
            defaultValueAsString = item.computeDefaultValue()
        }
        return defaultValueAsString
    }

    override fun hasDefaultValue() = isDefaultValueKnown()

    override fun isDefaultValueKnown(): Boolean {
        val psiParameter = item.psiParameter
        return if (psiParameter.isKotlin()) {
            defaultValueAsString() != INVALID_VALUE
        } else {
            // Java: Look for @ParameterName annotation
            item.modifiers.hasAnnotation(AnnotationItem::isDefaultValue)
        }
    }

    override fun value() = defaultValueAsString()

    @OptIn(KaExperimentalApi::class)
    private fun PsiParameterItem.computeDefaultValue(): String? {
        if (psiParameter.isKotlin()) {
            val psiCallableItem = item.containingCallable() as PsiCallableItem
            val ktFunction =
                ((psiCallableItem.psi() as? UMethod)?.sourcePsi as? KtFunction)
                    ?: return INVALID_VALUE

            analyze(ktFunction) {
                val function =
                    if (ktFunction.hasActualModifier()) {
                        ktFunction.symbol.getExpectsForActual().singleOrNull()
                    } else {
                        ktFunction.symbol
                    }
                if (function !is KaFunctionSymbol) return INVALID_VALUE
                val symbol = getKtParameterSymbol(function) ?: return INVALID_VALUE
                if (symbol is KaValueParameterSymbol && symbol.hasDefaultValue) {
                    val defaultValue =
                        (symbol.psi as? KtParameter)?.defaultValue ?: return INVALID_VALUE
                    if (defaultValue is KtConstantExpression) {
                        return defaultValue.text
                    }

                    val defaultExpression =
                        UastFacade.convertElement(defaultValue, null, UExpression::class.java)
                            as? UExpression
                            ?: return INVALID_VALUE
                    val constant = defaultExpression.evaluate()
                    return if (constant != null && constant !is Pair<*, *>) {
                        CodePrinter.constantToSource(constant)
                    } else {
                        // Expression: Compute from UAST rather than just using the source text
                        // such that we can ensure references are fully qualified etc.
                        codebase.printer.toSourceString(defaultExpression)
                    }
                }
            }

            return INVALID_VALUE
        } else {
            // Java: Look for @ParameterName annotation
            val annotation = modifiers.findAnnotation(AnnotationItem::isDefaultValue)
            if (annotation != null) {
                return annotation.attributes.firstOrNull()?.value?.value()?.toString()
            }
        }

        return null
    }

    private fun PsiParameterItem.getKtParameterSymbol(
        functionSymbol: KaFunctionSymbol
    ): KaParameterSymbol? {
        if (isReceiver()) {
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
            if (parameter.name.asString() == name()) {
                return parameter
            }
        }

        for (parameter in parameters) {
            if (parameter.name.asString() == name()) {
                return parameter
            }
        }

        // Fallback to handle scenario where the real parameter names are hidden by
        // UAST (see UastKotlinPsiParameter which replaces parameter names to p$index)
        if (index >= 0) {
            val parameter = parameters[index]
            if (!isReceiver()) {
                return parameter
            }
        }

        return null
    }

    companion object {

        /**
         * Private marker return value from [#computeDefaultValue] signifying that the parameter has
         * a default value but we were unable to compute a suitable static string representation for
         * it
         */
        const val INVALID_VALUE = "__invalid_value__"
    }
}
