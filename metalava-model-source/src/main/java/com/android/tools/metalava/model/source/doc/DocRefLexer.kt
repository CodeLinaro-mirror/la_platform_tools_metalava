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

import com.android.tools.metalava.model.parser.SharedLexer
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenType

/**
 * Additional [TokenType]s emitted by [DocRefLexer] on top of [SharedTokenType] when tokenizing
 * Javadoc references.
 */
internal object DocRefTokenType {
    /** A single `#` separating a class reference from a member name. */
    val HASH = TokenType("HASH")

    /**
     * A `##` URI fragment (e.g. `##my-anchor`).
     *
     * Spans the entire `##fragment` sequence, where the fragment string following `##` may be empty
     * if `##` is at the end of input or immediately followed by whitespace or `#`.
     */
    val URI_FRAGMENT = TokenType("URI_FRAGMENT")
}

/**
 * Lexer for Javadoc tag references (such as `@see` and `{@link ...}`).
 *
 * Extends [SharedLexer] so that a single [TokenStream] can tokenize both the outer reference
 * structure (qualified class names, `#member`, `##fragment`, parameter lists) and parameter types
 * parsed via [DocTypeParser.parseFromStream].
 */
internal class DocRefLexer(
    text: String,
    startInclusive: Int = 0,
    endExclusive: Int = text.length,
) : SharedLexer(text, startInclusive, endExclusive) {

    override fun tryMatchAdditionalToken(): Token {
        val start = index
        if (text[start] != '#') return Token.NONE

        return if (start + 1 < endExclusive && text[start + 1] == '#') {
            index = start + 2
            while (index < endExclusive) {
                val c = text[index]
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '#') break
                index++
            }
            createToken(
                DocRefTokenType.URI_FRAGMENT,
                start,
                index,
            )
        } else {
            index = start + 1
            createToken(DocRefTokenType.HASH, start, index)
        }
    }

    /**
     * Only `extends` and `super` (used in wildcard type bounds) are treated as keywords in Javadoc
     * references; all other Java identifiers (including Kotlin soft keywords like `reified` or
     * `dynamic`) are emitted as [SharedTokenType.IDENTIFIER].
     */
    override fun resolveKeywordOrIdentifier(start: Int, end: Int): TokenType {
        val length = end - start
        return when {
            matchSlice(start, length, "extends") -> SharedTokenType.EXTENDS
            matchSlice(start, length, "super") -> SharedTokenType.SUPER
            else -> SharedTokenType.IDENTIFIER
        }
    }
}
