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

package com.android.tools.metalava.model.parser

/**
 * Shared token types required for parsing types, type parameter lists, values, and annotations.
 *
 * Emitted by [SharedLexer] (and `SignatureFileLexer`).
 */
object SharedTokenType {
    // Identifiers & Literals (for types, values, and annotations)
    val IDENTIFIER = TokenType("IDENTIFIER", canBeIdentifier = true)
    val NUMBER_LITERAL = TokenType("NUMBER_LITERAL")
    val STRING_LITERAL = TokenType("STRING_LITERAL")
    val CHAR_LITERAL = TokenType("CHAR_LITERAL")

    // Type & Value Keywords
    val CLASS = TokenType("CLASS", canBeIdentifier = true)
    val EXTENDS = TokenType("EXTENDS", canBeIdentifier = true)
    val SUPER = TokenType("SUPER", canBeIdentifier = true)
    val REIFIED = TokenType("REIFIED", canBeIdentifier = true)

    // Type, Value & Annotation Delimiters / Punctuation
    val DOT = TokenType("DOT")
    val COMMA = TokenType("COMMA")
    val COLON = TokenType("COLON")
    val DOUBLE_COLON = TokenType("DOUBLE_COLON")
    val ANGLE_OPEN = TokenType("ANGLE_OPEN")
    val ANGLE_CLOSE = TokenType("ANGLE_CLOSE")
    val BRACKET_OPEN = TokenType("BRACKET_OPEN")
    val BRACKET_CLOSE = TokenType("BRACKET_CLOSE")
    val ELLIPSIS = TokenType("ELLIPSIS")
    val PAREN_OPEN = TokenType("PAREN_OPEN")
    val PAREN_CLOSE = TokenType("PAREN_CLOSE")
    val BRACE_OPEN = TokenType("BRACE_OPEN")
    val BRACE_CLOSE = TokenType("BRACE_CLOSE")
    val AT = TokenType("AT")
    val QUESTION = TokenType("QUESTION")
    val EXCLAMATION = TokenType("EXCLAMATION")
    val AMPERSAND = TokenType("AMPERSAND")
    val EQUALS = TokenType("EQUALS")
    val PLUS = TokenType("PLUS")
    val MINUS = TokenType("MINUS")
    val SLASH = TokenType("SLASH")

    // Fallback for unrecognized characters
    val UNKNOWN = TokenType("UNKNOWN")

    // End of input
    val EOF = TokenType("EOF")
}

/**
 * Shared lexer that tokenizes type strings, type parameter lists, values, and annotations into a
 * lazy stream of [Token]s of [SharedTokenType].
 *
 * Subclasses (such as `SignatureFileLexer`) may override [tryMatchAdditionalToken] and
 * [resolveKeywordOrIdentifier] to recognize additional token types and keywords.
 *
 * @param text the full string containing the text to tokenize.
 * @param startInclusive the index in [text] where tokenization should begin.
 * @param endExclusive the index in [text] where tokenization should end.
 */
