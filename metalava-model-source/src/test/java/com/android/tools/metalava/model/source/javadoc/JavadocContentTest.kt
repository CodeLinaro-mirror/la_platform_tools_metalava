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

import com.android.tools.metalava.model.api.flags.ApiFlagAction
import com.android.tools.metalava.model.api.flags.FlagState
import com.android.tools.metalava.model.source.doc.BaseDocCommentTest
import com.android.tools.metalava.model.source.doc.TagTypes
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import org.junit.Test

class JavadocContentTest : BaseDocCommentTest() {
    private fun checkConcatenate(
        vararg inputs: String,
        expectedContent: JavadocContent,
    ) {
        val result = concatJavadocContent {
            for (input in inputs) {
                val (docComment, _) = createTestDocCommentAndContext(input)
                val content = docComment.description!!
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

    @Test
    fun `Test JavadocText flagStateForRange and subSequenceFrom`() {
        val keptA = FlagState.additionFlag("Flags.FLAG_A", ApiFlagAction.KEEP)
        val keptB = FlagState.additionFlag("Flags.FLAG_B", ApiFlagAction.KEEP)
        val (docComment, _) =
            createTestDocCommentAndContext(
                input =
                    """
                        /**
                         * unguarded {@if (flag(Flags.FLAG_A)) {Pkg.{@if (flag(Flags.FLAG_B)) {Sub}}}}
                         */
                    """,
                flagActions =
                    mapOf(
                        "Flags.FLAG_A" to ApiFlagAction.KEEP,
                        "Flags.FLAG_B" to ApiFlagAction.KEEP,
                    ),
            )

        val text = assertIs<JavadocText>(docComment.description)
        assertEquals("unguarded Pkg.Sub", text.contents)

        // Any range touching unguarded text returns FlagState.NONE.
        assertSame(FlagState.NONE, text.flagStateForRange(0, 9))
        assertSame(FlagState.NONE, text.flagStateForRange(0, 17))
        assertSame(FlagState.NONE, text.flagStateForRange(9, 17))

        // Single-span and contiguous multi-span intersection queries.
        assertEquals(keptA, text.flagStateForRange(10, 14))
        assertEquals(keptA combineWith keptB, text.flagStateForRange(14, 17))
        assertEquals(keptA, text.flagStateForRange(10, 17))

        // Slicing shifts spans to match the suffix offsets.
        val suffix = assertIs<JavadocText>(text.subSequenceFrom(10))
        assertEquals("Pkg.Sub", suffix.contents)
        assertEquals(keptA, suffix.flagStateForRange(0, 4))
        assertEquals(keptA combineWith keptB, suffix.flagStateForRange(4, 7))
        assertEquals(keptA, suffix.flagStateForRange(0, 7))
    }
}
