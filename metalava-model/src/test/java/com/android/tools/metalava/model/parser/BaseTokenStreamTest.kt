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
import org.junit.Assert.assertThrows
import org.junit.Test

abstract class BaseTokenStreamTest {
    protected object TestTokenType {
        val WORD = TokenType("WORD")
        val PUNCT = TokenType("PUNCT")
        val EOF = TokenType("EOF")
    }

    protected val sampleTokens =
        listOf(
            Token(TestTokenType.WORD, 0, 1),
            Token(TestTokenType.PUNCT, 1, 2),
            Token(TestTokenType.WORD, 2, 3),
            Token(TestTokenType.EOF, 3, 3),
        )

    protected abstract fun createTokenStream(tokens: List<Token>): TokenStream

    @Test
    fun `Test peek and consume`() {
        val stream = createTokenStream(sampleTokens)

        assertEquals(sampleTokens[0], stream.peek())
        assertEquals(TestTokenType.WORD, stream.peekType())
        assertEquals(sampleTokens[0], stream.consume())

        assertEquals(sampleTokens[1], stream.peek())
        assertEquals(sampleTokens[1], stream.consume())

        assertEquals(sampleTokens[2], stream.consume())
        assertEquals(sampleTokens[3], stream.consume())

        // Consuming past the end returns the final (EOF) token.
        assertEquals(sampleTokens[3], stream.consume())
    }

    @Test
    fun `Test match`() {
        val stream = createTokenStream(sampleTokens)

        // Mismatched type does not consume the token.
        assertFalse(stream.match(TestTokenType.PUNCT))
        assertEquals(sampleTokens[0], stream.peek())

        // Matching type consumes the token.
        assertTrue(stream.match(TestTokenType.WORD))
        assertEquals(sampleTokens[1], stream.peek())
    }

    @Test
    fun `Test skip`() {
        val stream = createTokenStream(sampleTokens)

        stream.skip(0)
        assertEquals(sampleTokens[0], stream.peek())

        stream.skip(2)
        assertEquals(sampleTokens[2], stream.consume())
    }

    @Test
    fun `Test negative skip count throws IllegalArgumentException`() {
        val stream = createTokenStream(sampleTokens)
        assertThrows(IllegalArgumentException::class.java) { stream.skip(-1) }
    }
}
