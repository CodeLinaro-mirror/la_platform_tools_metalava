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
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
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

    @SupportedInputFormats(InputFormat.JAVA)
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
                "MAIN_SRC/src/test/pkg/Test.java:5:15: error: Could not resolve a field called 'Flags.FLAG' as could not find a field called 'FLAG' in 'class test.pkg.Flags' [InvalidJavadocExpr]"
        )
    }

    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
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
                "MAIN_SRC/src/test/pkg/Test.java:5:15: error: Could not resolve a field called 'Flags.FLAG' as could not find a field called 'FLAG' in 'class test.pkg.Flags' [InvalidJavadocExpr]"
        )
    }

    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
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

    /**
     * Verify that when a flag is reverted (`REVERT`), an `@if` block tag wrapping `:@deprecated`
     * and `:@see` omits the `true` branch, emits the `else` branch, and does not mark the item as
     * deprecated.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test @if block tag with deprecated and see when flag reverted`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        /**
                         * Summary.
                         *
                         * @if (flag(Flags.FLAG)) {
                         *     :@see Replacement
                         *     :@deprecated Use {@link Replacement} instead.
                         * } else {
                         *     :@see Existing
                         * }
                         */
                        public class Test {
                        }
                    """,
                ),
                java(
                    """
                        package test.pkg;

                        public class Replacement {}
                    """,
                ),
                java(
                    """
                        package test.pkg;

                        public class Existing {}
                    """,
                ),
                java(
                    """
                        package test.pkg;

                        public class Flags {
                            public static final String FLAG = "flag";
                        }
                    """,
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
                                ),
                            ),
                        ),
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            // Access `selectedApi` first because `SourceSelectedApi` lazily propagates
            // `@deprecated` from Javadoc into the item's deprecation state.
            testClass.selectedApi
            testClass.assertNotDeprecated()
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Summary.
                         * @see test.pkg.Existing Existing
                         */
                    """,
            )
        }
    }

    /**
     * Verify that when a flag is kept (`KEEP`), an `@if` block tag wrapping `:@deprecated` and
     * `:@see` emits the `true` branch and marks the item as explicitly deprecated via Javadoc.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test @if block tag with deprecated and see when flag kept`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        /**
                         * Summary.
                         *
                         * @if (flag(Flags.FLAG)) {
                         *     :@see Replacement
                         *     :@deprecated Use {@link Replacement} instead.
                         * } else {
                         *     :@see Existing
                         * }
                         */
                        public class Test {
                        }
                    """,
                ),
                java(
                    """
                        package test.pkg;

                        public class Replacement {}
                    """,
                ),
                java(
                    """
                        package test.pkg;

                        public class Existing {}
                    """,
                ),
                java(
                    """
                        package test.pkg;

                        public class Flags {
                            public static final String FLAG = "flag";
                        }
                    """,
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
                                ),
                            ),
                        ),
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            // Access `selectedApi` first because `SourceSelectedApi` lazily propagates
            // `@deprecated` from Javadoc into the item's deprecation state.
            testClass.selectedApi
            testClass.assertExplicitlyDeprecated()
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Summary.
                         *
                         * @see test.pkg.Replacement Replacement
                         * @deprecated Use {@link test.pkg.Replacement Replacement} instead.
                         */
                    """,
            )
        }
    }

    /**
     * Verify that attempting to place `@see` block tags inside an inline `{@if ...}` tag reports
     * `InvalidJavadoc` issues and does not leak inactive `@see` tags or trailing `}}` into printed
     * documentation (b/479490213).
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test block tag inside inline if tag reports InvalidJavadoc`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Test {
                            /**
                             * {@if (flag(Flags.FLAG)) {
                             *     Wholly new Javadoc for the method, with no see tags.
                             * } else {
                             *     The old Javadoc for the method.
                             *
                             *     @see #someOtherMethod()
                             *     @see #anotherMethod()
                             * }}
                             */
                            public void foo() {}

                            public void someOtherMethod() {}

                            public void anotherMethod() {}
                        }
                    """,
                ),
                java(
                    """
                        package test.pkg;

                        public class Flags {
                            public static final String FLAG = "flag";
                        }
                    """,
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
                                ),
                            ),
                        ),
                ),
        ) {
            val fooMethod = codebase.assertClass("test.pkg.Test").assertMethod("foo", emptyList())
            fooMethod.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /** Wholly new Javadoc for the method, with no see tags. */
                    """,
            )

            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Test.java:10:12: warning: block tag '@see' cannot be used inside an inline tag (ErrorWhenNew) [InvalidJavadoc]
                    MAIN_SRC/src/test/pkg/Test.java:11:12: warning: block tag '@see' cannot be used inside an inline tag (ErrorWhenNew) [InvalidJavadoc]
                """,
            )
        }
    }

    /**
     * Verify that an invalid surface name in `surface("...")` reports `InvalidJavadocExpr` and
     * evaluates to `false`.
     */
    @SupportedInputFormats(InputFormat.JAVA, InputFormat.KOTLIN)
    @Test
    fun `Test conditional doc with invalid surface() name`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * {@if (surface("InvalidName")) {True.} else {False.}}
                     */
                    public class Test {
                    }
                """,
            ),
            kotlin(
                """
                    package test.pkg

                    /**
                     * {@if (surface("InvalidName")) {True.} else {False.}}
                     */
                    class Test
                """,
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val ext = if (inputFormat == InputFormat.JAVA) "java" else "kt"
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /** False. */
                    """,
            )
            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Test.$ext:4:18: error: invalid surface name 'InvalidName', must match '[a-z-]+' [InvalidJavadocExpr]
                """,
            )
        }
    }

    /**
     * Verify `surface("name")` evaluation across a multi-level surface hierarchy (`public` <-
     * `system` <- `module`) in both Java and Kotlin comments.
     */
    @SupportedInputFormats(InputFormat.JAVA, InputFormat.KOTLIN)
    @Test
    fun `Test conditional doc with surface() on extending hierarchy`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        /**
                         * {@if (surface("public")) {Public. } else {NoPublic. }}{@if (surface("system")) {System. } else {NoSystem. }}{@if (surface("module")) {Module. } else {NoModule. }}{@if (surface("other")) {Other.} else {NoOther.}}
                         *
                         * @if (surface("system")) {
                         *     :@see Helper
                         * }
                         */
                        public class Test {
                        }
                    """,
                ),
                java(
                    """
                        package test.pkg;

                        public class Helper {}
                    """,
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        /**
                         * {@if (surface("public")) {Public. } else {NoPublic. }}{@if (surface("system")) {System. } else {NoSystem. }}{@if (surface("module")) {Module. } else {NoModule. }}{@if (surface("other")) {Other.} else {NoOther.}}
                         *
                         * @if (surface("system")) {
                         *     :@see Helper
                         * }
                         */
                        class Test

                        class Helper
                    """,
                ),
            ),
            testFixture =
                TestFixture(
                    apiSurfaceRules = TestableApiSurfaces.publicSystemModuleRules,
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * Public. System. Module. NoOther.
                         * @see test.pkg.Helper Helper
                         */
                    """,
            )
        }
    }

    /**
     * Verify `surface("name")` evaluation on a standalone surface that folds in `public` so
     * `contents` has no effect on behavior.
     */
    @SupportedInputFormats(InputFormat.JAVA, InputFormat.KOTLIN)
    @Test
    fun `Test conditional doc with surface() on standalone surface`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * {@if (surface("public")) {Public. } else {NoPublic. }}{@if (surface("standalone")) {Standalone. } else {NoStandalone. }}{@if (surface("other")) {Other.} else {NoOther.}}
                     */
                    public class Test {
                    }
                """,
            ),
            kotlin(
                """
                    package test.pkg

                    /**
                     * {@if (surface("public")) {Public. } else {NoPublic. }}{@if (surface("standalone")) {Standalone. } else {NoStandalone. }}{@if (surface("other")) {Other.} else {NoOther.}}
                     */
                    class Test
                """,
            ),
            testFixture =
                TestFixture(
                    apiSurfaceRules = TestableApiSurfaces.publicStandaloneRules,
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /** Public. Standalone. NoOther. */
                    """,
            )
        }
    }
}
