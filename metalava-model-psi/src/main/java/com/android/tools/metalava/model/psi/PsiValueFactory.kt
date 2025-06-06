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

package com.android.tools.metalava.model.psi

import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.DefaultPrimitiveTypeItem
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.android.tools.metalava.model.value.AnnotationValue
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.model.value.BaseCachingDeferredTypeValueProvider
import com.android.tools.metalava.model.value.CachingValueProvider
import com.android.tools.metalava.model.value.ClassObjectValue
import com.android.tools.metalava.model.value.CombinedValueProvider
import com.android.tools.metalava.model.value.ConstantValue
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.ImplementationValueToModelFactory
import com.android.tools.metalava.model.value.LiteralValue
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueFactory
import com.android.tools.metalava.model.value.ValueProvider
import com.android.tools.metalava.model.value.ValueProviderException
import com.android.tools.metalava.model.value.ValueUseSite
import com.android.tools.metalava.model.value.provider
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypes
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.UastQualifiedExpressionAccessType
import org.jetbrains.uast.getParameterForArgument

/**
 * Creates [ValueProvider]s that will delegate to [implementationValueToModelValue] to create
 * [Value]s when requested.
 *
 * @param globalTypeItemFactory the global [PsiTypeItemFactory] used for creating [TypeItem]s for
 *   [ClassObjectValue]s. It uses the global factory as the types will never be [VariableTypeItem]s
 *   and so there is no need to use a factory that has access to the in scope type parameters.
 */
