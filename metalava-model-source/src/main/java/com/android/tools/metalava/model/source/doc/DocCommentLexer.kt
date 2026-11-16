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

/** Token types produced by [DocCommentLexer]. */
internal object DocCommentTokenType {
    /**
     * A span of description content (either the main description before any block tags or the
     * description body of a block tag), with trailing whitespace trimmed.
     */
    val DESCRIPTION = TokenType("DESCRIPTION")

    /** The `@` character introducing a line-starting block tag. */
    val BLOCK_TAG_START = TokenType("BLOCK_TAG_START")

    /** The alphabetical name (`[a-zA-Z]+`) of a block tag immediately following `@`. */
    val BLOCK_TAG_NAME = TokenType("BLOCK_TAG_NAME")

    /** End of input marker. */
    val EOF = SharedTokenType.EOF
}

/** Lexer modes for [DocCommentLexer]. */
private enum class DocCommentLexerMode {
    /** Scanning description text until a line-starting block tag or EOF. */
    DESCRIPTION,

    /** Positioned at the `@` of a matched line-starting block tag. */
    BLOCK_TAG_START,

    /** Positioned at the tag name immediately following `@`. */
    BLOCK_TAG_NAME,
}

/**
 * Coarse-grained lexer that partitions the body of a documentation comment into description spans
 * and line-starting block tags without parsing inline tag content.
 *
 * Scanning only for line-starting block tags (`^\h*\*?\h*@([a-zA-Z]+)\h*`) allows
 * [DocCommentParser] to wrap each description span in a [LazyContentSupplier], deferring
 * fine-grained [com.android.tools.metalava.model.source.javadoc.JavadocLexer] and
 * [com.android.tools.metalava.model.source.javadoc.JavadocParser] execution until a section's
 * content is actually requested.
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

    override fun exitExpr() {}

    override fun nextToken(): Token {
        while (index < endExclusive) {
            val token =
                when (mode) {
                    DocCommentLexerMode.DESCRIPTION -> tokenizeDescription()
                    DocCommentLexerMode.BLOCK_TAG_START -> tokenizeBlockTagStart()
                    DocCommentLexerMode.BLOCK_TAG_NAME -> tokenizeBlockTagName()
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
     * Scans forward from [index] until the next line-starting block tag or [endExclusive].
     *
     * Emits a [DocCommentTokenType.DESCRIPTION] token with trailing whitespace trimmed if any
     * non-whitespace characters precede the next block tag (or end of input). If no non-whitespace
     * description precedes a matched block tag, transitions directly to emitting the block tag.
     */
    private fun tokenizeDescription(): Token {
        val descStart = index

        while (index < endExclusive) {
            if (atLineStart) {
                // End the preceding description at `index` (the start of this line) rather than
                // `atOffset` so leading indentation and `*` on the block tag's line are excluded
                // from the preceding description span.
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
            }

            // Advance across the current line, treating `\n`, `\r\n`, and standalone `\r` as line
            // breaks so block tags on the next line are recognized regardless of line ending style.
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
            } else {
                index++
            }
        }

        val trimmedEnd = trimTrailingWhitespace(descStart, endExclusive)
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

    /** Emits [DocCommentTokenType.BLOCK_TAG_START] for `@` and enters `BLOCK_TAG_NAME` mode. */
    private fun tokenizeBlockTagStart(): Token {
        val start = index
        index++
        mode = DocCommentLexerMode.BLOCK_TAG_NAME
        return createToken(
            DocCommentTokenType.BLOCK_TAG_START,
            start,
            index,
        )
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

    /**
     * Checks whether the line starting at [lineStart] begins with `\h*\*?\h*@[a-zA-Z]`.
     *
     * @return the index of `@` if matched, or `-1` otherwise.
     */
    private fun findBlockTagStartAtLine(lineStart: Int): Int {
        val p = skipLinePrefix(lineStart)
        return if (isBlockTagAt(p)) p else -1
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

    /** Returns `true` if [offset] starts `@[a-zA-Z]`. */
    private fun isBlockTagAt(offset: Int): Boolean =
        offset + 1 < endExclusive && text[offset] == '@' && isAsciiLetter(text[offset + 1])

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
