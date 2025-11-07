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
import com.android.tools.metalava.reporter.RecordingReporter
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import java.io.PrintWriter
import java.io.StringWriter
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
            val documentation = testClass.documentation
            assertEquals(
                """
                    /**
                     * Doc
                     */
                """
                    .trimIndent(),
                documentation.text.trim()
            )

            val methodDocumentation = testClass.methods().last().documentation
            assertEquals(
                """
                    /**
                         * Method Doc
                         */
                """
                    .trimIndent(),
                methodDocumentation.text.trim()
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
            val documentation = testClass.documentation
            assertEquals(
                """
                    /**
                     * Doc
                     */
                """
                    .trimIndent(),
                documentation.text.trim()
            )

            val methodDocumentation = testClass.methods().last().documentation
            assertEquals(
                """
                    /**
                         * Method Doc
                         */
                """
                    .trimIndent(),
                methodDocumentation.text.trim()
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
            val documentation = testClass.documentation
            assertEquals(
                """
                    /**
                     * Doc
                     */
                """
                    .trimIndent(),
                documentation.text.trim()
            )

            val methodDocumentation = testClass.methods().last().documentation
            assertEquals(
                """
                    /**
                         * Method Doc
                         */
                """
                    .trimIndent(),
                methodDocumentation.text.trim()
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
            val documentation = testClass.documentation
            assertEquals("", documentation.text)

            val methodDocumentation = testClass.methods().last().documentation
            assertEquals("", methodDocumentation.text.trim())
        }
    }

    fun CodebaseContext.checkItemDocumentationLocation(
        item: SelectableItem,
        expectedLocation: String
    ) {
        val documentation = item.documentation
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
        val reporter = RecordingReporter()
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
            testFixture =
                TestFixture(
                    reporter = reporter,
                ),
        ) {
            // Make sure that no issues are found before parsing the Javadoc description blocks.
            // This ensures that no parsing is done unless required.
            val issuesBeforeParsing = removeTestSpecificDirectories(reporter.issues)
            assertEquals("", issuesBeforeParsing)

            // Then, check the printed form of the comment. That is needed to ensure that the
            // comment is parsed and any issues found.
            val testClass = codebase.assertClass("test.pkg.Test")
            checkItemDocumentationPrint(
                testClass,
                """
                    /** {@code unterminated tag on class} */

                """,
            )

            val constructorItem = testClass.assertConstructor(emptyList())
            checkItemDocumentationPrint(
                constructorItem,
                """
                    /** {@code unterminated tag on constructor} */

                """,
            )

            val fieldItem = testClass.assertField("field")
            checkItemDocumentationPrint(
                fieldItem,
                """
                    /**
                     * Multi-line comment containing
                     * {@code unterminated tag on field}
                     */

                """,
            )

            val methodItem = testClass.assertMethod("commented", emptyList())
            checkItemDocumentationPrint(
                methodItem,
                """
                    /** Blah first; {@code unterminated tag on method} */

                """,
            )

            // Finally, check to see what issues have been reported.
            val issuesAfterParsing = removeTestSpecificDirectories(reporter.issues)
            assertEquals(
                """
                    MAIN_SRC/src/test/pkg/Test.java:3:5: error: unclosed inline '@code' tag [UnclosedInlineTag]
                    MAIN_SRC/src/test/pkg/Test.java:5:9: error: unclosed inline '@code' tag [UnclosedInlineTag]
                    MAIN_SRC/src/test/pkg/Test.java:10:8: error: unclosed inline '@code' tag [UnclosedInlineTag]
                    MAIN_SRC/src/test/pkg/Test.java:15:20: error: unclosed inline '@code' tag [UnclosedInlineTag]
                """
                    .trimIndent(),
                issuesAfterParsing
            )
        }
    }

    private fun checkItemDocumentationPrint(
        item: SelectableItem,
        expectedOutput: String,
        message: String? = null
    ) {
        val documentation = item.documentation
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { documentation.print(it) }
        val actualOutput = stringWriter.toString()
        assertEquals(expectedOutput.trimIndent(), actualOutput, message)
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
            checkItemDocumentationPrint(
                testClass,
                expectedOutput =
                    """
                        /** Single line comment. */

                    """,
            )

            val constructorItem = testClass.assertConstructor(emptyList())
            checkItemDocumentationPrint(
                constructorItem,
                expectedOutput =
                    """
                        /**
                         * Multi-line
                         * comment.
                         */

                     """,
            )

            val fieldItem = testClass.assertField("field")
            checkItemDocumentationPrint(
                fieldItem,
                expectedOutput =
                    """
                        /**
                         * Comment with start comment token
                         * /**.
                         */

                     """,
            )

            val methodItem = testClass.assertMethod("noComment", emptyList())
            checkItemDocumentationPrint(
                methodItem,
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
                methodItem.documentation.mainDescriptionOwner.append("Appended to main.")
                checkItemDocumentationPrint(
                    methodItem,
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
                methodItem.documentation
                    .blockTagDescriptionOwner("deprecated")
                    .append("Appended to deprecated.")
                checkItemDocumentationPrint(
                    methodItem,
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
                methodItem.documentation
                    .blockTagDescriptionOwner("deprecated")
                    .append("Appended to deprecated.")
                checkItemDocumentationPrint(
                    methodItem,
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
                methodItem.documentation.mainDescriptionOwner.append("Appended to main.")
                checkItemDocumentationPrint(
                    methodItem,
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
            checkItemDocumentationPrint(
                testClass,
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
            checkItemDocumentationPrint(
                testMethod,
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
            checkItemDocumentationPrint(
                testClass,
                expectedOutput =
                    """
                        /** Summary line. */

                    """,
            )

            testClass.documentation.addUniqueBlockTagSectionWithSimpleText("unique", "1")

            checkItemDocumentationPrint(
                testClass,
                expectedOutput =
                    """
                        /**
                         * Summary line.
                         * @unique 1
                         */

                    """,
            )

            testClass.documentation.addUniqueBlockTagSectionWithSimpleText("unique", "2")

            checkItemDocumentationPrint(
                testClass,
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

            checkItemDocumentationPrint(
                testClass,
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

            checkItemDocumentationPrint(
                testClass,
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
            checkItemDocumentationPrint(
                testField,
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
            checkItemDocumentationPrint(
                testMethod,
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

            checkItemDocumentationPrint(
                testClass,
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
            checkItemDocumentationPrint(
                testMethod,
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
            val documentation = testClass.documentation

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
            val documentation = testClass.documentation

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
            val documentation = testMethod.documentation

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
                otherClass.documentation.blockTagDescription("memberDoc", forAppending = true)!!

            val testClass = codebase.assertClass("test.pkg.Test")
            val classDocumentation = testClass.documentation

            checkItemDocumentationPrint(testClass, expectedOutput = "", message = "before mutation")

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
            assertEquals(
                expectedOutputAfterMutation.trimIndent(),
                classDocumentation.text,
                message = "text after mutation"
            )

            checkItemDocumentationPrint(
                testClass,
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
            val methodDocumentation = testMethod.documentation

            checkItemDocumentationPrint(
                testMethod,
                expectedOutput = "",
                message = "before mutation"
            )

            methodDocumentation.mainDescriptionOwner.append("Text to {@code append}.")

            val expectedOutputAfterMutation =
                """
                    /** Text to {@code append}. */

                """

            // Make sure that the text reflects the changes after mutation.
            assertEquals(
                expectedOutputAfterMutation.trimIndent(),
                methodDocumentation.text,
                message = "text after mutation"
            )

            checkItemDocumentationPrint(
                testMethod,
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
            val documentation = testClass.documentation

            assertEquals("/** @deprecated */", documentation.text, message = "before mutation")

            documentation.blockTagDescriptionOwner("deprecated").append("extra text")

            val expectedOutputAfterMutation =
                """
                    /** @deprecated extra text */

                """

            // Make sure that the text reflects the changes after mutation.
            assertEquals(
                expectedOutputAfterMutation.trimIndent(),
                documentation.text,
                message = "after mutation"
            )

            checkItemDocumentationPrint(
                testClass,
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
            val documentation = testClass.documentation

            assertEquals("", documentation.text, message = "text before mutation")

            // Get the description owner for the non-existent deprecated block tag.
            val descriptionOwner = documentation.blockTagDescriptionOwner("deprecated")

            // Make sure that just getting the description owner did not change the doc comment.
            checkItemDocumentationPrint(
                testClass,
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
            assertEquals(
                expectedOutputAfterFirstMutation.trimIndent(),
                documentation.text,
                message = "text after first mutation"
            )

            // Make sure that the model reflects the changes after mutation.
            checkItemDocumentationPrint(
                testClass,
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
            assertEquals(
                expectedOutputAfterSecondMutation.trimIndent(),
                documentation.text,
                message = "text after second mutation"
            )

            // Make sure that the model reflects the changes after mutation.
            checkItemDocumentationPrint(
                testClass,
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
            val documentation = testMethod.documentation

            assertEquals("/** @param p */", documentation.text, message = "before mutation")

            documentation.paramTagDescriptionOwner("p").append("extra text")

            val expectedOutputAfterMutation =
                """
                    /** @param p extra text */

                """

            // Make sure that the text reflects the changes after mutation.
            assertEquals(
                expectedOutputAfterMutation.trimIndent(),
                documentation.text,
                message = "after mutation"
            )

            checkItemDocumentationPrint(
                testMethod,
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
            val documentation = testMethod.documentation

            assertEquals("", documentation.text, message = "text before mutation")

            // Get the description owner for the non-existent deprecated block tag.
            val descriptionOwner = documentation.paramTagDescriptionOwner("p")

            // Make sure that just getting the description owner did not change the doc comment.
            checkItemDocumentationPrint(
                testMethod,
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
            assertEquals(
                expectedOutputAfterFirstMutation.trimIndent(),
                documentation.text,
                message = "text after first mutation"
            )

            // Make sure that the model reflects the changes after mutation.
            checkItemDocumentationPrint(
                testMethod,
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
            assertEquals(
                expectedOutputAfterSecondMutation.trimIndent(),
                documentation.text,
                message = "text after second mutation"
            )

            // Make sure that the model reflects the changes after mutation.
            checkItemDocumentationPrint(
                testMethod,
                expectedOutput = expectedOutputAfterSecondMutation,
                message = "model after second mutation"
            )
        }
    }
}
