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

abstract class BaseDocCommentTest {
    val reporter = CollatingDocumentationIssueReporter()
    val context = NoOpDocCommentContext()

    /**
     * Create a [DocComment] from [input] for testing, verifying that [expectedIssues] were found.
     */
    internal fun createTestDocComment(
        input: String,
        expectedIssues: String = "",
    ): DocComment {
        var docComment =
            DocCommentParser.parseText(
                context,
                input.trimIndent(),
                reporter,
            )
        assertEquals(expectedIssues.trimIndent(), reporter.toString().trim())
        return docComment
    }

    /**
     * Check the result of calling [DocComment.printAsJavadocComment] on [docComment] matches the
     * [expectedPrintOutput].
     */
    internal fun checkPrintOutput(
        docComment: DocComment,
        expectedPrintOutput: String,
        message: String? = null,
    ) {
        var actualPrintOutput = docComment.asJavadocCommentString().trim()
        assertEquals(expectedPrintOutput.trimIndent(), actualPrintOutput, message)
    }
}

/**
 * A [DocumentationIssueReporter] that collates any issues reported and returns them from
 * [toString].
 */
class CollatingDocumentationIssueReporter : DocumentationIssueReporter {
    private val builder = StringBuilder()

    override fun report(issue: Issues.Issue, message: String, lineOffset: Int, charOffset: Int) {
        builder.append("${lineOffset + 1}:${charOffset + 1}: $message [${issue.name}]\n")
    }

    override fun toString() = builder.toString()
}

/** A test [DocCommentContext] that provides basic no-op implementations. */
class NoOpDocCommentContext : DocCommentContext, DocCommentMutationListener {
    override val mutationListener: DocCommentMutationListener
        get() = this

    override fun docCommentMutated() {}

    override fun ordinalInParamsList(name: String) = 0

    override fun isOverridingMethod() = false

    override fun fullyQualifyComment(comment: String) = comment

    override fun resolveThrowableType(typeName: String) = ClassReference(typeName)
}
