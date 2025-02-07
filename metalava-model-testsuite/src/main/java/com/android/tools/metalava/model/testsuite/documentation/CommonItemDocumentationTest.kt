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

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
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
}
