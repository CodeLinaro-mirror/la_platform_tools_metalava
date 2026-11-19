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

import com.android.tools.metalava.model.parser.AbstractLexer
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenType
import com.android.tools.metalava.model.source.doc.DocumentationIssueReporter
import com.android.tools.metalava.reporter.Issues
import java.util.ArrayDeque

/** Token types produced by [JavadocLexer]. */
internal object JavadocTokenType {
    /** Plain text content outside of tags or within tag bodies. */
    val TEXT_CONTENT = TokenType("TEXT_CONTENT")

    /** One or more horizontal whitespace characters (spaces or tabs). */
    val SPACE = TokenType("SPACE")

    /**
     * A line break (`\n`, `\r\n`, or `\r`), optionally followed by horizontal whitespace and one or
     * more asterisks on the next line.
     */
    val NEWLINE = TokenType("NEWLINE")

    /** The `{@` prefix introducing an inline tag. */
    val INLINE_TAG_START = TokenType("INLINE_TAG_START")

    /** The name of an inline tag (e.g. `link`, `code`, etc.) following `{@`. */
    val INLINE_TAG_NAME = TokenType("INLINE_TAG_NAME")

    /** An opening curly brace `{`. */
    val BRACE_OPEN = SharedTokenType.BRACE_OPEN

    /** A closing curly brace `}`. */
    val BRACE_CLOSE = SharedTokenType.BRACE_CLOSE

    /** The `{@if` prefix introducing a conditional Javadoc tag. */
    val INLINE_IF_TAG_START = TokenType("INLINE_IF_TAG_START")

    /** The `else` keyword in an `{@if}` tag. */
    val IF_TAG_ELSE = TokenType("IF_TAG_ELSE")

    /** An opening parenthesis `(`. */
    val PAREN_OPEN = SharedTokenType.PAREN_OPEN

    /** A closing parenthesis `)`. */
    val PAREN_CLOSE = SharedTokenType.PAREN_CLOSE

    /** An identifier in an expression (e.g. function or flag name). */
    val IDENTIFIER = SharedTokenType.IDENTIFIER

    /** A dot `.` separator in a qualified identifier. */
    val DOT = SharedTokenType.DOT

    /** End of file / input marker. */
    val EOF = SharedTokenType.EOF
}

/** Lexer modes for [JavadocLexer]. */
private enum class LexerMode {
    /** The default mode for general comment description text. */
    DEFAULT,

    /** Mode active immediately after `{@`, expecting an inline tag name. */
    INLINE_TAG,

    /** Mode active within inline tags or brace blocks where `{` and `}` must be balanced. */
    BALANCED_BRACE,

    /** Mode active within `{@if ...}`, ignoring whitespace and recognizing delimiters. */
    INLINE_IF_TAG,

    /** Mode active within conditional expressions, recognizing identifiers, dots, and parens. */
    EXPR,
}

