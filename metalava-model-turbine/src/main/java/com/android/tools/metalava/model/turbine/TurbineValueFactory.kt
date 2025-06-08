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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.model.value.BaseCachingDeferredTypeValueProvider
import com.android.tools.metalava.model.value.CachingValueProvider
import com.android.tools.metalava.model.value.CombinedValueProvider
import com.android.tools.metalava.model.value.ConstantValue
import com.android.tools.metalava.model.value.ImplementationValueToModelFactory
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueFactory
import com.android.tools.metalava.model.value.ValueProviderException
import com.android.tools.metalava.model.value.ValueUseSite
import com.google.turbine.binder.bound.EnumConstantValue
import com.google.turbine.binder.bound.TurbineAnnotationValue
import com.google.turbine.binder.bound.TurbineClassValue
import com.google.turbine.binder.bound.TypeBoundClass
import com.google.turbine.model.Const
import com.google.turbine.model.Const.ArrayInitValue
import com.google.turbine.model.TurbineConstantTypeKind
import com.google.turbine.tree.Tree
import com.google.turbine.tree.Tree.ArrayInit
import com.google.turbine.tree.Tree.ConstVarName
import com.google.turbine.tree.Tree.Expression

/**
 * Factory for creating [Value]s from [TurbineValue]s.
 *
 * @param globalContext provides access to some global context needed by this.
 */
