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
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenType
import com.android.tools.metalava.reporter.Issues

/** Token types produced by [DocCommentLexer]. */
internal object DocCommentTokenType {
    /**
     * A span of description content (either the main description before any block tags or the
     * description body of a block tag), with trailing whitespace trimmed.
     */
    val DESCRIPTION = TokenType("DESCRIPTION")

    /**
     * The `@` character introducing a top-level block tag, or `:@` introducing a block tag
     * encapsulated within an `@if` clause.
     */
    val BLOCK_TAG_START = TokenType("BLOCK_TAG_START")

    /** The alphabetical name (`[a-zA-Z]+`) of a block tag immediately following `@` or `:@`. */
    val BLOCK_TAG_NAME = TokenType("BLOCK_TAG_NAME")

    /** The `@if` (top-level) or `:@if` (nested) prefix introducing a conditional block tag. */
    val BLOCK_IF_TAG_START = TokenType("BLOCK_IF_TAG_START")

    /** The `else` keyword in a conditional `@if` block tag. */
    val IF_TAG_ELSE = IF_TAG_ELSE_TOKEN

    /** An opening parenthesis `(`. */
    val PAREN_OPEN = SharedTokenType.PAREN_OPEN

    /** A closing parenthesis `)`. */
    val PAREN_CLOSE = SharedTokenType.PAREN_CLOSE

    /** An opening curly brace `{`. */
    val BRACE_OPEN = SharedTokenType.BRACE_OPEN

    /** A closing curly brace `}`. */
    val BRACE_CLOSE = SharedTokenType.BRACE_CLOSE

    /** An identifier in a conditional expression (e.g. function or flag name). */
    val IDENTIFIER = SharedTokenType.IDENTIFIER

    /** A string literal in a conditional expression (e.g. surface name). */
    val STRING_LITERAL = SharedTokenType.STRING_LITERAL

    /** A dot `.` separator in a qualified identifier. */
    val DOT = SharedTokenType.DOT

    /** End of input marker. */
    val EOF = SharedTokenType.EOF
}

/** Lexer modes for [DocCommentLexer]. */
private enum class DocCommentLexerMode {
    /** Scanning description text until a line-starting block tag (or `}` inside `@if`) or EOF. */
    DESCRIPTION,

    /** Positioned at `@` or `:@` of a matched block tag or `@if` tag. */
    BLOCK_TAG_START,

    /** Positioned at the tag name immediately following `@` or `:@`. */
    BLOCK_TAG_NAME,

    /** Inside `@if` before the true branch `{`, expecting `(<expr>)` and `{`. */
    IF_TAG,

    /** Inside the parenthesized condition expression of `@if (<expr>)`. */
    EXPR,

    /** Inside `{ ... }` of an `@if` or `else` branch, expecting `:@<tag>`, `:@if`, or `}`. */
    IF_BRANCH,

    /** Immediately after `}` of an `@if` true branch, checking for an optional `else` keyword. */
    IF_AFTER_TRUE_BRANCH,

    /** Immediately after `else` in an `@if` block tag, expecting `{`. */
    IF_ELSE_TAG,
}

