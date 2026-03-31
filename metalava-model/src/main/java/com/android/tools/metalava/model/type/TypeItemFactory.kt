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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.SkeletonTypeParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.typeNullability

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

    /** Get a type suitable for use as a class reference. */
    fun getClassReferenceType(underlyingType: T): ClassTypeItem

    // Item specific type methods.

    /**
     * Get the type for a field.
     *
     * This considers a number of factors, in addition to the declared type, to determine the
     * appropriate [TypeNullability] for the field type, i.e.:
     * * Any [AnnotationItem.typeNullability] annotations in [itemAnnotations].
     * * Setting of [isEnumConstant]; if it is `true` then it is always [TypeNullability.NONNULL].
     * * If the field is `final` then the nullability of the field's value can be considered
     *   ([isInitialValueNonNull]). Otherwise, it cannot as it may change over the lifetime of the
     *   field.
     *
     * @param underlyingType the underlying model's type.
     * @param itemAnnotations the annotations on the field (not the type).
     * @param isFinal `true` if the field is `final`.
     * @param isEnumConstant `true` if the field is actually an enum constant.
     * @param isInitialValueNonNull a lambda that will be invoked on `final` fields to determine
     *   whether its initial value is non-null. This is a lambda as the determination of the initial
     *   value may be expensive.
     */
    fun getFieldType(
        underlyingType: T,
        itemAnnotations: List<AnnotationItem>,
        isEnumConstant: Boolean,
        isFinal: Boolean,
        isInitialValueNonNull: () -> Boolean,
    ): TypeItem = error("unsupported")

    /**
     * Get the parameter type for a method (or constructor).
     *
     * This considers a number of factors, in addition to the declared type, to determine the
     * appropriate [TypeNullability] for the method parameter type, i.e.:
     * * Any [AnnotationItem.typeNullability] annotations in [itemAnnotations].
     * * Method [fingerprint], which may match a known method whose return type has a known
     *   [TypeNullability].
     *
     * @param underlyingParameterType the underlying model's type.
     * @param itemAnnotations the annotations on the method parameter (not the type).
     * @param fingerprint method fingerprint
     * @param parameterIndex the index of the parameter in the method's list of parameters.
     * @param isVarArg whether this parameter is a vararg parameter. This is provided separately to
     *   the [underlyingParameterType] because while some models encapsulate that information within
     *   the type not all do.
     */
    fun getMethodParameterType(
        underlyingParameterType: T,
        itemAnnotations: List<AnnotationItem>,
        fingerprint: MethodFingerprint,
        parameterIndex: Int,
        isVarArg: Boolean,
    ): TypeItem = error("unsupported")

    /**
     * Get the return type for a method.
     *
     * This considers a number of factors, in addition to the declared type, to determine the
     * appropriate [TypeNullability] for the field type, i.e.:
     * * If it [isAnnotationElement], they must be [TypeNullability.NONNULL].
     * * Method [fingerprint], which may match a known method whose return type has a known
     *   [TypeNullability].
     *
     * @param underlyingReturnType the underlying model's return type.
     * @param itemAnnotations the annotations on the field (not the type).
     * @param fingerprint method fingerprint
     * @param isAnnotationElement true for a non-static method of an annotation class.
     */
    fun getMethodReturnType(
        underlyingReturnType: T,
        itemAnnotations: List<AnnotationItem>,
        fingerprint: MethodFingerprint,
        isAnnotationElement: Boolean,
    ): TypeItem = error("unsupported")
}

/**
 * A fingerprint of a method that is used to determined if it is a known method with known
 * nullability.
 */
data class MethodFingerprint(
    /** The name of the method. */
    val name: String,
    /** The number of parameters. */
    val parameterCount: Int,
)

/**
 * Encapsulates the information necessary to compute the [TypeNullability] from a variety of
 * different sources with differing priorities.
 *
 * The priorities are:
 * 1. Forced by specification, e.g. enum constants, super class types, primitives.
 * 2. Kotlin; ignoring [TypeNullability.PLATFORM].
 * 3. Annotations.
 * 4. Nullability inferred from context, e.g. constant field with non-null value.
 * 4. [defaultNullability]
 */
