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

import com.android.tools.metalava.model.parser.LineMap
import kotlin.test.assertEquals
import org.junit.Test

/** Unit tests for [DocCommentLexer]. */
class DocCommentLexerTest : BaseDocCommentTest() {
    /**
     * Tokenizes the body of [input] using [DocCommentLexer] and verifies that the formatted token
     * stream matches [expectedTokens] and reported issues match [expectedIssues].
     */
    private fun checkTokenize(
        input: String,
        expectedTokens: String,
        expectedIssues: String = "",
        includePosition: Boolean = false,
        trimIndent: Boolean = true,
    ) {
        val text = if (trimIndent) input.trimIndent() else input
        val lineMap = LineMap.create(text)
        val startInclusive = skipDocCommentStartToken(text)
        val endExclusive = skipDocCommentEndTokens(text).coerceAtLeast(startInclusive)
        val lexer =
            DocCommentLexer(
                text,
                startInclusive,
                endExclusive,
                TestDocumentationIssueReporter(reporter, lineMap),
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
        assertJavadocParserIssues(expectedIssues)
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

    /**
     * Verify tokenization of `@if (<expr>) { ... } else { ... }` with `:`-prefixed inner block
     * tags, followed by a top-level block tag.
     */
    @Test
    fun `Test @if block tag with else`() {
        checkTokenize(
            """
                /**
                 * Summary.
                 *
                 * @if (flag(pkg.Flags.MY_FLAG)) {
                 *     :@see First
                 *     :@deprecated Use {@link First} instead.
                 * } else {
                 *     :@see Second
                 * }
                 * @return result
                 */
            """,
            expectedTokens =
                """
                    DESCRIPTION '\n * Summary.\n *'
                    BLOCK_IF_TAG_START '@if'
                    PAREN_OPEN '('
                    IDENTIFIER 'flag'
                    PAREN_OPEN '('
                    IDENTIFIER 'pkg'
                    DOT '.'
                    IDENTIFIER 'Flags'
                    DOT '.'
                    IDENTIFIER 'MY_FLAG'
                    PAREN_CLOSE ')'
                    PAREN_CLOSE ')'
                    BRACE_OPEN '{'
                    BLOCK_TAG_START ':@'
                    BLOCK_TAG_NAME 'see'
                    DESCRIPTION 'First'
                    BLOCK_TAG_START ':@'
                    BLOCK_TAG_NAME 'deprecated'
                    DESCRIPTION 'Use {@link First} instead.'
                    BRACE_CLOSE '}'
                    IF_TAG_ELSE 'else'
                    BRACE_OPEN '{'
                    BLOCK_TAG_START ':@'
                    BLOCK_TAG_NAME 'see'
                    DESCRIPTION 'Second'
                    BRACE_CLOSE '}'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'return'
                    DESCRIPTION 'result'
                    EOF ''
                """,
        )
    }

    /** Verify that `@ifdef` is tokenized as a regular block tag rather than `@if`. */
    @Test
    fun `Test @ifdef is treated as normal block tag`() {
        checkTokenize(
            """
                /**
                 * @ifdef FOO
                 */
            """,
            expectedTokens =
                """
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'ifdef'
                    DESCRIPTION 'FOO'
                    EOF ''
                """,
        )
    }

    /**
     * Verify that unprefixed block tags inside either branch of `@if` report `InvalidJavadoc` and
     * still recover cleanly.
     */
    @Test
    fun `Test unprefixed block tag inside @if reports InvalidJavadoc`() {
        checkTokenize(
            """
                /**
                 * @if (flag(Flags.MY_FLAG)) {
                 *     @see First
                 * } else {
                 *     @see Second
                 * }
                 */
            """,
            expectedTokens =
                """
                    BLOCK_IF_TAG_START '@if'
                    PAREN_OPEN '('
                    IDENTIFIER 'flag'
                    PAREN_OPEN '('
                    IDENTIFIER 'Flags'
                    DOT '.'
                    IDENTIFIER 'MY_FLAG'
                    PAREN_CLOSE ')'
                    PAREN_CLOSE ')'
                    BRACE_OPEN '{'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'see'
                    DESCRIPTION 'First'
                    BRACE_CLOSE '}'
                    IF_TAG_ELSE 'else'
                    BRACE_OPEN '{'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'see'
                    DESCRIPTION 'Second'
                    BRACE_CLOSE '}'
                    EOF ''
                """,
            expectedIssues =
                """
                    3:8: block tag '@see' inside '@if' must be prefixed with ':' [InvalidJavadoc]
                    5:8: block tag '@see' inside '@if' must be prefixed with ':' [InvalidJavadoc]
                """,
        )
    }

    /** Verify that a `:`-prefixed block tag outside `@if` reports `InvalidJavadoc`. */
    @Test
    fun `Test colon-prefixed block tag outside @if reports InvalidJavadoc`() {
        checkTokenize(
            """
                /**
                 * :@see First
                 */
            """,
            expectedTokens =
                """
                    BLOCK_TAG_START ':@'
                    BLOCK_TAG_NAME 'see'
                    DESCRIPTION 'First'
                    EOF ''
                """,
            expectedIssues =
                """
                    2:4: block tag '@see' outside '@if' must not be prefixed with ':' [InvalidJavadoc]
                """,
        )
    }

    /**
     * Verify that block tags (`@<tag>` or `:@<tag>`) inside an inline `{@if ...}` tag report
     * `InvalidJavadoc` without splitting the description, while block tags after `{@if}` closes are
     * still tokenized normally.
     */
    @Test
    fun `Test block tag inside inline {@if} reports InvalidJavadoc`() {
        // TODO(b/479490213): Report `InvalidJavadoc` for block tags inside inline `{@if ...}` tags
        //  and keep scanning until `{@if}` closes instead of splitting into `BlockTagSection`s.
        checkTokenize(
            """
                /**
                 * {@if (flag(Flags.MY_FLAG)) {
                 *     New Javadoc.
                 *     :@see Ignored
                 * } else {
                 *     Old Javadoc.
                 *
                 *     @see First
                 *     @see Second
                 * }}
                 * @return result
                 */
            """,
            expectedTokens =
                """
                    DESCRIPTION '\n * {@if (flag(Flags.MY_FLAG)) {\n *     New Javadoc.'
                    BLOCK_TAG_START ':@'
                    BLOCK_TAG_NAME 'see'
                    DESCRIPTION 'Ignored\n * } else {\n *     Old Javadoc.\n *'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'see'
                    DESCRIPTION 'First'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'see'
                    DESCRIPTION 'Second\n * }}'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'return'
                    DESCRIPTION 'result'
                    EOF ''
                """,
            expectedIssues =
                """
                    4:8: block tag '@see' outside '@if' must not be prefixed with ':' [InvalidJavadoc]
                """,
        )
    }

    /**
     * Verify that line-starting `@<name>` sequences inside `{@code ...}` or `{@literal ...}` report
     * `InvalidJavadoc` without splitting the description, whether nested inside `{@if}`, wrapping
     * `{@if}`, or used at the top level.
     */
    @Test
    fun `Test block tag inside {@code} or {@literal} reports InvalidJavadoc`() {
        // TODO(b/479490213): Track inline tag braces and report `InvalidJavadoc` for block tags
        //  inside `{@code}` or `{@literal}` instead of splitting the description.
        checkTokenize(
            """
                /**
                 * {@if (flag(Flags.MY_FLAG)) {
                 *     <pre>{@code
                 *     @Override
                 *     void foo() {}
                 *     }</pre>
                 * }}
                 * <pre>{@literal
                 * {@if (flag(Flags.MY_FLAG)) {
                 * @Deprecated
                 * }}
                 * }</pre>
                 * <pre>{@code
                 * @SuppressWarnings("all")
                 * }</pre>
                 */
            """,
            expectedTokens =
                """
                    DESCRIPTION '\n * {@if (flag(Flags.MY_FLAG)) {\n *     <pre>{@code'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'Override'
                    DESCRIPTION '\n *     void foo() {}\n *     }</pre>\n * }}\n * <pre>{@literal\n * {@if (flag(Flags.MY_FLAG)) {'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'Deprecated'
                    DESCRIPTION '\n * }}\n * }</pre>\n * <pre>{@code'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'SuppressWarnings'
                    DESCRIPTION '("all")\n * }</pre>'
                    EOF ''
                """,
        )
    }

    /**
     * Verify that an unclosed inline tag before top-level block tags (such as `@param` or `@hide`)
     * does not swallow subsequent block tags into the description.
     */
    @Test
    fun `Test unclosed inline tag before block tag does not swallow block tag`() {
        checkTokenize(
            """
                /**
                 * Returns {@link Foo without closing brace.
                 *
                 * @param x value
                 * @hide
                 */
            """,
            expectedTokens =
                """
                    DESCRIPTION '\n * Returns {@link Foo without closing brace.\n *'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'param'
                    DESCRIPTION 'x value'
                    BLOCK_TAG_START '@'
                    BLOCK_TAG_NAME 'hide'
                    EOF ''
                """,
        )
    }
}
