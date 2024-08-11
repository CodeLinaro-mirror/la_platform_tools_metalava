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
     * Finds uncaught exceptions actually thrown inside this callable (as opposed to ones declared
     * in the signature)
     */
    fun findThrownExceptions(): Set<ClassItem> = codebase.unsupported()

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
}
