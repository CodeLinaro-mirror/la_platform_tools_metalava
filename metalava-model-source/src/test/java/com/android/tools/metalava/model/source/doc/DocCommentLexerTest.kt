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

import kotlin.test.assertEquals
import org.junit.Test

/** Unit tests for [DocCommentLexer]. */
class DocCommentLexerTest : BaseDocCommentTest() {
    /**
     * Tokenizes the body of [input] using [DocCommentLexer] and verifies that the formatted token
     * stream matches [expectedTokens].
     */
    private fun checkTokenize(
        input: String,
        expectedTokens: String,
        includePosition: Boolean = false,
        trimIndent: Boolean = true,
    ) {
        val text = if (trimIndent) input.trimIndent() else input
        val startInclusive = skipDocCommentStartToken(text)
        val endExclusive = skipDocCommentEndTokens(text).coerceAtLeast(startInclusive)
        val lexer =
            DocCommentLexer(
                text,
                startInclusive,
                endExclusive,
            )
        val tokenStream = lexer.tokenize()
        val tokens = buildList {
            do {
                val token = tokenStream.consume()
                add(token)
            } while (token.type != DocCommentTokenType.EOF)
        }
        val actual = tokens.joinToString("\n") { it.format(text, includePosition) }
        assertEquals(expectedTokens.trimIndent(), actual)
    }

    /** Verify that an empty doc comment body emits only [DocCommentTokenType.EOF]. */
    @Test
    fun `Test empty comment`() {
        checkTokenize(
            "/***/",
            expectedTokens =
                """
                    EOF ''
                """,
        )
    }

    /** Verify that a comment without block tags emits a single `DESCRIPTION` span before `EOF`. */
    @Test
    fun `Test description only`() {
        checkTokenize(
            """
                /**
                 * Main description.
                 */
            """,
            expectedTokens =
                """
                    DESCRIPTION '\n * Main description.'
                    EOF ''
                """,
        )
    }

    /** Verify partitioning of a main description followed by multiple `@see` block tags. */
    @Test
    fun `Test description and block tags`() {
        checkTokenize(
            """
                /**
                 * Some text
                 * @see something
                 * @see other thing
                 */
            """,
            expectedTokens =
                """
                    DESCRIPTION '\n * Some text'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'see'
                    DESCRIPTION 'something'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'see'
                    DESCRIPTION 'other thing'
                    EOF ''
                """,
        )
    }

    /** Verify that consecutive block tags with empty bodies do not emit empty `DESCRIPTION`s. */
    @Test
    fun `Test empty block tags`() {
        checkTokenize(
            """
                /**
                 * @hide
                 * @deprecated
                 */
            """,
            expectedTokens =
                """
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'hide'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'deprecated'
                    EOF ''
                """,
        )
    }

    /**
     * Verify that mid-line `@` characters and `{@...}` inline tags remain part of the enclosing
     * `DESCRIPTION` token rather than starting new block tags.
     */
    @Test
    fun `Test mid-line @ and inline tags are part of description`() {
        checkTokenize(
            """
                /**
                 * Text with @midline and {@code inline}
                 * @deprecated reason @notABlockTag
                 */
            """,
            expectedTokens =
                """
                    DESCRIPTION '\n * Text with @midline and {@code inline}'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'deprecated'
                    DESCRIPTION 'reason @notABlockTag'
                    EOF ''
                """,
        )
    }

    /**
     * Verify that a line-starting `@` not immediately followed by an ASCII letter is kept in the
     * `DESCRIPTION` span.
     */
    @Test
    fun `Test @ followed by non-letter is not a block tag`() {
        checkTokenize(
            """
                /**
                 * @123 not a tag
                 * @ alone
                 * @see valid
                 */
            """,
            expectedTokens =
                """
                    DESCRIPTION '\n * @123 not a tag\n * @ alone'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'see'
                    DESCRIPTION 'valid'
                    EOF ''
                """,
        )
    }

    /** Verify that `\r\n` and standalone `\r` line breaks allow subsequent block tag matching. */
    @Test
    fun `Test CRLF and CR line endings`() {
        checkTokenize(
            "/**\r\n * Summary\r\n * @see First\r * @return Second\r\n */",
            trimIndent = false,
            expectedTokens =
                """
                    DESCRIPTION '\r\n * Summary'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'see'
                    DESCRIPTION 'First'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'return'
                    DESCRIPTION 'Second'
                    EOF ''
                """,
        )
    }
}
