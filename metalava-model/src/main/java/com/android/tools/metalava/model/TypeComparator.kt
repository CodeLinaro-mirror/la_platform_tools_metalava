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

package com.android.tools.metalava.model

/** Compares [TypeItem] instances for equality according to specific criteria. */
sealed interface TypeComparator {
    /** Compare [type1] and [type2] for equality. */
    fun compare(type1: TypeItem?, type2: TypeItem?): Boolean

    /** Compute a hash code for [type] that is consistent with [compare]. */
    fun hash(type: TypeItem?): Int

    /** Provides the basic implementation of [TypeComparator] that others extend. */
    sealed class Base : TypeComparator {
        override fun compare(type1: TypeItem?, type2: TypeItem?): Boolean {
            if (type1 === type2) return true
            if (type1 == null || type2 == null) return false

            return compareDifferentInstances(type1, type2)
        }

        /**
         * Compare [type1] and [type2], which are known to be non-null and not the same instance.
         */
        protected open fun compareDifferentInstances(type1: TypeItem, type2: TypeItem): Boolean {
            if (!compareModifiers(type1.modifiers, type2.modifiers)) return false

            return compareStructure(type1, type2)
        }

        override fun hash(type: TypeItem?): Int {
            if (type == null) return 0

            return hashInstance(type)
        }

        /** Hash [type], which is known to be non-null. */
        protected open fun hashInstance(type: TypeItem): Int {
            var result = hashStructure(type)
            result = 31 * result + hashModifiers(type.modifiers)
            return result
        }

        /**
         * Compare [modifiers1] and [modifiers2].
         *
         * The default compares both nullability and type-use annotations, matching the most
         * complete check across subclasses ([IDENTICAL] and [STRICT]). Subclasses that ignore
         * annotations or nullability override this method.
         */
        protected open fun compareModifiers(
            modifiers1: TypeModifiers,
            modifiers2: TypeModifiers,
        ): Boolean {
            return modifiers1.nullability == modifiers2.nullability &&
                modifiers1.annotations == modifiers2.annotations
        }

        /**
         * Hash [modifiers].
         *
         * The default hashes both nullability and type-use annotations, matching the most complete
         * check across subclasses ([IDENTICAL] and [STRICT]). Subclasses that ignore annotations or
         * nullability override this method.
         */
        protected open fun hashModifiers(modifiers: TypeModifiers): Int {
            var result = modifiers.nullability.hashCode()
            result = 31 * result + modifiers.annotations.hashCode()
            return result
        }

        /**
         * Compare [param1] and [param2].
         *
         * The default compares type parameters by equality ([param1] == [param2]), matching most
         * subclasses ([STRICT], [NULLABILITY_AWARE], [IGNORE_NULLABILITY], [ERASED], and
         * [FLATTENED_WILDCARDS]). Only [IDENTICAL] overrides this to compare by identity.
         */
        protected open fun compareTypeParameters(
            param1: TypeParameterItem,
            param2: TypeParameterItem,
        ): Boolean = param1 == param2

        /**
         * Hash [param].
         *
         * The default hashes type parameters by equality ([param].hashCode()), matching most
         * subclasses ([STRICT], [NULLABILITY_AWARE], [IGNORE_NULLABILITY], [ERASED], and
         * [FLATTENED_WILDCARDS]). Only [IDENTICAL] overrides this to hash by identity.
         */
        protected open fun hashTypeParameter(param: TypeParameterItem): Int = param.hashCode()

        /** Compare the structural elements of [type1] and [type2]. */
        protected open fun compareStructure(type1: TypeItem, type2: TypeItem): Boolean {
            return when (type1) {
                is PrimitiveTypeItem -> {
                    type2 is PrimitiveTypeItem && type1.kind == type2.kind
                }
                is ArrayTypeItem -> {
                    type2 is ArrayTypeItem && compareArrays(type1, type2)
                }
                is ClassTypeItem -> {
                    type2 is ClassTypeItem && compareClasses(type1, type2)
                }
                is VariableTypeItem -> {
                    type2 is VariableTypeItem &&
                        compareTypeParameters(type1.asTypeParameter, type2.asTypeParameter)
                }
                is WildcardTypeItem -> {
                    type2 is WildcardTypeItem &&
                        compare(type1.extendsBound, type2.extendsBound) &&
                        compare(type1.superBound, type2.superBound)
                }
                else -> false
            }
        }

        /** Hash the structural elements of [type]. */
        protected open fun hashStructure(type: TypeItem): Int {
            return when (type) {
                is PrimitiveTypeItem -> type.kind.hashCode()
                is ArrayTypeItem -> hashArray(type)
                is ClassTypeItem -> hashClass(type)
                is VariableTypeItem -> hashTypeParameter(type.asTypeParameter)
                is WildcardTypeItem -> {
                    var result = hash(type.extendsBound)
                    result = 31 * result + hash(type.superBound)
                    result
                }
                else -> 0
            }
        }

