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

/**
 * Represents a type parameter list. For example, in class<S, T extends List<String>> the type
 * parameter list is <S, T extends List<String>> and has type parameters named S and T, and type
 * parameter T has bounds List<String>.
 */
interface TypeParameterList {
    /**
     * Returns source representation of this type parameter, using fully qualified names (possibly
     * with java.lang. removed if requested via options)
     */
    override fun toString(): String

    /** Returns the type parameters, if any */
    @MetalavaApi fun typeParameters(): List<TypeParameterItem>

    /** Returns the number of type parameters */
    fun typeParameterCount() = typeParameters().size

    companion object {
        /** Type parameter list when there are no type parameters */
        val NONE =
            object : TypeParameterList {
                override fun toString(): String = ""

                override fun typeParameters(): List<TypeParameterItem> = emptyList()

                override fun typeParameterCount(): Int = 0
            }
    }
}

abstract class DefaultTypeParameterList : TypeParameterList {
    private val toString by lazy {
        buildString {
            if (typeParameters().isNotEmpty()) {
                append("<")
                var first = true
                for (param in typeParameters()) {
                    if (!first) {
                        append(", ")
                    }
                    first = false
                    append(param.toSource())
                }
                append(">")
            }
        }
    }

    override fun toString(): String {
        return toString
    }

    companion object {
        /**
         * Group up [typeParameters] and the [scope] that was used to resolve references when
         * creating their [BoundsTypeItem]s.
         */
        data class TypeParametersAndScope(
            val typeParameters: List<TypeParameterItem>,
            val scope: TypeParameterScope,
        )

        /**
         * Create a list of [TypeParameterItem] and a corresponding [TypeParameterScope] from model
         * specific parameter and bounds information.
         *
         * A type parameter list can contain cycles between its type parameters, e.g.
         *
         *     class Node<L extends Node<L, R>, R extends Node<L, R>>
         *
         * Parsing that requires a multi-stage approach.
         * 1. Separate the list into a mapping from `TypeParameterItem` that have not yet had their
         *    `bounds` property initialized to the model specific parameter.
         * 2. Create a nested scope of the enclosing scope which includes the type parameters. That
         *    will allow references between them to be resolved.
         * 3. Complete the initialization by converting each bounds string into a TypeItem.
         *
         * @param containingScope the containing scope, may be [TypeParameterScope.empty].
         * @param scopeDescription the description of the scope.
         * @param inputParams a list of the model specific type parameters.
         * @param paramFactory a function that will create a [TypeParameterItem] from the model
         *   specified parameter [P].
         * @param boundsSetter a function that will create a list of [BoundsTypeItem] from the model
         *   specific bounds and store it in [TypeParameterItem.typeBounds].
         * @param P the type of the model specific type parameter objects.
         */
        fun <I : TypeParameterItem, P> createTypeParameterItemsAndScope(
            containingScope: TypeParameterScope,
            scopeDescription: String,
            inputParams: List<P>,
            paramFactory: (P) -> I,
            boundsSetter: (TypeParameterScope, I, P) -> List<BoundsTypeItem>,
        ): TypeParametersAndScope {
            // First, create a Map from [TypeParameterItem] to the model specific parameter. Using
            // the [paramFactory] to convert the model specific parameter to a [TypeParameterItem].
            val typeParameterItemToBounds = inputParams.associateBy { param -> paramFactory(param) }

            // Then, create a scope for this list of type parameters.
            val typeParameters = typeParameterItemToBounds.keys.toList()
            val typeParameterScope = containingScope.nestedScope(scopeDescription, typeParameters)

            // Then, create and set the bounds in the [TypeParameterItem] passing in the scope to
            // allow cross-references to type parameters to be resolved.
            for ((typeParameter, param) in typeParameterItemToBounds) {
                val boundsTypeItem = boundsSetter(typeParameterScope, typeParameter, param)
                if (typeParameter.typeBounds() !== boundsTypeItem)
                    error("boundsSetter did not set bounds")
            }

            // Pair the list up with the scope so that the latter can be reused.
            return TypeParametersAndScope(typeParameters, typeParameterScope)
        }
    }
}
