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
     * initializer that is not a constant expression, and [valueUseSite] is [ValueUseSite.FIELD]
     * then this must return `null`. Otherwise, this must always return a [Value] or throw an
     * exception.
     */
    fun implementationValueToModelValue(
        optionalTypeItem: TypeItem?,
        implementationValue: I,
        valueUseSite: ValueUseSite,
    ): Value?
}

/** A [BaseCachingValueProvider] for a model implementation of a non-attribute value. */
class CachingValueProvider<I>(
    private val factory: ImplementationValueToModelFactory<I>,
    private val optionalTypeItem: TypeItem?,
    private val implementationValue: I,
    valueUseSite: ValueUseSite,
) : BaseCachingValueProvider(valueUseSite) {
    override fun provideValue() =
        factory.implementationValueToModelValue(
            optionalTypeItem,
            implementationValue,
            valueUseSite,
        )
}

/**
 * A [BaseCachingValueProvider] for a model implementation of a value whose [TypeItem] is not known
 * at construction time.
 *
 * When this is called the [TypeItem] of the [Value] to be created is not known. So, subclasses of
 * this must encapsulate the information necessary to allow the [Value]'s type to be resolved and
 * use that in [optionalTypeItem] to return the [TypeItem], or `null` if it could not be found.
 */
abstract class BaseCachingDeferredTypeValueProvider<I>(
    private val factory: ImplementationValueToModelFactory<I>,
    private val implementationValue: I,
    valueUseSite: ValueUseSite,
) : BaseCachingValueProvider(valueUseSite) {

    /** Get the optional [TypeItem] for the [Value] to be created. */
    protected abstract fun optionalTypeItem(): TypeItem?

    final override fun provideValue() =
        factory.implementationValueToModelValue(
            optionalTypeItem(),
            implementationValue,
            valueUseSite,
        )
}

/**
 * A [BaseCachingDeferredTypeValueProvider] for a model implementation of an attribute value.
 *
 * When this is called the [TypeItem] of the annotation attribute is not known. So, this
 * encapsulates [annotationClassItemProvider] and [attributeName] to allow the annotation's
 * [ClassItem] to be resolved and the [MethodItem] called [attributeName] found.
 *
 * If the definition of [AnnotationItem] is not resolvable then it will fail to find the [TypeItem]
 * and use `null` instead.
 */
class CachingAnnotationValueProvider<I>(
    factory: ImplementationValueToModelFactory<I>,
    private val attributeName: String,
    implementationValue: I,
    private val annotationClassItemProvider: () -> ClassItem?,
) :
    BaseCachingDeferredTypeValueProvider<I>(
        factory,
        implementationValue,
        valueUseSite = ValueUseSite.ANNOTATION
    ) {

    /**
     * Secondary constructor that provides an [annotationClassItemProvider] using [annotationItem]'s
     * [AnnotationItem.resolve] method.
     */
    constructor(
        factory: ImplementationValueToModelFactory<I>,
        annotationItem: AnnotationItem,
        attributeName: String,
        implementationValue: I,
    ) : this(factory, attributeName, implementationValue, annotationItem::resolve)

    /**
     * Get the [MethodItem.returnType] of the [annotationClassItemProvider]'s attribute method
     * called [attributeName].
     */
    override fun optionalTypeItem(): TypeItem? {
        val annotationClassItem = annotationClassItemProvider() ?: return null
        val attributeMethodItem = annotationClassItem.findMethod(attributeName, "")
        return attributeMethodItem?.returnType()
    }
}
