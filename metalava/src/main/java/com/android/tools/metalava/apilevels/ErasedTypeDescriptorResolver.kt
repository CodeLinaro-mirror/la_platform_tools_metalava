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

package com.android.tools.metalava.apilevels

/**
 * Resolves raw signature file type strings into erased JVM internal names (`pkg/Outer$Inner`) and
 * JVM type descriptors (`I`, `[Ljava/lang/String;`, etc.) without constructing
 * [com.android.tools.metalava.model.TypeItem] instances.
 */
internal class ErasedTypeDescriptorResolver {

    /**
     * Resolves a class or interface [type] string (such as from an `extends` or `implements`
     * clause) to its erased JVM internal name (e.g. `java/lang/Object` or `test/pkg/Outer$Inner`).
     *
     * @param type the raw type string from the signature file.
     * @param scope the [ErasedTypeScope] of in-scope type parameters.
     * @return the erased JVM internal name without `L` or `;` delimiters.
     */
    fun resolveInternalName(
        type: String,
        scope: ErasedTypeScope = ErasedTypeScope.Empty,
    ): String {
        val sb = StringBuilder(type.length)
        val scanner = TypeScanner(type)
        scanner.appendClassInternalName(sb, scope)
        return sb.toString()
    }

    /**
     * Resolves a class or interface [type] string (such as from an `extends` or `implements`
     * clause) to its erased dot-separated qualified name (e.g. `java.lang.Object` or
     * `test.pkg.Outer.Inner`).
     *
     * @param type the raw type string from the signature file.
     * @param scope the [ErasedTypeScope] of in-scope type parameters.
     * @return the erased dot-separated qualified class name.
     */
    fun resolveQualifiedName(
        type: String,
        scope: ErasedTypeScope = ErasedTypeScope.Empty,
    ): String {
        val sb = StringBuilder(type.length + 10)
        val scanner = TypeScanner(type)
        scanner.appendClassQualifiedName(sb, scope)
        return sb.toString()
    }

    /**
     * Resolves a parameter, return, or bound [type] string to its erased JVM descriptor (e.g. `I`,
     * `Ljava/lang/String;`, `[[Ltest/pkg/Outer$Inner;`).
     *
     * @param type the raw type string from the signature file.
     * @param scope the [ErasedTypeScope] of in-scope type parameters.
     * @param isVarargs whether the parameter is a varargs parameter (or has a `...` suffix in its
     *   [type] string). If `true` and [type] does not already end with `...`, an extra `[` array
     *   dimension is prepended.
     * @return the erased JVM type descriptor.
     */
    fun resolveDescriptor(
        type: String,
        scope: ErasedTypeScope = ErasedTypeScope.Empty,
        isVarargs: Boolean = false,
    ): String {
        val sb = StringBuilder(type.length + 16)
        appendDescriptor(sb, type, scope, isVarargs)
        return sb.toString()
    }

    /**
     * Appends the erased JVM descriptor for [type] directly to [out].
     *
     * @param out the [StringBuilder] to append the descriptor to.
     * @param type the raw type string from the signature file.
     * @param scope the [ErasedTypeScope] of in-scope type parameters.
     * @param isVarargs whether an extra array dimension should be added if [type] does not already
     *   include a trailing `...` varargs suffix.
     */
    fun appendDescriptor(
        out: StringBuilder,
        type: String,
        scope: ErasedTypeScope = ErasedTypeScope.Empty,
        isVarargs: Boolean = false,
    ) {
        val scanner = TypeScanner(type)
        scanner.appendDescriptor(out, scope, isVarargs)
    }

    /**
     * Appends the source-style representation of [type] (matching
     * [com.android.tools.metalava.model.TypeItem.toTypeString] with qualified `java.lang.` names,
     * preserved generic type arguments, and stripped annotations/nullability suffixes) directly to
     * [out].
     *
     * @param out the [StringBuilder] to append the formatted type string to.
     * @param type the raw type string from the signature file.
     * @param scope the [ErasedTypeScope] of in-scope type parameters.
     * @param isVarargs whether the outermost array dimension should be formatted as `...`.
     * @param spaceBetweenTypeArguments whether `, ` (with a space) should be used between generic
     *   type arguments instead of `,`.
     */
    fun appendSourceType(
        out: StringBuilder,
        type: String,
        scope: ErasedTypeScope = ErasedTypeScope.Empty,
        isVarargs: Boolean = false,
        spaceBetweenTypeArguments: Boolean = false,
    ) {
        val scanner = TypeScanner(type)
        scanner.appendSourceType(out, scope, isVarargs, spaceBetweenTypeArguments)
    }