internal class PsiValueFactory(
    private val codebase: PsiBasedCodebase,
    private val globalTypeItemFactory: PsiTypeItemFactory,
) : ValueFactory, ImplementationValueToModelFactory<Any> {
    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] of [optionalTypeItem]
     * from [anyValue].
     *
     * @param optionalTypeItem the optional type for the value, e.g. [MethodItem.returnType] (for
     *   attribute or attribute default values) or [FieldItem.type].
     * @param anyValue the underlying Psi specific value. It is of type [Any] to avoid having to
     *   duplicate everything for [UExpression] and [PsiAnnotationMemberValue].
     * @param valueUseSite the [ValueUseSite] for which this will provide a [Value].
     */
    fun providerFor(
        optionalTypeItem: TypeItem?,
        anyValue: Any,
        valueUseSite: ValueUseSite
    ): CombinedValueProvider = CachingValueProvider(this, optionalTypeItem, anyValue, valueUseSite)

    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] for attribute
     * [attributeName] of [annotationPsiClass] from [anyValue].
     *
     * @param annotationPsiClass the optional [PsiClass].
     * @param attributeName the name of the attribute whose value it will provide.
     * @param anyValue the underlying Psi specific value. It is of type [Any] to avoid having to
     *   duplicate everything for [UExpression] and [PsiAnnotationMemberValue].
     */
    fun providerForAnnotationValue(
        annotationPsiClass: PsiClass?,
        attributeName: String,
        anyValue: Any
    ): CombinedValueProvider =
        annotationPsiClass?.let {
            PsiCachingAnnotationValueProvider(
                this,
                anyValue,
                globalTypeItemFactory,
                annotationPsiClass,
                attributeName
            )
        } ?: providerFor(null, anyValue, ValueUseSite.ANNOTATION)

    /**
     * Create a [Value] of [optionalTypeItem] from [implementationValue].
     *
     * Uses [Any] to avoid having to duplicate everything for [UExpression] and
     * [PsiAnnotationMemberValue].
     */
    override fun implementationValueToModelValue(
        optionalTypeItem: TypeItem?,
        implementationValue: Any,
        valueUseSite: ValueUseSite,
    ): Value? {
        val value =
            when (implementationValue) {
                is UExpression -> {
                    uExpressionToValue(optionalTypeItem, implementationValue)
                }
                is PsiAnnotationMemberValue -> {
                    psiToValue(optionalTypeItem, implementationValue)
                }
                else -> null
            }

        // If no value could be created, and it is for an annotation attribute then fail as an
        // annotation attribute MUST always have a value.
        if (value == null && valueUseSite == ValueUseSite.ANNOTATION) {
            unknownExpression(optionalTypeItem, implementationValue)
        }

        return value
    }

    /**
     * An unknown [expression] of [optionalTypeItem] was found and it was not possible to return
     * `null` so throw an exception.
     */
    private fun unknownExpression(optionalTypeItem: TypeItem?, expression: Any): Nothing {
        throw ValueProviderException(
            "Unknown value '$expression' of ${expression.javaClass} for type $optionalTypeItem"
        )
    }

    /** Create a [Value] of [optionalTypeItem] from [uExpression]. */
    private fun uExpressionToValue(
        optionalTypeItem: TypeItem?,
        uExpression: UExpression,
    ): Value? {
        if (
            uExpression is UCallExpression &&
                uExpression.kind == UastCallKind.NESTED_ARRAY_INITIALIZER
        ) {
            val arrayTypeItem = optionalTypeItem as? ArrayTypeItem
            val elementType = arrayTypeItem?.componentType
            val elements =
                uExpression.valueArguments.map {
                    uExpressionToArrayElementValue(elementType, it)
                        ?: unknownExpression(elementType, it)
                }
            return createArrayValue(elements)
        }

        return if (optionalTypeItem is ArrayTypeItem) {
            // The type is an array so this is an example of not having to add curly braces around a
            // single value in an annotation attribute. Create a value for the component type and
            // then wrap it in an ArrayValue.
            uExpressionToArrayElementValue(optionalTypeItem.componentType, uExpression)?.let {
                singleValue ->
                createArrayValue(listOf(singleValue), wasUnwrappedInSource = true)
            }
        } else {
            uExpressionToArrayElementValue(optionalTypeItem, uExpression)
        }
    }

    /** Create an [ArrayElementValue] of [optionalTypeItem] from [uExpression]. */
    private fun uExpressionToArrayElementValue(
        optionalTypeItem: TypeItem?,
        uExpression: UExpression
    ): ArrayElementValue? {
        when (uExpression) {
            // Handle a qualified reference, i.e. one of the form <receiver>.<selector>.
            is UQualifiedReferenceExpression -> {
                // Check to see if it is a class literal and if so then create a ClassObjectValue
                // and return it, otherwise drop through.
                uReferenceExpressionToClassObjectValue(uExpression)?.let {
                    return it
                }

                val receiver = uExpression.receiver

                // Check to see if the receiver could be resolved to a class. If it could then pass
                // it in below as it may affect the result.
                val receiverPsiClass = (receiver as? UReferenceExpression)?.resolve() as? PsiClass

                // Try and resolve it and convert to a value.
                uResolvableToValue(optionalTypeItem, uExpression, receiverPsiClass)?.let {
                    return it
                }

                // Ignore any other access type than a simple '.'.
                if (uExpression.accessType == UastQualifiedExpressionAccessType.SIMPLE) {
                    // The `receiver` is the qualifier and the `selector` is what is being
                    // qualified.
                    when (val selector = uExpression.selector) {
                        is UCallExpression -> {
                            // Nested annotations are represented as a call to an annotation class
                            // constructor so check to see if that is the case.
                            uCallExpressionToAnnotationValue(selector)?.let {
                                return it
                            }

                            // Check to see whether the call is to a numeric conversion function.
                            val explicitConversionTo = selector.isNumericConversionFunction()
                            if (explicitConversionTo != null) {
                                //  Deconstruct the call to if it is being called on a field
                                //  reference. If it is then create a field reference for it. This
                                // handles two cases, e.g.:
                                //     qualified.FIELD.toLong()
                                //     UNQUALIFIED.toLong()
                                if (receiver is UReferenceExpression) {
                                    // Try and resolve it and convert to a value.
                                    uResolvableToValue(
                                            optionalTypeItem,
                                            receiver,
                                            receiverPsiClass,
                                            explicitConversionTo
                                        )
                                        ?.let {
                                            return it
                                        }
                                }
                            }
                        }
                        is USimpleNameReferenceExpression -> {
                            // Handle an unknown, unresolvable field.
                            val receiverText = receiver.asRenderString()
                            val selectorText = selector.asRenderString()
                            return createFieldReferenceValue(codebase, receiverText, selectorText)
                        }
                    }
                }
            }
            // Handle an unqualified reference, i.e. one of the form <identifier>.
            is USimpleNameReferenceExpression -> {
                // Try and resolve it and convert to a value.
                uResolvableToValue(optionalTypeItem, uExpression)?.let {
                    return it
                }

                // Handle an unknown, unresolvable field.
                return createFieldReferenceValue(codebase, "", uExpression.identifier)
            }
            is UClassLiteralExpression -> {
                uClassLiteralExpressionToClassObjectValue(uExpression)?.let {
                    return it
                }
            }
            is UCallExpression -> {
                // Nested annotations are represented as a call to an annotation class constructor
                // so check to see if that is the case.
                uCallExpressionToAnnotationValue(uExpression)?.let {
                    return it
                }
            }
        }

        // All others drop through.
        return uExpressionToConstant(optionalTypeItem, uExpression)
    }

    /**
     * Checks to see if [uExpression] is of the form `<type>::class.java`, if not it returns null
     * otherwise it creates a [ClassObjectValue] for it.
     *
     * In this case `<type>` can be either a primitive, a normal class, or an array (possibly
     * multidimensional) of them.
     */
    private fun uReferenceExpressionToClassObjectValue(
        uExpression: UQualifiedReferenceExpression
    ): ClassObjectValue? {
        // Check for the SIMPLE access type, i.e. ".", in `<class>::class.java`
        if (uExpression.accessType != UastQualifiedExpressionAccessType.SIMPLE) return null

        // Check for the `java` part.
        val selector = uExpression.selector
        // TODO(b/354633349): Support javaPrimitiveType and javaObjectType too?
        if (selector !is USimpleNameReferenceExpression || selector.identifier != "java")
            return null

        // Check to make sure the receiver is the `<class>::class` part.
        val receiver = uExpression.receiver as? UClassLiteralExpression ?: return null
        return uClassLiteralExpressionToClassObjectValue(receiver)
    }

    /**
     * Checks to see if [uExpression] is of the form `<type>::class`, if not it returns null
     * otherwise it creates a [ClassObjectValue] for it.
     *
     * In this case `<type>` can be either a primitive, a normal class, or an array (possibly
     * multidimensional) of them.
     */
    private fun uClassLiteralExpressionToClassObjectValue(
        uExpression: UClassLiteralExpression
    ): ClassObjectValue? {
        // Make sure the type is present.
        val type = uExpression.type ?: return null

        // Get the type of the class literal. e.g. if the expression was `X::class` then this
        // will be of type `X`, or if the expression was of type `Array<X>.class` then this will
        // be of type `X[]`. `X` may be a primitive type.
        val receiverTypeItem =
            globalTypeItemFactory.getType(
                type,
                contextNullability = ContextNullability.forceNonNull,
            )

        val unboxedTypeItem = unboxTypeItemIfNeeded(receiverTypeItem, uExpression)

        // If it is a ClassTypeItem then make sure it does not have any arguments. It is not
        // necessary to check array components as Kotlin does not support class literals for arrays
        // of generic classes, e.g. `Array<List<*>>::class`.
        val classLiteralTypeItem =
            if (unboxedTypeItem is ClassTypeItem)
                unboxedTypeItem.substitute(arguments = emptyList())
            else unboxedTypeItem

        return createClassObjectValue(classLiteralTypeItem, uExpression.asSourceString())
    }

    /** Try and convert a [UResolvable] to an [ArrayElementValue]. */
    private fun uResolvableToValue(
        optionalTypeItem: TypeItem?,
        uResolvable: UResolvable,
        receiverPsiClass: PsiClass? = null,
        explicitConversionTo: Primitive? = null,
    ): ArrayElementValue? {
        // Resolve it and convert it to a Value if possible.
        val resolved = uResolvable.resolve()

        // Try and convert the resolved PsiElement to a Value and return it if succeeded.
        resolvedPsiElementToValue(
                optionalTypeItem,
                resolved,
                receiverPsiClass,
                explicitConversionTo
            )
            ?.let {
                return it
            }

        return null
    }

    /**
     * Both Kotlin "primitive" types and their corresponding Java wrapper class will use the wrapper
     * class as their type, e.g. `Int::class.java` and `Integer::class.java` will both have a type
     * `Class<Integer>`. So, use clues from the source [receiver] to choose the correct one.
     */
    private fun unboxTypeItemIfNeeded(
        receiverTypeItem: PsiTypeItem,
        receiver: UClassLiteralExpression
    ): TypeItem {
        if (receiverTypeItem !is ClassTypeItem) return receiverTypeItem
        val expression =
            receiver.expression as? USimpleNameReferenceExpression ?: return receiverTypeItem
        val primitiveKind =
            Primitive.forWrapperClassName(receiverTypeItem.qualifiedName) ?: return receiverTypeItem

        if (expression.identifier != primitiveKind.kotlinName) return receiverTypeItem

        val psiType =
            when (primitiveKind) {
                Primitive.BOOLEAN -> PsiTypes.booleanType()
                Primitive.BYTE -> PsiTypes.byteType()
                Primitive.CHAR -> PsiTypes.charType()
                Primitive.DOUBLE -> PsiTypes.doubleType()
                Primitive.FLOAT -> PsiTypes.floatType()
                Primitive.INT -> PsiTypes.intType()
                Primitive.LONG -> PsiTypes.longType()
                Primitive.SHORT -> PsiTypes.shortType()
                Primitive.VOID -> PsiTypes.voidType()
            }

        return globalTypeItemFactory.getType(
            psiType,
            contextNullability = ContextNullability.forceNonNull
        )
    }

    /**
     * Create an [AnnotationValue] from [uExpression] if possible, otherwise return `null`.
     *
     * @param uExpression a call to an annotation class's constructor.
     */
    private fun uCallExpressionToAnnotationValue(uExpression: UCallExpression): AnnotationValue? {
        // Annotations are created using constructor calls.
        if (uExpression.kind != UastCallKind.CONSTRUCTOR_CALL) return null

        // Resolve the call to the constructor, return null if it cannot be resolved.
        val resolved = uExpression.resolve()
        if (resolved !is PsiMethod || !resolved.isConstructor) return null

        // Get the qualified name of the constructor class, return null if it is not available.
        val psiClass = resolved.containingClass
        val qualifiedClassName = psiClass?.qualifiedName ?: return null

        fun attributesProvider() =
            // Iterate over the arguments as the order in which they are specified is important.
            uExpression.valueArguments.mapNotNull { uArgument ->

                // Get the parameter for this argument, if no parameter is provided then ignore the
                // argument.
                val psiParameter =
                    uExpression.getParameterForArgument(uArgument) ?: return@mapNotNull null

                // Get the name and type from the parameter.
                val name = psiParameter.name
                val typeItem = globalTypeItemFactory.getType(psiParameter.type)

                // Create a value from the expression. This needs to be done immediately so that
                // asAnnotationAttributeValue() call below can differentiate between an ArrayValue
                // (which needs to be converted to an AnnotationArrayAttributeValue) and other
                // values.
                val value =
                    uExpressionToArrayElementValue(typeItem, uArgument)
                        ?: unknownExpression(typeItem, uArgument)
                DefaultAnnotationAttribute(
                    name,
                    value.provider(),
                )
            }

        val annotationItem =
            DefaultAnnotationItem.createAttributesLazily(
                codebase,
                FileLocation.UNKNOWN,
                qualifiedClassName,
                ::attributesProvider,
            )

        return createAnnotationValue(annotationItem!!)
    }

    /**
     * Check to see whether this [UCallExpression] is for a Kotlin numeric conversion function, e.g.
     * `toLong()`.
     */
    private fun UCallExpression.isNumericConversionFunction(): Primitive? {
        // Casts are represented as method calls.
        if (kind != UastCallKind.METHOD_CALL) return null

        // Cast methods have no arguments
        if (valueArgumentCount != 0) return null

        val methodName = methodName ?: return null
        return Primitive.forKotlinNumericConversionFunctionName(methodName)
    }

    /** Create a [ConstantValue] of [optionalTypeItem] from [uExpression]. */
    private fun uExpressionToConstant(
        optionalTypeItem: TypeItem?,
        uExpression: UExpression
    ): ConstantValue? {
        // If the type is supplied, and it's not a constant type then return immediately as this can
        // never be treated as a constant value. If it is not supplied then drop through and check
        // the actual value, if any.
        if (optionalTypeItem != null && !optionalTypeItem.isConstantType()) {
            return null
        }

        if (uExpression is ULiteralExpression) {
            uExpression.value?.let { underlyingValue ->
                // Get the original source value, undoing any int -> long conversions done by K2.
                val originalSourceValue =
                    when (underlyingValue) {
                        // Byte and short always use an integer literal as there are no byte or
                        // short literals in Kotlin. That is true whether they are signed or
                        // unsigned.
                        is Byte -> underlyingValue.toInt()
                        is Short -> underlyingValue.toInt()
                        is UByte -> underlyingValue.toInt()
                        is UShort -> underlyingValue.toInt()
                        is Long -> undoConversionOfSourceIntIfNeeded(underlyingValue, uExpression)
                        else -> underlyingValue
                    }

                // TODO(b/420371817): Work around an issue in Psi which prevents the class of the
                //   @setparam:Anno from being resolved which means that optionalTypeItem is null
                //   even though the underlying Psi code knows the type and has cast the integer
                //   literal to a `long`. The workaround synthesizes an optionalTypeItem of `long`
                //   based on the fact that the `underlyingValue` is `long`. It does not handle the
                //   other types as they are not needed at the moment.
                val actualPrimitiveKind =
                    if (optionalTypeItem == null && underlyingValue != originalSourceValue)
                        when (underlyingValue) {
                            is Byte -> Primitive.BYTE
                            is Long -> Primitive.LONG
                            is Short -> Primitive.SHORT
                            else -> null
                        }
                    else null

                val actualTypeItem =
                    actualPrimitiveKind?.let { kind ->
                        DefaultPrimitiveTypeItem(DefaultTypeModifiers.emptyNonNullModifiers, kind)
                    } ?: optionalTypeItem

                return uLiteralValue(actualTypeItem, originalSourceValue)
            }
        }

        // All others expressions are evaluated to a literal, if possible and returned.
        ConstantEvaluator.evaluate(null, uExpression)?.let { value ->
            // Get the original source value, undoing any int -> long conversions done by K2. This
            // is only done for unary minus expressions, i.e. of the form `-<expr>`.
            val originalSourceValue =
                if (
                    uExpression is UPrefixExpression &&
                        uExpression.operator == UastPrefixOperator.UNARY_MINUS &&
                        value is Long
                ) {
                    undoConversionOfSourceIntIfNeeded(value, uExpression)
                } else {
                    value
                }

            return uLiteralValue(optionalTypeItem, originalSourceValue, nonLiteralInSource = true)
        }

        // An unknown expression was found so return null and the caller will handle as needed.
        return null
    }

    /**
     * Checks to see if the underlying value has been already been converted from the source literal
     * type to a type appropriate for where it is being used; if it has then it undo the conversion
     * to preserve the information about the source literal type.
     *
     * That is needed to enable consistent processing with legacy value handling which often uses
     * the source type directly, e.g. when parsing `longValue = 1` it may write it as `longValue =
     * 1` instead of the more consistent `longValue = 1L`.
     *
     * This generally only affects K2 as K1 does not bother casting to the correct type.
     */
    private fun undoConversionOfSourceIntIfNeeded(
        underlyingValue: Long,
        uExpression: UExpression,
    ) =
        uExpression.sourcePsi?.text?.let { text ->
            // If the text ends with `L` or `l` then it was a long literal so keep it as
            // such.
            if (text.endsWith("L") || text.endsWith("l")) underlyingValue
            else {
                // Otherwise, try and see if it can be cast to an int without loss. If it
                // can then use the int, otherwise keep the original value.
                val asInt = underlyingValue.toInt()
                if (asInt.toLong() == underlyingValue) asInt else underlyingValue
            }
        } ?: underlyingValue

    /**
     * Create a [LiteralValue] from a [value].
     *
     * Handles mapping Kotlin unsigned value to the equivalent Java signed value.
     */
    private fun uLiteralValue(
        optionalTypeItem: TypeItem?,
        value: Any,
        nonLiteralInSource: Boolean = false,
    ): LiteralValue<*> {
        // Convert unsigned to signed values. It would be cleaner if these could just be treated
        // like another Number class as then they could be handled as part of the normalization done
        // by `createLiteralValue(...)` but unfortunately, the unsigned types are not Numbers.
        val transformedValue =
            when (value) {
                is UByte -> value.toByte()
                is UInt -> value.toInt()
                is ULong -> value.toLong()
                is UShort -> value.toShort()
                else -> value
            }

        return createLiteralValue(optionalTypeItem, transformedValue, nonLiteralInSource)
    }

    /** Create a [Value] of [optionalTypeItem] from [psiValue]. */
    private fun psiToValue(
        optionalTypeItem: TypeItem?,
        psiValue: PsiAnnotationMemberValue,
    ) =
        when (psiValue) {
            // Array literal.
            is PsiArrayInitializerMemberValue -> {
                val arrayTypeItem = optionalTypeItem as? ArrayTypeItem
                val elementType = arrayTypeItem?.componentType
                val elements =
                    psiValue.initializers.map {
                        psiToArrayElementValue(elementType, it)
                            ?: unknownExpression(elementType, it)
                    }
                createArrayValue(elements)
            }
            is PsiNewExpression -> {
                // New expressions cannot be used with annotations (they use array literals) and if
                // they are used with fields they always return a `null` value so just return
                // immediately. This avoids issues when dealing with expressions like `field = new
                // int[0]` which end up being evaluated in [psiToConstant] to an array or an Android
                // Lint specific type.
                null
            }
            else -> {
                if (optionalTypeItem is ArrayTypeItem) {
                    // The type is an array so this is an example of not having to add curly braces
                    // around a single value in an annotation attribute. Create a value for the
                    // component type and then wrap it in an ArrayValue.
                    psiToArrayElementValue(optionalTypeItem.componentType, psiValue)?.let {
                        singleValue ->
                        createArrayValue(listOf(singleValue), wasUnwrappedInSource = true)
                    }
                } else {
                    psiToArrayElementValue(optionalTypeItem, psiValue)
                }
            }
        }

    /** Create an [ArrayElementValue] of [optionalTypeItem] from [psiValue]. */
    private fun psiToArrayElementValue(
        optionalTypeItem: TypeItem?,
        psiValue: PsiAnnotationMemberValue,
    ): ArrayElementValue? {
        when (psiValue) {
            // Class literal, e.g. `SomeClass.class`.
            is PsiClassObjectAccessExpression -> {
                // Get the type of the class literal. e.g. if the expression was `X.class` then this
                // will be of type `X`, or if the expression was of type `X[].class` then this will
                // be of type `X[]`. `X` may be a primitive type.
                val classLiteralTypeItem =
                    globalTypeItemFactory.getType(
                        psiValue.operand.type,
                        contextNullability = ContextNullability.forceNonNull,
                    )

                return createClassObjectValue(
                    classLiteralTypeItem,
                    sourceExpression = psiValue.text,
                )
            }
            // Field reference.
            is PsiReferenceExpression -> {
                val resolved = psiValue.resolve()
                // Try and convert the resolved PsiElement to a Value and return it if succeeded.
                resolvedPsiElementToValue(optionalTypeItem, resolved)?.let {
                    return it
                }

                // Handle an unknown, unresolvable field.
                val qualifierText = psiValue.qualifierExpression?.text ?: ""
                val referenceName = psiValue.referenceName
                if (referenceName != null) {
                    return createFieldReferenceValue(codebase, qualifierText, referenceName)
                }
            }
            is PsiLiteral -> {
                val underlyingPsiValue = psiValue.value
                if (underlyingPsiValue is Pair<*, *>) {
                    // Needed for field reference in some special Kotlin annotations, e.g.
                    // @file:RestrictTo(RestrictTo.Scope.LIBRARY).
                    val (first, second) = underlyingPsiValue
                    if (first is ClassId && second is Name) {
                        val qualifiedClassName = first.asFqNameString()
                        val fieldName = second.asString()

                        return createFieldReferenceValueWithDeferredConstantValue(
                            codebase,
                            qualifiedClassName,
                            fieldName,
                            optionalTypeItem,
                        )
                    }
                }
            }
            // An annotation value.
            is PsiAnnotation -> {
                PsiAnnotationItem.create(codebase, psiValue)?.let { annotationItem ->
                    return createAnnotationValue(annotationItem)
                }
            }
        }

        // All others drop through.
        return psiToConstant(optionalTypeItem, psiValue)
    }

    /** Create a [ConstantValue] of [optionalTypeItem] from [psiValue]. */
    private fun psiToConstant(
        optionalTypeItem: TypeItem?,
        psiValue: PsiAnnotationMemberValue,
    ): ConstantValue? {
        // If the type is supplied, and it's not a constant type then return immediately as this can
        // never be treated as a constant value. If it is not supplied then drop through and check
        // the actual value, if any.
        if (optionalTypeItem != null && !optionalTypeItem.isConstantType()) {
            return null
        }

        // Literal primitive or String.
        if (psiValue is PsiLiteralExpression) {
            return psiValue.value?.let { underlyingValue ->
                createLiteralValue(optionalTypeItem, underlyingValue)
            }
        }

        // All others expressions are evaluated to a literal, if possible and returned.
        ConstantEvaluator.evaluate(null, psiValue)?.let { value ->
            return createLiteralValue(
                optionalTypeItem,
                value,
                nonLiteralInSource = true,
            )
        }

        // Temporarily fall through to use PsiConstantEvaluationHelper
        // TODO(b/408445860): Remove once ConstantEvaluator can handle the necessary cases.
        val javaPsiFacade = JavaPsiFacade.getInstance(codebase.project)
        javaPsiFacade.constantEvaluationHelper.computeConstantExpression(psiValue)?.let { value ->
            return createLiteralValue(
                optionalTypeItem,
                value,
                nonLiteralInSource = true,
            )
        }

        // An unknown expression was found so return null and the caller will handle as needed.
        return null
    }

    /**
     * Try and convert the [resolved] [PsiElement] to an [ArrayElementValue].
     *
     * If [resolved] is a [PsiField] and it is not an enum constant then it will call
     * [FieldItem.constantValue] to find the [ConstantValue] for the [FieldReferenceValue].
     */
    private fun resolvedPsiElementToValue(
        optionalTypeItem: TypeItem?,
        resolved: PsiElement?,
        receiverPsiClass: PsiClass? = null,
        explicitConversionTo: Primitive? = null,
    ): ArrayElementValue? {
        if (resolved is PsiField) {
            val qualifiedClassName = resolved.containingClass?.qualifiedName ?: ""
            val fieldName = resolved.name

            val kotlinCompanionClass =
                if (receiverPsiClass !== resolved.containingClass)
                    receiverPsiClass?.qualifiedNameIfCompanionClass()
                else null

            return createFieldReferenceValueWithDeferredConstantValue(
                codebase,
                qualifiedClassName,
                fieldName,
                optionalTypeItem,
                kotlinCompanionClass,
                explicitConversionTo,
            )
        }

        return null
    }

    /**
     * Returns [PsiClass.getQualifiedName] if this is a companion class.
     *
     * This must have been resolved from a [UQualifiedReferenceExpression.receiver].
     */
    private fun PsiClass.qualifiedNameIfCompanionClass(): String? =
        if (isCompanion()) qualifiedName else null

    /**
     * Returns `true` if this is a companion class.
     *
     * This uses [PsiModifierItem.create] to avoid having to duplicate the code that deals with the
     * Psi object model.
     */
    private fun PsiClass.isCompanion() = PsiModifierItem.create(codebase, this).isCompanion()
}

/**
 * A [BaseCachingDeferredTypeValueProvider] that is used for annotation attribute values.
 *
 * It will attempt to find the [optionalTypeItem] by looking for the attribute method called
 * [attributeName] in [annotationPsiClass] and if found, converting its return type to a [TypeItem]
 * using [globalTypeItemFactory].
 */
private class PsiCachingAnnotationValueProvider(
    factory: ImplementationValueToModelFactory<Any>,
    implementationValue: Any,
    private val globalTypeItemFactory: PsiTypeItemFactory,
    private val annotationPsiClass: PsiClass,
    private val attributeName: String,
) :
    BaseCachingDeferredTypeValueProvider<Any>(
        factory,
        implementationValue,
        ValueUseSite.ANNOTATION,
    ) {

    override fun optionalTypeItem() =
        annotationPsiClass
            // Find the attribute method.
            .methods
            .firstOrNull { it.name == attributeName }
            // If found then convert its return type to a TypeItem.
            ?.let { psiMethod ->
                psiMethod.returnType?.let { psiType ->
                    globalTypeItemFactory.getType(
                        psiType,
                        psiMethod,
                        ContextNullability.forceNonNull
                    )
                }
            }
}
