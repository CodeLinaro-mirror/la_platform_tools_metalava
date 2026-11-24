/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.LambdaTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem

interface InternalTypeItemFactory {
    /** Create an [ArrayTypeItem]. */
    fun createArrayType(
        modifiers: TypeModifiers,
        componentType: TypeItem,
        isVarargs: Boolean,
        isValueClassType: Boolean = false,
    ): ArrayTypeItem =
        DefaultArrayTypeItem(
            modifiers,
            componentType,
            isVarargs,
            isValueClassType,
        )

    /** Create a [ClassTypeItem]. */
    fun createClassType(
        modifiers: TypeModifiers,
        qualifiedName: String,
        arguments: List<TypeArgumentTypeItem>,
        outerClassType: ClassTypeItem?,
        isValueClassType: Boolean = false,
    ): ClassTypeItem =
        DefaultClassTypeItem(
            modifiers,
            qualifiedName,
            arguments,
            outerClassType,
            isValueClassType,
        )

    /** Create a [ClassTypeItem] for [ClassItem]. */
    fun createClassTypeForClassItem(classItem: ClassItem): ClassTypeItem {
        val arguments = classItem.typeParameterList.map { it.type() }
        val modifiers = TypeModifiers.emptyNonNullModifiers
        return createClassType(
            modifiers,
            classItem.qualifiedName(),
            arguments,
            classItem.outerClassType,
        )
    }

    /** Create a [LambdaTypeItem]. */
    fun createLambdaType(
        modifiers: TypeModifiers,
        qualifiedName: String,
        arguments: List<TypeArgumentTypeItem>,
        outerClassType: ClassTypeItem?,
        isSuspend: Boolean,
        receiverType: TypeItem?,
        parameterTypes: List<TypeItem>,
        returnType: TypeItem,
        isValueClassType: Boolean = false,
    ): LambdaTypeItem =
        createLambdaType(
            isSuspend = isSuspend,
            receiverType = receiverType,
            parameterTypes = parameterTypes,
            returnType = returnType,
            jvmClassType =
                createClassType(
                    modifiers,
                    qualifiedName,
                    arguments,
                    outerClassType,
                    isValueClassType,
                ),
        )

    /** Create a [LambdaTypeItem]. */
    fun createLambdaType(
        isSuspend: Boolean,
        receiverType: TypeItem?,
        parameterTypes: List<TypeItem>,
        returnType: TypeItem,
        jvmClassType: ClassTypeItem,
    ): LambdaTypeItem =
        DefaultLambdaTypeItem(
            isSuspend = isSuspend,
            receiverType = receiverType,
            parameterTypes = parameterTypes,
            returnType = returnType,
            jvmClassType = jvmClassType,
        )

    /** Create a [PrimitiveTypeItem]. */
    fun createPrimitiveType(
        modifiers: TypeModifiers,
        kind: Primitive,
        isValueClassType: Boolean = false,
    ): PrimitiveTypeItem =
        if (modifiers.annotations.isEmpty() && !isValueClassType) {
            // Use one of the pre-cached instances.
            primitiveTypes[kind.ordinal]
        } else {
            DefaultPrimitiveTypeItem(
                // Force primitives to be non-null.
                modifiers.substitute(nullability = TypeNullability.NONNULL),
                kind,
                isValueClassType,
            )
        }

    /** Create a [VariableTypeItem]. */
    fun createVariableType(
        modifiers: TypeModifiers,
        asTypeParameter: TypeParameterItem,
        isValueClassType: Boolean = false,
    ): VariableTypeItem =
        DefaultVariableTypeItem(
            modifiers,
            asTypeParameter,
            isValueClassType,
        )

    /** Create a [WildcardTypeItem]. */
    fun createWildcardType(
        modifiers: TypeModifiers,
        extendsBound: ReferenceTypeItem?,
        superBound: ReferenceTypeItem?,
    ): WildcardTypeItem =
        DefaultWildcardTypeItem(
            modifiers,
            extendsBound,
            superBound,
        )

    companion object {
        /** A cache of non-null [PrimitiveTypeItem]s indexed by [Primitive.ordinal]. */
        private val primitiveTypes = run {
            val kinds = Primitive.entries
            Array<PrimitiveTypeItem>(kinds.size) { ordinal ->
                DefaultPrimitiveTypeItem(TypeModifiers.emptyNonNullModifiers, kinds[ordinal])
            }
        }
    }
}

/**
 * Get the [ClassTypeItem] for this [ClassItem] to use as the [ClassTypeItem.outerClassType] for a
 * nested [ClassItem] of this.
 */
private val ClassItem.outerClassType
    get() =
        // Get the containing class type (if available) and adjust it based on the inner/static
        // nesting state of [classItem].
        containingClass()?.type()?.let { containingType ->
            if (modifiers.isStatic()) {
                // The type for a static nested class must not include any type arguments from its
                // containing outer class so remove any that it may have.
                containingType.substitute(arguments = emptyList())
            } else {
                // The type for an inner nested class must include type arguments from its
                // containing outer class, so keep its type as is.
                containingType
            }
        }
