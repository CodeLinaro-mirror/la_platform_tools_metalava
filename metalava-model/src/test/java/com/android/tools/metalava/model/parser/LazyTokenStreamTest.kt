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

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class LazyTokenStreamTest : BaseTokenStreamTest() {
    private class CountingTokenProducer(
        private val tokens: List<Token>,
    ) : TokenProducer {
        var producedCount = 0
            private set

        override fun nextToken(): Token {
            val index = producedCount.coerceAtMost(tokens.lastIndex)
            producedCount++
            return tokens[index]
        }
    }

    override fun createTokenStream(tokens: List<Token>): TokenStream =
        TokenStream.lazy(CountingTokenProducer(tokens))

    @Test
    fun `Test lazy production on peek and consume`() {
        val producer = CountingTokenProducer(sampleTokens)
        val stream = TokenStream.lazy(producer)

        // No tokens are produced upon construction.
        assertEquals(0, producer.producedCount)

        // Peeking pulls only the first token.
        assertEquals(sampleTokens[0], stream.peek())
        assertEquals(TestTokenType.WORD, stream.peekType())
        assertEquals(1, producer.producedCount)

        // Consuming returns the already-peeked token without pulling another.
        assertEquals(sampleTokens[0], stream.consume())
        assertEquals(1, producer.producedCount)

        // Consuming with empty lookahead pulls directly from the producer.
        assertEquals(sampleTokens[1], stream.consume())
        assertEquals(2, producer.producedCount)
    }

    @Test
    fun `Test lazy match and skip`() {
        val producer = CountingTokenProducer(sampleTokens)
        val stream = TokenStream.lazy(producer)

        // Skipping 0 tokens does not pull from producer.
        stream.skip(0)
        assertEquals(0, producer.producedCount)

        // Mismatched type pulls 1 token for peek but does not consume it.
        assertFalse(stream.match(TestTokenType.PUNCT))
        assertEquals(1, producer.producedCount)

        // Matching type consumes the buffered token without pulling another.
        assertTrue(stream.match(TestTokenType.WORD))
        assertEquals(1, producer.producedCount)

        // Buffer 1 token via peek, then skip 2 tokens (1 buffered + 1 unbuffered).
        assertEquals(sampleTokens[1], stream.peek())
        assertEquals(2, producer.producedCount)

        stream.skip(2)
        assertEquals(3, producer.producedCount)

        // Next token is sampleTokens[3] (EOF).
        assertEquals(sampleTokens[3], stream.consume())
        assertEquals(4, producer.producedCount)
    }
}