data class ContextNullability(
    /**
     * The [TypeNullability] that a [TypeItem] MUST have by virtue of what the type is, or where it
     * is used; e.g. [PrimitiveTypeItem]s and super class types MUST be [TypeNullability.NONNULL]
     * while [WildcardTypeItem]s MUST be [TypeNullability.UNDEFINED].
     *
     * This CANNOT be overridden by a nullability annotation.
     */
    val forcedNullability: TypeNullability? = null,

    /**
     * The [TypeNullability] that a [TypeItem] that is a component of an array MUST have.
     *
     * This is used to ensure that annotation attributes, i.e. methods on an annotation class, that
     * return arrays cannot return arrays of a nullable component type.
     *
     * This CANNOT be overridden by a nullability annotation.
     */
    val forcedComponentNullability: TypeNullability? = null,

    /**
     * The annotations from the [Item] whose type this is.
     *
     * If supplied then this may be used by [compute] to determine the [TypeNullability] of the
     * constructed type.
     */
    val itemAnnotations: List<AnnotationItem>? = null,

    /**
     * A [TypeNullability] that can be inferred from the context.
     *
     * It is passed as a lambda as it may be expensive to compute.
     */
    val inferNullability: (() -> TypeNullability?)? = null,

    /** The default [TypeNullability] when all else fails. */
    val defaultNullability: TypeNullability = TypeNullability.PLATFORM
) {
    /**
     * Compute the [TypeNullability] according to the priority in the documentation for this class.
     *
     * @param existingNullability this will either come from Kotlin type information or is the
     *   [TypeNullability] for an existing [TypeItem] that might need its
     *   [TypeModifiers.nullability] updating.
     */
    fun compute(
        existingNullability: TypeNullability?,
        typeAnnotations: List<AnnotationItem>
    ): TypeNullability =
        // If forced is set then use that as the top priority.
        forcedNullability
            // If an existing nullability is provided and is a known nullability then use that but
            // if it is a known nullability then ignore it for now as it is possible that a known
            // nullability will be provided by annotations or inference.
            ?: existingNullability?.takeIf { nullability -> nullability.known }

            // If annotations provide it then use them as the developer requested.
            ?: typeAnnotations.typeNullability

            // If item annotations are found then check them.
            ?: itemAnnotations?.typeNullability

            // If an inferred nullability is provided then use it.
            ?: inferNullability?.invoke()

            // Use an existing nullability, even if it is unknown.
            ?: existingNullability

            // Finally use the default.
            ?: defaultNullability

    /**
     * Get a [ContextNullability] instance for components of arrays.
     *
     * If [forcedComponentNullability] is null then this returns [ContextNullability.none],
     * otherwise it returns a [ContextNullability] whose [forcedNullability] is set to
     * [forcedComponentNullability].
     */
    fun forComponentType() =
        forcedComponentNullability?.let { ContextNullability(forcedNullability = it) } ?: none

    /**
     * Get a [ContextNullability] instance for type variables.
     *
     * If `this`] is [none] then this method returns [defaultUndefined], otherwise this method
     * returns a copy of `this` with [ContextNullability.defaultUndefined] set to
     * [TypeNullability.UNDEFINED].
     */
    fun forTypeVariable() =
        if (this == none) defaultUndefined else copy(defaultNullability = TypeNullability.UNDEFINED)

    companion object {
        /**
         * A [ContextNullability] instance that provides no hints from the context as to the
         * nullability of a type.
         */
        val none = ContextNullability()

        /**
         * A [ContextNullability] instance that will force a type to be treated as
         * [TypeNullability.NONNULL].
         */
        val forceNonNull =
            ContextNullability(
                forcedNullability = TypeNullability.NONNULL,
            )

        /**
         * A [ContextNullability] instance that will force a type to be treated as
         * [TypeNullability.UNDEFINED].
         */
        val forceUndefined =
            ContextNullability(
                forcedNullability = TypeNullability.UNDEFINED,
            )

        /**
         * A [ContextNullability] instance that will default to [TypeNullability.UNDEFINED] if not
         * otherwise specified.
         */
        val defaultUndefined = ContextNullability(defaultNullability = TypeNullability.UNDEFINED)
    }
}

