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

package com.android.tools.metalava.model.testing

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.BaseTypeVisitor
import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.JAVA_LANG_STRING
import com.android.tools.metalava.model.LambdaTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.SkeletonTypeParameterItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeStringConfiguration
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.item.DefaultTypeParameterItem

/**
 * The default [TypeStringConfiguration] that [testTypeString] uses to obtain the defaults for its
 * parameters to avoid duplicating them.
 */
private val DEFAULT = TypeStringConfiguration.DEFAULT

/**
 * Convenience method to simplify testing.
 *
 * @see [TypeStringConfiguration] for information on the parameters.
 */
fun TypeItem.testTypeString(
    annotations: Boolean = DEFAULT.annotations,
    kotlinStyleNulls: Boolean = DEFAULT.kotlinStyleNulls,
): String =
    toTypeString(
        TypeStringConfiguration(
            annotations = annotations,
            kotlinStyleNulls = kotlinStyleNulls,
        )
    )

/** Create a [PrimitiveTypeItem] for [kind]. */
fun primitiveTypeForKind(
    kind: Primitive,
    isValueClassType: Boolean = false,
    annotations: List<AnnotationItem> = emptyList(),
): PrimitiveTypeItem =
    TypeItem.createPrimitiveType(
        TypeModifiers.create(annotations, TypeNullability.NONNULL),
        kind,
        isValueClassType,
    )

/** Create a [ClassTypeItem] for [JAVA_LANG_STRING]. */
fun stringType(
    isValueClassType: Boolean = false,
    nullability: TypeNullability = TypeNullability.NONNULL,
    annotations: List<AnnotationItem> = emptyList(),
): ClassTypeItem =
    classTypeItem(
        JAVA_LANG_STRING,
        isValueClassType = isValueClassType,
        nullability = nullability,
        annotations = annotations,
    )

/** Create a [ClassTypeItem] for [qualifiedName] with [arguments] inside [outerClassType]. */
fun classTypeItem(
    qualifiedName: String,
    arguments: List<TypeArgumentTypeItem> = emptyList(),
    outerClassType: ClassTypeItem? = null,
    isValueClassType: Boolean = false,
    nullability: TypeNullability = TypeNullability.NONNULL,
    annotations: List<AnnotationItem> = emptyList(),
): ClassTypeItem =
    TypeItem.createClassType(
        TypeModifiers.create(annotations, nullability),
        qualifiedName,
        arguments,
        outerClassType,
        isValueClassType,
    )

/** Create a [LambdaTypeItem]. */
fun lambdaTypeItem(
    receiverType: TypeItem? = null,
    parameterTypes: List<TypeItem> = emptyList(),
    returnType: TypeItem = primitiveTypeForKind(Primitive.VOID),
    isSuspend: Boolean = false,
    arguments: List<TypeArgumentTypeItem>? = null,
    isValueClassType: Boolean = false,
): LambdaTypeItem {
    val arity = (if (receiverType == null) 0 else 1) + parameterTypes.size
    val (qualifiedName, typeArguments) =
        if (arity > LambdaTypeItem.MAX_SPECIFIC_FUNCTION_ARITY) {
            "kotlin.jvm.functions.FunctionN" to (arguments ?: listOf(returnType.asTypeArgument()))
        } else {
            "kotlin.jvm.functions.Function$arity" to
                (arguments
                    ?: buildList(arity + 1) {
                        receiverType?.let { add(it.asTypeArgument()) }
                        parameterTypes.mapTo(this) { it.asTypeArgument() }
                        add(returnType.asTypeArgument())
                    })
        }
    return TypeItem.createLambdaType(
        modifiers = TypeModifiers.emptyNonNullModifiers,
        qualifiedName = qualifiedName,
        arguments = typeArguments,
        outerClassType = null,
        isSuspend = isSuspend,
        receiverType = receiverType,
        parameterTypes = parameterTypes,
        returnType = returnType,
        isValueClassType = isValueClassType,
    )
}

private fun TypeItem.asTypeArgument(): TypeArgumentTypeItem =
    when (this) {
        is PrimitiveTypeItem ->
            classTypeItem(
                if (kind == Primitive.VOID) "kotlin.Unit" else kind.wrapperClass.canonicalName
            )
        is TypeArgumentTypeItem -> this
        else -> error("Unexpected type $this ($javaClass)")
    }

/** Create a [ArrayTypeItem] for [componentType]. */
fun arrayTypeItem(
    componentType: TypeItem,
    isVarargs: Boolean = false,
    isValueClassType: Boolean = false,
    nullability: TypeNullability = TypeNullability.NONNULL,
    annotations: List<AnnotationItem> = emptyList(),
): ArrayTypeItem =
    TypeItem.createArrayType(
        TypeModifiers.create(annotations, nullability),
        componentType,
        isVarargs,
        isValueClassType,
    )

/** Create a [VariableTypeItem] for [typeParameterItem]. */
fun variableTypeItem(
    typeParameterItem: TypeParameterItem,
    isValueClassType: Boolean = false,
    nullability: TypeNullability = TypeNullability.NONNULL,
    annotations: List<AnnotationItem> = emptyList(),
): VariableTypeItem =
    TypeItem.createVariableType(
        TypeModifiers.create(annotations, nullability),
        typeParameterItem,
        isValueClassType,
    )

/** Create a [VariableTypeItem] for a [TypeParameterItem] called [name]. */
fun variableTypeItem(
    name: String,
    isValueClassType: Boolean = false,
    nullability: TypeNullability = TypeNullability.NONNULL,
    annotations: List<AnnotationItem> = emptyList(),
): VariableTypeItem =
    variableTypeItem(
        typeParameterItem(name),
        isValueClassType = isValueClassType,
        nullability = nullability,
        annotations = annotations,
    )

/** Create a [WildcardTypeItem] for [extendsBound] of [superBound] . */
fun wildcardTypeItem(
    extendsBound: ReferenceTypeItem? = null,
    superBound: ReferenceTypeItem? = null,
    annotations: List<AnnotationItem> = emptyList(),
): WildcardTypeItem =
    TypeItem.createWildcardType(
        TypeModifiers.create(annotations, TypeNullability.UNDEFINED),
        extendsBound,
        superBound,
    )

/** Create a [TypeParameterItem] called [name] with [bounds]. */
fun typeParameterItem(
    name: String,
    bounds: List<BoundsTypeItem> = emptyList(),
): SkeletonTypeParameterItem =
    DefaultTypeParameterItem(DefaultModifierList.create(0), name, isReified = false).also {
        it.bounds = bounds
    }

/** Force the resolving of all [ClassTypeItem]s in this [TypeItem]. */
fun TypeItem.forceResolveClasses(classResolver: ClassResolver) =
    accept(
        object : BaseTypeVisitor() {
            override fun visitClassType(classType: ClassTypeItem) {
                classType.resolveClass(classResolver)
            }
        }
    )
