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

import com.android.tools.metalava.model.source.javadoc.BarTagData
import com.android.tools.metalava.model.source.javadoc.JavadocText
import com.android.tools.metalava.model.source.javadoc.TextContainsAnyVisitor
import com.android.tools.metalava.model.source.javadoc.dumpContentStructure
import junit.framework.TestCase.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class DocCommentParserTest : BaseDocCommentTest() {
    /** Context object used for the optional lambda taken by [checkDocComment]. */
    private data class DocCommentContext(val docComment: DocComment)

    /** Create a [DocComment] from [input], compare it against the [expectedString] */
    private fun checkDocComment(
        input: String,
        referenceResolver: ((String) -> ResolvedReference?)? = null,
        expectedString: String? = null,
        expectedPrintOutput: String? = null,
        expectedIssues: String = "",
        checker: DocCommentContext.() -> Unit = {},
    ) {
        if (referenceResolver != null) context.referenceResolver = referenceResolver
        val docComment = createTestDocComment(input, expectedIssues)
        if (expectedString != null) {
            assertEquals(expectedString.trimIndent(), docComment.toString())
        }

        if (expectedPrintOutput != null) {
            checkPrintOutput(docComment, expectedPrintOutput)
        }

        DocCommentContext(docComment).checker()
    }

    /** Dump the internal structure of this [DocComment]. */
    private fun DocComment.dumpStructure(): String = buildString {
        description?.let { append(it.dumpContentStructure()) }
        for (section in blockTagSections) {
            append("blockTag: ")
            append(section.tagType)
            section.tagData?.let { tagData ->
                append(" ")
                append(tagData)
            }
            append("\n")
            section.description?.let { append(it.dumpContentStructure().prependIndent("  ")) }
        }
    }

    /** Check the model structure of this [DocComment]. */
    internal fun DocComment.assertStructure(expected: String, message: String? = null) {
        // Generate a string representation of the model structure.
        val actualStructure = dumpStructure()
        assertEquals(expected.trimIndent(), actualStructure.trimEnd(), message)
    }

    @Test
    fun `Test non-existent comment`() {
        checkDocComment(
            input = "",
            expectedString = "description: <<>>",
            expectedPrintOutput = "",
        )
    }

    @Test
    fun `Test empty comment`() {
        checkDocComment(
            input = "/***/",
            expectedString = "description: <<>>",
            expectedPrintOutput =
                """
                    /** */
                """,
        )
    }

    @Test
    fun `Test description`() {
        checkDocComment(
            input = "Description",
            expectedString = "description: <<Description>>",
            expectedPrintOutput =
                """
                    /** Description */
                """,
        )
    }

    @Test
    fun `Test description with nested braces`() {
        checkDocComment(
            input = "Description {@code something}",
            expectedString = "description: <<Description {@code something}>>",
            expectedPrintOutput =
                """
                    /** Description {@code something} */
                """,
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
            expectedPrintOutput =
                """
                    /** @see something */
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
            expectedPrintOutput =
                """
                    /**
                     * Some text
                     *
                     * @see something
                     * @see other thing
                     */
                """,
        ) {
            docComment.assertStructure(
                """
                    text: 'Some text'
                    blockTag: see LabeledRefTagData(sourceReference=something, resolvedReference=null)
                    blockTag: see LabeledRefTagData(sourceReference=other, resolvedReference=null)
                      text: 'thing'
                """
            )
        }
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
            expectedPrintOutput =
                """
                    /**
                     * Some text
                     *
                     * @see something
                     * @see other thing
                     */
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
            expectedPrintOutput =
                """
                    /** @hide */
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
            expectedPrintOutput =
                """
                    /**
                     * @deprecated
                     * @hide
                     */
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
            expectedPrintOutput =
                """
                    /**
                     * A block @hide tag.
                     * @hide
                     */
                """,
        )
    }

    @Test
    fun `Test @throws block tag`() {
        checkDocComment(
            input =
                """
                    /**
                     * @throws SomeException reason
                     */
                """,
            expectedString =
                """
                    description: <<>>
                    @throws <<SomeException reason>>
                """,
            expectedPrintOutput = "/** @throws SomeException reason */",
        ) {
            docComment.assertStructure(
                """
                    blockTag: throws ThrowsTagData(throwableType=ClassReference(qualifiedName=SomeException))
                      text: 'reason'
                """
            )
        }
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
            expectedPrintOutput =
                """
                    /**
                     * An unbalanced open {
                     * @hide
                     */
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
                """,
            expectedPrintOutput =
                """
                    /** An invalid block tag at the end of the text. @hide */
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
                """,
            expectedPrintOutput =
                """
                    /**
                     * An invalid block tag at the end of the text.
                     * @deprecated for some reason. @hide
                     */
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
                """,
            expectedPrintOutput =
                """
                    /** An inline tag at the end of some text {@hide reason why hidden} */
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
                """,
            expectedPrintOutput =
                """
                    /**
                     * An inline tag.
                     * @see Something {@hide}
                     */
                """,
        )
    }

    @Test
    fun `Test ordering of block tags`() {
        checkDocComment(
            input =
                """
                    /**
                     * @serial some reason
                     * @hide
                     * @throws
                     * @version current
                     * @author me
                     * @throws Throwable
                     * @unknown
                     * @param
                     * @serialData some other reason
                     * @inheritDoc
                     * @sdkExtSince 7
                     * @param p2
                     * @serialField field name and type and explanation
                     * @since 1.4
                     * @return something
                     * @deprecated
                     * @attr ref xml-thing
                     * @mysterious
                     * @author them
                     * @param p1
                     * @apiSince 12
                     * @exception Exception
                     * @see #field
                     * @see #Class()
                     */
                """,
            expectedString =
                """
                    description: <<>>
                    @serial <<some reason>>
                    @hide <<>>
                    @throws <<>>
                    @version <<current>>
                    @author <<me>>
                    @throws <<Throwable>>
                    @unknown <<>>
                    @param <<>>
                    @serialData <<some other reason>>
                    @inheritDoc <<>>
                    @sdkExtSince <<7>>
                    @param <<p2>>
                    @serialField <<field name and type and explanation>>
                    @since <<1.4>>
                    @return <<something>>
                    @deprecated <<>>
                    @attr <<ref xml-thing>>
                    @mysterious <<>>
                    @author <<them>>
                    @param <<p1>>
                    @apiSince <<12>>
                    @throws <<Exception>>
                    @see <<#field>>
                    @see <<#Class()>>
                """,
            expectedPrintOutput =
                """
                    /**
                     * @inheritDoc
                     * @author me
                     * @author them
                     * @version current
                     * @param p1
                     * @param p2
                     * @param
                     * @return something
                     * @attr ref xml-thing
                     * @throws Exception
                     * @throws Throwable
                     * @throws
                     * @see #field
                     * @see #Class()
                     * @since 1.4
                     * @serial some reason
                     * @serialData some other reason
                     * @serialField field name and type and explanation
                     * @deprecated
                     * @hide
                     * @apiSince 12
                     * @sdkExtSince 7
                     * @mysterious
                     * @unknown
                     */
                """,
            expectedIssues = "11:5: Cannot use 'inheritDoc' as a block tag [InvalidTagForm]",
        )
    }

    @Test
    fun `Test a comment that has a line that starts with forward slash`() {
        checkDocComment(
            input =
                """
                    /**
                     * Summary.
                     * <pre>
                    // Java line comment
                    someSampleCode()
                     * </pre>
                     */
                """,
            expectedString =
                """
                    description: <<\n * Summary.\n * <pre>\n// Java line comment\nsomeSampleCode()\n * </pre>>>
                """,
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     * <pre>
                    // Java line comment
                     *someSampleCode()
                     * </pre>
                     */
                """,
        )
    }

    @Test
    fun `Test an inline tag split across multiple lines`() {
        checkDocComment(
            input =
                """
                    /**
                     * Summary.
                     * <pre>{@code
                     * someSampleCode()
                     * }</pre>
                     */
                """,
            expectedString =
                """
                    description: <<\n * Summary.\n * <pre>{@code\n * someSampleCode()\n * }</pre>>>
                """,
            expectedPrintOutput =
                """
                    /**
                     * Summary.
                     * <pre>{@code
                     * someSampleCode()
                     * }</pre>
                     */
                """,
        )
    }

    @Test
    fun `Test a block tag split across multiple lines`() {
        checkDocComment(
            input =
                """
                    /**
                     * @see
                     *
                     * "Me"
                     */
                """,
            expectedString =
                """
                    description: <<>>
                    @see <<\n *\n * "Me">>
                """,
            expectedPrintOutput =
                """
                    /** @see "Me" */
                """,
        )
    }

    @Test
    fun `Test multiple blank lines`() {
        checkDocComment(
            input =
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
            expectedString =
                """
                    description: <<\n * Summary line.\n *\n * <pre>\n * Text before multiple blank lines.\n *\n *\n * Text after multiple blank lines.\n * </pre>>>
                """,
            expectedPrintOutput =
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
        )
    }

    @Test
    fun `Test unclosed inline tag`() {
        checkDocComment(
            // This purposely indents the second and third lines so they no longer align with the
            // first so that there is some extra indentation on the last line with the */ token to
            // test the handling of that newline.
            input =
                """
                    /**
                       * {@code unclosed
                        */
                """,
            expectedString =
                """
                    description: <<\n   * {@code unclosed>>
                """,
            expectedPrintOutput =
                """
                    /** {@code unclosed} */
                """,
            expectedIssues = "2:6: unclosed inline '@code' tag [UnclosedInlineTag]",
        )
    }

    @Test
    fun `Test lineOffsetFor with out of bounds index`() {
        val str =
            """
            multi
            line
            string
            """
                .trimIndent()
        val length = str.length
        assertEquals(str.lineOffsetFor(length + 1), 2)
    }

    @Test
    fun `Test block tag data`() {
        checkDocComment(
            input =
                """
                    /**
                     * @bar foo block after
                     */
                """,
            expectedString =
                """
                    description: <<>>
                    @bar <<foo block after>>
                """,
            expectedPrintOutput =
                """
                    /** @bar foo block after */
                """,
            expectedIssues =
                "2:9: @bar tag cannot contain 'e' or 'o' in the identifier [InvalidJavadoc]",
        ) {
            val barBlockTagSection = docComment.blockTagSections.single()
            assertEquals(BarTagData("foo"), barBlockTagSection.tagData)
        }
    }

    @Test
    fun `Test append DocContent to empty`() {
        checkDocComment(
            input =
                """
                    /***/
                """,
            expectedString =
                """
                    description: <<>>
                """,
            expectedPrintOutput =
                """
                    /** */
                """,
        ) {
            val text = JavadocText("appended")
            docComment.append(text)
            checkPrintOutput(docComment, "/** appended */")
        }
    }

    @Test
    fun `Test append DocContent to existing`() {
        checkDocComment(
            input =
                """
                    /** existing */
                """,
            expectedString =
                """
                    description: << existing>>
                """,
            expectedPrintOutput =
                """
                    /** existing */
                """,
        ) {
            val text = JavadocText("appended")
            docComment.append(text)
            checkPrintOutput(
                docComment,
                """
                    /**
                     * existing.
                     * <br>
                     * appended
                     */
                """,
            )
        }
    }

    @Test
    fun `Test append String to empty`() {
        checkDocComment(
            input =
                """
                    /***/
                """,
            expectedString =
                """
                    description: <<>>
                """,
            expectedPrintOutput =
                """
                    /** */
                """,
        ) {
            docComment.append("some {@code text} to append")
            checkPrintOutput(
                docComment,
                """
                    /** some {@code text} to append */
                """,
            )
            docComment.assertStructure(
                """
                    text: 'some '
                    inlineTag: code
                      text: 'text'
                    text: ' to append'
                """
            )
        }
    }

    @Test
    fun `Test append String to existing`() {
        checkDocComment(
            input =
                """
                    /** existing */
                """,
            expectedString =
                """
                    description: << existing>>
                """,
            expectedPrintOutput =
                """
                    /** existing */
                """,
        ) {
            docComment.append("some {@code text} to append")
            checkPrintOutput(
                docComment,
                """
                    /**
                     * existing.
                     * <br>
                     * some {@code text} to append
                     */
                """,
            )
            docComment.assertStructure(
                """
                    text: 'existing'
                    text: '.'
                    text: '\n <br>\n '
                    text: 'some '
                    inlineTag: code
                      text: 'text'
                    text: ' to append'
                """
            )
        }
    }

    /** Checks if "Wally" is in the text. */
    private val wallyPredicate = TextContainsAnyVisitor { it.containsWord("Wally") }

    @Test
    fun `Test check predicate in main description`() {
        checkDocComment(
            input =
                """
                    /**
                     * Wally is here.
                     */
                """,
        ) {
            assertTrue(docComment.check(wallyPredicate))
        }
    }

    @Test
    fun `Test check predicate in block tag description`() {
        checkDocComment(
            input =
                """
                    /**
                     * Not here.
                     * @deprecated Wally is deprecated.
                     */
                """,
        ) {
            assertTrue(docComment.check(wallyPredicate))
        }
    }

    @Test
    fun `Test check predicate fails`() {
        checkDocComment(
            input =
                """
                    /**
                     * Not here.
                     * @deprecated Or here.
                     */
                """,
        ) {
            assertFalse(docComment.check(wallyPredicate))
        }
    }

    @Test
    fun `Test @see HTML a tag`() {
        checkDocComment(
            input =
                """
                    /**
                     * @see <a href="link.html">Label</a>
                     */
                """,
            referenceResolver = { error("should never be called") },
            expectedString =
                """
                    description: <<>>
                    @see <<<a href="link.html">Label</a>>>
                """,
            expectedPrintOutput = """/** @see <a href="link.html">Label</a> */"""
        ) {
            docComment.assertStructure(
                """
                    blockTag: see
                      text: '<a href="link.html">Label</a>'
                """
            )
        }
    }

    @Test
    fun `Test @see literal string`() {
        checkDocComment(
            input =
                """
                    /**
                     * @see "literal string"
                     */
                """,
            referenceResolver = { error("should never be called") },
            expectedString =
                """
                    description: <<>>
                    @see <<"literal string">>
                """,
            expectedPrintOutput = """/** @see "literal string" */""",
        ) {
            docComment.assertStructure(
                """
                    blockTag: see
                      text: '"literal string"'
                """
            )
        }
    }

    @Test
    fun `Test @see reference without label`() {
        checkDocComment(
            input =
                """
                    /**
                     * @see Reference
                     */
                """,
            referenceResolver = { ClassReference("resolved.$it") },
            expectedString =
                """
                    description: <<>>
                    @see <<Reference>>
                """,
            expectedPrintOutput = """/** @see resolved.Reference */""",
        ) {
            docComment.assertStructure(
                """
                    blockTag: see LabeledRefTagData(sourceReference=Reference, resolvedReference=ClassReference(qualifiedName=resolved.Reference))
                """
            )
        }
    }

    @Test
    fun `Test @see reference with label`() {
        checkDocComment(
            input =
                """
                    /**
                     * @see Reference Label
                     */
                """,
            referenceResolver = { ClassReference("resolved.$it") },
            expectedString =
                """
                    description: <<>>
                    @see <<Reference Label>>
                """,
            expectedPrintOutput = """/** @see resolved.Reference Label */""",
        ) {
            docComment.assertStructure(
                """
                    blockTag: see LabeledRefTagData(sourceReference=Reference, resolvedReference=ClassReference(qualifiedName=resolved.Reference))
                      text: 'Label'
                """
            )
        }
    }

    @Test
    fun `Test {@throws} - block tag as inline tag`() {
        checkDocComment(
            input =
                """
                    /**
                     * {@throws Exception}
                     */
                """,
            expectedString = """description: <<\n * {@throws Exception}>>""",
            expectedPrintOutput = """/** {@throws Exception} */""",
            expectedIssues = "2:6: Cannot use 'throws' as an inline tag [InvalidTagForm]",
        ) {
            docComment.assertStructure(
                """
                    inlineTag: throws ThrowsTagData(throwableType=ClassReference(qualifiedName=Exception))
                """
            )
        }
    }

    @Test
    fun `Test @link - inline tag as block tag`() {
        checkDocComment(
            input =
                """
                    /**
                     * @link String
                     */
                """,
            expectedString =
                """
                    description: <<>>
                    @link <<String>>
                """,
            expectedPrintOutput = """/** @link String */""",
            expectedIssues = "2:5: Cannot use 'link' as a block tag [InvalidTagForm]",
        ) {
            docComment.assertStructure(
                """
                    blockTag: link LabeledRefTagData(sourceReference=String, resolvedReference=null)
                """
            )
        }
    }
}
