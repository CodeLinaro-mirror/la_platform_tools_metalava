/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.psi.kotlin

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.KOTLIN_CONTINUATION
import com.android.tools.metalava.model.LambdaTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.DefaultArrayTypeItem
import com.android.tools.metalava.model.type.DefaultClassTypeItem
import com.android.tools.metalava.model.type.DefaultLambdaTypeItem
import com.android.tools.metalava.model.type.DefaultPrimitiveTypeItem
import com.android.tools.metalava.model.type.DefaultTypeItemFactory
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.android.tools.metalava.model.type.DefaultVariableTypeItem
import com.android.tools.metalava.model.type.DefaultWildcardTypeItem
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.KaTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.types.Variance

/** Constructs type items from [KaType]s. */
internal class KaTypeItemFactory(
    private val codebase: PsiBasedCodebase,
    private val assembler: KaCodebaseAssembler,
    typeParameterScope: TypeParameterScope,
) : DefaultTypeItemFactory<KaType, KaTypeItemFactory>(typeParameterScope) {
    constructor(
        codebase: PsiBasedCodebase,
        assembler: KaCodebaseAssembler,
        classItem: DefaultClassItem,
    ) : this(codebase, assembler, TypeParameterScope.from(classItem))

    override fun self(): KaTypeItemFactory = this

    override fun createNestedFactory(scope: TypeParameterScope): KaTypeItemFactory {
        return KaTypeItemFactory(codebase, assembler, scope)
    }

    override fun getType(
        underlyingType: KaType,
        contextNullability: ContextNullability,
        isVarArg: Boolean
    ): TypeItem {
        // For a general type, primitives don't need to be boxed.
        val type = underlyingType.toTypeItem(mustBoxPrimitives = false)
        return if (isVarArg) {
            DefaultArrayTypeItem(
                // Kotlin varargs are non-null, unlike Java varargs.
                modifiers = DefaultTypeModifiers.emptyNonNullModifiers,
                componentType = type,
                isVarargs = true,
            )
        } else {
            type
        }
    }

    // Override to ensure primitives are boxed.
    override fun getBoundsType(underlyingType: KaType): BoundsTypeItem {
        return underlyingType.toTypeItem(mustBoxPrimitives = true) as BoundsTypeItem
    }

    // Override to ensure primitives are boxed.
    override fun getSuperClassType(underlyingType: KaType): ClassTypeItem {
        return underlyingType.toTypeItem(mustBoxPrimitives = true) as ClassTypeItem
    }

    // Override to ensure primitives are boxed.
    override fun getInterfaceType(underlyingType: KaType): ClassTypeItem {
        return underlyingType.toTypeItem(mustBoxPrimitives = true) as ClassTypeItem
    }

    /**
     * Creates a [TypeItem] from the [KaType].
     *
     * If [mustBoxPrimitives] is true, all primitive types are boxed. If it is false, they are only
     * boxed when nullable.
     */
    private fun KaType.toTypeItem(mustBoxPrimitives: Boolean): TypeItem {
        val typeItemNullability =
            when (nullability) {
                KaTypeNullability.NULLABLE -> TypeNullability.NULLABLE
                KaTypeNullability.NON_NULLABLE -> TypeNullability.NONNULL
                KaTypeNullability.UNKNOWN -> TypeNullability.UNDEFINED
            }
        val typeItemAnnotations = annotations.mapNotNull { assembler.createAnnotation(it) }
        val modifiers = DefaultTypeModifiers.create(typeItemAnnotations, typeItemNullability)

        // Expand type aliases
        val expandedKaType = analyze(assembler.kaModule) { fullyExpandedType }
        // Convert expanded type to a TypeItem
        return when (expandedKaType) {
            is KaUsualClassType -> expandedKaType.toTypeItem(modifiers, mustBoxPrimitives)
            is KaFunctionType -> expandedKaType.toLambdaTypeItem(modifiers)
            is KaClassType ->
                error(
                    "expected class type to be KaUsualClassType or KaFunctionType, was $expandedKaType"
                )
            is KaTypeParameterType ->
                DefaultVariableTypeItem(
                    modifiers = modifiers,
                    asTypeParameter =
                        typeParameterScope.getTypeParameter(expandedKaType.name.identifier),
                )
            // A flexible type has an upper and lower bound. This is used to represent types with
            // Java platform nullability, so construct the type through one bound and then swap in
            // the correct nullability.
            is KaFlexibleType ->
                expandedKaType.lowerBound
                    .toTypeItem(mustBoxPrimitives)
                    .substitute(TypeNullability.PLATFORM)
            is KaDefinitelyNotNullType ->
                expandedKaType.original
                    .toTypeItem(mustBoxPrimitives)
                    // Ensure correct nullability.
                    .substitute(TypeNullability.NONNULL)
            // To avoid runtime errors, construct an invalid class type for error types.
            is KaErrorType ->
                DefaultClassTypeItem(
                    classResolver = codebase,
                    modifiers = modifiers,
                    qualifiedName = "ErrorType",
                    arguments = emptyList(),
                    outerClassType = null,
                )
            else -> error("Unable to process type: $this")
        }
    }

    /** Creates a [LambdaTypeItem] from the [KaFunctionType]. */
    private fun KaFunctionType.toLambdaTypeItem(modifiers: TypeModifiers): LambdaTypeItem {
        val receiverTypeItem = receiverType?.toTypeItem(mustBoxPrimitives = true)
        val originalParameterTypes = parameterTypes.map { it.toTypeItem(mustBoxPrimitives = true) }
        val originalReturnType = returnType.toTypeItem(mustBoxPrimitives = true)

        val (parameterTypeItems, returnTypeItem) =
            if (isSuspend) {
                // Suspend functions have an additional continuation parameter, using the original
                // return type.
                val continuationParameter =
                    DefaultClassTypeItem(
                        classResolver = codebase,
                        modifiers = DefaultTypeModifiers.emptyNonNullModifiers,
                        qualifiedName = KOTLIN_CONTINUATION,
                        arguments =
                            listOf(
                                createWildcardTypeItem(
                                    superBound = originalReturnType as ReferenceTypeItem
                                )
                            ),
                        outerClassType = null
                    )
                val allParameters = originalParameterTypes + continuationParameter
                // The return type for a suspend function is an object, now that the continuation
                // with the original return type is added to the parameter list.
                allParameters to createObjectWildcardTypeItem(TypeNullability.NONNULL)
            } else {
                originalParameterTypes to originalReturnType
            }

        // Lambda types are still created as class types, so all arguments need to be compiled into
        // a list.
        val arguments = listOfNotNull(receiverTypeItem) + parameterTypeItems + returnTypeItem
        // The function arity doesn't include the return type.
        val qualifiedName = "kotlin.jvm.functions.Function${arguments.size - 1}"

        return DefaultLambdaTypeItem(
            classResolver = codebase,
            modifiers = modifiers,
            qualifiedName = qualifiedName,
            arguments = arguments.map { it as TypeArgumentTypeItem },
            outerClassType = null,
            isSuspend = isSuspend,
            receiverType = receiverTypeItem,
            parameterTypes = parameterTypeItems,
            returnType = returnTypeItem,
        )
    }

    /**
     * Creates a [TypeItem] from the [KaUsualClassType]. This might be a class type, an array type,
     * or a primitive type.
     */
    private fun KaUsualClassType.toTypeItem(
        modifiers: TypeModifiers,
        mustBoxPrimitives: Boolean
    ): TypeItem {
        val qualifiedName = mapType(classId.asFqNameString())

        // Create a primitive type if allowed and possible.
        if (!mustBoxPrimitives && modifiers.nullability != TypeNullability.NULLABLE) {
            PrimitiveTypeItem.Primitive.forWrapperClassName(qualifiedName)?.let { primitive ->
                return DefaultPrimitiveTypeItem(modifiers, primitive)
            }
        }

        // Create an array type if possible.
        maybeToArrayTypeItem(modifiers)?.let {
            return it
        }

        // Otherwise, create a class type.
        val isValueClass =
            analyze(assembler.kaModule) {
                (expandedSymbol as? KaNamedClassSymbol)?.isInline == true
            }
        return DefaultClassTypeItem(
            codebase,
            modifiers,
            qualifiedName,
            // The last element of the qualifiers is for the innermost type
            qualifiers.last().typeArguments.toClassTypeArgumentItems(),
            createOuterClassTypeItem(),
            isValueClassType = isValueClass,
        )
    }

    /** Constructs the outer class for a class type. */
    private fun KaUsualClassType.createOuterClassTypeItem(): ClassTypeItem? {
        // Information about all the type arguments for each class level is in the qualifiers. Skip
        // the last element of the qualifiers, which are for the innermost class, as these will be
        // handled in [createFromClassType].
        val qualifiersToProcess = qualifiers.dropLast(1)
        if (qualifiersToProcess.isEmpty()) return null

        // Construct the outer class from the outermost class in.
        var outerClass: ClassTypeItem? = null
        for (qualifier in qualifiersToProcess) {
            // It should be possible to get a fully qualified name from the qualifier, fall back to
            // the simple name if it is not.
            val qualifiedName =
                mapType(
                    (qualifier.symbol as? KaClassSymbol)?.classId?.asFqNameString()
                        ?: qualifier.name.identifier
                )
            // If the outer class is a primitive class, the inner class is a companion which only
            // exists for kotlin and gets mapped to a java type without an outer class.
            if (PrimitiveTypeItem.Primitive.forWrapperClassName(qualifiedName) != null) return null
            outerClass =
                DefaultClassTypeItem(
                    classResolver = codebase,
                    // Outer classes must be non-null.
                    modifiers = DefaultTypeModifiers.emptyNonNullModifiers,
                    qualifiedName = mapType(qualifiedName),
                    arguments = qualifier.typeArguments.toClassTypeArgumentItems(),
                    // Use the previous outer class as the outer class for this one.
                    outerClassType = outerClass,
                )
        }
        // Return the last outer class created, which will be the innermost of the outer classes.
        return outerClass
    }

    /** Creates the arguments of a class type. */
    private fun List<KaTypeProjection>.toClassTypeArgumentItems(): List<TypeArgumentTypeItem> {
        return map {
            when (it) {
                // Using `*` bounds is equivalent to `? extends Object?`
                is KaStarTypeProjection -> createObjectWildcardTypeItem(TypeNullability.NULLABLE)
                is KaTypeArgumentWithVariance -> {
                    val type = it.type.toTypeItem(mustBoxPrimitives = true) as TypeArgumentTypeItem
                    when (it.variance) {
                        Variance.OUT_VARIANCE ->
                            createWildcardTypeItem(extendsBound = type as ReferenceTypeItem)
                        Variance.IN_VARIANCE ->
                            createWildcardTypeItem(superBound = type as ReferenceTypeItem)
                        else -> type
                    }
                }
            }
        }
    }

    /** Creates an [ArrayTypeItem], if the [KaUsualClassType] represents an array. */
    private fun KaUsualClassType.maybeToArrayTypeItem(modifiers: TypeModifiers): ArrayTypeItem? {
        return analyze(assembler.kaModule) {
            // If there's an arrayElementType, this is an array type.
            arrayElementType?.let { componentType ->
                // An array type might be `kotlin.Array`, or it might be `kotlin.IntArray`,
                // `kotlin.DoubleArray`, etc. When one of the special primitive array types is used,
                // the primitive is not boxed, but primitives should be boxed when a regular Array
                // type is used.
                val boxPrimitives = classId.shortClassName.identifierOrNullIfSpecial == "Array"
                DefaultArrayTypeItem(
                    modifiers = modifiers,
                    componentType = componentType.toTypeItem(boxPrimitives),
                    // Varargs aren't represented as an array ka type, instead [getType] will know
                    // if a type is varargs from the parameter modifiers.
                    isVarargs = false,
                )
            }
        }
    }

    /** Creates a [WildcardTypeItem] with an object extends bound of the given [nullability]. */
    private fun createObjectWildcardTypeItem(nullability: TypeNullability): WildcardTypeItem {
        return createWildcardTypeItem(
            extendsBound =
                DefaultClassTypeItem(
                    classResolver = codebase,
                    modifiers = DefaultTypeModifiers.create(emptyList(), nullability),
                    qualifiedName = "java.lang.Object",
                    arguments = emptyList(),
                    outerClassType = null,
                )
        )
    }

    /** Creates a [WildcardTypeItem] with the given [extendsBound] and [superBound]. */
    private fun createWildcardTypeItem(
        extendsBound: ReferenceTypeItem? = null,
        superBound: ReferenceTypeItem? = null,
    ): WildcardTypeItem {
        return DefaultWildcardTypeItem(
            modifiers = DefaultTypeModifiers.emptyUndefinedModifiers,
            extendsBound = extendsBound,
            superBound = superBound,
        )
    }

    /**
     * If the [kaType] and [type] represent a value class type and is used as non-null, returns the
     * inlined type. Otherwise returns the original type. In the case where a value class's inlined
     * type is itself a value class, this recursively inlines the type.
     */
    fun inlineTypeIfNeeded(
        kaType: KaType,
        type: TypeItem,
    ): TypeItem {
        return analyze(assembler.kaModule) {
            if (type.isValueClassType()) {
                // Find the inlined type through the constructor of the value class. Value classes
                // must have a single parameter for the primary constructor
                val inlineKaType =
                    (kaType.expandedSymbol as KaNamedClassSymbol)
                        .memberScope
                        .constructors
                        .first { it.isPrimary }
                        .valueParameters
                        .first()
                        .returnType
                // Create a TypeItem from the inlined ka type
                val inlineType = getGeneralType(inlineKaType).substitute(type.modifiers.nullability)
                // Recursively inline the type, if needed
                if (inlineType is PrimitiveTypeItem && inlineType.modifiers.isNullable) {
                    type
                } else {
                    val recursivelyInlinedType = inlineTypeIfNeeded(inlineKaType, inlineType)
                    if (
                        recursivelyInlinedType is PrimitiveTypeItem &&
                            !recursivelyInlinedType.isValueClassType()
                    ) {
                        // Make sure this is still listed as a value class type. This is only needed
                        // temporarily until the original value class type is used for property
                        // types instead of the inlined type.
                        object : PrimitiveTypeItem by recursivelyInlinedType {
                            override fun isValueClassType(): Boolean = true
                        }
                    } else {
                        recursivelyInlinedType
                    }
                }
            } else if (type.toTypeString() == "kotlin.ULong" && type.modifiers.isNonNull) {
                // Even though ULong is a value class type, it doesn't appear as one when using K1
                DefaultPrimitiveTypeItem(type.modifiers, PrimitiveTypeItem.Primitive.LONG)
            } else {
                type
            }
        }
    }

    /**
     * If the [typeItem] is a primitive type but any of the [overrideTypes] it overrides is not,
     * returns the boxed version of the primitive type. Otherwise, returns the original type.
     */
    fun handleOverrideBoxing(typeItem: TypeItem, overrideTypes: Sequence<KaType>): TypeItem {
        return if (
            typeItem is PrimitiveTypeItem &&
                overrideTypes.any { analyze(assembler.kaModule) { !it.isPrimitive } }
        ) {
            boxType(typeItem)
        } else {
            typeItem
        }
    }

    /** Converts the [primitiveTypeItem] to the boxed java class type. */
    private fun boxType(primitiveTypeItem: PrimitiveTypeItem): ClassTypeItem {
        return DefaultClassTypeItem(
            classResolver = codebase,
            modifiers = primitiveTypeItem.modifiers,
            qualifiedName = primitiveTypeItem.kind.wrapperClass.canonicalName,
            arguments = emptyList(),
            outerClassType = null,
        )
    }

    companion object {
        /**
         * Converts the qualified name of a
         * [Kotlin mapped type](https://kotlinlang.org/docs/java-interop.html#mapped-types) to the
         * Java equivalent, if possible. If the type is not a mapped type, just returns the original
         * [qualifiedName].
         */
        private fun mapType(qualifiedName: String): String {
            return JavaToKotlinClassMap.mapKotlinToJava(FqNameUnsafe(qualifiedName))
                ?.asFqNameString() ?: qualifiedName
        }
    }
}