    /**
     * Lightweight character scanner over a single [type] string that skips annotations, generic
     * type arguments, and nullability markers while extracting the erased base type and array
     * dimensions.
     */
    private class TypeScanner(private val type: String) {
        private val length = type.length
        private var pos = 0

        /**
         * Parses a class or interface type and appends its JVM internal name (`pkg/Outer$Inner`) to
         * [out].
         */
        fun appendClassInternalName(out: StringBuilder, scope: ErasedTypeScope) {
            skipWhitespaceAndAnnotations()
            val firstStart = pos
            val firstEnd = scanIdentifier()
            skipPostSegmentTokens()

            // Single-identifier class name (e.g. `Object`, `String`, `Comparable<T>`) or type
            // parameter.
            if (!isPackageOrNestedClassDot()) {
                val simpleName = type.substring(firstStart, firstEnd)
                val typeParamDesc = scope.findDescriptor(simpleName)
                if (typeParamDesc != null) {
                    if (typeParamDesc.startsWith('L') && typeParamDesc.endsWith(';')) {
                        out.append(typeParamDesc, 1, typeParamDesc.length - 1)
                    } else {
                        out.append(typeParamDesc)
                    }
                } else if (simpleName == "dynamic") {
                    out.append("dynamic")
                } else {
                    out.append("java/lang/").append(simpleName)
                }
                return
            }

            appendMultiSegmentInternalName(out, firstStart, firstEnd)
        }

        /**
         * Parses a class or interface type and appends its dot-separated qualified name
         * (`pkg.Outer.Inner`) to [out].
         */
        fun appendClassQualifiedName(out: StringBuilder, scope: ErasedTypeScope) {
            skipWhitespaceAndAnnotations()
            val firstStart = pos
            val firstEnd = scanIdentifier()
            skipPostSegmentTokens()

            if (!isPackageOrNestedClassDot()) {
                val simpleName = type.substring(firstStart, firstEnd)
                val typeParamDesc = scope.findDescriptor(simpleName)
                if (typeParamDesc != null) {
                    val start = if (typeParamDesc.startsWith('L')) 1 else 0
                    val end =
                        if (typeParamDesc.endsWith(';')) typeParamDesc.length - 1
                        else typeParamDesc.length
                    for (i in start until end) {
                        val c = typeParamDesc[i]
                        out.append(if (c == '/' || c == '$') '.' else c)
                    }
                } else if (simpleName == "dynamic") {
                    out.append("dynamic")
                } else {
                    out.append("java.lang.").append(simpleName)
                }
                return
            }

            if (containsUpperCase(firstStart, firstEnd)) {
                out.append("java.lang.")
            }
            out.append(type, firstStart, firstEnd)

            while (isPackageOrNestedClassDot()) {
                pos++ // consume '.'
                skipWhitespaceAndAnnotations()
                val nextStart = pos
                val nextEnd = scanIdentifier()
                skipPostSegmentTokens()
                out.append('.').append(type, nextStart, nextEnd)
            }
        }

        /**
         * Parses any type (type parameter, primitive, class, or array/varargs) and appends its JVM
         * type descriptor to [out].
         */
        fun appendDescriptor(
            out: StringBuilder,
            scope: ErasedTypeScope,
            isVarargs: Boolean,
        ) {
            // Remember the insertion point in `out` so array `[` prefixes can be inserted once all
            // trailing `[]` and `...` dimensions have been counted.
            val descriptorStart = out.length

            skipWhitespaceAndAnnotations()
            val firstStart = pos
            val firstEnd = scanIdentifier()
            skipPostSegmentTokens()

            if (!isPackageOrNestedClassDot()) {
                // Single-identifier base type: check in-scope type parameters first (in case a
                // Kotlin type parameter shadows a class or primitive name), then primitives, then
                // unqualified `java.lang.*` classes.
                val simpleName = type.substring(firstStart, firstEnd)
                val typeParamDesc = scope.findDescriptor(simpleName)
                if (typeParamDesc != null) {
                    out.append(typeParamDesc)
                } else {
                    val primitiveDescriptor = primitiveDescriptor(simpleName)
                    if (primitiveDescriptor != null) {
                        out.append(primitiveDescriptor)
                    } else if (simpleName == "dynamic") {
                        out.append("Ldynamic;")
                    } else {
                        out.append("Ljava/lang/").append(simpleName).append(';')
                    }
                }
            } else {
                // Multi-segment qualified class or nested class (`java.lang.String`,
                // `Thread.State`, `android.Manifest.permission`, etc.).
                out.append('L')
                appendMultiSegmentInternalName(out, firstStart, firstEnd)
                out.append(';')
            }

            // Count any trailing `[]` and `...` dimensions after the base type.
            val dimensions = countArrayDimensions(isVarargs)
            for (i in 0 until dimensions) {
                out.insert(descriptorStart, '[')
            }
        }

