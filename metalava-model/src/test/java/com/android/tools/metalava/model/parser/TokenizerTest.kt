/*
 * Copyright (C) 2023 The Android Open Source Project
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

import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TokenizerTest(private val params: Params) {

    data class Params(
        val input: String,
        val label: String = input,
        val purpose: TokenPurpose = TokenPurpose.GENERAL,
        val expectedTokens: List<String>? = null,
        val expectedError: String? = null,
    ) {
        init {
            if (expectedTokens == null && expectedError == null) {
                throw IllegalArgumentException(
                    "Expected one of `expectedToken` and `expectedError`, found neither"
                )
            } else if (expectedTokens != null && expectedError != null) {
                throw IllegalArgumentException(
                    "Expected one of `expectedToken` and `expectedError`, found both"
                )
            }
        }

        override fun toString(): String = "$label,purpose=$purpose"
    }

    companion object {
        private val params =
            listOf(
                Params(
                    input = """  "string"  """,
                    expectedTokens = listOf(""""string""""),
                ),
                Params(
                    input = """  "string  """,
                    expectedError = """api.txt:1: Unexpected end of file for " starting at 1""",
                ),
                Params(
                    input = """  "string\""",
                    expectedError = """api.txt:1: Unexpected end of file for " starting at 1""",
                ),
                // Test handling of empty parentheses.
                Params(
                    input = """@pkg.Annotation()""",
                    purpose = TokenPurpose.VALUE,
                    expectedTokens = listOf("""@pkg.Annotation()"""),
                ),
                Params(
                    input = """@pkg.Annotation()""",
                    purpose = TokenPurpose.GENERAL,
                    expectedTokens = listOf("@pkg.Annotation", "(", ")"),
                ),
                // Test handling of empty parentheses with extra space.
                Params(
                    input = """@pkg.Annotation( )""",
                    purpose = TokenPurpose.VALUE,
                    expectedTokens = listOf("@pkg.Annotation( )"),
                ),
                Params(
                    input = """@pkg.Annotation( )""",
                    purpose = TokenPurpose.GENERAL,
                    expectedTokens = listOf("@pkg.Annotation", "(", ")"),
                ),
                // Test handling of parentheses with one parameter.
                Params(
                    input = """@pkg.Annotation("string")""",
                    purpose = TokenPurpose.VALUE,
                    expectedTokens = listOf("""@pkg.Annotation("string")"""),
                ),
                Params(
                    input = """@pkg.Annotation("string")""",
                    purpose = TokenPurpose.GENERAL,
                    expectedTokens = listOf("@pkg.Annotation", "(", "\"string\"", ")"),
                ),
                // Test handling of parentheses with multiple, space separated parameters.
                Params(
                    input = """@pkg.Annotation(stringAttr="string", intAttr=1)""",
                    purpose = TokenPurpose.VALUE,
                    expectedTokens = listOf("@pkg.Annotation(stringAttr=\"string\", intAttr=1)"),
                ),
                Params(
                    input = """@pkg.Annotation(stringAttr="string", intAttr=1)""",
                    purpose = TokenPurpose.GENERAL,
                    expectedTokens =
                        listOf(
                            "@pkg.Annotation",
                            "(",
                            "stringAttr",
                            "=",
                            "\"string\"",
                            ",",
                            "intAttr",
                            "=",
                            "1",
                            ")",
                        ),
                ),
                // Test handling of nested layer of parentheses.
                Params(
                    input = """@pkg.Annotation(attr=1, nested=@pkg.Nested("string"))""",
                    purpose = TokenPurpose.VALUE,
                    expectedTokens =
                        listOf("""@pkg.Annotation(attr=1, nested=@pkg.Nested("string"))"""),
                ),
                Params(
                    input = """@pkg.Annotation(attr=1, nested=@pkg.Nested("string"))""",
                    purpose = TokenPurpose.GENERAL,
                    expectedTokens =
                        listOf(
                            "@pkg.Annotation",
                            "(",
                            "attr",
                            "=",
                            "1",
                            ",",
                            "nested",
                            "=",
                            "@pkg.Nested",
                            "(",
                            "\"string\"",
                            ")",
                            ")",
                        ),
                ),
                // Test handling of unmatched open parentheses.
                Params(
                    input = """@pkg.Annotation(""",
                    purpose = TokenPurpose.VALUE,
                    expectedError = """api.txt:1: Unexpected end of file for ( starting at 1""",
                ),
                Params(
                    input = """@pkg.Annotation(""",
                    purpose = TokenPurpose.GENERAL,
                    expectedTokens = listOf("@pkg.Annotation", "("),
                ),
                // Test handling of trailing closed parentheses.
                Params(
                    input = """1)""",
                    purpose = TokenPurpose.VALUE,
                    expectedTokens = listOf("1", ")"),
                ),
                Params(
                    input = """1)""",
                    purpose = TokenPurpose.GENERAL,
                    expectedTokens = listOf("1", ")"),
                ),
                // Test handling of unmatched open quotes.
                Params(
                    input = """ @pkg.Annotation("string """,
                    purpose = TokenPurpose.VALUE,
                    expectedError = """api.txt:1: Unexpected end of file for " starting at 1""",
                ),
                Params(
                    input = """ value=1""",
                    expectedTokens = listOf("value", "=", "1"),
                ),
                Params(
                    label = "line comment",
                    input =
                        """
                            // Comment before token
                            name
                        """,
                    expectedTokens = listOf("name"),
                ),
                Params(
                    input = """test.pkg.Generic<String>""",
                    expectedTokens = listOf("test.pkg.Generic<String>"),
                ),
                Params(
                    input = """test.pkg.Generic<String, Integer>""",
                    expectedTokens = listOf("test.pkg.Generic<String, Integer>"),
                ),
                Params(
                    input = """test.pkg.Generic<String, Integer, test.pkg.Nested<A, B>>""",
                    expectedTokens =
                        listOf("test.pkg.Generic<String, Integer, test.pkg.Nested<A, B>>"),
                ),
                Params(
                    input = """<A extends Other, B>""",
                    expectedTokens = listOf("<", "A", "extends", "Other", ",", "B", ">"),
                ),
                Params(
                    input = """<A extends Other<A>>""",
                    expectedTokens = listOf("<", "A", "extends", "Other<A>", ">"),
                ),
                Params(
                    input = """Other<String""",
                    expectedError = "api.txt:1: Unexpected end of file for < starting at 1",
                ),
                // Test handling of braces.
                Params(
                    input = """{1, 2}""",
                    purpose = TokenPurpose.GENERAL,
                    expectedTokens = listOf("{", "1", ",", "2", "}"),
                ),
                Params(
                    input = """{1, 2}""",
                    purpose = TokenPurpose.VALUE,
                    // TODO(b/354633349): This is wrong, should be a single token.
                    expectedTokens = listOf("{1, 2}"),
                ),
                // Test handling of unbalanced brace.
                Params(
                    input = """{1,""",
                    purpose = TokenPurpose.GENERAL,
                    expectedTokens = listOf("{", "1", ","),
                ),
                Params(
                    input = """{1,""",
                    purpose = TokenPurpose.VALUE,
                    expectedError = "api.txt:1: Unexpected end of file for { starting at 1",
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "<{0}>") fun testParams(): List<Params> = params
    }

    @Test
    fun `check token`() {
        val tokenizer = Tokenizer(Path.of("api.txt"), params.input.toCharArray())

        fun requireToken(): String {
            return tokenizer.requireToken(purpose = params.purpose)
        }

        params.expectedError?.let { expectedError ->
            val exception = assertThrows(ParseException::class.java) { requireToken() }
            assertEquals(expectedError, exception.message)
        }

        params.expectedTokens?.let { expectedTokens ->
            val tokens = buildList {
                do {
                    tokenizer.getToken(purpose = params.purpose)?.let { token -> add(token) }
                        ?: break
                } while (true)
            }
            assertEquals(expectedTokens, tokens)
        }
    }
}
