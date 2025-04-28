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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.model.value.CachingAnnotationValueProvider
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
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.model.Const
import com.google.turbine.model.Const.ArrayInitValue
import com.google.turbine.model.TurbineConstantTypeKind
import com.google.turbine.tree.Tree
import com.google.turbine.tree.Tree.ArrayInit
import com.google.turbine.tree.Tree.ConstVarName
import com.google.turbine.type.Type

internal class TurbineValueFactory(private val globalContext: TurbineGlobalContext) :
    ValueFactory,
    ImplementationValueToModelFactory<TurbineValue>,
    TurbineGlobalContext by globalContext {
    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] of [typeItem] from
     * [turbineValue].
     *
     * @param typeItem the required type for the value, e.g. [MethodItem.returnType] or
     *   [FieldItem.type].
     * @param turbineValue the underlying Turbine value.
     * @param valueUseSite the [ValueUseSite] for which this will provide a [Value].
     */
    fun providerFor(
        typeItem: TypeItem,
        turbineValue: TurbineValue,
        valueUseSite: ValueUseSite,
    ): CombinedValueProvider = CachingValueProvider(this, typeItem, turbineValue, valueUseSite)

    /**
     * Get a [CombinedValueProvider] that will create (and cache) a [Value] for attribute
     * [attributeName] of [annotationItem] from [turbineValue].
     *
     * @param annotationItem the containing [AnnotationItem].
     * @param attributeName the name of the attribute whose value it will provide.
     * @param turbineValue the underlying Turbine value.
     */
    fun providerForAnnotationValue(
        annotationItem: AnnotationItem,
        attributeName: String,
        turbineValue: TurbineValue
    ): CombinedValueProvider =
        CachingAnnotationValueProvider(
            this,
            annotationItem,
            attributeName,
            turbineValue,
        )

    override fun implementationValueToModelValue(
        optionalTypeItem: TypeItem?,
        implementationValue: TurbineValue,
        valueUseSite: ValueUseSite,
    ) =
        when (valueUseSite) {
            ValueUseSite.ANNOTATION -> {
                // For annotations convert to any Value.
                implementationValue.toValue(optionalTypeItem)
            }
            ValueUseSite.FIELD -> {
                // For fields convert to ConstantValues.
                implementationValue.toConstant(optionalTypeItem)
            }
        }

    /** Create a [Value] of [optionalTypeItem] from this [TurbineValue]. */
    private fun TurbineValue.toValue(optionalTypeItem: TypeItem?): Value {
        if (const is ArrayInitValue) {
            val arrayTypeItem = optionalTypeItem as ArrayTypeItem
            val elementTypeItem = arrayTypeItem.componentType

            val elements = const.elements()
            val exprElements = (expr as? ArrayInit)?.exprs()
            val turbineValues =
                elements.mapIndexed { index, element ->
                    TurbineValue(element, exprElements?.get(index))
                }

            val values = turbineValues.map { it.toArrayElementValue(elementTypeItem) }
            return createArrayValue(values)
        }

        return if (optionalTypeItem is ArrayTypeItem) {
            // The type is an array so this is an example of not having to add curly braces around a
            // single value in an annotation attribute. Create a value for the component type and
            // then wrap it in an ArrayValue.
            val singleValue = toArrayElementValue(optionalTypeItem.componentType)
            createArrayValue(listOf(singleValue))
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

                return createClassObjectValue(classLiteralTypeItem)
            }
            Const.Kind.ANNOTATION -> {
                const as TurbineAnnotationValue
                val annotation = annotationFactory.createAnnotation(const.info())!!
                return createAnnotationValue(annotation)
            }
            Const.Kind.ENUM_CONSTANT -> {
                const as EnumConstantValue
                // Create an EnumConstantValue for the underlying Turbine EnumConstantValue.
                val fieldSymbol = const.sym()
                return createEnumConstantValue(
                    fieldSymbol.owner().classTypeItem(),
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
                // Get the constant value first.
                val constantValue = toConstant(optionalTypeItem)

                return createConstantFieldValue(
                    fieldSymbol.owner().classTypeItem(),
                    fieldSymbol.name(),
                    constantValue,
                )
            }
        }

        return toConstant(optionalTypeItem)
    }

    /** Get a [ClassTypeItem] for this [ClassSymbol]. */
    private fun ClassSymbol.classTypeItem(): ClassTypeItem {
        // Create a raw type for this ClassSymbol.
        val rawClassType: Type.ClassTy = Type.ClassTy.asNonParametricClassTy(this)
        // Construct a ClassTypeItem from it.
        return globalTypeItemFactory.getClassReferenceType(rawClassType)
    }

    /** Create a [ConstantValue] of [optionalTypeItem] from this [TurbineValue]. */
    private fun TurbineValue.toConstant(optionalTypeItem: TypeItem?): ConstantValue {
        if (const.kind() == Const.Kind.PRIMITIVE) {
            // Check to see if the underlying value has been already been cast from the source
            // literal type to a type appropriate for where it is being used. If it has then reverse
            // the cast to preserve the information about the source literal type. That is needed to
            // enable consistent processing with legacy value handling which often uses the source
            // type directly, e.g. when parsing `longValue = 1` it may write it as `longValue = 1`
            // instead of the more consistent `longValue = 1L`.
            val transformedValue =
                when (val underlyingValue = (const as Const.Value).value) {
                    is Double,
                    is Float,
                    is Long -> {
                        if (expr is Tree.Literal && expr.tykind() == TurbineConstantTypeKind.INT) {
                            expr.toString().toInt()
                        } else underlyingValue
                    }
                    else -> underlyingValue
                }
            return createLiteralValue(optionalTypeItem, transformedValue)
        }

        throw ValueProviderException(
            "Unknown value '$const' of ${const.javaClass} for type $optionalTypeItem"
        )
    }
}
