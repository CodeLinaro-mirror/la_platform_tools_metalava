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
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runners.Parameterized

private val annotationsList = listOf(systemApiSource, flaggedApiSource, nonNullSource)

private const val FULLY_QUALIFIED_SYSTEM_API_SURFACE_ANNOTATION =
    "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)"

private const val FULLY_QUALIFIED_MODULE_LIB_API_SURFACE_ANNOTATION =
    "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.MODULE_LIBRARIES)"

@Suppress("JavadocDeclaration")
class FlaggedApiTest(private val config: Configuration) : DriverTest() {

    /** The configuration of the test. */
    data class Configuration(
        val surface: Surface,
        val flagged: Flagged,
    ) {
        val extraArguments = (surface.args + flagged.args)

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
        WITHOUT("without  flagged api", listOf(ARG_REVERT_ANNOTATION, ANDROID_FLAGGED_API)),

        /**
         * Represents an API without flagged APIs apart from those flagged APIs that are part of
         * feature `foo_bar`.
         */
        WITHOUT_APART_FROM_FOO_BAR_APIS(
            "without flagged api, with foo_bar",
            WITHOUT.args +
                listOf(ARG_REVERT_ANNOTATION, """!$ANDROID_FLAGGED_API("test.pkg.flags.foo_bar")""")
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

        /**
         * Regular expression that matches a FlaggedApi annotation in a signature file. It is not
         * fully qualified as the annotation is shortened in signature files. It includes the
         * following white space as this is used to remove the annotation by replacing the matched
         * text with an empty string.
         */
        val flaggedApiInSignatureRegex = """@FlaggedApi\([^)]+\) """.toRegex()

        /**
         * Regular expression that matches a FlaggedApi annotation in a stubs file. It is fully
         * qualified as annotations are fully qualified in stub files. It includes the following
         * newline or space as this is used to remove the annotation by replacing the matched text
         * with an empty string.
         */
        val flaggedApiInStubsRegex = """@android\.annotation\.FlaggedApi\([^)]+\)[\n ]""".toRegex()

        private val flagsFile =
            java(
                """
                package test.pkg.flags;

                /** @hide */
                public class Flags {
                    public static final String FLAG_FOO_BAR = "test.pkg.flags.foo_bar";
                }
            """
            )
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
        val expectedApiVersions: String = "",
    )

    /**
     * Check the result of generating APIs with and without flagged apis for both public and system
     * API surfaces.
     */
    private fun checkFlaggedApis(
        vararg sourceFiles: TestFile,
        extraArguments: Array<String> = emptyArray(),
        previouslyReleasedApi: Map<Surface, String> = emptyMap(),
        previouslyReleasedRemovedApi: Map<Surface, String> = emptyMap(),
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
                            // Remove any FlaggedApi annotations from the signature files
                            expectedApi = it.expectedApi.replace(flaggedApiInSignatureRegex, ""),
                            // Remove any FlaggedApi annotations from the stubs files
                            expectedStubs =
                                it.expectedStubs
                                    .map {
                                        val copy = TestFile()
                                        copy.contents =
                                            it.contents.replace(flaggedApiInStubsRegex, "")
                                        copy.targetRelativePath = it.targetRelativePath
                                        copy
                                    }
                                    .toTypedArray()
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

        // Get the surface for which this test is currently being run.
        val surface = config.surface

        // Get the previously released API surface specific to the surface being tested.
        val specificPreviouslyReleasedApi = previouslyReleasedApi[surface] ?: ""

        // Get the lists of API (and removed API) from the narrowest API surface (i.e. public) to
        // the widest (i.e. module-lib).
        val previouslyReleasedApiList = contributingSurfaces(previouslyReleasedApi)
        val previouslyReleasedRemovedApiList = contributingSurfaces(previouslyReleasedRemovedApi)

        val (apiVersionsArgs, apiVersionsFile) =
            if (expectations.expectedApiVersions != "") {
                val apiVersionsXmlFile = temporaryFolder.newFile("api-versions.xml")
                Pair(
                    arrayOf(
                        ARG_GENERATE_API_LEVELS,
                        apiVersionsXmlFile.path,
                        ARG_FIRST_VERSION,
                        "30",
                        ARG_CURRENT_VERSION,
                        "32",
                        ARG_CURRENT_CODENAME,
                        "Current",
                        ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS,
                    ),
                    apiVersionsXmlFile,
                )
            } else {
                Pair(emptyArray(), null)
            }

        val args =
            arrayOf(
                "--warning",
                "UnflaggedApi",
                *apiVersionsArgs,
                *config.extraArguments.toTypedArray(),
                *extraArguments,
            )

        check(
            // Enable API linting against the previous API; only report issues in changes to that
            // API. Only pass in the API for the surface whose test is currently run as API lint
            // does not support passing in a list.
            apiLint = specificPreviouslyReleasedApi,
            // Pass the previously released API as the API against which compatibility checks are
            // performed as that is what will determine the previous API to which a flagged API will
            // be reverted.
            checkCompatibilityApiReleasedList = previouslyReleasedApiList,
            checkCompatibilityRemovedApiReleasedList = previouslyReleasedRemovedApiList,
            format = FileFormat.V2,
            sourceFiles =
                buildList {
                        addAll(sourceFiles)
                        addAll(annotationsList)
                        add(flagsFile)
                        // Hide android.annotation classes.
                        add(KnownSourceFiles.androidAnnotationHide)
                    }
                    .toTypedArray(),
            api = expectations.expectedApi,
            stubFiles = expectations.expectedStubs,
            stubPaths = expectations.expectedStubPaths,
            expectedFail = expectations.expectedFail,
            expectedIssues = expectations.expectedIssues,
            // Do not include flags in the output but do not mark them as hide or removed.
            // This is needed to verify that the code to always inline the values of
            // FlaggedApi annotations even when not hidden or removed is working correctly.
            skipEmitPackages = listOf("test.pkg.flags"),
            extraArguments = args,
        )

        if (apiVersionsFile != null) {
            val expected = expectations.expectedApiVersions
            // Replace tabs with two spaces.
            val actual = apiVersionsFile.readText().replace("\t", "  ")
            assertEquals(expected.trimIndent(), actual.trimIndent())
        }
    }

    /**
     * Get the list of all surfaces in [apiSurfaces] that contribute to the [Surface] that is
     * currently under test; from the narrowest to the widest.
     *
     * e.g. When the surface under test is [Surface.PUBLIC] then this will return just the public
     * API surface, but when it is [Surface.SYSTEM] then this will return the public and system API
     * surfaces in that order.
     */
    private fun contributingSurfaces(apiSurfaces: Map<Surface, String>) =
        Surface.values().filter { it <= config.surface }.map { apiSurfaces[it] ?: "" }

    @Test
    fun `Basic test that FlaggedApi annotated items can be hidden`() {

        checkFlaggedApis(
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import android.annotation.SystemApi;
                    import test.pkg.flags.Flags;

                    public class Foo {
                        @FlaggedApi(Flags.FLAG_FOO_BAR)
                        public void flaggedPublicApi() {}

                        /** @hide */
                        @SystemApi
                        @FlaggedApi(Flags.FLAG_FOO_BAR)
                        public void flaggedSystemApi() {}
                    }
                """
            ),
            previouslyReleasedApi =
                mapOf(
                    Surface.PUBLIC to
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                              }
                            }
                        """,
                ),
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
                                    method @FlaggedApi("test.pkg.flags.foo_bar") public void flaggedPublicApi();
                                  }
                                }
                            """,
                        expectedStubs =
                            arrayOf(
                                java(
                                    """
                                    package test.pkg;
                                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                                    public class Foo {
                                    public Foo() { throw new RuntimeException("Stub!"); }
                                    @android.annotation.FlaggedApi("test.pkg.flags.foo_bar")
                                    public void flaggedPublicApi() { throw new RuntimeException("Stub!"); }
                                    }
                                """
                                ),
                            ),
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
                        expectedStubs =
                            arrayOf(
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
                        Surface.SYSTEM,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public class Foo {
                                    method @FlaggedApi("test.pkg.flags.foo_bar") public void flaggedSystemApi();
                                  }
                                }
                            """,
                        expectedStubs =
                            arrayOf(
                                java(
                                    """
                                        package test.pkg;
                                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                                        public class Foo {
                                        public Foo() { throw new RuntimeException("Stub!"); }
                                        @android.annotation.FlaggedApi("test.pkg.flags.foo_bar")
                                        public void flaggedPublicApi() { throw new RuntimeException("Stub!"); }
                                        /** @hide */
                                        @android.annotation.FlaggedApi("test.pkg.flags.foo_bar")
                                        public void flaggedSystemApi() { throw new RuntimeException("Stub!"); }
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
                        expectedStubs =
                            arrayOf(
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
    fun `Test that cross references are handled correctly when flagged APIs are hidden`() {
        checkFlaggedApis(
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import android.annotation.SystemApi;
                    import test.pkg.flags.Flags;

                    @FlaggedApi(Flags.FLAG_FOO_BAR)
                    public class Foo {
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import android.annotation.SystemApi;
                    import test.pkg.flags.Flags;

                    public class Bar {
                        /** @hide */
                        @SystemApi
                        @FlaggedApi(Flags.FLAG_FOO_BAR)
                        public void flaggedSystemApi(@android.annotation.NonNull Foo foo) {}
                    }
                """
            ),
            previouslyReleasedApi =
                mapOf(
                    Surface.PUBLIC to
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Bar {
                                ctor public Bar();
                              }
                            }
                        """,
                ),
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
                                  @FlaggedApi("test.pkg.flags.foo_bar") public class Foo {
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
                                    method @FlaggedApi("test.pkg.flags.foo_bar") public void flaggedSystemApi(@NonNull test.pkg.Foo);
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
                    import test.pkg.flags.Flags;

                    public class Foo {
                        @FlaggedApi(Flags.FLAG_FOO_BAR)
                        public void flaggedMethod() {}

                        /** @hide */
                        @SystemApi
                        @FlaggedApi(Flags.FLAG_FOO_BAR)
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
                mapOf(
                    Surface.PUBLIC to
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
                                    method @FlaggedApi("test.pkg.flags.foo_bar") public void flaggedMethod();
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
                                    method @FlaggedApi("test.pkg.flags.foo_bar") public void systemFlaggedMethod();
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
                        // Make sure that no FlaggedApi annotation appears in the stubs.
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
                    import test.pkg.flags.Flags;

                    /**
                     * @hide
                     */
                    @FlaggedApi(Flags.FLAG_FOO_BAR)
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
                mapOf(
                    Surface.PUBLIC to
                        """
                            // Signature format: 2.0
                        """,
                ),
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
                                  @FlaggedApi("test.pkg.flags.foo_bar") public final class Foo {
                                    ctor public Foo();
                                    method public void method();
                                  }
                                }
                            """,
                        expectedStubPaths =
                            arrayOf(
                                "test/pkg/Foo.java",
                            ),
                        expectedStubs =
                            arrayOf(
                                java(
                                    """
                                    package test.pkg;
                                    /**
                                     * @hide
                                     */
                                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                                    @android.annotation.FlaggedApi("test.pkg.flags.foo_bar")
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

    @Test
    fun `Test that previously released APIs which are now public and flagged are not removed`() {
        val stubsWithNewMembers =
            arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @android.annotation.FlaggedApi("test.pkg.flags.foo_bar")
                    public final class Foo {
                    public Foo() { throw new RuntimeException("Stub!"); }
                    public void method() { throw new RuntimeException("Stub!"); }
                    public final int field = 2; // 0x2
                    }
                """
                ),
            )
        val stubsWithoutNewMembers =
            arrayOf(
                java(
                    """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public final class Foo {
                    Foo() { throw new RuntimeException("Stub!"); }
                    }
                """
                ),
            )
        checkFlaggedApis(
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import test.pkg.flags.Flags;

                    @FlaggedApi(Flags.FLAG_FOO_BAR)
                    public final class Foo {
                        public Foo() {}
                        public void method() {}
                        /** @removed */
                        public void removedMethod() {}
                        public final int field = 2;
                    }
                """
            ),
            previouslyReleasedApi =
                mapOf(
                    // Use the same previously released API for each surface on which this test is
                    // being run. That is needed because this test verifies what happens when an API
                    // that was previously released in one API surface, is moved from that surface
                    // to public while adding some new members. If the class was previously
                    // released in the public API surface this tests what happens when a class is
                    // annotated with @FlaggedApi because it contains new members.
                    config.surface to
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public final class Foo {
                              }
                            }
                        """,
                ),
            previouslyReleasedRemovedApi =
                mapOf(
                    // See above for an explanation as to why this uses config.surface.
                    config.surface to
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public final class Foo {
                                method public void removedMethod();
                              }
                            }
                        """,
                ),
            expectationsList =
                listOf(
                    // The following public expectations verify what happens with a class that was
                    // previously released but which is annotated with FlaggedApi because it has new
                    // members.
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  @FlaggedApi("test.pkg.flags.foo_bar") public final class Foo {
                                    ctor public Foo();
                                    method public void method();
                                    field public final int field = 2; // 0x2
                                  }
                                }
                            """,
                        expectedStubs = stubsWithNewMembers,
                    ),
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITHOUT,
                        // Even without flagged APIs the class is still part of the public API
                        // because being annotated with @FlaggedApi does not cause it to be removed
                        // it was previously part of a released API. However, the new members did
                        // not exist in the previously released API so have been removed.
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public final class Foo {
                                  }
                                }
                            """,
                        expectedStubs = stubsWithoutNewMembers,
                    ),
                    // The following system expectations verify what happens with a class that was
                    // previously released as part of the system API but which is annotated with
                    // FlaggedApi because it has moved to public and has new members.
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITH,
                        // This is expected to be empty as the API has moved to public.
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        // The system API stubs with flagged APIs include the class and the new
                        // methods because while they are no longer system API they are public API
                        // and system API stubs include public API stubs.
                        expectedStubs = stubsWithNewMembers,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITHOUT,
                        // Even without flagged APIs the class is still part of the system API
                        // because being annotated with @FlaggedApi does not cause it to be removed
                        // it was previously part of a released API. However, the new members did
                        // not exist in the previously released API so have been removed.
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public final class Foo {
                                  }
                                }
                            """,
                        // The system API stubs without flagged APIs include the class but exclude
                        // the new methods because the class was present in the previously released
                        // system API but the methods were not.
                        expectedStubs = stubsWithoutNewMembers,
                    ),
                    // The following module lib expectations verify what happens with a class that
                    // was previously released as part of the module lib API but which is annotated
                    // with FlaggedApi because it has moved to public and has new members.
                    Expectations(
                        Surface.MODULE_LIB,
                        Flagged.WITH,
                        // This is expected to be empty as the API has moved to public.
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        // The module lib API stubs with flagged APIs include the class and the new
                        // methods because while they are no longer module lib API they are public
                        // API and module lib API stubs include public API stubs.
                        expectedStubs = stubsWithNewMembers,
                    ),
                    Expectations(
                        Surface.MODULE_LIB,
                        Flagged.WITHOUT,
                        // Even without flagged APIs the class is still part of the module lib API
                        // because being annotated with @FlaggedApi does not cause it to be removed
                        // it was previously part of a released API. However, the new members did
                        // not exist in the previously released API so have been removed.
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public final class Foo {
                                  }
                                }
                            """,
                        // The module lib API stubs without flagged APIs include the class but
                        // exclude the new methods because the class was present in the previously
                        // released module lib API but the methods were not.
                        expectedStubs = stubsWithoutNewMembers,
                    ),
                ),
        )
    }

    @Test
    fun `Test that previously released APIs which are now system and flagged are not removed`() {
        val stubsWithNewMembers =
            arrayOf(
                java(
                    """
                    package test.pkg;
                    /** @hide */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    @android.annotation.FlaggedApi("test.pkg.flags.foo_bar")
                    public final class Foo {
                    public Foo() { throw new RuntimeException("Stub!"); }
                    public void method() { throw new RuntimeException("Stub!"); }
                    public final int field = 2; // 0x2
                    }
                """
                ),
            )
        val stubsWithoutNewMembers =
            arrayOf(
                java(
                    """
                    package test.pkg;
                    /** @hide */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public final class Foo {
                    Foo() { throw new RuntimeException("Stub!"); }
                    }
                """
                ),
            )
        checkFlaggedApis(
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import android.annotation.SystemApi;
                    import test.pkg.flags.Flags;

                    /** @hide */
                    @SystemApi
                    @FlaggedApi(Flags.FLAG_FOO_BAR)
                    public final class Foo {
                        public Foo() {}
                        public void method() {}
                        /** @removed */
                        public void removedMethod() {}
                        public final int field = 2;
                    }
                """
            ),
            previouslyReleasedApi =
                mapOf(
                    // Use the same previously released API for each surface on which this test is
                    // being run. That is needed because this test verifies what happens when an API
                    // that was previously released in one API surface, is moved from that surface
                    // to system while adding some new members. If the class was previously
                    // released in the system API surface this tests what happens when a class is
                    // annotated with @FlaggedApi because it contains new members.
                    config.surface to
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public final class Foo {
                              }
                            }
                        """,
                ),
            previouslyReleasedRemovedApi =
                mapOf(
                    // See above for an explanation as to why this uses config.surface.
                    config.surface to
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public final class Foo {
                                method public void removedMethod();
                              }
                            }
                        """,
                ),
            expectationsList =
                listOf(
                    // The following system expectations verify what happens with a class that was
                    // previously released as part of the system API but which is annotated with
                    // FlaggedApi because it has new members.
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  @FlaggedApi("test.pkg.flags.foo_bar") public final class Foo {
                                    ctor public Foo();
                                    method public void method();
                                    field public final int field = 2; // 0x2
                                  }
                                }
                            """,
                        expectedStubs = stubsWithNewMembers,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITHOUT,
                        // Even without flagged APIs the class is still part of the system API
                        // because being annotated with @FlaggedApi does not cause it to be removed
                        // it was previously part of a released API. However, the new members did
                        // not exist in the previously released API so have been removed.
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public final class Foo {
                                  }
                                }
                            """,
                        expectedStubs = stubsWithoutNewMembers,
                    ),
                    // The following module lib expectations verify what happens with a class that
                    // was previously released as part of the module lib API but which is annotated
                    // with FlaggedApi because it has moved to system API and has new members.
                    Expectations(
                        Surface.MODULE_LIB,
                        Flagged.WITH,
                        // This is expected to be empty as the API has moved to system.
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        // The module lib API stubs with flagged APIs include the class and the new
                        // methods because while they are no longer module lib API they are public
                        // API and module lib API stubs include public API stubs.
                        expectedStubs = stubsWithNewMembers,
                    ),
                    Expectations(
                        Surface.MODULE_LIB,
                        Flagged.WITHOUT,
                        // Even without flagged APIs the class is still part of the module lib API
                        // because being annotated with @FlaggedApi does not cause it to be removed
                        // it was previously part of a released API. However, the new members did
                        // not exist in the previously released API so have been removed.
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public final class Foo {
                                  }
                                }
                            """,
                        // The module lib API stubs without flagged APIs include the class but
                        // exclude the new methods because the class was present in the previously
                        // released module lib API but the methods were not.
                        expectedStubs = stubsWithoutNewMembers,
                    ),
                ),
        )
    }

    @Test
    fun `Test interface fields behave correctly when flagged`() {
        val expectedStubPaths =
            arrayOf(
                "test/pkg/Foo.java",
            )

        val stubsWithFlaggedApi =
            arrayOf(
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public interface Foo {
                        @android.annotation.FlaggedApi("test.pkg.flags.foo_bar") public static final int CONSTANT = 1; // 0x1
                        }
                    """
                ),
            )

        val stubsWithoutFlaggedApi =
            arrayOf(
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public interface Foo {
                        }
                    """
                ),
            )

        checkFlaggedApis(
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import android.annotation.SystemApi;
                    import test.pkg.flags.Flags;

                    public interface Foo {
                        @FlaggedApi(Flags.FLAG_FOO_BAR)
                        int CONSTANT = 1;
                    }
                """
            ),
            previouslyReleasedApi =
                mapOf(
                    Surface.PUBLIC to
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public interface Foo {
                                }
                            }
                        """,
                ),
            expectationsList =
                listOf(
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public interface Foo {
                                    field @FlaggedApi("test.pkg.flags.foo_bar") public static final int CONSTANT = 1; // 0x1
                                  }
                                }
                            """,
                        expectedStubPaths = expectedStubPaths,
                        expectedStubs = stubsWithFlaggedApi,
                    ),
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public interface Foo {
                                  }
                                }
                            """,
                        expectedStubPaths = expectedStubPaths,
                        expectedStubs = stubsWithoutFlaggedApi,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubPaths = expectedStubPaths,
                        expectedStubs = stubsWithFlaggedApi,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubPaths = expectedStubPaths,
                        expectedStubs = stubsWithoutFlaggedApi,
                    ),
                ),
        )
    }

    @Test
    fun `Test that changing modifiers of public class can be reverted`() {
        val stubsWithFlaggedApis =
            arrayOf(
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        @android.annotation.FlaggedApi("test.pkg.flags.foo_bar")
                        public class Foo {
                        public Foo() { throw new RuntimeException("Stub!"); }
                        public void abstractMethod() { throw new RuntimeException("Stub!"); }
                        public void method(@android.annotation.Nullable java.lang.String p) { throw new RuntimeException("Stub!"); }
                        public native void nativeMethod();
                        public static int field;
                        }
                    """
                ),
            )

        val stubsWithoutFlaggedApis =
            arrayOf(
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public abstract class Foo {
                        protected Foo() { throw new RuntimeException("Stub!"); }
                        public abstract void abstractMethod();
                        public final void method(@android.annotation.Nullable java.lang.String p) { throw new RuntimeException("Stub!"); }
                        public void nativeMethod() { throw new RuntimeException("Stub!"); }
                        public static final int field;
                        static { field = 0; }
                        }
                    """
                ),
            )

        checkFlaggedApis(
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import test.pkg.flags.Flags;

                    @FlaggedApi(Flags.FLAG_FOO_BAR)
                    public class Foo {
                        public Foo() {}
                        public void abstractMethod();
                        public void method(@Nullable String p) {}
                        public native void nativeMethod();
                        public static int field;
                    }
                """
            ),
            extraArguments =
                arrayOf(
                    ARG_HIDE,
                    Issues.REMOVED_FINAL_STRICT.name,
                ),
            // The previously released public api.
            previouslyReleasedApi =
                mapOf(
                    Surface.PUBLIC to
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public abstract class Foo {
                                ctor protected Foo();
                                method public abstract void abstractMethod();
                                method public final void method(@Nullable String);
                                method public void nativeMethod();
                                field public static final int field;
                              }
                            }
                        """,
                ),
            expectationsList =
                listOf(
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  @FlaggedApi("test.pkg.flags.foo_bar") public class Foo {
                                    ctor public Foo();
                                    method public void abstractMethod();
                                    method public void method(@Nullable String);
                                    method public void nativeMethod();
                                    field public static int field;
                                  }
                                }
                            """,
                        expectedStubs = stubsWithFlaggedApis,
                        expectedApiVersions =
                            """
                                <?xml version="1.0" encoding="utf-8"?>
                                <api version="3" min="30">
                                  <class name="test/pkg/Foo" since="33">
                                    <method name="&lt;init>()V"/>
                                    <method name="abstractMethod()V"/>
                                    <method name="method(Ljava/lang/String;)V"/>
                                    <method name="nativeMethod()V"/>
                                    <field name="field"/>
                                  </class>
                                </api>
                            """,
                    ),
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public abstract class Foo {
                                    ctor protected Foo();
                                    method public abstract void abstractMethod();
                                    method public final void method(@Nullable String);
                                    method public void nativeMethod();
                                    field public static final int field;
                                  }
                                }
                            """,
                        expectedStubs = stubsWithoutFlaggedApis,
                        expectedApiVersions =
                            """
                                <?xml version="1.0" encoding="utf-8"?>
                                <api version="3" min="30">
                                  <class name="test/pkg/Foo" since="33">
                                    <method name="&lt;init>()V"/>
                                    <method name="abstractMethod()V"/>
                                    <method name="method(Ljava/lang/String;)V"/>
                                    <method name="nativeMethod()V"/>
                                    <field name="field"/>
                                  </class>
                                </api>
                            """,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubs = stubsWithFlaggedApis,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubs = stubsWithoutFlaggedApis,
                    ),
                    Expectations(
                        Surface.MODULE_LIB,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubs = stubsWithFlaggedApis,
                    ),
                    Expectations(
                        Surface.MODULE_LIB,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubs = stubsWithoutFlaggedApis,
                    ),
                ),
        )
    }

    @Test
    fun `Test that changing deprecated status of public class can be reverted`() {
        val stubsWithFlaggedApis =
            arrayOf(
                java(
                    """
                        package test.pkg;
                        /**
                         * A Bar class.
                         *
                         * @deprecated a multi-line, multi-sentence
                         * deprecation message. Deprecated for
                         * testing.
                         */
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        @Deprecated
                        @android.annotation.FlaggedApi("test.pkg.flags.foo_bar")
                        public class Bar {
                        /**
                         * A Bar constructor.
                         * @deprecated constructor
                         */
                        @Deprecated
                        public Bar() { throw new RuntimeException("Stub!"); }
                        /**
                         * A method.
                         * @deprecated method
                         */
                        @Deprecated
                        public void method() { throw new RuntimeException("Stub!"); }
                        /**
                         * A field.
                         * @deprecated field
                         */
                        @Deprecated public static int field;
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        @android.annotation.FlaggedApi("test.pkg.flags.foo_bar")
                        public class Foo {
                        Foo() { throw new RuntimeException("Stub!"); }
                        public void method(@android.annotation.Nullable java.lang.String p) { throw new RuntimeException("Stub!"); }
                        /** @deprecated */
                        @Deprecated public static int field;
                        }
                    """
                ),
            )

        val stubsWithoutFlaggedApis =
            arrayOf(
                java(
                    """
                        package test.pkg;
                        /**
                         * A Bar class.
                         *
                         */
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class Bar {
                        /**
                         * A Bar constructor.
                         */
                        public Bar() { throw new RuntimeException("Stub!"); }
                        /**
                         * A method.
                         */
                        public void method() { throw new RuntimeException("Stub!"); }
                        /**
                         * A field.
                         */
                        public static int field;
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class Foo {
                        Foo() { throw new RuntimeException("Stub!"); }
                        public void method(@android.annotation.Nullable java.lang.String p) { throw new RuntimeException("Stub!"); }
                        public static int field;
                        }
                    """
                ),
            )

        checkFlaggedApis(
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import test.pkg.flags.Flags;

                    /**
                     * A Bar class.
                     *
                     * @deprecated a multi-line, multi-sentence
                     * deprecation message. Deprecated for
                     * testing.
                     */
                    @FlaggedApi(Flags.FLAG_FOO_BAR)
                    public class Bar {
                        /**
                         * A Bar constructor.
                         * @deprecated constructor
                         */
                        @Deprecated
                        public Bar() {}
                        /**
                         * A method.
                         * @deprecated method
                         */
                        @Deprecated
                        public void method() {}
                        /**
                         * A field.
                         * @deprecated field
                         */
                        public @Deprecated static int field;
                    }
                """
            ),
            // This makes sure that existing deprecation annotations and tags are not discarded even
            // if annotated with @FlaggedApi.
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import test.pkg.flags.Flags;

                    /** @deprecated */
                    @FlaggedApi(Flags.FLAG_FOO_BAR)
                    @Deprecated
                    public class Baz {
                        /** @deprecated */
                        @Deprecated
                        public Baz() {}
                        /** @deprecated */
                        @Deprecated
                        public void method() {}
                        /** @deprecated */
                        public @Deprecated static int field;
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import test.pkg.flags.Flags;

                    @FlaggedApi(Flags.FLAG_FOO_BAR)
                    public class Foo {
                        private Foo() {}
                        public void method(@Nullable String p) {}
                        /** @deprecated */
                        public @Deprecated static int field;
                    }
                """
            ),
            // The previously released public api.
            previouslyReleasedApi =
                mapOf(
                    Surface.PUBLIC to
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public void method(@Nullable String);
                                field public static int field;
                              }
                              public class Bar {
                                ctor public Bar();
                                method public void method();
                                field public static int field;
                              }
                              @Deprecated public class Baz {
                                ctor @Deprecated public Baz();
                                method @Deprecated public void method();
                                field @Deprecated public static int field;
                              }
                            }
                        """,
                ),
            expectationsList =
                listOf(
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  @Deprecated @FlaggedApi("test.pkg.flags.foo_bar") public class Bar {
                                    ctor @Deprecated public Bar();
                                    method @Deprecated public void method();
                                    field @Deprecated public static int field;
                                  }
                                  @Deprecated @FlaggedApi("test.pkg.flags.foo_bar") public class Baz {
                                    ctor @Deprecated public Baz();
                                    method @Deprecated public void method();
                                    field @Deprecated public static int field;
                                  }
                                  @FlaggedApi("test.pkg.flags.foo_bar") public class Foo {
                                    method public void method(@Nullable String);
                                    field @Deprecated public static int field;
                                  }
                                }
                            """,
                        expectedStubs = stubsWithFlaggedApis,
                        expectedApiVersions =
                            """
                                <?xml version="1.0" encoding="utf-8"?>
                                <api version="3" min="30">
                                  <class name="test/pkg/Bar" since="33" deprecated="33">
                                    <method name="&lt;init>()V"/>
                                    <method name="method()V"/>
                                    <field name="field"/>
                                  </class>
                                  <class name="test/pkg/Baz" since="33" deprecated="33">
                                    <method name="&lt;init>()V"/>
                                    <method name="method()V"/>
                                    <field name="field"/>
                                  </class>
                                  <class name="test/pkg/Foo" since="33">
                                    <method name="method(Ljava/lang/String;)V"/>
                                    <field name="field" deprecated="33"/>
                                  </class>
                                </api>
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
                                    method public void method();
                                    field public static int field;
                                  }
                                  @Deprecated public class Baz {
                                    ctor @Deprecated public Baz();
                                    method @Deprecated public void method();
                                    field @Deprecated public static int field;
                                  }
                                  public class Foo {
                                    method public void method(@Nullable String);
                                    field public static int field;
                                  }
                                }
                            """,
                        expectedStubs = stubsWithoutFlaggedApis,
                        expectedApiVersions =
                            """
                                <?xml version="1.0" encoding="utf-8"?>
                                <api version="3" min="30">
                                  <class name="test/pkg/Bar" since="33">
                                    <method name="&lt;init>()V"/>
                                    <method name="method()V"/>
                                    <field name="field"/>
                                  </class>
                                  <class name="test/pkg/Baz" since="33" deprecated="33">
                                    <method name="&lt;init>()V"/>
                                    <method name="method()V"/>
                                    <field name="field"/>
                                  </class>
                                  <class name="test/pkg/Foo" since="33">
                                    <method name="method(Ljava/lang/String;)V"/>
                                    <field name="field"/>
                                  </class>
                                </api>
                            """,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubs = stubsWithFlaggedApis,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubs = stubsWithoutFlaggedApis,
                    ),
                    Expectations(
                        Surface.MODULE_LIB,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubs = stubsWithFlaggedApis,
                    ),
                    Expectations(
                        Surface.MODULE_LIB,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubs = stubsWithoutFlaggedApis,
                    ),
                ),
        )
    }

    @Test
    fun `Test that pulling method up into super class can be reverted`() {
        val stubsWithFlaggedApis =
            arrayOf(
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class Bar {
                        public Bar() { throw new RuntimeException("Stub!"); }
                        @android.annotation.FlaggedApi("test.pkg.flags.foo_bar")
                        public void method() { throw new RuntimeException("Stub!"); }
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class Foo extends test.pkg.Bar {
                        Foo() { throw new RuntimeException("Stub!"); }
                        }
                    """
                ),
            )

        val stubsWithoutFlaggedApis =
            arrayOf(
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class Bar {
                        public Bar() { throw new RuntimeException("Stub!"); }
                        }
                    """
                ),
                // TODO(b/337840740): Foo should have method().
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class Foo extends test.pkg.Bar {
                        Foo() { throw new RuntimeException("Stub!"); }
                        }
                    """
                ),
            )

        checkFlaggedApis(
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import test.pkg.flags.Flags;

                    public class Bar {
                        // This is flagged as the method was pulled up from Foo.
                        @FlaggedApi(Flags.FLAG_FOO_BAR)
                        public void method() {}
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    import android.annotation.FlaggedApi;
                    import test.pkg.flags.Flags;

                    public class Foo extends Bar {
                        private Foo() {}
                    }
                """
            ),
            // The previously released public api.
            previouslyReleasedApi =
                mapOf(
                    Surface.PUBLIC to
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Bar {
                                ctor public Bar();
                              }
                              public class Foo {
                                method public void method();
                              }
                            }
                        """,
                ),
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
                                    method @FlaggedApi("test.pkg.flags.foo_bar") public void method();
                                  }
                                  public class Foo extends test.pkg.Bar {
                                  }
                                }
                            """,
                        expectedStubs = stubsWithFlaggedApis,
                    ),
                    Expectations(
                        Surface.PUBLIC,
                        Flagged.WITHOUT,
                        expectedApi =
                            // TODO(b/337840740): Foo should have method().
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public class Bar {
                                    ctor public Bar();
                                  }
                                  public class Foo extends test.pkg.Bar {
                                  }
                                }
                            """,
                        expectedStubs = stubsWithoutFlaggedApis,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubs = stubsWithFlaggedApis,
                    ),
                    Expectations(
                        Surface.SYSTEM,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubs = stubsWithoutFlaggedApis,
                    ),
                    Expectations(
                        Surface.MODULE_LIB,
                        Flagged.WITH,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubs = stubsWithFlaggedApis,
                    ),
                    Expectations(
                        Surface.MODULE_LIB,
                        Flagged.WITHOUT,
                        expectedApi =
                            """
                                // Signature format: 2.0
                            """,
                        expectedStubs = stubsWithoutFlaggedApis,
                    ),
                ),
        )
    }
}
