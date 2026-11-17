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

/** Produces [Token]s on demand for a [TokenStream]. */
fun interface TokenProducer {
    /**
     * Returns the next [Token] from the input.
     *
     * Once the input is exhausted, subsequent calls must return an EOF token.
     */
    fun nextToken(): Token
}

/** A forward-moving stream of [Token]s. */
sealed interface TokenStream {
    /** Returns the next token to be consumed without consuming it. */
    fun peek(): Token

    /** Returns the [TokenType] of the next token to be consumed. */
    fun peekType(): TokenType = peek().type

    /** Consumes and returns the next token from the stream. */
    fun consume(): Token

    /** Consumes the next token if its type equals [type], returning `true`. */
    fun match(type: TokenType): Boolean {
        if (peekType() == type) {
            consume()
            return true
        }
        return false
    }

    /** Discards [count] tokens from the front of the stream. */
    fun skip(count: Int) {
        require(count >= 0) { "count must be non-negative, was $count" }
        repeat(count) { consume() }
    }

    companion object {
        /** Creates a [TokenStream] backed by a pre-tokenized [List] of [Token]s. */
        fun eager(tokens: List<Token>): TokenStream = EagerTokenStream(tokens)

        /** Creates a lazy [TokenStream] that pulls tokens on demand from [producer]. */
        fun lazy(producer: TokenProducer): TokenStream = LazyTokenStream(producer)
    }
}

/**
 * A [TokenStream] implementation backed by a pre-tokenized [List] of [Token]s ending with an EOF
 * token.
 */
internal class EagerTokenStream(
    private val tokens: List<Token>,
) : TokenStream {
    init {
        require(tokens.isNotEmpty()) { "tokens must not be empty" }
    }

    /** The index of the current token in [tokens]. */
    private var current = 0

    override fun peek(): Token = tokens[current]

    override fun consume(): Token {
        val token = tokens[current]
        if (current < tokens.lastIndex) {
            current++
        }
        return token
    }
}

/**
 * A lazy [TokenStream] implementation that pulls [Token]s on demand from [producer].
 *
 * Only the single peeked lookahead token is buffered in memory; once consumed via [consume] or
 * [skip], the token is immediately discarded.
 */
internal class LazyTokenStream(
    private val producer: TokenProducer,
) : TokenStream {
    /** The next token to be consumed, if already peeked, or [Token.NONE] if not buffered. */
    private var next: Token = Token.NONE

    override fun peek(): Token {
        var current = next
        if (current == Token.NONE) {
            current = producer.nextToken()
            next = current
        }
        return current
    }

    override fun consume(): Token {
        val current = next
        if (current != Token.NONE) {
            next = Token.NONE
            return current
        }
        return producer.nextToken()
    }
}
