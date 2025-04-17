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

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.JAVA_LANG_STRING
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeStringConfiguration
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.item.DefaultTypeParameterItem
import com.android.tools.metalava.model.type.DefaultArrayTypeItem
import com.android.tools.metalava.model.type.DefaultClassTypeItem
import com.android.tools.metalava.model.type.DefaultPrimitiveTypeItem
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.android.tools.metalava.model.type.DefaultVariableTypeItem
import com.android.tools.metalava.model.type.DefaultWildcardTypeItem

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
fun primitiveTypeForKind(kind: Primitive): PrimitiveTypeItem =
    DefaultPrimitiveTypeItem(DefaultTypeModifiers.emptyNonNullModifiers, kind)

/** Create a [ClassTypeItem] for [JAVA_LANG_STRING]. */
fun stringType(): ClassTypeItem = classTypeItem(JAVA_LANG_STRING)

/** Create a [ClassTypeItem] for [qualifiedName] with [arguments] inside [outerClassType]. */
fun classTypeItem(
    qualifiedName: String,
    arguments: List<TypeArgumentTypeItem> = emptyList(),
    outerClassType: ClassTypeItem? = null,
): ClassTypeItem =
    DefaultClassTypeItem(
        ClassResolver.THROWING,
        DefaultTypeModifiers.emptyNonNullModifiers,
        qualifiedName,
        arguments,
        outerClassType,
    )

/** Create a [ArrayTypeItem] for [componentType]. */
fun arrayTypeItem(componentType: TypeItem): ArrayTypeItem =
    DefaultArrayTypeItem(
        DefaultTypeModifiers.emptyNonNullModifiers,
        componentType,
        isVarargs = false,
    )

/** Create a [VariableTypeItem] for a [TypeArgumentTypeItem] called [name]. */
fun variableTypeItem(name: String): VariableTypeItem =
    DefaultVariableTypeItem(
        DefaultTypeModifiers.emptyNonNullModifiers,
        DefaultTypeParameterItem(
            ClassResolver.THROWING,
            DefaultModifierList.create(0),
            name,
            isReified = false
        )
    )

/** Create a [WildcardTypeItem] for [extendsBound] of [superBound] . */
fun wildcardTypeItem(
    extendsBound: ReferenceTypeItem? = null,
    superBound: ReferenceTypeItem? = null,
): WildcardTypeItem =
    DefaultWildcardTypeItem(DefaultTypeModifiers.emptyUndefinedModifiers, extendsBound, superBound)
