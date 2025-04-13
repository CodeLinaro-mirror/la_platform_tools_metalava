/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.value.CachingAnnotationValueProvider
import com.android.tools.metalava.model.value.CachingValueProvider
import com.android.tools.metalava.model.value.CombinedValueProvider
import com.android.tools.metalava.model.value.ImplementationValueToModelFactory
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueFactory
import com.android.tools.metalava.model.value.ValueProvider
import com.android.tools.metalava.model.value.ValueProviderException
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiLiteralExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression

/**
 * Creates [ValueProvider]s that will delegate to [implementationValueToModelValue] to create
 * [Value]s when requested.
 */
internal class PsiValueFactory : ValueFactory, ImplementationValueToModelFactory<Any> {
    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] of [typeItem] from
     * [anyValue].
     *
     * @param typeItem the required type for the value, e.g. [MethodItem.returnType] or
     *   [FieldItem.type].
     * @param anyValue the underlying Psi specific value. It is of type [Any] to avoid having to
     *   duplicate everything for [UExpression] and [PsiAnnotationMemberValue].
     */
    fun providerFor(typeItem: TypeItem, anyValue: Any): CombinedValueProvider =
        CachingValueProvider(this, typeItem, anyValue)

    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] for attribute
     * [attributeName] of [annotationItem] from [anyValue].
     *
     * @param annotationItem the containing [AnnotationItem].
     * @param attributeName the name of the attribute whose value it will provide.
     * @param anyValue the underlying Psi specific value. It is of type [Any] to avoid having to
     *   duplicate everything for [UExpression] and [PsiAnnotationMemberValue].
     */
    fun providerForAnnotationValue(
        annotationItem: AnnotationItem,
        attributeName: String,
        anyValue: Any
    ): CombinedValueProvider =
        CachingAnnotationValueProvider(this, annotationItem, attributeName, anyValue)

    /**
     * Create a [Value] of [optionalTypeItem] from [implementationValue].
     *
     * Uses [Any] to avoid having to duplicate everything for [UExpression] and
     * [PsiAnnotationMemberValue].
     */
    override fun implementationValueToModelValue(
        optionalTypeItem: TypeItem?,
        implementationValue: Any,
    ): Value {
        return when (implementationValue) {
            is UExpression -> uExpressionToValue(optionalTypeItem, implementationValue)
            is PsiAnnotationMemberValue -> psiToValue(optionalTypeItem, implementationValue)
            else ->
                throw ValueProviderException(
                    "Unknown value '$implementationValue' of ${implementationValue.javaClass} for type $optionalTypeItem"
                )
        }
    }

    /** Create a [Value] of [optionalTypeItem] from [uExpression]. */
    private fun uExpressionToValue(optionalTypeItem: TypeItem?, uExpression: UExpression): Value {
        if (uExpression is ULiteralExpression) {
            uExpression.value?.let { underlyingValue ->
                return createLiteralValue(optionalTypeItem, underlyingValue)
            }
        }

        // All others expressions are evaluated to a literal, if possible and returned.
        ConstantEvaluator.evaluate(null, uExpression)?.let { value ->
            return createLiteralValue(optionalTypeItem, value)
        }

        // Drop through to throw an exception to document why it failed.
        throw ValueProviderException(
            "Unknown value '$uExpression' of ${uExpression.javaClass} for type $optionalTypeItem"
        )
    }

    /** Create a [Value] of [optionalTypeItem] from [psiValue]. */
    private fun psiToValue(
        optionalTypeItem: TypeItem?,
        psiValue: PsiAnnotationMemberValue,
    ): Value {
        // Literal primitive or String.
        if (psiValue is PsiLiteralExpression) {
            return psiValue.value?.let { underlyingValue ->
                createLiteralValue(optionalTypeItem, underlyingValue)
            }
                ?: error(
                    "Unknown value '$psiValue' of ${psiValue.javaClass} for type $optionalTypeItem"
                )
        }

        // All others expressions are evaluated to a literal, if possible and returned.
        ConstantEvaluator.evaluate(null, psiValue)?.let { value ->
            return createLiteralValue(optionalTypeItem, value)
        }

        // Drop through to throw an exception to document why it failed.
        throw ValueProviderException(
            "Unknown value '$psiValue' of ${psiValue.javaClass} for type $optionalTypeItem"
        )
    }
}
