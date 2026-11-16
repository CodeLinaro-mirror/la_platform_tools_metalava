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

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Unit tests for conditional `@if` block tag parsing, evaluation, and error recovery. */
class ConditionalBlockJavadocTest : BaseDocCommentTest() {
    /**
     * Parses [input] into a [DocComment] with [flags] and verifies [expectedString],
     * [expectedPrintOutput], and [expectedIssues].
     */
    private fun checkConditionalDocComment(
        input: String,
        flags: Map<String, Boolean> = emptyMap(),
        expectedString: String? = null,
        expectedPrintOutput: String? = null,
        expectedIssues: String = "",
        checker: (DocComment) -> Unit = {},
    ) {
        val (docComment, _) =
            createTestDocCommentAndContext(
                input,
                expectedIssues = expectedIssues,
                flagToEnabledStatus = flags,
            )
        if (expectedString != null) {
            assertEquals(expectedString.trimIndent(), docComment.toString())
        }
        if (expectedPrintOutput != null) {
            checkPrintOutput(docComment, expectedPrintOutput)
        }
        checker(docComment)
    }

    /** Verify that an `@if` block tag without `else` includes its tags when the flag is enabled. */
    @Test
    fun `Test @if without else - true`() {
        checkConditionalDocComment(
            input =
                """
                    /**
                     * Summary.
                     *
                     * @if (flag(Flags.TEST_FLAG)) {
                     *     :@see First
                     * }
                     */
                """,
            flags = mapOf("Flags.TEST_FLAG" to true),
            expectedString =
                """
                    description: <<\n * Summary.\n *>>
                    @see <<First>>
                """,
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     * @see resolved.First First
                     */
                """,
        )
    }

    /** Verify that an `@if` block tag without `else` omits its tags when the flag is disabled. */
    @Test
    fun `Test @if without else - false`() {
        checkConditionalDocComment(
            input =
                """
                    /**
                     * Summary.
                     *
                     * @if (flag(Flags.TEST_FLAG)) {
                     *     :@see First
                     * }
                     */
                """,
            flags = mapOf("Flags.TEST_FLAG" to false),
            expectedString =
                """
                    description: <<\n * Summary.\n *>>
                """,
            expectedPrintOutput =
                """
                    /** Summary. */
                """,
        )
    }

    /**
     * Verify that when an `@if ... else` condition evaluates to `true`, the true branch's `:@see`
     * and `:@deprecated` tags are emitted and `hasBlockTagOfType("deprecated")` is `true`.
     */
    @Test
    fun `Test @if with else - true`() {
        checkConditionalDocComment(
            input =
                """
                    /**
                     * Summary.
                     *
                     * @if (flag(Flags.TEST_FLAG)) {
                     *     :@see First
                     *     :@deprecated Use {@link First} instead.
                     * } else {
                     *     :@see Second
                     * }
                     * @return result
                     */
                """,
            flags = mapOf("Flags.TEST_FLAG" to true),
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     *
                     * @return result
                     * @see resolved.First First
                     * @deprecated Use {@link resolved.First First} instead.
                     */
                """,
        ) { docComment ->
            assertTrue(docComment.hasBlockTagOfType("deprecated"))
        }
    }

    /**
     * Verify that when an `@if ... else` condition evaluates to `false`, the `else` branch tags are
     * emitted and an inactive `:@deprecated` tag does not appear on the [DocComment].
     */
    @Test
    fun `Test @if with else - false`() {
        checkConditionalDocComment(
            input =
                """
                    /**
                     * Summary.
                     *
                     * @if (flag(Flags.TEST_FLAG)) {
                     *     :@see First
                     *     :@deprecated Use {@link First} instead.
                     * } else {
                     *     :@see Second
                     * }
                     * @return result
                     */
                """,
            flags = mapOf("Flags.TEST_FLAG" to false),
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     *
                     * @return result
                     * @see resolved.Second Second
                     */
                """,
        ) { docComment ->
            assertFalse(docComment.hasBlockTagOfType("deprecated"))
        }
    }

    /** Verify evaluation of nested `:@if` block tags across outer and inner flag combinations. */
    @Test
    fun `Test nested @if block tags`() {
        val comment =
            """
                /**
                 * Summary.
                 *
                 * @if (flag(Flags.OUTER_FLAG)) {
                 *     :@if (flag(Flags.INNER_FLAG)) {
                 *         :@see BothTrue
                 *     } else {
                 *         :@see OuterTrueInnerFalse
                 *     }
                 * } else {
                 *     :@see OuterFalse
                 * }
                 */
            """

        checkConditionalDocComment(
            input = comment,
            flags = mapOf("Flags.OUTER_FLAG" to true, "Flags.INNER_FLAG" to true),
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     * @see resolved.BothTrue BothTrue
                     */
                """,
        )

        checkConditionalDocComment(
            input = comment,
            flags = mapOf("Flags.OUTER_FLAG" to true, "Flags.INNER_FLAG" to false),
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     * @see resolved.OuterTrueInnerFalse OuterTrueInnerFalse
                     */
                """,
        )

        checkConditionalDocComment(
            input = comment,
            flags = mapOf("Flags.OUTER_FLAG" to false, "Flags.INNER_FLAG" to true),
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     * @see resolved.OuterFalse OuterFalse
                     */
                """,
        )
    }

