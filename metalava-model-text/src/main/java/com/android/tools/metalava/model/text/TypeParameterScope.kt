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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.TypeParameterItem

/**
 * The set of [TypeParameterItem]s that are in scope.
 *
 * This is used to resolve a reference to a type parameter to the appropriate type parameter. They
 * are in order from closest to most distant. e.g. When resolving the type parameters for `method`
 * this will contain the type parameters from `method` then from `Inner`, then from `Outer`, i.e.
 * `[M, X, I, X, O, X]`. That ensures that when searching for a type parameter whose name shadows
 * one from an outer scope, e.g. `X`, that the inner one is used.
 *
 * ```
 *     public class Outer<O, X> {
 *     }
 *
 *     public class Outer.Inner<I, X> {
 *       method public <M, X> M method(O o, I i);
 *     }
 * ```
 */
internal sealed class TypeParameterScope private constructor() {

    /** True if there are no type parameters in scope. */
    fun isEmpty() = count == 0

    /**
     * Create a nested [TypeParameterScope] that will delegate to this one for any
     * [TypeParameterItem]s that it cannot find.
     */
    fun nestedScope(typeParameters: List<TypeParameterItem>) =
        // If the typeParameters is empty then just reuse this one, otherwise create a new scope
        // delegating to this.
        if (typeParameters.isEmpty()) this else ListWrapper(typeParameters, this)

    /** Finds the closest [TypeParameterItem] with the specified name. */
    abstract fun findTypeParameter(name: String): TypeParameterItem?

    protected abstract val count: Int

    companion object {
        val empty: TypeParameterScope = Empty

        /**
         * Collect all the type parameters in scope for the given [owner] then wrap them in an
         * [TypeParameterScope].
         */
        fun from(owner: TypeParameterListOwner?): TypeParameterScope {
            return if (owner == null) empty
            else {
                // Construct a scope from the owner.
                from(owner.typeParameterListOwnerParent())
                    // Nest this inside it.
                    .nestedScope(owner.typeParameterList().typeParameters())
            }
        }
    }

    private class ListWrapper(
        private val list: List<TypeParameterItem>,
        private val enclosingScope: TypeParameterScope
    ) : TypeParameterScope() {

        override val count: Int = list.size + enclosingScope.count

        override fun findTypeParameter(name: String) =
            // Search in this scope first, then delegate to the parent.
            list.firstOrNull { it.name() == name } ?: enclosingScope.findTypeParameter(name)
    }

    private object Empty : TypeParameterScope() {

        override val count: Int = 0

        override fun findTypeParameter(name: String) = null
    }
}
