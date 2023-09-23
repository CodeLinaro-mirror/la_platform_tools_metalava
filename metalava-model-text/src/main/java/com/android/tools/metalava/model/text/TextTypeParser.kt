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

import com.android.tools.metalava.model.JAVA_LANG_OBJECT
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeParameterItem
import java.util.HashMap

/** Parses and caches types for a [codebase]. */
internal class TextTypeParser(val codebase: TextCodebase) {
    private val typeCache = Cache<String, TextTypeItem>()

    /**
     * Creates a [TextTypeItem] representing the type of [cl]. Since this is definitely a class
     * type, the steps in [obtainTypeFromString] aren't needed.
     */
    fun obtainTypeFromClass(cl: TextClassItem): TextTypeItem {
        val params = cl.typeParameterList.typeParameters().map { it.toType() }
        return TextClassTypeItem(codebase, cl.qualifiedTypeName, cl.qualifiedName, params)
    }

    /** Creates or retrieves from cache a [TextTypeItem] representing `java.lang.Object` */
    fun obtainObjectType(): TextTypeItem {
        return typeCache.obtain(JAVA_LANG_OBJECT) {
            TextClassTypeItem(codebase, JAVA_LANG_OBJECT, JAVA_LANG_OBJECT, emptyList())
        }
    }

    /**
     * Creates or retrieves from the cache a [TextTypeItem] representing [type], in the context of
     * the type parameters from [typeParams], if applicable.
     */
    @Throws(ApiParseException::class)
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
    @Throws(ApiParseException::class)
    private fun parseType(type: String, typeParams: List<TypeParameterItem>): TextTypeItem {
        // TODO(b/300081840): handle annotations
        val (unannotated, annotations) = trimLeadingAnnotations(type)
        val (withoutNullability, suffix) = splitNullabilitySuffix(unannotated)
        val trimmed = withoutNullability.trim()

        // Figure out what kind of type this is. Start with the simple cases: primitive or variable.
        return asPrimitive(type, trimmed)
            ?: asVariable(type, trimmed, typeParams)
            // Try parsing as a wildcard before trying to parse as an array.
            // `? extends java.lang.String[]` should be parsed as a wildcard with an array bound,
            // not as an array of wildcards, for consistency with how this would be compiled.
            ?: asWildcard(type, trimmed, typeParams)
            // Try parsing as an array.
            ?: asArray(trimmed, annotations, suffix, typeParams)
            // If it isn't anything else, parse the type as a class.
            ?: asClass(type, trimmed, typeParams)
    }

    /**
     * Try parsing [type] as a primitive. This will return a non-null [TextPrimitiveTypeItem] if
     * [type] exactly matches a primitive name.
     *
     * [type] should have annotations and nullability markers stripped, with [original] as the
     * complete annotated type. Once annotations are properly handled (b/300081840), preserving
     * [original] won't be necessary.
     */
    private fun asPrimitive(original: String, type: String): TextPrimitiveTypeItem? {
        val kind =
            when (type) {
                "byte" -> PrimitiveTypeItem.Primitive.BYTE
                "char" -> PrimitiveTypeItem.Primitive.CHAR
                "double" -> PrimitiveTypeItem.Primitive.DOUBLE
                "float" -> PrimitiveTypeItem.Primitive.FLOAT
                "int" -> PrimitiveTypeItem.Primitive.INT
                "long" -> PrimitiveTypeItem.Primitive.LONG
                "short" -> PrimitiveTypeItem.Primitive.SHORT
                "boolean" -> PrimitiveTypeItem.Primitive.BOOLEAN
                "void" -> PrimitiveTypeItem.Primitive.VOID
                else -> return null
            }
        return TextPrimitiveTypeItem(codebase, original, kind)
    }

