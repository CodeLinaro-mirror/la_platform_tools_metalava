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

package com.android.tools.metalava.model.source.doc

import com.android.tools.metalava.reporter.Issues
import kotlin.test.assertEquals
import org.junit.Test

class DocCommentParserTest {
    /** Create a [DocComment] from [input], compare it against the [expectedString] */
    private fun checkDocComment(
        input: String,
        expectedString: String,
        expectedIssues: String = "",
    ) {
        val reporter = CollatingDocumentationIssueReporter()
        var docComment = DocCommentParser.parseText(input.trimIndent(), reporter)
        assertEquals(expectedString.trimIndent(), docComment.toString())
        assertEquals(expectedIssues.trimIndent(), reporter.toString().trim())
    }

    @Test
    fun `Test empty comment`() {
        checkDocComment(
            input = "",
            expectedString = "description: <<>>",
        )
    }

    @Test
    fun `Test description`() {
        checkDocComment(
            input = "Description",
            expectedString = "description: <<Description>>",
        )
    }

    @Test
    fun `Test description with nested braces`() {
        checkDocComment(
            input = "Description {@code something}",
            expectedString = "description: <<Description {@code something}>>",
        )
    }

    @Test
    fun `Test no description block tag`() {
        checkDocComment(
            input = "@see something",
            expectedString =
                """
                    description: <<>>
                    @see <<something>>
                """,
        )
    }

    @Test
    fun `Test description and block tags without javadoc comment`() {
        checkDocComment(
            input =
                """
                    Some text
                    @see something
                    @see other thing
                """,
            expectedString =
                """
                    description: <<Some text>>
                    @see <<something>>
                    @see <<other thing>>
                """,
        )
    }

    @Test
    fun `Test description and block tags in javadoc comment`() {
        checkDocComment(
            input =
                """
                    /**
                     * Some text
                     * @see something
                     * @see other thing
                     */
                """,
            expectedString =
                """
                    description: <<\n * Some text>>
                    @see <<something>>
                    @see <<other thing>>
                """,
        )
    }

    @Test
    fun `Test block tag in single line javadoc comment`() {
        checkDocComment(
            input =
                """
                    /** @hide */
                """,
            expectedString =
                """
                    description: <<>>
                    @hide <<>>
                """,
        )
    }

    @Test
    fun `Test multiple block tags in javadoc comment`() {
        checkDocComment(
            input =
                """
                    /**
                     * @hide
                     * @deprecated
                     */
                """,
            expectedString =
                """
                    description: <<>>
                    @hide <<>>
                    @deprecated <<>>
                """,
        )
    }

    @Test
    fun `Test a block @hide tag and some text`() {
        checkDocComment(
            input =
                """
                    /**
                     * A block @hide tag.
                     *
                     * @hide
                     */
                """,
            expectedString =
                """
                    description: <<\n * A block @hide tag.\n *>>
                    @hide <<>>
                """,
        )
    }

    @Test
    fun `Test an unbalanced open brace`() {
        checkDocComment(
            input =
                """
                    /**
                     * An unbalanced open {
                     *
                     * @hide
                     */
                """,
            expectedString =
                """
                    description: <<\n * An unbalanced open {\n *>>
                    @hide <<>>
                """,
        )
    }

    @Test
    fun `Test a @hide at the end of the text`() {
        checkDocComment(
            input =
                """
                    /**
                     * An invalid block tag at the end of the text. @hide
                     */
                """,
            expectedString =
                """
                    description: <<\n * An invalid block tag at the end of the text. @hide>>
                    @hide <<>>
                """,
            expectedIssues =
                """
                    line 2: Invalid @hide syntax, must be a block tag [InvalidJavadoc]
                """,
        )
    }

    @Test
    fun `Test a @hide at the end of a block tag`() {
        checkDocComment(
            input =
                """
                    /**
                     * An invalid block tag at the end of the text.
                     * @deprecated for some reason. @hide
                     */
                """,
            expectedString =
                """
                    description: <<\n * An invalid block tag at the end of the text.>>
                    @deprecated <<for some reason. @hide>>
                    @hide <<>>
                """,
            expectedIssues =
                """
                    line 3: Invalid @hide syntax, must be a block tag [InvalidJavadoc]
                """,
        )
    }

    @Test
    fun `Test a {@hide} at the end of the text`() {
        checkDocComment(
            input =
                """
                    /**
                     * An inline tag at the end of some text {@hide reason why hidden}
                     */
                """,
            expectedString =
                """
                    description: <<\n * An inline tag at the end of some text {@hide reason why hidden}>>
                    @hide <<>>
                """,
            expectedIssues =
                """
                    line 2: Invalid @hide syntax, must be a block tag [InvalidJavadoc]
                """,
        )
    }

    @Test
    fun `Test an inline {@hide} tag used like a block tag`() {
        checkDocComment(
            input =
                """
                    /**
                     * An inline tag.
                     * @see Something
                     * {@hide}
                     */
                """,
            expectedString =
                """
                    description: <<\n * An inline tag.>>
                    @see <<Something\n * {@hide}>>
                    @hide <<>>
                """,
            expectedIssues =
                """
                    line 4: Invalid @hide syntax, must be a block tag [InvalidJavadoc]
                """,
        )
    }
}

/**
 * A [DocumentationIssueReporter] that collates any issues reported and returns them from
 * [toString].
 */
class CollatingDocumentationIssueReporter : DocumentationIssueReporter {
    private val builder = StringBuilder()

    override fun report(issue: Issues.Issue, message: String, lineOffset: Int) {
        builder.append("line ${lineOffset + 1}: $message [${issue.name}]\n")
    }

    override fun toString() = builder.toString()
}
