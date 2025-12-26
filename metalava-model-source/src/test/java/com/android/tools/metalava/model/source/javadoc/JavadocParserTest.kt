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
import com.android.tools.metalava.model.source.doc.DocComment
import org.junit.Test

class JavadocParserTest : BaseDocCommentTest() {
    /** Check that [text] is parsed correctly by [JavadocParser]. */
    private fun checkParse(
        text: String,
        contentGetter: (DocComment) -> JavadocContent? = { docComment -> docComment.description },
        expectedStructure: String,
        expectedJavadocIssues: String = "",
    ) {
        val docComment = createTestDocComment(text)

        // Parse the main description
        var content = contentGetter(docComment)

        // Make sure that no unexpected JavadocParser issues were found.
        reporter.assertJavadocParserIssues(expectedJavadocIssues)

        // Check the model structure.
        content.assertStructure(expectedStructure.trimIndent())
    }

    @Test
    fun `Test simple comment`() {
        checkParse(
            "/** Simple text */",
            expectedStructure = "text: 'Simple text'",
        )
    }

    @Test
    fun `Test simple comment - leading newline`() {
        checkParse(
            "\n/** Simple text */",
            expectedStructure = """text: 'Simple text'""",
        )
    }

    @Test
    fun `Test simple comment - trailing newline`() {
        checkParse(
            "/** Simple text */\n",
            expectedStructure = """text: 'Simple text'""",
        )
    }

    @Test
    fun `Test comment with nested javadoc start`() {
        checkParse(
            "/** /** */\n",
            expectedStructure =
                """
                    text: '/**'
                """,
        )
    }

    @Test
    fun `Test link - standalone`() {
        checkParse(
            """
                /**
                 * {@link Class}
                 */
            """,
            expectedStructure =
                """
                    inlineTag: link LinkTagData(sourceReference=Class, resolvedReference=null)
                """,
        )
    }

    @Test
    fun `Test link - in text`() {
        checkParse(
            """
                /**
                 * Text before link {@link Class} and some text after.
                 */
            """,
            expectedStructure =
                """
                    text: 'Text before link '
                    inlineTag: link LinkTagData(sourceReference=Class, resolvedReference=null)
                    text: ' and some text after.'
                """,
        )
    }

    @Test
    fun `Test link - on new line`() {
        checkParse(
            """
                /**
                 * Text before link
                 * {@link Class}
                 * and some text after.
                 */
            """,
            expectedStructure =
                """
                    text: 'Text before link\n '
                    inlineTag: link LinkTagData(sourceReference=Class, resolvedReference=null)
                    text: '\n and some text after.'
                """,
        )
    }

    @Test
    fun `Test @ inside inline tag`() {
        checkParse(
            """
                /**
                 * {@code @Annotation}
                 */
            """,
            expectedStructure =
                """
                    inlineTag: code
                      text: '@Annotation'
                """,
        )
    }

    @Test
    fun `Test nested inline tags`() {
        checkParse(
            """
                /**
                 * {@code some {@code nested} inline tags}
                 */
            """,
            expectedStructure =
                """
                    inlineTag: code
                      text: 'some {@code nested} inline tags'
                """,
        )
    }

    @Test
    fun `Test inline tag nested within code tag`() {
        // Make sure that the BAR_TAG_TYPE is registered.
        TestTagTypes.BAR_TAG_TYPE
        checkParse(
            """
                /**
                 * {@code cannot contain inline {@bar tag}}.
                 */
            """,
            expectedStructure =
                """
                    inlineTag: code
                      text: 'cannot contain inline {@bar tag}'
                    text: '.'
                """,
        )
    }

    @Test
    fun `Test inline tag nested within literal tag`() {
        // Make sure that the BAR_TAG_TYPE is registered.
        TestTagTypes.BAR_TAG_TYPE
        checkParse(
            """
                /**
                 * {@literal cannot contain inline {@bar tag}}.
                 */
            """,
            expectedStructure =
                """
                    inlineTag: literal
                      text: 'cannot contain inline {@bar tag}'
                    text: '.'
                """,
        )
    }

    @Test
    fun `Test inline tag nested within link tag`() {
        // Make sure that the BAR_TAG_TYPE is registered.
        TestTagTypes.BAR_TAG_TYPE
        checkParse(
            """
                /**
                 * {@link String cannot contain inline {@bar
                 * tag}}.
                 */
            """,
            expectedStructure =
                """
                    inlineTag: link LinkTagData(sourceReference=String, resolvedReference=null)
                      text: 'cannot contain inline {@bar\n tag}'
                    text: '.'
                """,
        )
    }

    @Test
    fun `Test inline tag nested within linkplain tag`() {
        // Make sure that the BAR_TAG_TYPE is registered.
        TestTagTypes.BAR_TAG_TYPE
        checkParse(
            """
                /**
                 * {@linkplain String cannot contain inline {@bar tag}}.
                 */
            """,
            expectedStructure =
                """
                    inlineTag: linkplain LinkTagData(sourceReference=String, resolvedReference=null)
                      text: 'cannot contain inline {@bar tag}'
                    text: '.'
                """,
        )
    }

