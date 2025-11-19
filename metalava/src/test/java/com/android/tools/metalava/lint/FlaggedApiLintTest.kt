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

import com.android.tools.metalava.ARG_PASS_THROUGH_ANNOTATION
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.cli.common.ARG_WARNING
import com.android.tools.metalava.requiresPermissionSource
import com.android.tools.metalava.systemApiSource
import com.android.tools.metalava.testing.KnownJarFiles
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

    @Test
    fun `Dont require @FlaggedApi on methods that get elided from signature files`() {
        check(
            showAnnotations = arrayOf("android.annotation.SystemApi"),
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

                            /** @hide */
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
                                /** @hide */
                                @SystemApi
                                public int existingSystemApi() { return 0; }
                            }
                        """
                    ),
                    systemApiSource,
                ),
            // Access android.annotation.FlaggedApi
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
            extraArguments = arrayOf("--warning", "UnflaggedApi")
        )
    }

    @Test
    fun `Require @FlaggedApi on new APIs`() {
        check(
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
            // Access android.annotation.FlaggedApi
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
            extraArguments = arrayOf(ARG_WARNING, "UnflaggedApi", ARG_HIDE, "HiddenSuperclass")
        )
    }

    @Test
    fun `Dont require @FlaggedApi on existing items in nested SystemApi classes`() {
        check(
            showAnnotations = arrayOf("android.annotation.SystemApi"),
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
                                    /** @hide */
                                    @SystemApi
                                    public int existing() {}
                                }
                            }
                        """
                    ),
                    systemApiSource,
                ),
            // Access android.annotation.FlaggedApi
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
            extraArguments = arrayOf("--warning", "UnflaggedApi")
        )
    }

    @Test
    fun `Dont require @FlaggedApi on existing items inherited into new SystemApi classes`() {
        check(
            showAnnotations = arrayOf("android.annotation.SystemApi"),
            expectedIssues =
                """
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

            // TODO b/448620194 : currently android.foobar.Bad.BAD_INHERITED is not written to the
            // api signature file.
            // This inconsistency will be resolved in later Cls where the signature writer
            // should write fields in this edge case
            api =
                """
                package android.foobar {
                  public class Bad {
                    method public void badInherited();
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

                            /** @hide */
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

                            /** @hide */
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

                            /** @hide */
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

                            /** @hide */
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

                            /** @hide */
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

                            /** @hide */
                            @SystemApi
                            public class Existing extends ExistingPublicSuperClass implements ExistingPublicInterface {
                                private Existing() {}
                            }
                        """
                    ),
                    systemApiSource,
                ),
            // Access android.annotation.FlaggedApi
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
            extraArguments = arrayOf(ARG_WARNING, "UnflaggedApi", ARG_HIDE, "HiddenSuperclass"),
            checkCompilation = true
        )
    }

    @Test
    fun `Require @FlaggedApi to reference generated fields`() {
        check(
            expectedIssues =
                """
                    src/android/foobar/Bad.java:5: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). (ErrorWhenNew) [FlaggedApiLiteral]
                    src/android/foobar/Bad.java:7: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). (ErrorWhenNew) [FlaggedApiLiteral]
                    src/android/foobar/Bad.java:9: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). (ErrorWhenNew) [FlaggedApiLiteral]
                    src/android/foobar/Bad.java:11: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). (ErrorWhenNew) [FlaggedApiLiteral]
                    src/android/foobar/Bad.java:13: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_MY_FEATURE). (ErrorWhenNew) [FlaggedApiLiteral]
                    src/android/foobar/Bad.java:16: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (furthermore, the current flag literal seems to be malformed). (ErrorWhenNew) [FlaggedApiLiteral]
                    src/android/foobar/Bad.java:18: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.foobar.Flags.FLAG_NONEXISTENT_FLAG, however this flag doesn't seem to exist). (ErrorWhenNew) [FlaggedApiLiteral]
                    src/android/foobar/Bad.java:20: warning: @FlaggedApi contains a string literal, but should reference the field generated by aconfig (android.baz.Flags.FLAG_NON_EXISTENT_PACKAGE, however this flag doesn't seem to exist). (ErrorWhenNew) [FlaggedApiLiteral]
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
            // Access android.annotation.FlaggedApi
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
        )
    }

    @Test
    fun `Require @FlaggedApi on APIs whose modifiers have changed`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/Foo.java:3: warning: Changes to modifiers, from 'public abstract' to 'public' must be flagged with @FlaggedApi: class test.pkg.Foo [UnflaggedApi]
                    src/test/pkg/Foo.java:4: warning: Changes to modifiers, from 'protected' to 'public' must be flagged with @FlaggedApi: constructor test.pkg.Foo() [UnflaggedApi]
                    src/test/pkg/Foo.java:5: warning: Changes to modifiers, from 'public final' to 'public' must be flagged with @FlaggedApi: method test.pkg.Foo.method() [UnflaggedApi]
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
            // Access android.annotation.FlaggedApi
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
            extraArguments = arrayOf(ARG_WARNING, "UnflaggedApi"),
        )
    }

    @Test
    fun `Do not require @FlaggedApi on concrete class methods that override a default interface method`() {
        check(
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
            // Access android.annotation.FlaggedApi
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
            extraArguments = arrayOf(ARG_WARNING, "UnflaggedApi"),
        )
    }

    @Test
    fun `Require @FlaggedApi on APIs whose deprecated status has changed to deprecated`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/Foo.java:6: warning: Changes from not deprecated to deprecated must be flagged with @FlaggedApi: class test.pkg.Foo [UnflaggedApi]
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
            // Access android.annotation.FlaggedApi
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
            extraArguments = arrayOf(ARG_WARNING, "UnflaggedApi"),
        )
    }

    @Test
    fun `Require @FlaggedApi on APIs whose deprecated status has changed to not deprecated`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/Foo.java:3: warning: Changes from deprecated to not deprecated must be flagged with @FlaggedApi: class test.pkg.Foo [UnflaggedApi]
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
            // Access android.annotation.FlaggedApi
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
            extraArguments = arrayOf(ARG_WARNING, "UnflaggedApi"),
        )
    }

    // b/448616809 Test added to showcase the discrepancy between how the previously released api
    // (loaded from prebuilts) and the current api (loaded from sources) store @RequiresPermission.
    // Comparisons for this annotation will be disabled until this discrepancy is resolved, hence
    // changes in the @RequiresPermission annotation will not require flagging for the time being.
    @Test
    fun `Do not require @FlaggedApi on RequiresPermission`() {
        check(
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
            api =
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
            // Access android.annotation.FlaggedApi
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
            extraArguments = arrayOf(ARG_WARNING, "UnflaggedApi"),
        )
    }

    // This test ensure any implementation that compares annotations does not do so through
    // inheritance
    @Test
    fun `Do not require @FlaggedApi on concrete class methods that override an annotated method`() {
        check(
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
            api =
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
            // Access android.annotation.FlaggedApi
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
            extraArguments =
                arrayOf(
                    ARG_WARNING,
                    "UnflaggedApi",
                    ARG_PASS_THROUGH_ANNOTATION,
                    "test.annotation.Custom"
                ),
        )
    }

    @Test
    fun `Require @FlaggedApi on API that modify annotations`() {
        check(
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
            api =
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
            // Access android.annotation.FlaggedApi
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
            extraArguments =
                arrayOf(
                    ARG_WARNING,
                    "UnflaggedApi",
                    ARG_PASS_THROUGH_ANNOTATION,
                    "test.annotation.Custom"
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
                    xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../../../resources/schemas/config.xsd">
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

        check(
            configFiles = arrayOf(apiFlagsXmlFile),
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
                            @FlaggedApi("test.pkg.unexported_flag")
                            public class Foo {
                                // Use of exported flag will not result in errors
                                @FlaggedApi("test.pkg.exported_flag")
                                public Foo() {}
                            }
                        """
                    ),
                ),
            checkCompatibilityApiReleased = previouslyReleasedApi,
            // Access android.annotation.FlaggedApi
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
        )
    }
}
