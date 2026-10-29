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

package com.android.tools.metalava.model.source.javadoc

import com.android.tools.metalava.model.source.doc.BaseDocCommentTest
import com.android.tools.metalava.model.source.doc.TestDocumentationIssueReporter
import kotlin.test.assertEquals
import org.junit.Test

class JavadocLexerTest : BaseDocCommentTest() {
    /**
     * Check that [text] is tokenized correctly by [JavadocLexer].
     *
     * Tokenizes [text] between [startInclusive] and [endExclusive] and verifies that the emitted
     * tokens match [expectedTokens]. When [includePosition] is true, position and offset
     * information is included in each token's string representation. Also asserts that any issues
     * reported during lexing match [expectedIssues].
     */
    private fun checkTokenize(
        text: String,
        expectedTokens: String,
        startInclusive: Int = 0,
        endExclusive: Int = text.length,
        expectedIssues: String = "",
        includePosition: Boolean = false,
    ) {
        val lexer =
            JavadocLexer(
                text,
                startInclusive,
                endExclusive,
                TestDocumentationIssueReporter(reporter),
            )
        val tokens = lexer.tokenize()
        val actual =
            tokens.joinToString("\n") { token ->
                val escaped =
                    token.text.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
                if (includePosition) {
                    "${token.type} '$escaped' (${token.line}:${token.charPositionInLine}, ${token.startOffset}..${token.endOffset})"
                } else {
                    "${token.type} '$escaped'"
                }
            }
        assertEquals(expectedTokens.trimIndent(), actual)
        assertJavadocParserIssues(expectedIssues)
    }

