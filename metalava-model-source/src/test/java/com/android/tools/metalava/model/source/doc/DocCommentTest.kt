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

import com.android.tools.metalava.model.source.javadoc.toOptionalJavadocContent
import kotlin.test.assertEquals
import org.junit.Test

class DocCommentTest : BaseDocCommentTest() {
    /** Add a block tag section for [tagTypeName] containing [text]. */
    private fun DocComment.addBlockTagSection(tagTypeName: String, text: String) {
        addBlockTagSection(tagTypeName, text.toOptionalJavadocContent())
    }

    @Test
    fun `Test removeBlockTagSections`() {
        val docComment =
            createTestDocComment(
                """
                    /**
                     * Some text.
                     * @singleLine single line tag.
                     * @multipleLine a block tag
                     * that is spread across multiple lines.
                     * @singleLine another single line tag.
                     */
                """
            )

        checkPrintOutput(
            docComment,
            expectedPrintOutput =
                """
                    /**
                     * Some text.
                     *
                     * @multipleLine a block tag
                     * that is spread across multiple lines.
                     * @singleLine single line tag.
                     * @singleLine another single line tag.
                     */
                """,
            message = "before mutations"
        )

        val countBeforeRemoval = docComment.blockTagSections.size
        assertEquals(3, countBeforeRemoval, message = "count before removal")

        // Try and remove a block tag that is not present. Verify that it does not change the size
        // of block tags.
        docComment.removeBlockTagSections { it.tagType.name == "unknown" }
        assertEquals(
            countBeforeRemoval,
            docComment.blockTagSections.size,
            message = "remove unknown"
        )

        // Remove all singleLine block tag sections. Verify that it removes 2 block tags.
        docComment.removeBlockTagSections { it.tagType.name == "singleLine" }
        assertEquals(
            countBeforeRemoval - 2,
            docComment.blockTagSections.size,
            message = "remove singleLine"
        )

        // Make sure that the removal is reflected in the printed output.
        checkPrintOutput(
            docComment,
            expectedPrintOutput =
                """
                    /**
                     * Some text.
                     *
                     * @multipleLine a block tag
                     * that is spread across multiple lines.
                     */
                """,
            message = "after remove @singleLine block tag sections"
        )

        // Remove all block tag sections. Verify that there are none left.
        docComment.removeBlockTagSections { true }
        assertEquals(0, docComment.blockTagSections.size, message = "remove all")

        // Make sure that the removal is reflected in the printed output.
        checkPrintOutput(
            docComment,
            expectedPrintOutput =
                """
                    /** Some text. */
                """,
            message = "after remove all block tag sections"
        )
    }

    @Test
    fun `Test addBlockTagSection`() {
        val docComment =
            createTestDocComment(
                """
                    /**
                     * Some text.
                     */
                """
            )

        docComment.addBlockTagSection("custom", "a custom block tag")

        checkPrintOutput(
            docComment,
            expectedPrintOutput =
                """
                    /**
                     * Some text.
                     * @custom a custom block tag
                     */
                """,
            message = "after adding custom tag"
        )

        docComment.addBlockTagSection("custom", "another custom block tag")

        checkPrintOutput(
            docComment,
            expectedPrintOutput =
                """
                    /**
                     * Some text.
                     *
                     * @custom a custom block tag
                     * @custom another custom block tag
                     */
                """,
            message = "after adding another custom tag"
        )
    }
}