        /** Compare [type1] and [type2] when both are [ArrayTypeItem]. */
        protected open fun compareArrays(type1: ArrayTypeItem, type2: ArrayTypeItem): Boolean {
            return type1.isVarargs == type2.isVarargs &&
                compare(type1.componentType, type2.componentType)
        }

        /** Hash [type] when it is an [ArrayTypeItem]. */
        protected open fun hashArray(type: ArrayTypeItem): Int {
            var result = type.isVarargs.hashCode()
            result = 31 * result + hash(type.componentType)
            return result
        }

        /** Compare [type1] and [type2] when both are [ClassTypeItem]. */
        protected open fun compareClasses(type1: ClassTypeItem, type2: ClassTypeItem): Boolean {
            return type1.qualifiedName == type2.qualifiedName &&
                type1.arguments.size == type2.arguments.size &&
                type1.arguments.zip(type2.arguments).all { (a1, a2) -> compare(a1, a2) } &&
                compare(type1.outerClassType, type2.outerClassType)
        }

        /** Hash [type] when it is a [ClassTypeItem]. */
        protected open fun hashClass(type: ClassTypeItem): Int {
            var result = type.qualifiedName.hashCode()
            result = 31 * result + hash(type.outerClassType)
            result = 31 * result + type.arguments.fold(1) { acc, arg -> 31 * acc + hash(arg) }
            return result
        }
    }

    /**
     * [TypeComparator] that compares structure (including type parameter identity), nullability,
     * and type-use annotations.
     *
     * Delegates directly to [TypeItem.equals].
     */
    data object IDENTICAL : TypeComparator {
        override fun compare(type1: TypeItem?, type2: TypeItem?): Boolean = type1 == type2

        override fun hash(type: TypeItem?): Int = type?.hashCode() ?: 0
    }

    /**
     * [TypeComparator] that compares structure (including type parameter equality), nullability,
     * and type-use annotations.
     */
    data object STRICT : Base()

    /**
     * [TypeComparator] that compares structure (including type parameter equality) and nullability,
     * ignoring type-use annotations.
     */
    data object NULLABILITY_AWARE : Base() {
        override fun compareModifiers(
            modifiers1: TypeModifiers,
            modifiers2: TypeModifiers,
        ): Boolean = modifiers1.nullability == modifiers2.nullability

        override fun hashModifiers(modifiers: TypeModifiers): Int = modifiers.nullability.hashCode()
    }

    /** [TypeComparator] that compares structure, ignoring nullability and type-use annotations. */
    data object IGNORE_NULLABILITY : Base() {
        override fun compareModifiers(
            modifiers1: TypeModifiers,
            modifiers2: TypeModifiers,
        ): Boolean = true

        override fun hashModifiers(modifiers: TypeModifiers): Int = 0
    }

    /**
     * [TypeComparator] that compares erased types, ignoring nullability, type-use annotations, and
     * generic type arguments.
     */
    data object ERASED : Base() {
        /** Erased types ignore all modifiers (nullability and type-use annotations). */
        override fun compareModifiers(
            modifiers1: TypeModifiers,
            modifiers2: TypeModifiers,
        ): Boolean = true

        /** Erased types ignore all modifiers (nullability and type-use annotations). */
        override fun hashModifiers(modifiers: TypeModifiers): Int = 0

        /**
         * Resolves any [VariableTypeItem] to its bound before comparing, as type variables erase to
         * their upper bounds.
         */
        override fun compareStructure(type1: TypeItem, type2: TypeItem): Boolean {
            if (type1 is VariableTypeItem || type2 is VariableTypeItem) {
                return compareVariableType(type1, type2)
            }
            return super.compareStructure(type1, type2)
        }

        /**
         * Resolves any [VariableTypeItem] to its bound before hashing, as type variables erase to
         * their upper bounds.
         */
        override fun hashStructure(type: TypeItem): Int {
            if (type is VariableTypeItem) {
                return hashVariableType(type)
            }
            return super.hashStructure(type)
        }

        /**
         * Compares arrays ignoring whether either is a varargs array, as varargs erases to a
         * regular array.
         */
        override fun compareArrays(type1: ArrayTypeItem, type2: ArrayTypeItem): Boolean {
            return compare(type1.componentType, type2.componentType)
        }

        /**
         * Hashes arrays ignoring whether either is a varargs array, as varargs erases to a regular
         * array.
         */
        override fun hashArray(type: ArrayTypeItem): Int = hash(type.componentType)

