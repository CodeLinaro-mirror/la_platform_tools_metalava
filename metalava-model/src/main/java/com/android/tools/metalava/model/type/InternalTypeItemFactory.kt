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
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.LambdaTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
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
        classResolver: ClassResolver,
        modifiers: TypeModifiers,
        qualifiedName: String,
        arguments: List<TypeArgumentTypeItem>,
        outerClassType: ClassTypeItem?,
        isValueClassType: Boolean = false,
    ): ClassTypeItem =
        DefaultClassTypeItem(
            classResolver,
            modifiers,
            qualifiedName,
            arguments,
            outerClassType,
            isValueClassType,
        )

    /** Create a [ClassTypeItem] for [ClassItem]. */
    fun createClassTypeForClassItem(classItem: ClassItem): ClassTypeItem {
        val arguments = classItem.typeParameterList.map { it.type() }
        val modifiers = DefaultTypeModifiers.emptyNonNullModifiers
        return DefaultResolvedClassTypeItem(modifiers, classItem, arguments)
    }

    /** Create a [LambdaTypeItem]. */
    fun createLambdaType(
        classResolver: ClassResolver,
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
        DefaultLambdaTypeItem(
            classResolver,
            modifiers,
            qualifiedName,
            arguments,
            outerClassType,
            isSuspend,
            receiverType,
            parameterTypes,
            returnType,
            isValueClassType,
        )

    /** Create a [PrimitiveTypeItem]. */
    fun createPrimitiveType(
        modifiers: TypeModifiers,
        kind: Primitive,
        isValueClassType: Boolean = false,
    ): PrimitiveTypeItem =
        DefaultPrimitiveTypeItem(
            modifiers,
            kind,
            isValueClassType,
        )

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
        isValueClassType: Boolean = false,
    ): WildcardTypeItem =
        DefaultWildcardTypeItem(
            modifiers,
            extendsBound,
            superBound,
            isValueClassType,
        )
}
