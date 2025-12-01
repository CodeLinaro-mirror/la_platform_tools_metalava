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

package com.android.tools.metalava.model.testsuite.documentation

import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.doc.DocContent
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

class CommonItemDocumentationTest : BaseModelTest() {
    @Test
    fun `Test accessing documentation comment`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * Doc
                     */
                    public class Test {
                        /**
                         * Other Method Doc
                         */
                        public void otherMethod() {}

                        /**
                         * Method Doc
                         */
                        public void method() {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    /**
                     * Doc
                     */
                    class Test {
                        /**
                         * Other Method Doc
                         */
                        fun otherMethod() {}

                        /**
                         * Method Doc
                         */
                        fun method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertDocumentationText(expectedOutput = "/** Doc */", message = "class")

            val testMethod = testClass.methods().last()
            testMethod.assertDocumentationText(
                expectedOutput = "/** Method Doc */",
                message = "method"
            )
        }
    }

    @Test
    fun `Test accessing documentation comment after inline comment - bug 391104222`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    /// Inline comment
                    /**
                     * Doc
                     */
                    public class Test {
                        /**
                         * Other Method Doc
                         */
                        public void otherMethod() {}

                        /// Inline method comment
                        /**
                         * Method Doc
                         */
                        public void method() {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    /// Inline comment
                    /**
                     * Doc
                     */
                    class Test {
                        /**
                         * Other Method Doc
                         */
                        fun otherMethod() {}

                        /// Inline method comment
                        /**
                         * Method Doc
                         */
                        fun method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertDocumentationText(expectedOutput = "/** Doc */", message = "class")

            val testMethod = testClass.methods().last()
            testMethod.assertDocumentationText(
                expectedOutput = "/** Method Doc */",
                message = "method"
            )
        }
    }

    @Test
    fun `Test accessing documentation comment before inline comment - bug 391104222`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * Doc
                     */
                    /// Inline comment
                    public class Test {
                        /**
                         * Other Method Doc
                         */
                        public void otherMethod() {}

                        /**
                         * Method Doc
                         */
                        /// Inline method comment
                        public void method() {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    /**
                     * Doc
                     */
                    /// Inline comment
                    class Test {
                        /**
                         * Other Method Doc
                         */
                        fun otherMethod() {}

                        /**
                         * Method Doc
                         */
                        /// Inline method comment
                        fun method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertDocumentationText(expectedOutput = "/** Doc */", message = "class")

            val testMethod = testClass.methods().last()
            testMethod.assertDocumentationText(
                expectedOutput = "/** Method Doc */",
                message = "method"
            )
        }
    }

    @Test
    fun `Test does not treat standalone inline comment as documentation comment - bug 391104222`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    /// Inline comment
                    public class Test {
                        /**
                         * Other Method Doc
                         */
                        public void otherMethod() {}

                        /// Inline method comment
                        public void method() {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    /// Inline comment
                    class Test {
                        /**
                         * Other Method Doc
                         */
                        fun otherMethod() {}

                        /// Inline method comment
                        fun method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertDocumentationText(expectedOutput = "", message = "class")

            val testMethod = testClass.methods().last()
            testMethod.assertDocumentationText(expectedOutput = "", message = "method")
        }
    }

    fun CodebaseContext.checkItemDocumentationLocation(
        item: SelectableItem,
        expectedLocation: String
    ) {
        val documentation = item.requiredDocumentation
        val location = documentation.fileLocation
        assertEquals(expectedLocation, removeTestSpecificDirectories(location.toString()))
    }

    @Test
    fun `Test javadoc locations`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    /** Single line comment. */
                    public class Test {
                        /**
                         * Multi-line
                         * comment.
                         */
                        public Test() {}

                        /**
                         * Comment with start comment token
                         * /**.
                         */
                        public int field = 0;

                        public void noComment() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            checkItemDocumentationLocation(testClass, "MAIN_SRC/src/test/pkg/Test.java:3:1")

            val constructorItem = testClass.assertConstructor(emptyList())
            checkItemDocumentationLocation(constructorItem, "MAIN_SRC/src/test/pkg/Test.java:5:5")

