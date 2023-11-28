/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private val annotationsList = listOf(systemApiSource, flaggedApiSource, nonNullSource)

@RunWith(Parameterized::class)
class FlaggedApiTest(private val config: Configuration) : DriverTest() {

    /** The configuration of the test. */
    data class Configuration(
        val surface: Surface,
        val flagged: Flagged,
    ) {
        val extraArguments = surface.args + flagged.args

        override fun toString(): String {
            val surfaceText = surface.name.lowercase(Locale.US)
            val prepositionText = flagged.name.lowercase(Locale.US)
            return "$surfaceText $prepositionText flagged api"
        }
    }

    /** The surfaces that this test will check. */
    enum class Surface(val args: List<String>) {
        PUBLIC(emptyList()),
        SYSTEM(listOf(ARG_SHOW_ANNOTATION, ANDROID_SYSTEM_API)),
    }

    /** The different configurations of the flagged API that this test will check. */
    enum class Flagged(val args: List<String>) {
        WITH(emptyList()),
        WITHOUT(listOf(ARG_HIDE_ANNOTATION, ANDROID_FLAGGED_API))
    }

    companion object {
        /** Compute the cross product of [Surface] and [Flagged]. */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun configurations(): Iterable<Configuration> =
            Surface.values().flatMap { surface ->
                Flagged.values().map { flagged ->
                    Configuration(
                        surface = surface,
                        flagged = flagged,
                    )
                }
            }
    }

    /**
     * Check the result of generating APIs with and without flagged apis for both public and system
     * API surfaces.
     */
    private fun checkFlaggedApis(
        vararg sourceFiles: TestFile,
        previouslyReleasedApi: String,
        expectedPublicApi: String,
        expectedPublicApiMinusFlaggedApi: String,
        expectedPublicApiMinusFlaggedApiIssues: String = "",
        expectedSystemApi: String,
        expectedSystemApiMinusFlaggedApi: String,
        expectedSystemApiMinusFlaggedApiFail: String = "",
        expectedSystemApiMinusFlaggedApiIssues: String = "",
    ) {
        data class Expectations(
            val expectedApi: String,
            val expectedFail: String = "",
            val expectedIssues: String = "",
        )
        val expectations =
            when (config.surface) {
                Surface.PUBLIC ->
                    when (config.flagged) {
                        Flagged.WITH ->
                            Expectations(
                                expectedApi = expectedPublicApi,
                            )
                        Flagged.WITHOUT ->
                            Expectations(
                                expectedApi = expectedPublicApiMinusFlaggedApi,
                                expectedIssues = expectedPublicApiMinusFlaggedApiIssues,
                            )
                    }
                Surface.SYSTEM ->
                    when (config.flagged) {
                        Flagged.WITH ->
                            Expectations(
                                expectedApi = expectedSystemApi,
                            )
                        Flagged.WITHOUT ->
                            Expectations(
                                expectedApi = expectedSystemApiMinusFlaggedApi,
                                expectedFail = expectedSystemApiMinusFlaggedApiFail,
                                expectedIssues = expectedSystemApiMinusFlaggedApiIssues,
                            )
                    }
            }

        check(
            // Enable API linting against the previous API; only report issues in changes to that
            // API.
            apiLint = previouslyReleasedApi,
            format = FileFormat.V2,
            sourceFiles =
                buildList {
                        addAll(sourceFiles)
                        addAll(annotationsList)
                    }
                    .toTypedArray(),
            api = expectations.expectedApi,
            expectedFail = expectations.expectedFail,
            expectedIssues = expectations.expectedIssues,
            extraArguments =
                arrayOf(ARG_HIDE_PACKAGE, "android.annotation", "--warning", "UnflaggedApi") +
                    config.extraArguments,
        )
    }

