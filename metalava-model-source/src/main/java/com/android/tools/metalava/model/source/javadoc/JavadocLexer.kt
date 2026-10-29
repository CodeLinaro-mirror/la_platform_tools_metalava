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

import com.android.tools.metalava.model.source.doc.DocumentationIssueReporter
import com.android.tools.metalava.reporter.Issues
import java.util.ArrayDeque

/** Token types produced by [JavadocLexer]. */
internal enum class TokenType {
    /** Plain text content outside of tags or within tag bodies. */
    TEXT_CONTENT,

    /** One or more horizontal whitespace characters (spaces or tabs). */
    SPACE,

    /**
     * A line break (`\n`, `\r\n`, or `\r`), optionally followed by horizontal whitespace and one or
     * more asterisks on the next line.
     */
    NEWLINE,

    /** The `{@` prefix introducing an inline tag. */
    INLINE_TAG_START,

    /** The name of an inline tag (e.g. `link`, `code`, etc.) following `{@`. */
    INLINE_TAG_NAME,

    /** An opening curly brace `{`. */
    BRACE_OPEN,

    /** A closing curly brace `}`. */
    BRACE_CLOSE,

    /** The `{@if` prefix introducing a conditional Javadoc tag. */
    INLINE_IF_TAG_START,

    /** The `else` keyword in an `{@if}` tag. */
    IF_TAG_ELSE,

    /** An opening parenthesis `(`. */
    PAREN_OPEN,

    /** A closing parenthesis `)`. */
    PAREN_CLOSE,

    /** An identifier in an expression (e.g. function or flag name). */
    IDENTIFIER,

    /** A dot `.` separator in a qualified identifier. */
    DOT,

    /** End of file / input marker. */
    EOF,
}

/**
 * A token produced by [JavadocLexer].
 *
 * @property type the [TokenType] representing the kind of token.
 * @property text the raw string content of this token.
 * @property line 1-based line number relative to the start of the parsed text range.
 * @property charPositionInLine 0-based character position relative to the start of the containing
 *   line.
 * @property startOffset 0-based start index of this token within the input text.
 * @property endOffset 0-based exclusive end index of this token within the input text.
 */
internal data class Token(
    val type: TokenType,
    val text: String,
    val line: Int,
    val charPositionInLine: Int,
    val startOffset: Int,
    val endOffset: Int,
)

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
 * into a stream of [Token]s ending with [TokenType.EOF].
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
 *     - Matches newlines ([TokenType.NEWLINE]), horizontal whitespace ([TokenType.SPACE]), inline
 *       tag starts (`{@` as [TokenType.INLINE_TAG_START], `{@if` as
 *       [TokenType.INLINE_IF_TAG_START]), or general text ([TokenType.TEXT_CONTENT]).
 *     - Standalone `{` (not followed by `@`) and `}` are emitted as plain text.
 * 2. **[LexerMode.INLINE_TAG]**:
 *     - Entered immediately upon encountering `{@`.
 *     - Matches the tag name (`[a-zA-Z]+`, emitted as [TokenType.INLINE_TAG_NAME]) and transitions
 *       to [LexerMode.BALANCED_BRACE] to tokenize the tag body.
 *     - If unexpected characters (e.g. whitespace) appear before the tag name, an issue is reported
 *       via [reporter] and the lexer recovers.
 * 3. **[LexerMode.BALANCED_BRACE]**:
 *     - Active within inline tag bodies or conditional branch bodies.
 *     - Tracks brace nesting: an opening `{` ([TokenType.BRACE_OPEN]) pushes another
 *       [LexerMode.BALANCED_BRACE] mode, while a closing `}` ([TokenType.BRACE_CLOSE]) pops the
 *       mode.
 *     - Nested inline tags (`{@` and `{@if`) are also recognized.
 * 4. **[LexerMode.INLINE_IF_TAG]**:
 *     - Active inside `{@if ...}` tags.
 *     - Skips horizontal whitespace and newlines (including continuation asterisks).
 *     - Matches `(` ([TokenType.PAREN_OPEN], switching to [LexerMode.EXPR]), `{`
 *       ([TokenType.BRACE_OPEN], switching to [LexerMode.BALANCED_BRACE] for branch bodies), `}`
 *       ([TokenType.BRACE_CLOSE], popping the [LexerMode.INLINE_IF_TAG] mode), and the `else`
 *       keyword ([TokenType.IF_TAG_ELSE]).
 * 5. **[LexerMode.EXPR]**:
 *     - Active within conditional expressions (`{@if (expr)}`).
 *     - Skips horizontal whitespace and newlines.
 *     - Recognizes parentheses `(` and `)`, dot operators `.`, and identifiers
 *       ([TokenType.IDENTIFIER]).
 *     - If an unexpected `{` or `}` is encountered, pops [LexerMode.EXPR] to recover from an
 *       unclosed expression.
 *
 * ### Comment Formatting and Newline Normalization
 *
 * Multi-line Javadoc comments typically prefix continuation lines with optional whitespace and one
 * or more asterisks (e.g. `\n * `). The lexer collapses a newline sequence (`\r\n`, `\n`, or `\r`)
 * and any following continuation asterisks on the next line into a single [TokenType.NEWLINE] token
 * in [LexerMode.DEFAULT] and [LexerMode.BALANCED_BRACE] modes. In [LexerMode.INLINE_IF_TAG] and
 * [LexerMode.EXPR] modes, newlines and continuation asterisks are skipped along with whitespace.
 *
 * ### Position Tracking and Issue Reporting
 *
 * The lexer tracks:
 * - `line`: 1-based line number relative to [startInclusive].
 * - `charPositionInLine`: 0-based character position within the current line.
 * - `startIndex` and `endIndex`: absolute character offsets within [text].
 *
 * Every emitted [Token] carries these location coordinates. Any lexical syntax errors encountered
 * during scanning (such as unexpected characters after `{@` or in expressions) are reported
 * directly via [reporter].
 *
 * @param text the full string containing the Javadoc text to tokenize.
 * @param startInclusive the index in [text] where tokenization should begin.
 * @param endExclusive the index in [text] where tokenization should end.
 * @param reporter used for reporting any syntax issues encountered during lexing.
 */
