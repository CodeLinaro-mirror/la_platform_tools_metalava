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

/**
 * Base class for lexers that scan a slice of [text] from [startInclusive] to [endExclusive] and
 * lazily produce a [TokenStream] of [Token]s with offsets in [text].
 *
 * @param text the full string containing the text to tokenize.
 * @param startInclusive the index in [text] where tokenization should begin.
 * @param endExclusive the index in [text] where tokenization should end.
 */
abstract class AbstractLexer(
    protected val text: String,
    protected val startInclusive: Int = 0,
    protected val endExclusive: Int = text.length,
) : TokenProducer {
    /** Current reading position in [text]. */
    protected var index: Int = startInclusive

    /**
     * Returns `true` if the slice of [text] starting at [start] with [length] characters equals
     * [target].
     */
    protected fun matchSlice(start: Int, length: Int, target: String): Boolean =
        length == target.length && text.regionMatches(start, target, 0, length)

    /**
     * Creates and returns a [Token] with [Token.startOffset] and [Token.endOffset] set to
     * [startIndex] and [endIndex].
     *
     * Subclasses may override this to perform additional actions (such as flushing pending state)
     * before creating the token.
     */
    protected open fun createToken(
        type: TokenType,
        startIndex: Int,
        endIndex: Int,
    ): Token =
        Token(
            type,
            startIndex,
            endIndex,
        )

    /**
     * Scans a double-quoted string literal starting at [start], stopping at a matching unescaped
     * `"`, a line break (`\n` or `\r`), or [endExclusive], and returns a
     * [SharedTokenType.STRING_LITERAL] token.
     *
     * Validation of whether the literal was closed and unescaping of its contents is performed by
     * [parseStringLiteralValue].
     */
    protected fun scanStringLiteral(start: Int): Token {
        index = start + 1
        while (index < endExclusive) {
            val c = text[index]
            if (c == '"') {
                index++
                break
            } else if (c == '\n' || c == '\r') {
                break
            } else if (c == '\\') {
                index++
                if (index < endExclusive && text[index] != '\n' && text[index] != '\r') {
                    index++
                }
            } else {
                index++
            }
        }
        return createToken(
            SharedTokenType.STRING_LITERAL,
            start,
            index,
        )
    }

    /**
     * Tokenize the text from [startInclusive] to [endExclusive].
     *
     * @return a lazy [TokenStream] backed by this lexer.
     */
    fun tokenize(): TokenStream = TokenStream.lazy(this)
}
