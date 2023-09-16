/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.TypeParameterItem
import java.util.HashMap
import kotlin.math.min

/** Parses and caches types for a [codebase]. */
internal class TextTypeParser(val codebase: TextCodebase) {
    private val typeCache = Cache<String, TextTypeItem>()

    /**
     * Creates a [TextTypeItem] representing the type of [cl]. Since this is definitely a class
     * type, the steps in [obtainTypeFromString] aren't needed.
     */
    fun obtainTypeFromClass(cl: TextClassItem): TextTypeItem {
        return TextTypeItem(codebase, cl.qualifiedTypeName)
    }

    /**
     * Creates or retrieves from the cache a [TextTypeItem] representing [type], in the context of
     * the type parameters from [typeParams], if applicable.
     */
    fun obtainTypeFromString(
        type: String,
        typeParams: List<TypeParameterItem> = emptyList()
    ): TextTypeItem {
        // Only use the cache if there are no type parameters to prevent identically named type
        // variables from different contexts being parsed as the same type.
        return if (typeParams.isEmpty()) {
            typeCache.obtain(type) { parseType(it, typeParams) }
        } else {
            parseType(type, typeParams)
        }
    }

    /** Converts the [type] to a [TextTypeItem] in the context of the [typeParams]. */
    private fun parseType(type: String, typeParams: List<TypeParameterItem>): TextTypeItem {
        if (typeParams.isNotEmpty() && TextTypeItem.isLikelyTypeParameter(type)) {
            // Find the "name" part of the type (before a list of type parameters, array marking,
            // or nullability annotation), and see if it is a type parameter name.
            // This does not handle when a type variable is in the middle of a type (e.g. List<T>),
            // which will be fixed when type parsing is rewritten later.
            val length = type.length
            var nameEnd = length
            for (i in 0 until length) {
                val c = type[i]
                if (c == '<' || c == '[' || c == '!' || c == '?') {
                    nameEnd = i
                    break
                }
            }
            val name =
                if (nameEnd == length) {
                    type
                } else {
                    type.substring(0, nameEnd)
                }

            // Confirm that it's a type variable
            if (typeParams.any { it.simpleName() == name }) {
                return TextTypeItem(codebase, type)
            }
        }

        return if (implicitJavaLangType(type)) {
            TextTypeItem(codebase, "java.lang.$type")
        } else {
            TextTypeItem(codebase, type)
        }
    }

    private fun implicitJavaLangType(s: String): Boolean {
        if (s.length <= 1) {
            return false // Usually a type variable
        }
        if (s[1] == '[') {
            return false // Type variable plus array
        }

        val dotIndex = s.indexOf('.')
        val array = s.indexOf('[')
        val generics = s.indexOf('<')
        if (array == -1 && generics == -1) {
            return dotIndex == -1 && !isPrimitive(s)
        }
        val typeEnd =
            if (array != -1) {
                if (generics != -1) {
                    min(array, generics)
                } else {
                    array
                }
            } else {
                generics
            }

        // Allow dotted type in generic parameter, e.g. "Iterable<java.io.File>" -> return
        // true
        return (dotIndex == -1 || dotIndex > typeEnd) &&
            !isPrimitive(s.substring(0, typeEnd).trim())
    }

    private class Cache<Key, Value> {
        private val cache = HashMap<Key, Value>()

        fun obtain(o: Key, make: (Key) -> Value): Value {
            var r = cache[o]
            if (r == null) {
                r = make(o)
                cache[o] = r
            }
            // r must be non-null: either it was cached or created with make
            return r!!
        }
    }

    companion object {
        /** Whether the string represents a primitive type. */
        fun isPrimitive(type: String): Boolean {
            return when (type) {
                "byte",
                "char",
                "double",
                "float",
                "int",
                "long",
                "short",
                "boolean",
                "void",
                "null" -> true
                else -> false
            }
        }

        /**
         * Given a string and the index in that string which is the start of an annotation (the
         * character _after_ the `@`), returns the index of the end of the annotation.
         */
        fun findAnnotationEnd(type: String, start: Int): Int {
            var index = start
            val length = type.length
            var balance = 0
            while (index < length) {
                val c = type[index]
                if (c == '(') {
                    balance++
                } else if (c == ')') {
                    balance--
                    if (balance == 0) {
                        return index + 1
                    }
                } else if (c != '.' && !Character.isJavaIdentifierPart(c) && balance == 0) {
                    break
                }
                index++
            }
            return index
        }

        /**
         * Breaks a string representing type parameters into a list of the type parameter strings.
         * E.g. `"<A, B, C>"` -> `["A", "B", "C"]` and `"<List<A>, B>"` -> `["List<A>", "B"]`.
         */
        fun typeParameterStrings(typeString: String?): List<String> {
            val s = typeString ?: return emptyList()
            val list = mutableListOf<String>()
            var balance = 0
            var expect = false
            var start = 0
            for (i in s.indices) {
                val c = s[i]
                if (c == '<') {
                    balance++
                    expect = balance == 1
                } else if (c == '>') {
                    balance--
                    if (balance == 1) {
                        add(list, s, start, i + 1)
                        start = i + 1
                    } else if (balance == 0) {
                        add(list, s, start, i)
                        return list
                    }
                } else if (c == ',') {
                    expect =
                        if (balance == 1) {
                            add(list, s, start, i)
                            true
                        } else {
                            false
                        }
                } else if (expect && balance == 1) {
                    start = i
                    expect = false
                }
            }
            return list
        }

        /**
         * Adds the substring of [s] from [from] to [to] to the [list], trimming whitespace from the
         * front.
         */
        private fun add(list: MutableList<String>, s: String, from: Int, to: Int) {
            for (i in from until to) {
                if (!Character.isWhitespace(s[i])) {
                    list.add(s.substring(i, to))
                    return
                }
            }
        }
    }
}