/**
 * Coarse-grained modal lexer that partitions the body of a documentation comment into description
 * spans, block tags, and conditional `@if` block tag structures without parsing inline content.
 *
 * Scanning only for line-starting block tags and `@if` structure allows [DocCommentParser] to wrap
 * each active description span in a [LazyContentSupplier], deferring fine-grained
 * [com.android.tools.metalava.model.source.javadoc.JavadocLexer] and
 * [com.android.tools.metalava.model.source.javadoc.JavadocParser] execution until a section's
 * content is actually requested.
 *
 * Within `@if { ... } else { ... }` clauses, inner block tags must be prefixed with `:` (e.g.
 * `:@see`, `:@deprecated`) so external tools such as `javac` and Error Prone do not interpret them
 * as standalone top-level block tags. Unprefixed block tags inside `@if`, `:`-prefixed block tags
 * outside `@if`, and block tags placed inside inline `{@...}` tags are reported via [reporter] as
 * [Issues.INVALID_JAVADOC] and recovered.
 *
 * @param text the full documentation comment text.
 * @param startInclusive the start offset of the comment body (after `/**`, if present).
 * @param endExclusive the exclusive end offset of the comment body (before `*/`, if present).
 * @param reporter reporter used for lexical syntax diagnostics.
 */
internal class DocCommentLexer(
    text: String,
    startInclusive: Int = 0,
    endExclusive: Int = text.length,
    reporter: DocumentationIssueReporter = DocumentationIssueReporter.THROWING,
) :
    AbstractDocLexer(
        text,
        startInclusive,
        endExclusive,
        reporter,
    ) {

    /** Current lexical mode. */
    private var mode: DocCommentLexerMode = DocCommentLexerMode.DESCRIPTION

    /**
     * Whether [index] is currently positioned at the start of a line (either [startInclusive] or
     * immediately following a `\n`, `\r\n`, or `\r` line break).
     */
    private var atLineStart: Boolean = true

    /** Nesting depth of enclosing `@if { ... }` or `else { ... }` branch bodies. */
    private var ifDepth: Int = 0

    /**
     * Bitmask recording whether each active `@if` nesting level `d` (`0 <= d < ifDepth`) is inside
     * an `else` branch (`1L shl d`) rather than a `true` branch (`0L`).
     */
    private var inElseBranchMask: Long = 0L

    override fun exitExpr() {
        mode = DocCommentLexerMode.IF_TAG
    }

    override fun nextToken(): Token {
        while (index < endExclusive) {
            val token =
                when (mode) {
                    DocCommentLexerMode.DESCRIPTION -> tokenizeDescription()
                    DocCommentLexerMode.BLOCK_TAG_START -> tokenizeBlockTagStart()
                    DocCommentLexerMode.BLOCK_TAG_NAME -> tokenizeBlockTagName()
                    DocCommentLexerMode.IF_TAG -> tokenizeIfTag()
                    DocCommentLexerMode.EXPR -> tokenizeExpr()
                    DocCommentLexerMode.IF_BRANCH -> tokenizeIfBranch()
                    DocCommentLexerMode.IF_AFTER_TRUE_BRANCH -> tokenizeIfAfterTrueBranch()
                    DocCommentLexerMode.IF_ELSE_TAG -> tokenizeIfElseTag()
                }
            if (token != Token.NONE) {
                return token
            }
        }

        return createToken(
            DocCommentTokenType.EOF,
            endExclusive,
            endExclusive,
        )
    }

    /**
     * Scans forward from [index] until the next line-starting block tag, the closing `}` of an
     * enclosing `@if` branch (when [ifDepth] > 0 and braces are balanced), or [endExclusive].
     */
    private fun tokenizeDescription(): Token {
        val descStart = index
        var branchBraceDepth = 0
        var inlineTagBraceDepth = 0

        while (index < endExclusive) {
            if (atLineStart) {
                if (inlineTagBraceDepth > 0) {
                    val atOffset = findBlockTagStartAtLine(index)
                    val atIfClosingBrace =
                        atOffset < 0 &&
                            ifDepth > 0 &&
                            branchBraceDepth == inlineTagBraceDepth &&
                            findClosingBraceAtLine(index) >= 0
                    if (
                        (atOffset >= 0 || atIfClosingBrace) &&
                            !hasClosingInlineBrace(index, inlineTagBraceDepth, branchBraceDepth)
                    ) {
                        // The preceding `{@...` inline tag was never closed before a subsequent
                        // real block tag or enclosing `@if` branch `}`; reset inline tag depth so
                        // subsequent block tags (such as `@param` or `@hide`) and `@if` branch
                        // delimiters are still recognized, leaving `JavadocParser` to report
                        // `UnclosedInlineTag` on the truncated description.
                        if (ifDepth > 0) {
                            branchBraceDepth -= inlineTagBraceDepth
                        }
                        inlineTagBraceDepth = 0
                    } else {
                        // Inside a closed inline `{@...}` tag, line-starting block tags are
                        // invalid. Report each offending tag and keep scanning until the inline tag
                        // closes so the tag does not prematurely split the description or leak out
                        // as an unconditional top-level BlockTagSection.
                        if (atOffset >= 0) {
                            reportBlockTagInsideInlineTag(atOffset)
                        }
                        atLineStart = false
                    }
                }
                if (atLineStart) {
                    if (branchBraceDepth == 0) {
                        // Only recognize a line-starting block tag or closing `}` when
                        // `branchBraceDepth == 0` so `}` inside a multi-line `{ ... }` block does
                        // not prematurely close the enclosing `@if` branch.
                        if (ifDepth > 0) {
                            // End the preceding description at `index` (the start of this line) so
                            // the closing line's leading indentation and `*` are not included in
                            // the description span.
                            val braceOffset = findClosingBraceAtLine(index)
                            if (braceOffset >= 0) {
                                val trimmedEnd = trimTrailingWhitespace(descStart, index)
                                index = braceOffset
                                mode = DocCommentLexerMode.IF_BRANCH
                                return if (trimmedEnd > descStart) {
                                    createToken(
                                        DocCommentTokenType.DESCRIPTION,
                                        descStart,
                                        trimmedEnd,
                                    )
                                } else {
                                    tokenizeIfBranch()
                                }
                            }
                        }

                        // End the preceding description at `index` (the start of this line) rather
                        // than `atOffset` so leading indentation and `*` on the block tag's line
                        // are excluded from the preceding description span.
                        val atOffset = findBlockTagStartAtLine(index)
                        if (atOffset >= 0) {
                            val trimmedEnd = trimTrailingWhitespace(descStart, index)
                            index = atOffset
                            mode = DocCommentLexerMode.BLOCK_TAG_START
                            return if (trimmedEnd > descStart) {
                                createToken(
                                    DocCommentTokenType.DESCRIPTION,
                                    descStart,
                                    trimmedEnd,
                                )
                            } else {
                                tokenizeBlockTagStart()
                            }
                        }
                        atLineStart = false
                    } else {
                        atLineStart = false
                    }
                }
            }

            // Advance across the current line, tracking balanced braces for inline `{@...}` tags
            // and enclosing `@if` branches (when `ifDepth > 0`), while keeping bare `{` and `}` in
            // top-level prose unbalanced.
            val c = text[index]
            if (c == '\n') {
                index++
                atLineStart = true
            } else if (c == '\r') {
                if (index + 1 < endExclusive && text[index + 1] == '\n') {
                    index += 2
                } else {
                    index++
                }
                atLineStart = true
            } else if (c == '{') {
                if (ifDepth > 0) branchBraceDepth++
                if (inlineTagBraceDepth > 0) {
                    inlineTagBraceDepth++
                    index++
                } else if (isInlineTagStartAt(index)) {
                    inlineTagBraceDepth = 1
                    index += 2
                } else {
                    index++
                }
            } else if (c == '}') {
                if (inlineTagBraceDepth > 0) inlineTagBraceDepth--
                if (ifDepth > 0) {
                    if (branchBraceDepth > 0) {
                        branchBraceDepth--
                        index++
                    } else {
                        val trimmedEnd = trimTrailingWhitespace(descStart, index)
                        mode = DocCommentLexerMode.IF_BRANCH
                        return if (trimmedEnd > descStart) {
                            createToken(
                                DocCommentTokenType.DESCRIPTION,
                                descStart,
                                trimmedEnd,
                            )
                        } else {
                            tokenizeIfBranch()
                        }
                    }
                } else {
                    index++
                }
            } else {
                index++
            }
        }

        val trimmedEnd = trimTrailingWhitespace(descStart, endExclusive)
        if (ifDepth > 0) {
            mode = DocCommentLexerMode.IF_BRANCH
        }
        return if (trimmedEnd > descStart) {
            createToken(
                DocCommentTokenType.DESCRIPTION,
                descStart,
                trimmedEnd,
            )
        } else {
            Token.NONE
        }
    }

    /**
     * Emits [DocCommentTokenType.BLOCK_IF_TAG_START] (for `@if` / `:@if`) or
     * [DocCommentTokenType.BLOCK_TAG_START] (for `@` / `:@`), validating the `:` prefix rule
     * against [ifDepth].
     */
    private fun tokenizeBlockTagStart(): Token {
        val start = index
        val hasColonPrefix = text[start] == ':'
        val nameStart = if (hasColonPrefix) start + 2 else start + 1

        var nameEnd = nameStart
        while (nameEnd < endExclusive && isAsciiLetter(text[nameEnd])) {
            nameEnd++
        }

        // Enforce that block tags inside `@if` use the `:` prefix (hiding them from `javac` and
        // other external tools) while top-level block tags outside `@if` do not, then continue
        // tokenizing the tag normally to recover.
        if (ifDepth > 0 && !hasColonPrefix) {
            val tagName = text.substring(nameStart, nameEnd)
            reportIssue(
                Issues.INVALID_JAVADOC,
                "block tag '@$tagName' inside '@if' must be prefixed with ':'",
                start,
            )
        } else if (ifDepth == 0 && hasColonPrefix) {
            val tagName = text.substring(nameStart, nameEnd)
            reportIssue(
                Issues.INVALID_JAVADOC,
                "block tag '@$tagName' outside '@if' must not be prefixed with ':'",
                start,
            )
        }

        // Check `!isJavaIdentifierPart()` on the character after `if` so tags with longer names or
        // trailing digits (such as `@ifdef` or `@if0`) are treated as ordinary block tags.
        val isIfTag =
            nameEnd - nameStart == 2 &&
                text[nameStart] == 'i' &&
                text[nameStart + 1] == 'f' &&
                (nameEnd == endExclusive || !text[nameEnd].isJavaIdentifierPart())

        return if (isIfTag) {
            index = nameEnd
            mode = DocCommentLexerMode.IF_TAG
            createToken(
                DocCommentTokenType.BLOCK_IF_TAG_START,
                start,
                index,
            )
        } else {
            index = nameStart
            mode = DocCommentLexerMode.BLOCK_TAG_NAME
            createToken(
                DocCommentTokenType.BLOCK_TAG_START,
                start,
                index,
            )
        }
    }

    /**
     * Emits [DocCommentTokenType.BLOCK_TAG_NAME] for `[a-zA-Z]+`, skips trailing horizontal
     * whitespace on the same line, and returns to `DESCRIPTION` mode.
     */
    private fun tokenizeBlockTagName(): Token {
        val start = index
        while (index < endExclusive && isAsciiLetter(text[index])) {
            index++
        }
        val end = index

        // Skip horizontal whitespace immediately after the tag name so the description starts at
        // the first argument/content character (or at the newline if the line has no content).
        while (index < endExclusive && isHorizontalWhitespace(text[index])) {
            index++
        }

        // Remaining characters on the same line are description content, not a new line start.
        atLineStart = false
        mode = DocCommentLexerMode.DESCRIPTION

        return createToken(
            DocCommentTokenType.BLOCK_TAG_NAME,
            start,
            end,
        )
    }

    /** Tokenizes `(` and `{` following `@if` or `:@if` in [DocCommentLexerMode.IF_TAG]. */
    private fun tokenizeIfTag(): Token {
        skipWhitespaceAndNewlines()
        if (index >= endExclusive) return Token.NONE

        val start = index
        val c = text[start]
        return when {
            c == '(' -> {
                index++
                enterExpr()
                mode = DocCommentLexerMode.EXPR
                createToken(
                    DocCommentTokenType.PAREN_OPEN,
                    start,
                    index,
                )
            }
            c == '{' -> {
                index++
                inElseBranchMask = inElseBranchMask and (1L shl ifDepth).inv()
                ifDepth++
                mode = DocCommentLexerMode.IF_BRANCH
                createToken(
                    DocCommentTokenType.BRACE_OPEN,
                    start,
                    index,
                )
            }
            c == '}' -> {
                if (ifDepth > 0) {
                    // Leave `}` unconsumed so the enclosing `IF_BRANCH` can close cleanly.
                    mode = DocCommentLexerMode.IF_BRANCH
                    Token.NONE
                } else {
                    index++
                    mode = DocCommentLexerMode.DESCRIPTION
                    atLineStart = false
                    createToken(
                        DocCommentTokenType.BRACE_CLOSE,
                        start,
                        index,
                    )
                }
            }
            isBlockTagAt(start) -> {
                // Unclosed `@if` header before the next block tag: recover without consuming it.
                mode = DocCommentLexerMode.BLOCK_TAG_START
                Token.NONE
            }
            else -> {
                recordUnexpected("in '@if' tag")
                Token.NONE
            }
        }
    }

    /**
     * Tokenizes the start of an element inside an `@if` or `else` branch body (`{ ... }`),
     * expecting `:@<tag>`, `:@if`, or `}`.
     */
    private fun tokenizeIfBranch(): Token {
        skipWhitespaceAndNewlines()
        if (index >= endExclusive) return Token.NONE

        val start = index
        val c = text[start]
        return when {
            c == '}' -> {
                index++
                ifDepth--

                // Closing a `true` branch transitions to `IF_AFTER_TRUE_BRANCH` to check for an
                // optional `else` keyword, whereas closing an `else` branch finishes the entire
                // `@if` construct at this nesting depth.
                val wasElse = (inElseBranchMask and (1L shl ifDepth)) != 0L
                mode =
                    if (wasElse) {
                        if (ifDepth > 0) {
                            DocCommentLexerMode.IF_BRANCH
                        } else {
                            atLineStart = false
                            DocCommentLexerMode.DESCRIPTION
                        }
                    } else {
                        DocCommentLexerMode.IF_AFTER_TRUE_BRANCH
                    }
                createToken(
                    DocCommentTokenType.BRACE_CLOSE,
                    start,
                    index,
                )
            }
            isBlockTagAt(start) -> {
                mode = DocCommentLexerMode.BLOCK_TAG_START
                tokenizeBlockTagStart()
            }
            else -> {
                // Unexpected non-tag text inside `@if { ... }`: consume up to the end of the line
                // or `}` and report a single diagnostic.
                while (index < endExclusive) {
                    val ch = text[index]
                    if (ch == '\n' || ch == '\r' || ch == '}') break
                    index++
                }
                val trimmedEnd = trimTrailingWhitespace(start, index)
                val chunk = text.substring(start, trimmedEnd)
                reportIssue(
                    Issues.INVALID_JAVADOC,
                    "unexpected '$chunk' in '@if' block, expected block tag prefixed with ':' or '}'",
                    start,
                )
                Token.NONE
            }
        }
    }

    /**
     * Checks for an optional `else` keyword immediately following the closing `}` of an `@if` true
     * branch.
     */
    private fun tokenizeIfAfterTrueBranch(): Token {
        val crossedNewline = skipWhitespaceAndNewlines()
        if (isElseKeywordAtCurrentIndex()) {
            val start = index
            index += 4
            mode = DocCommentLexerMode.IF_ELSE_TAG
            return createToken(
                DocCommentTokenType.IF_TAG_ELSE,
                start,
                index,
            )
        }

        if (ifDepth > 0) {
            mode = DocCommentLexerMode.IF_BRANCH
        } else {
            // If lookahead crossed a newline (skipping the new line's leading `*`), preserve
            // `atLineStart = true` so a top-level block tag on that line is still recognized.
            atLineStart = crossedNewline
            mode = DocCommentLexerMode.DESCRIPTION
        }
        return Token.NONE
    }

    /** Tokenizes `{` immediately following `else` in an `@if` block tag. */
    private fun tokenizeIfElseTag(): Token {
        skipWhitespaceAndNewlines()
        if (index >= endExclusive) return Token.NONE

        val start = index
        val c = text[start]
        return when {
            c == '{' -> {
                index++
                inElseBranchMask = inElseBranchMask or (1L shl ifDepth)
                ifDepth++
                mode = DocCommentLexerMode.IF_BRANCH
                createToken(
                    DocCommentTokenType.BRACE_OPEN,
                    start,
                    index,
                )
            }
            c == '}' -> {
                if (ifDepth > 0) {
                    mode = DocCommentLexerMode.IF_BRANCH
                    Token.NONE
                } else {
                    index++
                    mode = DocCommentLexerMode.DESCRIPTION
                    atLineStart = false
                    createToken(
                        DocCommentTokenType.BRACE_CLOSE,
                        start,
                        index,
                    )
                }
            }
            isBlockTagAt(start) -> {
                mode = DocCommentLexerMode.BLOCK_TAG_START
                Token.NONE
            }
            else -> {
                recordUnexpected("after 'else' in '@if' tag")
                Token.NONE
            }
        }
    }

    /**
     * Checks whether the line starting at [lineStart] begins with `\h*\*?\h*:?@[a-zA-Z]`.
     *
     * @return the index of `:` or `@` if matched, or `-1` otherwise.
     */
    private fun findBlockTagStartAtLine(lineStart: Int): Int {
        val p = skipLinePrefix(lineStart)
        return if (isBlockTagAt(p)) p else -1
    }

    /**
     * Checks whether the line starting at [lineStart] begins with `\h*\*?\h*\}`.
     *
     * @return the index of `}` if matched, or `-1` otherwise.
     */
    private fun findClosingBraceAtLine(lineStart: Int): Int {
        val p = skipLinePrefix(lineStart)
        return if (p < endExclusive && text[p] == '}') p else -1
    }

    /** Skips `\h*\*?\h*` starting at [lineStart] and returns the resulting index. */
    private fun skipLinePrefix(lineStart: Int): Int {
        var p = lineStart
        while (p < endExclusive && isHorizontalWhitespace(text[p])) {
            p++
        }
        if (p < endExclusive && text[p] == '*') {
            p++
        }
        while (p < endExclusive && isHorizontalWhitespace(text[p])) {
            p++
        }
        return p
    }

    /** Returns `true` if [offset] starts `@[a-zA-Z]` or `:@[a-zA-Z]`. */
    private fun isBlockTagAt(offset: Int): Boolean =
        (offset + 1 < endExclusive && text[offset] == '@' && isAsciiLetter(text[offset + 1])) ||
            (offset + 2 < endExclusive &&
                text[offset] == ':' &&
                text[offset + 1] == '@' &&
                isAsciiLetter(text[offset + 2]))

    /**
     * Returns `true` if [offset] starts an inline `{@...}` tag (`{@` followed by optional
     * horizontal whitespace and an ASCII letter).
     */
    private fun isInlineTagStartAt(offset: Int): Boolean {
        if (offset + 2 >= endExclusive || text[offset] != '{' || text[offset + 1] != '@') {
            return false
        }
        var p = offset + 2
        while (p < endExclusive && isHorizontalWhitespace(text[p])) {
            p++
        }
        return p < endExclusive && isAsciiLetter(text[p])
    }

    /**
     * Returns `true` if the currently open inline tag(s) at [fromIndex] have matching closing `}`
     * brace(s) before any enclosing `@if` branches close and before [endExclusive].
     */
    private fun hasClosingInlineBrace(
        fromIndex: Int,
        currentInlineDepth: Int,
        currentBranchDepth: Int,
    ): Boolean {
        var depth = if (ifDepth > 0) currentBranchDepth + ifDepth else currentInlineDepth
        var i = fromIndex
        while (i < endExclusive) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return true
                }
            }
            i++
        }
        return false
    }

    /**
     * Reports [Issues.INVALID_JAVADOC] for a block tag starting at [atOffset] (`@<name>` or
     * `:@<name>`) inside an inline `{@...}` tag.
     */
    private fun reportBlockTagInsideInlineTag(atOffset: Int) {
        val nameStart = if (text[atOffset] == ':') atOffset + 2 else atOffset + 1
        var nameEnd = nameStart
        while (nameEnd < endExclusive && isAsciiLetter(text[nameEnd])) {
            nameEnd++
        }
        val rawTag = text.substring(atOffset, nameEnd)
        reportIssue(
            Issues.INVALID_JAVADOC,
            "block tag '$rawTag' cannot be used inside an inline tag",
            atOffset,
        )
    }

    /**
     * Returns the exclusive end index of `[startInclusive, endExclusive)` after stripping trailing
     * whitespace characters.
     */
    private fun trimTrailingWhitespace(startInclusive: Int, endExclusive: Int): Int {
        var end = endExclusive
        while (end > startInclusive && text[end - 1].isWhitespace()) {
            end--
        }
        return end
    }
}
