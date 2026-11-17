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
 * Represents the kind of a [Token] produced by a lexer.
 *
 * @property name the name of the token type, used for debugging and formatting.
 * @property canBeIdentifier `true` if this token is an identifier or a keyword that can appear in
 *   an identifier position (such as a package, class, method, field, property, or parameter name).
 */
class TokenType(
    val name: String,
    val canBeIdentifier: Boolean = false,
) {
    /** Unique 8-bit identifier (0..254) assigned on creation for packing into [Token]. */
    internal val id: Int = register(this)

    override fun toString(): String = name

    companion object {
        private const val MAX_TOKEN_TYPES = 255
        private val registry = arrayOfNulls<TokenType>(MAX_TOKEN_TYPES)
        private var nextId = 0

        @Synchronized
        private fun register(type: TokenType): Int {
            val id = nextId++
            check(id < MAX_TOKEN_TYPES) {
                "Exceeded maximum number of TokenTypes ($MAX_TOKEN_TYPES)"
            }
            registry[id] = type
            return id
        }

        internal fun byId(id: Int): TokenType = registry[id]!!
    }
}

/**
 * A lexical token produced by a lexer, represented as a packed 64-bit value:
 * - Bits 56..63 (8 bits): [TokenType.id]
 * - Bits 32..55 (24 bits): token length (`endOffset - startOffset`)
 * - Bits 0..31 (32 bits): [startOffset]
 */
@JvmInline
value class Token private constructor(private val packed: Long) {
    constructor(
        type: TokenType,
        startOffset: Int,
        endOffset: Int,
    ) : this(
        (type.id.toLong() shl 56) or
            (((endOffset - startOffset).toLong() and 0xFFFFFFL) shl 32) or
            (startOffset.toLong() and 0xFFFFFFFFL)
    )

    /** The [TokenType] representing the kind of token. */
    val type: TokenType
        get() = TokenType.byId((packed ushr 56).toInt())

    /** 0-based start index of this token relative to the start of the parsed text range. */
    val startOffset: Int
        get() = packed.toInt()

    /** 0-based exclusive end index of this token relative to the start of the parsed text range. */
    val endOffset: Int
        get() = packed.toInt() + ((packed ushr 32).toInt() and 0xFFFFFF)

    /** Returns the raw string content of this token sliced from [sourceText]. */
    fun text(sourceText: String): String = sourceText.substring(startOffset, endOffset)

    /**
     * Formats this token as a human-readable string, escaping `\r`, `\n`, and `\t` in the token
     * text sliced from [sourceText], and optionally appending the token's character offset range
     * `([startOffset]..[endOffset])` when [includePosition] is `true`.
     */
    fun format(sourceText: String, includePosition: Boolean = true): String {
        val escaped =
            text(sourceText).replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
        return if (includePosition) {
            "$type '$escaped' ($startOffset..$endOffset)"
        } else {
            "$type '$escaped'"
        }
    }

    override fun toString(): String =
        if (this == NONE) "NONE" else "$type ($startOffset..$endOffset)"

    companion object {
        /**
         * Sentinel value representing the absence of a token, avoiding nullable `Token?` boxing.
         */
        val NONE = Token(-1L)
    }
}
