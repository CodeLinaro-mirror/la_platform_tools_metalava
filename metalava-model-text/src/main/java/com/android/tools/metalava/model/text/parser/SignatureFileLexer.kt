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

package com.android.tools.metalava.model.text.parser

import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.parser.SharedLexer
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenType

/**
 * Token types specific to API signature files, emitted by [SignatureFileLexer] alongside
 * [SharedTokenType] tokens.
 */
internal object SignatureTokenType {
    // Punctuation exclusive to signature file structure
    val SEMICOLON = TokenType("SEMICOLON")
    val HASH = TokenType("HASH")

    // Target language markers (e.g. @KotlinOnly, @BytecodeOnly, etc.)
    val TARGET_LANGUAGE = TokenType("TARGET_LANGUAGE")

    // Top-level & Class Kind Keywords (note: CLASS and EXTENDS are in SharedTokenType)
    val PACKAGE = TokenType("PACKAGE", canBeIdentifier = true)
    val INTERFACE = TokenType("INTERFACE", canBeIdentifier = true)
    val ENUM = TokenType("ENUM", canBeIdentifier = true)
    val ANNOTATION_INTERFACE = TokenType("ANNOTATION_INTERFACE")
    val TYPEALIAS = TokenType("TYPEALIAS", canBeIdentifier = true)
    val RECORD = TokenType("RECORD", canBeIdentifier = true)

    // Class Hierarchy & Member Clause Keywords
    val IMPLEMENTS = TokenType("IMPLEMENTS", canBeIdentifier = true)
    val PERMITS = TokenType("PERMITS", canBeIdentifier = true)
    val THROWS = TokenType("THROWS", canBeIdentifier = true)
    val DEFAULT = TokenType("DEFAULT", canBeIdentifier = true)

    // Class Member Kind Keywords
    val CTOR = TokenType("CTOR", canBeIdentifier = true)
    val METHOD = TokenType("METHOD", canBeIdentifier = true)
    val FIELD = TokenType("FIELD", canBeIdentifier = true)
    val ENUM_CONSTANT = TokenType("ENUM_CONSTANT", canBeIdentifier = true)
    val PROPERTY = TokenType("PROPERTY", canBeIdentifier = true)
    val RECORD_COMPONENT = TokenType("RECORD_COMPONENT", canBeIdentifier = true)

    // Visibility & Modifier Keywords
    val PUBLIC = TokenType("PUBLIC", canBeIdentifier = true)
    val PROTECTED = TokenType("PROTECTED", canBeIdentifier = true)
    val PRIVATE = TokenType("PRIVATE", canBeIdentifier = true)
    val INTERNAL = TokenType("INTERNAL", canBeIdentifier = true)
    val STATIC = TokenType("STATIC", canBeIdentifier = true)
    val FINAL = TokenType("FINAL", canBeIdentifier = true)
    val DEPRECATED = TokenType("DEPRECATED", canBeIdentifier = true)
    val ABSTRACT = TokenType("ABSTRACT", canBeIdentifier = true)
    val TRANSIENT = TokenType("TRANSIENT", canBeIdentifier = true)
    val VOLATILE = TokenType("VOLATILE", canBeIdentifier = true)
    val SEALED = TokenType("SEALED", canBeIdentifier = true)
    val NON_SEALED = TokenType("NON_SEALED")
    val EXHAUSTIVE = TokenType("EXHAUSTIVE", canBeIdentifier = true)
    val NON_EXHAUSTIVE = TokenType("NON_EXHAUSTIVE")
    val SYNCHRONIZED = TokenType("SYNCHRONIZED", canBeIdentifier = true)
    val NATIVE = TokenType("NATIVE", canBeIdentifier = true)
    val STRICTFP = TokenType("STRICTFP", canBeIdentifier = true)
    val INFIX = TokenType("INFIX", canBeIdentifier = true)
    val OPERATOR = TokenType("OPERATOR", canBeIdentifier = true)
    val INLINE = TokenType("INLINE", canBeIdentifier = true)
    val VALUE = TokenType("VALUE", canBeIdentifier = true)
    val SUSPEND = TokenType("SUSPEND", canBeIdentifier = true)
    val VARARG = TokenType("VARARG", canBeIdentifier = true)
    val FUN = TokenType("FUN", canBeIdentifier = true)
    val DATA = TokenType("DATA", canBeIdentifier = true)

    // Parameter Modifier Keywords
    val OPTIONAL = TokenType("OPTIONAL", canBeIdentifier = true)
    val CONTEXT = TokenType("CONTEXT", canBeIdentifier = true)
    val RECEIVER = TokenType("RECEIVER", canBeIdentifier = true)
}

/**
 * Lexer that tokenizes API signature files into a lazy stream of [Token]s of [SharedTokenType] and
 * [SignatureTokenType].
 *
 * @param text the full string containing the signature file text to tokenize.
 * @param startInclusive the index in [text] where tokenization should begin.
 * @param endExclusive the index in [text] where tokenization should end.
 */