            val fieldItem = testClass.assertField("field")
            checkItemDocumentationLocation(fieldItem, "MAIN_SRC/src/test/pkg/Test.java:11:5")

            // Check location of javadoc that is not specified.
            val methodItem = testClass.assertMethod("noComment", emptyList())
            checkItemDocumentationLocation(methodItem, "null")
        }
    }

    @Test
    fun `Test kdoc locations`() {
        runSourceCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    /** Single line comment. */
                    class Test {
                        /**
                         * Multi-line
                         * comment.
                         */
                        constructor()

                        /**
                         * Comment with start comment token
                         * /**. */
                         */
                        val property = 0

                        fun noComment() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            checkItemDocumentationLocation(testClass, "MAIN_SRC/src/test/pkg/Test.kt:3:1")

            val constructorItem = testClass.assertConstructor(emptyList())
            checkItemDocumentationLocation(constructorItem, "MAIN_SRC/src/test/pkg/Test.kt:5:5")

            val propertyItem = testClass.assertProperty("property")
            checkItemDocumentationLocation(propertyItem, "MAIN_SRC/src/test/pkg/Test.kt:11:5")

            // Check location of javadoc that is not specified.
            val methodItem = testClass.assertMethod("noComment", emptyList())
            checkItemDocumentationLocation(methodItem, "null")
        }
    }

    @Test
    fun `Test javadoc error locations`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    /** {@code unterminated tag on class */
                    public class Test {
                        /** {@code unterminated tag on constructor */
                        public Test() {}

                        /**
                         * Multi-line comment containing
                         * {@code unterminated tag on field
                         */
                        public int field = 0;

                        /**
                         * Blah first; {@code unterminated tag on method
                         */
                        fun commented() {}
                    }
                """
            ),
        ) {
            // Make sure that no issues are found before parsing the Javadoc description blocks.
            // This ensures that no parsing is done unless required.
            assertAndRemoveReportedIssues("")

            // Then, check the printed form of the comment. That is needed to ensure that the
            // comment is parsed and any issues found.
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                """
                    /** {@code unterminated tag on class} */
                """,
            )

            val constructorItem = testClass.assertConstructor(emptyList())
            constructorItem.assertPrintedDocumentation(
                """
                    /** {@code unterminated tag on constructor} */
                """,
            )

            val fieldItem = testClass.assertField("field")
            fieldItem.assertPrintedDocumentation(
                """
                    /**
                     * Multi-line comment containing
                     * {@code unterminated tag on field}
                     */
                """,
            )

            val methodItem = testClass.assertMethod("commented", emptyList())
            methodItem.assertPrintedDocumentation(
                """
                    /** Blah first; {@code unterminated tag on method} */
                """,
            )

            // Finally, check to see what issues have been reported.
            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Test.java:3:5: error: unclosed inline '@code' tag [UnclosedInlineTag]
                    MAIN_SRC/src/test/pkg/Test.java:5:9: error: unclosed inline '@code' tag [UnclosedInlineTag]
                    MAIN_SRC/src/test/pkg/Test.java:10:8: error: unclosed inline '@code' tag [UnclosedInlineTag]
                    MAIN_SRC/src/test/pkg/Test.java:15:20: error: unclosed inline '@code' tag [UnclosedInlineTag]
                """
            )
        }
    }

    @Test
    fun `Test ItemDocumentation print`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    /** Single line comment. */
                    public class Test {
                        /**
                         * Multi-line
                         * comment.
                         */
                        public Test() {}

                        /**
                         * Comment with start comment token
                         * /**.
                         */
                        public int field = 0;

                        public void noComment() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /** Single line comment. */
                    """,
            )

            val constructorItem = testClass.assertConstructor(emptyList())
            constructorItem.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Multi-line
                         * comment.
                         */
                     """,
            )