        /**
         * Parses any type (wildcard, type parameter, primitive, class, or array/varargs) and
         * appends its source-style representation to [out].
         */
        fun appendSourceType(
            out: StringBuilder,
            scope: ErasedTypeScope,
            isVarargs: Boolean,
            spaceBetweenTypeArguments: Boolean,
        ) {
            skipWhitespaceAndAnnotations()
            if (pos < length && type[pos] == '?') {
                appendWildcardSourceType(out, scope, spaceBetweenTypeArguments)
                return
            }

            val firstStart = pos
            val firstEnd = scanIdentifier()
            skipWhitespace()

            val hasTypeArgs = pos < length && type[pos] == '<'
            if (!hasTypeArgs) {
                skipNullabilitySuffixes()
            }
            val hasDot = !hasTypeArgs && isPackageOrNestedClassDot()

            if (!hasTypeArgs && !hasDot) {
                val simpleName = type.substring(firstStart, firstEnd)
                if (
                    scope.findDescriptor(simpleName) != null ||
                        primitiveDescriptor(simpleName) != null ||
                        simpleName == "dynamic"
                ) {
                    out.append(simpleName)
                } else {
                    out.append("java.lang.").append(simpleName)
                }
            } else {
                if (hasTypeArgs || containsUpperCase(firstStart, firstEnd)) {
                    out.append("java.lang.")
                }
                out.append(type, firstStart, firstEnd)
                if (hasTypeArgs) {
                    appendSourceTypeArguments(out, scope, spaceBetweenTypeArguments)
                    skipNullabilitySuffixes()
                }
                while (isPackageOrNestedClassDot()) {
                    pos++ // consume '.'
                    skipWhitespaceAndAnnotations()
                    val nextStart = pos
                    val nextEnd = scanIdentifier()
                    out.append('.').append(type, nextStart, nextEnd)
                    skipWhitespace()
                    if (pos < length && type[pos] == '<') {
                        appendSourceTypeArguments(out, scope, spaceBetweenTypeArguments)
                    }
                    skipNullabilitySuffixes()
                }
            }

            appendSourceArrayDimensions(out, isVarargs)
        }

        /**
         * Appends a wildcard type (`?`, `? extends Bound`, or `? super Bound`) to [out], omitting `
         * extends java.lang.Object` to match
         * [com.android.tools.metalava.model.TypeItem.toTypeString].
         */
        private fun appendWildcardSourceType(
            out: StringBuilder,
            scope: ErasedTypeScope,
            spaceBetweenTypeArguments: Boolean,
        ) {
            pos++ // consume '?'
            skipWhitespace()
            if (pos < length && isIdentifierChar(type[pos])) {
                val kwStart = pos
                val kwEnd = scanIdentifier()
                val kwLength = kwEnd - kwStart
                if (kwLength == 5 && type.regionMatches(kwStart, "super", 0, 5)) {
                    out.append("? super ")
                    appendSourceType(
                        out,
                        scope,
                        isVarargs = false,
                        spaceBetweenTypeArguments = spaceBetweenTypeArguments,
                    )
                    return
                }
                if (kwLength == 7 && type.regionMatches(kwStart, "extends", 0, 7)) {
                    val wildcardStart = out.length
                    out.append("? extends ")
                    val boundStart = out.length
                    appendSourceType(
                        out,
                        scope,
                        isVarargs = false,
                        spaceBetweenTypeArguments = spaceBetweenTypeArguments,
                    )
                    if (
                        out.length - boundStart == JAVA_LANG_OBJECT_QUALIFIED_NAME.length &&
                            out.regionMatches(
                                boundStart,
                                JAVA_LANG_OBJECT_QUALIFIED_NAME,
                                0,
                                JAVA_LANG_OBJECT_QUALIFIED_NAME.length,
                            )
                    ) {
                        out.setLength(wildcardStart)
                        out.append('?')
                    }
                    return
                }
            }
            out.append('?')
        }

