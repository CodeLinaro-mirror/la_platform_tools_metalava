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

import com.android.tools.metalava.model.source.javadoc.ExprContext
import com.android.tools.metalava.reporter.Issues.Issue
import com.android.tools.metalava.reporter.LocationSpecificReporter
import kotlin.test.assertEquals

abstract class BaseDocCommentTest {
    val reporter = CollatingDocumentationIssueReporter()
    val context = TestDocCommentContext()

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
        reporter.assertJavadocParserIssues(expectedIssues)
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
    private val list = mutableListOf<Report>()

    private data class Report(
        val line: Int,
        val charPosition: Int,
        val issue: Issue,
        val message: String,
    )

    override fun report(issue: Issue, message: String, lineOffset: Int, charOffset: Int) {
        list.add(Report(lineOffset + 1, charOffset + 1, issue, message))
    }

    override fun toString(): String {
        list.sortWith(reportComparator)
        return list.joinToString("\n") { report ->
            "${report.line}:${report.charPosition}: ${report.message} [${report.issue.name}]"
        }
    }

    /** Verify that the reported issues matches [expectedIssues]. */
    fun assertJavadocParserIssues(expectedIssues: String) {
        assertEquals(expectedIssues.trimIndent(), toString(), message = "javadoc parser issues")
    }

    companion object {
        private val reportComparator =
            compareBy<Report>(
                { it.line },
                { it.charPosition },
                { it.issue?.name },
                { it.message },
            )
    }
}

/** A test [DocCommentContext] that provides basic implementations. */
class TestDocCommentContext : DocCommentContext, DocCommentMutationListener {
    override val mutationListener: DocCommentMutationListener
        get() = this

    override fun docCommentMutated() {}

    /** A map from flage name to enabled status. */
    var flags: Map<String, Boolean> = emptyMap()

    /** Implements [ExprContext.isFlagEnabled]. */
    override fun isFlagEnabled(flagFieldReference: String) = flags[flagFieldReference] ?: false

    override fun ordinalInParamsList(name: String) = 0

    override fun isOverridingMethod() = false

    override fun fullyQualifyComment(comment: String) = comment

    override fun resolveThrowableType(reporter: LocationSpecificReporter, typeName: String) =
        ClassReference(typeName)

    var referenceResolver: (String) -> ResolvedReference? = { null }

    override fun resolveReference(sourceReference: String) = referenceResolver(sourceReference)
}
