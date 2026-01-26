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
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeModifiers

internal class DefaultResolvedClassTypeItem(
    modifiers: TypeModifiers,
    private val classItem: ClassItem,
    override val arguments: List<TypeArgumentTypeItem>,
    isValueClassType: Boolean = false,
) : ClassTypeItem, DefaultTypeItem(modifiers, isValueClassType) {

    override val qualifiedName = classItem.qualifiedName()

    /**
     * The outer class type to use depends on whether [classItem] is an inner (i.e. not-static
     * nested) class or simply a static nested class. For inner classes the outer type needs to
     * include its type arguments but for static nested classes the outer type must not include any
     * type arguments.
     */
    override val outerClassType =
        // Get the containing class type (if available) and adjust it based on the inner/static
        // nesting state of [classItem].
        classItem.containingClass()?.type()?.let { containingType ->
            if (classItem.modifiers.isStatic()) {
                // The type for a static nested class does not include any type arguments from its
                // containing outer class so remove any that it may have.
                containingType.substitute(arguments = emptyList())
            } else {
                // The type for an inner nested class does so keep its type as is.
                containingType
            }
        }

    override val className = classItem.simpleName()

    override fun resolveClass() = classItem

    @Deprecated(
        "implementation detail of this class",
        replaceWith = ReplaceWith("substitute(modifiers, outerClassType, arguments)"),
    )
    override fun duplicate(
        modifiers: TypeModifiers,
        outerClassType: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>
    ): ClassTypeItem {
        return DefaultResolvedClassTypeItem(modifiers, classItem, arguments)
    }
}
