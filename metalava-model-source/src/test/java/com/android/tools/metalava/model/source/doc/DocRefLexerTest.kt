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

package com.android.tools.metalava.model.source.doc

import com.android.tools.metalava.model.parser.SharedTokenType
import kotlin.test.assertEquals
import org.junit.Test

class DocRefLexerTest {
    private fun checkTokenize(
        text: String,
        expectedTokens: String,
        startInclusive: Int = 0,
        endExclusive: Int = text.length,
        includePosition: Boolean = false,
    ) {
        val lexer = DocRefLexer(text, startInclusive, endExclusive)
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
    fun `Test class and member references`() {
        checkTokenize(
            "pkg.Class#member",
            includePosition = true,
            expectedTokens =
                """
                    IDENTIFIER 'pkg' (0..3)
                    DOT '.' (3..4)
                    IDENTIFIER 'Class' (4..9)
                    HASH '#' (9..10)
                    IDENTIFIER 'member' (10..16)
                    EOF '' (16..16)
                """,
        )
    }

    @Test
    fun `Test URI fragment references`() {
        checkTokenize(
            "pkg.Class##my-anchor_1.2",
            includePosition = true,
            expectedTokens =
                """
                    IDENTIFIER 'pkg' (0..3)
                    DOT '.' (3..4)
                    IDENTIFIER 'Class' (4..9)
                    URI_FRAGMENT '##my-anchor_1.2' (9..24)
                    EOF '' (24..24)
                """,
        )
    }

    @Test
    fun `Test empty and hash-terminated URI fragments`() {
        checkTokenize(
            "## ###frag",
            includePosition = true,
            expectedTokens =
                """
                    URI_FRAGMENT '##' (0..2)
                    URI_FRAGMENT '##' (3..5)
                    HASH '#' (5..6)
                    IDENTIFIER 'frag' (6..10)
                    EOF '' (10..10)
                """,
        )
    }

    @Test
    fun `Test method reference with generic and varargs parameters`() {
        checkTokenize(
            "Class#foo(List<? extends Number>p1, String... p2)",
            expectedTokens =
                """
                    IDENTIFIER 'Class'
                    HASH '#'
                    IDENTIFIER 'foo'
                    PAREN_OPEN '('
                    IDENTIFIER 'List'
                    ANGLE_OPEN '<'
                    QUESTION '?'
                    EXTENDS 'extends'
                    IDENTIFIER 'Number'
                    ANGLE_CLOSE '>'
                    IDENTIFIER 'p1'
                    COMMA ','
                    IDENTIFIER 'String'
                    ELLIPSIS '...'
                    IDENTIFIER 'p2'
                    PAREN_CLOSE ')'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test Java identifiers vs wildcard keywords`() {
        checkTokenize(
            "extends super class reified dynamic",
            expectedTokens =
                """
                    EXTENDS 'extends'
                    SUPER 'super'
                    IDENTIFIER 'class'
                    IDENTIFIER 'reified'
                    IDENTIFIER 'dynamic'
                    EOF ''
                """,
        )
    }
}
