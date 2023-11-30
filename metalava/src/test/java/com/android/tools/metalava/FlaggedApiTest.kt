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
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private val annotationsList = listOf(systemApiSource, flaggedApiSource, nonNullSource)

private const val FULLY_QUALIFIED_SYSTEM_API_SURFACE_ANNOTATION =
    "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)"

private const val FULLY_QUALIFIED_MODULE_LIB_API_SURFACE_ANNOTATION =
    "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.MODULE_LIBRARIES)"

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
            return "$surfaceText ${flagged.text}"
        }
    }

    /** The surfaces that this test will check. */
    enum class Surface(val args: List<String>) {
        PUBLIC(emptyList()),
        SYSTEM(
            listOf(
                ARG_SHOW_ANNOTATION,
                FULLY_QUALIFIED_SYSTEM_API_SURFACE_ANNOTATION,
            )
        ),
        MODULE_LIB(
            listOf(
                ARG_SHOW_ANNOTATION,
                FULLY_QUALIFIED_MODULE_LIB_API_SURFACE_ANNOTATION,
                ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION,
                FULLY_QUALIFIED_SYSTEM_API_SURFACE_ANNOTATION,
            )
        ),
    }

    /** The different configurations of the flagged API that this test will check. */
    enum class Flagged(val text: String, val args: List<String>) {
        /** Represents an API with all flagged APIs. */
        WITH("with flagged api", emptyList()),

        /** Represents an API without any flagged APIs. */
        WITHOUT("without  flagged api", listOf(ARG_HIDE_ANNOTATION, ANDROID_FLAGGED_API)),

        /**
         * Represents an API without flagged APIs apart from those flagged APIs that are part of
         * feature `foo/bar`.
         */
        WITHOUT_APART_FROM_FOO_BAR_APIS(
            "without flagged api, with foo/bar",
            WITHOUT.args +
                listOf(ARG_HIDE_ANNOTATION, """!android.annotation.FlaggedApi("foo/bar")""")
        ),
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

    @Suppress("ArrayInDataClass")
    data class Expectations(
        val surface: Surface,
        val flagged: Flagged,
        val expectedApi: String,
        val expectedFail: String = "",
        val expectedIssues: String = "",
        val expectedStubs: Array<TestFile> = emptyArray(),
        val expectedStubPaths: Array<String>? = null,
    )

    /**
     * Check the result of generating APIs with and without flagged apis for both public and system
     * API surfaces.
     */
    private fun checkFlaggedApis(
        vararg sourceFiles: TestFile,
        previouslyReleasedApi: String,
        expectationsList: List<Expectations>,
    ) {
        val transformedExpectationsList =
            expectationsList.flatMap {
                // All Expectations with flagged APIs are identical to the Expectations without
                // flagged APIs apart from those for feature flag `foo/bar`. So, this adds
                // additional Expectations without flagged APIs but with flagged APIs for feature
                // flag `foo/bar` flagged API that are identical to the "with flagged APIs" except
                // with for the expectedApi which does not include `@FlaggedApi` annotations.
                if (it.flagged == Flagged.WITH) {
                    listOf(
                        it,
                        it.copy(
                            flagged = Flagged.WITHOUT_APART_FROM_FOO_BAR_APIS,
                            expectedApi =
                                it.expectedApi.replace("""@FlaggedApi\([^)]+\) """.toRegex(), "")
                        ),
                    )
                } else {
                    listOf(it)
                }
            }

        val filterExpectations =
            transformedExpectationsList.filter {
                it.surface == config.surface && it.flagged == config.flagged
            }
        // singleOrNull will return null if called on a list with more than one item
        // which would ignore what is an error so check that explicitly first.
        if (filterExpectations.size > 1) {
            throw IllegalStateException(
                "Found ${filterExpectations.size} expectations that match config"
            )
        }
        val expectations = filterExpectations.singleOrNull() ?: return

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
            stubFiles = expectations.expectedStubs,
            stubPaths = expectations.expectedStubPaths,
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
            expectationsList =
                listOf(
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public class Foo {
                                    ctor public Foo();
                                    method @FlaggedApi("foo/bar") public void flaggedPublicApi();
                                  }
                                }
                            """,
                    ),
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public class Foo {
                                    ctor public Foo();
                                  }
                                }
                            """,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public class Foo {
                                    method @FlaggedApi("foo/bar") public void flaggedSystemApi();
                                  }
                                }
                            """,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0                        
                            """,
                    ),
                ),
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
            expectationsList =
                listOf(
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITH,
                        expectedApi =
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
                    ),
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public class Bar {
                                    ctor public Bar();
                                  }
                                }
                            """,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public class Bar {
                                    method @FlaggedApi("foo/bar") public void flaggedSystemApi(@NonNull test.pkg.Foo);
                                  }
                                }
                            """,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                    ),
                ),
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
            expectationsList =
                listOf(
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITH,
                        expectedApi =
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
                    ),
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITHOUT,
                        expectedApi =
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
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public class Foo {
                                    method @FlaggedApi("foo/bar") public void systemFlaggedMethod();
                                  }
                                }
                            """,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubPaths =
                            arrayOf(
                                "test/pkg/Bar.java",
                                "test/pkg/Foo.java",
                            ),
                        // Make sure that no flagged API appears in the stubs.
                        expectedStubs =
                            arrayOf(
                                java(
                                    """
                                    package test.pkg;
                                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                                    public class Bar extends test.pkg.Foo {
                                    public Bar() { throw new RuntimeException("Stub!"); }
                                    }
                                """
                                ),
                                java(
                                    """
                                    package test.pkg;
                                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                                    public class Foo {
                                    public Foo() { throw new RuntimeException("Stub!"); }
                                    }
                                """
                                ),
                            ),
                    ),
                    Expectations(
                        Surface.MODULE_LIB,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubs =
                            arrayOf(
                                java(
                                    """
                                    package test.pkg;
                                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                                    public class Bar extends test.pkg.Foo {
                                    public Bar() { throw new RuntimeException("Stub!"); }
                                    }
                                """
                                ),
                                java(
                                    """
                                    package test.pkg;
                                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                                    public class Foo {
                                    public Foo() { throw new RuntimeException("Stub!"); }
                                    }
                                """
                                ),
                            ),
                    ),
                ),
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
                    public final class Foo {
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
            expectationsList =
                listOf(
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                    ),
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  @FlaggedApi("foo/bar") public final class Foo {
                                    ctor public Foo();
                                    method public void method();
                                  }
                                }
                            """,
                        expectedStubPaths =
                            arrayOf(
                                "test/pkg/Foo.java",
                            ),
                        // Make sure that no flagged API appears in the stubs.
                        expectedStubs =
                            arrayOf(
                                java(
                                    """
                                    package test.pkg;
                                    /**
                                     * @hide
                                     */
                                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                                    public final class Foo {
                                    /**
                                     * @hide
                                     */
                                    public Foo() { throw new RuntimeException("Stub!"); }
                                    /**
                                     * @hide
                                     */
                                    public void method() { throw new RuntimeException("Stub!"); }
                                    }
                                """
                                ),
                            ),
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        // Make sure that no stub classes are generated at all.
                        expectedStubPaths = emptyArray(),
                    ),
                    // Check the module lib stubs without flagged apis.
                    Expectations(
                        Surface.MODULE_LIB,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        // There should be no stubs generated.
                        expectedStubPaths = emptyArray(),
                    ),
                ),
        )
    }
}