        /**
         * Appends a `<...>` generic type argument list to [out], formatting each type argument with
         * [appendSourceType].
         */
        private fun appendSourceTypeArguments(
            out: StringBuilder,
            scope: ErasedTypeScope,
            spaceBetweenTypeArguments: Boolean,
        ) {
            pos++ // consume '<'
            out.append('<')
            var first = true
            while (pos < length) {
                skipWhitespace()
                if (pos < length && type[pos] == '>') {
                    pos++
                    break
                }
                if (!first) {
                    out.append(',')
                    if (spaceBetweenTypeArguments) {
                        out.append(' ')
                    }
                }
                first = false
                appendSourceType(
                    out,
                    scope,
                    isVarargs = false,
                    spaceBetweenTypeArguments = spaceBetweenTypeArguments,
                )
                skipWhitespace()
                if (pos < length && type[pos] == ',') {
                    pos++
                }
            }
            out.append('>')
        }

        /**
         * Appends trailing `[]` array brackets and `...` varargs suffixes to [out], stripping any
         * interspersed annotations and nullability suffixes.
         */
        private fun appendSourceArrayDimensions(out: StringBuilder, isVarargs: Boolean) {
            var dimensions = 0
            var sawEllipsis = false
            while (pos < length) {
                skipWhitespaceAndAnnotations()
                if (pos >= length) break
                val c = type[pos]
                when {
                    c == '[' -> {
                        pos++
                        skipWhitespace()
                        if (pos < length && type[pos] == ']') {
                            pos++
                        }
                        dimensions++
                    }
                    c == '.' &&
                        pos + 2 < length &&
                        type[pos + 1] == '.' &&
                        type[pos + 2] == '.' -> {
                        pos += 3
                        dimensions++
                        sawEllipsis = true
                    }
                    c == '?' || c == '!' -> {
                        pos++
                    }
                    else -> break
                }
            }
            val effectiveVarargs = isVarargs || sawEllipsis
            if (isVarargs && !sawEllipsis) {
                dimensions++
            }
            for (i in 0 until dimensions) {
                if (i == dimensions - 1 && effectiveVarargs) {
                    out.append("...")
                } else {
                    out.append("[]")
                }
            }
        }

        /** Skips whitespace and any `?` or `!` nullability suffixes at [pos]. */
        private fun skipNullabilitySuffixes() {
            while (pos < length) {
                skipWhitespace()
                if (pos < length && (type[pos] == '?' || type[pos] == '!')) {
                    pos++
                } else {
                    break
                }
            }
        }

        /**
         * Appends a multi-segment qualified class name starting from the already-scanned first
         * identifier `[firstStart, firstEnd)`, converting package `.` separators to `/` and nested
         * class `.` separators (after the first class segment) to `$`.
         */
        private fun appendMultiSegmentInternalName(
            out: StringBuilder,
            firstStart: Int,
            firstEnd: Int,
        ) {
            var seenClassSegment = containsUpperCase(firstStart, firstEnd)
            if (seenClassSegment) {
                // If the first segment starts with an uppercase letter (e.g. `Thread.State`), it
                // is an unqualified `java.lang` outer class followed by nested class segments.
                out.append("java/lang/")
            }
            out.append(type, firstStart, firstEnd)

            while (isPackageOrNestedClassDot()) {
                pos++ // consume '.'
                val hadAnnotation = skipWhitespaceAndAnnotations()
                val nextStart = pos
                val nextEnd = scanIdentifier()
                skipPostSegmentTokens()

                if (seenClassSegment) {
                    // Every segment after the outer class segment is a nested class.
                    out.append('$')
                } else {
                    // Still in package segments; the separator before this segment is `/`.
                    out.append('/')
                    // A segment is the outer class segment if it contains an uppercase character or
                    // was preceded by a type-use annotation (e.g. `pkg.@Anno lowerCaseClass`).
                    if (hadAnnotation || containsUpperCase(nextStart, nextEnd)) {
                        seenClassSegment = true
                    }
                }
                out.append(type, nextStart, nextEnd)
            }
        }

        /**
         * Counts trailing `[]` array brackets and `...` varargs ellipses, skipping any interspersed
         * whitespace, type-use annotations, and `?`/`!` nullability suffixes.
         */
        private fun countArrayDimensions(isVarargs: Boolean): Int {
            var dimensions = 0
            var sawEllipsis = false
            while (pos < length) {
                skipWhitespaceAndAnnotations()
                if (pos >= length) break
                val c = type[pos]
                when {
                    c == '[' -> {
                        pos++
                        skipWhitespace()
                        if (pos < length && type[pos] == ']') {
                            pos++
                        }
                        dimensions++
                    }
                    c == '.' &&
                        pos + 2 < length &&
                        type[pos + 1] == '.' &&
                        type[pos + 2] == '.' -> {
                        pos += 3
                        dimensions++
                        sawEllipsis = true
                    }
                    c == '?' || c == '!' -> {
                        pos++
                    }
                    else -> break
                }
            }
            if (isVarargs && !sawEllipsis) {
                dimensions++
            }
            return dimensions
        }

