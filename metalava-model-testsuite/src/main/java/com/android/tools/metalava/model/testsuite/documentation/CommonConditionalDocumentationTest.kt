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

import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlagAction
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import org.junit.Test

class CommonConditionalDocumentationTest : BaseModelTest() {
    @Test
    fun `Test conditional javadoc no flag field defined`() {
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
                            mapOf(
                                "flag" to
                                    ApiFlag.getFlag(
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
                            mapOf(
                                "flag" to
                                    ApiFlag.getFlag(
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