internal class TurbineValueFactory(globalContext: TurbineGlobalContext) :
    ValueFactory,
    ImplementationValueToModelFactory<TurbineValue>,
    TurbineGlobalContext by globalContext {
    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] of [optionalTypeItem]
     * from [turbineValue].
     *
     * @param optionalTypeItem the optional type for the value, e.g. [MethodItem.returnType] (for
     *   attribute or attribute default values) or [FieldItem.type].
     * @param turbineValue the underlying Turbine value.
     * @param valueUseSite the [ValueUseSite] for which this will provide a [Value].
     */
    fun providerFor(
        optionalTypeItem: TypeItem?,
        turbineValue: TurbineValue,
        valueUseSite: ValueUseSite,
    ): CombinedValueProvider =
        CachingValueProvider(this, optionalTypeItem, turbineValue, valueUseSite)

    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] for attribute
     * [attributeName] of [annotationClass] from [turbineValue].
     *
     * @param annotationClass the optional [TypeBoundClass].
     * @param attributeName the name of the attribute whose value it will provide.
     * @param turbineValue the underlying Turbine value.
     */
    fun providerForAnnotationValue(
        annotationClass: TypeBoundClass?,
        attributeName: String,
        turbineValue: TurbineValue
    ): CombinedValueProvider =
        if (annotationClass == null) {
            // If no annotationClass could be found then just use a normal provider with a `null`
            // optionalTypeItem.
            providerFor(null, turbineValue, ValueUseSite.ANNOTATION)
        } else {
            // Otherwise, create a provider that will get the attribute's type if possible.
            TurbineCachingAnnotationValueProvider(
                this,
                turbineValue,
                globalTypeItemFactory,
                annotationClass,
                attributeName,
            )
        }

    override fun implementationValueToModelValue(
        optionalTypeItem: TypeItem?,
        implementationValue: TurbineValue,
        valueUseSite: ValueUseSite,
    ) = implementationValue.toValue(optionalTypeItem)

    /** Create a [Value] of [optionalTypeItem] from this [TurbineValue]. */
    private fun TurbineValue.toValue(optionalTypeItem: TypeItem?): Value {
        if (const is ArrayInitValue) {
            val arrayTypeItem = optionalTypeItem as ArrayTypeItem
            val elementTypeItem = arrayTypeItem.componentType

            val elements = const.elements()
            val exprElements = (expr as? ArrayInit)?.exprs()
            val turbineValues =
                elements.mapIndexed { index, element ->
                    TurbineValue(element, exprElements?.get(index), fieldResolver)
                }

            val values = turbineValues.map { it.toArrayElementValue(elementTypeItem) }

            // If the source was a single non-array expression of an array type then that needs to
            // be passed to the `ArrayValue`. Turbine has automatically wrapped that in an
            // `ArrayInitValue` so check the expression. If the expression was provided (i.e. from
            // sources not jars) but was not an `ArrayInit` expression (no `exprElements) then it
            // was unwrapped in the sources, otherwise it was not.
            val wasUnwrappedInSource = expr != null && exprElements == null

            return createArrayValue(values, wasUnwrappedInSource)
        }

        return if (optionalTypeItem is ArrayTypeItem) {
            // The type is an array so this is an example of not having to add curly braces around a
            // single value in an annotation attribute. Create a value for the component type and
            // then wrap it in an ArrayValue.
            val singleValue = toArrayElementValue(optionalTypeItem.componentType)
            createArrayValue(listOf(singleValue), wasUnwrappedInSource = true)
        } else {
            toArrayElementValue(optionalTypeItem)
        }
    }

    /** Create an [ArrayElementValue] of [optionalTypeItem] from this [TurbineValue]. */
    private fun TurbineValue.toArrayElementValue(optionalTypeItem: TypeItem?): ArrayElementValue {
        when (const.kind()) {
            Const.Kind.CLASS_LITERAL -> {
                const as TurbineClassValue
                // Get the type of the class literal. e.g. if the expression was `X.class` then this
                // will be of type `X`, or if the expression was of type `X[].class` then this will
                // be of type `X[]`. `X` may be a primitive type.
                val classLiteralTypeItem =
                    globalTypeItemFactory.createType(
                        const.type(),
                        isVarArg = false,
                        ContextNullability.forceNonNull
                    )

                return createClassObjectValue(
                    classLiteralTypeItem,
                    sourceExpression = expr?.toString(),
                )
            }
            Const.Kind.ANNOTATION -> {
                const as TurbineAnnotationValue
                val annotation = annotationFactory.createAnnotation(const.info(), fieldResolver)!!
                return createAnnotationValue(annotation)
            }
            Const.Kind.ENUM_CONSTANT -> {
                const as EnumConstantValue
                // Create an EnumConstantValue for the underlying Turbine EnumConstantValue.
                val fieldSymbol = const.sym()
                return createFieldReferenceValue(
                    codebase,
                    fieldSymbol.owner().qualifiedName,
                    fieldSymbol.name(),
                )
            }
            else -> {}
        }

        // Check for a field reference if a field resolver is available.
        if (expr != null && expr is ConstVarName && fieldResolver != null) {
            val fieldInfo = fieldResolver.resolveField(expr)
            val fieldSymbol = fieldInfo?.sym()
            // If the field could be resolved then wrap it around the constant value.
            if (fieldSymbol != null) {
                return createFieldReferenceValueWithDeferredConstantValue(
                    codebase,
                    fieldSymbol.owner().qualifiedName,
                    fieldSymbol.name(),
                    optionalTypeItem,
                )
            }
        }

        return toConstant(optionalTypeItem)
    }

    /** Create a [ConstantValue] of [optionalTypeItem] from this [TurbineValue]. */
    private fun TurbineValue.toConstant(optionalTypeItem: TypeItem?): ConstantValue {
        if (const.kind() == Const.Kind.PRIMITIVE) {
            val underlyingValue = (const as Const.Value).value

            // If no expr is provided then this comes from a .class file, otherwise it comes from
            // the source.
            if (expr == null) {
                // A .class file stores byte and short constants as ints so convert them back from
                // the Turbine value (which has been converted to the correct type) to the behavior
                // relied upon by Psi legacy behavior.
                val transformedValue =
                    when (underlyingValue) {
                        is Byte -> underlyingValue.toInt()
                        is Short -> underlyingValue.toInt()
                        else -> underlyingValue
                    }

                return createLiteralValue(optionalTypeItem, transformedValue)
            } else {
                // Check to see if the underlying value has been already been converted from the
                // source literal type to a type appropriate for where it is being used. If it has
                // then this undoes the conversion to preserve the information about the source
                // literal type. That is needed to enable consistent processing with legacy value
                // handling which often uses the source type directly, e.g. when parsing
                //     `longValue = 1`
                // it may write it as
                //     `longValue = 1`
                // instead of the more consistent
                //     `longValue = 1L`.
                val transformedValue =
                    when (underlyingValue) {
                        is Byte,
                        is Double,
                        is Float,
                        is Long,
                        is Short -> {
                            when (expr.getLiteralKind()) {
                                TurbineConstantTypeKind.INT -> {
                                    (underlyingValue as Number).toInt()
                                }
                                TurbineConstantTypeKind.FLOAT -> {
                                    (underlyingValue as Number).toFloat()
                                }
                                else -> underlyingValue
                            }
                        }
                        else -> underlyingValue
                    }

                // A value is considered non-literal if it was not a literal expression.
                val nonLiteralInSource = expr !is Tree.Literal
                return createLiteralValue(optionalTypeItem, transformedValue, nonLiteralInSource)
            }
        }

        throw ValueProviderException(
            "Unknown value '$const' of ${const.javaClass} for type $optionalTypeItem"
        )
    }

    /**
     * Get the literal kind of this expression.
     *
     * If this is itself a [Tree.Literal] then return its [Tree.Literal.tykind]. Otherwise, if this
     * is a [Tree.Unary], e.g. `-<expr>` of `+<expr>`, then it will call this on its
     * [Tree.Unary.expr].
     */
    private fun Expression.getLiteralKind(): TurbineConstantTypeKind? =
        when (this) {
            is Tree.Literal -> this.tykind()
            is Tree.Unary -> expr().getLiteralKind()
            else -> null
        }
}

/**
 * A [BaseCachingDeferredTypeValueProvider] that is used for annotation attribute values.
 *
 * It will attempt to find the [optionalTypeItem] by looking for the attribute method called
 * [attributeName] in [annotationClass] and if found, converting its return type to a [TypeItem]
 * using [globalTypeItemFactory].
 */
private class TurbineCachingAnnotationValueProvider(
    factory: ImplementationValueToModelFactory<TurbineValue>,
    implementationValue: TurbineValue,
    private val globalTypeItemFactory: TurbineTypeItemFactory,
    private val annotationClass: TypeBoundClass,
    private val attributeName: String,
) :
    BaseCachingDeferredTypeValueProvider<TurbineValue>(
        factory,
        implementationValue,
        ValueUseSite.ANNOTATION,
    ) {

    override fun optionalTypeItem() =
        annotationClass
            // Try and find the attribute method.
            .methods()
            .firstOrNull { it.name() == attributeName }
            // If found then convert its return type to a TypeItem.
            ?.returnType()
            ?.let { type -> globalTypeItemFactory.getGeneralType(type) }
}
