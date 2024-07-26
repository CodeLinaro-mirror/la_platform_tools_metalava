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

package com.android.tools.metalava.model

/**
 * A visitor like pattern that can apply a transform to a [TypeItem].
 *
 * See [TypeItem.transform].
 */
interface TypeTransformer {
    fun transform(typeItem: ArrayTypeItem): ArrayTypeItem = typeItem

    fun transform(typeItem: ClassTypeItem): ClassTypeItem = typeItem

    fun transform(typeItem: LambdaTypeItem): LambdaTypeItem = typeItem

    fun transform(typeItem: PrimitiveTypeItem): PrimitiveTypeItem = typeItem

    fun transform(typeItem: VariableTypeItem): VariableTypeItem = typeItem

    fun transform(typeItem: WildcardTypeItem): WildcardTypeItem = typeItem
}

/**
 * A [TypeTransformer] that recursively calls [TypeItem.transform] on each [TypeItem]'s
 * [TypeItem.modifiers] and any contained [TypeItem] and then substitutes those in to the [TypeItem]
 * using the appropriate `substitute(TypeModifiers,...)` method.
 */
open class BaseTypeTransformer : TypeTransformer {

    open fun transform(modifiers: TypeModifiers): TypeModifiers = modifiers

    override fun transform(typeItem: ArrayTypeItem): ArrayTypeItem {
        return typeItem.substitute(
            modifiers = transform(typeItem.modifiers),
            componentType = typeItem.componentType.transform(this),
        )
    }

    override fun transform(typeItem: ClassTypeItem): ClassTypeItem {
        return typeItem.substitute(
            modifiers = transform(typeItem.modifiers),
            outerClassType = typeItem.outerClassType?.transform(this),
            arguments = typeItem.arguments.mapIfNotSame { it.transform(this) }
        )
    }

    override fun transform(typeItem: LambdaTypeItem): LambdaTypeItem {
        return typeItem.substitute(
            modifiers = transform(typeItem.modifiers),
            outerClassType = typeItem.outerClassType?.transform(this),
            arguments = typeItem.arguments.mapIfNotSame { it.transform(this) }
        )
    }

    override fun transform(typeItem: PrimitiveTypeItem): PrimitiveTypeItem {
        return typeItem.substitute(
            modifiers = transform(typeItem.modifiers),
        )
    }

    override fun transform(typeItem: VariableTypeItem): VariableTypeItem {
        return typeItem.substitute(
            modifiers = transform(typeItem.modifiers),
        )
    }

    override fun transform(typeItem: WildcardTypeItem): WildcardTypeItem {
        return typeItem.substitute(
            modifiers = transform(typeItem.modifiers),
            extendsBound = typeItem.extendsBound?.transform(this),
            superBound = typeItem.superBound?.transform(this),
        )
    }
}