/**
 * A modal lexer for Javadoc comments.
 *
 * Scans a slice of Javadoc comment text from [startInclusive] to [endExclusive] and converts it
 * into a stream of [Token]s ending with [JavadocTokenType.EOF].
 *
 * ### Architecture and How It Works
 *
 * Javadoc syntax is context-sensitive:
 * - In general description text, braces `{` and `}` can appear unescaped in prose or code snippets
 *   without requiring matching closing braces, while `{@` signals the beginning of an inline tag.
 * - Within inline tags (such as `{@link ...}` or `{@code ...}`), opening and closing braces must
 *   balance so that the outermost closing `}` correctly terminates the tag, while any inner braces
 *   are preserved.
 * - In conditional Javadoc tags (`{@if (expr)} ... {@else} ...}`), the lexer must recognize
 *   expression syntax (identifiers, dot operators, parentheses) where whitespace is ignored,
 *   followed by brace-delimited branch bodies.
 *
 * To handle these distinct syntactic contexts, [JavadocLexer] uses a mode stack of [LexerMode]s,
 * starting with [LexerMode.DEFAULT]. As delimiters are encountered, modes are pushed or popped:
 * 1. **[LexerMode.DEFAULT]**:
 *     - The initial mode for general comment description text outside any tag.
 *     - Matches newlines ([JavadocTokenType.NEWLINE]), horizontal whitespace
 *       ([JavadocTokenType.SPACE]), inline tag starts (`{@` as [JavadocTokenType.INLINE_TAG_START],
 *       `{@if` as [JavadocTokenType.INLINE_IF_TAG_START]), or general text
 *       ([JavadocTokenType.TEXT_CONTENT]).
 *     - Standalone `{` (not followed by `@`) and `}` are emitted as plain text.
 * 2. **[LexerMode.INLINE_TAG]**:
 *     - Entered immediately upon encountering `{@`.
 *     - Matches the tag name (`[a-zA-Z]+`, emitted as [JavadocTokenType.INLINE_TAG_NAME]) and
 *       transitions to [LexerMode.BALANCED_BRACE] to tokenize the tag body.
 *     - If unexpected characters (e.g. whitespace) appear before the tag name, an issue is reported
 *       via [reporter] and the lexer recovers.
 * 3. **[LexerMode.BALANCED_BRACE]**:
 *     - Active within inline tag bodies or conditional branch bodies.
 *     - Tracks brace nesting: an opening `{` ([JavadocTokenType.BRACE_OPEN]) pushes another
 *       [LexerMode.BALANCED_BRACE] mode, while a closing `}` ([JavadocTokenType.BRACE_CLOSE]) pops
 *       the mode.
 *     - Nested inline tags (`{@` and `{@if`) are also recognized.
 * 4. **[LexerMode.INLINE_IF_TAG]**:
 *     - Active inside `{@if ...}` tags.
 *     - Skips horizontal whitespace and newlines (including continuation asterisks).
 *     - Matches `(` ([JavadocTokenType.PAREN_OPEN], switching to [LexerMode.EXPR]), `{`
 *       ([JavadocTokenType.BRACE_OPEN], switching to [LexerMode.BALANCED_BRACE] for branch bodies),
 *       `}` ([JavadocTokenType.BRACE_CLOSE], popping the [LexerMode.INLINE_IF_TAG] mode), and the
 *       `else` keyword ([JavadocTokenType.IF_TAG_ELSE]).
 * 5. **[LexerMode.EXPR]**:
 *     - Active within conditional expressions (`{@if (expr)}`).
 *     - Skips horizontal whitespace and newlines.
 *     - Recognizes parentheses `(` and `)`, dot operators `.`, and identifiers
 *       ([JavadocTokenType.IDENTIFIER]).
 *     - If an unexpected `{` or `}` is encountered, pops [LexerMode.EXPR] to recover from an
 *       unclosed expression.
 *
 * ### Comment Formatting and Newline Normalization
 *
 * Multi-line Javadoc comments typically prefix continuation lines with optional whitespace and one
 * or more asterisks (e.g. `\n * `). The lexer collapses a newline sequence (`\r\n`, `\n`, or `\r`)
 * and any following continuation asterisks on the next line into a single
 * [JavadocTokenType.NEWLINE] token in [LexerMode.DEFAULT] and [LexerMode.BALANCED_BRACE] modes. In
 * [LexerMode.INLINE_IF_TAG] and [LexerMode.EXPR] modes, newlines and continuation asterisks are
 * skipped along with whitespace.
 *
 * ### Position Tracking and Issue Reporting
 *
 * The lexer tracks `startIndex` and `endIndex` (absolute character offsets within [text]). Every
 * emitted [Token] carries `startOffset` and `endOffset` within [text]. Any lexical syntax errors
 * encountered during scanning (such as unexpected characters after `{@` or in expressions) are
 * reported directly via [reporter].
 *
 * @param text the full string containing the Javadoc text to tokenize.
 * @param startInclusive the index in [text] where tokenization should begin.
 * @param endExclusive the index in [text] where tokenization should end.
 * @param reporter used for reporting any syntax issues encountered during lexing.
 */
