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

package com.android.tools.metalava.model.turbine

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
import com.android.tools.metalava.model.value.ValueProviderException
import com.google.turbine.model.Const

internal class TurbineValueFactory : ValueFactory, ImplementationValueToModelFactory<TurbineValue> {
    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] of [typeItem] from
     * [turbineValue].
     *
     * @param typeItem the required type for the value, e.g. [MethodItem.returnType] or
     *   [FieldItem.type].
     * @param turbineValue the underlying Turbine value.
     */
    fun providerFor(typeItem: TypeItem, turbineValue: TurbineValue): CombinedValueProvider =
        CachingValueProvider(this, typeItem, turbineValue)

    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] for attribute
     * [attributeName] of [annotationItem] from [anyValue].
     *
     * @param annotationItem the containing [AnnotationItem].
     * @param attributeName the name of the attribute whose value it will provide.
     * @param turbineValue the underlying Turbine value.
     */
    fun providerForAnnotationValue(
        annotationItem: AnnotationItem,
        attributeName: String,
        turbineValue: TurbineValue
    ): CombinedValueProvider =
        CachingAnnotationValueProvider(
            this,
            annotationItem,
            attributeName,
            turbineValue,
        )

    override fun implementationValueToModelValue(
        optionalTypeItem: TypeItem?,
        implementationValue: TurbineValue
    ) = implementationValue.toValue(optionalTypeItem)

    private fun TurbineValue.toValue(optionalTypeItem: TypeItem?): Value {
        if (const.kind() == Const.Kind.PRIMITIVE) {
            val underlyingValue = (const as Const.Value).value
            return createLiteralValue(optionalTypeItem, underlyingValue)
        }

        throw ValueProviderException(
            "Unknown value '$const' of ${const.javaClass} for type $optionalTypeItem"
        )
    }
}
