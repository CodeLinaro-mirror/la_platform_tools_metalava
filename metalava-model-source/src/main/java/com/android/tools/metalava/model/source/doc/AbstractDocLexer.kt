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

import com.android.tools.metalava.model.parser.AbstractLexer
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenType
import com.android.tools.metalava.reporter.Issues

/** Shared token type for the `else` keyword in conditional `@if` inline and block tags. */
internal val IF_TAG_ELSE_TOKEN = TokenType("IF_TAG_ELSE")

/**
 * Shared base class for documentation lexers ([DocCommentLexer] and
 * [com.android.tools.metalava.model.source.javadoc.JavadocLexer]).
 *
 * Provides common support for:
 * - Accumulating consecutive unexpected characters and reporting them as a single
 *   [Issues.INVALID_JAVADOC] diagnostic once a valid token or explicit issue is reached.
 * - Skipping horizontal whitespace, line breaks, and leading `*` continuation margins within
 *   conditional `@if` tags and expressions.
 * - Tokenizing parenthesized conditional expressions (`(<expr>)`) into [SharedTokenType] tokens.
 *
 * @param text the full string containing the documentation text to tokenize.
 * @param startInclusive the index in [text] where tokenization begins.
 * @param endExclusive the exclusive end index in [text] where tokenization ends.
 */
internal abstract class AbstractDocLexer(
    text: String,
    startInclusive: Int = 0,
    endExclusive: Int = text.length,

    /** Reporter used for lexical syntax diagnostics. */
    protected val reporter: DocumentationIssueReporter = DocumentationIssueReporter.THROWING,
) :
    AbstractLexer(
        text,
        startInclusive,
        endExclusive,
    ) {

    /**
     * Start index in [text] of an accumulated unexpected character sequence, or equal to
     * [unexpectedEndIndex] when none is pending.
     */
    private var unexpectedStartIndex: Int = startInclusive

    /** Exclusive end index in [text] of an accumulated unexpected character sequence. */
    private var unexpectedEndIndex: Int = startInclusive

    /** Context description (e.g. `"in '@if' tag"`) for an accumulated unexpected sequence. */
    private var unexpectedContext: String? = null

    /** Nesting depth of open parentheses within the active conditional expression. */
    private var exprParenDepth: Int = 0

    /** Creates and returns a [Token], flushing any pending unexpected character sequence first. */
    override fun createToken(
        type: TokenType,
        startIndex: Int,
        endIndex: Int,
    ): Token {
        flushUnexpected()
        return super.createToken(
            type,
            startIndex,
            endIndex,
        )
    }

    /**
     * Reports [issue] with [message] at [offset] via [reporter], flushing any pending unexpected
     * character sequence first.
     */
    protected fun reportIssue(
        issue: Issues.Issue,
        message: String,
        offset: Int = index,
    ) {
        flushUnexpected()
        reporter.report(issue, message, offset)
    }

    /** Records an unexpected character at [index] with [context] and advances [index]. */
    protected fun recordUnexpected(context: String) {
        if (unexpectedStartIndex == unexpectedEndIndex) {
            unexpectedStartIndex = index
            unexpectedContext = context
        }
        index++
        unexpectedEndIndex = index
    }

    /** Flushes any accumulated unexpected character sequence and reports it via [reporter]. */
    private fun flushUnexpected() {
        if (unexpectedStartIndex != unexpectedEndIndex) {
            val chunk = text.substring(unexpectedStartIndex, unexpectedEndIndex)
            reporter.report(
                Issues.INVALID_JAVADOC,
                "unexpected '$chunk' $unexpectedContext",
                unexpectedStartIndex,
            )
            unexpectedStartIndex = unexpectedEndIndex
            unexpectedContext = null
        }
    }

    /**
     * Initializes expression parenthesis tracking after consuming the opening `(` of an `@if`
     * condition.
     */
    protected fun enterExpr() {
        exprParenDepth = 1
    }

    /**
     * Called when [tokenizeExpr] finishes or recovers from the outermost `(<expr>)` condition so
     * the subclass can restore its enclosing `@if` lexer mode.
     */
    protected abstract fun exitExpr()

    /**
     * Tokenizes identifiers, `.`, `(`, and `)` inside a conditional `@if (<expr>)` expression,
     * calling [exitExpr] once the matching outer `)` is consumed or `{` / `}` triggers recovery.
     */
    protected fun tokenizeExpr(): Token {
        skipWhitespaceAndNewlines()
        if (index >= endExclusive) return Token.NONE

        val start = index
        val c = text[start]
        return when {
            c == '(' -> {
                index++
                exprParenDepth++
                createToken(
                    SharedTokenType.PAREN_OPEN,
                    start,
                    index,
                )
            }
            c == ')' -> {
                index++
                exprParenDepth--
                if (exprParenDepth == 0) {
                    exitExpr()
                }
                createToken(
                    SharedTokenType.PAREN_CLOSE,
                    start,
                    index,
                )
            }
            c == '.' -> {
                index++
                createToken(
                    SharedTokenType.DOT,
                    start,
                    index,
                )
            }
            c.isJavaIdentifierStart() -> {
                while (index < endExclusive && text[index].isJavaIdentifierPart()) {
                    index++
                }
                createToken(
                    SharedTokenType.IDENTIFIER,
                    start,
                    index,
                )
            }
            c == '{' || c == '}' -> {
                // Unclosed `(` before `{` or `}`: exit expression mode without consuming the brace
                // so the enclosing `@if` mode can handle or recover on it.
                exprParenDepth = 0
                exitExpr()
                Token.NONE
            }
            else -> {
                recordUnexpected("in expression")
                Token.NONE
            }
        }
    }

    /**
     * Skips horizontal whitespace and newlines (including leading `*` comment continuation margins
     * on new lines), returning `true` if at least one newline was crossed.
     */
    protected fun skipWhitespaceAndNewlines(): Boolean {
        var crossedNewline = false
        while (index < endExclusive) {
            val c = text[index]
            if (isHorizontalWhitespace(c)) {
                index++
            } else if (c == '\n' || c == '\r') {
                crossedNewline = true
                if (c == '\r' && index + 1 < endExclusive && text[index + 1] == '\n') {
                    index += 2
                } else {
                    index++
                }

                // Consume optional leading horizontal whitespace and `*` comment margin on the new
                // line so the outer loop can continue skipping spaces after `*`.
                var p = index
                while (p < endExclusive && isHorizontalWhitespace(text[p])) {
                    p++
                }
                if (p < endExclusive && text[p] == '*') {
                    while (p < endExclusive && text[p] == '*') {
                        p++
                    }
                    index = p
                }
            } else {
                break
            }
        }
        return crossedNewline
    }

    /**
     * Returns `true` if [index] is positioned at a standalone `else` keyword within
     * `[startInclusive, endExclusive)`.
     */
    protected fun isElseKeywordAtCurrentIndex(): Boolean =
        index + 4 <= endExclusive &&
            text.startsWith("else", index) &&
            (index + 4 == endExclusive || !text[index + 4].isJavaIdentifierPart())
}

/** Returns `true` if [c] is an ASCII letter (`a`..`z` or `A`..`Z`). */
internal fun isAsciiLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

/**
 * Returns `true` if [c] is a horizontal whitespace character (matching regex `\h`: space, tab, or
 * non-ASCII Unicode space separator, excluding line terminators).
 */
internal fun isHorizontalWhitespace(c: Char): Boolean =
    c == ' ' || c == '\t' || (c > '\u007f' && Character.isSpaceChar(c))