    /**
     * Try parsing [type] as an array. This will return a non-null [TextArrayTypeItem] if [type]
     * ends with `[]` or `...`.
     *
     * The context [typeParams] are used to parse the component type of the array.
     */
    private fun asArray(
        type: String,
        componentAnnotations: List<String>,
        nullability: String,
        typeParams: List<TypeParameterItem>
    ): TextArrayTypeItem? {
        // Check if this is a regular array or varargs.
        val (inner, varargs) =
            if (type.endsWith("...")) {
                Pair(type.dropLast(3), true)
            } else if (type.endsWith("[]")) {
                Pair(type.dropLast(2), false)
            } else {
                return null
            }

        // TODO(b/300081840): handle annotations
        val (component, arrayAnnotations) = trimTrailingAnnotations(inner)
        val componentType = obtainTypeFromString(component, typeParams)

        // Reassemble the full text of the array. The reason this is needed instead of simply using
        // the original type like the other constructors do is that the component type might be an
        // implicit `java.lang` type. If that's true, we need to add the `java.lang` prefix to the
        // array type too. Once annotations are properly handled (b/300081840), this shouldn't be
        // necessary.
        // This isn't the case for any other complex types, because java.lang is only stripped from
        // the beginning of a type string and wildcard bounds and class parameters are at the end.
        val leadingAnnotations =
            if (componentAnnotations.isEmpty()) ""
            else {
                componentAnnotations.joinToString(" ") + " "
            }
        val trailingAnnotations =
            if (arrayAnnotations.isEmpty()) ""
            else {
                " " + arrayAnnotations.joinToString(" ") + " "
            }
        val suffix = (if (varargs) "..." else "[]") + nullability
        val reassembledTypeString = "$leadingAnnotations$componentType$trailingAnnotations$suffix"

        return TextArrayTypeItem(codebase, reassembledTypeString, componentType, varargs)
    }

    /**
     * Try parsing [type] as a wildcard. This will return a non-null [TextWildcardTypeItem] if
     * [type] begins with `?`.
     *
     * The context [typeParams] are needed to parse the bounds of the wildcard.
     *
     * [type] should have annotations and nullability markers stripped, with [original] as the
     * complete annotated type. Once annotations are properly handled (b/300081840), preserving
     * [original] won't be necessary.
     */
    @Throws(ApiParseException::class)
    private fun asWildcard(
        original: String,
        type: String,
        typeParams: List<TypeParameterItem>
    ): TextWildcardTypeItem? {
        // See if this is a wildcard
        if (!type.startsWith("?")) return null

        // Unbounded wildcard type: there is an implicit Object extends bound
        if (type == "?") return TextWildcardTypeItem(codebase, type, obtainObjectType(), null)

        val bound = type.substring(2)
        return if (bound.startsWith("extends")) {
            val extendsBound = bound.substring(8)
            TextWildcardTypeItem(
                codebase,
                original,
                obtainTypeFromString(extendsBound, typeParams),
                null
            )
        } else if (bound.startsWith("super")) {
            val superBound = bound.substring(6)
            TextWildcardTypeItem(
                codebase,
                original,
                // All wildcards have an implicit Object extends bound
                obtainObjectType(),
                obtainTypeFromString(superBound, typeParams)
            )
        } else {
            throw ApiParseException(
                "Type starts with \"?\" but doesn't appear to be wildcard: $type"
            )
        }
    }

    /**
     * Try parsing [type] as a type variable. This will return a non-null [TextVariableTypeItem] if
     * [type] matches a parameter from [typeParams].
     *
     * [type] should have annotations and nullability markers stripped, with [original] as the
     * complete annotated type. Once annotations are properly handled (b/300081840), preserving
     * [original] won't be necessary.
     */
    private fun asVariable(
        original: String,
        type: String,
        typeParams: List<TypeParameterItem>
    ): TextVariableTypeItem? {
        val param = typeParams.firstOrNull { it.simpleName() == type } ?: return null
        return TextVariableTypeItem(codebase, original, type, param)
    }