    @Test
    fun `Basic test that FlaggedApi annotated items can be hidden`() {
        checkFlaggedApis(
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import android.annotation.SystemApi;

                    public class Foo {
                        @FlaggedApi("foo/bar")
                        public void flaggedPublicApi() {}

                        /** @hide */
                        @SystemApi
                        @FlaggedApi("foo/bar")
                        public void flaggedSystemApi() {}
                    }
                """
            ),
            previouslyReleasedApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                      }
                    }
                """,
            expectedPublicApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method @FlaggedApi("foo/bar") public void flaggedPublicApi();
                      }
                    }
                """,
            expectedPublicApiMinusFlaggedApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                      }
                    }
                """,
            expectedSystemApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method @FlaggedApi("foo/bar") public void flaggedSystemApi();
                      }
                    }
                """,
            expectedSystemApiMinusFlaggedApi =
                """
                    // Signature format: 2.0
                """,
        )
    }

    @Test
    fun `Test that cross references are handled correctly when flagged APIs are hidden`() {
        checkFlaggedApis(
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import android.annotation.SystemApi;

                    @FlaggedApi("foo/bar")
                    public class Foo {
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import android.annotation.SystemApi;

                    public class Bar {
                        /** @hide */
                        @SystemApi
                        @FlaggedApi("foo/bar")
                        public void flaggedSystemApi(@android.annotation.NonNull Foo foo) {}
                    }
                """
            ),
            previouslyReleasedApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                        ctor public Bar();
                      }
                    }
                """,
            expectedPublicApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                        ctor public Bar();
                      }
                      @FlaggedApi("foo/bar") public class Foo {
                        ctor public Foo();
                      }
                    }
                """,
            expectedPublicApiMinusFlaggedApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                        ctor public Bar();
                      }
                    }
                """,
            expectedSystemApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                        method @FlaggedApi("foo/bar") public void flaggedSystemApi(@NonNull test.pkg.Foo);
                      }
                    }
                """,
            expectedSystemApiMinusFlaggedApi =
                """
                    // Signature format: 2.0
                """,
            expectedSystemApiMinusFlaggedApiIssues = "",
        )
    }

    @Test
    fun `Test that method overrides are handled correctly when flagged APIs are hidden`() {
        checkFlaggedApis(
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import android.annotation.SystemApi;

                    public class Foo {
                        @FlaggedApi("foo/bar")
                        public void flaggedMethod() {}

                        /** @hide */
                        @SystemApi
                        @FlaggedApi("foo/bar")
                        public void systemFlaggedMethod() {}
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import android.annotation.SystemApi;

                    public class Bar extends Foo {
                        @Override
                        public void flaggedMethod() {}

                        /** @hide */
                        @SystemApi
                        @Override
                        public void systemFlaggedMethod() {}
                    }
                """
            ),
            previouslyReleasedApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar extends test.pkg.Foo {
                        ctor public Bar();
                      }
                      public class Foo {
                        ctor public Foo();
                      }
                    }
                """,
            expectedPublicApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar extends test.pkg.Foo {
                        ctor public Bar();
                      }
                      public class Foo {
                        ctor public Foo();
                        method @FlaggedApi("foo/bar") public void flaggedMethod();
                      }
                    }
                """,
            // This should not include flaggedMethod(). As overrides of flagged methods do not need
            // to themselves be flagged then removing flagged methods should remove the overrides
            // too.
            expectedPublicApiMinusFlaggedApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar extends test.pkg.Foo {
                        ctor public Bar();
                        method public void flaggedMethod();
                      }
                      public class Foo {
                        ctor public Foo();
                      }
                    }
                """,
            // This should be empty.
            expectedPublicApiMinusFlaggedApiIssues =
                """
                    src/test/pkg/Bar.java:8: warning: New API must be flagged with @FlaggedApi: method test.pkg.Bar.flaggedMethod() [UnflaggedApi]
                """,
            expectedSystemApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method @FlaggedApi("foo/bar") public void systemFlaggedMethod();
                      }
                    }
                """,
            // This should not include systemFlaggedMethod(). As overrides of flagged methods do not
            // need to themselves be flagged then removing flagged methods should remove the
            // overrides too. That would leave this empty apart from the signature header.
            expectedSystemApiMinusFlaggedApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar extends test.pkg.Foo {
                        method public void systemFlaggedMethod();
                      }
                    }
                """,
            expectedSystemApiMinusFlaggedApiIssues =
                """
                    src/test/pkg/Bar.java:13: warning: New API must be flagged with @FlaggedApi: method test.pkg.Bar.systemFlaggedMethod() [UnflaggedApi]
                """,
        )
    }

    @Test
    fun `Test that annotated class members are handled correctly when flagged APIs are hidden`() {
        checkFlaggedApis(
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import android.annotation.SystemApi;

                    /**
                     * @hide
                     */
                    @FlaggedApi("foo/bar")
                    @SystemApi
                    public class Foo {
                        /**
                         * @hide
                         */
                        @SystemApi
                        public Foo() {}

                        /**
                         * @hide
                         */
                        @SystemApi
                        public void method() {}
                    }
                """
            ),
            previouslyReleasedApi =
                """
                    // Signature format: 2.0
                """,
            expectedPublicApi =
                """
                    // Signature format: 2.0
                """,
            expectedPublicApiMinusFlaggedApi =
                """
                    // Signature format: 2.0
                """,
            expectedSystemApi =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @FlaggedApi("foo/bar") public class Foo {
                        ctor public Foo();
                        method public void method();
                      }
                    }
                """,
            expectedSystemApiMinusFlaggedApi =
                """
                    // Signature format: 2.0
                """,
            // There should be no lint errors or issues.
            expectedSystemApiMinusFlaggedApiFail = DefaultLintErrorMessage,
            expectedSystemApiMinusFlaggedApiIssues =
                """
                    src/test/pkg/Foo.java:16: error: Attempting to unhide constructor test.pkg.Foo(), but surrounding class test.pkg.Foo is hidden and should also be annotated with @android.annotation.SystemApi [ShowingMemberInHiddenClass]
                    src/test/pkg/Foo.java:22: error: Attempting to unhide method test.pkg.Foo.method(), but surrounding class test.pkg.Foo is hidden and should also be annotated with @android.annotation.SystemApi [ShowingMemberInHiddenClass]
                """,
        )
    }
}
