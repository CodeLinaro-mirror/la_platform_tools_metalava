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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterScope

/**
 * Factory for building [TypeItem] from the underlying model specific representation of the type.
 *
 * The purpose of this is to abstract away all the model specific details of type construction in a
 * consistent manner across the models so that the types can be constructed in a consistent manner.
 * It provides specialist functions for different type uses that can ensure the specific constraints
 * of those type uses are enforced. e.g. types used for super class and interfaces cannot be
 * nullable.
 *
 * At the moment the specialist functions are limited to just a few different types but over time it
 * will be expanded as more type creation logic is moved inside.
 *
 * @param T the underlying model specific representation of a type.
 * @param F the type of the factory implementation.
 */
interface TypeItemFactory<in T, F : TypeItemFactory<T, F>> {

    /**
     * The [TypeParameterScope] that is used by this factory to resolve references to
     * [TypeParameterItem]s.
     */
    val typeParameterScope: TypeParameterScope

    /**
     * Create a [TypeItemFactory] that can resolve references to the [typeParameters].
     *
     * Returns `this` if [typeParameters] is empty, otherwise returns a new [TypeItemFactory] with a
     * new [typeParameterScope] with the specified [scopeDescription] and containing the supplied
     * [typeParameters].
     */
    fun nestedFactory(scopeDescription: String, typeParameters: List<TypeParameterItem>): F

    /** Get a type suitable for use in a wildcard type bounds clause. */
    fun getBoundsType(underlyingType: T): BoundsTypeItem

    /** Get a type suitable for use in a `throws` list. */
    fun getExceptionType(underlyingType: T): ExceptionTypeItem

    /**
     * Get a general type suitable for use anywhere not covered by one of the more specific type
     * methods in this.
     */
    fun getGeneralType(underlyingType: T): TypeItem

    /**
     * Get a type suitable for use in an `implements` list of a concrete class, or an `extends` list
     * of an interface class.
     */
    fun getInterfaceType(underlyingType: T): ClassTypeItem

    /** Get a type suitable for use in an `extends` clause of a concrete class. */
    fun getSuperClassType(underlyingType: T): ClassTypeItem
}