abstract class DefaultTypeItemFactory<in T, F : DefaultTypeItemFactory<T, F>>(
    final override val typeParameterScope: TypeParameterScope
) : TypeItemFactory<T, F> {

    final override fun nestedFactory(
        scopeDescription: String,
        typeParameters: List<TypeParameterItem>
    ): F {
        val scope = typeParameterScope.nestedScope(scopeDescription, typeParameters)
        return if (scope === typeParameterScope) self() else createNestedFactory(scope)
    }

    override fun getBoundsType(underlyingType: T) = getType(underlyingType) as BoundsTypeItem

    override fun getExceptionType(underlyingType: T) = getType(underlyingType) as ExceptionTypeItem

    override fun getGeneralType(underlyingType: T) = getType(underlyingType)

    override fun getInterfaceType(underlyingType: T) = getSuperType(underlyingType)

    override fun getSuperClassType(underlyingType: T) = getSuperType(underlyingType)

    override fun getClassReferenceType(underlyingType: T): ClassTypeItem {
        return getType(underlyingType, contextNullability = ContextNullability.forceNonNull)
            as ClassTypeItem
    }

    /**
     * Creates a [ClassTypeItem] that is suitable for use as a super type, e.g. in an `extends` or
     * `implements` list.
     */
    private fun getSuperType(underlyingType: T): ClassTypeItem {
        return getType(underlyingType, contextNullability = ContextNullability.forceNonNull)
            as ClassTypeItem
    }

    override fun getFieldType(
        underlyingType: T,
        itemAnnotations: List<AnnotationItem>,
        isEnumConstant: Boolean,
        isFinal: Boolean,
        isInitialValueNonNull: () -> Boolean
    ): TypeItem {
        // Get the context nullability. Enum constants are always non-null, item annotations and
        // whether a field is final and has a non-null value are used only if no other source of
        // information about nullability is available.
        val contextNullability =
            if (isEnumConstant) ContextNullability.forceNonNull
            else {
                ContextNullability(
                    itemAnnotations = itemAnnotations,
                    inferNullability = {
                        // Treat the field as non-null if it is final and has a non-null value.
                        TypeNullability.NONNULL.takeIf { isFinal && isInitialValueNonNull() }
                    }
                )
            }

        // Get the field's type, passing in the context nullability.
        return getType(underlyingType, contextNullability = contextNullability)
    }

    override fun getMethodParameterType(
        underlyingParameterType: T,
        itemAnnotations: List<AnnotationItem>,
        fingerprint: MethodFingerprint,
        parameterIndex: Int,
        isVarArg: Boolean
    ): TypeItem {
        val contextNullability =
            ContextNullability(
                itemAnnotations = itemAnnotations,
                inferNullability = {
                    // Check for a known method's nullability.
                    getMethodParameterNullability(fingerprint, parameterIndex)
                }
            )

        return getType(
            underlyingParameterType,
            contextNullability = contextNullability,
            isVarArg = isVarArg,
        )
    }

    override fun getMethodReturnType(
        underlyingReturnType: T,
        itemAnnotations: List<AnnotationItem>,
        fingerprint: MethodFingerprint,
        isAnnotationElement: Boolean,
    ): TypeItem {
        // Annotation elements, aka attributes, or methods on an annotation class cannot have a null
        // value.
        val annotationElementNullability = TypeNullability.NONNULL.takeIf { isAnnotationElement }
        val contextNullability =
            ContextNullability(
                forcedNullability = annotationElementNullability,
                forcedComponentNullability = annotationElementNullability,
                itemAnnotations = itemAnnotations,
                inferNullability = {
                    // Check for a known method's nullability.
                    getMethodReturnTypeNullability(fingerprint)
                }
            )

        // Get the method's return type, passing in the context nullability.
        return getType(underlyingReturnType, contextNullability = contextNullability)
    }

    /** Type safe access to `this`. */
    protected abstract fun self(): F

    /** Create a nested factory that is a copy of this one, except using [scope]. */
    protected abstract fun createNestedFactory(scope: TypeParameterScope): F

    /**
     * Get the [TypeItem] corresponding to the [underlyingType] and within the [contextNullability].
     *
     * The [isVarArg] is provided separately to the [underlyingType] because not all models
     * encapsulate that information within the type.
     */
    protected abstract fun getType(
        underlyingType: T,
        contextNullability: ContextNullability = ContextNullability.none,
        isVarArg: Boolean = false,
    ): TypeItem

    companion object {
        /**
         * Get known [TypeNullability] for parameter [parameterIndex] of method with [fingerprint]
         * if available, or `null`.
         */
        private fun getMethodParameterNullability(
            fingerprint: MethodFingerprint,
            parameterIndex: Int
        ): TypeNullability? {
            val (name, parameterCount) = fingerprint
            return when {
                name == "equals" && parameterCount == 1 ->
                    TypeNullability.NULLABLE.takeIf { parameterIndex == 0 }
                else -> null
            }
        }

        /**
         * Get [TypeNullability], if known, for the return type of the method with [fingerprint], or
         * `null` if the method is not known.
         */
        private fun getMethodReturnTypeNullability(
            fingerprint: MethodFingerprint
        ): TypeNullability? {
            val (name, parameterCount) = fingerprint
            return when {
                name == "toString" && parameterCount == 0 -> TypeNullability.NONNULL
                else -> null
            }
        }
    }

    /**
     * Create a list of [TypeParameterItem] and a corresponding [TypeItemFactory] from model
     * specific parameter and bounds information within this [TypeItemFactory].
     *
     * A type parameter list can contain cycles between its type parameters, e.g.
     *
     *     class Node<L extends Node<L, R>, R extends Node<L, R>>
     *
     * Parsing that requires a multi-stage approach.
     * 1. Separate the list into a mapping from `TypeParameterItem` that have not yet had their
     *    `bounds` property initialized to the model specific parameter.
     * 2. Create a nested factory of the enclosing factory which includes the type parameters. That
     *    will allow references between them to be resolved.
     * 3. Complete the initialization by converting each bounds string into a TypeItem.
     *
     * @param scopeDescription the description of the scope that will be created by the factory.
     * @param inputParams a list of the model specific type parameters.
     * @param paramFactory a function that will create a [TypeParameterItem] from the model
     *   specified parameter [P].
     * @param boundsGetter a function that will create a list of [BoundsTypeItem] from the model
     *   specific bounds which will be stored in [SkeletonTypeParameterItem.bounds].
     * @param P the type of the underlying model specific type parameter objects.
     */
    fun <P> createTypeParameterItemsAndFactory(
        scopeDescription: String,
        inputParams: List<P>,
        paramFactory: (P) -> SkeletonTypeParameterItem,
        boundsGetter: (F, P) -> List<BoundsTypeItem>,
    ): TypeParameterListAndFactory<F> {
        // First, create a Map from [TypeParameterItem] to the model specific parameter. Using
        // the [paramFactory] to convert the model specific parameter to a [TypeParameterItem].
        val typeParameterItemToBounds = inputParams.associateBy { param -> paramFactory(param) }

        // Then, create a [TypeItemFactory] for this list of type parameters.
        val typeParameters = typeParameterItemToBounds.keys.toList()
        val typeItemFactory = nestedFactory(scopeDescription, typeParameters)

        // Then, create and set the bounds in the [TypeParameterItem] passing in the
        // [TypeItemFactory] to allow cross-references to type parameters to be resolved.
        for ((typeParameter, param) in typeParameterItemToBounds) {
            val boundsTypeItems = boundsGetter(typeItemFactory, param)
            if (boundsTypeItems.isEmpty()) {
                error("No bounds provided for type parameter: $param")
            }
            typeParameter.bounds = boundsTypeItems
        }

        // Pair the list up with the [TypeItemFactory] so that the latter can be reused.
        val typeParameterList = DefaultTypeParameterList(typeParameters)
        return TypeParameterListAndFactory(typeParameterList, typeItemFactory)
    }
}

/**
 * Group up [typeParameterList] and the [factory] that was used to resolve references when creating
 * their [com.android.tools.metalava.model.BoundsTypeItem]s.
 */
data class TypeParameterListAndFactory<F : TypeItemFactory<*, F>>(
    val typeParameterList: TypeParameterList,
    val factory: F,
)

/** Determine if [qualifiedName] is a class name according to standard Java naming conventions. */
fun isClassByConvention(qualifiedName: String): Boolean {
    val length = qualifiedName.length
    var startIndex = 0

    // Iterate over the simple names in the qualified name, starting with the first simple name.
    // If any simple name looks like a class (starts with an upper case character) then the whole
    // name is for a class as a class can only qualify other classes. That ensures correct behavior
    // for something like android.Manifest.permission.
    while (startIndex < length) {
        // Determine if the simple name being processed is for a class.
        val c = qualifiedName[startIndex]
        if (c.isUpperCase()) return true

        // Find the end of the current simple name.
        val nextDotIndex = qualifiedName.indexOf('.', startIndex)
        val endOfNextSimpleName = if (nextDotIndex == -1) length else nextDotIndex

        // Move onto the next simple name, if any, by skipping over the '.' separator.
        startIndex = endOfNextSimpleName + 1
    }

    return false
}
