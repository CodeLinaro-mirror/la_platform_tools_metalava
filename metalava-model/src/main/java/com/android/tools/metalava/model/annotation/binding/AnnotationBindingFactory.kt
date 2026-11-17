/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.annotation.binding

import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.value.AnnotationValue
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.model.value.ArrayValue
import com.android.tools.metalava.model.value.BooleanValue
import com.android.tools.metalava.model.value.ClassObjectValue
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.IntValue
import com.android.tools.metalava.model.value.StringValue
import kotlin.reflect.KClass

/** A binding factory that will create an instance of [T] from an [AnnotationItem]. */
interface AnnotationBindingFactory<T : Any> {
    /** Create an instance of [T] from [annotationItem] that was applied to [item]. */
    fun createInstanceFrom(
        annotationItem: AnnotationItem,
        item: Item,
    ): T?
}

/**
 * Bind this [AnnotationItem] from [item] to [T].
 *
 * Type [T] must be a concrete, instantiable class with at least one accessible constructor. If it
 * has more than one constructor then the constructor to use must be annotated with
 * [BindingConstructor]. It could be a `data class` but does not have to be.
 *
 * The parameters of the binding constructor determine how it is bound. Each parameter is bound to
 * the annotation attribute with the same name and its [AnnotationAttribute.value] it converted to
 * the parameter's type. e.g. binding to the following class would bind the `alpha` parameter to the
 * `alpha` attribute and its value would be converted to an `Int`. Similarly, the `beta` parameter
 * would be bound to the `beta` attribute and its value would be converted to a `String`.
 *
 * ```
 * data class Foo(val alpha: Int, val beta: String)
 * ```
 *
 * Supported parameter types are:
 * * [ArrayElementValue] - for an [ArrayElementValue]; used when a [FieldReferenceValue] needs to be
 *   handled specially.
 * * [Boolean] - for a [BooleanValue].
 * * [ClassItem] - for a [ClassObjectValue]
 * * [Int] - for an [IntValue].
 * * [Item] - to bind to the annotated [Item].
 * * [String] - for a [StringValue].
 * * Any normal concrete class - for an [AnnotationValue]; its [AnnotationValue.annotationItem] is
 *   bound to the class using this method.
 * * [List] - for [ArrayValue]s of the above types. It can also be used for single values of the
 *   above.
 *
 * Annotations do not support nullable values so every attribute has to have a value, either
 * provided explicitly for required attributes or via a default for optional attributes. When
 * binding, if no attribute is provided then the default will be used.
 *
 * It is possible, e.g. when the definition of the annotation cannot be resolved, for the default to
 * be unavailable. It may also be possible for Metalava to be given source code that does not
 * provide a value for a required attribute. However, it is necessary to provide a value for every
 * parameter in the binding constructor. That can be done in one of the following ways:
 * 1. By specifying a default for the constructor parameter. If the attribute is optional then this
 *    must be the same as the default value specified in the declaration. Otherwise, this should be
 *    a value that indicates that a required attribute has not been provided. That will prevent
 *    compilation. Code that processes it must not abort, but it can treat it as an error.
 * 2. Falling back to a zero value, i.e. a default value of last resort, e.g. `0` for primitive
 *    numbers, `false` for booleans, etc.
 *
 * If a parameter type does not have a default value or a zero value then it must be made nullable
 * to avoid exceptions being thrown when creating an instance from sources with missing required
 * attributes.
 */
inline fun <reified T : Any> AnnotationItem.bindTo(item: Item) = bindTo(T::class, item)

/** Bind this [AnnotationItem] from [item] to [kClass]. */
fun <T : Any> AnnotationItem.bindTo(
    kClass: KClass<T>,
    item: Item,
): T? {
    val factory = item.codebase.bindingFactoryFor(kClass, annotationClass?.defaults)
    return factory.createInstanceFrom(this, item)
}
