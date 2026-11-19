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

import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.SurfaceSelectionRule
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlagAction
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.source.doc.DocContentPredicates
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.KnownJarFiles
import com.android.tools.metalava.testing.html
import com.android.tools.metalava.testing.java
import kotlin.test.assertTrue
import org.junit.Test

/** Common tests for references from within documentation comments. */
class CommonDocReferenceTest : BaseModelTest() {
    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
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
                "MAIN_SRC/test/pkg/package-info.java:5:11: warning: Could not resolve '#invalid(String,Number)' as there is no containing class (ErrorWhenNew) [UnresolvedLink]"
            )
        }
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test link tag in package-html`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Test {
                            public int field;
                            public void method(Test t, String s) {}
                        }
                    """
                ),
                html(
                    "src/test/pkg/package.html",
                    """
                        <HTML>
                        <BODY>
                        {@link String}
                        {@link String#length()}
                        {@link Test}
                        {@link Test#field}
                        {@link Test#method(Test,String)}
                        </BODY>
                        </HTML>
                    """
                ),
            ),
        ) {
            val testPackage = codebase.assertPackage("test.pkg")
            testPackage.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * {@link java.lang.String String}
                         * {@link java.lang.String#length() String.length()}
                         * {@link test.pkg.Test Test}
                         * {@link test.pkg.Test#field Test.field}
                         * {@link test.pkg.Test#method(test.pkg.Test,java.lang.String) Test.method(Test,String)}
                         */
                    """,
            )

            assertAndRemoveReportedIssues("")
        }
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test current-class member and uri-fragment links in package docs`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg1;
                        public class Test {}
                    """
                ),
                java(
                    "test/pkg1/package-info.java",
                    """
                        /**
                         * {@link #field}
                         * {@link ##pkg-heading pkg heading}
                         */
                        package test.pkg1;
                    """
                ),
                java(
                    """
                        package test.pkg2;
                        public class Test {}
                    """
                ),
                html(
                    "src/test/pkg2/package.html",
                    """
                        <HTML>
                        <BODY>
                        {@link #method()}
                        {@link ##html-heading html heading}
                        </BODY>
                        </HTML>
                    """
                ),
            ),
        ) {
            codebase
                .assertPackage("test.pkg1")
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@link #field}
                             * {@link ##pkg-heading pkg heading}
                             */
                        """,
                )
            codebase
                .assertPackage("test.pkg2")
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@link #method()}
                             * {@link ##html-heading html heading}
                             */
                        """,
                )

            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/test/pkg1/package-info.java:2:11: warning: Could not resolve '#field' as there is no containing class (ErrorWhenNew) [UnresolvedLink]
                    warning: Could not resolve '#method()' as there is no containing class (ErrorWhenNew) [UnresolvedLink]
                """
            )
        }
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test external package references`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Test {
                            /**
                             * {@link ext.pkg.ExternalClass}
                             * {@link ext.pkg.ExternalClass#field}
                             * {@link ext.pkg.ExternalClass#method(String, ext.pkg.ExternalParam, UnknownParam)}
                             * {@link ext.pkg.ExternalClass.method()}
                             * {@linkplain ext.pkg.ExternalClass#field}
                             *
                             * @see ext.pkg.ExternalClass#method()
                             * @throws ext.pkg.ExternalException if external fails.
                             * @throws UnknownException if unknown fails.
                             */
                            public void method() {}
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    externalPackagePrefixes = setOf("ext.pkg"),
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val testMethod = testClass.methods().single()
            testMethod.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * {@link ext.pkg.ExternalClass}
                         * {@link ext.pkg.ExternalClass#field}
                         * {@link ext.pkg.ExternalClass#method(java.lang.String,ext.pkg.ExternalParam,UnknownParam) ext.pkg.ExternalClass.method(String,ext.pkg.ExternalParam,UnknownParam)}
                         * {@link ext.pkg.ExternalClass.method()}
                         * {@linkplain ext.pkg.ExternalClass#field}
                         *
                         * @throws UnknownException if unknown fails.
                         * @throws ext.pkg.ExternalException if external fails.
                         * @see ext.pkg.ExternalClass#method()
                         */
                    """,
            )

            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Test.java:6:15: warning: Could not resolve a class called 'UnknownParam' in 'method test.pkg.Test.method()' (ErrorWhenNew) [UnresolvedLink]
                    MAIN_SRC/src/test/pkg/Test.java:12:16: warning: Could not resolve a class or type parameter called 'UnknownException' in 'method test.pkg.Test.method()' (ErrorWhenNew) [UnresolvedLink]
                """
            )
        }
    }

    /**
     * Verify how documentation references to annotations that are mapped in documentation stubs
     * (such as `android.annotation.Nullable` -> `androidx.annotation.Nullable`) and unmapped
     * annotations (such as `android.annotation.Hide`) are formatted when printed.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test mapped annotation references`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package android.annotation;
                        public @interface Nullable {}
                    """
                ),
                java(
                    """
                        package org.jetbrains.annotations;
                        public @interface Nullable {}
                    """
                ),
                java(
                    """
                        package android.annotation;
                        /** {@link android.annotation.IntDef#DEFAULT_FLAG} */
                        public @interface IntDef {
                            int DEFAULT_FLAG = 0;
                            boolean flag() default false;
                        }
                    """
                ),
                java(
                    """
                        package android.annotation;
                        public @interface Hide {
                            String value() default "";
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import android.annotation.Hide;
                        import android.annotation.IntDef;
                        import android.annotation.Nullable;
                        public class Test {
                            /**
                             * {@link Nullable}
                             * {@link android.annotation.Nullable}
                             * {@link org.jetbrains.annotations.Nullable}
                             * {@linkplain Nullable}
                             * {@link IntDef#DEFAULT_FLAG}
                             * {@link IntDef#flag}
                             * {@link IntDef#flag()}
                             * {@link android.annotation.IntDef#flag()}
                             * {@link android.annotation.IntDef.flag()}
                             * {@link Nullable##uri-fragment label}
                             * {@link Hide}
                             * {@link Hide#value()}
                             *
                             * @see Nullable
                             * @see android.annotation.Nullable
                             */
                            public void method() {}
                        }
                    """
                ),
            ),
        ) {
            codebase
                .assertClass("android.annotation.IntDef")
                .assertPrintedDocumentation(
                    expectedOutput = """/** {@link #DEFAULT_FLAG} */""",
                )

            val testClass = codebase.assertClass("test.pkg.Test")
            testClass
                .assertMethod("method", emptyList())
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@link androidx.annotation.Nullable Nullable}
                             * {@link androidx.annotation.Nullable}
                             * {@link androidx.annotation.Nullable}
                             * {@linkplain androidx.annotation.Nullable Nullable}
                             * {@link androidx.annotation.IntDef#DEFAULT_FLAG IntDef.DEFAULT_FLAG}
                             * {@link androidx.annotation.IntDef#flag IntDef.flag}
                             * {@link androidx.annotation.IntDef#flag() IntDef.flag()}
                             * {@link androidx.annotation.IntDef#flag()}
                             * {@link androidx.annotation.IntDef#flag()}
                             * {@link androidx.annotation.Nullable##uri-fragment label}
                             * {@link android.annotation.Hide Hide}
                             * {@link android.annotation.Hide#value() Hide.value()}
                             *
                             * @see androidx.annotation.Nullable Nullable
                             * @see androidx.annotation.Nullable
                             */
                        """,
                )

            assertAndRemoveReportedIssues("")
        }
    }

    /**
     * Verify how documentation references (`{@link}`, `{@linkplain}`, `@see`, `@throws`) to `@hide`
     * packages/classes/members and package-private classes/members are resolved, checked for
     * `HiddenDocReference`, and rewritten to `ERROR[...]` when printed.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test hidden references`() {
        runCodebaseTest(
            inputSet(
                java(
                    "hidden/pkg/package-info.java",
                    """
                        /** @hide */
                        package hidden.pkg;
                    """
                ),
                java(
                    """
                        package hidden.pkg;
                        public class InHiddenPackage {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        class PackagePrivateClass {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        /** @hide */
                        public class HiddenClass {
                            public int field;
                            public void method(PackagePrivateClass p) {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Other {
                            /** @hide */
                            public int hiddenField;
                            int packagePrivateField;
                            /** @hide */
                            public class HiddenNested {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Test {
                            /**
                             * {@link hidden.pkg}
                             * {@link hidden.pkg.InHiddenPackage}
                             * {@link PackagePrivateClass}
                             * {@link HiddenClass}
                             * {@link HiddenClass custom label}
                             * {@link HiddenClass#field}
                             * {@link HiddenClass#method}
                             * {@link HiddenClass#method(PackagePrivateClass)}
                             * {@link HiddenClass##uri-fragment label}
                             * {@link Other#hiddenField}
                             * {@link Other#packagePrivateField}
                             * {@link Other.HiddenNested}
                             * {@linkplain HiddenClass}
                             * {@linkplain HiddenClass custom label}
                             *
                             * @see HiddenClass
                             * @see HiddenClass custom label
                             * @throws HiddenException if hidden exception is thrown.
                             */
                            public void method() {}

                            /** @hide */
                            public static class HiddenException extends RuntimeException {}
                        }
                    """
                ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val testMethod = testClass.methods().single()

            // Hidden inline references (`{@link}`, `{@linkplain}`) are replaced with
            // `ERROR[<reason>: <link> <label>]` and hidden `@see` references with
            // `@see "ERROR[<reason>: <link> <label>]"`.
            testMethod.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * ERROR[hidden: hidden.pkg]
                         * ERROR[hidden: hidden.pkg.InHiddenPackage]
                         * ERROR[not public: PackagePrivateClass]
                         * ERROR[hidden: HiddenClass]
                         * ERROR[hidden: HiddenClass custom label]
                         * ERROR[hidden: HiddenClass#field]
                         * ERROR[hidden: HiddenClass#method]
                         * ERROR[hidden: HiddenClass#method(PackagePrivateClass)]
                         * ERROR[hidden: HiddenClass##uri-fragment label]
                         * ERROR[hidden: Other#hiddenField]
                         * ERROR[not public: Other#packagePrivateField]
                         * ERROR[hidden: Other.HiddenNested]
                         * ERROR[hidden: HiddenClass]
                         * ERROR[hidden: HiddenClass custom label]
                         *
                         * @throws test.pkg.Test.HiddenException if hidden exception is thrown.
                         * @see "ERROR[hidden: HiddenClass]"
                         * @see "ERROR[hidden: HiddenClass custom label]"
                         */
                    """,
            )

            // Line 11 (`HiddenClass#method(PackagePrivateClass)`) reports both the hidden
            // qualifying class and the package-private parameter class.
            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Test.java:4:15: warning: Package hidden.pkg is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:5:15: warning: Class hidden.pkg.InHiddenPackage is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:6:15: warning: Class test.pkg.PackagePrivateClass is not public (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:7:15: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:8:15: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:9:15: warning: Field test.pkg.HiddenClass.field is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:10:15: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:11:15: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:11:15: warning: Class test.pkg.PackagePrivateClass is not public (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:12:15: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:13:15: warning: Field test.pkg.Other.hiddenField is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:14:15: warning: Field test.pkg.Other.packagePrivateField is not public (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:15:15: warning: Class test.pkg.Other.HiddenNested is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:16:20: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:17:20: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:19:13: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:20:13: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:21:16: warning: Class test.pkg.Test.HiddenException is hidden (ErrorWhenNew) [HiddenDocReference]
                """
            )
        }
    }

    /**
     * Verify how `HiddenDocReference` checks documentation references to `@hide` annotations that
     * are mapped to different annotations in documentation stubs (e.g.
     * `android.annotation.Nullable` -> `androidx.annotation.Nullable`), versus unmapped `@hide`
     * annotations, explicitly `@hide` annotation fields, and mapped targets that are themselves
     * `@hide` in the codebase.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test hidden mapped annotation references`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package android.annotation;
                        /** @hide */
                        public @interface Nullable {}
                    """
                ),
                java(
                    """
                        package android.annotation;
                        /** @hide */
                        public @interface IntDef {
                            int DEFAULT_FLAG = 0;
                            /** @hide */
                            int HIDDEN_FLAG = 1;
                            boolean flag() default false;
                        }
                    """
                ),
                java(
                    """
                        package android.annotation;
                        /** @hide */
                        public @interface NonNull {}
                    """
                ),
                java(
                    """
                        package androidx.annotation;
                        public @interface NonNull {}
                    """
                ),
                java(
                    """
                        package android.annotation;
                        /** @hide */
                        public @interface IntRange {}
                    """
                ),
                java(
                    """
                        package androidx.annotation;
                        /** @hide */
                        public @interface IntRange {}
                    """
                ),
                java(
                    """
                        package android.annotation;
                        /** @hide */
                        public @interface Hide {
                            String value() default "";
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import android.annotation.Hide;
                        import android.annotation.IntDef;
                        import android.annotation.IntRange;
                        import android.annotation.NonNull;
                        import android.annotation.Nullable;
                        public class Test {
                            /**
                             * {@link Nullable}
                             * {@link IntDef#DEFAULT_FLAG}
                             * {@link IntDef#HIDDEN_FLAG}
                             * {@link IntDef#flag}
                             * {@link IntDef#flag()}
                             * {@link Nullable##uri-fragment label}
                             * {@link NonNull}
                             * {@link IntRange}
                             * {@link Hide}
                             * {@link Hide#value()}
                             */
                            public void method() {}
                        }
                    """
                ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass
                .assertMethod("method", emptyList())
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@link androidx.annotation.Nullable Nullable}
                             * {@link androidx.annotation.IntDef#DEFAULT_FLAG IntDef.DEFAULT_FLAG}
                             * ERROR[hidden: IntDef#HIDDEN_FLAG]
                             * {@link androidx.annotation.IntDef#flag IntDef.flag}
                             * {@link androidx.annotation.IntDef#flag() IntDef.flag()}
                             * {@link androidx.annotation.Nullable##uri-fragment label}
                             * {@link androidx.annotation.NonNull NonNull}
                             * ERROR[hidden: IntRange]
                             * ERROR[hidden: Hide]
                             * ERROR[hidden: Hide#value()]
                             */
                        """,
                )

            // Only explicitly hidden fields (`IntDef#HIDDEN_FLAG`), mapped targets that are
            // themselves `@hide` in the codebase (`androidx.annotation.IntRange`), and unmapped
            // `@hide` annotations (`android.annotation.Hide`) report `HiddenDocReference`.
            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Test.java:11:15: warning: Field android.annotation.IntDef.HIDDEN_FLAG is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:16:15: warning: Class androidx.annotation.IntRange is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:17:15: warning: Class android.annotation.Hide is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:18:15: warning: Class android.annotation.Hide is hidden (ErrorWhenNew) [HiddenDocReference]
                """
            )
        }
    }

    /**
     * Verify that documentation on `@hide` classes, members of `@hide` classes, and `@hide` methods
     * in public classes does not report `HiddenDocReference` when referencing hidden targets, even
     * when printing strips `@hide` tags.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test hidden referencing items`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        /** @hide */
                        public class HiddenTarget {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        /**
                         * {@link #method()}
                         * {@link HiddenTarget}
                         * @hide
                         */
                        public class HiddenReferencer {
                            /** {@link HiddenTarget} */
                            public void method() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Test {
                            /**
                             * {@link HiddenTarget}
                             * @hide
                             */
                            public void hiddenMethod() {}
                        }
                    """
                ),
            ),
        ) {
            val hiddenReferencer = codebase.assertClass("test.pkg.HiddenReferencer")
            hiddenReferencer.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * {@link #method()}
                         * {@link test.pkg.HiddenTarget HiddenTarget}
                         */
                    """,
            )
            hiddenReferencer
                .methods()
                .single()
                .assertPrintedDocumentation(
                    expectedOutput = """/** {@link test.pkg.HiddenTarget HiddenTarget} */""",
                )

            val testClass = codebase.assertClass("test.pkg.Test")
            testClass
                .assertMethod("hiddenMethod", emptyList())
                .assertPrintedDocumentation(
                    expectedOutput = """/** {@link test.pkg.HiddenTarget HiddenTarget} */""",
                )

            assertAndRemoveReportedIssues("")
        }
    }

    /**
     * Verify that documentation on public API items can reference `@DocOnly` classes and their
     * members without reporting `HiddenDocReference`.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test doconly references`() {
        // Configure `@test.pkg.DocOnly` with `DOC_ONLY` effect (legacy `@doconly` Javadoc tags
        // are unsupported).
        val apiSurfaces = ApiSurfaces.create()
        val rulesByName = mapOf("main" to listOf(SurfaceSelectionRule.unannotated))
        val variantRules =
            listOf(
                SurfaceSelectionRule.createAnnotationRule(
                    "test.pkg.DocOnly",
                    effect = SurfaceSelectionRule.Effect.DOC_ONLY,
                ),
            )
        val apiSurfaceRules = ApiSurfaceRules(apiSurfaces, rulesByName, variantRules)

        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        @Retention(RetentionPolicy.SOURCE)
                        @interface DocOnly {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        @DocOnly
                        public class DocOnlyClass {
                            public int field;
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Test {
                            /**
                             * {@link DocOnlyClass}
                             * {@link DocOnlyClass#field}
                             */
                            public void publicMethod() {}
                        }
                    """
                ),
            ),
            testFixture = TestFixture(apiSurfaceRules = apiSurfaceRules),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass
                .assertMethod("publicMethod", emptyList())
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@link test.pkg.DocOnlyClass DocOnlyClass}
                             * {@link test.pkg.DocOnlyClass#field DocOnlyClass.field}
                             */
                        """,
                )

            assertAndRemoveReportedIssues("")
        }
    }

    /**
     * Verify that documentation on public API items reports `HiddenDocReference` with reason
     * `"removed"` when referencing `@removed` items, whereas documentation on `@removed` items
     * skips checking.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test removed references`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        /** @removed */
                        public class RemovedClass {
                            public int field;
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Test {
                            /**
                             * {@link RemovedClass}
                             * {@link RemovedClass#field}
                             */
                            public void publicMethod() {}

                            /**
                             * {@link RemovedClass}
                             * @removed
                             */
                            public void removedMethod() {}
                        }
                    """
                ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            testClass
                .assertMethod("publicMethod", emptyList())
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * ERROR[removed: RemovedClass]
                             * ERROR[removed: RemovedClass#field]
                             */
                        """,
                )
            testClass
                .assertMethod("removedMethod", emptyList())
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@link test.pkg.RemovedClass RemovedClass}
                             * @removed
                             */
                        """,
                )

            // Only `publicMethod()` reports `HiddenDocReference`; `removedMethod()` is itself
            // `@removed` and therefore not a documented item.
            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Test.java:4:15: warning: Class test.pkg.RemovedClass is removed (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:5:15: warning: Field test.pkg.RemovedClass.field is removed (ErrorWhenNew) [HiddenDocReference]
                """
            )
        }
    }

    /**
     * Verify that documentation on unflagged public API items reports `HiddenDocReference` and
     * rewrites links to `ERROR[<reason>: <link>...]` when referencing items in a wider API surface,
     * items guarded by an addition `@FlaggedApi`, or items scheduled for removal by `@FlaggedApi`.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test wider surface and flagged references`() {
        val apiSurfaces =
            ApiSurfaces.build {
                createSurface("public", isMain = true)
                createSurface("system", extends = "public")
            }
        val apiSurfaceRules =
            ApiSurfaceRules(
                apiSurfaces,
                mapOf(
                    "public" to listOf(SurfaceSelectionRule.unannotated),
                    "system" to
                        listOf(SurfaceSelectionRule.createAnnotationRule("test.pkg.SystemApi")),
                ),
                listOf(
                    SurfaceSelectionRule.createAnnotationRule(
                        "test.pkg.Removed",
                        effect = SurfaceSelectionRule.Effect.REMOVED,
                    ),
                ),
            )

        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        @Retention(RetentionPolicy.SOURCE)
                        @interface SystemApi {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        @Retention(RetentionPolicy.SOURCE)
                        @interface Removed {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        @SystemApi
                        public class SystemClass {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import android.annotation.FlaggedApi;
                        @FlaggedApi("test.pkg.flags.add_flag")
                        public class FlaggedAdditionClass {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import android.annotation.FlaggedApi;
                        @Removed
                        @FlaggedApi("test.pkg.flags.remove_flag")
                        public class FlaggedRemovalClass {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Test {
                            /**
                             * {@link SystemClass}
                             * {@link SystemClass custom label}
                             * {@link FlaggedAdditionClass}
                             * {@link FlaggedRemovalClass}
                             *
                             * @see SystemClass
                             */
                            public void publicMethod() {}
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    additionalClassPath = listOf(KnownJarFiles.stubAnnotationsJar),
                    apiSurfaceRules = apiSurfaceRules,
                    apiFlags =
                        ApiFlags(
                            listOf(
                                ApiFlag("test.pkg.flags.add_flag", ApiFlagAction.KEEP),
                                ApiFlag("test.pkg.flags.remove_flag", ApiFlagAction.KEEP),
                            )
                        ),
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass
                .assertMethod("publicMethod", emptyList())
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * ERROR[in a wider API surface (system) than public: SystemClass]
                             * ERROR[in a wider API surface (system) than public: SystemClass custom label]
                             * ERROR[guarded by @FlaggedApi(test.pkg.flags.add_flag): FlaggedAdditionClass]
                             * ERROR[scheduled for removal by @FlaggedApi(test.pkg.flags.remove_flag): FlaggedRemovalClass]
                             * @see "ERROR[in a wider API surface (system) than public: SystemClass]"
                             */
                        """,
                )

            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Test.java:4:15: warning: Class test.pkg.SystemClass is in a wider API surface (system) than public (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:5:15: warning: Class test.pkg.SystemClass is in a wider API surface (system) than public (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:6:15: warning: Class test.pkg.FlaggedAdditionClass is guarded by @FlaggedApi(test.pkg.flags.add_flag) (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:7:15: warning: Class test.pkg.FlaggedRemovalClass is scheduled for removal by @FlaggedApi(test.pkg.flags.remove_flag) (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:9:13: warning: Class test.pkg.SystemClass is in a wider API surface (system) than public (ErrorWhenNew) [HiddenDocReference]
                """
            )
        }
    }

    /**
     * Verify that URLs used inside `{@link}`, `{@linkplain}`, and `@see` tags report
     * `MalformedDocReference`, while `@see <a href="...">...</a>` HTML links are accepted without
     * warning.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test URL references in link and see tags`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    /**
                     * {@link https://example.com}
                     * {@link https://example.com Example}
                     * {@linkplain http://example.com/docs}
                     *
                     * @see https://example.com
                     * @see <a href="https://example.com">Example</a>
                     */
                    public class Test {}
                """,
            ),
        ) {
            codebase
                .assertClass("test.pkg.Test")
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@link https://example.com}
                             * {@link https://example.com Example}
                             * {@linkplain http://example.com/docs}
                             *
                             * @see https://example.com
                             * @see <a href="https://example.com">Example</a>
                             */
                        """,
                )

            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Test.java:3:11: warning: Malformed reference `https://example.com`, URLs are not valid in `@link`, use `<a href="https://example.com">...</a>` instead (ErrorWhenNew) [MalformedDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:4:11: warning: Malformed reference `https://example.com`, URLs are not valid in `@link`, use `<a href="https://example.com">...</a>` instead (ErrorWhenNew) [MalformedDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:5:16: warning: Malformed reference `http://example.com/docs`, URLs are not valid in `@linkplain`, use `<a href="http://example.com/docs">...</a>` instead (ErrorWhenNew) [MalformedDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:7:9: warning: Malformed reference `https://example.com`, URLs are not valid in `@see`, use `@see <a href="https://example.com">...</a>` instead (ErrorWhenNew) [MalformedDocReference]
                """,
            )
        }
    }

    /**
     * Verify how URI fragment references (`##<fragment>` and `<Class>##<fragment>`) with and
     * without labels (including trailing whitespace, mapped annotations, and `@see` inline-tag
     * labels) are formatted and checked.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test URI fragment references with and without labels`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package android.annotation;
                        /** @hide */
                        public @interface Nullable {}
                    """,
                ),
                java(
                    """
                        package test.pkg;
                        /**
                         * {@link ##sec   }
                         * {@link ##sec Section}
                         * {@link android.annotation.Nullable##sec}
                         *
                         * @see Test##sec
                         * @see Test##sec {@code codeLabel}
                         */
                        public class Test {}
                    """,
                ),
            ),
        ) {
            codebase
                .assertClass("test.pkg.Test")
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@link ##sec}
                             * {@link ##sec Section}
                             * {@link androidx.annotation.Nullable##sec}
                             *
                             * @see ##sec
                             * @see ##sec {@code codeLabel}
                             */
                        """,
                )

            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Test.java:3:11: warning: Malformed reference `##sec`, URI fragment reference requires a label (ErrorWhenNew) [MalformedDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:5:11: warning: Malformed reference `android.annotation.Nullable##sec`, URI fragment reference requires a label (ErrorWhenNew) [MalformedDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:7:9: warning: Malformed reference `Test##sec`, URI fragment reference requires a label (ErrorWhenNew) [MalformedDocReference]
                """,
            )
        }
    }

    /**
     * Verify that inline `{@if (flag(...))}` and block `@if (flag(...))` branches guard references
     * to unfinalized `@FlaggedApi` items when the branch's flag is unfinalized (`KEEP`), whereas
     * branches guarded only by a finalized flag (`FINALIZE`), wider-surface references, and
     * scheduled-removal references still report `HiddenDocReference` and rewrite links to
     * `ERROR[...]`.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test conditional if references to flagged APIs`() {
        val apiSurfaces =
            ApiSurfaces.build {
                createSurface("public", isMain = true)
                createSurface("system", extends = "public")
            }
        val apiSurfaceRules =
            ApiSurfaceRules(
                apiSurfaces,
                mapOf(
                    "public" to listOf(SurfaceSelectionRule.unannotated),
                    "system" to
                        listOf(SurfaceSelectionRule.createAnnotationRule("test.pkg.SystemApi")),
                ),
                listOf(
                    SurfaceSelectionRule.createAnnotationRule(
                        "test.pkg.Removed",
                        effect = SurfaceSelectionRule.Effect.REMOVED,
                    ),
                ),
            )

        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        @Retention(RetentionPolicy.SOURCE)
                        @interface SystemApi {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        @Retention(RetentionPolicy.SOURCE)
                        @interface Removed {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Flags {
                            public static final String ADD_FLAG = "test.pkg.flags.add_flag";
                            public static final String OTHER_KEEP_FLAG = "test.pkg.flags.other_keep_flag";
                            public static final String FINALIZED_FLAG = "test.pkg.flags.finalized_flag";
                            public static final String REVERTED_FLAG = "test.pkg.flags.reverted_flag";
                            public static final String REMOVE_FLAG = "test.pkg.flags.remove_flag";
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        @SystemApi
                        public class SystemClass {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import android.annotation.FlaggedApi;
                        @FlaggedApi(Flags.ADD_FLAG)
                        public class FlaggedAdditionClass {
                            public void action() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import android.annotation.FlaggedApi;
                        @FlaggedApi(Flags.ADD_FLAG)
                        public class FlaggedException extends RuntimeException {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import android.annotation.FlaggedApi;
                        @Removed
                        @FlaggedApi(Flags.REMOVE_FLAG)
                        public class FlaggedRemovalClass {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Test {
                            /**
                             * {@if (flag(Flags.ADD_FLAG)) {{@link FlaggedAdditionClass} and {@linkplain FlaggedAdditionClass#action()}}}
                             * {@if (flag(Flags.OTHER_KEEP_FLAG)) {{@link FlaggedAdditionClass}}}
                             * {@if (flag(Flags.FINALIZED_FLAG)) {{@if (flag(Flags.ADD_FLAG)) {{@link FlaggedAdditionClass}}}}}
                             * {@if (flag(Flags.REVERTED_FLAG)) {unused} else {{@if (flag(Flags.ADD_FLAG)) {{@link FlaggedAdditionClass}}}}}
                             *
                             * @if (flag(Flags.ADD_FLAG)) {
                             *     :@param x {@link FlaggedAdditionClass}
                             *     :@throws FlaggedException if {@link FlaggedAdditionClass} fails.
                             *     :@see FlaggedAdditionClass
                             * }
                             */
                            public void guardedMethod(int x) {}

                            /**
                             * {@if (flag(Flags.FINALIZED_FLAG)) {{@link FlaggedAdditionClass}}}
                             * {@if (flag(Flags.REVERTED_FLAG)) {unused} else {{@link FlaggedAdditionClass}}}
                             * {@if (flag(Flags.ADD_FLAG)) {{@link SystemClass}}}
                             * {@if (flag(Flags.ADD_FLAG)) {{@link FlaggedRemovalClass}}}
                             *
                             * @if (flag(Flags.FINALIZED_FLAG)) {
                             *     :@see FlaggedAdditionClass
                             * }
                             */
                            public void invalidGuardsMethod() {}
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    additionalClassPath = listOf(KnownJarFiles.stubAnnotationsJar),
                    apiSurfaceRules = apiSurfaceRules,
                    apiFlags =
                        ApiFlags(
                            listOf(
                                ApiFlag("test.pkg.flags.add_flag", ApiFlagAction.KEEP),
                                ApiFlag("test.pkg.flags.other_keep_flag", ApiFlagAction.KEEP),
                                ApiFlag("test.pkg.flags.finalized_flag", ApiFlagAction.FINALIZE),
                                ApiFlag("test.pkg.flags.reverted_flag", ApiFlagAction.REVERT),
                                ApiFlag("test.pkg.flags.remove_flag", ApiFlagAction.KEEP),
                            )
                        ),
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass
                .assertMethod("guardedMethod", listOf("int"))
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@link test.pkg.FlaggedAdditionClass FlaggedAdditionClass} and {@linkplain test.pkg.FlaggedAdditionClass#action() FlaggedAdditionClass.action()}
                             * {@link test.pkg.FlaggedAdditionClass FlaggedAdditionClass}
                             * {@link test.pkg.FlaggedAdditionClass FlaggedAdditionClass}
                             * {@link test.pkg.FlaggedAdditionClass FlaggedAdditionClass}
                             *
                             * @param x {@link test.pkg.FlaggedAdditionClass FlaggedAdditionClass}
                             * @throws test.pkg.FlaggedException if {@link test.pkg.FlaggedAdditionClass FlaggedAdditionClass} fails.
                             * @see test.pkg.FlaggedAdditionClass FlaggedAdditionClass
                             */
                        """,
                )

            testClass
                .assertMethod("invalidGuardsMethod", emptyList())
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * ERROR[guarded by @FlaggedApi(test.pkg.flags.add_flag): FlaggedAdditionClass]
                             * ERROR[guarded by @FlaggedApi(test.pkg.flags.add_flag): FlaggedAdditionClass]
                             * ERROR[in a wider API surface (system) than public: SystemClass]
                             * ERROR[scheduled for removal by @FlaggedApi(test.pkg.flags.remove_flag): FlaggedRemovalClass]
                             * @see "ERROR[guarded by @FlaggedApi(test.pkg.flags.add_flag): FlaggedAdditionClass]"
                             */
                        """,
                )

            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Test.java:18:50: warning: Class test.pkg.FlaggedAdditionClass is guarded by @FlaggedApi(test.pkg.flags.add_flag) (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:19:63: warning: Class test.pkg.FlaggedAdditionClass is guarded by @FlaggedApi(test.pkg.flags.add_flag) (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:20:44: warning: Class test.pkg.SystemClass is in a wider API surface (system) than public (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:21:44: warning: Class test.pkg.FlaggedRemovalClass is scheduled for removal by @FlaggedApi(test.pkg.flags.remove_flag) (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:24:18: warning: Class test.pkg.FlaggedAdditionClass is guarded by @FlaggedApi(test.pkg.flags.add_flag) (ErrorWhenNew) [HiddenDocReference]
                """
            )
        }
    }

    /**
     * Verify that when a previously released non-deprecated item is newly deprecated under an
     * unfinalized `@FlaggedApi`, unguarded references inside `@deprecated` to unfinalized
     * `@FlaggedApi` items are valid (because `CodebaseSnapshotTaker` strips `@deprecated` on
     * revert), whereas unguarded references in its main description, on items with a finalized
     * `@FlaggedApi`, or on items that were already deprecated in the previously released API still
     * report `HiddenDocReference` and rewrite to `ERROR[...]`.
     */
    @RequiresCapabilities(Capability.REVERTED_ITEMS)
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test deprecated section references on newly deprecated FlaggedApi items`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class PreviouslyUndeprecated {
                            public void methodByAnnotation() {}
                            public void methodByDocTagOnly() {}
                            public void methodWithFinalizedFlag() {}
                        }
                        public class OuterNewlyFlagged {
                            public void inheritedDeprecatedMethod() {}
                        }
                        @Deprecated
                        public class PreviouslyDeprecated {
                            @Deprecated
                            public void alreadyDeprecatedMethod() {}
                        }
                    """
                ),
            ),
            testFixture = TestFixture(checkSupportedInputFormats = false),
        ) {
            val previousCodebase = codebase
            runCodebaseTest(
                inputSet(
                    java(
                        """
                            package test.pkg;
                            public class Flags {
                                public static final String ADD_FLAG = "test.pkg.flags.add_flag";
                                public static final String FINALIZED_FLAG = "test.pkg.flags.finalized_flag";
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import android.annotation.FlaggedApi;
                            @FlaggedApi(Flags.ADD_FLAG)
                            public class FlaggedReplacement {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import android.annotation.FlaggedApi;
                            public class PreviouslyUndeprecated {
                                /**
                                 * Main unguarded: {@link FlaggedReplacement}.
                                 * Main conditional: {@if (flag(Flags.ADD_FLAG)) {{@link FlaggedReplacement}}}.
                                 *
                                 * @deprecated Use {@link FlaggedReplacement} instead.
                                 */
                                @FlaggedApi(Flags.ADD_FLAG)
                                @Deprecated
                                public void methodByAnnotation() {}

                                /**
                                 * Summary.
                                 *
                                 * @deprecated Use {@link FlaggedReplacement} instead.
                                 */
                                @FlaggedApi(Flags.ADD_FLAG)
                                public void methodByDocTagOnly() {}

                                /**
                                 * @deprecated Use {@link FlaggedReplacement} instead.
                                 */
                                @FlaggedApi(Flags.FINALIZED_FLAG)
                                @Deprecated
                                public void methodWithFinalizedFlag() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import android.annotation.FlaggedApi;
                            @FlaggedApi(Flags.ADD_FLAG)
                            public class OuterNewlyFlagged {
                                /**
                                 * @deprecated Use {@link FlaggedReplacement} instead.
                                 */
                                @Deprecated
                                public void inheritedDeprecatedMethod() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import android.annotation.FlaggedApi;
                            @FlaggedApi(Flags.ADD_FLAG)
                            @Deprecated
                            public class PreviouslyDeprecated {
                                /**
                                 * @deprecated Use {@link FlaggedReplacement} instead.
                                 */
                                @FlaggedApi(Flags.ADD_FLAG)
                                @Deprecated
                                public void alreadyDeprecatedMethod() {}
                            }
                        """
                    ),
                ),
                testFixture =
                    TestFixture(
                        additionalClassPath = listOf(KnownJarFiles.stubAnnotationsJar),
                        apiFlags =
                            ApiFlags(
                                listOf(
                                    ApiFlag("test.pkg.flags.add_flag", ApiFlagAction.KEEP),
                                    ApiFlag(
                                        "test.pkg.flags.finalized_flag",
                                        ApiFlagAction.FINALIZE,
                                    ),
                                )
                            ),
                        previouslyReleasedCodebase = previousCodebase,
                    ),
            ) {
                val undeprecated = codebase.assertClass("test.pkg.PreviouslyUndeprecated")
                undeprecated
                    .assertMethod("methodByAnnotation", emptyList())
                    .assertPrintedDocumentation(
                        expectedOutput =
                            """
                                /**
                                 * Main unguarded: ERROR[guarded by @FlaggedApi(test.pkg.flags.add_flag): FlaggedReplacement].
                                 * Main conditional: {@link test.pkg.FlaggedReplacement FlaggedReplacement}.
                                 * @deprecated Use {@link test.pkg.FlaggedReplacement FlaggedReplacement} instead.
                                 */
                            """,
                    )
                undeprecated
                    .assertMethod("methodByDocTagOnly", emptyList())
                    .assertPrintedDocumentation(
                        expectedOutput =
                            """
                                /**
                                 * Summary.
                                 * @deprecated Use {@link test.pkg.FlaggedReplacement FlaggedReplacement} instead.
                                 */
                            """,
                    )
                undeprecated
                    .assertMethod("methodWithFinalizedFlag", emptyList())
                    .assertPrintedDocumentation(
                        expectedOutput =
                            """
                                /** @deprecated Use ERROR[guarded by @FlaggedApi(test.pkg.flags.add_flag): FlaggedReplacement] instead. */
                            """,
                    )

                codebase
                    .assertClass("test.pkg.OuterNewlyFlagged")
                    .assertMethod("inheritedDeprecatedMethod", emptyList())
                    .assertPrintedDocumentation(
                        expectedOutput =
                            """
                                /** @deprecated Use {@link test.pkg.FlaggedReplacement FlaggedReplacement} instead. */
                            """,
                    )

                codebase
                    .assertClass("test.pkg.PreviouslyDeprecated")
                    .assertMethod("alreadyDeprecatedMethod", emptyList())
                    .assertPrintedDocumentation(
                        expectedOutput =
                            """
                                /** @deprecated Use ERROR[guarded by @FlaggedApi(test.pkg.flags.add_flag): FlaggedReplacement] instead. */
                            """,
                    )

                assertAndRemoveReportedIssues(
                    """
                        MAIN_SRC/src/test/pkg/PreviouslyUndeprecated.java:5:31: warning: Class test.pkg.FlaggedReplacement is guarded by @FlaggedApi(test.pkg.flags.add_flag) (ErrorWhenNew) [HiddenDocReference]
                        MAIN_SRC/src/test/pkg/PreviouslyUndeprecated.java:23:31: warning: Class test.pkg.FlaggedReplacement is guarded by @FlaggedApi(test.pkg.flags.add_flag) (ErrorWhenNew) [HiddenDocReference]
                        MAIN_SRC/src/test/pkg/PreviouslyDeprecated.java:7:31: warning: Class test.pkg.FlaggedReplacement is guarded by @FlaggedApi(test.pkg.flags.add_flag) (ErrorWhenNew) [HiddenDocReference]
                    """
                )
            }
        }
    }

    /**
     * Verify that unparameterized method references written without `#` or `()` (including
     * `java.lang.Object` methods on interfaces and mapped doc-stubs annotation elements) resolve to
     * `#`-separated callable links.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test ambiguous method references without hash or parentheses`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package android.annotation;
                        /** @hide */
                        public @interface IntDef {
                            boolean flag() default false;
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        /** {@link toString} */
                        public interface MyInterface {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Parent {
                            public void superMethod() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Child extends Parent {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        /** @hide */
                        public class HiddenChild extends Parent {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import android.annotation.IntDef;
                        public class Test {
                            /**
                             * {@link IntDef.flag}
                             * {@link android.annotation.IntDef.flag}
                             * {@link android.annotation.IntDef.getClass}
                             * {@link Child.superMethod}
                             * {@link HiddenChild.superMethod}
                             */
                            public void test() {}
                        }
                    """
                ),
            ),
        ) {
            codebase
                .assertClass("test.pkg.MyInterface")
                .assertPrintedDocumentation(
                    expectedOutput = "/** {@link java.lang.Object#toString toString} */",
                )

            codebase
                .assertClass("test.pkg.Test")
                .assertMethod("test", emptyList())
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@link androidx.annotation.IntDef#flag IntDef.flag}
                             * {@link androidx.annotation.IntDef#flag}
                             * {@link androidx.annotation.IntDef#getClass}
                             * {@link test.pkg.Child#superMethod Child.superMethod}
                             * ERROR[hidden: HiddenChild.superMethod]
                             */
                        """,
                )

            assertAndRemoveReportedIssues(
                "MAIN_SRC/src/test/pkg/Test.java:9:15: warning: Class test.pkg.HiddenChild is hidden (ErrorWhenNew) [HiddenDocReference]"
            )
        }
    }

    /**
     * Verify how unparameterized method references without `#` or `()` check accessibility across
     * hidden methods (declared and inherited), superinterface `static` methods, and overloaded
     * methods with mixed visibility (including overloads split across supertypes and methods
     * inherited via hidden intermediate supertypes).
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test hidden and overloaded ambiguous method references`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Other {
                            /** @hide */
                            public void hiddenMethod() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class SubOther extends Other {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public interface StaticHelperInterface {
                            static void helper() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class ImplWithHiddenHelper implements StaticHelperInterface {
                            /** {@link helper} */
                            public void test() {}

                            /** @hide */
                            public void helper() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public interface HiddenOverload {
                            /** @hide */
                            default void compute(String s) {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public interface PublicOverload {
                            default void compute(int x) {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class SplitOverloads implements HiddenOverload, PublicOverload {
                            /** {@link compute} */
                            public void test() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public interface PublicApi {
                            default void inheritedViaHidden() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        /** @hide */
                        public interface HiddenMid extends PublicApi {
                            @Override
                            default void inheritedViaHidden() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public interface PublicSub extends HiddenMid, PublicApi {
                            /** {@link inheritedViaHidden} */
                            void test();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Test {
                            /**
                             * {@link hiddenMethod}
                             * {@link overloadedMethod}
                             * {@link allHiddenOverloads}
                             * {@link Other.hiddenMethod}
                             * {@link SubOther.hiddenMethod}
                             * {@link StaticHelperInterface.helper}
                             * {@link ImplWithHiddenHelper.helper}
                             */
                            public void test() {}

                            /** @hide */
                            public void hiddenMethod() {}

                            public void overloadedMethod(int x) {}
                            /** @hide */
                            public void overloadedMethod(String s) {}

                            void allHiddenOverloads(String s) {}
                            /** @hide */
                            public void allHiddenOverloads(int x) {}
                        }
                    """
                ),
            ),
        ) {
            codebase
                .assertClass("test.pkg.ImplWithHiddenHelper")
                .assertMethod("test", emptyList())
                .assertPrintedDocumentation(
                    expectedOutput = "/** ERROR[hidden: helper] */",
                )

            codebase
                .assertClass("test.pkg.SplitOverloads")
                .assertMethod("test", emptyList())
                .assertPrintedDocumentation(
                    expectedOutput = "/** {@link #compute} */",
                )

            codebase
                .assertClass("test.pkg.PublicSub")
                .assertMethod("test", emptyList())
                .assertPrintedDocumentation(
                    expectedOutput = "/** {@link #inheritedViaHidden} */",
                )

            codebase
                .assertClass("test.pkg.Test")
                .assertMethod("test", emptyList())
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * ERROR[hidden: hiddenMethod]
                             * {@link #overloadedMethod}
                             * ERROR[hidden: allHiddenOverloads]
                             * ERROR[hidden: Other.hiddenMethod]
                             * ERROR[hidden: SubOther.hiddenMethod]
                             * {@link test.pkg.StaticHelperInterface#helper StaticHelperInterface.helper}
                             * ERROR[hidden: ImplWithHiddenHelper.helper]
                             */
                        """,
                )

            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/ImplWithHiddenHelper.java:3:16: warning: Method test.pkg.ImplWithHiddenHelper.helper() is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:4:15: warning: Method test.pkg.Test.hiddenMethod() is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:6:15: warning: Method test.pkg.Test.allHiddenOverloads(int) is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:7:15: warning: Method test.pkg.Other.hiddenMethod() is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:8:15: warning: Method test.pkg.Other.hiddenMethod() is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:10:15: warning: Method test.pkg.ImplWithHiddenHelper.helper() is hidden (ErrorWhenNew) [HiddenDocReference]
                """
            )
        }
    }

    /**
     * Verify how `#member` and `Class#member` references resolve fields inherited from superclasses
     * and superinterfaces, including hidden field targets and hidden qualifying subclasses.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test inherited field member references`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public interface MyInterface {
                            int INTERFACE_FIELD = 1;
                            /** @hide */
                            int HIDDEN_INTERFACE_FIELD = 2;
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Parent {
                            public static final int SUPER_FIELD = 1;
                            /** @hide */
                            public static final int HIDDEN_SUPER_FIELD = 2;
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        /**
                         * {@link #SUPER_FIELD}
                         * {@link #HIDDEN_SUPER_FIELD}
                         */
                        public class Child extends Parent implements MyInterface {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        /** @hide */
                        public class HiddenChild extends Parent {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        /**
                         * {@link Child#SUPER_FIELD}
                         * {@link Child#INTERFACE_FIELD}
                         * {@link Child#HIDDEN_SUPER_FIELD}
                         * {@link Child#HIDDEN_INTERFACE_FIELD}
                         * {@link HiddenChild#SUPER_FIELD}
                         */
                        public class Test {}
                    """
                ),
            ),
        ) {
            codebase
                .assertClass("test.pkg.Child")
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@link #SUPER_FIELD}
                             * ERROR[hidden: #HIDDEN_SUPER_FIELD]
                             */
                        """,
                )

            codebase
                .assertClass("test.pkg.Test")
                .assertPrintedDocumentation(
                    expectedOutput =
                        """
                            /**
                             * {@link test.pkg.Child#SUPER_FIELD Child.SUPER_FIELD}
                             * {@link test.pkg.Child#INTERFACE_FIELD Child.INTERFACE_FIELD}
                             * ERROR[hidden: Child#HIDDEN_SUPER_FIELD]
                             * ERROR[hidden: Child#HIDDEN_INTERFACE_FIELD]
                             * ERROR[hidden: HiddenChild#SUPER_FIELD]
                             */
                        """,
                )

            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Child.java:4:11: warning: Field test.pkg.Parent.HIDDEN_SUPER_FIELD is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:5:11: warning: Field test.pkg.Parent.HIDDEN_SUPER_FIELD is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:6:11: warning: Field test.pkg.MyInterface.HIDDEN_INTERFACE_FIELD is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:7:11: warning: Class test.pkg.HiddenChild is hidden (ErrorWhenNew) [HiddenDocReference]
                """
            )
        }
    }

    /**
     * Verify how `#member`, `Nested#member`, and `Outer.Nested#member` references resolve fields
     * declared on enclosing classes, including hidden field targets and hidden qualifying nested
     * classes.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test enclosing-class field member references`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Outer {
                            public static final int OUTER_FIELD = 1;
                            /** @hide */
                            public static final int HIDDEN_OUTER_FIELD = 2;

                            /**
                             * {@link #OUTER_FIELD}
                             * {@link #HIDDEN_OUTER_FIELD}
                             * {@link Nested#OUTER_FIELD}
                             * {@link Nested#HIDDEN_OUTER_FIELD}
                             */
                            public static class Nested {}

                            /** @hide */
                            public static class HiddenNested {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        /**
                         * {@link Outer.Nested#OUTER_FIELD}
                         * {@link Outer.Nested#HIDDEN_OUTER_FIELD}
                         * {@link Outer.HiddenNested#OUTER_FIELD}
                         */
                        public class Test {}
                    """
                ),
            ),
        ) {
            codebase
                .assertClass("test.pkg.Outer.Nested")
                .assertPrintedDocumentation(
                    // TODO(b/447588621): Resolve enclosing-class fields to `test.pkg.Outer` and
                    //  rewrite `#HIDDEN_OUTER_FIELD` and `Nested#HIDDEN_OUTER_FIELD` to
                    //  `ERROR[...]`.
                    expectedOutput =
                        """
                            /**
                             * {@link #OUTER_FIELD}
                             * {@link #HIDDEN_OUTER_FIELD}
                             * {@link #OUTER_FIELD Nested.OUTER_FIELD}
                             * {@link #HIDDEN_OUTER_FIELD Nested.HIDDEN_OUTER_FIELD}
                             */
                        """,
                )

            codebase
                .assertClass("test.pkg.Test")
                .assertPrintedDocumentation(
                    // TODO(b/447588621): Resolve `Outer.Nested#OUTER_FIELD` to
                    //  `test.pkg.Outer#OUTER_FIELD` and rewrite `Outer.Nested#HIDDEN_OUTER_FIELD`
                    //  to `ERROR[...]`.
                    expectedOutput =
                        """
                            /**
                             * {@link test.pkg.Outer.Nested#OUTER_FIELD Outer.Nested.OUTER_FIELD}
                             * {@link test.pkg.Outer.Nested#HIDDEN_OUTER_FIELD Outer.Nested.HIDDEN_OUTER_FIELD}
                             * ERROR[hidden: Outer.HiddenNested#OUTER_FIELD]
                             */
                        """,
                )

            assertAndRemoveReportedIssues(
                // TODO(b/447588621): Report HiddenDocReference for `HIDDEN_OUTER_FIELD` on
                //  `Outer.Nested` and `Test`.
                "MAIN_SRC/src/test/pkg/Test.java:5:11: warning: Class test.pkg.Outer.HiddenNested is hidden (ErrorWhenNew) [HiddenDocReference]"
            )
        }
    }
}
