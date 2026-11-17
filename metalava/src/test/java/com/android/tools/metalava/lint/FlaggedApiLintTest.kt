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

package com.android.tools.metalava.lint

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.ARG_PASS_THROUGH_ANNOTATION
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.KnownApiSurface
import com.android.tools.metalava.androidRestrictedForEnvironment
import com.android.tools.metalava.androidXRestrictedForEnvironment
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.requiresPermissionSource
import com.android.tools.metalava.testing.KnownJarFiles
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.xml
import org.junit.Test

class FlaggedApiLintTest : DriverTest() {

    private val flagsFile =
        java(
            """
                package android.foobar;

                /** @hide */
                public class Flags {
                    public static final String FLAG_MY_FEATURE = "android.foobar.my_feature";
                }
            """
        )

    /** Check the behavior of the `@FlaggedApi` linter. */
    private fun checkFlaggedApiLint(
        apiSurface: KnownApiSurface? = null,
        apiFlagsConfig: TestFile? = null,
        expectedIssues: String,
        apiLint: String,
        sourceFiles: Array<TestFile>,
        extraArguments: Array<String> = emptyArray(),
        expectedApiSignature: String? = null,
        expectedRemovedApiSignature: String? = null,
        checkCompilation: Boolean = false,
        checkCompatibilityApiReleased: String? = null,
    ) {
        check(
            apiSurface = apiSurface,
            configFiles = listOfNotNull(apiFlagsConfig).toTypedArray(),
            apiLint = apiLint,
            sourceFiles = sourceFiles,
            // Access android.annotation.FlaggedApi
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
            extraArguments =
                warningIssues(
                    Issues.UNFLAGGED_API,
                ) + extraArguments,
            expectedIssues = expectedIssues,
            expectedApiSignature = expectedApiSignature,
            removedApi = expectedRemovedApiSignature,
            checkCompatibilityApiReleased = checkCompatibilityApiReleased,
            checkCompilation = checkCompilation,
        )
    }

