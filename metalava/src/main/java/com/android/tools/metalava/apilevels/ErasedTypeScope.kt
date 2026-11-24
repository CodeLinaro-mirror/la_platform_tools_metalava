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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.model.text.ParsedTypeParameter

/**
 * Lexical scope mapping in-scope type parameter names (such as `T` in `<T extends CharSequence>`)
 * to their erased JVM type descriptors (such as `Ljava/lang/CharSequence;` or
 * `Ljava/lang/Object;`).
 *
 * Scopes can be nested (outer class -> inner class -> method/constructor) via [extend].
 */
internal sealed class ErasedTypeScope {

    /**
     * Looks up the erased JVM descriptor (e.g. `Ljava/lang/Object;`) for the type parameter named
     * [name], or returns `null` if [name] is not a type parameter in this scope or any enclosing
     * scope.
     */
    abstract fun findDescriptor(name: String): String?

    /**
     * Returns a new [ErasedTypeScope] extending this scope with [typeParameters], or returns `this`
     * if [typeParameters] is empty.
     *
     * @param typeParameters the parsed type parameters declared by the class or callable.
     * @param resolver the [ErasedTypeDescriptorResolver] used to resolve explicit type parameter
     *   bounds.
     */
    fun extend(
        typeParameters: List<ParsedTypeParameter>,
        resolver: ErasedTypeDescriptorResolver,
    ): ErasedTypeScope {
        if (typeParameters.isEmpty()) {
            return this
        }
        return PopulatedScope(this, typeParameters, resolver)
    }

    /** The root empty [ErasedTypeScope] with no type parameters in scope. */
    data object Empty : ErasedTypeScope() {
        override fun findDescriptor(name: String): String? = null
    }

    /**
     * An [ErasedTypeScope] containing one or more [typeParameters] on top of a [parent] scope.
     *
     * Bounds are resolved lazily on first lookup so forward-referenced type parameter bounds within
     * the same declaration (e.g. `<T extends U, U extends CharSequence>`) and self-referential
     * bounds (e.g. `<T extends Comparable<T>>`) are handled cleanly.
     */
    private class PopulatedScope(
        private val parent: ErasedTypeScope,
        private val typeParameters: List<ParsedTypeParameter>,
        private val resolver: ErasedTypeDescriptorResolver,
    ) : ErasedTypeScope() {

        /**
         * Cache of resolved erased descriptors indexed by position in [typeParameters].
         *
         * While a type parameter at index `i` is actively being resolved, `resolved[i]` is
         * temporarily set to [JAVA_LANG_OBJECT_DESCRIPTOR] to break any cycle before storing the
         * final resolved bound descriptor.
         */
        private val resolved = arrayOfNulls<String>(typeParameters.size)

        override fun findDescriptor(name: String): String? {
            // Search backwards in case of duplicate type parameter names, matching innermost/last
            // declaration semantics.
            for (i in typeParameters.indices.reversed()) {
                val typeParameter = typeParameters[i]
                if (typeParameter.name == name) {
                    resolved[i]?.let {
                        return it
                    }
                    // Guard against cyclic bounds (e.g. `<T extends U, U extends T>`) by setting a
                    // temporary fallback before resolving the first bound.
                    resolved[i] = JAVA_LANG_OBJECT_DESCRIPTOR
                    val firstBound = typeParameter.bounds.firstOrNull()
                    val descriptor =
                        if (firstBound == null) {
                            JAVA_LANG_OBJECT_DESCRIPTOR
                        } else {
                            resolver.resolveDescriptor(firstBound.type, scope = this)
                        }
                    resolved[i] = descriptor
                    return descriptor
                }
            }
            return parent.findDescriptor(name)
        }
    }

    companion object {
        private const val JAVA_LANG_OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
    }
}
