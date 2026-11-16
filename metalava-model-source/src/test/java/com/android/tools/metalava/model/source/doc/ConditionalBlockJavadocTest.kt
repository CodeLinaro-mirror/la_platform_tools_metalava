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
        // TODO(b/586585311): `@if` should be evaluated as a conditional block tag, emitting
        //  `@see resolved.First First` instead of an unknown `@if` custom block tag.
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
                    @if <<(flag(Flags.TEST_FLAG)) {\n *     :@see First\n * }>>
                """,
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     *
                     * @if (flag(Flags.TEST_FLAG)) {
                     *     :@see First
                     * }
                     */
                """,
        )
    }

    /** Verify that an `@if` block tag without `else` omits its tags when the flag is disabled. */
    @Test
    fun `Test @if without else - false`() {
        // TODO(b/586585311): `@if` should omit the inactive branch completely.
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
                    @if <<(flag(Flags.TEST_FLAG)) {\n *     :@see First\n * }>>
                """,
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     *
                     * @if (flag(Flags.TEST_FLAG)) {
                     *     :@see First
                     * }
                     */
                """,
        )
    }

    /**
     * Verify that when an `@if ... else` condition evaluates to `true`, the true branch's `:@see`
     * and `:@deprecated` tags are emitted and `hasBlockTagOfType("deprecated")` is `true`.
     */
    @Test
    fun `Test @if with else - true`() {
        // TODO(b/586585311): `@if` should emit `@see` and `@deprecated` from the active branch and
        //  set `hasBlockTagOfType("deprecated")` to `true`.
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
                     * @if (flag(Flags.TEST_FLAG)) {
                     *     :@see First
                     *     :@deprecated Use {@link resolved.First First} instead.
                     * } else {
                     *     :@see Second
                     * }
                     */
                """,
        ) { docComment ->
            assertFalse(docComment.hasBlockTagOfType("deprecated"))
        }
    }

    /**
     * Verify that when an `@if ... else` condition evaluates to `false`, the `else` branch tags are
     * emitted and an inactive `:@deprecated` tag does not appear on the [DocComment].
     */
    @Test
    fun `Test @if with else - false`() {
        // TODO(b/586585311): `@if` should emit only `@see resolved.Second Second` from the `else`
        //  branch.
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
                     * @if (flag(Flags.TEST_FLAG)) {
                     *     :@see First
                     *     :@deprecated Use {@link resolved.First First} instead.
                     * } else {
                     *     :@see Second
                     * }
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

        // TODO(b/586585311): Nested `:@if` block tags should evaluate outer and inner conditions.
        val preFixExpected =
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
            expectedPrintOutput = preFixExpected,
        )

        checkConditionalDocComment(
            input = comment,
            flags = mapOf("Flags.OUTER_FLAG" to true, "Flags.INNER_FLAG" to false),
            expectedPrintOutput = preFixExpected,
        )

        checkConditionalDocComment(
            input = comment,
            flags = mapOf("Flags.OUTER_FLAG" to false, "Flags.INNER_FLAG" to true),
            expectedPrintOutput = preFixExpected,
        )
    }

    /** Verify error reporting and recovery when `@if` is missing its `(<expr>)` condition. */
    @Test
    fun `Test @if error - missing expr`() {
        // TODO(b/586585311): Report `InvalidIfTag` and `InvalidJavadoc` when `<expr>` is missing.
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
                     *
                     * @return result
                     * @if {
                     *     :@see First
                     * }
                     */
                """,
        )
    }

    /** Verify error reporting and recovery when `@if (<expr>` is missing its closing `)`. */
    @Test
    fun `Test @if error - unbalanced parenthesis`() {
        // TODO(b/586585311): Report `InvalidJavadoc` for unbalanced `(` in `@if` condition.
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
                     *
                     * @if (flag(Flags.TEST_FLAG) {
                     *     :@see First
                     * }
                     */
                """,
        )
    }

    /** Verify error reporting and recovery when `@if (<expr>)` is followed by a tag without `{`. */
    @Test
    fun `Test @if error - missing opening brace before next block tag`() {
        // TODO(b/586585311): Report `InvalidJavadoc` when `{` is missing after `@if (<expr>)`.
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
                     *
                     * @return result
                     * @if (flag(Flags.TEST_FLAG))
                     */
                """,
        )
    }

    /**
     * Verify error reporting when an `@if { ...` branch reaches the end of the comment unclosed.
     */
    @Test
    fun `Test @if error - unclosed branch`() {
        // TODO(b/586585311): Report `InvalidJavadoc` when `}` is missing at end of `@if` branch.
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
                     *
                     * @if (flag(Flags.TEST_FLAG)) {
                     *     :@see First
                     */
                """,
        )
    }

    /** Verify error reporting and recovery when plain non-tag text appears directly in `@if`. */
    @Test
    fun `Test @if error - plain text inside branch`() {
        // TODO(b/586585311): Report `InvalidJavadoc` for non-tag plain text inside `@if { ... }`.
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
                     *
                     * @if (flag(Flags.TEST_FLAG)) {
                     *     plain text
                     *     :@see First
                     * }
                     */
                """,
        )
    }
}
