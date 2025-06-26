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

package com.android.tools.metalava.model.psi.kotlin

import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.model.value.CachingValueProvider
import com.android.tools.metalava.model.value.CombinedValueProvider
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.ImplementationValueToModelFactory
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueFactory
import com.android.tools.metalava.model.value.ValueUseSite
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.toUElement

/** Creates [Value]s from [KaAnnotationValue]s and optionally psi [ValueArgument]s. */
internal class KaValueFactory(
    private val codebase: PsiBasedCodebase,
    private val assembler: KaCodebaseAssembler,
    private val globalTypeItemFactory: KaTypeItemFactory,
) : ValueFactory, ImplementationValueToModelFactory<Pair<KaAnnotationValue, ValueArgument?>> {
    /**
     * Creates a lazy caching value provider for the annotation value represented by
     * [kaAnnotationValue] and optionally [psiValue].
     */
    fun providerForAnnotationValue(
        kaAnnotationValue: KaAnnotationValue,
        psiValue: ValueArgument?,
    ): CombinedValueProvider =
        CachingValueProvider(this, null, kaAnnotationValue to psiValue, ValueUseSite.ANNOTATION)

    override fun implementationValueToModelValue(
        optionalTypeItem: TypeItem?,
        implementationValue: Pair<KaAnnotationValue, ValueArgument?>,
        valueUseSite: ValueUseSite
    ): Value? {
        val (kaAnnotationValue, psiArgument) = implementationValue
        return when (kaAnnotationValue) {
            is KaAnnotationValue.ArrayValue ->
                createArrayValue(
                    kaAnnotationValue.values.mapNotNull {
                        implementationValueToModelValue(
                            optionalTypeItem = null,
                            implementationValue = it to null,
                            valueUseSite = ValueUseSite.ANNOTATION
                        )
                            as? ArrayElementValue
                    }
                )
            is KaAnnotationValue.ClassLiteralValue ->
                createClassObjectValue(
                    globalTypeItemFactory.getClassReferenceType(kaAnnotationValue.type),
                    // The sourceExpression is only used for legacy value formatting, so it isn't
                    // important to include it here.
                    sourceExpression = null,
                )
            is KaAnnotationValue.ConstantValue -> {
                // A constant value might be created through a field reference. Check if the psi
                // value is a field reference with the codebase's PsiValueFactory.
                (psiArgument?.getArgumentExpression()?.toUElement() as? UReferenceExpression)
                    ?.let { uExpr ->
                        (codebase.valueFactory.implementationValueToModelValue(
                            optionalTypeItem,
                            uExpr,
                            valueUseSite
                        ) as? FieldReferenceValue)
                    }
                    // If it isn't a field reference, use the evaluated constant value.
                    ?: kaAnnotationValue.value.value?.let {
                        createLiteralValue(optionalTypeItem, it)
                    }
            }
            is KaAnnotationValue.EnumEntryValue ->
                kaAnnotationValue.callableId?.let { callableId ->
                    callableId.classId?.let { classId ->
                        createFieldReferenceValue(
                            codebase,
                            classId.asFqNameString(),
                            callableId.callableName.identifier,
                        )
                    }
                }
            is KaAnnotationValue.NestedAnnotationValue ->
                assembler.createAnnotation(kaAnnotationValue.annotation)?.let {
                    createAnnotationValue(it)
                }
            is KaAnnotationValue.UnsupportedValue -> error("Unsupported value $implementationValue")
        }
    }
}