open class SharedLexer(
    text: String,
    startInclusive: Int = 0,
    endExclusive: Int = text.length,
) :
    AbstractLexer(
        text,
        startInclusive,
        endExclusive,
    ) {
    override fun nextToken(): Token {
        skipWhitespaceAndComments()

        if (index >= endExclusive) {
            return createToken(
                SharedTokenType.EOF,
                endExclusive,
                endExclusive,
            )
        }

        val additional = tryMatchAdditionalToken()
        if (additional != Token.NONE) {
            return additional
        }

        val start = index
        return when (val c = text[start]) {
            '.' -> {
                if (start + 2 < endExclusive && text[start + 1] == '.' && text[start + 2] == '.') {
                    index = start + 3
                    createToken(SharedTokenType.ELLIPSIS, start, index)
                } else {
                    index = start + 1
                    createToken(SharedTokenType.DOT, start, index)
                }
            }
            ',' -> {
                index = start + 1
                createToken(SharedTokenType.COMMA, start, index)
            }
            ':' -> {
                if (start + 1 < endExclusive && text[start + 1] == ':') {
                    index = start + 2
                    createToken(SharedTokenType.DOUBLE_COLON, start, index)
                } else {
                    index = start + 1
                    createToken(SharedTokenType.COLON, start, index)
                }
            }
            '<' -> {
                index = start + 1
                createToken(SharedTokenType.ANGLE_OPEN, start, index)
            }
            '>' -> {
                index = start + 1
                createToken(SharedTokenType.ANGLE_CLOSE, start, index)
            }
            '[' -> {
                index = start + 1
                createToken(SharedTokenType.BRACKET_OPEN, start, index)
            }
            ']' -> {
                index = start + 1
                createToken(SharedTokenType.BRACKET_CLOSE, start, index)
            }
            '(' -> {
                index = start + 1
                createToken(SharedTokenType.PAREN_OPEN, start, index)
            }
            ')' -> {
                index = start + 1
                createToken(SharedTokenType.PAREN_CLOSE, start, index)
            }
            '{' -> {
                index = start + 1
                createToken(SharedTokenType.BRACE_OPEN, start, index)
            }
            '}' -> {
                index = start + 1
                createToken(SharedTokenType.BRACE_CLOSE, start, index)
            }
            '@' -> {
                index = start + 1
                createToken(SharedTokenType.AT, start, index)
            }
            '?' -> {
                index = start + 1
                createToken(SharedTokenType.QUESTION, start, index)
            }
            '!' -> {
                index = start + 1
                createToken(SharedTokenType.EXCLAMATION, start, index)
            }
            '&' -> {
                index = start + 1
                createToken(SharedTokenType.AMPERSAND, start, index)
            }
            '=' -> {
                index = start + 1
                createToken(SharedTokenType.EQUALS, start, index)
            }
            '+' -> {
                index = start + 1
                createToken(SharedTokenType.PLUS, start, index)
            }
            '-' -> {
                index = start + 1
                createToken(SharedTokenType.MINUS, start, index)
            }
            '/' -> {
                index = start + 1
                createToken(SharedTokenType.SLASH, start, index)
            }
            '"' -> scanStringLiteral(start)
            '\'' -> scanCharLiteral(start)
            in '0'..'9' -> scanNumberLiteral(start)
            else -> {
                if (Character.isJavaIdentifierStart(c)) {
                    scanIdentifierOrKeyword(start)
                } else {
                    index = start + 1
                    createToken(
                        SharedTokenType.UNKNOWN,
                        start,
                        index,
                    )
                }
            }
        }
    }

    /** Skips whitespace characters and `//` line comments. */
    private fun skipWhitespaceAndComments() {
        while (index < endExclusive) {
            val c = text[index]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                index++
            } else if (c == '/' && index + 1 < endExclusive && text[index + 1] == '/') {
                index += 2
                while (index < endExclusive) {
                    val ch = text[index]
                    if (ch == '\n' || ch == '\r') break
                    index++
                }
            } else {
                break
            }
        }
    }

    /**
     * Hook for subclasses to match additional punctuation or prefix tokens at the current [index]
     * before [SharedLexer] attempts its standard matching, or return [Token.NONE] if not matched.
     */
    protected open fun tryMatchAdditionalToken(): Token = Token.NONE

    /**
     * Maps the slice of [text] from [start] to [end] to a keyword [TokenType] or
     * [SharedTokenType.IDENTIFIER].
     *
     * Subclasses may override this to recognize additional keywords.
     */
    protected open fun resolveKeywordOrIdentifier(start: Int, end: Int): TokenType {
        val length = end - start
        return when (text[start]) {
            'c' ->
                if (matchSlice(start, length, "class")) SharedTokenType.CLASS
                else SharedTokenType.IDENTIFIER
            'e' ->
                if (matchSlice(start, length, "extends")) SharedTokenType.EXTENDS
                else SharedTokenType.IDENTIFIER
            'r' ->
                if (matchSlice(start, length, "reified")) SharedTokenType.REIFIED
                else SharedTokenType.IDENTIFIER
            's' ->
                if (matchSlice(start, length, "super")) SharedTokenType.SUPER
                else SharedTokenType.IDENTIFIER
            else -> SharedTokenType.IDENTIFIER
        }
    }

    /** Scans a double-quoted string literal starting at [start]. */
    private fun scanStringLiteral(start: Int): Token {
        index = start + 1
        while (index < endExclusive) {
            val c = text[index++]
            if (c == '"') {
                break
            } else if (c == '\\' && index < endExclusive) {
                index++
            }
        }
        return createToken(
            SharedTokenType.STRING_LITERAL,
            start,
            index,
        )
    }

    /** Scans a single-quoted character literal starting at [start]. */
    private fun scanCharLiteral(start: Int): Token {
        index = start + 1
        while (index < endExclusive) {
            val c = text[index++]
            if (c == '\'') {
                break
            } else if (c == '\\' && index < endExclusive) {
                index++
            }
        }
        return createToken(
            SharedTokenType.CHAR_LITERAL,
            start,
            index,
        )
    }

    /** Scans a numeric literal (decimal, hex, integer, or floating-point) starting at [start]. */
    private fun scanNumberLiteral(start: Int): Token {
        index = start + 1
        val isHex =
            index < endExclusive && text[start] == '0' && (text[index] == 'x' || text[index] == 'X')
        while (index < endExclusive) {
            val c = text[index]
            if (c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' || c == '_') {
                index++
                val isExponent = if (isHex) (c == 'p' || c == 'P') else (c == 'e' || c == 'E')
                if (
                    isExponent && index < endExclusive && (text[index] == '+' || text[index] == '-')
                ) {
                    index++
                }
            } else if (
                c == '.' &&
                    index + 1 < endExclusive &&
                    (text[index + 1] in '0'..'9' ||
                        (isHex && (text[index + 1] in 'a'..'f' || text[index + 1] in 'A'..'F')))
            ) {
                index += 2
            } else {
                break
            }
        }
        return createToken(
            SharedTokenType.NUMBER_LITERAL,
            start,
            index,
        )
    }

    /** Scans a Java/Kotlin identifier or keyword starting at [start]. */
    protected open fun scanIdentifierOrKeyword(start: Int): Token {
        index = start + 1
        while (index < endExclusive && Character.isJavaIdentifierPart(text[index])) {
            index++
        }
        val type = resolveKeywordOrIdentifier(start, index)
        return createToken(
            type,
            start,
            index,
        )
    }
}