    /** Verify that single-line `@if (...) { :@see ... } else { :@see ... }` syntax is supported. */
    @Test
    fun `Test single-line @if block tag`() {
        checkConditionalDocComment(
            input =
                """
                    /**
                     * Summary.
                     * @if (flag(Flags.TEST_FLAG)) { :@see First } else { :@see Second }
                     */
                """,
            flags = mapOf("Flags.TEST_FLAG" to false),
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     * @see resolved.Second Second
                     */
                """,
        )
    }

    /**
     * Verify that multi-line `{@code ...}` blocks containing line-starting `@Override` annotations
     * and `{ ... }` braces inside an `@if` branch do not prematurely end the tag or close `@if`.
     */
    @Test
    fun `Test nested braces and annotations inside @code within @if block tag`() {
        checkConditionalDocComment(
            input =
                """
                    /**
                     * Summary.
                     *
                     * @if (flag(Flags.TEST_FLAG)) {
                     *     :@deprecated Example:
                     *     <pre>{@code
                     *     @Override
                     *     void foo() {
                     *     }
                     *     }</pre>
                     * }
                     */
                """,
            flags = mapOf("Flags.TEST_FLAG" to true),
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     *
                     * @deprecated Example:
                     *     <pre>{@code
                     *     @Override
                     *     void foo() {
                     *     }
                     *     }</pre>
                     */
                """,
        )
    }

    /** Verify error reporting and recovery when `@if` is missing its `(<expr>)` condition. */
    @Test
    fun `Test @if error - missing expr`() {
        checkConditionalDocComment(
            input =
                """
                    /**
                     * Summary.
                     * @if {
                     *     :@see First
                     * }
                     * @return result
                     */
                """,
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     * @return result
                     */
                """,
            expectedIssues =
                """
                    3:4: missing <expr> [InvalidIfTag]
                    3:8: expected '(', found '{' [InvalidJavadoc]
                """,
        )
    }

    /** Verify error reporting and recovery when `@if (<expr>` is missing its closing `)`. */
    @Test
    fun `Test @if error - unbalanced parenthesis`() {
        checkConditionalDocComment(
            input =
                """
                    /**
                     * Summary.
                     * @if (flag(Flags.TEST_FLAG) {
                     *     :@see First
                     * }
                     */
                """,
            flags = mapOf("Flags.TEST_FLAG" to true),
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     * @see resolved.First First
                     */
                """,
            expectedIssues =
                """
                    3:31: expected ')', found '{' [InvalidJavadoc]
                """,
        )
    }

    /** Verify error reporting and recovery when `@if (<expr>)` is followed by a tag without `{`. */
    @Test
    fun `Test @if error - missing opening brace before next block tag`() {
        checkConditionalDocComment(
            input =
                """
                    /**
                     * Summary.
                     * @if (flag(Flags.TEST_FLAG))
                     * @return result
                     */
                """,
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     * @return result
                     */
                """,
            expectedIssues =
                """
                    4:4: expected '{', found '@' [InvalidJavadoc]
                """,
        )
    }

    /**
     * Verify error reporting when an `@if { ...` branch reaches the end of the comment unclosed.
     */
    @Test
    fun `Test @if error - unclosed branch`() {
        checkConditionalDocComment(
            input =
                """
                    /**
                     * Summary.
                     * @if (flag(Flags.TEST_FLAG)) {
                     *     :@see First
                     */
                """,
            flags = mapOf("Flags.TEST_FLAG" to true),
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     * @see resolved.First First
                     */
                """,
            expectedIssues =
                """
                    5:2: expected '}' to close '@if' branch [InvalidJavadoc]
                """,
        )
    }

    /** Verify error reporting and recovery when plain non-tag text appears directly in `@if`. */
    @Test
    fun `Test @if error - plain text inside branch`() {
        checkConditionalDocComment(
            input =
                """
                    /**
                     * Summary.
                     * @if (flag(Flags.TEST_FLAG)) {
                     *     plain text
                     *     :@see First
                     * }
                     */
                """,
            flags = mapOf("Flags.TEST_FLAG" to true),
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     * @see resolved.First First
                     */
                """,
            expectedIssues =
                """
                    4:8: unexpected 'plain text' in '@if' block, expected block tag prefixed with ':' or '}' [InvalidJavadoc]
                """,
        )
    }
}