internal class JavadocLexer(
    private val text: String,
    private val startInclusive: Int,
    private val endExclusive: Int,
    private val reporter: DocumentationIssueReporter,
) {
    /** Current reading position in [text]. */
    private var index = startInclusive

    /** Current line number (1-based relative to [startInclusive]). */
    private var line = 1

    /** Current character position within the current line (0-based). */
    private var charPositionInLine = 0

    /** Stack of [LexerMode]s controlling context-dependent tokenization. */
    private val modeStack = ArrayDeque<LexerMode>()

    /** Accumulated tokens produced by this lexer. */
    private val tokens = mutableListOf<Token>()

    /**
     * Start index in [text] of an unexpected character sequence, or equal to [unexpectedEndIndex]
     * if none.
     */
    private var unexpectedStartIndex = startInclusive

    /** Exclusive end index in [text] of an unexpected character sequence. */
    private var unexpectedEndIndex = startInclusive

    /** Line number where the unexpected character sequence started. */
    private var unexpectedStartLine = -1

    /** Character position in line where the unexpected character sequence started. */
    private var unexpectedStartChar = -1

    /** Context description for the unexpected character sequence (e.g. "in expression"). */
    private var unexpectedContext: String? = null

    init {
        modeStack.push(LexerMode.DEFAULT)
    }

    /**
     * Creates and adds a [Token] to [tokens], flushing any pending unexpected character sequence
     * first.
     */
    private fun addToken(
        type: TokenType,
        text: String,
        line: Int,
        charPositionInLine: Int,
        startOffset: Int,
        endOffset: Int,
    ) {
        flushUnexpected()
        tokens.add(
            Token(
                type,
                text,
                line,
                charPositionInLine,
                startOffset,
                endOffset,
            )
        )
    }

    /**
     * Reports an issue via [reporter], flushing any pending unexpected character sequence first.
     */
    private fun reportIssue(
        issue: Issues.Issue,
        message: String,
        line: Int,
        charPositionInLine: Int,
    ) {
        flushUnexpected()
        reporter.report(issue, message, line, charPositionInLine)
    }

    /**
     * Records an unexpected character with the given [context], advancing [index] and
     * [charPositionInLine].
     */
    private fun recordUnexpected(context: String) {
        if (unexpectedStartIndex == unexpectedEndIndex) {
            unexpectedStartIndex = index
            unexpectedStartLine = line
            unexpectedStartChar = charPositionInLine
            unexpectedContext = context
        }
        index++
        charPositionInLine++
        unexpectedEndIndex = index
    }

    /** Flushes any accumulated unexpected character sequence and reports it as an issue. */
    private fun flushUnexpected() {
        if (unexpectedStartIndex != unexpectedEndIndex) {
            val chunk = text.substring(unexpectedStartIndex, unexpectedEndIndex)
            reporter.report(
                Issues.INVALID_JAVADOC,
                "unexpected '$chunk' $unexpectedContext",
                unexpectedStartLine - 1,
                unexpectedStartChar,
            )
            unexpectedStartIndex = unexpectedEndIndex
            unexpectedContext = null
        }
    }

    /**
     * Tokenize the text from [startInclusive] to [endExclusive].
     *
     * @return a list of [Token]s ending with a [TokenType.EOF] token.
     */
    fun tokenize(): List<Token> {
        while (index < endExclusive) {
            val currentMode = modeStack.peek()
            when (currentMode) {
                // In DEFAULT mode: general comment text outside tags, matching newlines, spaces,
                // inline tag starts, or plain text.
                LexerMode.DEFAULT -> tokenizeDefault()

                // In INLINE_TAG mode: immediately after '{@', expecting the tag name (e.g. 'link',
                // 'code').
                LexerMode.INLINE_TAG -> tokenizeInlineTag()

                // In BALANCED_BRACE mode: inside an inline tag or brace block, tracking '{' and '}'
                // nesting.
                LexerMode.BALANCED_BRACE -> tokenizeBalancedBrace()

                // In INLINE_IF_TAG mode: inside '{@if ...}', ignoring whitespace and matching '(',
                // '{', '}', and 'else'.
                LexerMode.INLINE_IF_TAG -> tokenizeInlineIfTag()

                // In EXPR mode: inside the condition of '{@if (expr)}', matching identifiers, dots,
                // and parens.
                LexerMode.EXPR -> tokenizeExpr()
            }
        }

        // If the input ended while expecting an inline tag name (e.g. '{@' at EOF), report the
        // missing tag name issue and pop the mode.
        if (modeStack.peek() == LexerMode.INLINE_TAG) {
            reportIssue(
                Issues.INVALID_JAVADOC,
                "missing inline tag name",
                line - 1,
                charPositionInLine,
            )
            modeStack.pop()
        }
        addToken(
            TokenType.EOF,
            "",
            line,
            charPositionInLine,
            endExclusive,
            endExclusive,
        )
        return tokens
    }

    /**
     * Attempt to match a newline sequence (`\r\n`, `\n`, or `\r`), followed by optional
     * continuation prefix on the next line (optional horizontal whitespace followed by one or more
     * asterisks).
     *
     * If matched, advances [index], increments [line], updates [charPositionInLine], adds a
     * [TokenType.NEWLINE] token, and returns `true`. Otherwise returns `false`.
     */
    private fun tryMatchNewline(): Boolean {
        // Return false if there are no characters remaining to match.
        if (index >= endExclusive) return false
        val c = text[index]

        // If the current character is not a newline indicator (\n or \r), then no newline matches.
        if (c != '\n' && c != '\r') return false

        val startIndex = index
        val startLine = line
        val startChar = charPositionInLine

        // Handle \r\n as a single 2-character newline; otherwise consume a single \n or \r.
        if (c == '\r' && index + 1 < endExclusive && text[index + 1] == '\n') {
            index += 2
        } else {
            index++
        }
        line++
        charPositionInLine = 0

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
            charPositionInLine += (p - index)
            index = p
        }

        val tokenText = text.substring(startIndex, index)
        addToken(
            TokenType.NEWLINE,
            tokenText,
            startLine,
            startChar,
            startIndex,
            index,
        )
        return true
    }

    /**
     * Attempt to match one or more horizontal whitespace characters (spaces or tabs).
     *
     * If matched, advances [index] and [charPositionInLine], adds a [TokenType.SPACE] token, and
     * returns `true`. Otherwise returns `false`.
     */
    private fun tryMatchSpace(): Boolean {
        // Return false if there are no characters remaining to match.
        if (index >= endExclusive) return false
        val c = text[index]

        // If the current character is not horizontal whitespace (space or tab), return false.
        if (c != ' ' && c != '\t') return false

        val startIndex = index
        val startLine = line
        val startChar = charPositionInLine

        // Consume all consecutive horizontal whitespace characters.
        while (index < endExclusive && (text[index] == ' ' || text[index] == '\t')) {
            index++
            charPositionInLine++
        }

        val tokenText = text.substring(startIndex, index)
        addToken(
            TokenType.SPACE,
            tokenText,
            startLine,
            startChar,
            startIndex,
            index,
        )
        return true
    }

    /**
     * Attempt to match an inline tag start sequence (`{@if` or `{@`).
     *
     * If `{@if` is followed by a non-identifier character, switches to [LexerMode.INLINE_IF_TAG]
     * and emits [TokenType.INLINE_IF_TAG_START]. Otherwise switches to [LexerMode.INLINE_TAG] and
     * emits [TokenType.INLINE_TAG_START].
     */
    private fun tryMatchInlineTagStart(): Boolean {
        // Check if there are at least two characters remaining and they match '{@'.
        if (index + 1 >= endExclusive || text[index] != '{' || text[index + 1] != '@') return false

        val startIndex = index
        val startLine = line
        val startChar = charPositionInLine

        // Check whether the tag is a conditional '{@if' tag:
        // Ensure "if" is followed by end-of-input or a non-identifier character (to avoid matching
        // tags like '{@ifdef').
        if (index + 3 < endExclusive && text[index + 2] == 'i' && text[index + 3] == 'f') {
            if (index + 4 >= endExclusive || !text[index + 4].isJavaIdentifierPart()) {
                // Match '{@if': advance index, enter INLINE_IF_TAG mode, and emit
                // INLINE_IF_TAG_START token.
                index += 4
                charPositionInLine += 4
                modeStack.push(LexerMode.INLINE_IF_TAG)
                addToken(
                    TokenType.INLINE_IF_TAG_START,
                    "{@if",
                    startLine,
                    startChar,
                    startIndex,
                    index
                )
                return true
            }
        }

        // Otherwise, it is a standard inline tag '{@': advance index, enter INLINE_TAG mode, and
        // emit INLINE_TAG_START token.
        index += 2
        charPositionInLine += 2
        modeStack.push(LexerMode.INLINE_TAG)
        addToken(
            TokenType.INLINE_TAG_START,
            "{@",
            startLine,
            startChar,
            startIndex,
            index,
        )
        return true
    }

    /**
     * Tokenize text in [LexerMode.DEFAULT].
     *
     * Matches newlines, spaces, inline tag starts (`{@` and `{@if`), or general text content. In
     * this mode, standalone `{` (not followed by `@`) and `}` are treated as plain text.
     */
    private fun tokenizeDefault() {
        // First, check for newline sequences (including continuation asterisks on the next line).
        if (tryMatchNewline()) return

        // Next, check for horizontal whitespace.
        if (tryMatchSpace()) return

        // Check for inline tag starts ('{@if' or '{@').
        if (tryMatchInlineTagStart()) return

        val startIndex = index
        val startLine = line
        val startChar = charPositionInLine

        // Consume characters as plain text until reaching whitespace, a newline, or the start of
        // an inline tag '{@'.
        while (index < endExclusive) {
            val c = text[index]

            // Stop at newline or horizontal whitespace so they can be emitted as separate tokens.
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') break

            // Stop before '{@' so it can be parsed as an inline tag start.
            if (c == '{' && index + 1 < endExclusive && text[index + 1] == '@') break
            index++
            charPositionInLine++
        }

        val tokenText = text.substring(startIndex, index)
        addToken(
            TokenType.TEXT_CONTENT,
            tokenText,
            startLine,
            startChar,
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
    private fun tokenizeInlineTag() {
        val c = text[index]
        if (c in 'a'..'z' || c in 'A'..'Z') {
            // An alphabetical character indicates a valid tag name (e.g. "link", "code").
            // Consume the full tag name and switch to BALANCED_BRACE mode for parsing the tag body.
            val startIndex = index
            val startLine = line
            val startChar = charPositionInLine
            while (index < endExclusive && (text[index] in 'a'..'z' || text[index] in 'A'..'Z')) {
                index++
                charPositionInLine++
            }
            val tagName = text.substring(startIndex, index)
            modeStack.pop()
            modeStack.push(LexerMode.BALANCED_BRACE)
            addToken(
                TokenType.INLINE_TAG_NAME,
                tagName,
                startLine,
                startChar,
                startIndex,
                index,
            )
        } else if (c == ' ' || c == '\t') {
            // Whitespace immediately follows '{@' (e.g. '{@ link}').
            // Record unexpected whitespace to report as a chunk once a valid character is reached.
            recordUnexpected("after '{@'")
        } else if (c == '}' || c == '\n' || c == '\r') {
            // A closing brace or newline immediately follows '{@' (e.g. '{@}' or '{@\n').
            // Report a missing tag name issue and pop INLINE_TAG mode without consuming the
            // delimiter, allowing the caller or enclosing mode to handle the delimiter.
            reportIssue(
                Issues.INVALID_JAVADOC,
                "missing inline tag name",
                line - 1,
                charPositionInLine,
            )
            modeStack.pop()
        } else {
            // Any other unexpected characters (e.g. symbols, punctuation, digits).
            // Record unexpected characters to report as a chunk once a valid character is reached.
            recordUnexpected("after '{@', expected tag name")
        }
    }

    /**
     * Tokenize in [LexerMode.BALANCED_BRACE].
     *
     * In this mode, braces `{` and `}` are tracked: `{` pushes another balanced brace mode, and `}`
     * pops the mode. Nested inline tags (`{@` and `{@if`) are also recognized.
     */
    private fun tokenizeBalancedBrace() {
        // Check for newline sequences.
        if (tryMatchNewline()) return

        // Check for horizontal whitespace.
        if (tryMatchSpace()) return

        // Check for nested inline tag starts ('{@if' or '{@').
        if (tryMatchInlineTagStart()) return

        val c = text[index]
        if (c == '{') {
            // Opening brace '{'.
            // Push a new BALANCED_BRACE mode to track this nested level and emit BRACE_OPEN token.
            val startIndex = index
            val startLine = line
            val startChar = charPositionInLine
            index++
            charPositionInLine++
            modeStack.push(LexerMode.BALANCED_BRACE)
            addToken(
                TokenType.BRACE_OPEN,
                "{",
                startLine,
                startChar,
                startIndex,
                index,
            )
            return
        }
        if (c == '}') {
            // Closing brace '}'.
            // Pop the current BALANCED_BRACE mode and emit BRACE_CLOSE token.
            val startIndex = index
            val startLine = line
            val startChar = charPositionInLine
            index++
            charPositionInLine++
            modeStack.pop()
            addToken(
                TokenType.BRACE_CLOSE,
                "}",
                startLine,
                startChar,
                startIndex,
                index,
            )
            return
        }

        // Plain text content within the balanced brace block.
        // Consume characters until whitespace, a newline, a brace delimiter ('{' or '}'), or '{@'.
        val startIndex = index
        val startLine = line
        val startChar = charPositionInLine

        while (index < endExclusive) {
            val ch = text[index]
            if (ch == '\n' || ch == '\r' || ch == ' ' || ch == '\t' || ch == '{' || ch == '}') break
            index++
            charPositionInLine++
        }

        val tokenText = text.substring(startIndex, index)
        addToken(
            TokenType.TEXT_CONTENT,
            tokenText,
            startLine,
            startChar,
            startIndex,
            index,
        )
    }

    /**
     * Skips whitespace and newlines (including continuation asterisks) within `{@if ...}` tags and
     * expressions, updating [line] and [charPositionInLine] accordingly.
     */
    private fun skipWhitespaceAndNewlinesInIfOrExpr() {
        while (index < endExclusive) {
            val c = text[index]
            if (c == ' ' || c == '\t') {
                // Horizontal whitespace (spaces/tabs).
                // Advance position on the current line.
                index++
                charPositionInLine++
            } else if (c == '\n' || c == '\r') {
                // Newline sequence.
                // Advance index for \r\n (2 chars) or \n/\r (1 char), increment line count, and
                // reset char position.
                if (c == '\r' && index + 1 < endExclusive && text[index + 1] == '\n') {
                    index += 2
                } else {
                    index++
                }
                line++
                charPositionInLine = 0

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
                    charPositionInLine += (p - index)
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
    private fun tokenizeInlineIfTag() {
        skipWhitespaceAndNewlinesInIfOrExpr()

        // If end of input is reached, return.
        if (index >= endExclusive) return

        val c = text[index]
        val startIndex = index
        val startLine = line
        val startChar = charPositionInLine

        when {
            c == '(' -> {
                // Opening parenthesis '(' begins the condition expression.
                // Switch to EXPR mode to tokenize the condition.
                index++
                charPositionInLine++
                modeStack.push(LexerMode.EXPR)
                addToken(
                    TokenType.PAREN_OPEN,
                    "(",
                    startLine,
                    startChar,
                    startIndex,
                    index,
                )
            }
            c == '{' -> {
                // Opening brace '{' begins the true or false branch body.
                // Switch to BALANCED_BRACE mode to handle nested content.
                index++
                charPositionInLine++
                modeStack.push(LexerMode.BALANCED_BRACE)
                addToken(
                    TokenType.BRACE_OPEN,
                    "{",
                    startLine,
                    startChar,
                    startIndex,
                    index,
                )
            }
            c == '}' -> {
                // Closing brace '}' closes the entire '{@if}' tag.
                // Pop INLINE_IF_TAG mode to return to the enclosing mode.
                index++
                charPositionInLine++
                modeStack.pop()
                addToken(
                    TokenType.BRACE_CLOSE,
                    "}",
                    startLine,
                    startChar,
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
                charPositionInLine += 4
                addToken(
                    TokenType.IF_TAG_ELSE,
                    "else",
                    startLine,
                    startChar,
                    startIndex,
                    index,
                )
            }
            else -> {
                // Unexpected character sequence in '{@if' structure.
                // Record unexpected characters to report as a chunk once a valid token or issue is
                // reached.
                recordUnexpected("in '@if' tag")
            }
        }
    }

    /**
     * Tokenize in [LexerMode.EXPR].
     *
     * Recognizes `(`, `)`, `.`, and identifier names within conditional expressions.
     */
    private fun tokenizeExpr() {
        skipWhitespaceAndNewlinesInIfOrExpr()

        // If end of input is reached, return.
        if (index >= endExclusive) return

        val c = text[index]
        val startIndex = index
        val startLine = line
        val startChar = charPositionInLine

        when {
            c == '(' -> {
                // Opening parenthesis '(' for nested expression or function call argument list.
                // Push EXPR mode to track nested parentheses.
                index++
                charPositionInLine++
                modeStack.push(LexerMode.EXPR)
                addToken(
                    TokenType.PAREN_OPEN,
                    "(",
                    startLine,
                    startChar,
                    startIndex,
                    index,
                )
            }
            c == ')' -> {
                // Closing parenthesis ')' ending an expression or function call argument list.
                // Pop the current EXPR mode.
                index++
                charPositionInLine++
                modeStack.pop()
                addToken(
                    TokenType.PAREN_CLOSE,
                    ")",
                    startLine,
                    startChar,
                    startIndex,
                    index,
                )
            }
            c == '.' -> {
                // Dot '.' separator in qualified names (e.g. package or class qualifiers).
                index++
                charPositionInLine++
                addToken(
                    TokenType.DOT,
                    ".",
                    startLine,
                    startChar,
                    startIndex,
                    index,
                )
            }
            c.isJavaIdentifierStart() -> {
                // Identifier start character (e.g. function name, class name, or field name).
                // Consume all subsequent identifier characters.
                while (index < endExclusive && text[index].isJavaIdentifierPart()) {
                    index++
                    charPositionInLine++
                }
                val ident = text.substring(startIndex, index)
                addToken(
                    TokenType.IDENTIFIER,
                    ident,
                    startLine,
                    startChar,
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
            }
            else -> {
                // Unexpected character sequence in expression.
                // Record unexpected characters to report as a chunk once a valid token or issue is
                // reached.
                recordUnexpected("in expression")
            }
        }
    }
}