internal class JavadocLexer(
    text: String,
    startInclusive: Int,
    endExclusive: Int,
    private val reporter: DocumentationIssueReporter,
) :
    AbstractLexer(
        text,
        startInclusive,
        endExclusive,
    ) {
    /** Stack of [LexerMode]s controlling context-dependent tokenization. */
    private val modeStack = ArrayDeque<LexerMode>()

    /**
     * Start index in [text] of an unexpected character sequence, or equal to [unexpectedEndIndex]
     * if none.
     */
    private var unexpectedStartIndex = startInclusive

    /** Exclusive end index in [text] of an unexpected character sequence. */
    private var unexpectedEndIndex = startInclusive

    /** Context description for the unexpected character sequence (e.g. "in expression"). */
    private var unexpectedContext: String? = null

    /** Whether the end of input has already been reached and checked. */
    private var reachedEof = false

    init {
        modeStack.push(LexerMode.DEFAULT)
    }

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
     * Reports an issue via [reporter], flushing any pending unexpected character sequence first.
     */
    private fun reportIssue(
        issue: Issues.Issue,
        message: String,
    ) {
        flushUnexpected()
        reporter.report(issue, message, index)
    }

    /** Records an unexpected character with the given [context], advancing [index]. */
    private fun recordUnexpected(context: String) {
        if (unexpectedStartIndex == unexpectedEndIndex) {
            unexpectedStartIndex = index
            unexpectedContext = context
        }
        index++
        unexpectedEndIndex = index
    }

    /** Flushes any accumulated unexpected character sequence and reports it as an issue. */
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

    override fun nextToken(): Token {
        while (index < endExclusive) {
            val currentMode = modeStack.peek()
            val token =
                when (currentMode) {
                    // In DEFAULT mode: general comment text outside tags, matching newlines,
                    // spaces, inline tag starts, or plain text.
                    LexerMode.DEFAULT -> tokenizeDefault()

                    // In INLINE_TAG mode: immediately after '{@', expecting the tag name (e.g.
                    // 'link', 'code').
                    LexerMode.INLINE_TAG -> tokenizeInlineTag()

                    // In BALANCED_BRACE mode: inside an inline tag or brace block, tracking '{' and
                    // '}' nesting.
                    LexerMode.BALANCED_BRACE -> tokenizeBalancedBrace()

                    // In INLINE_IF_TAG mode: inside '{@if ...}', ignoring whitespace and matching
                    // '(', '{', '}', and 'else'.
                    LexerMode.INLINE_IF_TAG -> tokenizeInlineIfTag()

                    // In EXPR mode: inside the condition of '{@if (expr)}', matching identifiers,
                    // dots, and parens.
                    LexerMode.EXPR -> tokenizeExpr()
                }
            if (token != Token.NONE) {
                return token
            }
        }

        // TokenProducer.nextToken() must continue to return an EOF token on all subsequent calls
        // once the input is exhausted. Only run end-of-input checks the first time EOF is reached.
        if (!reachedEof) {
            reachedEof = true

            // If the input ended while expecting an inline tag name (e.g. '{@' at EOF), report the
            // missing tag name issue and pop the mode.
            if (modeStack.peek() == LexerMode.INLINE_TAG) {
                reportIssue(
                    Issues.INVALID_JAVADOC,
                    "missing inline tag name",
                )
                modeStack.pop()
            }
        }

        return createToken(
            JavadocTokenType.EOF,
            endExclusive,
            endExclusive,
        )
    }

    /**
     * Attempt to match a newline sequence (`\r\n`, `\n`, or `\r`), followed by optional
     * continuation prefix on the next line (optional horizontal whitespace followed by one or more
     * asterisks).
     *
     * If matched, advances [index] and returns a [JavadocTokenType.NEWLINE] token. Otherwise
     * returns [Token.NONE].
     */
    private fun tryMatchNewline(): Token {
        // Return Token.NONE if there are no characters remaining to match.
        if (index >= endExclusive) return Token.NONE
        val c = text[index]

        // If the current character is not a newline indicator (\n or \r), then no newline matches.
        if (c != '\n' && c != '\r') return Token.NONE

        val startIndex = index

        // Handle \r\n as a single 2-character newline; otherwise consume a single \n or \r.
        if (c == '\r' && index + 1 < endExclusive && text[index + 1] == '\n') {
            index += 2
        } else {
            index++
        }

        // Match optional leading whitespace (spaces/tabs) on the next line before continuation
        // asterisks.
        var p = index
        while (p < endExclusive && (text[p] == ' ' || text[p] == '\t')) {
            p++
        }

        // If followed by one or more asterisks '*', consume them as part of the newline
        // continuation prefix.
        if (p < endExclusive && text[p] == '*') {
            while (p < endExclusive && text[p] == '*') {
                p++
            }
            index = p
        }

        return createToken(
            JavadocTokenType.NEWLINE,
            startIndex,
            index,
        )
    }

    /**
     * Attempt to match one or more horizontal whitespace characters (spaces or tabs).
     *
     * If matched, advances [index] and returns a [JavadocTokenType.SPACE] token. Otherwise returns
     * [Token.NONE].
     */
    private fun tryMatchSpace(): Token {
        // Return Token.NONE if there are no characters remaining to match.
        if (index >= endExclusive) return Token.NONE
        val c = text[index]

        // If the current character is not horizontal whitespace (space or tab), return Token.NONE.
        if (c != ' ' && c != '\t') return Token.NONE

        val startIndex = index

        // Consume all consecutive horizontal whitespace characters.
        while (index < endExclusive && (text[index] == ' ' || text[index] == '\t')) {
            index++
        }

        return createToken(
            JavadocTokenType.SPACE,
            startIndex,
            index,
        )
    }

    /**
     * Attempt to match an inline tag start sequence (`{@if` or `{@`).
     *
     * If `{@if` is followed by a non-identifier character, switches to [LexerMode.INLINE_IF_TAG]
     * and returns [JavadocTokenType.INLINE_IF_TAG_START]. Otherwise switches to
     * [LexerMode.INLINE_TAG] and returns [JavadocTokenType.INLINE_TAG_START].
     */
    private fun tryMatchInlineTagStart(): Token {
        // Check if there are at least two characters remaining and they match '{@'.
        if (index + 1 >= endExclusive || text[index] != '{' || text[index + 1] != '@') {
            return Token.NONE
        }

        val startIndex = index

        // Check whether the tag is a conditional '{@if' tag:
        // Ensure "if" is followed by end-of-input or a non-identifier character (to avoid matching
        // tags like '{@ifdef').
        if (index + 3 < endExclusive && text[index + 2] == 'i' && text[index + 3] == 'f') {
            if (index + 4 >= endExclusive || !text[index + 4].isJavaIdentifierPart()) {
                // Match '{@if': advance index, enter INLINE_IF_TAG mode, and emit
                // INLINE_IF_TAG_START token.
                index += 4
                modeStack.push(LexerMode.INLINE_IF_TAG)
                return createToken(
                    JavadocTokenType.INLINE_IF_TAG_START,
                    startIndex,
                    index,
                )
            }
        }

        // Otherwise, it is a standard inline tag '{@': advance index, enter INLINE_TAG mode, and
        // emit INLINE_TAG_START token.
        index += 2
        modeStack.push(LexerMode.INLINE_TAG)
        return createToken(
            JavadocTokenType.INLINE_TAG_START,
            startIndex,
            index,
        )
    }

    /**
     * Tokenize text in [LexerMode.DEFAULT].
     *
     * Matches newlines, spaces, inline tag starts (`{@` and `{@if`), or general text content. In
     * this mode, standalone `{` (not followed by `@`) and `}` are treated as plain text.
     */
    private fun tokenizeDefault(): Token {
        // First, check for newline sequences (including continuation asterisks on the next line).
        val newline = tryMatchNewline()
        if (newline != Token.NONE) {
            return newline
        }

        // Next, check for horizontal whitespace.
        val space = tryMatchSpace()
        if (space != Token.NONE) {
            return space
        }

        // Check for inline tag starts ('{@if' or '{@').
        val inlineTagStart = tryMatchInlineTagStart()
        if (inlineTagStart != Token.NONE) {
            return inlineTagStart
        }

        val startIndex = index

        // Consume characters as plain text until reaching whitespace, a newline, or the start of
        // an inline tag '{@'.
        while (index < endExclusive) {
            val c = text[index]

            // Stop at newline or horizontal whitespace so they can be emitted as separate tokens.
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') break

            // Stop before '{@' so it can be parsed as an inline tag start.
            if (c == '{' && index + 1 < endExclusive && text[index + 1] == '@') break
            index++
        }

        return createToken(
            JavadocTokenType.TEXT_CONTENT,
            startIndex,
            index,
        )
    }

    /**
     * Tokenize in [LexerMode.INLINE_TAG], which is entered after seeing `{@`.
     *
     * Matches the inline tag name (`[a-zA-Z]+`) and switches to [LexerMode.BALANCED_BRACE]. If
     * unexpected characters (such as whitespace) occur before the tag name, reports an issue and
     * skips them to recover.
     */
    private fun tokenizeInlineTag(): Token {
        val c = text[index]
        return if (c in 'a'..'z' || c in 'A'..'Z') {
            // An alphabetical character indicates a valid tag name (e.g. "link", "code").
            // Consume the full tag name and switch to BALANCED_BRACE mode for parsing the tag body.
            val startIndex = index
            while (index < endExclusive && (text[index] in 'a'..'z' || text[index] in 'A'..'Z')) {
                index++
            }
            modeStack.pop()
            modeStack.push(LexerMode.BALANCED_BRACE)
            createToken(
                JavadocTokenType.INLINE_TAG_NAME,
                startIndex,
                index,
            )
        } else if (c == ' ' || c == '\t') {
            // Whitespace immediately follows '{@' (e.g. '{@ link}').
            // Record unexpected whitespace to report as a chunk once a valid character is reached.
            recordUnexpected("after '{@'")
            Token.NONE
        } else if (c == '}' || c == '\n' || c == '\r') {
            // A closing brace or newline immediately follows '{@' (e.g. '{@}' or '{@\n').
            // Report a missing tag name issue and pop INLINE_TAG mode without consuming the
            // delimiter, allowing the caller or enclosing mode to handle the delimiter.
            reportIssue(
                Issues.INVALID_JAVADOC,
                "missing inline tag name",
            )
            modeStack.pop()
            Token.NONE
        } else {
            // Any other unexpected characters (e.g. symbols, punctuation, digits).
            // Record unexpected characters to report as a chunk once a valid character is reached.
            recordUnexpected("after '{@', expected tag name")
            Token.NONE
        }
    }

    /**
     * Tokenize in [LexerMode.BALANCED_BRACE].
     *
     * In this mode, braces `{` and `}` are tracked: `{` pushes another balanced brace mode, and `}`
     * pops the mode. Nested inline tags (`{@` and `{@if`) are also recognized.
     */
    private fun tokenizeBalancedBrace(): Token {
        // Check for newline sequences.
        val newline = tryMatchNewline()
        if (newline != Token.NONE) {
            return newline
        }

        // Check for horizontal whitespace.
        val space = tryMatchSpace()
        if (space != Token.NONE) {
            return space
        }

        // Check for nested inline tag starts ('{@if' or '{@').
        val inlineTagStart = tryMatchInlineTagStart()
        if (inlineTagStart != Token.NONE) {
            return inlineTagStart
        }

        val c = text[index]
        if (c == '{') {
            // Opening brace '{'.
            // Push a new BALANCED_BRACE mode to track this nested level and emit BRACE_OPEN token.
            val startIndex = index
            index++
            modeStack.push(LexerMode.BALANCED_BRACE)
            return createToken(
                JavadocTokenType.BRACE_OPEN,
                startIndex,
                index,
            )
        }
        if (c == '}') {
            // Closing brace '}'.
            // Pop the current BALANCED_BRACE mode and emit BRACE_CLOSE token.
            val startIndex = index
            index++
            modeStack.pop()
            return createToken(
                JavadocTokenType.BRACE_CLOSE,
                startIndex,
                index,
            )
        }

        // Plain text content within the balanced brace block.
        // Consume characters until whitespace, a newline, a brace delimiter ('{' or '}'), or '{@'.
        val startIndex = index

        while (index < endExclusive) {
            val ch = text[index]
            if (ch == '\n' || ch == '\r' || ch == ' ' || ch == '\t' || ch == '{' || ch == '}') break
            index++
        }

        return createToken(
            JavadocTokenType.TEXT_CONTENT,
            startIndex,
            index,
        )
    }

    /**
     * Skips whitespace and newlines (including continuation asterisks) within `{@if ...}` tags and
     * expressions.
     */
    private fun skipWhitespaceAndNewlinesInIfOrExpr() {
        while (index < endExclusive) {
            val c = text[index]
            if (c == ' ' || c == '\t') {
                // Horizontal whitespace (spaces/tabs).
                index++
            } else if (c == '\n' || c == '\r') {
                // Newline sequence.
                // Advance index for \r\n (2 chars) or \n/\r (1 char).
                if (c == '\r' && index + 1 < endExclusive && text[index + 1] == '\n') {
                    index += 2
                } else {
                    index++
                }

                // Skip optional leading spaces/tabs on the new line.
                var p = index
                while (p < endExclusive && (text[p] == ' ' || text[p] == '\t')) {
                    p++
                }

                // If followed by one or more asterisks '*', skip them as comment continuation
                // prefix.
                if (p < endExclusive && text[p] == '*') {
                    while (p < endExclusive && text[p] == '*') {
                        p++
                    }
                    index = p
                }
            } else {
                // Non-whitespace character reached; stop skipping.
                break
            }
        }
    }

    /**
     * Tokenize in [LexerMode.INLINE_IF_TAG].
     *
     * Skips whitespace and recognizes `(`, `{`, `}`, and `else`.
     */
    private fun tokenizeInlineIfTag(): Token {
        skipWhitespaceAndNewlinesInIfOrExpr()

        // If end of input is reached, return.
        if (index >= endExclusive) return Token.NONE

        val c = text[index]
        val startIndex = index

        return when {
            c == '(' -> {
                // Opening parenthesis '(' begins the condition expression.
                // Switch to EXPR mode to tokenize the condition.
                index++
                modeStack.push(LexerMode.EXPR)
                createToken(
                    JavadocTokenType.PAREN_OPEN,
                    startIndex,
                    index,
                )
            }
            c == '{' -> {
                // Opening brace '{' begins the true or false branch body.
                // Switch to BALANCED_BRACE mode to handle nested content.
                index++
                modeStack.push(LexerMode.BALANCED_BRACE)
                createToken(
                    JavadocTokenType.BRACE_OPEN,
                    startIndex,
                    index,
                )
            }
            c == '}' -> {
                // Closing brace '}' closes the entire '{@if}' tag.
                // Pop INLINE_IF_TAG mode to return to the enclosing mode.
                index++
                modeStack.pop()
                createToken(
                    JavadocTokenType.BRACE_CLOSE,
                    startIndex,
                    index,
                )
            }
            // The keyword "else" introducing the alternative branch.
            // Ensure "else" fits completely within the subrange [startInclusive, endExclusive),
            // and is not a prefix of a longer identifier before emitting IF_TAG_ELSE.
            index + 4 <= endExclusive &&
                text.startsWith("else", index) &&
                (index + 4 == endExclusive || !text[index + 4].isJavaIdentifierPart()) -> {
                index += 4
                createToken(
                    JavadocTokenType.IF_TAG_ELSE,
                    startIndex,
                    index,
                )
            }
            else -> {
                // Unexpected character sequence in '{@if' structure.
                // Record unexpected characters to report as a chunk once a valid token or issue is
                // reached.
                recordUnexpected("in '@if' tag")
                Token.NONE
            }
        }
    }

    /**
     * Tokenize in [LexerMode.EXPR].
     *
     * Recognizes `(`, `)`, `.`, and identifier names within conditional expressions.
     */
    private fun tokenizeExpr(): Token {
        skipWhitespaceAndNewlinesInIfOrExpr()

        // If end of input is reached, return.
        if (index >= endExclusive) return Token.NONE

        val c = text[index]
        val startIndex = index

        return when {
            c == '(' -> {
                // Opening parenthesis '(' for nested expression or function call argument list.
                // Push EXPR mode to track nested parentheses.
                index++
                modeStack.push(LexerMode.EXPR)
                createToken(
                    JavadocTokenType.PAREN_OPEN,
                    startIndex,
                    index,
                )
            }
            c == ')' -> {
                // Closing parenthesis ')' ending an expression or function call argument list.
                // Pop the current EXPR mode.
                index++
                modeStack.pop()
                createToken(
                    JavadocTokenType.PAREN_CLOSE,
                    startIndex,
                    index,
                )
            }
            c == '.' -> {
                // Dot '.' separator in qualified names (e.g. package or class qualifiers).
                index++
                createToken(
                    JavadocTokenType.DOT,
                    startIndex,
                    index,
                )
            }
            c.isJavaIdentifierStart() -> {
                // Identifier start character (e.g. function name, class name, or field name).
                // Consume all subsequent identifier characters.
                while (index < endExclusive && text[index].isJavaIdentifierPart()) {
                    index++
                }
                createToken(
                    JavadocTokenType.IDENTIFIER,
                    startIndex,
                    index,
                )
            }
            c == '{' || c == '}' -> {
                // Brace character encountered while in EXPR mode.
                // Indicates an unclosed expression (missing ')') before the body block '{' or end
                // of tag '}'.
                // Pop EXPR mode to recover and allow INLINE_IF_TAG mode to handle the brace.
                modeStack.pop()
                Token.NONE
            }
            else -> {
                // Unexpected character sequence in expression.
                // Record unexpected characters to report as a chunk once a valid token or issue is
                // reached.
                recordUnexpected("in expression")
                Token.NONE
            }
        }
    }
}
