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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlagAction
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import org.junit.Test

class CommonConditionalDocumentationTest : BaseModelTest() {
    /**
     * Check the behavior of an invalid flags field.
     *
     * @param flagsFile the definition of the `Flags` class.
     * @param expectedIssues the expected issues that will be reported.
     */
    private fun checkInvalidFlagsField(
        flagsFile: TestFile,
        expectedIssues: String,
    ) {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        /**
                         * Summary.
                         * {@if (flag(Flags.FLAG))
                         *     {Content when flag enabled.}
                         * else
                         *     {Content when flag disabled.}
                         * }
                         */
                        public class Test {
                        }
                    """
                ),
                flagsFile,
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Summary.
                         * Content when flag disabled.
                         */
                    """,
            )

            assertAndRemoveReportedIssues(expectedIssues)
        }
    }

    @Test
    fun `Test conditional javadoc no flag field defined`() {
        checkInvalidFlagsField(
            java(
                """
                    package test.pkg;

                    public class Flags {
                    }
                """
            ),
            expectedIssues =
                "MAIN_SRC/src/test/pkg/Test.java:5:15: error: Could not resolve 'Flags.FLAG' as could not find 'FLAG' in 'class test.pkg.Flags' [InvalidJavadocExpr]"
        )
    }

    @Test
    fun `Test conditional javadoc flag field has no constant value`() {
        checkInvalidFlagsField(
            java(
                """
                    package test.pkg;

                    public class Flags {
                        public static final String FLAG = "flag".toUpperCase();
                    }
                """
            ),
            expectedIssues =
                "MAIN_SRC/src/test/pkg/Test.java:5:15: error: invalid flag field 'Flags.FLAG', it does not have a constant value [InvalidJavadocExpr]"
        )
    }

    @Test
    fun `Test conditional javadoc flag field value is not a string`() {
        checkInvalidFlagsField(
            java(
                """
                    package test.pkg;

                    public class Flags {
                        public static final int FLAG = 10;
                    }
                """
            ),
            expectedIssues =
                "MAIN_SRC/src/test/pkg/Test.java:5:15: error: invalid flag field 'Flags.FLAG', expected a string value, found 10 of type int [InvalidJavadocExpr]"
        )
    }

    @Test
    fun `Test conditional javadoc flag field reference does not refer to a field`() {
        checkInvalidFlagsField(
            java(
                """
                    package test.pkg;

                    public class Flags {
                        public static class FLAG {}
                    }
                """
            ),
            expectedIssues =
                "MAIN_SRC/src/test/pkg/Test.java:5:15: error: invalid item found for 'Flags.FLAG', expected field, found class test.pkg.Flags.FLAG [InvalidJavadocExpr]"
        )
    }

    @Test
    fun `Test conditional javadoc flag field defined but no flag defined`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        /**
                         * Summary.
                         * {@if (flag(Flags.FLAG))
                         *     {Content when flag enabled.}
                         * else
                         *     {Content when flag disabled.}
                         * }
                         */
                        public class Test {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Flags {
                            public static final String FLAG = "flag";
                        }
                    """
                ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Summary.
                         * Content when flag enabled.
                         */
                    """,
            )
        }
    }

    @Test
    fun `Test conditional javadoc flag field and flag defined but reverted`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        /**
                         * Summary.
                         * {@if (flag(Flags.FLAG))
                         *     {Content when flag enabled.}
                         * else
                         *     {Content when flag disabled.}
                         * }
                         */
                        public class Test {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Flags {
                            public static final String FLAG = "flag";
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    apiFlags =
                        ApiFlags(
                            listOf(
                                ApiFlag(
                                    "flag",
                                    ApiFlagAction.REVERT,
                                    isExported = true,
                                )
                            )
                        )
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Summary.
                         * Content when flag disabled.
                         */
                    """,
            )
        }
    }

    @Test
    fun `Test conditional javadoc flag field and flag defined but kept`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        /**
                         * Summary.
                         * {@if (flag(Flags.FLAG))
                         *     {Content when flag enabled.}
                         * else
                         *     {Content when flag disabled.}
                         * }
                         */
                        public class Test {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Flags {
                            public static final String FLAG = "flag";
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    apiFlags =
                        ApiFlags(
                            listOf(
                                ApiFlag(
                                    "flag",
                                    ApiFlagAction.KEEP,
                                    isExported = true,
                                )
                            )
                        )
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Summary.
                         * Content when flag enabled.
                         */
                    """,
            )
        }
    }

    @Test
    fun `Test conditional javadoc flag field using statically imported flag field`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        import static test.pkg.Flags.FLAG;

                        /**
                         * Summary.
                         * {@if (flag(FLAG))
                         *     {Content when flag enabled.}
                         * else
                         *     {Content when flag disabled.}
                         * }
                         */
                        public class Test {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Flags {
                            public static final String FLAG = "flag";
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    apiFlags =
                        ApiFlags(
                            listOf(
                                ApiFlag(
                                    "flag",
                                    ApiFlagAction.KEEP,
                                    isExported = true,
                                )
                            )
                        )
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Summary.
                         * Content when flag enabled.
                         */
                    """,
            )
        }
    }
}
