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
import com.android.tools.metalava.model.source.doc.TagTypes
import kotlin.test.assertEquals
import org.junit.Test

class JavadocContentTest : BaseDocCommentTest() {
    private fun checkConcatenate(
        vararg inputs: String,
        expectedContent: JavadocContent,
    ) {
        val result = concatJavadocContent {
            for (input in inputs) {
                var content = createTestDocComment(input).description!!
                add(content)
            }
        }
        assertEquals(expectedContent, result)
    }

    @Test
    fun `Test add JavadocContent text plus text`() {
        checkConcatenate(
            "first",
            "second",
            expectedContent =
                JavadocContentList(
                    listOf(
                        JavadocText("first"),
                        JavadocText("second"),
                    ),
                ),
        )
    }

    @Test
    fun `Test add JavadocContent mixture`() {
        val codeTagType = TagTypes.CODE
        val literalTagType = TagTypes.LITERAL
        checkConcatenate(
            "first",
            "second {@code inline}",
            "{@literal before} third",
            expectedContent =
                JavadocContentList(
                    listOf(
                        JavadocText("first"),
                        JavadocText("second "),
                        JavadocInlineTag(codeTagType, null, JavadocText("inline")),
                        JavadocInlineTag(literalTagType, null, JavadocText("before")),
                        JavadocText(" third"),
                    ),
                ),
        )
    }
}