    /**
     * Parse the [type] as a class. This function will always return a non-null [TextClassTypeItem],
     * so it should only be used when it is certain that [type] is not a different kind of type.
     *
     * The context [typeParams] are used to parse the parameters of the class type.
     *
     * [type] should have annotations and nullability markers stripped, with [original] as the
     * complete annotated type. Once annotations are properly handled (b/300081840), preserving
     * [original] won't be necessary.
     */
    @Throws(ApiParseException::class)
    private fun asClass(
        original: String,
        type: String,
        typeParams: List<TypeParameterItem>
    ): TextClassTypeItem {
        return createClassType(original, type, null, typeParams)
    }

    /**
     * Creates a class name for the class represented by [type] with optional qualified name prefix
     * [outerQualifiedName].
     *
     * For instance, `test.pkg.Outer<P1>` would be the [outerQualifiedName] when parsing `Inner<P2>`
     * from the [original] type `test.pkg.Outer<P1>.Inner<P2>`.
     */
    @Throws(ApiParseException::class)
    private fun createClassType(
        original: String,
        type: String,
        outerQualifiedName: String?,
        typeParams: List<TypeParameterItem>,
    ): TextClassTypeItem {
        // TODO(b/300081840): handle annotations
        val (name, paramString, _) = trimClassAnnotations(type)
        val (qualifiedName, fullName) =
            if (outerQualifiedName != null) {
                // This is an inner type, add the prefix of the outer name
                Pair("$outerQualifiedName.$name", original)
            } else if (!name.contains('.')) {
                // Reverse the effect of [TypeItem.stripJavaLangPrefix].
                Pair("java.lang.$name", "java.lang.$original")
            } else {
                Pair(name, original)
            }
        val (paramStrings, remainder) = typeParameterStringsWithRemainder(paramString)

        if (remainder != null) {
            if (!remainder.startsWith('.')) {
                throw ApiParseException(
                    "Could not parse type `$type`. Found unexpected string after type parameters: $remainder"
                )
            }
            // This is an inner class type, recur with the new outer qualified name
            // TODO(b/301076671): This loses information about the outer type parameters
            return createClassType(fullName, remainder.substring(1), qualifiedName, typeParams)
        }
        val params = paramStrings.map { obtainTypeFromString(it, typeParams) }
        return TextClassTypeItem(codebase, fullName, qualifiedName, params)
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
                "void" -> true
                else -> false
            }
        }

        /**
         * Splits the Kotlin-style nullability marker off the type string, returning a pair of the
         * cleaned type string and the nullability suffix.
         */
        fun splitNullabilitySuffix(type: String): Pair<String, String> {
            // Don't interpret the wildcard type `?` as a nullability marker.
            return if (type.length == 1) {
                Pair(type, "")
            } else if (type.endsWith("?") || type.endsWith("!")) {
                Pair(type.dropLast(1), type.last().toString())
            } else {
                Pair(type, "")
            }
        }

        /**
         * Removes all annotations at the beginning of the type, returning the trimmed type and list
         * of annotations.
         */
        fun trimLeadingAnnotations(type: String): Pair<String, List<String>> {
            val annotations = mutableListOf<String>()
            var trimmed = type.trim()
            while (trimmed.startsWith('@')) {
                val end = findAnnotationEnd(trimmed, 1)
                annotations.add(trimmed.substring(0, end).trim())
                trimmed = trimmed.substring(end).trim()
            }
            return Pair(trimmed, annotations)
        }

        /**
         * Removes all annotations at the end of the [type], returning the trimmed type and list of
         * annotations. This is for use with arrays where annotations applying to the array type go
         * after the component type, for instance `String @A []`. The input [type] should **not**
         * include the array suffix (`[]` or `...`).
         */
        fun trimTrailingAnnotations(type: String): Pair<String, List<String>> {
            // The simple way to implement this would be to work from the end of the string, finding
            // `@` and removing annotations from the end. However, it is possible for an annotation
            // string to contain an `@`, so this is not a safe way to remove the annotations.
            // Instead, this finds all annotations starting from the beginning of the string, then
            // works backwards to find which ones are the trailing annotations.
            val allAnnotationIndices = mutableListOf<Pair<Int, Int>>()
            var trimmed = type.trim()

            // First find all annotations, saving the first and last index.
            var currIndex = 0
            while (currIndex < trimmed.length) {
                if (trimmed[currIndex] == '@') {
                    val endIndex = findAnnotationEnd(trimmed, currIndex + 1)
                    allAnnotationIndices.add(Pair(currIndex, endIndex))
                    currIndex = endIndex + 1
                } else {
                    currIndex++
                }
            }

            val annotations = mutableListOf<String>()
            // Go through all annotations from the back, seeing if they're at the end of the string.
            for ((start, end) in allAnnotationIndices.reversed()) {
                // This annotation isn't at the end, so we've hit the last trailing annotation
                if (end < trimmed.length) {
                    break
                }
                annotations.add(trimmed.substring(start))
                // Cut this annotation off, so now the next one can end at the last index.
                trimmed = trimmed.substring(0, start).trim()
            }
            return Pair(trimmed, annotations.reversed())
        }

        /**
         * Given [type] which represents a class, splits the string into the qualified name of the
         * class, the type parameter string, and a list of type-use annotations.
         *
         * For instance, for `java.util.@A @B List<java.lang.@C String>`, returns the triple
         * ("java.util.List", "<java.lang.@C String", listOf("@A", "@B")).
         */
        fun trimClassAnnotations(type: String): Triple<String, String?, List<String>> {
            // The constructed qualified type name
            var className = ""
            // The part of the type which still needs to be parsed
            var remaining = type.trim()
            val annotations: MutableList<String> = mutableListOf()

            var annotationIndex = remaining.indexOf('@')
            var paramIndex = remaining.indexOf('<')
            while (annotationIndex != -1) {
                // If there's an annotation before the params start, parse the next annotation
                if (annotationIndex < paramIndex || paramIndex == -1) {
                    // Everything before the annotation is part of the class name
                    className += remaining.substring(0, annotationIndex).trim()
                    // Find the end of the annotation, add the annotation to the list
                    val annotationEnd = findAnnotationEnd(remaining, annotationIndex + 1)
                    annotations.add(remaining.substring(annotationIndex, annotationEnd).trim())
                    // Set the remaining string to parse
                    remaining =
                        if (annotationEnd == remaining.length) {
                            ""
                        } else {
                            remaining.substring(annotationEnd).trim()
                        }
                    // Reset indices to continue
                    annotationIndex = remaining.indexOf('@')
                    paramIndex = remaining.indexOf('<')
                } else {
                    // Params start first, so there are no more annotations breaking up the name.
                    // All remaining annotations apply to parameters of the class, not the class
                    // type itself.
                    break
                }
            }

            // Finalize the class name and find the parameter string
            val params: String?
            if (paramIndex == -1) {
                className += remaining
                params = null
            } else {
                className += remaining.substring(0, paramIndex).trim()
                params = remaining.substring(paramIndex)
            }

            return Triple(className, params, annotations)
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
         *
         * E.g. `"<A, B, C>"` -> `["A", "B", "C"]` and `"<List<A>, B>"` -> `["List<A>", "B"]`.
         */
        fun typeParameterStrings(typeString: String?): List<String> {
            return typeParameterStringsWithRemainder(typeString).first
        }

        /**
         * Breaks a string representing type parameters into a list of the type parameter strings,
         * and also returns the remainder of the string after the closing ">".
         *
         * E.g. `"<A, B, C>.Inner"` -> `Pair(["A", "B", "C"], ".Inner")`
         */
        fun typeParameterStringsWithRemainder(typeString: String?): Pair<List<String>, String?> {
            val s = typeString ?: return Pair(emptyList(), null)
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
                        return if (i == s.length - 1) {
                            Pair(list, null)
                        } else {
                            Pair(list, s.substring(i + 1))
                        }
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
            return Pair(list, null)
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
