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

package com.android.tools.metalava.model.value

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TypeItem

/**
 * Interface provided by a model implementation to construct a model [Value] from an implementation
 * value of type [I].
 */
interface ImplementationValueToModelFactory<I> {
    /**
     * Construct a model [Value] instance of [optionalTypeItem] from [implementationValue].
     *
     * If the [implementationValue] cannot be mapped to a [Value], e.g. because it is a field
     * initializer that is not a constant expression, then this must return `null`.
     */
    fun implementationValueToModelValue(optionalTypeItem: TypeItem?, implementationValue: I): Value?
}

/** A [BaseCachingValueProvider] for a model implementation of a non-attribute value. */
class CachingValueProvider<I>(
    private val factory: ImplementationValueToModelFactory<I>,
    private val typeItem: TypeItem,
    private val implementationValue: I,
) : BaseCachingValueProvider() {
    override fun provideValue() =
        factory.implementationValueToModelValue(typeItem, implementationValue)
}

/**
 * A [BaseCachingValueProvider] for a model implementation of an attribute value.
 *
 * When this is called the [TypeItem] of the annotation attribute is not known. So, this
 * encapsulates [annotationItem] and [attributeName] to allow the annotation's [ClassItem] to be
 * resolved and the [MethodItem] called [attributeName] found.
 *
 * If the definition of [AnnotationItem] is not resolvable then it will fail to find the [TypeItem]
 * and use `null` instead.
 */
class CachingAnnotationValueProvider<I>(
    private val factory: ImplementationValueToModelFactory<I>,
    private val annotationItem: AnnotationItem,
    private val attributeName: String,
    private val implementationValue: I,
) : BaseCachingValueProvider() {
    /**
     * Get the [MethodItem.returnType] of the [annotationItem]'s attribute method called
     * [attributeName].
     */
    private fun annotationAttributeType(): TypeItem? {
        val annotationClassItem = annotationItem.resolve() ?: return null
        val attributeMethodItem = annotationClassItem.findMethod(attributeName, "")
        return attributeMethodItem?.returnType()
    }

    override fun provideValue() =
        factory.implementationValueToModelValue(annotationAttributeType(), implementationValue)
}
