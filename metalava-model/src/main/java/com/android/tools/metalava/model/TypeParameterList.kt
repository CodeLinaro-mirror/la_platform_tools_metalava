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

import com.android.tools.metalava.model.TypeParameterItem.Companion.SOURCE_TYPE_STRING_CONFIGURATION

/**
 * Represents a type parameter list. For example, in class<S, T extends List<String>> the type
 * parameter list is <S, T extends List<String>> and has type parameters named S and T, and type
 * parameter T has bounds List<String>.
 */
interface TypeParameterList : List<TypeParameterItem> {
    /**
     * Returns source representation of this type parameter, using fully qualified names (possibly
     * with java.lang. removed if requested via [configuration]).
     */
    fun toSource(configuration: TypeStringConfiguration = SOURCE_TYPE_STRING_CONFIGURATION): String

    /**
     * Returns source representation of this type parameter, using fully qualified names (possibly
     * with java.lang. removed if requested via options)
     */
    override fun toString(): String

    /** Implemented according to the general [java.util.List.equals] contract. */
    override fun equals(other: Any?): Boolean

    /** Implemented according to the general [java.util.List.hashCode] contract. */
    override fun hashCode(): Int

    companion object {
        private val emptyListDelegate = emptyList<TypeParameterItem>()

        /** Type parameter list when there are no type parameters */
        val NONE: TypeParameterList =
            object : TypeParameterList, List<TypeParameterItem> by emptyListDelegate {
                override fun toSource(configuration: TypeStringConfiguration): String {
                    return ""
                }

                override fun toString() = toSource()

                override fun equals(other: Any?) = emptyListDelegate == other

                override fun hashCode() = emptyListDelegate.hashCode()
            }
    }
}

class DefaultTypeParameterList
internal constructor(private val typeParameters: List<TypeParameterItem>) :
    TypeParameterList, List<TypeParameterItem> by typeParameters {

    private val toString by lazy(LazyThreadSafetyMode.NONE) { toSource() }

    override fun toSource(configuration: TypeStringConfiguration) = buildString {
        if (this@DefaultTypeParameterList.isNotEmpty()) {
            append("<")
            var first = true
            for (param in this@DefaultTypeParameterList) {
                if (!first) {
                    append(", ")
                }
                first = false
                append(param.toSource(configuration))
            }
            append(">")
        }
    }

    override fun toString(): String {
        return toString
    }

    override fun equals(other: Any?) = typeParameters == other

    override fun hashCode() = typeParameters.hashCode()
}
