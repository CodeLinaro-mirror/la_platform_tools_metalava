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

package com.android.tools.metalava.model

import java.util.Objects
import java.util.function.Predicate

/** Common to [MethodItem] and [ConstructorItem]. */
@MetalavaApi
interface CallableItem : MemberItem, TypeParameterListOwner {

    /** Whether this callable is a constructor or a method. */
    @MetalavaApi fun isConstructor(): Boolean

    /**
     * The return type of this callable.
     *
     * Returns the [ClassItem.type] of the [containingClass] for constructors.
     */
    @MetalavaApi fun returnType(): TypeItem

    /** The list of parameters */
    @MetalavaApi fun parameters(): List<ParameterItem>

    override fun type() = returnType()

    /** Types of exceptions that this callable can throw */
    fun throwsTypes(): List<ExceptionTypeItem>

    /** The body of this, may not be available. */
    val body: CallableBody

    /** Returns true if this callable throws the given exception */
    fun throws(qualifiedName: String): Boolean {
        for (type in throwsTypes()) {
            val throwableClass = type.erasedClass ?: continue
            if (throwableClass.extends(qualifiedName)) {
                return true
            }
        }

        return false
    }

    fun filteredThrowsTypes(predicate: Predicate<Item>): Collection<ExceptionTypeItem> {
        if (throwsTypes().isEmpty()) {
            return emptyList()
        }
        return filteredThrowsTypes(predicate, LinkedHashSet())
    }

    private fun filteredThrowsTypes(
        predicate: Predicate<Item>,
        throwsTypes: LinkedHashSet<ExceptionTypeItem>
    ): LinkedHashSet<ExceptionTypeItem> {
        for (exceptionType in throwsTypes()) {
            if (exceptionType is VariableTypeItem) {
                throwsTypes.add(exceptionType)
            } else {
                val classItem = exceptionType.erasedClass ?: continue
                if (predicate.test(classItem)) {
                    throwsTypes.add(exceptionType)
                } else {
                    // Excluded, but it may have super class throwables that are included; if so,
                    // include those.
                    classItem
                        .allSuperClasses()
                        .firstOrNull { superClass -> predicate.test(superClass) }
                        ?.let { superClass -> throwsTypes.add(superClass.type()) }
                }
            }
        }
        return throwsTypes
    }

    /** Override to specialize return type. */
    override fun findCorrespondingItemIn(
        codebase: Codebase,
        superMethods: Boolean,
        duplicate: Boolean,
    ): CallableItem?

    override fun baselineElementId() = buildString {
        append(containingClass().qualifiedName())
        append("#")
        append(name())
        append("(")
        parameters().joinTo(this) { it.type().toSimpleType() }
        append(")")
    }

