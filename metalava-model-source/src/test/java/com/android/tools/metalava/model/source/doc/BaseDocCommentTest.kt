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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ReferencableItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.scope.NameClassification
import com.android.tools.metalava.model.source.javadoc.ExprContext
import com.android.tools.metalava.model.source.javadoc.TestTagTypes
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.reporter.Issues.Issue
import kotlin.test.assertEquals
import org.junit.Before
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

abstract class BaseDocCommentTest {
    internal val reporter = CollatingDocumentationIssueReporter()
    internal val context = TestDocCommentContext(reporter)

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

        // Parse all the descriptions
        docComment.description
        docComment.blockTagSections.forEach { it.description }

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

    @Before
    fun initializeTestTagTypes() {
        // Make sure that the test tag types are registered.
        TestTagTypes
    }
}

/**
 * A [DocumentationIssueReporter] that collates any issues reported and returns them from
 * [toString].
 */
internal class CollatingDocumentationIssueReporter : DocumentationIssueReporter {
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
internal class TestDocCommentContext(reporter: DocumentationIssueReporter) : DocCommentContext {

    /** A map from flage name to enabled status. */
    var flags: Map<String, Boolean> = emptyMap()

    /** Qualify [sourceReference], if needed. */
    private fun qualifySourceReference(sourceReference: String): String =
        if (sourceReference.contains(".") || sourceReference.startsWith("#")) sourceReference
        else "resolved.$sourceReference"

    override fun resolveItemReference(
        sourceReference: String,
        nameClassification: NameClassification
    ): ReferencableItem =
        when (nameClassification) {
            NameClassification.FIELD -> {
                mock<FieldItem>(stubOnly = true) {
                    on { constantValue } doReturn Value.createLiteralValue(null, sourceReference)
                }
            }
            NameClassification.TYPE,
            NameClassification.AMBIGUOUS -> {
                val qualifiedName = qualifySourceReference(sourceReference)
                mock<ClassItem>(stubOnly = true) { on { qualifiedName() } doReturn qualifiedName }
            }
            else ->
                error(
                    "referencableItemResolver did not return an item for ${nameClassification.describeName(sourceReference)}"
                )
        }

    /** Implements [ExprContext.isFlagEnabled]. */
    override fun isFlagEnabled(flagName: String) = flags[flagName] ?: false

    override fun ordinalInParamsList(name: String) = 0

    override fun isOverridingMethod() = false

    override val containingClassItem: ClassItem?
        get() = null

    override val docTypeParser: DocTypeParser =
        DocTypeParser.create(reporter, TypeParameterScope.empty)
}
