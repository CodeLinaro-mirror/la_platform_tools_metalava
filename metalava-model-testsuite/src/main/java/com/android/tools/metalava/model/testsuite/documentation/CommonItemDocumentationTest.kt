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
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.reporter.RecordingReporter
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertEquals
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

    private fun checkItemDocumentationPrint(item: SelectableItem, expectedOutput: String) {
        val documentation = item.documentation
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { documentation.print(it) }
        val actualOutput = stringWriter.toString()
        assertEquals(expectedOutput.trimIndent(), actualOutput)
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

    private fun checkItemDocumentationAppend(item: SelectableItem, expectedOutput: String) {
        val documentation = item.documentation
        documentation.appendDocumentation("Appended.", null)
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { documentation.print(it) }
        val actualOutput = stringWriter.toString()
        assertEquals(expectedOutput.trimIndent(), actualOutput)
    }

    @Test
    fun `Test ItemDocumentation appendDocumentation`() {
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
            checkItemDocumentationAppend(
                testClass,
                expectedOutput =
                    """
                        /**
                         * Single line comment.
                         * <br>
                         * Appended.
                         */

                    """,
            )

            val constructorItem = testClass.assertConstructor(emptyList())
            checkItemDocumentationAppend(
                constructorItem,
                expectedOutput =
                    """
                        /**
                         * Multi-line
                         * comment.
                         *
                         * <br>
                         * Appended.
                         */

                     """,
            )

            val fieldItem = testClass.assertField("field")
            checkItemDocumentationAppend(
                fieldItem,
                expectedOutput =
                    """
                        /**
                         * Comment with start comment token
                         * /**.
                         *
                         * <br>
                         * Appended.
                         */

                     """,
            )

            val methodItem = testClass.assertMethod("noComment", emptyList())
            checkItemDocumentationAppend(
                methodItem,
                expectedOutput =
                    """
                        /** Appended. */

                    """,
            )
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
    fun `Test appending to Javadoc with errors`() {
        val reporter = RecordingReporter()
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    /** Unclosed {@code inline tag */
                    public class Test {}
                 """
            ),
            testFixture =
                TestFixture(
                    reporter = reporter,
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            // Add a block tag to the `DocComment`. This will create a DocComment from `text`, by
            // splitting it into a main `DocDescription` and an empty set of `BlockTagSection`s. It
            // does not parse the `DocDescription` content so does not detect the unclosed `code`
            // tag. After creating the `DocComment` it mutates it by adding a `BlockTagSection`
            // which again does not detect the unclosed `code` tag. It then sets `text` to `null` to
            // force it to be regenerated from the `DocComment` next time it is accessed.
            testClass.documentation.addUniqueBlockTagSectionWithSimpleText("custom", "text")

            // Append the documentation. This forces the `text` field to be generated from the
            // `DocComment`. That first has to parse the `DocDescription` and create the
            // `JavadocContent` model. During that process the unclosed `code` tag is detected and
            // reported. Reporting requires accessing `ItemDocumentation.fileLocation` and in the
            // `PsiItemDocumentation` implementation that requires the `psiComment` field to have
            // been initialized. That is initialized at the same time as `text` was first
            // initialized so the implementation checks to see whether `text` has been initialized
            // before trying to initialize it to avoid re-entering the code to generate `text` from
            // the `DocComment` which would cause a `StackOverflowError`.
            testClass.documentation.appendDocumentation("Blah", null)

            checkItemDocumentationPrint(
                testClass,
                expectedOutput =
                    """
                        /**
                         * Unclosed {@code inline tag}
                         * <br>
                         * Blah
                         * @custom text
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
}