    @Test
    fun `Dont require @FlaggedApi on methods that get elided from signature files`() {
        checkFlaggedApiLint(
            apiSurface = KnownApiSurface.SYSTEM,
            expectedIssues = "",
            apiLint =
                """
                    package android.foobar {
                      public class ExistingSystemApi {
                          ctor public ExistingSystemApi();
                      }
                      public class Existing {
                          method public int existingSystemApi();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.foobar;

                            import android.annotation.SystemApi;
                            import android.annotation.FlaggedApi;

                            @SystemApi
                            public class ExistingSystemApi extends Existing {
                                /** exactly matches Object.equals, not emitted */
                                @Override
                                public boolean equals(Object other) { return false; }
                                /** exactly matches Object.hashCode, not emitted */
                                @Override
                                public int hashCode() { return 0; }
                                /** exactly matches ExistingPublicApi.existingPublicApi, not emitted */
                                @Override
                                public int existingPublicApi() { return 0; }
                                @Override
                                public int existingSystemApi() { return 0; }
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.SystemApi;
                            import android.annotation.FlaggedApi;

                            public class Existing {
                                public int existingPublicApi() { return 0; }
                                @SystemApi
                                public int existingSystemApi() { return 0; }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Require @FlaggedApi on new APIs`() {
        checkFlaggedApiLint(
            expectedIssues =
                """
                    src/android/foobar/Bad.java:3: warning: New API must be flagged with @FlaggedApi: class android.foobar.Bad [UnflaggedApi]
                    src/android/foobar/Bad.java:4: warning: New API must be flagged with @FlaggedApi: field android.foobar.Bad.BAD [UnflaggedApi]
                    src/android/foobar/Bad.java:5: warning: New API must be flagged with @FlaggedApi: method android.foobar.Bad.bad() [UnflaggedApi]
                    src/android/foobar/Bad.java:6: warning: New API must be flagged with @FlaggedApi: class android.foobar.Bad.BadInterface [UnflaggedApi]
                    src/android/foobar/Bad.java:7: warning: New API must be flagged with @FlaggedApi: class android.foobar.Bad.BadAnnotation [UnflaggedApi]
                    src/android/foobar/BadHiddenSuperClass.java:4: warning: New API must be flagged with @FlaggedApi: field android.foobar.Bad.INHERITED_BAD [UnflaggedApi]
                    src/android/foobar/BadHiddenSuperClass.java:4: warning: New API must be flagged with @FlaggedApi: field android.foobar.ExistingClass.INHERITED_BAD [UnflaggedApi]
                    src/android/foobar/BadHiddenSuperClass.java:5: warning: New API must be flagged with @FlaggedApi: method android.foobar.Bad.inheritedBad() [UnflaggedApi]
                    src/android/foobar/BadHiddenSuperClass.java:5: warning: New API must be flagged with @FlaggedApi: method android.foobar.ExistingClass.inheritedBad() [UnflaggedApi]
                    src/android/foobar/ExistingClass.java:9: warning: New API must be flagged with @FlaggedApi: field android.foobar.ExistingClass.BAD [UnflaggedApi]
                    src/android/foobar/ExistingClass.java:10: warning: New API must be flagged with @FlaggedApi: method android.foobar.ExistingClass.bad() [UnflaggedApi]
                """,
            apiLint =
                """
                    package android.foobar {
                      public class ExistingClass {
                          ctor public ExistingClass();
                          field public static final String EXISTING_FIELD = "foo";
                          method public void existingMethod();
                      }
                      public interface ExistingInterface {
                          field public static final String EXISTING_INTERFACE_FIELD = "foo";
                          method public default void existingInterfaceMethod();
                      }
                      public class ExistingSuperClass {
                          ctor public ExistingSuperClass();
                          field public static final String EXISTING_SUPER_FIELD = "foo";
                          method public void existingSuperMethod();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;

                            public interface ExistingInterface {
                                public static final String EXISTING_INTERFACE_FIELD = "foo";
                                public default void existingInterfaceMethod() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;

                            public class ExistingSuperClass {
                                public static final String EXISTING_SUPER_FIELD = "foo";
                                public void existingSuperMethod() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;

                            public class ExistingClass extends BadHiddenSuperClass implements BadHiddenSuperInterface {
                                public static final String EXISTING_FIELD = "foo";
                                public void existingMethod() {}

                                public static final String BAD = "bar";
                                public void bad() {}

                                @FlaggedApi(Flags.FLAG_MY_FEATURE)
                                public static final String OK = "baz";

                                @FlaggedApi(Flags.FLAG_MY_FEATURE)
                                public void ok() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            class BadHiddenSuperClass {
                                public static final String INHERITED_BAD = "bar";
                                public void inheritedBad() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            interface BadHiddenSuperInterface {
                                public static final String INHERITED_BAD = "bar";
                                public void inheritedBad() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            public class Bad extends BadHiddenSuperClass implements BadHiddenSuperInterface {
                                public static final String BAD = "bar";
                                public void bad() {}
                                public interface BadInterface {}
                                public @interface BadAnnotation {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;

                            @FlaggedApi(Flags.FLAG_MY_FEATURE)
                            public class Ok extends ExistingSuperClass implements ExistingInterface {
                                public static final String OK = "bar";
                                public void ok() {}
                                public interface OkInterface {}
                                public @interface OkAnnotation {}
                            }
                        """
                    ),
                    flagsFile,
                ),
            extraArguments =
                hiddenIssues(
                    Issues.HIDDEN_SUPERCLASS,
                ),
        )
    }

    @Test
    fun `Dont require @FlaggedApi on existing items in nested SystemApi classes`() {
        checkFlaggedApiLint(
            apiSurface = KnownApiSurface.SYSTEM,
            expectedIssues = "",
            apiLint =
                """
                    package android.foobar {
                      public class Existing.Inner {
                          method public int existing();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.foobar;

                            import android.annotation.SystemApi;
                            public class Existing {
                                public class Inner {
                                    @SystemApi
                                    public int existing() {}
                                }
                            }
                        """
                    ),
                ),
            extraArguments = warningIssues(Issues.UNFLAGGED_API),
        )
    }

    @Test
    fun `Dont require @FlaggedApi on existing items inherited into new SystemApi classes`() {
        checkFlaggedApiLint(
            apiSurface = KnownApiSurface.SYSTEM,
            expectedIssues =
                """
                    src/android/foobar/BadHiddenSuperClass.java:4: warning: New API must be flagged with @FlaggedApi: field android.foobar.Bad.BAD_INHERITED [UnflaggedApi]
                    src/android/foobar/BadHiddenSuperClass.java:5: warning: New API must be flagged with @FlaggedApi: method android.foobar.Bad.badInherited() [UnflaggedApi]
                """,
            apiLint =
                """
                    package android.foobar {
                      public interface ExistingSystemInterface {
                          field public static final String EXISTING_SYSTEM_INTERFACE_FIELD = "foo";
                          method public default void existingSystemInterfaceMethod();
                      }
                      public class ExistingSystemSuperClass {
                          ctor public ExistingSystemSuperClass();
                          field public static final String EXISTING_SYSTEM_SUPER_FIELD = "foo";
                          method public void existingSystemSuperMethod();
                      }
                      public class Existing {
                      }
                    }
                """,
            expectedApiSignature =
                """
                package android.foobar {
                  public class Bad {
                    method public void badInherited();
                    field public static final String BAD_INHERITED = "foo";
                  }
                  public class Existing extends android.foobar.ExistingPublicSuperClass implements android.foobar.ExistingPublicInterface {
                  }
                  public interface ExistingSystemInterface {
                    method public default void existingSystemInterfaceMethod();
                    field public static final String EXISTING_SYSTEM_INTERFACE_FIELD = "foo";
                  }
                  public class ExistingSystemSuperClass {
                    ctor public ExistingSystemSuperClass();
                    method public void existingSystemSuperMethod();
                    field public static final String EXISTING_SYSTEM_SUPER_FIELD = "foo";
                  }
                  public class Ok extends android.foobar.ExistingSystemSuperClass implements android.foobar.ExistingSystemInterface {
                  }
                  public class Ok2 extends android.foobar.ExistingPublicSuperClass implements android.foobar.ExistingPublicInterface {
                  }
                }
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;
                            import android.annotation.SystemApi;

                            @SystemApi
                            public interface ExistingSystemInterface {
                                public static final String EXISTING_SYSTEM_INTERFACE_FIELD = "foo";
                                public default void existingSystemInterfaceMethod() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;
                            import android.annotation.SystemApi;

                            @SystemApi
                            public class ExistingSystemSuperClass {
                                public static final String EXISTING_SYSTEM_SUPER_FIELD = "foo";
                                public void existingSystemSuperMethod() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            public interface ExistingPublicInterface {
                                public static final String EXISTING_PUBLIC_INTERFACE_FIELD = "foo";
                                public default void existingPublicInterfaceMethod() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            class BadHiddenSuperClass {
                                public static final String BAD_INHERITED = "foo";
                                public default void badInherited() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            public class ExistingPublicSuperClass {
                                public static final String EXISTING_PUBLIC_SUPER_FIELD = "foo";
                                public void existingPublicSuperMethod() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.SystemApi;

                            @SystemApi
                            @SuppressWarnings("UnflaggedApi")  // Ignore the class itself for this test.
                            public class Ok extends ExistingSystemSuperClass implements ExistingSystemInterface {
                                private Ok() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.SystemApi;

                            @SystemApi
                            @SuppressWarnings("UnflaggedApi")  // Ignore the class itself for this test.
                            public class Bad extends BadHiddenSuperClass {
                                private Bad() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.SystemApi;

                            @SystemApi
                            @SuppressWarnings("UnflaggedApi")  // Ignore the class itself for this test.
                            public class Ok2 extends ExistingPublicSuperClass implements ExistingPublicInterface {
                                private Ok2() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.SystemApi;

                            @SystemApi
                            public class Existing extends ExistingPublicSuperClass implements ExistingPublicInterface {
                                private Existing() {}
                            }
                        """
                    ),
                ),
            extraArguments =
                hiddenIssues(
                    Issues.HIDDEN_SUPERCLASS,
                ),
            checkCompilation = true,
        )
    }

    @Test
    fun `Require @FlaggedApi to reference generated fields`() {
        checkFlaggedApiLint(
            expectedIssues =
                """
                    src/android/foobar/Bad.java:5: error: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). [FlaggedApiLiteral]
                    src/android/foobar/Bad.java:7: error: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). [FlaggedApiLiteral]
                    src/android/foobar/Bad.java:9: error: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). [FlaggedApiLiteral]
                    src/android/foobar/Bad.java:11: error: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). [FlaggedApiLiteral]
                    src/android/foobar/Bad.java:13: error: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). [FlaggedApiLiteral]
                    src/android/foobar/Bad.java:16: error: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (furthermore, the current flag literal seems to be malformed). [FlaggedApiLiteral]
                    src/android/foobar/Bad.java:18: error: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_NONEXISTENT_FLAG, however this flag doesn't seem to exist). [FlaggedApiLiteral]
                    src/android/foobar/Bad.java:20: error: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.baz.Flags.FLAG_NON_EXISTENT_PACKAGE, however this flag doesn't seem to exist). [FlaggedApiLiteral]
                """,
            apiLint = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;

                            @FlaggedApi("android.foobar.my_feature")
                            public class Bad {
                                @FlaggedApi("android.foobar.my_feature")
                                public static final String BAD = "bar";
                                @FlaggedApi("android.foobar.my_feature")
                                public void bad() {}
                                @FlaggedApi("android.foobar.my_feature")
                                public interface BadInterface {}
                                @FlaggedApi("android.foobar.my_feature")
                                public @interface BadAnnotation {}

                                @FlaggedApi("malformed/flag")
                                public void malformed() {}
                                @FlaggedApi("android.foobar.nonexistent_flag")
                                public void nonexistentFlag() {}
                                @FlaggedApi("android.baz.non_existent_package")
                                public void nonexistentPackage() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;

                            @FlaggedApi(android.foobar.Flags.FLAG_MY_FEATURE)
                            public class Ok {
                                @FlaggedApi(android.foobar.Flags.FLAG_MY_FEATURE)
                                public static final String OK = "bar";
                                @FlaggedApi(android.foobar.Flags.FLAG_MY_FEATURE)
                                public void ok() {}
                                @FlaggedApi(android.foobar.Flags.FLAG_MY_FEATURE)
                                public interface OkInterface {}
                                @FlaggedApi(android.foobar.Flags.FLAG_MY_FEATURE)
                                public @interface OkAnnotation {}
                            }
                        """
                    ),
                    flagsFile,
                ),
        )
    }

    @Test
    fun `Require @FlaggedApi on APIs whose modifiers have changed`() {
        checkFlaggedApiLint(
            expectedIssues =
                """
                    src/test/pkg/Foo.java:3: error: Changes to modifiers, from 'public abstract' to 'public' must be flagged with @FlaggedApi: class test.pkg.Foo [UnflaggedApi]
                    src/test/pkg/Foo.java:4: error: Changes to modifiers, from 'protected' to 'public' must be flagged with @FlaggedApi: constructor test.pkg.Foo() [UnflaggedApi]
                    src/test/pkg/Foo.java:5: error: Changes to modifiers, from 'public final' to 'public' must be flagged with @FlaggedApi: method test.pkg.Foo.method() [UnflaggedApi]
                """,
            apiLint =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public abstract class Foo {
                        ctor protected Foo();
                        method public final void method();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Foo {
                                public Foo() {}
                                public void method() {}
                            }
                        """
                    ),
                    flagsFile,
                ),
            extraArguments = errorIssues(Issues.UNFLAGGED_API),
        )
    }

    @Test
    fun `Do not require @FlaggedApi on concrete class methods that override a default interface method`() {
        checkFlaggedApiLint(
            expectedIssues = "",
            apiLint =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface Base {
                        method public default void method();
                      }
                      public class Foo implements test.pkg.Base {
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public interface Base {
                                default void method() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public class Foo implements Base {
                                private Foo() {}
                                public void method() {}
                            }
                        """
                    ),
                    flagsFile,
                ),
        )
    }

    @Test
    fun `Require @FlaggedApi on APIs whose deprecated status has changed to deprecated`() {
        checkFlaggedApiLint(
            expectedIssues =
                """
                    src/test/pkg/Foo.java:6: error: Changes from not deprecated to deprecated must be flagged with @FlaggedApi: class test.pkg.Foo [UnflaggedApi]
                """,
            apiLint =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            /**
                             * @deprecated
                             */
                            @Deprecated
                            public class Foo {
                            }
                        """
                    ),
                    flagsFile,
                ),
            extraArguments = errorIssues(Issues.UNFLAGGED_API),
        )
    }

    @Test
    fun `Require @FlaggedApi on APIs whose deprecated status has changed to not deprecated`() {
        checkFlaggedApiLint(
            expectedIssues =
                """
                    src/test/pkg/Foo.java:3: error: Changes from deprecated to not deprecated must be flagged with @FlaggedApi: class test.pkg.Foo [UnflaggedApi]
                """,
            apiLint =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @Deprecated public class Foo {
                        ctor public Foo();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Foo {
                            }
                        """
                    ),
                    flagsFile,
                ),
            extraArguments = errorIssues(Issues.UNFLAGGED_API),
        )
    }

    /**
     * Verify that newly added `@Deprecated` APIs and modifier changes to previously `@Deprecated`
     * APIs still require `@FlaggedApi`, while unchanged `@Deprecated` APIs do not.
     */
    @Test
    fun `Require @FlaggedApi on deprecated APIs that are added or modified`() {
        checkFlaggedApiLint(
            expectedIssues =
                """
                    src/test/pkg/Foo.java:6: error: Changes to modifiers, from '@Deprecated public abstract' to '@Deprecated public' must be flagged with @FlaggedApi: class test.pkg.Foo [UnflaggedApi]
                    src/test/pkg/Foo.java:15: error: Changes to modifiers, from '@Deprecated public final' to '@Deprecated public' must be flagged with @FlaggedApi: method test.pkg.Foo.modifiedMethod() [UnflaggedApi]
                    src/test/pkg/Foo.java:20: error: New API must be flagged with @FlaggedApi: method test.pkg.Foo.newDeprecatedMethod() [UnflaggedApi]
                """,
            apiLint =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @Deprecated public abstract class Foo {
                        ctor @Deprecated public Foo();
                        method @Deprecated public final void modifiedMethod();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            // Previously deprecated class with `abstract` removed without @FlaggedApi.
                            /** @deprecated */
                            @Deprecated
                            public class Foo {
                                // Unchanged previously deprecated constructor: no UnflaggedApi expected.
                                /** @deprecated */
                                @Deprecated
                                public Foo() {}

                                // Previously deprecated method with `final` removed without @FlaggedApi.
                                /** @deprecated */
                                @Deprecated
                                public void modifiedMethod() {}

                                // Newly added deprecated method without @FlaggedApi.
                                /** @deprecated */
                                @Deprecated
                                public void newDeprecatedMethod() {}
                            }
                        """
                    ),
                    flagsFile,
                ),
            extraArguments = errorIssues(Issues.UNFLAGGED_API),
        )
    }

    @Test
    fun `Require @FlaggedApi on RequiresPermission changes`() {
        checkFlaggedApiLint(
            expectedIssues =
                "src/test/pkg/Foo.java:5: warning: Changes to modifiers, from '@androidx.annotation.RequiresPermission(\"android.permission.MY_PERMISSION_STRING\") public' to 'public' must be flagged with @FlaggedApi: class test.pkg.Foo [UnflaggedApi]",
            apiLint =
                """
                    // Signature format: 2.0
                    package test.pkg {
                    @RequiresPermission("android.permission.MY_PERMISSION_STRING") public class Foo {
                    }
                    public class Manifest {
                    }
                    public static final class Manifest.permission {
                      field public static final String MY_PERMISSION = "android.permission.MY_PERMISSION_STRING";
                    }
                  }
                """,
            expectedApiSignature =
                """
                  package test.pkg {
                    public class Foo {
                    }
                    public class Manifest {
                    }
                    public static final class Manifest.permission {
                      field public static final String MY_PERMISSION = "android.permission.MY_PERMISSION_STRING";
                    }
                  }
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Manifest {
                                Manifest() {}

                                public static final class permission {
                                    permission() {}
                                    public static final String MY_PERMISSION = "android.permission.MY_PERMISSION_STRING";
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.annotation.RequiresPermission;

                            public class Foo {
                                Foo() {}
                            }
                        """
                    ),
                    flagsFile,
                    requiresPermissionSource
                ),
        )
    }

    // This test was added to showcase what happens when the attributes for @RequiresPermission are
    // stored differently between sources (current api) and the prebuilts signature file (previous
    // api).
    @Test
    fun `Do not require @FlaggedApi on RequiresPermission annotations that resolve to the same value`() {
        checkFlaggedApiLint(
            expectedIssues = "",
            apiLint =
                """
                    // Signature format: 2.0
                    package test.pkg {
                    @RequiresPermission("android.permission.MY_PERMISSION_STRING") public class Foo {
                    }
                    public class Manifest {
                    }
                    public static final class Manifest.permission {
                      field public static final String MY_PERMISSION = "android.permission.MY_PERMISSION_STRING";
                    }
                  }
                """,
            expectedApiSignature =
                """
                  package test.pkg {
                    @RequiresPermission(test.pkg.Manifest.permission.MY_PERMISSION) public class Foo {
                    }
                    public class Manifest {
                    }
                    public static final class Manifest.permission {
                      field public static final String MY_PERMISSION = "android.permission.MY_PERMISSION_STRING";
                    }
                  }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Manifest {
                                Manifest() {}

                                public static final class permission {
                                    permission() {}
                                    public static final String MY_PERMISSION = "android.permission.MY_PERMISSION_STRING";
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.annotation.RequiresPermission;

                            @RequiresPermission(Manifest.permission.MY_PERMISSION)
                            public class Foo {
                                Foo() {}
                            }
                        """
                    ),
                    flagsFile,
                    requiresPermissionSource
                ),
        )
    }

    // This test ensure any implementation that compares annotations does not do so through
    // inheritance
    @Test
    fun `Do not require @FlaggedApi on concrete class methods that override an annotated method`() {
        checkFlaggedApiLint(
            expectedIssues = "",
            apiLint =
                """
                    // Signature format: 2.0
                    package test.annotation {
                        @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD}) public @interface Custom {}
                     }
                    package test.pkg {
                      public class Base {
                        method @test.annotation.Custom public void method();
                      }
                      public class Foo extends test.pkg.Base {
                      }
                    }
                """,
            expectedApiSignature =
                """
                package test.annotation {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD}) public @interface Custom {
                  }
                }
                package test.pkg {
                  public class Base {
                    method @test.annotation.Custom public void method();
                  }
                  public class Foo extends test.pkg.Base {
                  }
                }
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.annotation;

                            import static java.lang.annotation.ElementType.METHOD;
                            import java.lang.annotation.Target;

                            @Target({METHOD})
                            public @interface Custom {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import test.annotation.Custom;

                            public class Base {
                                private Base() {}
                                @Custom()
                                public void method() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public class Foo extends Base {
                                private Foo() {}
                                @Override
                                public void method() {}
                            }
                        """
                    ),
                    flagsFile,
                ),
            extraArguments =
                arrayOf(
                    ARG_PASS_THROUGH_ANNOTATION,
                    "test.annotation.Custom",
                ),
        )
    }

    @Test
    fun `Require @FlaggedApi on API that modify annotations`() {
        checkFlaggedApiLint(
            expectedIssues =
                """
                src/test/pkg/Foo.java:10: warning: Changes to modifiers, from 'public' to '@test.annotation.Custom(1) public' must be flagged with @FlaggedApi: constructor test.pkg.Foo() [UnflaggedApi]
                src/test/pkg/Foo.java:13: warning: Changes to modifiers, from '@test.annotation.Custom(1) public' to '@test.annotation.Custom(2) public' must be flagged with @FlaggedApi: field test.pkg.Foo.field_change_annotation [UnflaggedApi]
                src/test/pkg/Foo.java:14: warning: Changes to modifiers, from '@test.annotation.Custom(1) public' to 'public' must be flagged with @FlaggedApi: method test.pkg.Foo.method_remove_annotation() [UnflaggedApi]
            """,
            apiLint =
                """
                    package test.annotation {
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.CONSTRUCTOR}) public @interface Custom {
                        method public abstract int value() default 0;
                      }
                    }
                    package test.pkg {
                      @test.annotation.Custom(1) public class Foo {
                        ctor public Foo();
                        method public void method_add_flagged_annotation();
                        method @test.annotation.Custom(1) public void method_remove_annotation();
                        field @test.annotation.Custom(1) public int field_change_annotation;
                      }
                    }
                """,
            expectedApiSignature =
                """
                    package test.annotation {
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.CONSTRUCTOR}) public @interface Custom {
                        method public abstract int value() default 0;
                      }
                    }
                    package test.pkg {
                      @test.annotation.Custom(1) public class Foo {
                        ctor @test.annotation.Custom(1) public Foo();
                        method @FlaggedApi(Flags.FLAG_MY_FEATURE) @test.annotation.Custom(1) public void method_add_flagged_annotation();
                        method public void method_remove_annotation();
                        field @test.annotation.Custom(2) public int field_change_annotation;
                      }
                    }
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.annotation;

                            import static java.lang.annotation.ElementType.METHOD;
                            import static java.lang.annotation.ElementType.FIELD;
                            import static java.lang.annotation.ElementType.TYPE;
                            import static java.lang.annotation.ElementType.CONSTRUCTOR;
                            import java.lang.annotation.Target;

                            @Target({METHOD, TYPE, FIELD, CONSTRUCTOR})
                            public @interface Custom {
                                int value() default 0;
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import test.annotation.Custom;
                            import android.annotation.FlaggedApi;

                            @Custom(1)
                            public class Foo {
                                // Add new annotation
                                @Custom(1)
                                public Foo() {}

                                @Custom(2)
                                public int field_change_annotation = 1;
                                public void method_remove_annotation() {}
                                @FlaggedApi(Flags.FLAG_MY_FEATURE)
                                @Custom(1)
                                public void method_add_flagged_annotation() {}
                            }
                        """
                    ),
                    flagsFile,
                ),
            extraArguments =
                arrayOf(
                    ARG_PASS_THROUGH_ANNOTATION,
                    "test.annotation.Custom",
                ),
        )
    }

    @Test
    fun `Do not require @FlaggedApi on annotations changes that involve attribute fields that resolve to the same value`() {
        checkFlaggedApiLint(
            expectedIssues = "",
            apiLint =
                """
                     // Signature format: 5.0
                     package test.annotation {
                       @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD}) public @interface Custom {
                         method public abstract int value() default 0;
                       }
                     }
                     package test.pkg {
                       public class Foo {
                         ctor public Foo();
                         method @test.annotation.Custom(test.pkg.Foo.SECONDARY_FIELD) public void bar();
                         field public static final int PRIMARY_FIELD = 1; // 0x1
                         field public static final int SECONDARY_FIELD = 1; // 0x1
                       }
                     }
                """,
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.annotation {
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD}) public @interface Custom {
                        method public abstract int value() default 0;
                      }
                    }
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method @test.annotation.Custom(test.pkg.Foo.PRIMARY_FIELD) public void bar();
                        field public static final int PRIMARY_FIELD = 1; // 0x1
                        field public static final int SECONDARY_FIELD = 1; // 0x1
                      }
                    }
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.annotation;

                            import static java.lang.annotation.ElementType.METHOD;
                            import java.lang.annotation.Target;

                            @Target({METHOD})
                            public @interface Custom {
                                int value() default 0;
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import test.annotation.Custom;

                            public class Foo {
                                public static final int PRIMARY_FIELD = 1;
                                public static final int SECONDARY_FIELD = 1;

                                @Custom(PRIMARY_FIELD)
                                public void bar() {}
                            }
                        """
                    ),
                    flagsFile,
                ),
            extraArguments =
                arrayOf(
                    ARG_PASS_THROUGH_ANNOTATION,
                    "test.annotation.Custom",
                ),
        )
    }

    @Test
    fun `Require @FlaggedApi api flags to be exported`() {
        val apiFlagsXmlFile =
            xml(
                "config-api-flags.xml",
                """
                <config xmlns="http://www.google.com/tools/metalava/config"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../../resources/schemas/config.xsd">
                    <api-flags>
                        <api-flag package="test.pkg" name="unexported_flag" mutability="mutable" status="disabled" is-exported='false'/>
                        <api-flag package="test.pkg" name="exported_flag" mutability="mutable" status="disabled" is-exported='true'/>
                    </api-flags>
                </config>
            """
            )

        val previouslyReleasedApi =
            """
                // Signature format: 2.0
                package test.pkg {
                  @FlaggedApi("test.pkg.unexported_flag")
                  public class Foo {
                    ctor @FlaggedApi("test.pkg.exported_flag") public Foo();
                  }
                }
            """

        checkFlaggedApiLint(
            apiFlagsConfig = apiFlagsXmlFile,
            expectedIssues =
                """
                    src/test/pkg/Foo.java:6: warning: @FlaggedApi flag test.pkg.unexported_flag is not exported (ErrorWhenNew) [UnexportedFlaggedApi]
                """,
            apiLint = previouslyReleasedApi,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.annotation.FlaggedApi;

                            // Use of unexported flag will result in UnexportedFlaggedApi error
                            @FlaggedApi(Flags.FLAG_UNEXPORTED_FLAG)
                            public class Foo {
                                // Use of exported flag will not result in errors
                                @FlaggedApi(Flags.FLAG_EXPORTED_FLAG)
                                public Foo() {}
                            }
                        """
                    ),
                    // Reference generated flag constants rather than string literals so this test
                    // isolates UnexportedFlaggedApi from FlaggedApiLiteral.
                    java(
                        """
                            package test.pkg;

                            class Flags {
                                static final String FLAG_UNEXPORTED_FLAG = "test.pkg.unexported_flag";
                                static final String FLAG_EXPORTED_FLAG = "test.pkg.exported_flag";
                            }
                        """
                    ),
                ),
            checkCompatibilityApiReleased = previouslyReleasedApi,
        )
    }

    /**
     * Verify that `FlaggedApiLiteral` is reported even when `@FlaggedApi("...")` with a string
     * literal is used on a previously released API (for example, when deprecating an existing API).
     */
    @Test
    fun `Report FlaggedApiLiteral on previously released APIs`() {
        checkFlaggedApiLint(
            expectedIssues =
                """
                    src/android/foobar/Existing.java:6: error: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). [FlaggedApiLiteral]
                    src/android/foobar/Existing.java:13: error: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). [FlaggedApiLiteral]
                """,
            apiLint =
                """
                    // Signature format: 2.0
                    package android.foobar {
                      public class Existing {
                        ctor public Existing();
                        method public void deprecatedWithLiteral();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;

                            // Previously released class annotated with a string literal.
                            @FlaggedApi("android.foobar.my_feature")
                            public class Existing {
                                public Existing() {}

                                // Previously released method deprecated using a string literal.
                                /** @deprecated */
                                @Deprecated
                                @FlaggedApi("android.foobar.my_feature")
                                public void deprecatedWithLiteral() {}
                            }
                        """
                    ),
                    flagsFile,
                ),
        )
    }

    // b/483372828 - This test was added to prevent false-positives from occurring stating that
    // @RestrictedForEnvironment should be flagged when it shouldn't. @RestrictedForEnvironment has
    // attribute values that are represented differently between the sources (current api) and the
    // prebuilts signature file (previous api). Testing around this can be improved in the future.
    @Test
    fun `Do not require @FlaggedApi for @RestrictedForEnvironment with no changes`() {
        checkFlaggedApiLint(
            expectedIssues = "",
            apiLint =
                """
                    // Signature format: 5.0
                    package android.pkg {
                      @androidx.annotation.RestrictedForEnvironment(environments="SDK Runtime", from=34) public final class Foo {
                        ctor public Foo();
                      }
                    }
                """,
            expectedApiSignature =
                """
                // Signature format: 5.0
                package android.pkg {
                  @RestrictedForEnvironment(environments="SDK Runtime", from=34) public final class Foo {
                    ctor public Foo();
                  }
                }
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;
                            import androidx.annotation.RestrictedForEnvironment;

                            @RestrictedForEnvironment(environments=android.annotation.RestrictedForEnvironment.ENVIRONMENT_SDK_RUNTIME, from=34)
                            public final class Foo {
                                public Foo() {}
                            }
                        """
                    ),
                    androidRestrictedForEnvironment,
                    androidXRestrictedForEnvironment,
                    KnownSourceFiles.stringDefSource,
                    flagsFile,
                ),
        )
    }

    /**
     * Verify that removing previously released classes and members via `@RemovedFromApi` without
     * `@FlaggedApi` reports `UnflaggedApi` on:
     * - The outermost removed class only (never on its enclosed constructors, methods, fields, or
     *   nested classes).
     * - Individual removed constructors, methods (including `@Deprecated` methods), fields, and
     *   nested classes inside a non-removed class.
     * - A `@RemovedFromApi` member inside a `@FlaggedApi`-annotated non-removed class, because an
     *   enclosing class's `@FlaggedApi` does not gate `@RemovedFromApi` on its members.
     *
     * Also verifies that `@SuppressWarnings("UnflaggedApi")` suppresses the error and that newly
     * added `@RemovedFromApi` items that were never in `previouslyReleasedApi` do not require
     * `@FlaggedApi`.
     */
    @Test
    fun `Require @FlaggedApi on APIs removed with @RemovedFromApi`() {
        checkFlaggedApiLint(
            apiSurface = KnownApiSurface.PUBLIC,
            expectedIssues =
                """
                    src/android/foobar/ExistingClass.java:9: error: Removed API must be flagged with @FlaggedApi: constructor android.foobar.ExistingClass(int) [UnflaggedApi]
                    src/android/foobar/ExistingClass.java:12: error: Removed API must be flagged with @FlaggedApi: method android.foobar.ExistingClass.removedMethod() [UnflaggedApi]
                    src/android/foobar/ExistingClass.java:17: error: Removed API must be flagged with @FlaggedApi: method android.foobar.ExistingClass.removedDeprecatedMethod() [UnflaggedApi]
                    src/android/foobar/ExistingClass.java:20: error: Removed API must be flagged with @FlaggedApi: field android.foobar.ExistingClass.REMOVED_FIELD [UnflaggedApi]
                    src/android/foobar/ExistingClass.java:23: error: Removed API must be flagged with @FlaggedApi: class android.foobar.ExistingClass.RemovedNested [UnflaggedApi]
                    src/android/foobar/FlaggedExistingClass.java:11: error: Removed API must be flagged with @FlaggedApi: method android.foobar.FlaggedExistingClass.unflaggedRemovedInFlaggedClass() [UnflaggedApi]
                    src/android/foobar/RemovedClass.java:6: error: Removed API must be flagged with @FlaggedApi: class android.foobar.RemovedClass [UnflaggedApi]
                """,
            apiLint =
                """
                    // Signature format: 2.0
                    package android.foobar {
                      public class ExistingClass {
                        ctor public ExistingClass();
                        ctor public ExistingClass(int);
                        method @Deprecated public void removedDeprecatedMethod();
                        method public void removedMethod();
                        method public void suppressedRemovedMethod();
                        field public static final int REMOVED_FIELD = 1; // 0x1
                      }
                      public static class ExistingClass.RemovedNested {
                        ctor public ExistingClass.RemovedNested();
                        method public void innerMethod();
                      }
                      public static class ExistingClass.RemovedNested.DeepInner {
                        ctor public ExistingClass.RemovedNested.DeepInner();
                      }
                      public class FlaggedExistingClass {
                        ctor public FlaggedExistingClass();
                        method public void unflaggedRemovedInFlaggedClass();
                      }
                      public class RemovedClass {
                        ctor public RemovedClass();
                        method public void method();
                        field public static final int FIELD = 1; // 0x1
                      }
                      public static class RemovedClass.Inner {
                        ctor public RemovedClass.Inner();
                        method public void innerMethod();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.foobar;

                            import android.annotation.RemovedFromApi;

                            @RemovedFromApi
                            public class RemovedClass {
                                public RemovedClass() {}
                                public void method() {}
                                public static final int FIELD = 1;

                                public static class Inner {
                                    public Inner() {}
                                    public void innerMethod() {}
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.RemovedFromApi;

                            public class ExistingClass {
                                public ExistingClass() {}

                                @RemovedFromApi
                                public ExistingClass(int x) {}

                                @RemovedFromApi
                                public void removedMethod() {}

                                /** @deprecated */
                                @Deprecated
                                @RemovedFromApi
                                public void removedDeprecatedMethod() {}

                                @RemovedFromApi
                                public static final int REMOVED_FIELD = 1;

                                @RemovedFromApi
                                public static class RemovedNested {
                                    public RemovedNested() {}
                                    public void innerMethod() {}

                                    public static class DeepInner {
                                        public DeepInner() {}
                                    }
                                }

                                @SuppressWarnings("UnflaggedApi")
                                @RemovedFromApi
                                public void suppressedRemovedMethod() {}

                                @RemovedFromApi
                                public void neverReleasedRemovedMethod() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;
                            import android.annotation.RemovedFromApi;

                            @FlaggedApi(Flags.FLAG_MY_FEATURE)
                            public class FlaggedExistingClass {
                                public FlaggedExistingClass() {}

                                @RemovedFromApi
                                public void unflaggedRemovedInFlaggedClass() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            class Flags {
                                static final String FLAG_MY_FEATURE = "android.foobar.my_feature";
                            }
                        """
                    ),
                ),
            extraArguments = errorIssues(Issues.UNFLAGGED_API),
        )
    }

    /**
     * Specifically verify legacy `@removed` Javadoc tag behavior (omitting `apiSurface` so legacy
     * Javadoc tags are honored): removing previously released APIs via `@removed` always reports
     * `UnflaggedApi` — even if `@FlaggedApi` is also present (`flaggedDocRemovedMethod`), because
     * `@FlaggedApi` only gates configured removed annotations (`@RemovedFromApi`), not `@removed`
     * doc tags.
     */
    @Test
    fun `Require @FlaggedApi on APIs removed with @removed doc tag`() {
        checkFlaggedApiLint(
            expectedIssues =
                """
                    src/android/foobar/ExistingClass.java:9: error: Removed API must be flagged with @FlaggedApi: constructor android.foobar.ExistingClass(int) [UnflaggedApi]
                    src/android/foobar/ExistingClass.java:13: error: Removed API must be flagged with @FlaggedApi: method android.foobar.ExistingClass.flaggedDocRemovedMethod() [UnflaggedApi]
                    src/android/foobar/ExistingClass.java:16: error: Removed API must be flagged with @FlaggedApi: method android.foobar.ExistingClass.removedMethod() [UnflaggedApi]
                    src/android/foobar/ExistingClass.java:23: error: Removed API must be flagged with @FlaggedApi: method android.foobar.ExistingClass.removedDeprecatedMethod() [UnflaggedApi]
                    src/android/foobar/ExistingClass.java:26: error: Removed API must be flagged with @FlaggedApi: field android.foobar.ExistingClass.REMOVED_FIELD [UnflaggedApi]
                    src/android/foobar/ExistingClass.java:29: error: Removed API must be flagged with @FlaggedApi: class android.foobar.ExistingClass.RemovedNested [UnflaggedApi]
                    src/android/foobar/RemovedClass.java:4: error: Removed API must be flagged with @FlaggedApi: class android.foobar.RemovedClass [UnflaggedApi]
                """,
            apiLint =
                """
                    // Signature format: 2.0
                    package android.foobar {
                      public class ExistingClass {
                        ctor public ExistingClass();
                        ctor public ExistingClass(int);
                        method public void flaggedDocRemovedMethod();
                        method @Deprecated public void removedDeprecatedMethod();
                        method public void removedMethod();
                        field public static final int REMOVED_FIELD = 1; // 0x1
                      }
                      public static class ExistingClass.RemovedNested {
                        ctor public ExistingClass.RemovedNested();
                        method public void innerMethod();
                      }
                      public class RemovedClass {
                        ctor public RemovedClass();
                        method public void method();
                      }
                      public static class RemovedClass.Inner {
                        ctor public RemovedClass.Inner();
                      }
                    }
                """,
            expectedApiSignature =
                """
                    package android.foobar {
                      public class ExistingClass {
                        ctor public ExistingClass();
                      }
                    }
                """,
            expectedRemovedApiSignature =
                """
                    package android.foobar {
                      public class ExistingClass {
                        ctor public ExistingClass(int);
                        method @FlaggedApi("android.foobar.my_feature") public void flaggedDocRemovedMethod();
                        method public void neverReleasedRemovedMethod();
                        method @Deprecated public void removedDeprecatedMethod();
                        method public void removedMethod();
                        field public static final int REMOVED_FIELD = 1; // 0x1
                      }
                      public static class ExistingClass.RemovedNested {
                        ctor public ExistingClass.RemovedNested();
                        method public void innerMethod();
                      }
                      public class RemovedClass {
                        ctor public RemovedClass();
                        method public void method();
                      }
                      public static class RemovedClass.Inner {
                        ctor public RemovedClass.Inner();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.foobar;

                            /** @removed */
                            public class RemovedClass {
                                public RemovedClass() {}
                                public void method() {}

                                public static class Inner {
                                    public Inner() {}
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;

                            public class ExistingClass {
                                public ExistingClass() {}

                                /** @removed */
                                public ExistingClass(int x) {}

                                /** @removed */
                                @FlaggedApi(Flags.FLAG_MY_FEATURE)
                                public void flaggedDocRemovedMethod() {}

                                /** @removed */
                                public void removedMethod() {}

                                /**
                                 * @deprecated
                                 * @removed
                                 */
                                @Deprecated
                                public void removedDeprecatedMethod() {}

                                /** @removed */
                                public static final int REMOVED_FIELD = 1;

                                /** @removed */
                                public static class RemovedNested {
                                    public RemovedNested() {}
                                    public void innerMethod() {}
                                }

                                /** @removed */
                                public void neverReleasedRemovedMethod() {}
                            }
                        """
                    ),
                    flagsFile,
                ),
            extraArguments = errorIssues(Issues.UNFLAGGED_API),
        )
    }

    /**
     * Verify `@FlaggedApi` checks when analyzing the `@SystemApi` delta surface:
     * - Removing `@SystemApi` classes or methods without `@FlaggedApi` reports `UnflaggedApi`.
     * - Removing a `public` method (`unflaggedRemovedPublicMethod`) is not reported in the `system`
     *   delta check (it belongs to `public(R)`, not `system(R)`).
     * - A `@SystemApi` method inside a `@RemovedFromApi @FlaggedApi` `public` class does not
     *   require its own `@FlaggedApi` because its enclosing class is already being removed.
     */
    @Test
    fun `Require @FlaggedApi on SystemApi removed with @RemovedFromApi`() {
        val apiFlagsXmlFile =
            xml(
                "config-api-flags.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                        <api-flags>
                            <api-flag package="android.foobar" name="my_feature" mutability="immutable" status="enabled" is-exported="true"/>
                        </api-flags>
                    </config>
                """
            )
        checkFlaggedApiLint(
            apiSurface = KnownApiSurface.SYSTEM,
            apiFlagsConfig = apiFlagsXmlFile,
            expectedIssues =
                """
                    src/android/foobar/PublicClass.java:14: error: Removed API must be flagged with @FlaggedApi: method android.foobar.PublicClass.unflaggedRemovedSystemMethod() [UnflaggedApi]
                    src/android/foobar/RemovedSystemClass.java:8: error: Removed API must be flagged with @FlaggedApi: class android.foobar.RemovedSystemClass [UnflaggedApi]
                """,
            apiLint =
                """
                    // Signature format: 2.0
                    package android.foobar {
                      public class FlaggedRemovedPublicClass {
                        method public void systemMethodInRemovedPublicClass();
                      }
                      public class PublicClass {
                        method public void unflaggedRemovedSystemMethod();
                      }
                      public class RemovedSystemClass {
                        ctor public RemovedSystemClass();
                        method public void method();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;
                            import android.annotation.RemovedFromApi;
                            import android.annotation.SystemApi;

                            @RemovedFromApi
                            @FlaggedApi(Flags.FLAG_MY_FEATURE)
                            public class FlaggedRemovedPublicClass {
                                public FlaggedRemovedPublicClass() {}

                                @SystemApi
                                public void systemMethodInRemovedPublicClass() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.RemovedFromApi;
                            import android.annotation.SystemApi;

                            public class PublicClass {
                                private PublicClass() {}

                                @RemovedFromApi
                                public void unflaggedRemovedPublicMethod() {}

                                @SystemApi
                                @RemovedFromApi
                                public void unflaggedRemovedSystemMethod() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.RemovedFromApi;
                            import android.annotation.SystemApi;

                            @SystemApi
                            @RemovedFromApi
                            public class RemovedSystemClass {
                                public RemovedSystemClass() {}
                                public void method() {}
                            }
                        """
                    ),
                    flagsFile,
                ),
            extraArguments = errorIssues(Issues.UNFLAGGED_API),
        )
    }

    /**
     * Verify that `@RemovedFromApi` items gated by a kept (`mutable`) or reverted (`immutable` +
     * `disabled`) `@FlaggedApi` remain in the core API without reporting `UnflaggedApi`.
     */
    @Test
    fun `Do not require @FlaggedApi when @RemovedFromApi is kept or reverted`() {
        val apiFlagsXmlFile =
            xml(
                "config-api-flags.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                        <api-flags>
                            <api-flag package="android.foobar" name="kept_flag" mutability="mutable" status="enabled" is-exported="true"/>
                            <api-flag package="android.foobar" name="reverted_flag" mutability="immutable" status="disabled" is-exported="true"/>
                        </api-flags>
                    </config>
                """
            )
        val previouslyReleasedApi =
            """
                // Signature format: 2.0
                package android.foobar {
                  public class ExistingClass {
                    ctor public ExistingClass();
                    method public void keptRemovedMethod();
                    method public void revertedRemovedMethod();
                  }
                  public class KeptRemovedClass {
                    ctor public KeptRemovedClass();
                    method public void method();
                  }
                  public class RevertedRemovedClass {
                    ctor public RevertedRemovedClass();
                    method public void method();
                  }
                }
            """
        checkFlaggedApiLint(
            apiSurface = KnownApiSurface.PUBLIC,
            apiFlagsConfig = apiFlagsXmlFile,
            expectedIssues = "",
            apiLint = previouslyReleasedApi,
            checkCompatibilityApiReleased = previouslyReleasedApi,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.foobar;

                            class CustomFlags {
                                static final String FLAG_KEPT = "android.foobar.kept_flag";
                                static final String FLAG_REVERTED = "android.foobar.reverted_flag";
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;
                            import android.annotation.RemovedFromApi;

                            @RemovedFromApi
                            @FlaggedApi(CustomFlags.FLAG_KEPT)
                            public class KeptRemovedClass {
                                public KeptRemovedClass() {}
                                public void method() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;
                            import android.annotation.RemovedFromApi;

                            @RemovedFromApi
                            @FlaggedApi(CustomFlags.FLAG_REVERTED)
                            public class RevertedRemovedClass {
                                public RevertedRemovedClass() {}
                                public void method() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;
                            import android.annotation.RemovedFromApi;

                            public class ExistingClass {
                                public ExistingClass() {}

                                @RemovedFromApi
                                @FlaggedApi(CustomFlags.FLAG_KEPT)
                                public void keptRemovedMethod() {}

                                @RemovedFromApi
                                @FlaggedApi(CustomFlags.FLAG_REVERTED)
                                public void revertedRemovedMethod() {}
                            }
                        """
                    ),
                ),
            extraArguments = errorIssues(Issues.UNFLAGGED_API),
        )
    }

    /**
     * Verify that finalized `@RemovedFromApi` items annotated with `@FlaggedApi` do not report
     * `UnflaggedApi`, while still validating their `@FlaggedApi` annotations for
     * `FlaggedApiLiteral` and `UnexportedFlaggedApi`.
     */
    @Test
    fun `Check @FlaggedApi on finalized @RemovedFromApi items`() {
        val apiFlagsXmlFile =
            xml(
                "config-api-flags.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                        <api-flags>
                            <api-flag package="android.foobar" name="finalized_flag" mutability="immutable" status="enabled" is-exported="true"/>
                            <api-flag package="android.foobar" name="unexported_finalized_flag" mutability="immutable" status="enabled" is-exported="false"/>
                        </api-flags>
                    </config>
                """
            )
        checkFlaggedApiLint(
            apiSurface = KnownApiSurface.PUBLIC,
            apiFlagsConfig = apiFlagsXmlFile,
            expectedIssues =
                """
                    src/android/foobar/ExistingClass.java:14: error: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_FINALIZED_FLAG). [FlaggedApiLiteral]
                    src/android/foobar/ExistingClass.java:18: warning: @FlaggedApi flag android.foobar.unexported_finalized_flag is not exported (ErrorWhenNew) [UnexportedFlaggedApi]
                """,
            apiLint =
                """
                    // Signature format: 2.0
                    package android.foobar {
                      public class ExistingClass {
                        ctor public ExistingClass();
                        method public void literalFinalizedRemoval();
                        method public void okFinalizedRemoval();
                        method public void unexportedFinalizedRemoval();
                      }
                      public class FinalizedRemovedClass {
                        ctor public FinalizedRemovedClass();
                        method public void method();
                      }
                    }
                """,
            expectedRemovedApiSignature =
                """
                    package android.foobar {
                      public class ExistingClass {
                        method public void literalFinalizedRemoval();
                        method public void okFinalizedRemoval();
                        method public void unexportedFinalizedRemoval();
                      }
                      public class FinalizedRemovedClass {
                        ctor public FinalizedRemovedClass();
                        method public void method();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.foobar;

                            class Flags {
                                static final String FLAG_FINALIZED_FLAG = "android.foobar.finalized_flag";
                                static final String FLAG_UNEXPORTED_FINALIZED_FLAG = "android.foobar.unexported_finalized_flag";
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;
                            import android.annotation.RemovedFromApi;

                            @RemovedFromApi
                            @FlaggedApi(Flags.FLAG_FINALIZED_FLAG)
                            public class FinalizedRemovedClass {
                                public FinalizedRemovedClass() {}
                                public void method() {}
                            }
                        """
                    ),
                    java(
                        """
                            package android.foobar;

                            import android.annotation.FlaggedApi;
                            import android.annotation.RemovedFromApi;

                            public class ExistingClass {
                                public ExistingClass() {}

                                @RemovedFromApi
                                @FlaggedApi(Flags.FLAG_FINALIZED_FLAG)
                                public void okFinalizedRemoval() {}

                                @RemovedFromApi
                                @FlaggedApi("android.foobar.finalized_flag")
                                public void literalFinalizedRemoval() {}

                                @RemovedFromApi
                                @FlaggedApi(Flags.FLAG_UNEXPORTED_FINALIZED_FLAG)
                                public void unexportedFinalizedRemoval() {}
                            }
                        """
                    ),
                ),
            extraArguments = errorIssues(Issues.UNFLAGGED_API),
        )
    }
}
