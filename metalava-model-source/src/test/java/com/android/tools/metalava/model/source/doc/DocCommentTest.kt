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

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class DocCommentTest : BaseDocCommentTest() {
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
                     * @singleLine single line tag.
                     * @multipleLine a block tag
                     * that is spread across multiple lines.
                     * @singleLine another single line tag.
                     */
                """,
            message = "before mutations"
        )

        // Try and remove a block tag that is not present.
        assertFalse(
            docComment.removeBlockTagSections { it.tagType == "unknown" },
            message = "remove unknown"
        )

        // Remove all singleLine block tag sections.
        assertTrue(
            docComment.removeBlockTagSections { it.tagType == "singleLine" },
            message = "remove singleLine"
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
                     */
                """,
            message = "after remove @singleLine block tag sections"
        )

        // Remove all block tag sections.
        assertTrue(docComment.removeBlockTagSections { true }, message = "remove all")

        checkPrintOutput(
            docComment,
            expectedPrintOutput =
                """
                    /** Some text. */
                """,
            message = "after remove all block tag sections"
        )
    }
}