    @Test
    fun `Test unclosed inline tags in main description`() {
        checkParse(
            // This purposely indents the second and third lines so they no longer align with the
            // first so that there is some extra indentation on the last line with the */ token to
            // test the handling of that newline.
            """
                /**
                   * {@code unclosed
                    */
            """,
            expectedStructure =
                """
                    inlineTag: code
                      text: 'unclosed'
                """,
            expectedJavadocIssues =
                """
                    2:6: unclosed inline '@code' tag [UnclosedInlineTag]
                """,
        )
    }

    @Test
    fun `Test space between @ and inline tag name`() {
        checkParse(
            """
                /**
                 * {@ code extra space}
                 */
            """,
            expectedStructure =
                """
                    inlineTag: code
                      text: 'extra space'
                """,
        )
    }

    @Test
    fun `Test empty inline tag`() {
        checkParse(
            """
                /** {@inheritDoc} */
            """,
            expectedStructure =
                """
                    inlineTag: inheritDoc
                """,
        )
    }

    @Test
    fun `Test trailing whitespace`() {
        checkParse(
            """
                /**
                 * Some text with trailing whitespaceXX
                 * on multiple linesXX
                 */
            """
                // Replace capital X with a space. This is needed to avoid adding literal trailing
                // whitespace in the string as it will cause issues when checking this code.
                .replace('X', ' '),
            expectedStructure =
                """
                    text: 'Some text with trailing whitespace\n on multiple lines'
                """,
        )
    }

    @Test
    fun `Test invalid Javadoc comment with end comment token in main description`() {
        checkParse(
            """
                /**
                 * Some text with */ inside
                 */
            """,
            expectedStructure =
                // Error recovery ignores the */ and everything after it.
                """
                    text: 'Some text with'
                """,
            expectedJavadocIssues =
                """
                    2:19: extraneous input '*/' expecting {<EOF>, NEWLINE} [InvalidJavadoc]
                """,
        )
    }

    @Test
    fun `Test invalid Javadoc comment with end comment token in block tag description`() {
        checkParse(
            """
                /**
                 * Some text
                 * @param p A block tag with */ inside
                 */
            """,
            contentGetter = { docComment -> docComment.blockTagSections.single().description },
            expectedStructure =
                // Error recovery ignores the */ and everything after it.
                """
                    text: 'A block tag with'
                """,
            expectedJavadocIssues =
                """
                    3:30: mismatched input '*/' expecting {<EOF>, NEWLINE}
                      Expected:
                        EOF
                        NEWLINE
                      Found:
                        COMMENT_END "*/"
                     [InvalidJavadoc]
                """,
        )
    }

    @Test
    fun `Test an inline tag split across multiple lines`() {
        checkParse(
            """
                /**
                 * Summary.
                 * <pre>{@code
                 * someSampleCode()
                 * }</pre>
                 */
            """,
            expectedStructure =
                """
                    text: 'Summary.\n <pre>'
                    inlineTag: code
                      text: '\n someSampleCode()\n '
                    text: '</pre>'
                """,
        )
    }

    @Test
    fun `Test multiple blank lines`() {
        checkParse(
            """
                /**
                 * Summary line.
                 *
                 * <pre>
                 * Text before multiple blank lines.
                 *
                 *
                 * Text after multiple blank lines.
                 * </pre>
                 */
            """,
            expectedStructure =
                """
                    text: 'Summary line.\n\n <pre>\n Text before multiple blank lines.\n\n\n Text after multiple blank lines.\n </pre>'
                """,
        )
    }

    @Test
    fun `Test inline tag data`() {
        // Make sure that the BAR_TAG_TYPE is registered.
        TestTagTypes.BAR_TAG_TYPE
        checkParse(
            """
                /**
                 * outside before {@bar inline inside} outside after
                 */
            """,
            expectedStructure =
                """
                    text: 'outside before '
                    inlineTag: bar BarTagData(identifier=inline)
                      text: 'inside'
                    text: ' outside after'
                """,
            expectedJavadocIssues =
                """
                    2:25: @bar tag cannot contain 'e' or 'o' in the identifier [InvalidJavadoc]
                """,
        )
    }

    @Test
    fun `Test inline tag split across lines - nested content starts with text`() {
        checkParse(
            """
                /**
                 * {@code some
                 * text}
                 */
            """,
            expectedStructure =
                """
                    inlineTag: code
                      text: 'some\n text'
                """,
        )
    }

    @Test
    fun `Test inline tag split across lines - nested content starts with whitespace`() {
        checkParse(
            """
                /**
                 * {@code
                 * some text}
                 * {@code
                 * some text}
                 */
            """,
            expectedStructure =
                """
                    inlineTag: code
                      text: '\n some text'
                    text: '\n '
                    inlineTag: code
                      text: '\n some text'
                """,
        )
    }

    @Test
    fun `Test inline tag split across lines - tag with data`() {
        // Make sure that the BAR_TAG_TYPE is registered.
        TestTagTypes.BAR_TAG_TYPE
        checkParse(
            """
                /**
                 * {@bar some
                 * text}
                    * {@bar
                 * some text}
                 */
            """,
            expectedStructure =
                """
                    inlineTag: bar BarTagData(identifier=some)
                      text: 'text'
                    text: '\n '
                    inlineTag: bar BarTagData(identifier=some)
                      text: 'text'
                """,
            expectedJavadocIssues =
                """
                    2:10: @bar tag cannot contain 'e' or 'o' in the identifier [InvalidJavadoc]
                    4:12: @bar tag cannot contain 'e' or 'o' in the identifier [InvalidJavadoc]
                """,
        )
    }
}
