/*
 * Copyright (C) 2018 The Android Open Source Project
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

@MetalavaApi
interface TypeParameterItem {
    val codebase: Codebase

    /** Return the modifiers of this class */
    @MetalavaApi val modifiers: ModifierList

    /** The name of the type parameter. */
    fun name(): String

    /** The [VariableTypeItem] representing the type of this type parameter. */
    fun type(): VariableTypeItem

    fun typeBounds(): List<BoundsTypeItem>

    /**
     * Get the erased type of this, i.e. the type that would be used at runtime to represent
     * something of this type. That is either the first bound (the super class) or
     * `java.lang.Object` if there are no bounds.
     */
    fun asErasedType(): BoundsTypeItem? =
        typeBounds().firstOrNull() ?: codebase.resolveClass(JAVA_LANG_OBJECT)?.type()

    fun isReified(): Boolean

    fun toSource(): String {
        return buildString {
            if (isReified()) {
                append("reified ")
            }
            append(name())
            // If the only bound is Object, omit it because it is implied.
            if (
                typeBounds().isNotEmpty() && typeBounds().singleOrNull()?.isJavaLangObject() != true
            ) {
                append(" extends ")
                var first = true
                for (bound in typeBounds()) {
                    if (!first) {
                        append(" ")
                        append("&")
                        append(" ")
                    }
                    first = false
                    append(bound.toTypeString(spaceBetweenParameters = true))
                }
            }
        }
    }
}
