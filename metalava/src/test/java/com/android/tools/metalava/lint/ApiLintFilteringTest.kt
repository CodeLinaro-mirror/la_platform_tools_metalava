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

package com.android.tools.metalava.lint

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.testing.java
import org.junit.Test
import org.junit.runners.Parameterized

/** Tests for the filtering of issues done by [ApiLint]. */
class ApiLintFilteringTest(private val previouslyReleasedApiUse: PreviouslyReleasedApiUse) :
    DriverTest() {

    enum class PreviouslyReleasedApiUse {
        WITH,
        WITHOUT,
    }

    companion object {
        /** Run each test with and without the previously released API. */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun testParameters() = PreviouslyReleasedApiUse.entries
    }

    private fun checkFiltering(
        sourceFiles: Array<TestFile>,
        expectedIssuesWithoutPreviouslyReleasedApi: String,
        previouslyReleasedApi: String,
        expectedIssuesWithPreviouslyReleasedApi: String,
    ) {
        val (apiLint, expectedIssues) =
            if (previouslyReleasedApiUse == PreviouslyReleasedApiUse.WITH)
                Pair(previouslyReleasedApi, expectedIssuesWithPreviouslyReleasedApi)
            else Pair("", expectedIssuesWithoutPreviouslyReleasedApi)
        val expectedFail = if (expectedIssues == "") "" else DefaultLintErrorMessage
        check(
            apiLint = apiLint,
            sourceFiles = sourceFiles,
            expectedFail = expectedFail,
            expectedIssues = expectedIssues,
        )
    }

    @Test
    fun `test errors are ignored on previously released APIs`() {
        checkFiltering(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class Foo {
                                public void method(String s) {}
                                public int hashCode() {return 0;}
                                public String field;
                            }
                        """
                    ),
                ),
            expectedIssuesWithoutPreviouslyReleasedApi =
                """
                    src/test/pkg/Foo.java:3: error: Missing nullability on parameter `s` in method `method` [MissingNullability]
                    src/test/pkg/Foo.java:4: error: Must override both equals and hashCode; missing one in test.pkg.Foo [EqualsAndHashCode]
                    src/test/pkg/Foo.java:5: error: Missing nullability on field `field` in class `class test.pkg.Foo` [MissingNullability]
                    src/test/pkg/Foo.java:5: error: Bare field field must be marked final, or moved behind accessors if mutable [MutableBareField]
                """,
            previouslyReleasedApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public void method(String);
                        method public int hashCode();
                        field public String field;
                      }
                    }
                """,
            expectedIssuesWithPreviouslyReleasedApi = "",
        )
    }

    @Test
    fun `test checkClass produced errors are treated as warnings on new members of previously released classes`() {
        // The `EqualsAndHashCode` check is performed by `checkEquals()` which is called by
        // `checkClass()` and so was previously only called for new classes. This test checks that
        // behavior is maintained.
        checkFiltering(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class Foo {
                                public void method(String s) {}
                                public String field;
                                public int hashCode() {return 0;}
                            }
                        """
                    ),
                ),
            expectedIssuesWithoutPreviouslyReleasedApi =
                """
                    src/test/pkg/Foo.java:3: error: Missing nullability on parameter `s` in method `method` [MissingNullability]
                    src/test/pkg/Foo.java:4: error: Missing nullability on field `field` in class `class test.pkg.Foo` [MissingNullability]
                    src/test/pkg/Foo.java:4: error: Bare field field must be marked final, or moved behind accessors if mutable [MutableBareField]
                    src/test/pkg/Foo.java:5: error: Must override both equals and hashCode; missing one in test.pkg.Foo [EqualsAndHashCode]
                """,
            previouslyReleasedApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                      }
                    }
                """,
            expectedIssuesWithPreviouslyReleasedApi =
                // Notice that the `EqualsAndHashCode` issue is reported as a warning, not an error.
                """
                    src/test/pkg/Foo.java:3: error: Missing nullability on parameter `s` in method `method` [MissingNullability]
                    src/test/pkg/Foo.java:4: error: Missing nullability on field `field` in class `class test.pkg.Foo` [MissingNullability]
                    src/test/pkg/Foo.java:4: error: Bare field field must be marked final, or moved behind accessors if mutable [MutableBareField]
                    src/test/pkg/Foo.java:5: warning: Must override both equals and hashCode; missing one in test.pkg.Foo (ErrorWhenNew) [EqualsAndHashCode]
                """,
        )
    }
}
