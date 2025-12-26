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

import com.android.tools.metalava.model.source.doc.BaseDocCommentTest
import kotlin.test.assertEquals
import org.junit.Test

class TagDataExtractorTest : BaseDocCommentTest() {
    private fun checkExtractedData(
        input: String,
        expectedInputStructure: String?,
        expectedJavadocIssues: String = "",
        expectedTagData: BarTagData?,
        expectedRemainderStructure: String? = expectedInputStructure,
    ) {
        val docComment = createTestDocComment("/** $input */")
        val content = docComment.description
        content.assertStructure(expectedInputStructure, message = "input structure")

        var result =
            content?.extractTagDataForTagType(
                context,
                TestTagTypes.BAR_TAG_TYPE,
                reporter,
            )

        reporter.assertJavadocParserIssues(expectedJavadocIssues)

        val tagData = result?.tagData
        assertEquals(expectedTagData, tagData, message = "tagData")

        val remainder = result?.remainder
        assertEquals(
            expectedRemainderStructure?.trimIndent(),
            remainder?.dumpContentStructure()?.trim(),
            message = "remainder structure"
        )
    }

    @Test
    fun `Test extract data from empty text`() {
        checkExtractedData(
            input = "",
            expectedInputStructure = null,
            expectedTagData = null,
        )
    }

    @Test
    fun `Test extract data from invalid inline tag`() {
        checkExtractedData(
            input = "{@code inline tag}",
            expectedInputStructure =
                """
                    inlineTag: code
                      text: 'inline tag'
                """,
            expectedTagData = null,
        )
    }

    @Test
    fun `Test extract data from valid text`() {
        checkExtractedData(
            input = "foo text after",
            expectedInputStructure =
                """
                    text: 'foo text after'
                """,
            expectedJavadocIssues =
                """
                    1:1: @bar tag cannot contain 'e' or 'o' in the identifier [InvalidJavadoc]
                """,
            expectedTagData = BarTagData(identifier = "foo"),
            expectedRemainderStructure =
                """
                    text: 'text after'
                """,
        )
    }

    @Test
    fun `Test extract data from list`() {
        checkExtractedData(
            input = "foo text after {@code inline} more text",
            expectedInputStructure =
                """
                    text: 'foo text after '
                    inlineTag: code
                      text: 'inline'
                    text: ' more text'
                """,
            expectedJavadocIssues =
                """
                    1:1: @bar tag cannot contain 'e' or 'o' in the identifier [InvalidJavadoc]
                """,
            expectedTagData = BarTagData(identifier = "foo"),
            expectedRemainderStructure =
                """
                    text: 'text after '
                    inlineTag: code
                      text: 'inline'
                    text: ' more text'
                """,
        )
    }
}