    override fun toStringForItem(): String {
        return "${if (isConstructor()) "constructor" else "method"} ${
            containingClass().qualifiedName()}.${name()}(${parameters().joinToString { it.type().toSimpleType() }})"
    }

    /**
     * Returns true if overloads of this callable should be checked separately when checking the
     * signature of this callable.
     *
     * This works around the issue of actual callable not generating overloads for @JvmOverloads
     * annotation when the default is specified on expect side
     * (https://youtrack.jetbrains.com/issue/KT-57537).
     */
    fun shouldExpandOverloads(): Boolean = false

    override fun equalsToItem(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CallableItem) return false

        // The name check will ensure that methods and constructors do not compare equally to each
        // other even if the rest of this method would return true. That is because constructor
        // names MUST be the class' `simpleName`, whereas method names MUST NOT be the class'
        // `simpleName`.
        if (name() != other.name()) {
            return false
        }

        if (containingClass() != other.containingClass()) {
            return false
        }

        val parameters1 = parameters()
        val parameters2 = other.parameters()

        if (parameters1.size != parameters2.size) {
            return false
        }

        for (i in parameters1.indices) {
            val parameter1 = parameters1[i]
            val parameter2 = parameters2[i]
            if (parameter1.type() != parameter2.type()) {
                return false
            }
        }

        val typeParameters1 = typeParameterList
        val typeParameters2 = other.typeParameterList

        if (typeParameters1.size != typeParameters2.size) {
            return false
        }

        for (i in typeParameters1.indices) {
            val typeParameter1 = typeParameters1[i]
            val typeParameter2 = typeParameters2[i]
            if (typeParameter1.typeBounds() != typeParameter2.typeBounds()) {
                return false
            }
        }
        return true
    }

    override fun hashCodeForItem(): Int {
        // Just use the callable name, containing class and number of parameters.
        return Objects.hash(name(), containingClass(), parameters().size)
    }

    /**
     * Returns true if this callable is a signature match for the given callable (e.g. can be
     * overriding if it is a method). This checks that the name and parameter lists match, but
     * ignores differences in parameter names, return value types and throws list types.
     */
    fun matches(other: CallableItem): Boolean {
        if (this === other) return true

        // The name check will ensure that methods and constructors do not compare equally to each
        // other even if the rest of this method would return true. That is because constructor
        // names MUST be the class' `simpleName`, whereas method names MUST NOT be the class'
        // `simpleName`.
        if (name() != other.name()) {
            return false
        }

        val parameters1 = parameters()
        val parameters2 = other.parameters()

        if (parameters1.size != parameters2.size) {
            return false
        }

        for (i in parameters1.indices) {
            val parameter1Type = parameters1[i].type()
            val parameter2Type = parameters2[i].type()
            if (parameter1Type == parameter2Type) continue
            if (parameter1Type.toErasedTypeString() == parameter2Type.toErasedTypeString()) continue

            val convertedType =
                parameter1Type.convertType(other.containingClass(), containingClass())
            if (convertedType != parameter2Type) return false
        }
        return true
    }

    /**
     * Returns whether this callable has any types in its signature that does not match the given
     * filter.
     */
    fun hasHiddenType(filterReference: Predicate<Item>): Boolean {
        for (parameter in parameters()) {
            if (parameter.type().hasHiddenType(filterReference)) return true
        }

        if (returnType().hasHiddenType(filterReference)) return true

        for (typeParameter in typeParameterList) {
            if (typeParameter.typeBounds().any { it.hasHiddenType(filterReference) }) return true
        }

        return false
    }

    /** Checks if there is a reference to a hidden class anywhere in the type. */
    private fun TypeItem.hasHiddenType(filterReference: Predicate<Item>): Boolean {
        return when (this) {
            is PrimitiveTypeItem -> false
            is ArrayTypeItem -> componentType.hasHiddenType(filterReference)
            is ClassTypeItem ->
                asClass()?.let { !filterReference.test(it) } == true ||
                    outerClassType?.hasHiddenType(filterReference) == true ||
                    arguments.any { it.hasHiddenType(filterReference) }
            is VariableTypeItem -> !filterReference.test(asTypeParameter)
            is WildcardTypeItem ->
                extendsBound?.hasHiddenType(filterReference) == true ||
                    superBound?.hasHiddenType(filterReference) == true
            else -> throw IllegalStateException("Unrecognized type: $this")
        }
    }

    companion object {
        private fun compareCallables(
            o1: CallableItem,
            o2: CallableItem,
            overloadsInSourceOrder: Boolean
        ): Int {
            val name1 = o1.name()
            val name2 = o2.name()
            if (name1 == name2) {
                if (overloadsInSourceOrder) {
                    val rankDelta = o1.sortingRank - o2.sortingRank
                    if (rankDelta != 0) {
                        return rankDelta
                    }
                }

                // Compare by the rest of the signature to ensure stable output (we don't need to
                // sort
                // by return value or modifiers or modifiers or throws-lists since methods can't be
                // overloaded
                // by just those attributes
                val p1 = o1.parameters()
                val p2 = o2.parameters()
                val p1n = p1.size
                val p2n = p2.size
                for (i in 0 until minOf(p1n, p2n)) {
                    val compareTypes =
                        p1[i]
                            .type()
                            .toTypeString()
                            .compareTo(p2[i].type().toTypeString(), ignoreCase = true)
                    if (compareTypes != 0) {
                        return compareTypes
                    }
                    // (Don't compare names; they're not part of the signatures)
                }
                return p1n.compareTo(p2n)
            }

            return name1.compareTo(name2)
        }

        val comparator: Comparator<CallableItem> = Comparator { o1, o2 ->
            compareCallables(o1, o2, false)
        }
        val sourceOrderComparator: Comparator<CallableItem> = Comparator { o1, o2 ->
            val delta = o1.sortingRank - o2.sortingRank
            if (delta == 0) {
                // Within a source file all the items will have unique sorting ranks, but since
                // we copy methods in from hidden super classes it's possible for ranks to clash,
                // and in that case we'll revert to a signature based comparison
                comparator.compare(o1, o2)
            } else {
                delta
            }
        }
        val sourceOrderForOverloadedMethodsComparator: Comparator<CallableItem> =
            Comparator { o1, o2 ->
                compareCallables(o1, o2, true)
            }
    }
}
