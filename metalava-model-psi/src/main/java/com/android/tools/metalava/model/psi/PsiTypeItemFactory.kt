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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.LambdaTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.DefaultTypeItemFactory
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.model.type.isClassByConvention
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.impl.source.PsiClassReferenceType
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.kotlin.isKotlin

/**
 * Encapsulates a [PsiType] and an optional context [PsiElement] for use with [PsiTypeItemFactory].
 */
data class PsiTypeInfo(val psiType: PsiType, val context: PsiElement? = null)

/**
 * Creates [TypeItem]s from [PsiType]s and an optional context [PsiElement], encapsulated within
 * [PsiTypeInfo].
 */
internal class PsiTypeItemFactory(
    private val globalContext: PsiGlobalContext,
    typeParameterScope: TypeParameterScope
) : DefaultTypeItemFactory<PsiTypeInfo, PsiTypeItemFactory>(typeParameterScope) {

    private val codebase = globalContext.psiCodebase

    /** Construct a [PsiTypeItemFactory] suitable for creating types within [classItem]. */
    fun from(classItem: ClassItem): PsiTypeItemFactory {
        val scope = TypeParameterScope.from(classItem)
        return if (scope.isEmpty()) this else PsiTypeItemFactory(globalContext, scope)
    }

    /** Construct a [PsiTypeItemFactory] suitable for creating types within [callableItem]. */
    fun from(callableItem: CallableItem): PsiTypeItemFactory {
        val scope = TypeParameterScope.from(callableItem)
        return if (scope.isEmpty()) this else PsiTypeItemFactory(globalContext, scope)
    }

    override fun self() = this

    override fun createNestedFactory(scope: TypeParameterScope) =
        PsiTypeItemFactory(globalContext, scope)

    override fun getType(
        underlyingType: PsiTypeInfo,
        contextNullability: ContextNullability,
        // The isVarArg is unused here as that information is encoded in the [PsiType] using the
        // [PsiEllipsisType] extension of [PsiArrayType].
        isVarArg: Boolean,
    ): TypeItem {
        return getType(underlyingType.psiType, underlyingType.context, contextNullability)
    }

    override fun getMethodParameterType(
        underlyingParameterType: PsiTypeInfo,
        itemAnnotations: List<AnnotationItem>,
        fingerprint: MethodFingerprint,
        parameterIndex: Int,
        isVarArg: Boolean
    ): TypeItem {
        val isFinalParameter = parameterIndex + 1 == fingerprint.parameterCount
        val fixedUnderlyingParameterType =
            if (
                // Workaround for b/388030457, b/388508139: when a vararg is used in kotlin for a
                // parameter that isn't final, it should be a regular PsiArrayType, not a
                // PsiEllipsisType, but psi gets it wrong in some cases.
                underlyingParameterType.context?.isKotlin() == true &&
                    underlyingParameterType.psiType is PsiEllipsisType &&
                    !isFinalParameter
            ) {
                underlyingParameterType.copy(
                    psiType = underlyingParameterType.psiType.toArrayType()
                )
            } else if (
                // Part of the workaround for https://youtrack.jetbrains.com/issue/KT-57537: for
                // expect/actual JvmOverloads methods, the overloads aren't present in UAST and are
                // created by metalava. A non-final varargs parameter will not have a
                // PsiEllipsisType, but if the varargs parameter becomes final in one of the
                // overloads, it needs to be switched to a PsiEllipsisType.
                isFinalParameter &&
                    !isVarArg &&
                    underlyingParameterType.psiType is PsiArrayType &&
                    (underlyingParameterType.context as? UParameter).hasVarargModifier()
            ) {
                underlyingParameterType.copy(
                    psiType =
                        PsiEllipsisType(
                            underlyingParameterType.psiType.componentType,
                            underlyingParameterType.psiType.annotationProvider
                        )
                )
            } else {
                underlyingParameterType
            }

        return super.getMethodParameterType(
            fixedUnderlyingParameterType,
            itemAnnotations,
            fingerprint,
            parameterIndex,
            isVarArg
        )
    }

    private fun UParameter?.hasVarargModifier() =
        this?.modifierList?.text?.contains("vararg") == true

    /**
     * Returns a [TypeItem] representing the [psiType]. The [context] is used to get nullability
     * information for Kotlin types.
     */
    internal fun getType(
        psiType: PsiType,
        context: PsiElement? = null,
        contextNullability: ContextNullability = ContextNullability.none,
    ): TypeItem {
        val kotlinTypeInfo =
            if (context != null && isKotlin(context)) {
                KotlinTypeInfo.fromContext(context)
            } else {
                null
            }

        // Note: We do *not* cache these; it turns out that storing PsiType instances
        // in a map is bad for performance; it has a very expensive equals operation
        // for some type comparisons (and we sometimes end up with unexpected results,
        // e.g. where we fetch an "equals" type from the map but its representation
        // is slightly different to what was intended
        return createTypeItem(psiType, kotlinTypeInfo, contextNullability)
    }

    // TypeItem factory methods

    /** Creates modifiers based on the annotations of the [type]. */
    private fun createTypeModifiers(
        type: PsiType,
        kotlinType: KotlinTypeInfo?,
        contextNullability: ContextNullability,
    ): TypeModifiers {
        val typeAnnotations =
            type.annotations.mapNotNull { anno ->
                // SLC adds JetBrain nullness annotation on types.
                if (anno.isJetBrainNullnessAnnotation) null
                else PsiAnnotationItem.create(codebase, anno)
            }
        // Compute the nullability, factoring in any context nullability, kotlin types and
        // type annotations.
        val nullability = contextNullability.compute(kotlinType?.nullability(), typeAnnotations)
        return TypeModifiers.create(typeAnnotations, nullability)
    }

    private val PsiAnnotation.isJetBrainNotNull: Boolean
        get() {
            return qualifiedName == org.jetbrains.annotations.NotNull::class.qualifiedName
        }

    private val PsiAnnotation.isJetBrainNullable: Boolean
        get() {
            return qualifiedName == org.jetbrains.annotations.Nullable::class.qualifiedName
        }

    private val PsiAnnotation.isJetBrainNullnessAnnotation: Boolean
        get() {
            return isJetBrainNotNull || isJetBrainNullable
        }

    /** Create a [TypeItem]. */
    private fun createTypeItem(
        psiType: PsiType,
        kotlinType: KotlinTypeInfo?,
        contextNullability: ContextNullability = ContextNullability.none,
    ): TypeItem {
        return when (psiType) {
            is PsiPrimitiveType ->
                createPrimitiveTypeItem(
                    psiType = psiType,
                    kotlinType = kotlinType,
                )
            is PsiArrayType ->
                createArrayTypeItem(
                    psiType = psiType,
                    kotlinType = kotlinType,
                    contextNullability = contextNullability,
                )
            is PsiClassType -> {
                val typeParameterItem =
                    when (val psiClass = psiType.resolve()) {
                        // If the type resolves to a PsiTypeParameter then the TypeParameterItem
                        // must exist.
                        is PsiTypeParameter -> {
                            val name = psiClass.simpleName
                            typeParameterScope.getTypeParameter(name)
                        }
                        // If the type could not be resolved then the TypeParameterItem might
                        // exist.
                        null ->
                            psiType.className?.let { name ->
                                typeParameterScope.findTypeParameter(name)
                            }
                        // Else it is not a TypeParameterItem.
                        else -> null
                    }

                if (typeParameterItem != null) {
                    createVariableTypeItem(
                        psiType = psiType,
                        kotlinType = kotlinType,
                        typeParameterItem = typeParameterItem,
                        contextNullability = contextNullability,
                    )
                } else {
                    if (kotlinType?.isLambdaType() == true) {
                        createLambdaTypeItem(
                            psiType = psiType,
                            kotlinType = kotlinType,
                            contextNullability = contextNullability,
                        )
                    } else if (psiType.className?.toIntOrNull() != null) {
                        // Workaround for b/407632515: a synthetic delegate lambda method loaded
                        // from a jar has a number as the class name and causes on error when
                        // creating the outer class type. Reload the PsiType, updating the qualified
                        // name to one which doesn't make a number appear a class type.
                        // Replace `.`s before numbers with `$`s, like would be present in the
                        // compiled type.
                        val workaroundQualifiedName =
                            psiType.computeQualifiedName().replace(Regex("\\.(\\d)"), "\\$$1")
                        val workaroundPsiType = globalContext.createPsiType(workaroundQualifiedName)
                        createTypeItem(workaroundPsiType, kotlinType, contextNullability)
                    } else {
                        val classType =
                            createClassTypeItem(
                                psiType = psiType,
                                kotlinType = kotlinType,
                                contextNullability = contextNullability,
                            )
                        checkForTypeAliasSubstitution(classType, contextNullability) ?: classType
                    }
                }
            }
            is PsiWildcardType ->
                createWildcardTypeItem(
                    psiType = psiType,
                    kotlinType = kotlinType,
                )
            // There are other [PsiType]s, but none can appear in API surfaces.
            else ->
                throw IllegalStateException(
                    "Invalid type in API surface: $psiType${
                    if (kotlinType != null) {
                        val location = PsiFileLocation.fromPsiElement(kotlinType.context)
                        " for element ${location.baselineKey?.elementId()}" +
                            " in file ${location.path}:${location.line}"
                    } else ""
                }"
                )
        }
    }

    /**
     * Checks if there are is a type alias matching the name of the [classTypeItem]. If there is,
     * converts the aliased type for this usage (mapping type parameters and adjusting nullability).
     *
     * If there is no matching type alias, returns null.
     */
    private fun checkForTypeAliasSubstitution(
        classTypeItem: ClassTypeItem,
        contextNullability: ContextNullability
    ): TypeItem? {
        // Don't bother checking for type aliases in non-KMP codebases because the substitution will
        // already have happened in the UAST representation. Substitution won't have happened for
        // expect/actual type aliases used from a common source set because the type is platform
        // dependent. Metalava is just modeling the android/jvm platform, so the substitution needs
        // to happen here.
        if (!codebase.inlineTypeAliasUsages) return null

        val typeAlias = codebase.findTypeAlias(classTypeItem.qualifiedName) ?: return null

        // Map type parameters of the type alias, if applicable.
        val convertedType =
            if (typeAlias.typeParameterList.isEmpty()) {
                typeAlias.aliasedType
            } else {
                val typeParameterBindings =
                    typeAlias.typeParameterList.zip(classTypeItem.arguments).toMap()
                typeAlias.aliasedType.convertType(typeParameterBindings)
            }

        // Update type nullability: if the context requires a specific nullability, use that.
        // If the aliased type is nullable, that propagates to the usage. Otherwise, use the
        // nullability set by the usage site.
        val nullability =
            contextNullability.forcedNullability
                ?: if (typeAlias.aliasedType.modifiers.isNullable) {
                    TypeNullability.NULLABLE
                } else {
                    classTypeItem.modifiers.nullability
                }

        return convertedType.substitute(nullability)
    }

    /** Create a [PrimitiveTypeItem]. */
    private fun createPrimitiveTypeItem(
        psiType: PsiPrimitiveType,
        kotlinType: KotlinTypeInfo?,
    ) =
        TypeItem.createPrimitiveType(
            modifiers = createTypeModifiers(psiType, kotlinType, ContextNullability.forceNonNull),
            kind = getKind(psiType),
            isValueClassType = kotlinType.isValueClassTypeIfAvailable,
        )

    /** Get the [PrimitiveTypeItem.Primitive] enum from the [PsiPrimitiveType]. */
    private fun getKind(type: PsiPrimitiveType): PrimitiveTypeItem.Primitive {
        return when (type) {
            PsiTypes.booleanType() -> PrimitiveTypeItem.Primitive.BOOLEAN
            PsiTypes.byteType() -> PrimitiveTypeItem.Primitive.BYTE
            PsiTypes.charType() -> PrimitiveTypeItem.Primitive.CHAR
            PsiTypes.doubleType() -> PrimitiveTypeItem.Primitive.DOUBLE
            PsiTypes.floatType() -> PrimitiveTypeItem.Primitive.FLOAT
            PsiTypes.intType() -> PrimitiveTypeItem.Primitive.INT
            PsiTypes.longType() -> PrimitiveTypeItem.Primitive.LONG
            PsiTypes.shortType() -> PrimitiveTypeItem.Primitive.SHORT
            PsiTypes.voidType() -> PrimitiveTypeItem.Primitive.VOID
            else ->
                throw java.lang.IllegalStateException(
                    "Invalid primitive type in API surface: $type"
                )
        }
    }

    /**
     * Get the [KotlinTypeInfo.isValueClassType] information from [KotlinTypeInfo], defaulting to
     * `false` if the [KotlinTypeInfo] is `null`.
     */
    private val KotlinTypeInfo?.isValueClassTypeIfAvailable
        get() = this?.isValueClassType() == true

    /** Create an [ArrayTypeItem]. */
    private fun createArrayTypeItem(
        psiType: PsiArrayType,
        kotlinType: KotlinTypeInfo?,
        contextNullability: ContextNullability,
    ) =
        TypeItem.createArrayType(
            modifiers = createTypeModifiers(psiType, kotlinType, contextNullability),
            componentType =
                createTypeItem(
                    psiType.componentType,
                    kotlinType?.forArrayComponentType(),
                    //  Pass in the [ContextNullability.forComponentType] just in case this is the
                    // return type of an annotation method, or in other words the type of an
                    // annotation attribute.
                    contextNullability.forComponentType(),
                ),
            isVarargs = psiType is PsiEllipsisType,
            isValueClassType = kotlinType.isValueClassTypeIfAvailable,
        )

    /** Create a [ClassTypeItem]. */
    private fun createClassTypeItem(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo?,
        contextNullability: ContextNullability,
    ): ClassTypeItem {
        val outerClassType =
            computeOuterClass(
                psiType,
                kotlinType,
            )

        val qualifiedName =
            if (outerClassType == null) psiType.computeQualifiedName()
            else {
                "${outerClassType.qualifiedName}.${psiType.name}"
            }

        return TypeItem.createClassType(
            modifiers = createTypeModifiers(psiType, kotlinType, contextNullability),
            qualifiedName = qualifiedName,
            arguments =
                computeTypeArguments(
                    psiType,
                    kotlinType,
                ),
            outerClassType = outerClassType,
            isValueClassType = kotlinType.isValueClassTypeIfAvailable,
        )
    }

    /** Compute the [ClassTypeItem.arguments]. */
    private fun computeTypeArguments(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo?,
    ): List<TypeArgumentTypeItem> {
        return psiType.parameters.toList().mapIndexed { i, param ->
            val forTypeArgument = kotlinType?.forTypeArgument(i)
            createTypeItem(param, forTypeArgument) as TypeArgumentTypeItem
        }
    }

    /** Compute the [ClassTypeItem.outerClassType], which will be `null` if no type exists. */
    private fun computeOuterClass(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo?,
    ): ClassTypeItem? {
        // Try and construct a qualifying PsiType for psiType. If the result resolves to a class
        // then it is an outer class, otherwise it could be a package or an unknown class. That will
        // be handled below.
        val outerPsiType =
            when (psiType) {
                // PsiClassReferenceType are class type references, either in sources or possibly
                // binary files. It contains a single PsiJavaCodeReferenceElement that may contain
                // a qualifying reference, annotations and type parameters.
                is PsiClassReferenceType -> {
                    // Get the children of the reference.
                    val children = psiType.reference.children

                    // If this type is qualified then it will contain a PsiJavaCodeReferenceElement
                    // as the first child  that encapsulates the qualifier, including any
                    // annotations and type parameters it may have.
                    val javaCodeReference = children.firstOrNull() as? PsiJavaCodeReferenceElement
                    if (javaCodeReference != null) {
                        // Construct a PsiClassReferenceType around the qualifying reference. This
                        // could be a package or outer class so it will need to be checked first.
                        PsiClassReferenceType(javaCodeReference, psiType.languageLevel)
                    } else {
                        // There is no outer class mentioned in the source reference so cannot
                        // create an outer class type from it. However, that does not mean that
                        // there is no outer class type so drop through and try another way.
                        null
                    }
                }
                else -> {
                    // Drop through and try another way to create the type.
                    null
                }
            }

        // If an outer PsiType was constructed, and it could be resolved then it is a known class
        // so construct a ClassTypeItem for it.
        if (outerPsiType != null && outerPsiType.resolve() != null) {
            return createClassTypeItem(
                psiType = outerPsiType,
                kotlinType = kotlinType?.forOuterClass(),
                // Outer class types can never be null.
                contextNullability = ContextNullability.forceNonNull,
            )
        }

        return PsiNameHelper.getOuterClassReference(psiType.canonicalText).let { outerClassName ->
            // [PsiNameHelper.getOuterClassReference] returns an empty string if there is no
            // outer class reference. If the type is not a nested type, it returns the package
            // name (e.g. for "java.lang.String" it returns "java.lang").
            //
            // Only create a class if the outer class name does not look like a class name.
            if (isClassByConvention(outerClassName)) {
                val psiOuterClassType =
                    globalContext.createPsiType(
                        outerClassName,
                        // The context psi element allows variable types to be resolved (with no
                        // context, they would be interpreted as class types). The [psiContext]
                        // works in most cases, but is null when creating a type directly from a
                        // class declaration, so the resolved [psiType] provides context then.
                        psiType.psiContext ?: psiType.resolve()
                    )
                createTypeItem(
                    psiOuterClassType,
                    kotlinType?.forOuterClass(),
                    // An outer class reference can't be null.
                    contextNullability = ContextNullability.forceNonNull,
                )
                    as ClassTypeItem
            } else {
                null
            }
        }
    }

    /** Support mapping from boxed types back to their primitive type. */
    private val boxedToPsiPrimitiveType =
        mapOf(
            "java.lang.Byte" to PsiTypes.byteType(),
            "java.lang.Double" to PsiTypes.doubleType(),
            "java.lang.Float" to PsiTypes.floatType(),
            "java.lang.Integer" to PsiTypes.intType(),
            "java.lang.Long" to PsiTypes.longType(),
            "java.lang.Short" to PsiTypes.shortType(),
            "java.lang.Boolean" to PsiTypes.booleanType(),
            // This is not strictly speaking a boxed -> unboxed mapping, but it fits in nicely
            // with the others.
            "kotlin.Unit" to PsiTypes.voidType(),
        )

    /** If the type item is not nullable and is a boxed type then map it to the unboxed type. */
    private fun unboxTypeWherePossible(typeItem: TypeItem): TypeItem {
        if (
            typeItem is ClassTypeItem && typeItem.modifiers.nullability == TypeNullability.NONNULL
        ) {
            boxedToPsiPrimitiveType[typeItem.qualifiedName]?.let { psiPrimitiveType ->
                return createPrimitiveTypeItem(psiPrimitiveType, null)
            }
        }
        return typeItem
    }

    /** An input parameter of type X is represented as a "? super X" in the `Function<X>` class. */
    private fun unwrapInputType(typeItem: TypeItem): TypeItem {
        return unboxTypeWherePossible((typeItem as? WildcardTypeItem)?.superBound ?: typeItem)
    }

    /**
     * The return type of type X can be represented as a "? extends X" in the `Function<X>` class.
     */
    private fun unwrapOutputType(typeItem: TypeItem): TypeItem {
        return unboxTypeWherePossible((typeItem as? WildcardTypeItem)?.extendsBound ?: typeItem)
    }

    /**
     * Create a [LambdaTypeItem].
     *
     * Extends a [ClassTypeItem] and then deconstructs the type arguments of Kotlin `Function<N>` to
     * extract the receiver, input and output types. This makes heavy use of the
     * [org.jetbrains.kotlin.analysis.api.types.KaType] of [kotlinType] which must be a
     * [org.jetbrains.kotlin.analysis.api.types.KaFunctionType]. That has the information necessary
     * to determine which of the Kotlin `Function<N>` class's type arguments are the receiver (if
     * any) and which are input parameters. The last type argument is always the return type.
     */
    private fun createLambdaTypeItem(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo,
        contextNullability: ContextNullability,
    ): LambdaTypeItem {
        val qualifiedName = psiType.computeQualifiedName()

        val actualKotlinType = kotlinType.asLambdaType()

        // Get the type arguments for the kotlin.jvm.functions.Function<X> class.
        val typeArguments = computeTypeArguments(psiType, actualKotlinType)

        // If the function has a receiver then it is the first type argument.
        var firstParameterTypeIndex = 0
        val receiverType =
            if (actualKotlinType.hasReceiver) {
                // The first parameter type is now the second type argument.
                firstParameterTypeIndex = 1
                unwrapInputType(typeArguments[0])
            } else {
                null
            }

        // The last type argument is always the return type.
        val returnType = unwrapOutputType(typeArguments.last())
        val lastParameterTypeIndex = typeArguments.size - 1

        // Get the parameter types, excluding the optional receiver and the return type.
        val parameterTypes =
            typeArguments
                .subList(firstParameterTypeIndex, lastParameterTypeIndex)
                .map { unwrapInputType(it) }
                .toList()

        // Verify that the lambda is being implemented using the expected classes.
        require(
            qualifiedName.startsWith(KOTLIN_FUNCTION_PREFIX) ||
                qualifiedName == KOTLIN_REFLECT_FUNCTION
        ) {
            "internal error: Kotlin lambda implemented using unexpected class '$qualifiedName'."
        }

        return TypeItem.createLambdaType(
            modifiers = createTypeModifiers(psiType, actualKotlinType, contextNullability),
            qualifiedName = qualifiedName,
            arguments = typeArguments,
            // Lambdas are implemented using a number of top level classes so never have an outer
            // class.
            outerClassType = null,
            isSuspend = actualKotlinType.isSuspend,
            receiverType = receiverType,
            parameterTypes = parameterTypes,
            returnType = returnType,
            isValueClassType = kotlinType.isValueClassTypeIfAvailable,
        )
    }

    /** Create a [VariableTypeItem]. */
    private fun createVariableTypeItem(
        psiType: PsiClassType,
        kotlinType: KotlinTypeInfo?,
        typeParameterItem: TypeParameterItem,
        contextNullability: ContextNullability,
    ) =
        TypeItem.createVariableType(
            modifiers =
                createTypeModifiers(psiType, kotlinType, contextNullability.forTypeVariable()),
            asTypeParameter = typeParameterItem,
            isValueClassType = kotlinType.isValueClassTypeIfAvailable,
        )

    /** Create a [WildcardTypeItem]. */
    private fun createWildcardTypeItem(
        psiType: PsiWildcardType,
        kotlinType: KotlinTypeInfo?,
    ) =
        TypeItem.createWildcardType(
            modifiers = createTypeModifiers(psiType, kotlinType, ContextNullability.forceUndefined),
            extendsBound =
                createBound(
                    psiType.extendsBound,
                    // The kotlinType only applies to an explicit bound, not an implicit bound, so
                    // only pass it through if this has an explicit `extends` bound.
                    kotlinType.takeIf { psiType.isExtends },
                    // If this is a Kotlin wildcard type with an implicit Object extends bound, the
                    // Object bound should be nullable, not platform nullness like in Java.
                    contextNullability =
                        if (kotlinType != null && !psiType.isExtends) {
                            ContextNullability(TypeNullability.NULLABLE)
                        } else {
                            ContextNullability.none
                        }
                ),
            superBound =
                createBound(
                    psiType.superBound,
                    // The kotlinType only applies to an explicit bound, not an implicit bound, so
                    // only pass it through if this has an explicit `super` bound.
                    kotlinType.takeIf { psiType.isSuper },
                ),
            isValueClassType = kotlinType.isValueClassTypeIfAvailable,
        )

    /**
     * Create a [WildcardTypeItem.extendsBound] or [WildcardTypeItem.superBound].
     *
     * If a [PsiWildcardType] doesn't have a bound, the bound is represented as the null [PsiType]
     * instead of just `null`.
     */
    private fun createBound(
        bound: PsiType,
        kotlinType: KotlinTypeInfo?,
        contextNullability: ContextNullability = ContextNullability.none
    ): ReferenceTypeItem? {
        return if (bound == PsiTypes.nullType()) {
            null
        } else {
            // Use the same Kotlin type, because the wildcard isn't its own level in the KtType.
            createTypeItem(bound, kotlinType, contextNullability) as ReferenceTypeItem
        }
    }
}

/** Compute the qualified name for a [PsiClassType].. */
internal fun PsiClassType.computeQualifiedName(): String {
    // It should be possible to do `psiType.rawType().canonicalText` instead, but this does not
    // always work if psi is unable to resolve the reference.
    // See https://youtrack.jetbrains.com/issue/KTIJ-27093 for more details.
    return PsiNameHelper.getQualifiedClassName(canonicalText, true)
}

/** Prefix of Kotlin JVM function types, used for lambdas. */
private const val KOTLIN_FUNCTION_PREFIX = "kotlin.jvm.functions.Function"

/** Prefix of Kotlin reflect function types, used for lambdas. */
private const val KOTLIN_REFLECT_FUNCTION = "kotlin.reflect.KFunction"