internal class SignatureFileLexer(
    text: String,
    startInclusive: Int = 0,
    endExclusive: Int = text.length,
) : SharedLexer(text, startInclusive, endExclusive) {

    /**
     * Matches signature-specific punctuation (`;`, `#`), `@interface`, target language prefixes
     * (e.g. `@KotlinOnly`), and identifiers starting with `-` before [SharedLexer] attempts its
     * standard token matching.
     */
    override fun tryMatchAdditionalToken(): Token {
        val start = index
        return when (text[start]) {
            ';' -> {
                index = start + 1
                createToken(SignatureTokenType.SEMICOLON, start, index)
            }
            '#' -> {
                index = start + 1
                createToken(SignatureTokenType.HASH, start, index)
            }
            '@' -> {
                // If '@' is followed by an identifier start character, scan the full
                // '@<identifier>' candidate to check for '@interface' or a target language prefix
                // (e.g. '@KotlinOnly').
                if (start + 1 < endExclusive && Character.isJavaIdentifierStart(text[start + 1])) {
                    var end = start + 2
                    while (end < endExclusive && Character.isJavaIdentifierPart(text[end])) {
                        end++
                    }
                    // Only treat `@interface` or target language markers (e.g. `@KotlinOnly`) as
                    // special signature tokens when not followed by `.` (a qualified annotation
                    // name) or `(` (an annotation argument list). Otherwise, return Token.NONE so
                    // SharedLexer emits AT ('@') for normal annotation parsing.
                    if (end >= endExclusive || (text[end] != '.' && text[end] != '(')) {
                        val length = end - start
                        if (matchSlice(start, length, "@interface")) {
                            index = end
                            return createToken(
                                SignatureTokenType.ANNOTATION_INTERFACE,
                                start,
                                end,
                            )
                        }
                        if (
                            TargetLanguageSet.signatureFileRepresentationToTargetLanguageSet.keys
                                .any { matchSlice(start, length, it) }
                        ) {
                            index = end
                            return createToken(
                                SignatureTokenType.TARGET_LANGUAGE,
                                start,
                                end,
                            )
                        }
                    }
                }
                Token.NONE
            }
            '-' -> {
                // In signature files, some synthetic or mangled identifiers start with a hyphen
                // (e.g. '-Foo'). When '-' is immediately followed by an identifier start
                // character, scan it as an identifier; otherwise return Token.NONE so SharedLexer
                // emits MINUS ('-').
                if (start + 1 < endExclusive && Character.isJavaIdentifierStart(text[start + 1])) {
                    scanIdentifierOrKeyword(start)
                } else {
                    Token.NONE
                }
            }
            else -> Token.NONE
        }
    }

    /**
     * Scans an identifier or keyword starting at [start], extending [SharedLexer] to support
     * hyphenated identifiers and keywords in signature files.
     */
    override fun scanIdentifierOrKeyword(start: Int): Token {
        index = start + 1
        while (index < endExclusive) {
            val c = text[index]
            // Allow '-' anywhere after the start of an identifier or keyword to support hyphenated
            // modifiers (e.g. 'non-sealed', 'non-exhaustive') and mangled names (e.g. 'box-impl',
            // 'foo--bar').
            if (Character.isJavaIdentifierPart(c) || c == '-') {
                index++
            } else {
                break
            }
        }
        // Signature-specific keywords (such as 'method', 'public', 'optional', or 'value') are
        // always followed by whitespace in signature files, whereas identifiers with the same name
        // (such as method names, parameter names, or package segments) may be followed directly by
        // punctuation like '(', ':', ',', '.', or ';'. Only resolve signature keywords when
        // followed by whitespace or end-of-input; otherwise fall back to
        // SharedLexer.resolveKeywordOrIdentifier (which only recognizes shared type/value keywords
        // like 'class', 'extends', and 'super').
        val type =
            if (index >= endExclusive || isWhitespace(text[index])) {
                resolveKeywordOrIdentifier(start, index)
            } else {
                super.resolveKeywordOrIdentifier(start, index)
            }
        return createToken(
            type,
            start,
            index,
        )
    }

    /** Returns `true` if [c] is a whitespace character. */
    private fun isWhitespace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\r'

    /**
     * Maps the slice of [text] from [start] to [end] to a [SignatureTokenType] keyword, or
     * delegates to [SharedLexer.resolveKeywordOrIdentifier] for shared keywords and
     * [SharedTokenType.IDENTIFIER].
     */
    override fun resolveKeywordOrIdentifier(start: Int, end: Int): TokenType {
        val length = end - start
        return when (text[start]) {
            'a' ->
                if (matchSlice(start, length, "abstract")) SignatureTokenType.ABSTRACT
                else SharedTokenType.IDENTIFIER
            'c' ->
                when {
                    matchSlice(start, length, "ctor") -> SignatureTokenType.CTOR
                    matchSlice(start, length, "context") -> SignatureTokenType.CONTEXT
                    else -> super.resolveKeywordOrIdentifier(start, end)
                }
            'd' ->
                when {
                    matchSlice(start, length, "default") -> SignatureTokenType.DEFAULT
                    matchSlice(start, length, "deprecated") -> SignatureTokenType.DEPRECATED
                    matchSlice(start, length, "data") -> SignatureTokenType.DATA
                    else -> SharedTokenType.IDENTIFIER
                }
            'e' ->
                when {
                    matchSlice(start, length, "enum") -> SignatureTokenType.ENUM
                    matchSlice(start, length, "enum_constant") -> SignatureTokenType.ENUM_CONSTANT
                    matchSlice(start, length, "exhaustive") -> SignatureTokenType.EXHAUSTIVE
                    else -> super.resolveKeywordOrIdentifier(start, end)
                }
            'f' ->
                when {
                    matchSlice(start, length, "field") -> SignatureTokenType.FIELD
                    matchSlice(start, length, "final") -> SignatureTokenType.FINAL
                    matchSlice(start, length, "fun") -> SignatureTokenType.FUN
                    else -> SharedTokenType.IDENTIFIER
                }
            'i' ->
                when {
                    matchSlice(start, length, "interface") -> SignatureTokenType.INTERFACE
                    matchSlice(start, length, "implements") -> SignatureTokenType.IMPLEMENTS
                    matchSlice(start, length, "internal") -> SignatureTokenType.INTERNAL
                    matchSlice(start, length, "infix") -> SignatureTokenType.INFIX
                    matchSlice(start, length, "inline") -> SignatureTokenType.INLINE
                    else -> SharedTokenType.IDENTIFIER
                }
            'm' ->
                if (matchSlice(start, length, "method")) SignatureTokenType.METHOD
                else SharedTokenType.IDENTIFIER
            'n' ->
                when {
                    matchSlice(start, length, "non-sealed") -> SignatureTokenType.NON_SEALED
                    matchSlice(start, length, "non-exhaustive") ||
                        matchSlice(start, length, "nonexhaustive") ->
                        SignatureTokenType.NON_EXHAUSTIVE
                    matchSlice(start, length, "native") -> SignatureTokenType.NATIVE
                    else -> SharedTokenType.IDENTIFIER
                }
            'o' ->
                when {
                    matchSlice(start, length, "operator") -> SignatureTokenType.OPERATOR
                    matchSlice(start, length, "optional") -> SignatureTokenType.OPTIONAL
                    else -> SharedTokenType.IDENTIFIER
                }
            'p' ->
                when {
                    matchSlice(start, length, "public") -> SignatureTokenType.PUBLIC
                    matchSlice(start, length, "package") -> SignatureTokenType.PACKAGE
                    matchSlice(start, length, "protected") -> SignatureTokenType.PROTECTED
                    matchSlice(start, length, "private") -> SignatureTokenType.PRIVATE
                    matchSlice(start, length, "property") -> SignatureTokenType.PROPERTY
                    matchSlice(start, length, "permits") -> SignatureTokenType.PERMITS
                    else -> SharedTokenType.IDENTIFIER
                }
            'r' ->
                when {
                    matchSlice(start, length, "record") -> SignatureTokenType.RECORD
                    matchSlice(start, length, "record_component") ->
                        SignatureTokenType.RECORD_COMPONENT
                    matchSlice(start, length, "receiver") -> SignatureTokenType.RECEIVER
                    else -> super.resolveKeywordOrIdentifier(start, end)
                }
            's' ->
                when {
                    matchSlice(start, length, "static") -> SignatureTokenType.STATIC
                    matchSlice(start, length, "sealed") -> SignatureTokenType.SEALED
                    matchSlice(start, length, "synchronized") -> SignatureTokenType.SYNCHRONIZED
                    matchSlice(start, length, "strictfp") -> SignatureTokenType.STRICTFP
                    matchSlice(start, length, "suspend") -> SignatureTokenType.SUSPEND
                    else -> super.resolveKeywordOrIdentifier(start, end)
                }
            't' ->
                when {
                    matchSlice(start, length, "throws") -> SignatureTokenType.THROWS
                    matchSlice(start, length, "typealias") -> SignatureTokenType.TYPEALIAS
                    matchSlice(start, length, "transient") -> SignatureTokenType.TRANSIENT
                    else -> SharedTokenType.IDENTIFIER
                }
            'v' ->
                when {
                    matchSlice(start, length, "volatile") -> SignatureTokenType.VOLATILE
                    matchSlice(start, length, "value") -> SignatureTokenType.VALUE
                    matchSlice(start, length, "vararg") -> SignatureTokenType.VARARG
                    else -> SharedTokenType.IDENTIFIER
                }
            else -> SharedTokenType.IDENTIFIER
        }
    }
}
