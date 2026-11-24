/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava

object JavaKeywords {
    /**
     * Reserved keywords from https://en.wikipedia.org/wiki/List_of_Java_keywords.
     *
     * Includes those in https://en.wikipedia.org/wiki/List_of_Java_keywords#Reserved_keywords
     * (including unused) and reserved keywords for literal values from
     * https://en.wikipedia.org/wiki/List_of_Java_keywords#Reserved_words_for_literal_values too.
     *
     * The latter two types of keywords are marked with an inline comment.
     *
     * It does not include contextual keywords.
     *
     * Sorted in alphabetical order.
     */
    private val JAVA_RESERVED_KEYWORDS =
        setOf(
            "abstract",
            "assert",
            "boolean",
            "break",
            "byte",
            "case",
            "catch",
            "char",
            "class",
            "const", // unused keyword
            "continue",
            "default",
            "do",
            "double",
            "else",
            "enum",
            "extends",
            "false", // literal keyword
            "final",
            "finally",
            "float",
            "for",
            "goto", // unused keyword
            "if",
            "implements",
            "import",
            "instanceof",
            "int",
            "interface",
            "long",
            "native",
            "new",
            "null", // literal keyword
            "package",
            "private",
            "protected",
            "public",
            "return",
            "short",
            "static",
            "strictfp", // unused keyword
            "super",
            "switch",
            "synchronized",
            "this",
            "throw",
            "throws",
            "transient",
            "true", // literal keyword
            "try",
            "void",
            "volatile",
            "while",
        )

    fun isReservedJavaKeyword(value: String) = value in JAVA_RESERVED_KEYWORDS
}
