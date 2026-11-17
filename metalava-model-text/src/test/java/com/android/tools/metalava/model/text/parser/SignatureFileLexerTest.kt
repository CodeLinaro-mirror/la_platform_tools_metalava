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

import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SignatureFileLexerTest {

    /**
     * Tokenizes [text] using [SignatureFileLexer] and returns all emitted [Token]s, including the
     * trailing [SharedTokenType.EOF] token.
     */
    private fun tokenize(text: String): List<Token> {
        val stream = SignatureFileLexer(text).tokenize()
        val tokens = mutableListOf<Token>()
        while (true) {
            val token = stream.consume()
            tokens.add(token)
            if (token.type == SharedTokenType.EOF) break
        }
        return tokens
    }

    /**
     * Tokenizes [text] using [SignatureFileLexer] and asserts that the emitted tokens (excluding
     * [SharedTokenType.EOF]) match the [expected] `(type, text)` pairs in order.
     */
    private fun assertTokenTypes(text: String, vararg expected: Pair<TokenType, String>) {
        val tokens = tokenize(text).dropLast(1) // Drop EOF
        assertThat(tokens.map { it.type to it.text(text) })
            .containsExactlyElementsIn(expected)
            .inOrder()
    }

    @Test
    fun `Tokenize signature punctuation and target languages`() {
        assertTokenTypes(
            "@KotlinOnly @OtherAnno public @interface Foo { record_component #0 a: int; }",
            SignatureTokenType.TARGET_LANGUAGE to "@KotlinOnly",
            SharedTokenType.AT to "@",
            SharedTokenType.IDENTIFIER to "OtherAnno",
            SignatureTokenType.PUBLIC to "public",
            SignatureTokenType.ANNOTATION_INTERFACE to "@interface",
            SharedTokenType.IDENTIFIER to "Foo",
            SharedTokenType.BRACE_OPEN to "{",
            SignatureTokenType.RECORD_COMPONENT to "record_component",
            SignatureTokenType.HASH to "#",
            SharedTokenType.NUMBER_LITERAL to "0",
            SharedTokenType.IDENTIFIER to "a",
            SharedTokenType.COLON to ":",
            SharedTokenType.IDENTIFIER to "int",
            SignatureTokenType.SEMICOLON to ";",
            SharedTokenType.BRACE_CLOSE to "}",
        )
    }

    @Test
    fun `Tokenize hyphenated modifiers and mangled method names`() {
        assertTokenTypes(
            "public sealed non-exhaustive non-sealed class -Foo { method box-impl(int); }",
            SignatureTokenType.PUBLIC to "public",
            SignatureTokenType.SEALED to "sealed",
            SignatureTokenType.NON_EXHAUSTIVE to "non-exhaustive",
            SignatureTokenType.NON_SEALED to "non-sealed",
            SharedTokenType.CLASS to "class",
            SharedTokenType.IDENTIFIER to "-Foo",
            SharedTokenType.BRACE_OPEN to "{",
            SignatureTokenType.METHOD to "method",
            SharedTokenType.IDENTIFIER to "box-impl",
            SharedTokenType.PAREN_OPEN to "(",
            SharedTokenType.IDENTIFIER to "int",
            SharedTokenType.PAREN_CLOSE to ")",
            SignatureTokenType.SEMICOLON to ";",
            SharedTokenType.BRACE_CLOSE to "}",
        )
    }

    @Test
    fun `Tokenize mangled names containing consecutive hyphens`() {
        assertTokenTypes(
            "public final class -Foo--Bar { method foo--bar(long, long); }",
            SignatureTokenType.PUBLIC to "public",
            SignatureTokenType.FINAL to "final",
            SharedTokenType.CLASS to "class",
            SharedTokenType.IDENTIFIER to "-Foo--Bar",
            SharedTokenType.BRACE_OPEN to "{",
            SignatureTokenType.METHOD to "method",
            SharedTokenType.IDENTIFIER to "foo--bar",
            SharedTokenType.PAREN_OPEN to "(",
            SharedTokenType.IDENTIFIER to "long",
            SharedTokenType.COMMA to ",",
            SharedTokenType.IDENTIFIER to "long",
            SharedTokenType.PAREN_CLOSE to ")",
            SignatureTokenType.SEMICOLON to ";",
            SharedTokenType.BRACE_CLOSE to "}",
        )
    }

    @Test
    fun `Keywords followed by punctuation are tokenized as identifiers`() {
        assertTokenTypes(
            "method public value(optional value: int, optional: String): void;",
            SignatureTokenType.METHOD to "method",
            SignatureTokenType.PUBLIC to "public",
            SharedTokenType.IDENTIFIER to "value",
            SharedTokenType.PAREN_OPEN to "(",
            SignatureTokenType.OPTIONAL to "optional",
            SharedTokenType.IDENTIFIER to "value",
            SharedTokenType.COLON to ":",
            SharedTokenType.IDENTIFIER to "int",
            SharedTokenType.COMMA to ",",
            SharedTokenType.IDENTIFIER to "optional",
            SharedTokenType.COLON to ":",
            SharedTokenType.IDENTIFIER to "String",
            SharedTokenType.PAREN_CLOSE to ")",
            SharedTokenType.COLON to ":",
            SharedTokenType.IDENTIFIER to "void",
            SignatureTokenType.SEMICOLON to ";",
        )
    }
}