        /**
         * Compares classes ignoring type arguments, as generic classes erase to their raw types.
         */
        override fun compareClasses(type1: ClassTypeItem, type2: ClassTypeItem): Boolean {
            return type1.qualifiedName == type2.qualifiedName &&
                compare(type1.outerClassType, type2.outerClassType)
        }

        /** Hashes classes ignoring type arguments, as generic classes erase to their raw types. */
        override fun hashClass(type: ClassTypeItem): Int {
            var result = type.qualifiedName.hashCode()
            result = 31 * result + hash(type.outerClassType)
            return result
        }

        /**
         * Compare [type1] and [type2] when at least one of them is a [VariableTypeItem].
         *
         * In Java type erasure (JLS §4.6), a type variable erases to the erasure of its leftmost
         * bound, or to `java.lang.Object` if no bound was specified.
         *
         * This resolves any [VariableTypeItem] to its bound (or `null` if unbounded, representing
         * `java.lang.Object`) and compares:
         * - If either is `java.lang.Object` (or unbounded), both must be `java.lang.Object` (or
         *   unbounded).
         * - Otherwise, their resolved bounds are recursively compared with [compare].
         */
        private fun compareVariableType(type1: TypeItem, type2: TypeItem): Boolean {
            val bound1 = if (type1 is VariableTypeItem) type1.resolveBound() else type1
            val bound2 = if (type2 is VariableTypeItem) type2.resolveBound() else type2

            val isObj1 = bound1 == null || bound1.isJavaLangObject()
            val isObj2 = bound2 == null || bound2.isJavaLangObject()
            return when {
                // If either bound represents java.lang.Object (explicitly or via an unbounded
                // type variable), then both must represent java.lang.Object to be equal.
                isObj1 || isObj2 -> isObj1 && isObj2
                else -> compare(bound1, bound2)
            }
        }

        private fun hashVariableType(type: VariableTypeItem): Int {
            val bound = type.resolveBound()
            return if (bound == null || bound.isJavaLangObject()) {
                JAVA_LANG_OBJECT.hashCode() * 31
            } else {
                hashStructure(bound)
            }
        }

        /**
         * Resolve the leftmost bound of this [VariableTypeItem], following any chains of type
         * variables until a non-variable [TypeItem] is reached.
         *
         * Returns `null` if this type variable is unbounded (representing an implicit bound of
         * `java.lang.Object`), or if a cycle is detected in malformed bounds.
         */
        private fun VariableTypeItem.resolveBound(): TypeItem? {
            var current: TypeItem = this
            val visited = mutableSetOf<TypeParameterItem>()
            while (current is VariableTypeItem) {
                val param = current.asTypeParameter
                if (!visited.add(param)) return null
                val bounds = param.typeBounds()
                if (bounds.isEmpty()) return null
                current = bounds.first()
            }
            return current
        }
    }

    /**
     * [TypeComparator] that compares types after flattening wildcards to their bounds, comparing
     * structure and nullability, but ignoring type-use annotations.
     */
    data object FLATTENED_WILDCARDS : Base() {
        /**
         * Flattens any [WildcardTypeItem] to its bound before comparing modifiers and structure.
         */
        override fun compareDifferentInstances(type1: TypeItem, type2: TypeItem): Boolean {
            val actualType1 = if (type1 is WildcardTypeItem) type1.flatten() else type1
            val actualType2 = if (type2 is WildcardTypeItem) type2.flatten() else type2

            if (actualType1 === actualType2) return true

            return super.compareDifferentInstances(actualType1, actualType2)
        }

        /** Flattens any [WildcardTypeItem] to its bound before hashing modifiers and structure. */
        override fun hashInstance(type: TypeItem): Int {
            val actualType = if (type is WildcardTypeItem) type.flatten() else type
            return super.hashInstance(actualType)
        }

        /** Compares nullability while ignoring type-use annotations. */
        override fun compareModifiers(
            modifiers1: TypeModifiers,
            modifiers2: TypeModifiers,
        ): Boolean = modifiers1.nullability == modifiers2.nullability

        /** Hashes nullability while ignoring type-use annotations. */
        override fun hashModifiers(modifiers: TypeModifiers): Int = modifiers.nullability.hashCode()

        /**
         * Flatten this [WildcardTypeItem] to its bound.
         *
         * Returns this [WildcardTypeItem] if it is unbounded.
         */
        private fun WildcardTypeItem.flatten(): TypeItem = superBound ?: extendsBound ?: this
    }
}

/** Compare this [TypeItem] to [other] using [comparator]. */
fun TypeItem?.equalTo(
    other: TypeItem?,
    comparator: TypeComparator = TypeComparator.IDENTICAL,
): Boolean = comparator.compare(this, other)