        /**
         * Returns `true` if [pos] is at a `.` separator between package or class segments (and not
         * a `...` varargs ellipsis).
         */
        private fun isPackageOrNestedClassDot(): Boolean {
            if (pos >= length || type[pos] != '.') return false
            return !(pos + 2 < length && type[pos + 1] == '.' && type[pos + 2] == '.')
        }

        /**
         * Skips whitespace, generic `<...>` type argument lists, and `?`/`!` nullability suffixes
         * immediately following an identifier segment.
         */
        private fun skipPostSegmentTokens() {
            while (pos < length) {
                skipWhitespace()
                if (pos >= length) break
                when (type[pos]) {
                    '<' -> skipBalancedAngles()
                    '?',
                    '!' -> pos++
                    else -> break
                }
            }
        }

        /**
         * Skips whitespace and any `@...` type-use annotations at [pos].
         *
         * @return `true` if at least one annotation was skipped.
         */
        private fun skipWhitespaceAndAnnotations(): Boolean {
            var skippedAnnotation = false
            while (pos < length) {
                skipWhitespace()
                if (pos < length && type[pos] == '@') {
                    skipAnnotation()
                    skippedAnnotation = true
                } else {
                    break
                }
            }
            return skippedAnnotation
        }

        /** Skips a single `@QualifiedName(...)` annotation starting at [pos]. */
        private fun skipAnnotation() {
            pos++ // consume '@'
            skipWhitespace()
            scanIdentifier()
            skipWhitespace()
            while (isPackageOrNestedClassDot()) {
                pos++ // consume '.'
                skipWhitespace()
                scanIdentifier()
                skipWhitespace()
            }
            if (pos < length && type[pos] == '(') {
                skipBalancedParens()
            }
        }

        /** Skips balanced `(...)` starting at `(` at [pos], respecting string and char literals. */
        private fun skipBalancedParens() {
            pos++ // consume '('
            var depth = 1
            while (pos < length && depth > 0) {
                when (val c = type[pos++]) {
                    '(' -> depth++
                    ')' -> depth--
                    '"',
                    '\'' -> skipQuotedLiteral(c)
                }
            }
        }

        /** Skips balanced `<...>` starting at `<` at [pos], respecting string and char literals. */
        private fun skipBalancedAngles() {
            pos++ // consume '<'
            var depth = 1
            while (pos < length && depth > 0) {
                when (val c = type[pos++]) {
                    '<' -> depth++
                    '>' -> depth--
                    '"',
                    '\'' -> skipQuotedLiteral(c)
                }
            }
        }

        /** Skips a string or character literal after its opening [quote] character. */
        private fun skipQuotedLiteral(quote: Char) {
            while (pos < length) {
                val c = type[pos++]
                if (c == '\\' && pos < length) {
                    pos++
                } else if (c == quote) {
                    break
                }
            }
        }

        /** Advances [pos] past any whitespace characters. */
        private fun skipWhitespace() {
            while (pos < length && type[pos].isWhitespace()) {
                pos++
            }
        }

        /**
         * Scans a Java/Kotlin identifier starting at [pos] and returns the exclusive end offset.
         */
        private fun scanIdentifier(): Int {
            while (pos < length && isIdentifierChar(type[pos])) {
                pos++
            }
            return pos
        }

        /** Returns `true` if any character in `[start, end)` is an uppercase character. */
        private fun containsUpperCase(start: Int, end: Int): Boolean {
            for (i in start until end) {
                if (type[i].isUpperCase()) return true
            }
            return false
        }

        /** Returns `true` if [c] can be part of a Java identifier in a signature file. */
        private fun isIdentifierChar(c: Char): Boolean {
            return c.isLetterOrDigit() || c == '_' || c == '$'
        }
    }

    companion object {
        private const val JAVA_LANG_OBJECT_QUALIFIED_NAME = "java.lang.Object"

        /**
         * Returns the single-character JVM descriptor for a primitive type name, or `null` if
         * [name] is not a primitive keyword.
         */
        private fun primitiveDescriptor(name: String): String? =
            when (name) {
                "boolean" -> "Z"
                "byte" -> "B"
                "char" -> "C"
                "double" -> "D"
                "float" -> "F"
                "int" -> "I"
                "long" -> "J"
                "short" -> "S"
                "void" -> "V"
                else -> null
            }
    }
}
