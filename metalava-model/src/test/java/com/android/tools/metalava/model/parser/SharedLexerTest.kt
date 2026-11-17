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

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class SharedLexerTest {
    private fun checkTokenize(
        text: String,
        expectedTokens: String,
        startInclusive: Int = 0,
        endExclusive: Int = text.length,
        includePosition: Boolean = false,
    ) {
        val lexer = SharedLexer(text, startInclusive, endExclusive)
        val tokenStream = lexer.tokenize()
        val tokens = buildList {
            do {
                val token = tokenStream.consume()
                add(token)
            } while (token.type != SharedTokenType.EOF)
        }
        val actual = tokens.joinToString("\n") { it.format(text, includePosition) }
        assertEquals(expectedTokens.trimIndent(), actual)
    }

    @Test
    fun `Test identifiers and unknown tokens`() {
        checkTokenize(
            "foo bar_1 $ #",
            expectedTokens =
                """
                    IDENTIFIER 'foo'
                    IDENTIFIER 'bar_1'
                    IDENTIFIER '$'
                    UNKNOWN '#'
                    EOF ''
                """,
        )
        assertTrue(SharedTokenType.IDENTIFIER.canBeIdentifier)
        assertFalse(SharedTokenType.UNKNOWN.canBeIdentifier)
        assertFalse(SharedTokenType.EOF.canBeIdentifier)
    }

    @Test
    fun `Test simple and qualified class names`() {
        checkTokenize(
            "java.lang.String",
            expectedTokens =
                """
                    IDENTIFIER 'java'
                    DOT '.'
                    IDENTIFIER 'lang'
                    DOT '.'
                    IDENTIFIER 'String'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test string and char literals`() {
        checkTokenize(
            """"hi\"@" 'x' '\''""",
            expectedTokens =
                """
                    STRING_LITERAL '"hi\"@"'
                    CHAR_LITERAL ''x''
                    CHAR_LITERAL ''\'''
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test numeric and special float tokens`() {
        checkTokenize(
            "(0.0 / 0.0) +1 -2 3.5f 0x10",
            expectedTokens =
                """
                    PAREN_OPEN '('
                    NUMBER_LITERAL '0.0'
                    SLASH '/'
                    NUMBER_LITERAL '0.0'
                    PAREN_CLOSE ')'
                    PLUS '+'
                    NUMBER_LITERAL '1'
                    MINUS '-'
                    NUMBER_LITERAL '2'
                    NUMBER_LITERAL '3.5f'
                    NUMBER_LITERAL '0x10'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test decimal and hex number exponents`() {
        // In decimal numbers, 'e'/'E' introduces a signed exponent, whereas 'p'/'P' does not.
        // In hexadecimal numbers, 'p'/'P' introduces a signed binary exponent, whereas 'e'/'E' is a
        // hex digit so a following '-' or '+' is a separate token.
        checkTokenize(
            "1e-5 1E-5 1p-5 1P-5 0x1p-5 0x1P-5 0x1.8p-5 0x1e-1 0x1E-1",
            expectedTokens =
                """
                    NUMBER_LITERAL '1e-5'
                    NUMBER_LITERAL '1E-5'
                    NUMBER_LITERAL '1p'
                    MINUS '-'
                    NUMBER_LITERAL '5'
                    NUMBER_LITERAL '1P'
                    MINUS '-'
                    NUMBER_LITERAL '5'
                    NUMBER_LITERAL '0x1p-5'
                    NUMBER_LITERAL '0x1P-5'
                    NUMBER_LITERAL '0x1.8p-5'
                    NUMBER_LITERAL '0x1e'
                    MINUS '-'
                    NUMBER_LITERAL '1'
                    NUMBER_LITERAL '0x1E'
                    MINUS '-'
                    NUMBER_LITERAL '1'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test whitespace and line comments`() {
        checkTokenize(
            """
                // Leading comment
                foo // inline comment
                    bar
            """,
            expectedTokens =
                """
                    IDENTIFIER 'foo'
                    IDENTIFIER 'bar'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test subrange tokenization and positions`() {
        val text = "prefix foo bar suffix"
        val start = text.indexOf("foo")
        val end = start + "foo bar".length
        checkTokenize(
            text,
            startInclusive = start,
            endExclusive = end,
            includePosition = true,
            expectedTokens =
                """
                    IDENTIFIER 'foo' (7..10)
                    IDENTIFIER 'bar' (11..14)
                    EOF '' (14..14)
                """,
        )
    }

    @Test
    fun `Test class literal tokens`() {
        checkTokenize(
            "List<String>[].class String::class",
            expectedTokens =
                """
                    IDENTIFIER 'List'
                    ANGLE_OPEN '<'
                    IDENTIFIER 'String'
                    ANGLE_CLOSE '>'
                    BRACKET_OPEN '['
                    BRACKET_CLOSE ']'
                    DOT '.'
                    CLASS 'class'
                    IDENTIFIER 'String'
                    DOUBLE_COLON '::'
                    CLASS 'class'
                    EOF ''
                """,
        )
        assertTrue(SharedTokenType.CLASS.canBeIdentifier)
    }

    @Test
    fun `Test array delimiter tokens`() {
        checkTokenize(
            "{1, 2, 3}",
            expectedTokens =
                """
                    BRACE_OPEN '{'
                    NUMBER_LITERAL '1'
                    COMMA ','
                    NUMBER_LITERAL '2'
                    COMMA ','
                    NUMBER_LITERAL '3'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test annotation tokens`() {
        checkTokenize(
            """@pkg.MyAnno(a = 1, b = "hi")""",
            expectedTokens =
                """
                    AT '@'
                    IDENTIFIER 'pkg'
                    DOT '.'
                    IDENTIFIER 'MyAnno'
                    PAREN_OPEN '('
                    IDENTIFIER 'a'
                    EQUALS '='
                    NUMBER_LITERAL '1'
                    COMMA ','
                    IDENTIFIER 'b'
                    EQUALS '='
                    STRING_LITERAL '"hi"'
                    PAREN_CLOSE ')'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test nullability suffix tokens`() {
        checkTokenize(
            "int? String!",
            expectedTokens =
                """
                    IDENTIFIER 'int'
                    QUESTION '?'
                    IDENTIFIER 'String'
                    EXCLAMATION '!'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test wildcard bound keywords`() {
        checkTokenize(
            "? extends Number ? super T",
            expectedTokens =
                """
                    QUESTION '?'
                    EXTENDS 'extends'
                    IDENTIFIER 'Number'
                    QUESTION '?'
                    SUPER 'super'
                    IDENTIFIER 'T'
                    EOF ''
                """,
        )
        assertTrue(SharedTokenType.EXTENDS.canBeIdentifier)
        assertTrue(SharedTokenType.SUPER.canBeIdentifier)
    }

    @Test
    fun `Test varargs ellipsis token`() {
        checkTokenize(
            "String... int[]...",
            expectedTokens =
                """
                    IDENTIFIER 'String'
                    ELLIPSIS '...'
                    IDENTIFIER 'int'
                    BRACKET_OPEN '['
                    BRACKET_CLOSE ']'
                    ELLIPSIS '...'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test colon token`() {
        checkTokenize(
            "a: int",
            expectedTokens =
                """
                    IDENTIFIER 'a'
                    COLON ':'
                    IDENTIFIER 'int'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test type parameter tokens`() {
        checkTokenize(
            "<reified T extends Comparable<T> & java.io.Serializable>",
            expectedTokens =
                """
                    ANGLE_OPEN '<'
                    REIFIED 'reified'
                    IDENTIFIER 'T'
                    EXTENDS 'extends'
                    IDENTIFIER 'Comparable'
                    ANGLE_OPEN '<'
                    IDENTIFIER 'T'
                    ANGLE_CLOSE '>'
                    AMPERSAND '&'
                    IDENTIFIER 'java'
                    DOT '.'
                    IDENTIFIER 'io'
                    DOT '.'
                    IDENTIFIER 'Serializable'
                    ANGLE_CLOSE '>'
                    EOF ''
                """,
        )
        assertTrue(SharedTokenType.REIFIED.canBeIdentifier)
    }

    @Test
    fun `Test EOF token is returned on subsequent nextToken calls`() {
        val lexer = SharedLexer("int")
        assertEquals(SharedTokenType.IDENTIFIER, lexer.nextToken().type)
        val eof1 = lexer.nextToken()
        val eof2 = lexer.nextToken()
        assertEquals(SharedTokenType.EOF, eof1.type)
        assertEquals(eof1, eof2)
    }
}
