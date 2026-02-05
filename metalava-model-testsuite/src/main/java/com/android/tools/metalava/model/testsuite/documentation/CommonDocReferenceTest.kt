/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.source.doc.DocContentPredicates
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertTrue
import org.junit.Test

/** Common tests for references from within documentation comments. */
class CommonDocReferenceTest : BaseModelTest() {
    @RequiresCapabilities(Capability.JAVA)
    @Test
    @Suppress("RedundantThrows")
    fun `Test @throws resolution`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import java.util.ConcurrentModificationException;
                        import static java.lang.System.err;
                        public class Test<X extends Throwable> {
                            /**
                             * @throws X because reason 1.
                             * @throws Y because reason 2.
                             * @throws TestException    because reason 3.
                             * @throws IllegalArgumentException because reason 4.
                             * @throws java.io.IOException because reason 5.
                             * @throws ConcurrentModificationException because reason 6.
                             * @throws UnknownException because reason 7.
                             * @throws err because reason 8.
                             */
                            public <Y extends Throwable> void method() throws X, Y, java.io.IOException {}

                            public class TestException extends RuntimeException {}
                        }
                    """
                ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val testMethod = testClass.methods().single()
            testMethod.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * @throws UnknownException because reason 7.
                         * @throws X because reason 1.
                         * @throws Y because reason 2.
                         * @throws err because reason 8.
                         * @throws java.io.IOException because reason 5.
                         * @throws java.lang.IllegalArgumentException because reason 4.
                         * @throws java.util.ConcurrentModificationException because reason 6.
                         * @throws test.pkg.Test.TestException because reason 3.
                         */
                    """,
            )

            val containsIOException =
                DocContentPredicates.textContainsAny { it.contains("IOException") }
            assertTrue(
                testMethod.requiredDocumentation.check(containsIOException),
                message = "contains IOException"
            )

            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Test.java:12:16: warning: Could not resolve a class or type parameter called 'UnknownException' in 'method test.pkg.Test.method()' (ErrorWhenNew) [UnresolvedLink]
                    MAIN_SRC/src/test/pkg/Test.java:13:16: warning: Could not resolve a class or type parameter called 'err' in 'method test.pkg.Test.method()' (ErrorWhenNew) [UnresolvedLink]
                """
            )
        }
    }

    @Test
    fun `Test link tag spread across multiple lines`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    import java.util.List;
                    /**
                     * {@link java.util.List a list
                     * class}
                     * {@link List a list
                     * class}
                     * {@link List
                     * a list class}
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
                         * {@link java.util.List a list
                         * class}
                         * {@link java.util.List a list
                         * class}
                         * {@link java.util.List a list class}
                         */
                    """,
            )
        }
    }

    @Test
    fun `Test link tag inside @code`() {
        // This is not valid. The specification says the following at
        // https://docs.oracle.com/en/java/javase/21/docs/specs/javadoc/doc-comment-spec.html#code
        //   `{@code text}` - Displays text in code font without interpreting the text as HTML
        //           markup or nested Javadoc tags.
        //
        // This verifies that a `{@link}` tag is not resolved when it is inside `{@code}`.
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    import java.util.ArrayList;
                    /** {@code new {@link ArrayList}()} */
                    public class Test {
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /** {@code new {@link ArrayList}()} */
                    """,
            )
        }
    }

    @Test
    fun `Test link tag with invalid reference starting with period`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    import java.util.ArrayList;
                    /** {@link .java.util.List} */
                    public class Test {
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /** {@link .java.util.List} */
                    """,
            )

            assertAndRemoveReportedIssues(
                "MAIN_SRC/src/test/pkg/Test.java:3:12: warning: Malformed reference `.java.util.List` (ErrorWhenNew) [MalformedDocReference]"
            )
        }
    }

    @Test
    fun `Test link tag in package-info`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import java.util.List;
                        public class Test {
                            public int field;
                            public void method(Test t, List<String> s) {}
                        }
                    """
                ),
                java(
                    "test/pkg/package-info.java",
                    """
                        /**
                         * {@link Test}
                         * {@link Test#field}
                         * {@link Test#method(Test,List<String>)}
                         * {@link #invalid(String,Number)}
                         */
                        package test.pkg;

                        import java.util.List;
                    """
                ),
            ),
        ) {
            val testPackage = codebase.assertPackage("test.pkg")
            testPackage.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * {@link test.pkg.Test Test}
                         * {@link test.pkg.Test#field Test.field}
                         * {@link test.pkg.Test#method(test.pkg.Test,java.util.List) Test.method(Test,List<String>)}
                         * {@link #invalid(java.lang.String,java.lang.Number) invalid(String,Number)}
                         */
                    """,
            )

            assertAndRemoveReportedIssues(
                // TODO(b/447588621): Should report an issue about the #invalid(...) reference.
                ""
            )
        }
    }
}