    @Test
    fun `Test plain text`() {
        checkTokenize(
            "Hello world",
            expectedTokens =
                """
                    TEXT_CONTENT 'Hello'
                    SPACE ' '
                    TEXT_CONTENT 'world'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test multiple spaces and tabs`() {
        checkTokenize(
            "a   \t  b",
            expectedTokens =
                """
                    TEXT_CONTENT 'a'
                    SPACE '   \t  '
                    TEXT_CONTENT 'b'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test standalone braces in default mode`() {
        checkTokenize(
            "{ not a tag } and } alone",
            expectedTokens =
                """
                    TEXT_CONTENT '{'
                    SPACE ' '
                    TEXT_CONTENT 'not'
                    SPACE ' '
                    TEXT_CONTENT 'a'
                    SPACE ' '
                    TEXT_CONTENT 'tag'
                    SPACE ' '
                    TEXT_CONTENT '}'
                    SPACE ' '
                    TEXT_CONTENT 'and'
                    SPACE ' '
                    TEXT_CONTENT '}'
                    SPACE ' '
                    TEXT_CONTENT 'alone'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test simple newlines`() {
        checkTokenize(
            "line1\nline2\r\nline3\rline4",
            expectedTokens =
                """
                    TEXT_CONTENT 'line1'
                    NEWLINE '\n'
                    TEXT_CONTENT 'line2'
                    NEWLINE '\r\n'
                    TEXT_CONTENT 'line3'
                    NEWLINE '\r'
                    TEXT_CONTENT 'line4'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test newline with continuation prefix`() {
        checkTokenize(
            "line1\n * line2\n   ** line3\n*line4",
            expectedTokens =
                """
                    TEXT_CONTENT 'line1'
                    NEWLINE '\n *'
                    SPACE ' '
                    TEXT_CONTENT 'line2'
                    NEWLINE '\n   **'
                    SPACE ' '
                    TEXT_CONTENT 'line3'
                    NEWLINE '\n*'
                    TEXT_CONTENT 'line4'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test newline without continuation asterisks`() {
        checkTokenize(
            "line1\n   line2",
            expectedTokens =
                """
                    TEXT_CONTENT 'line1'
                    NEWLINE '\n'
                    SPACE '   '
                    TEXT_CONTENT 'line2'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test inline tag - simple`() {
        checkTokenize(
            "{@link Foo}",
            expectedTokens =
                """
                    INLINE_TAG_START '{@'
                    INLINE_TAG_NAME 'link'
                    SPACE ' '
                    TEXT_CONTENT 'Foo'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test inline tag - in text`() {
        checkTokenize(
            "Before {@code a + b} after",
            expectedTokens =
                """
                    TEXT_CONTENT 'Before'
                    SPACE ' '
                    INLINE_TAG_START '{@'
                    INLINE_TAG_NAME 'code'
                    SPACE ' '
                    TEXT_CONTENT 'a'
                    SPACE ' '
                    TEXT_CONTENT '+'
                    SPACE ' '
                    TEXT_CONTENT 'b'
                    BRACE_CLOSE '}'
                    SPACE ' '
                    TEXT_CONTENT 'after'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test inline tag with nested braces`() {
        checkTokenize(
            "{@code { a } }",
            expectedTokens =
                """
                    INLINE_TAG_START '{@'
                    INLINE_TAG_NAME 'code'
                    SPACE ' '
                    BRACE_OPEN '{'
                    SPACE ' '
                    TEXT_CONTENT 'a'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test inline tag with nested inline tag`() {
        checkTokenize(
            "{@see {@link Bar}}",
            expectedTokens =
                """
                    INLINE_TAG_START '{@'
                    INLINE_TAG_NAME 'see'
                    SPACE ' '
                    INLINE_TAG_START '{@'
                    INLINE_TAG_NAME 'link'
                    SPACE ' '
                    TEXT_CONTENT 'Bar'
                    BRACE_CLOSE '}'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test inline tag - space after start`() {
        checkTokenize(
            "{@ link}",
            expectedIssues =
                """
                    1:3: unexpected ' ' after '{@' [InvalidJavadoc]
                """,
            expectedTokens =
                """
                    INLINE_TAG_START '{@'
                    INLINE_TAG_NAME 'link'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test inline tag - missing name`() {
        checkTokenize(
            "{@}",
            expectedIssues =
                """
                    1:3: missing inline tag name [InvalidJavadoc]
                """,
            expectedTokens =
                """
                    INLINE_TAG_START '{@'
                    TEXT_CONTENT '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test inline tag - unexpected character after start`() {
        checkTokenize(
            "{@#tag}",
            expectedIssues =
                """
                    1:3: unexpected '#' after '{@', expected tag name [InvalidJavadoc]
                """,
            expectedTokens =
                """
                    INLINE_TAG_START '{@'
                    INLINE_TAG_NAME 'tag'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test if tag - simple flag`() {
        checkTokenize(
            "{@if (flag(Flags.FOO)) { true text }}",
            expectedTokens =
                """
                    INLINE_IF_TAG_START '{@if'
                    PAREN_OPEN '('
                    IDENTIFIER 'flag'
                    PAREN_OPEN '('
                    IDENTIFIER 'Flags'
                    DOT '.'
                    IDENTIFIER 'FOO'
                    PAREN_CLOSE ')'
                    PAREN_CLOSE ')'
                    BRACE_OPEN '{'
                    SPACE ' '
                    TEXT_CONTENT 'true'
                    SPACE ' '
                    TEXT_CONTENT 'text'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test if tag with else`() {
        checkTokenize(
            "{@if (flag(FLAG)) { true } else { false }}",
            expectedTokens =
                """
                    INLINE_IF_TAG_START '{@if'
                    PAREN_OPEN '('
                    IDENTIFIER 'flag'
                    PAREN_OPEN '('
                    IDENTIFIER 'FLAG'
                    PAREN_CLOSE ')'
                    PAREN_CLOSE ')'
                    BRACE_OPEN '{'
                    SPACE ' '
                    TEXT_CONTENT 'true'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    IF_TAG_ELSE 'else'
                    BRACE_OPEN '{'
                    SPACE ' '
                    TEXT_CONTENT 'false'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test if tag - else prefix not keyword`() {
        checkTokenize(
            "{@if (flag(FLAG)) { true } elsewhere",
            expectedIssues =
                """
                    1:28: unexpected 'elsewhere' in '@if' tag [InvalidJavadoc]
                """,
            expectedTokens =
                """
                    INLINE_IF_TAG_START '{@if'
                    PAREN_OPEN '('
                    IDENTIFIER 'flag'
                    PAREN_OPEN '('
                    IDENTIFIER 'FLAG'
                    PAREN_CLOSE ')'
                    PAREN_CLOSE ')'
                    BRACE_OPEN '{'
                    SPACE ' '
                    TEXT_CONTENT 'true'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test if tag with nested inline tag`() {
        checkTokenize(
            "{@if (flag(FLAG)) { {@link Foo} }}",
            expectedTokens =
                """
                    INLINE_IF_TAG_START '{@if'
                    PAREN_OPEN '('
                    IDENTIFIER 'flag'
                    PAREN_OPEN '('
                    IDENTIFIER 'FLAG'
                    PAREN_CLOSE ')'
                    PAREN_CLOSE ')'
                    BRACE_OPEN '{'
                    SPACE ' '
                    INLINE_TAG_START '{@'
                    INLINE_TAG_NAME 'link'
                    SPACE ' '
                    TEXT_CONTENT 'Foo'
                    BRACE_CLOSE '}'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test if tag with nested if tag`() {
        checkTokenize(
            "{@if (flag(A)) { {@if (flag(B)) { nested }} }}",
            expectedTokens =
                """
                    INLINE_IF_TAG_START '{@if'
                    PAREN_OPEN '('
                    IDENTIFIER 'flag'
                    PAREN_OPEN '('
                    IDENTIFIER 'A'
                    PAREN_CLOSE ')'
                    PAREN_CLOSE ')'
                    BRACE_OPEN '{'
                    SPACE ' '
                    INLINE_IF_TAG_START '{@if'
                    PAREN_OPEN '('
                    IDENTIFIER 'flag'
                    PAREN_OPEN '('
                    IDENTIFIER 'B'
                    PAREN_CLOSE ')'
                    PAREN_CLOSE ')'
                    BRACE_OPEN '{'
                    SPACE ' '
                    TEXT_CONTENT 'nested'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    BRACE_CLOSE '}'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test tag name starting with if but longer`() {
        checkTokenize(
            "{@ifdef foo}",
            expectedTokens =
                """
                    INLINE_TAG_START '{@'
                    INLINE_TAG_NAME 'ifdef'
                    SPACE ' '
                    TEXT_CONTENT 'foo'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test expr mode - unclosed parenthesis before brace`() {
        checkTokenize(
            "{@if (flag(FLAG) { body }}",
            expectedTokens =
                """
                    INLINE_IF_TAG_START '{@if'
                    PAREN_OPEN '('
                    IDENTIFIER 'flag'
                    PAREN_OPEN '('
                    IDENTIFIER 'FLAG'
                    PAREN_CLOSE ')'
                    BRACE_OPEN '{'
                    SPACE ' '
                    TEXT_CONTENT 'body'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test expr mode - unexpected character`() {
        checkTokenize(
            "{@if (flag(FLAG) #) { body }}",
            expectedIssues =
                """
                    1:18: unexpected '#' in expression [InvalidJavadoc]
                """,
            expectedTokens =
                """
                    INLINE_IF_TAG_START '{@if'
                    PAREN_OPEN '('
                    IDENTIFIER 'flag'
                    PAREN_OPEN '('
                    IDENTIFIER 'FLAG'
                    PAREN_CLOSE ')'
                    PAREN_CLOSE ')'
                    BRACE_OPEN '{'
                    SPACE ' '
                    TEXT_CONTENT 'body'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test if tag - unexpected character in if tag`() {
        checkTokenize(
            "{@if (flag(FLAG)) ? { body }}",
            expectedIssues =
                """
                    1:19: unexpected '?' in '@if' tag [InvalidJavadoc]
                """,
            expectedTokens =
                """
                    INLINE_IF_TAG_START '{@if'
                    PAREN_OPEN '('
                    IDENTIFIER 'flag'
                    PAREN_OPEN '('
                    IDENTIFIER 'FLAG'
                    PAREN_CLOSE ')'
                    PAREN_CLOSE ')'
                    BRACE_OPEN '{'
                    SPACE ' '
                    TEXT_CONTENT 'body'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test token positions and offsets`() {
        checkTokenize(
            "line1\n * line2",
            includePosition = true,
            expectedTokens =
                """
                    TEXT_CONTENT 'line1' (1:0, 0..5)
                    NEWLINE '\n *' (1:5, 5..8)
                    SPACE ' ' (2:2, 8..9)
                    TEXT_CONTENT 'line2' (2:3, 9..14)
                    EOF '' (2:8, 14..14)
                """,
        )
    }

    @Test
    fun `Test subrange tokenization`() {
        checkTokenize(
            "prefix Hello world suffix",
            startInclusive = 7,
            endExclusive = 18,
            expectedTokens =
                """
                    TEXT_CONTENT 'Hello'
                    SPACE ' '
                    TEXT_CONTENT 'world'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test if tag - subrange boundary inside else`() {
        val text = "{@if (flag) { true } else { false }}"
        val elseStart = text.indexOf("else")
        checkTokenize(
            text,
            startInclusive = 0,
            endExclusive = elseStart + 2,
            expectedIssues =
                """
                    1:22: unexpected 'el' in '@if' tag [InvalidJavadoc]
                """,
            expectedTokens =
                """
                    INLINE_IF_TAG_START '{@if'
                    PAREN_OPEN '('
                    IDENTIFIER 'flag'
                    PAREN_CLOSE ')'
                    BRACE_OPEN '{'
                    SPACE ' '
                    TEXT_CONTENT 'true'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test if tag - multiple unexpected characters`() {
        checkTokenize(
            "{@if (flag(FLAG)) ??? { body }}",
            expectedIssues =
                """
                    1:19: unexpected '???' in '@if' tag [InvalidJavadoc]
                """,
            expectedTokens =
                """
                    INLINE_IF_TAG_START '{@if'
                    PAREN_OPEN '('
                    IDENTIFIER 'flag'
                    PAREN_OPEN '('
                    IDENTIFIER 'FLAG'
                    PAREN_CLOSE ')'
                    PAREN_CLOSE ')'
                    BRACE_OPEN '{'
                    SPACE ' '
                    TEXT_CONTENT 'body'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test expr mode - multiple unexpected characters`() {
        checkTokenize(
            "{@if (flag(FLAG) #%^) { body }}",
            expectedIssues =
                """
                    1:18: unexpected '#%^' in expression [InvalidJavadoc]
                """,
            expectedTokens =
                """
                    INLINE_IF_TAG_START '{@if'
                    PAREN_OPEN '('
                    IDENTIFIER 'flag'
                    PAREN_OPEN '('
                    IDENTIFIER 'FLAG'
                    PAREN_CLOSE ')'
                    PAREN_CLOSE ')'
                    BRACE_OPEN '{'
                    SPACE ' '
                    TEXT_CONTENT 'body'
                    SPACE ' '
                    BRACE_CLOSE '}'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test inline tag - multiple unexpected characters after start`() {
        checkTokenize(
            "{@###tag}",
            expectedIssues =
                """
                    1:3: unexpected '###' after '{@', expected tag name [InvalidJavadoc]
                """,
            expectedTokens =
                """
                    INLINE_TAG_START '{@'
                    INLINE_TAG_NAME 'tag'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test inline tag - multiple spaces after start`() {
        checkTokenize(
            "{@   link}",
            expectedIssues =
                """
                    1:3: unexpected '   ' after '{@' [InvalidJavadoc]
                """,
            expectedTokens =
                """
                    INLINE_TAG_START '{@'
                    INLINE_TAG_NAME 'link'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test inline tag - incomplete at EOF`() {
        checkTokenize(
            "{@",
            expectedIssues =
                """
                    1:3: missing inline tag name [InvalidJavadoc]
                """,
            expectedTokens =
                """
                    INLINE_TAG_START '{@'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test inline tag - space then EOF`() {
        checkTokenize(
            "{@ ",
            expectedIssues =
                """
                    1:3: unexpected ' ' after '{@' [InvalidJavadoc]
                    1:4: missing inline tag name [InvalidJavadoc]
                """,
            expectedTokens =
                """
                    INLINE_TAG_START '{@'
                    EOF ''
                """,
        )
    }
}