            val fieldItem = testClass.assertField("field")
            fieldItem.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Comment with start comment token
                         * /**.
                         */
                     """,
            )

            val methodItem = testClass.assertMethod("noComment", emptyList())
            methodItem.assertPrintedDocumentation(
                expectedOutput = "",
            )
        }
    }

    @Test
    fun `Test DocContentOwner append String on overriding method`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Base {
                            public void noCommentAppendToMainDescription() {}

                            public void noCommentAppendDeprecated() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Test extends Base {
                            @Override
                            public void noCommentAppendToMainDescription() {}

                            @Override
                            public void noCommentAppendDeprecated() {}
                        }
                    """
                ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertMethod("noCommentAppendToMainDescription", emptyList()).let { methodItem
                ->
                // Add to main description first.
                methodItem.requiredDocumentation.mainDescriptionOwner.append("Appended to main.")
                methodItem.assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@inheritDoc}
                             *
                             * Appended to main.
                             */
                        """,
                )

                // Add to deprecated second.
                methodItem.requiredDocumentation
                    .blockTagDescriptionOwner("deprecated")
                    .append("Appended to deprecated.")
                methodItem.assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@inheritDoc}
                             *
                             * Appended to main.
                             * @deprecated Appended to deprecated.
                             */
                        """,
                )
            }

            testClass.assertMethod("noCommentAppendDeprecated", emptyList()).let { methodItem ->
                // Add to deprecated first.
                methodItem.requiredDocumentation
                    .blockTagDescriptionOwner("deprecated")
                    .append("Appended to deprecated.")
                methodItem.assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@inheritDoc}
                             * @deprecated Appended to deprecated.
                             */
                        """,
                )

                // TODO(b/454257440): The main description and the block tag descriptions are
                //  intended to be separate and modifying one should not affect the other. So, the
                //  order in which they are done should not matter but this shows that when the
                //  deprecated is added first it behaves differently (extra `<br>` inserted) to when
                //  the main description is added first.

                // Add to main second.
                methodItem.requiredDocumentation.mainDescriptionOwner.append("Appended to main.")
                methodItem.assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@inheritDoc}
                             * <br>
                             * Appended to main.
                             * @deprecated Appended to deprecated.
                             */
                        """,
                )
            }
        }
    }

    @Test
    fun `Test mixture of indentation`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * Summary line.
                    No leading asterisks
                        No leading asterisks but leading whitespace
                    ****** Multiple leading asterisks
                         **  **  ** Mixture of leading asterisks and whitespace
                    // Leading forwards slash
                     // Leading whitespace then forwards slash
                     */
                    public class Test {
                        /**
                         * Summary line.
                        No leading asterisks
                            No leading asterisks but leading whitespace
                        ****** Multiple leading asterisks
                             **  **  ** Mixture of leading asterisks and whitespace
                        // Leading forwards slash
                         // Leading whitespace then forwards slash
                         */
                        public void method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Summary line.
                         *No leading asterisks
                         *    No leading asterisks but leading whitespace
                         * Multiple leading asterisks
                         *  **  ** Mixture of leading asterisks and whitespace
                        // Leading forwards slash
                         * // Leading whitespace then forwards slash
                         */
                    """,
            )

            val testMethod = testClass.methods().single()
            testMethod.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Summary line.
                         *    No leading asterisks
                         *        No leading asterisks but leading whitespace
                         * Multiple leading asterisks
                         *  **  ** Mixture of leading asterisks and whitespace
                         *    // Leading forwards slash
                         *     // Leading whitespace then forwards slash
                         */
                    """,
            )
        }
    }

    @Test
    fun `Test addUniqueBlockTagSectionWithSimpleText`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * Summary line.
                     */
                    public class Test {
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /** Summary line. */
                    """,
            )

            testClass.requiredDocumentation.addUniqueBlockTagSectionWithSimpleText("unique", "1")

            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Summary line.
                         * @unique 1
                         */
                    """,
            )

            testClass.requiredDocumentation.addUniqueBlockTagSectionWithSimpleText("unique", "2")

            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Summary line.
                         * @unique 2
                         */
                    """,
            )
        }
    }

    @Test
    fun `Test leading whitespace in descriptions`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    /**
                     *    Summary line with leading whitespace.
                     * @see   "With leading whitespace"
                     * @deprecated
                     *     Block tag with leading whitespace on separate line.
                     */
                    public class Test {}
                 """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            testClass.assertPrintedDocumentation(
                // The whitespace at the start of the summary line and at the start of each block
                // tag is removed.
                expectedOutput =
                    """
                        /**
                         * Summary line with leading whitespace.
                         *
                         * @see "With leading whitespace"
                         * @deprecated Block tag with leading whitespace on separate line.
                         */
                    """,
            )
        }
    }

    @Test
    fun `Test sorting @param to match parameter list order`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    /**
                     * @param unknown
                     * @param mysterious
                     * @param <D> unknown
                     * @param <C> should be third
                     * @param <B> should be first
                     * @param <A> should be second
                     */
                    public class Test<B, A, C> {
                        /**
                         * @param unknown
                         * @param mysterious
                         * @param <D> unknown
                         * @param <A> mysterious
                         */
                        public static final int FIELD = 1;

                        /**
                         * Type parameters should come before callable parameters.
                         * @param <D> unknown
                         * @param <Z> should be third
                         * @param <Y> should be first
                         * @param <X> should be second
                         * @param unknown
                         * @param mysterious
                         * @param c should be third
                         * @param b should be first
                         * @param a should be second
                         */
                        public <Y, X, Z> void method(Y b, X a, Z c) {}
                    }
                 """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * @param <B> should be first
                         * @param <A> should be second
                         * @param <C> should be third
                         * @param <D> unknown
                         * @param mysterious
                         * @param unknown
                         */
                    """,
            )

            val testField = testClass.fields().single()
            testField.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * @param <A> mysterious
                         * @param <D> unknown
                         * @param mysterious
                         * @param unknown
                         */
                    """,
            )

            val testMethod = testClass.methods().single()
            testMethod.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Type parameters should come before callable parameters.
                         *
                         * @param <Y> should be first
                         * @param <X> should be second
                         * @param <Z> should be third
                         * @param <D> unknown
                         * @param b should be first
                         * @param a should be second
                         * @param c should be third
                         * @param mysterious
                         * @param unknown
                         */
                    """,
            )
        }
    }

    @Test
    fun `Test fully qualifying links that wrap on multiple lines`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import other.pkg.Other;
                        /**
                         * {@link
                         * Other}
                         * {@link
                         * Other#Other}
                         * {@link
                         * Other#field}
                         * {@link
                         * Other#method}
                         * <br>
                         * {@link Other
                         * other class}
                         * {@link Other#method
                         * custom text}
                         */
                        public class Test {
                            /**
                             * Method.
                             * @param p Parameter
                             *     {@link
                             *     Other}
                             *     {@link
                             *     Other#Other}
                             *     {@link
                             *     Other#field}
                             *     {@link
                             *     Other#method}
                             *     <br>
                             *     {@link Other
                             *     other class}
                             *     {@link Other#method
                             *     custom text}
                             */
                            public void method(int p) {}
                        }
                    """
                ),
                java(
                    """
                        package other.pkg;

                        public class Other {
                            public Other() {}
                            public int field;
                            public void method() {}
                        }
                    """
                )
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * {@link other.pkg.Other Other}
                         * {@link other.pkg.Other#Other Other.Other}
                         * {@link other.pkg.Other#field Other.field}
                         * {@link other.pkg.Other#method Other.method}
                         * <br>
                         * {@link other.pkg.Other other class}
                         * {@link other.pkg.Other#method custom text}
                         */
                    """,
            )

            val testMethod = testClass.methods().single()
            testMethod.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Method.
                         *
                         * @param p Parameter
                         *     {@link other.pkg.Other Other}
                         *     {@link other.pkg.Other#Other Other.Other}
                         *     {@link other.pkg.Other#field Other.field}
                         *     {@link other.pkg.Other#method Other.method}
                         *     <br>
                         *     {@link other.pkg.Other other class}
                         *     {@link other.pkg.Other#method custom text}
                         */
                    """,
            )
        }
    }

    private fun assertDocContentToString(
        content: DocContent?,
        expected: String?,
        message: String? = null
    ) {
        if (expected == null) {
            assertNull(content)
        } else {
            assertNotNull(content)
            assertEquals(expected, content.toString(), message)
        }
    }

    @Test
    fun `Test DocContent with main description`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    /**
                     * Main documentation.
                     */
                    public class Test {}
                 """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val documentation = testClass.requiredDocumentation

            assertDocContentToString(
                documentation.mainDescription,
                """JavadocText("Main documentation.")"""
            )
        }
    }

    @Test
    fun `Test DocContentOwner without main description`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    /**
                     * @see String block tag documentation.
                     */
                    public class Test {}
                 """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val documentation = testClass.requiredDocumentation

            assertDocContentToString(
                documentation.mainDescription,
                expected = null,
                message = "mainDescription"
            )
            assertDocContentToString(
                documentation.blockTagDescription("see"),
                expected = """JavadocText("String block tag documentation.")""",
                message = "@see block tag"
            )
            assertNull(documentation.blockTagDescription("unknown"), message = "@unknown block tag")
        }
    }

    @Test
    fun `Test DocContent for param description`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Test {
                        /**
                         * @param p1 param 1 documentation.
                         * @param p2 param 2 documentation.
                         */
                        public void method(String p1, int p2) {}
                    }
                 """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val testMethod = testClass.methods().single()
            val documentation = testMethod.requiredDocumentation

            assertDocContentToString(
                documentation.paramTagDescription("p1"),
                """JavadocText("param 1 documentation.")""",
                message = "@param p1 tag"
            )
            assertDocContentToString(
                documentation.paramTagDescription("p2"),
                """JavadocText("param 2 documentation.")""",
                message = "@param p2 tag"
            )
            assertNull(documentation.paramTagDescription("unknown"), message = "unknown param")
        }
    }

    @Test
    fun `Test append DocContent to main description`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.other;
                        import test.another.Another;
                        /**
                         * @memberDoc Text to {@code append} see {@link #method()}. This is spread
                         *        across multiple lines with leading whitespace and a link to
                         *        {@link Another} class.
                         */
                        public class Other {
                            public void method() {}
                        }
                     """
                ),
                java(
                    """
                        package test.another;
                        public class Another {
                        }
                     """
                ),
                java(
                    """
                        package test.pkg;
                        public class Test {
                            public void method() {}
                        }
                     """
                ),
            ),
        ) {
            val otherClass = codebase.assertClass("test.other.Other")
            val contentToAppend =
                otherClass.requiredDocumentation.blockTagDescription(
                    "memberDoc",
                    forAppending = true
                )!!

            val testClass = codebase.assertClass("test.pkg.Test")
            val classDocumentation = testClass.requiredDocumentation

            testClass.assertPrintedDocumentation(expectedOutput = "", message = "before mutation")

            classDocumentation.mainDescriptionOwner.append(contentToAppend)

            // TODO(b/450228132): The first '@link' should have been resolved to
            //  `test.other.Other#method()`.
            val expectedOutputAfterMutation =
                """
                    /**
                     * Text to {@code append} see {@link #method()}. This is spread
                     * across multiple lines with leading whitespace and a link to
                     * {@link test.another.Another Another} class.
                     */
                """

            // Make sure that the text reflects the changes after mutation.
            testClass.assertDocumentationText(
                expectedOutput = expectedOutputAfterMutation,
                message = "text after mutation"
            )

            testClass.assertPrintedDocumentation(
                expectedOutput = expectedOutputAfterMutation,
                message = "after mutation"
            )
        }
    }

    @Test
    fun `Test append String to main description`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Test {
                        public void method() {}
                    }
                 """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            val testMethod = testClass.methods().single()
            val methodDocumentation = testMethod.requiredDocumentation

            testMethod.assertPrintedDocumentation(expectedOutput = "", message = "before mutation")

            methodDocumentation.mainDescriptionOwner.append("Text to {@code append}.")

            val expectedOutputAfterMutation =
                """
                    /** Text to {@code append}. */
                """

            // Make sure that the text reflects the changes after mutation.
            testMethod.assertDocumentationText(
                expectedOutput = expectedOutputAfterMutation,
                message = "text after mutation"
            )

            testMethod.assertPrintedDocumentation(
                expectedOutput = expectedOutputAfterMutation,
                message = "after mutation"
            )
        }
    }

    @Test
    fun `Test append String to block tag description`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    /** @deprecated */
                    public class Test {
                    }
                 """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val documentation = testClass.requiredDocumentation

            testClass.assertDocumentationText(
                expectedOutput = "/** @deprecated */",
                message = "before mutation"
            )

            documentation.blockTagDescriptionOwner("deprecated").append("extra text")

            val expectedOutputAfterMutation =
                """
                    /** @deprecated extra text */
                """

            // Make sure that the text reflects the changes after mutation.
            testClass.assertDocumentationText(
                expectedOutput = expectedOutputAfterMutation,
                message = "after mutation"
            )

            testClass.assertPrintedDocumentation(
                expectedOutput = expectedOutputAfterMutation,
            )
        }
    }

    @Test
    fun `Test append String to non-existent block tag description`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Test {
                    }
                 """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val documentation = testClass.requiredDocumentation

            testClass.assertDocumentationText(expectedOutput = "", message = "text before mutation")

            // Get the description owner for the non-existent deprecated block tag.
            val descriptionOwner = documentation.blockTagDescriptionOwner("deprecated")

            // Make sure that just getting the description owner did not change the doc comment.
            testClass.assertPrintedDocumentation(
                expectedOutput = "",
                message = "model before mutation"
            )

            // Append the content, this should create the `@deprecated` block tag.
            descriptionOwner.append("extra text")

            val expectedOutputAfterFirstMutation =
                """
                    /** @deprecated extra text */
                """

            // Make sure that the text reflects the changes after mutation.
            testClass.assertDocumentationText(
                expectedOutput = expectedOutputAfterFirstMutation,
                message = "text after first mutation"
            )

            // Make sure that the model reflects the changes after mutation.
            testClass.assertPrintedDocumentation(
                expectedOutput = expectedOutputAfterFirstMutation,
                message = "model after first mutation"
            )

            // Use the descriptionOwner to append some more content to make sure the block tag is
            // not added twice.
            descriptionOwner.append("Some more content")

            val expectedOutputAfterSecondMutation =
                """
                    /**
                     * @deprecated extra text.
                     * <br>
                     * Some more content
                     */
                """

            // Make sure that the text reflects the changes after mutation.
            testClass.assertDocumentationText(
                expectedOutput = expectedOutputAfterSecondMutation,
                message = "text after second mutation"
            )

            // Make sure that the model reflects the changes after mutation.
            testClass.assertPrintedDocumentation(
                expectedOutput = expectedOutputAfterSecondMutation,
                message = "model after second mutation"
            )
        }
    }

    @Test
    fun `Test append String to param tag description`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Test {
                        /** @param p */
                        public void method(int p) {}
                    }
                 """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            val testMethod = testClass.methods().single()
            val documentation = testMethod.requiredDocumentation

            testMethod.assertDocumentationText(
                expectedOutput = "/** @param p */",
                message = "before mutation"
            )

            documentation.paramTagDescriptionOwner("p").append("extra text")

            val expectedOutputAfterMutation =
                """
                    /** @param p extra text */
                """

            // Make sure that the text reflects the changes after mutation.
            testMethod.assertDocumentationText(
                expectedOutput = expectedOutputAfterMutation,
                message = "after mutation"
            )

            testMethod.assertPrintedDocumentation(
                expectedOutput = expectedOutputAfterMutation,
            )
        }
    }

    @Test
    fun `Test append String to non-existent param tag description`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Test {
                        public void method(int p) {}
                    }
                 """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val testMethod = testClass.methods().single()
            val documentation = testMethod.requiredDocumentation

            testMethod.assertDocumentationText(
                expectedOutput = "",
                message = "text before mutation"
            )

            // Get the description owner for the non-existent deprecated block tag.
            val descriptionOwner = documentation.paramTagDescriptionOwner("p")

            // Make sure that just getting the description owner did not change the doc comment.
            testMethod.assertPrintedDocumentation(
                expectedOutput = "",
                message = "model before mutation"
            )

            // Append the content, this should create the `@deprecated` block tag.
            descriptionOwner.append("extra text")

            val expectedOutputAfterFirstMutation =
                """
                    /** @param p extra text */
                """

            // Make sure that the text reflects the changes after mutation.
            testMethod.assertDocumentationText(
                expectedOutput = expectedOutputAfterFirstMutation,
                message = "text after first mutation"
            )

            // Make sure that the model reflects the changes after mutation.
            testMethod.assertPrintedDocumentation(
                expectedOutput = expectedOutputAfterFirstMutation,
                message = "model after first mutation"
            )

            // Use the descriptionOwner to append some more content to make sure the block tag is
            // not added twice.
            descriptionOwner.append("Some more content")

            val expectedOutputAfterSecondMutation =
                """
                    /**
                     * @param p extra text.
                     * <br>
                     * Some more content
                     */
                """

            // Make sure that the text reflects the changes after mutation.
            testMethod.assertDocumentationText(
                expectedOutput = expectedOutputAfterSecondMutation,
                message = "text after second mutation"
            )

            // Make sure that the model reflects the changes after mutation.
            testMethod.assertPrintedDocumentation(
                expectedOutput = expectedOutputAfterSecondMutation,
                message = "model after second mutation"
            )
        }
    }

    @Test
    fun `Test first sentence handling - pure text`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * A summary line that uses e.g. to test whether the workaround for a Javadoc
                     * problem that ends the summary line at the first `.` is applied when
                     * printing.
                     */
                    public class Test {
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * A summary line that uses e.g.&nbsp;to test whether the workaround for a Javadoc
                         * problem that ends the summary line at the first `.` is applied when
                         * printing.
                         */
                    """,
            )
        }
    }

    @Test
    fun `Test first sentence handling - link tag`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * A {@link java.util.List list} contains things, e.g. names.
                     */
                    public class Test {
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /** A {@link java.util.List list} contains things, e.g.&nbsp;names. */
                    """,
            )
        }
    }

    @Test
    fun `Test first sentence handling - eg not in summary sentence`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * A simple summary sentence.
                     *
                     * <p>A paragraph with some stuff, e.g. words</p>
                     */
                    public class Test {
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * A simple summary sentence.
                         *
                         * <p>A paragraph with some stuff, e.g. words</p>
                         */
                    """,
            )
        }
    }

    @Test
    fun `Test first sentence handling - eg inside inline tag`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * A simple summary sentence with an inline tag {@code e.g. this}.
                     */
                    public class Test {
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /** A simple summary sentence with an inline tag {@code e.g. this}. */
                    """,
            )
        }
    }

    @Test
    fun `Test allow reading comments = false`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        /**
                         * @hide
                         */
                        @PkgAnno
                        package test.pkg;
                    """
                ),
                java(
                    """
                        package test.pkg;

                        /** Annotation comment. */
                        public @interface PkgAnno {
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    allowReadingComments = false,
                ),
        ) {
            val testPackage = codebase.assertPackage("test.pkg")
            testPackage.assertPrintedDocumentation(
                expectedOutput = "",
            )

            assertEquals(
                "ModifierList(flags = [public], annotations = [@test.pkg.PkgAnno])",
                testPackage.modifiers.toString()
            )

            val testAnnotation = codebase.assertClass("test.pkg.PkgAnno")
            testAnnotation.assertPrintedDocumentation(
                expectedOutput = "",
            )
        }
    }

    @Test
    fun `Test allow reading comments = true`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        /**
                         * Some comment.
                         */
                        @PkgAnno
                        package test.pkg;
                    """
                ),
                java(
                    """
                        package test.pkg;

                        /** Annotation comment. */
                        public @interface PkgAnno {
                        }
                    """
                ),
            ),
        ) {
            val testPackage = codebase.assertPackage("test.pkg")
            testPackage.assertPrintedDocumentation(
                expectedOutput = "/** Some comment. */\n",
            )

            assertEquals(
                "ModifierList(flags = [public], annotations = [@test.pkg.PkgAnno])",
                testPackage.modifiers.toString()
            )

            val testAnnotation = codebase.assertClass("test.pkg.PkgAnno")
            testAnnotation.assertPrintedDocumentation(
                expectedOutput = "/** Annotation comment. */\n",
            )
        }
    }
}
