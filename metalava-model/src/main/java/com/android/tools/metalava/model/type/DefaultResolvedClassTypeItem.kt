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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeModifiers

internal class DefaultResolvedClassTypeItem(
    modifiers: TypeModifiers,
    private val classItem: ClassItem,
    arguments: List<TypeArgumentTypeItem>,
    outerClassType: ClassTypeItem? = classItem.outerClassType,
    isValueClassType: Boolean = false,
) :
    DefaultClassTypeItem(
        modifiers,
        classItem.qualifiedName(),
        arguments,
        outerClassType,
        isValueClassType,
    ) {
    override val className = classItem.simpleName()

    override fun resolveClass(classResolver: ClassResolver) =
        // If the ClassResolver is the Codebase for the classItem then just return it, otherwise
        // resolve this by name in the ClassResolver.
        if (classItem.codebase === classResolver) classItem
        else classResolver.resolveClass(qualifiedName)

    override fun substitute(
        modifiers: TypeModifiers,
        outerClassType: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>
    ) =
        if (requiresNewInstance(modifiers, outerClassType, arguments))
            DefaultResolvedClassTypeItem(
                modifiers,
                classItem,
                arguments,
                outerClassType,
                isValueClassType,
            )
        else this
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
