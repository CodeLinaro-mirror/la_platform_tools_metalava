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
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.asAnnotationAttributeValue
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.value.AnnotationValue
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.model.value.CachingAnnotationValueProvider
import com.android.tools.metalava.model.value.CachingValueProvider
import com.android.tools.metalava.model.value.ClassObjectValue
import com.android.tools.metalava.model.value.CombinedValueProvider
import com.android.tools.metalava.model.value.ConstantValue
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.ImplementationValueToModelFactory
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
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypes
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.UastQualifiedExpressionAccessType

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
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] of [typeItem] from
     * [anyValue].
     *
     * @param typeItem the required type for the value, e.g. [MethodItem.returnType] or
     *   [FieldItem.type].
     * @param anyValue the underlying Psi specific value. It is of type [Any] to avoid having to
     *   duplicate everything for [UExpression] and [PsiAnnotationMemberValue].
     * @param valueUseSite the [ValueUseSite] for which this will provide a [Value].
     */
    fun providerFor(
        typeItem: TypeItem,
        anyValue: Any,
        valueUseSite: ValueUseSite
    ): CombinedValueProvider = CachingValueProvider(this, typeItem, anyValue, valueUseSite)

    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] for attribute
     * [attributeName] of [annotationItem] from [anyValue].
     *
     * @param annotationItem the containing [AnnotationItem].
     * @param attributeName the name of the attribute whose value it will provide.
     * @param anyValue the underlying Psi specific value. It is of type [Any] to avoid having to
     *   duplicate everything for [UExpression] and [PsiAnnotationMemberValue].
     */
    fun providerForAnnotationValue(
        annotationItem: AnnotationItem,
        attributeName: String,
        anyValue: Any
    ): CombinedValueProvider =
        CachingAnnotationValueProvider(this, annotationItem, attributeName, anyValue)

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
        return when (valueUseSite) {
            ValueUseSite.ANNOTATION -> {
                // For annotations convert to any Value.
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

                if (value == null) {
                    unknownExpression(optionalTypeItem, implementationValue)
                }
                value
            }
            ValueUseSite.FIELD -> {
                // For fields convert to ConstantValues.
                when (implementationValue) {
                    is UExpression -> {
                        uExpressionToConstant(optionalTypeItem, implementationValue)
                    }
                    is PsiAnnotationMemberValue -> {
                        psiToConstant(optionalTypeItem, implementationValue)
                    }
                    else -> null
                }
            }
        }
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
                createArrayValue(listOf(singleValue))
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

                // Try and resolve it and convert to a value.
                uResolvableToValue(optionalTypeItem, uExpression)?.let {
                    return it
                }
            }
            // Handle an unqualified reference, i.e. one of the form <identifier>.
            is USimpleNameReferenceExpression -> {
                // Try and resolve it and convert to a value.
                uResolvableToValue(optionalTypeItem, uExpression)?.let {
                    return it
                }
            }
            is UClassLiteralExpression -> {
                uClassLiteralExpressionToClassObjectValue(uExpression)?.let {
                    return it
                }
            }
            is UCallExpression -> {
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

        return createClassObjectValue(classLiteralTypeItem)
    }

    /** Try and convert a [UResolvable] to an [ArrayElementValue]. */
    private fun uResolvableToValue(
        optionalTypeItem: TypeItem?,
        uResolvable: UResolvable
    ): ArrayElementValue? {
        // Resolve it and convert it to a Value if possible.
        val resolved = uResolvable.resolve()

        // Try and convert the resolved PsiElement to a Value and return it if succeeded.
        resolvedPsiElementToValue(optionalTypeItem, resolved)?.let {
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
            // Iterate over the parameters of the annotation constructor as this needs to get the
            // parameter name for each argument to use as the attribute name and its type for use
            // to guide conversion.
            resolved.psiParameters.mapNotNull { psiParameter ->
                val index = psiParameter.parameterIndex()
                // Get the argument for this parameter, if no argument is provided then ignore the
                // parameter.
                val uArgument = uExpression.getArgumentForParameter(index) ?: return@mapNotNull null

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
                    value.asAnnotationAttributeValue(),
                )
            }

        val annotationItem =
            DefaultAnnotationItem.createAttributesLazily(
                codebase,
                FileLocation.UNKNOWN,
                qualifiedClassName,
            ) {
                attributesProvider()
            }

        return createAnnotationValue(annotationItem!!)
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
                // Check to see if the underlying value has been already been cast from the source
                // literal type to a type appropriate for where it is being used. If it has then
                // reverse the cast to preserve the information about the source literal type. That
                // is needed to enable consistent processing with legacy value handling which often
                // uses the source type directly, e.g. when parsing `longValue = 1` it may write it
                // as `longValue = 1` instead of the more consistent `longValue = 1L`.
                val transformedValue =
                    if (underlyingValue is Long) {
                        uExpression.sourcePsi?.text?.let { text ->
                            // If the text ends with `L` or `l` then it was a long literal so keep
                            // it as such.
                            if (text.endsWith("L") || text.endsWith("l")) underlyingValue
                            else {
                                // Otherwise, try and see if it can be cast to an int without loss.
                                // If it can then use the int, otherwise keep the long.
                                val asInt = underlyingValue.toInt()
                                if (asInt.toLong() == underlyingValue) asInt else underlyingValue
                            }
                        } ?: underlyingValue
                    } else underlyingValue
                return createLiteralValue(optionalTypeItem, transformedValue)
            }
        }

        // All others expressions are evaluated to a literal, if possible and returned.
        ConstantEvaluator.evaluate(null, uExpression)?.let { value ->
            return createLiteralValue(optionalTypeItem, value)
        }

        // An unknown expression was found so return null and the caller will handle as needed.
        return null
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
                        createArrayValue(listOf(singleValue))
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

                return createClassObjectValue(classLiteralTypeItem)
            }
            // Field reference.
            is PsiReferenceExpression -> {
                val resolved = psiValue.resolve()
                // Try and convert the resolved PsiElement to a Value and return it if succeeded.
                resolvedPsiElementToValue(optionalTypeItem, resolved)?.let {
                    return it
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
            return createLiteralValue(optionalTypeItem, value)
        }

        // Temporarily fall through to use PsiConstantEvaluationHelper
        // TODO(b/408445860): Remove once ConstantEvaluator can handle the necessary cases.
        val javaPsiFacade = JavaPsiFacade.getInstance(codebase.project)
        javaPsiFacade.constantEvaluationHelper.computeConstantExpression(psiValue)?.let { value ->
            return createLiteralValue(optionalTypeItem, value)
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
    ): ArrayElementValue? {
        if (resolved is PsiField) {
            codebase.findField(resolved)?.let { fieldItem ->
                return createFieldReferenceValue(optionalTypeItem, fieldItem)
            }
        }

        return null
    }
}
