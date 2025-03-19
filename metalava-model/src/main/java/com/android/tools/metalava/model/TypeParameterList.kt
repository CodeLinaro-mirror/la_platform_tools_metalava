/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.TypeParameterItem.Companion.SOURCE_TYPE_STRING_CONFIGURATION
import com.android.tools.metalava.model.item.DefaultTypeParameterItem
import com.android.tools.metalava.model.type.TypeItemFactory

/**
 * Represents a type parameter list. For example, in class<S, T extends List<String>> the type
 * parameter list is <S, T extends List<String>> and has type parameters named S and T, and type
 * parameter T has bounds List<String>.
 */
interface TypeParameterList : List<TypeParameterItem> {
    /**
     * Returns source representation of this type parameter, using fully qualified names (possibly
     * with java.lang. removed if requested via [configuration]).
     */
    fun toSource(configuration: TypeStringConfiguration = SOURCE_TYPE_STRING_CONFIGURATION): String

    /**
     * Returns source representation of this type parameter, using fully qualified names (possibly
     * with java.lang. removed if requested via options)
     */
    override fun toString(): String

    /** Implemented according to the general [java.util.List.equals] contract. */
    override fun equals(other: Any?): Boolean

    /** Implemented according to the general [java.util.List.hashCode] contract. */
    override fun hashCode(): Int

    companion object {
        private val emptyListDelegate = emptyList<TypeParameterItem>()

        /** Type parameter list when there are no type parameters */
        val NONE: TypeParameterList =
            object : TypeParameterList, List<TypeParameterItem> by emptyListDelegate {
                override fun toSource(configuration: TypeStringConfiguration): String {
                    return ""
                }

                override fun toString() = toSource()

                override fun equals(other: Any?) = emptyListDelegate == other

                override fun hashCode() = emptyListDelegate.hashCode()
            }
    }
}

class DefaultTypeParameterList
private constructor(private val typeParameters: List<TypeParameterItem>) :
    TypeParameterList, List<TypeParameterItem> by typeParameters {

    private val toString by lazy(LazyThreadSafetyMode.NONE) { toSource() }

    override fun toSource(configuration: TypeStringConfiguration) = buildString {
        if (this@DefaultTypeParameterList.isNotEmpty()) {
            append("<")
            var first = true
            for (param in this@DefaultTypeParameterList) {
                if (!first) {
                    append(", ")
                }
                first = false
                append(param.toSource(configuration))
            }
            append(">")
        }
    }

    override fun toString(): String {
        return toString
    }

    override fun equals(other: Any?) = typeParameters == other

    override fun hashCode() = typeParameters.hashCode()

    companion object {

        /**
         * Create a list of [TypeParameterItem] and a corresponding [TypeItemFactory] from model
         * specific parameter and bounds information.
         *
         * A type parameter list can contain cycles between its type parameters, e.g.
         *
         *     class Node<L extends Node<L, R>, R extends Node<L, R>>
         *
         * Parsing that requires a multi-stage approach.
         * 1. Separate the list into a mapping from `TypeParameterItem` that have not yet had their
         *    `bounds` property initialized to the model specific parameter.
         * 2. Create a nested factory of the enclosing factory which includes the type parameters.
         *    That will allow references between them to be resolved.
         * 3. Complete the initialization by converting each bounds string into a TypeItem.
         *
         * @param containingTypeItemFactory the containing factory.
         * @param scopeDescription the description of the scope that will be created by the factory.
         * @param inputParams a list of the model specific type parameters.
         * @param paramFactory a function that will create a [TypeParameterItem] from the model
         *   specified parameter [P].
         * @param boundsGetter a function that will create a list of [BoundsTypeItem] from the model
         *   specific bounds which will be stored in [DefaultTypeParameterItem.bounds].
         * @param P the type of the underlying model specific type parameter objects.
         * @param F the type of the model specific [TypeItemFactory].
         */
        fun <P, F : TypeItemFactory<*, F>> createTypeParameterItemsAndFactory(
            containingTypeItemFactory: F,
            scopeDescription: String,
            inputParams: List<P>,
            paramFactory: (P) -> DefaultTypeParameterItem,
            boundsGetter: (F, P) -> List<BoundsTypeItem>,
        ): TypeParameterListAndFactory<F> {
            // First, create a Map from [TypeParameterItem] to the model specific parameter. Using
            // the [paramFactory] to convert the model specific parameter to a [TypeParameterItem].
            val typeParameterItemToBounds = inputParams.associateBy { param -> paramFactory(param) }

            // Then, create a [TypeItemFactory] for this list of type parameters.
            val typeParameters = typeParameterItemToBounds.keys.toList()
            val typeItemFactory =
                containingTypeItemFactory.nestedFactory(scopeDescription, typeParameters)

            // Then, create and set the bounds in the [TypeParameterItem] passing in the
            // [TypeItemFactory] to allow cross-references to type parameters to be resolved.
            for ((typeParameter, param) in typeParameterItemToBounds) {
                val boundsTypeItems = boundsGetter(typeItemFactory, param)
                typeParameter.bounds = boundsTypeItems
            }

            // Pair the list up with the [TypeItemFactory] so that the latter can be reused.
            val typeParameterList = DefaultTypeParameterList(typeParameters)
            return TypeParameterListAndFactory(typeParameterList, typeItemFactory)
        }
    }
}

/**
 * Group up [typeParameterList] and the [factory] that was used to resolve references when creating
 * their [BoundsTypeItem]s.
 */
data class TypeParameterListAndFactory<F : TypeItemFactory<*, F>>(
    val typeParameterList: TypeParameterList,
    val factory: F,
)
