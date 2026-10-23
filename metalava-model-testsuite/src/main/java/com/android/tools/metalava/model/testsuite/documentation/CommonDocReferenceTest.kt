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
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.source.doc.DocContentPredicates
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.BaseModelTest
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
                // TODO(b/447588621): Should report an issue about the #invalid(...) reference.
                ""
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
                             * {@link Nullable##uri-fragment}
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
                             * {@link androidx.annotation.Nullable##uri-fragment}
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
     * packages/classes/members and package-private classes/members are resolved, fully qualified
     * when printed, and checked for `HiddenDocReference`.
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
                             * {@link HiddenClass#field}
                             * {@link HiddenClass#method}
                             * {@link HiddenClass#method(PackagePrivateClass)}
                             * {@link HiddenClass##uri-fragment}
                             * {@link Other#hiddenField}
                             * {@link Other#packagePrivateField}
                             * {@link Other.HiddenNested}
                             * {@linkplain HiddenClass}
                             *
                             * @see HiddenClass
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

            // Even when referencing hidden items, links are still resolved and fully qualified.
            testMethod.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /**
                         * {@link hidden.pkg}
                         * {@link hidden.pkg.InHiddenPackage}
                         * {@link test.pkg.PackagePrivateClass PackagePrivateClass}
                         * {@link test.pkg.HiddenClass HiddenClass}
                         * {@link test.pkg.HiddenClass#field HiddenClass.field}
                         * {@link test.pkg.HiddenClass#method HiddenClass.method}
                         * {@link test.pkg.HiddenClass#method(test.pkg.PackagePrivateClass) HiddenClass.method(PackagePrivateClass)}
                         * {@link test.pkg.HiddenClass##uri-fragment}
                         * {@link test.pkg.Other#hiddenField Other.hiddenField}
                         * {@link test.pkg.Other#packagePrivateField Other.packagePrivateField}
                         * {@link test.pkg.Other.HiddenNested Other.HiddenNested}
                         * {@linkplain test.pkg.HiddenClass HiddenClass}
                         *
                         * @throws test.pkg.Test.HiddenException if hidden exception is thrown.
                         * @see test.pkg.HiddenClass HiddenClass
                         */
                    """,
            )

            // Line 10 (`HiddenClass#method(PackagePrivateClass)`) reports both the hidden
            // qualifying class and the package-private parameter class.
            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Test.java:4:15: warning: Package hidden.pkg is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:5:15: warning: Class hidden.pkg.InHiddenPackage is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:6:15: warning: Class test.pkg.PackagePrivateClass is not public (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:7:15: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:8:15: warning: Field test.pkg.HiddenClass.field is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:9:15: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:10:15: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:10:15: warning: Class test.pkg.PackagePrivateClass is not public (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:11:15: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:12:15: warning: Field test.pkg.Other.hiddenField is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:13:15: warning: Field test.pkg.Other.packagePrivateField is not public (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:14:15: warning: Class test.pkg.Other.HiddenNested is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:15:20: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:17:13: warning: Class test.pkg.HiddenClass is hidden (ErrorWhenNew) [HiddenDocReference]
                    MAIN_SRC/src/test/pkg/Test.java:18:16: warning: Class test.pkg.Test.HiddenException is hidden (ErrorWhenNew) [HiddenDocReference]
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
                             * {@link Nullable##uri-fragment}
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
                             * {@link androidx.annotation.IntDef#HIDDEN_FLAG IntDef.HIDDEN_FLAG}
                             * {@link androidx.annotation.IntDef#flag IntDef.flag}
                             * {@link androidx.annotation.IntDef#flag() IntDef.flag()}
                             * {@link androidx.annotation.Nullable##uri-fragment}
                             * {@link androidx.annotation.NonNull NonNull}
                             * {@link androidx.annotation.IntRange IntRange}
                             * {@link android.annotation.Hide Hide}
                             * {@link android.annotation.Hide#value() Hide.value()}
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
}
