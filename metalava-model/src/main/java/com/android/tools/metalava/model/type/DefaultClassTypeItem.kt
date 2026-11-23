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

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultStandaloneTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers

internal class DefaultClassTypeItem(
    modifiers: TypeModifiers,
    override val qualifiedName: String,
    override val arguments: List<TypeArgumentTypeItem>,
    override val outerClassType: ClassTypeItem?,
    isValueClassType: Boolean = false,
) : ClassTypeItem, DefaultStandaloneTypeItem(modifiers, isValueClassType) {

    init {
        // Make sure that if an outer class type is provided that its qualified name is the outer
        // class of this type's qualified name.
        if (outerClassType != null) {
            val outerClassName = outerClassType.qualifiedName
            require(outerClassName.isOuterClassOf(qualifiedName)) {
                "$outerClassName is not a valid outer class type for $qualifiedName"
            }
        }
    }

    override val className: String = ClassTypeItem.computeClassName(qualifiedName)

    /**
     * Check whether the provided [modifiers], [outerClassType] and [arguments] are different to the
     * current values.
     *
     * Used by [ClassTypeItem.substitute] to determine whether it needs to create a new instance.
     */
    private fun requiresNewInstance(
        modifiers: TypeModifiers,
        outerClassType: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>,
    ): Boolean =
        modifiers !== this.modifiers ||
            outerClassType !== this.outerClassType ||
            arguments !== this.arguments

    override fun substitute(
        modifiers: TypeModifiers,
        outerClassType: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>,
    ): ClassTypeItem =
        if (requiresNewInstance(modifiers, outerClassType, arguments)) {
            TypeItem.createClassType(
                modifiers,
                qualifiedName,
                arguments,
                outerClassType,
                isValueClassType,
            )
        } else this

    override fun equalsImpl(other: DefaultStandaloneTypeItem): Boolean {
        if (other !is ClassTypeItem) return false
        return qualifiedName == other.qualifiedName &&
            outerClassType == other.outerClassType &&
            arguments == other.arguments
    }

    override fun hashCodeImpl(): Int {
        var result = qualifiedName.hashCode()
        result = 31 * result + (outerClassType?.hashCode() ?: 0)
        result = 31 * result + arguments.hashCode()
        return result
    }
}

/**
 * Check to see whether this [String] is the outer class of [qualifiedName].
 *
 * i.e. is this [String] with "." appended to it a prefix of [qualifiedName].
 */
private fun String.isOuterClassOf(qualifiedName: String) =
    qualifiedName.length > length && qualifiedName[length] == '.' && qualifiedName.startsWith(this)
