/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.source.javadoc

import org.junit.Test

class ConditionalJavadocTest : BaseJavadocTest() {
    fun checkConditionalParse(
        text: String,
        flags: Map<String, Boolean> = emptyMap(),
        expectedJavadocIssues: String = "",
        expectedStructure: String,
    ) {
        checkParse(
            text,
            flagToEnabledStatus = flags,
            expectedJavadocIssues = expectedJavadocIssues,
            expectedStructure = expectedStructure,
        )
    }

    @Test
    fun `Test if - missing expr`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if {missing expr }}after.
                 */
            """,
            expectedJavadocIssues =
                """
                    2:11: missing <expr> [InvalidIfTag]
                    2:16: expected '(', found '{' [InvalidJavadoc]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if - unbalanced parenthesis`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG) {unbalanced parenthesis }}after.
                 */
            """,
            expectedJavadocIssues =
                """
                    2:39: expected ')', found '{' [InvalidJavadoc]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if - unknown function`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (blah()) {unknown function }}after.
                 */
            """,
            expectedJavadocIssues =
                """
                    2:17: unknown function 'blah', expected 'flag' [InvalidJavadocExpr]
                    2:22: expected field reference, found ')' [InvalidJavadoc]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if - missing true expression`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))}after.
                 */
            """,
            expectedJavadocIssues =
                """
                    2:39: expected '{', found '}' [InvalidJavadoc]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if - qualified flag with underscores and numbers`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(pkg.Flags.TEST_FLAG1))
                 *   {true }
                 * else
                 *   {false }
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "pkg.Flags.TEST_FLAG1" to false,
                ),
            expectedStructure =
                """
                    text: 'Before false after.'
                """,
        )
    }

    @Test
    fun `Test if - no else - false`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))
                 *   {true }
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to false,
                ),
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if - no else - true`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))
                 *   {true }
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to true,
                ),
            expectedStructure =
                """
                    text: 'Before true after.'
                """,
        )
    }

    @Test
    fun `Test if - with else - false`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))
                 *   {true }
                 * else
                 *   {false }
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to false,
                ),
            expectedStructure =
                """
                    text: 'Before false after.'
                """,
        )
    }

    @Test
    fun `Test if - with else - true`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))
                 *   {true }
                 * else
                 *   {false }
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to true,
                ),
            expectedStructure =
                """
                    text: 'Before true after.'
                """,
        )
    }

    @Test
    fun `Test if - inside if tag`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))
                 *   {{@if (flag(Flags.NESTED_TEST_FLAG))
                 *       {true }
                 *     else
                 *       {false }
                 *   }}
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to true,
                    "Flags.NESTED_TEST_FLAG" to true,
                ),
            expectedStructure =
                """
                    text: 'Before true after.'
                """,
        )
    }

    @Test
    fun `Test if - inside inline tag`() {
        checkConditionalParse(
            """
                /**
                 * {@literal Before {@if (flag(Flags.TEST_FLAG)) {inline }}after}.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to true,
                ),
            expectedStructure =
                """
                    inlineTag: literal
                      text: 'Before inline after'
                    text: '.'
                """,
        )
    }

    @Test
    fun `Test if - inside link tag - false`() {
        checkConditionalParse(
            """
                /**
                 * Before {@link {@if (flag(Flags.TEST_FLAG)) {ref1} else {ref2}} label} after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to false,
                ),
            expectedStructure =
                """
                    text: 'Before '
                    inlineTag: link LabeledRefTagData(sourceReference=ref2, resolvedReference=ClassReference(qualifiedName=resolved.ref2))
                      text: 'label'
                    text: ' after.'
                """,
        )
    }

    @Test
    fun `Test if - inside link tag - true`() {
        checkConditionalParse(
            """
                /**
                 * Before {@link {@if (flag(Flags.TEST_FLAG)) {ref1} else {ref2}} label} after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to true,
                ),
            expectedStructure =
                """
                    text: 'Before '
                    inlineTag: link LabeledRefTagData(sourceReference=ref1, resolvedReference=ClassReference(qualifiedName=resolved.ref1))
                      text: 'label'
                    text: ' after.'
                """,
        )
    }

    @Test
    fun `Test if - containing inline tag`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))
                 *   {{@code literal content} }
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to true,
                ),
            expectedStructure =
                """
                    text: 'Before '
                    inlineTag: code
                      text: 'literal content'
                    text: ' after.'
                """,
        )
    }

    @Test
    fun `Test if - containing inline tag - false`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))
                 *   {{@code literal content} }
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to false,
                ),
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if - containing inline tag followed by text - false`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG)) { text before {@link SomeClass} text after }} after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to false,
                ),
            expectedStructure =
                """
                    text: 'Before  after.'
                """,
        )
    }

    @Test
    fun `Test if - containing inline tag at end of branch - false`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG)) { text before {@link SomeClass}}} after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to false,
                ),
            expectedStructure =
                """
                    text: 'Before  after.'
                """,
        )
    }
}
