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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.androidxNonNullSource
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.android.tools.metalava.testing.signature
import org.junit.Test

class NullnessCompatibilityTest : DriverTest() {
    @Test
    fun `Compare signatures with Kotlin nullability from signature`() {
        check(
            expectedIssues =
                """
                    load-api.txt:5: error: Attempted to remove nullability from java.lang.String (was NONNULL) in parameter str in test.pkg.Foo.method1(int p, Integer int2, int p1, String str, java.lang.String... args) [InvalidNullConversion]
                    load-api.txt:7: error: Attempted to change nullability of java.lang.String (from NULLABLE to NONNULL) in parameter str in test.pkg.Foo.method3(String str, int p, int int2) [InvalidNullConversion]
                """,
            format = FileFormat.V3,
            checkCompatibilityApiReleased =
                """
                    // Signature format: 3.0
                    package test.pkg {
                      public final class Foo {
                        ctor public Foo();
                        method public void method1(int p = 42, Integer? int2 = null, int p1 = 42, String str = "hello world", java.lang.String... args);
                        method public void method2(int p, int int2 = (2 * int) * some.other.pkg.Constants.Misc.SIZE);
                        method public void method3(String? str, int p, int int2 = double(int) + str.length);
                        field public static final test.pkg.Foo.Companion! Companion;
                      }
                    }
                """,
            signatureSource =
                """
                    // Signature format: 3.0
                    package test.pkg {
                      public final class Foo {
                        ctor public Foo();
                        method public void method1(int p = 42, Integer? int2 = null, int p1 = 42, String! str = "hello world", java.lang.String... args);
                        method public void method2(int p, int int2 = (2 * int) * some.other.pkg.Constants.Misc.SIZE);
                        method public void method3(String str, int p, int int2 = double(int) + str.length);
                        field public static final test.pkg.Foo.Companion! Companion;
                      }
                    }
                """
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Compare signatures with Kotlin nullability from source`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/test.kt:2: error: Attempted to change nullability of java.lang.String (from NULLABLE to NONNULL) in parameter str1 in test.pkg.TestKt.fun1(String str1, String str2, java.util.List<java.lang.String> list) [InvalidNullConversion]
                """,
            format = FileFormat.V3,
            checkCompatibilityApiReleased =
                """
                    // Signature format: 3.0
                    package test.pkg {
                      public final class TestKt {
                        method public static void fun1(String? str1, String str2, java.util.List<java.lang.String!> list);
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            fun fun1(str1: String, str2: String?, list: List<String?>) = Unit
                        """
                    )
                )
        )
    }

    @Test
    fun `Flag invalid nullness changes in final class`() {
        check(
            expectedIssues =
                """
                    load-api.txt:6: error: Attempted to remove nullability from java.lang.Double (was NULLABLE) in method test.pkg.MyTest.convert3(Float) [InvalidNullConversion]
                    load-api.txt:6: error: Attempted to remove nullability from java.lang.Float (was NULLABLE) in parameter arg1 in test.pkg.MyTest.convert3(Float arg1) [InvalidNullConversion]
                    load-api.txt:7: error: Attempted to remove nullability from java.lang.Double (was NONNULL) in method test.pkg.MyTest.convert4(Float) [InvalidNullConversion]
                    load-api.txt:7: error: Attempted to remove nullability from java.lang.Float (was NONNULL) in parameter arg1 in test.pkg.MyTest.convert4(Float arg1) [InvalidNullConversion]
                    load-api.txt:8: error: Attempted to change nullability of java.lang.Float (from NULLABLE to NONNULL) in parameter arg1 in test.pkg.MyTest.convert5(Float arg1) [InvalidNullConversion]
                    load-api.txt:9: error: Attempted to change nullability of java.lang.Double (from NONNULL to NULLABLE) in method test.pkg.MyTest.convert6(Float) [InvalidNullConversion]
                """,
            format = FileFormat.V2,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public final class MyTest {
                        method public Double convert1(Float);
                        method public Double convert2(Float);
                        method @Nullable public Double convert3(@Nullable Float);
                        method @NonNull public Double convert4(@NonNull Float);
                        method @Nullable public Double convert5(@Nullable Float);
                        method @NonNull public Double convert6(@NonNull Float);
                        // booleans cannot reasonably be annotated with @Nullable/@NonNull but
                        // the compiler accepts it and we had a few of these accidentally annotated
                        // that way in API 28, such as Boolean.getBoolean. Make sure we don't flag
                        // these as incompatible changes when they're dropped.
                        method public void convert7(@NonNull boolean);
                      }
                    }
                """,
            // Changes: +nullness, -nullness, nullable->nonnull, nonnull->nullable
            signatureSource =
                """
                    package test.pkg {
                      public final class MyTest {
                        method @Nullable public Double convert1(@Nullable Float);
                        method @NonNull public Double convert2(@NonNull Float);
                        method public Double convert3(Float);
                        method public Double convert4(Float);
                        method @NonNull public Double convert5(@NonNull Float);
                        method @Nullable public Double convert6(@Nullable Float);
                        method public void convert7(boolean);
                      }
                    }
                """
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Kotlin Nullness`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/Outer.kt:5: error: Attempted to change nullability of java.lang.String (from NONNULL to NULLABLE) in method test.pkg.Outer.method2(String,String) [InvalidNullConversion]
                    src/test/pkg/Outer.kt:5: error: Attempted to change nullability of java.lang.String (from NULLABLE to NONNULL) in parameter string in test.pkg.Outer.method2(String string, String maybeString) [InvalidNullConversion]
                    src/test/pkg/Outer.kt:6: error: Attempted to change nullability of java.lang.String (from NULLABLE to NONNULL) in parameter string in test.pkg.Outer.method3(String maybeString, String string) [InvalidNullConversion]
                    src/test/pkg/Outer.kt:8: error: Attempted to change nullability of java.lang.String (from NONNULL to NULLABLE) in method test.pkg.Outer.Inner.method2(String,String) [InvalidNullConversion]
                    src/test/pkg/Outer.kt:8: error: Attempted to change nullability of java.lang.String (from NULLABLE to NONNULL) in parameter string in test.pkg.Outer.Inner.method2(String string, String maybeString) [InvalidNullConversion]
                    src/test/pkg/Outer.kt:9: error: Attempted to change nullability of java.lang.String (from NULLABLE to NONNULL) in parameter string in test.pkg.Outer.Inner.method3(String maybeString, String string) [InvalidNullConversion]
                """,
            format = FileFormat.V2,
            checkCompatibilityApiReleased =
                """
                    // Signature format: 3.0
                    package test.pkg {
                      public final class Outer {
                        ctor public Outer();
                        method public final String? method1(String, String?);
                        method public final String method2(String?, String);
                        method public final String? method3(String, String?);
                      }
                      public static final class Outer.Inner {
                        ctor public Outer.Inner();
                        method public final String method2(String?, String);
                        method public final String? method3(String, String?);
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg

                            class Outer {
                                fun method1(string: String, maybeString: String?): String? = null
                                fun method2(string: String, maybeString: String?): String? = null
                                fun method3(maybeString: String?, string : String): String = ""
                                class Inner {
                                    fun method2(string: String, maybeString: String?): String? = null
                                    fun method3(maybeString: String?, string : String): String = ""
                                }
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Field nullness changes are not allowed`() {
        check(
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        field public String changeNonNullToNullable;
                        field public String changeNonNullToPlatform;
                        field public String? changeNullableToNonNull;
                        field public String? changeNullableToPlatform;
                        field public String! changePlatformToNonNull;
                        field public String! changePlatformToNullable;
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import androidx.annotation.NonNull;
                            import androidx.annotation.Nullable;
                            public class Foo {
                                public @Nullable String changeNonNullToNullable;
                                public String changeNonNullToPlatform;
                                public @NonNull String changeNullableToNonNull;
                                public String changeNullableToPlatform;
                                public @NonNull String changePlatformToNonNull;
                                public @Nullable String changePlatformToNullable;
                            }
                        """,
                    ),
                    androidxNonNullSource,
                    androidxNullableSource,
                ),
            expectedIssues =
                """
                    src/test/pkg/Foo.java:5: error: Attempted to change nullability of java.lang.String (from NONNULL to NULLABLE) in field test.pkg.Foo.changeNonNullToNullable [InvalidNullConversion]
                    src/test/pkg/Foo.java:6: error: Attempted to remove nullability from java.lang.String (was NONNULL) in field test.pkg.Foo.changeNonNullToPlatform [InvalidNullConversion]
                    src/test/pkg/Foo.java:7: error: Attempted to change nullability of java.lang.String (from NULLABLE to NONNULL) in field test.pkg.Foo.changeNullableToNonNull [InvalidNullConversion]
                    src/test/pkg/Foo.java:8: error: Attempted to remove nullability from java.lang.String (was NULLABLE) in field test.pkg.Foo.changeNullableToPlatform [InvalidNullConversion]
                """,
        )
    }

    @Test
    fun `Nullable to non-null method return in non-final method is not allowed`() {
        check(
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class FinalClass {
                        ctor public FinalClass();
                        method public String? methodInFinalClass();
                      }
                      public class NonFinalClass {
                        ctor public NonFinalClass();
                        method public final String? finalMethod();
                        method public String? nonFinalMethod();
                      }
                      public final class NoPublicCtorClass {
                        method public String? methodInNoPublicCtorClass();
                      }
                      public abstract sealed class SealedClass {
                        ctor public SealedClass();
                        method public final String? methodInSealedClass();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    signature(
                        """
                            // Signature format: 5.0
                            package test.pkg {
                              public final class FinalClass {
                                ctor public FinalClass();
                                method public String methodInFinalClass();
                              }
                              public class NonFinalClass {
                                ctor public NonFinalClass();
                                method public final String finalMethod();
                                method public String nonFinalMethod();
                              }
                              public final class NoPublicCtorClass {
                                method public String methodInNoPublicCtorClass();
                              }
                              public abstract sealed class SealedClass {
                                ctor public SealedClass();
                                method public final String methodInSealedClass();
                              }
                            }
                        """,
                    )
                ),
            expectedIssues =
                """
                    api.txt:10: warning: Attempted to change nullability of java.lang.String (from NULLABLE to NONNULL) in method test.pkg.NonFinalClass.nonFinalMethod() (ErrorWhenNew) [InvalidNullConversion]
                """
        )
    }

    @Test
    fun `Non-null to nullable parameter in non-final method is not allowed`() {
        check(
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class FinalClass {
                        ctor public FinalClass();
                        method public void methodInFinalClass(String);
                      }
                      public class NonFinalClass {
                        ctor public NonFinalClass();
                        method public final void finalMethod(String);
                        method public void nonFinalMethod(String);
                      }
                      public final class NoPublicCtorClass {
                        method public void methodInNoPublicCtorClass(String);
                      }
                      public abstract sealed class SealedClass {
                        ctor public SealedClass();
                        method public final void methodInSealedClass(String);
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    signature(
                        """
                            // Signature format: 5.0
                            package test.pkg {
                              public final class FinalClass {
                                ctor public FinalClass();
                                method public void methodInFinalClass(String?);
                              }
                              public class NonFinalClass {
                                ctor public NonFinalClass();
                                method public final void finalMethod(String?);
                                method public void nonFinalMethod(String?);
                              }
                              public final class NoPublicCtorClass {
                                method public void methodInNoPublicCtorClass(String?);
                              }
                              public abstract sealed class SealedClass {
                                ctor public SealedClass();
                                method public final void methodInSealedClass(String?);
                              }
                            }
                        """,
                    )
                ),
            expectedIssues =
                """
                    api.txt:10: warning: Attempted to change nullability of java.lang.String (from NONNULL to NULLABLE) in parameter arg1 in test.pkg.NonFinalClass.nonFinalMethod(String arg1) (ErrorWhenNew) [InvalidNullConversion]
                """
        )
    }

    @Test
    fun `Array type component nullness changes`() {
        check(
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method public String[] foo(String![], String?[], String?[]?[]);
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    signature(
                        """
                            // Signature format: 5.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public String?[] foo(String[], String[], String![][]);
                              }
                            }
                        """
                    )
                ),
            expectedIssues =
                """
                    api.txt:5: error: Attempted to change nullability of java.lang.String (from NONNULL to NULLABLE) in method test.pkg.Foo.foo(String[],String[],String[][]) [InvalidNullConversion]
                    api.txt:5: error: Attempted to change nullability of java.lang.String (from NULLABLE to NONNULL) in parameter arg2 in test.pkg.Foo.foo(String[] arg1, String[] arg2, String[][] arg3) [InvalidNullConversion]
                    api.txt:5: error: Attempted to change nullability of java.lang.String[] (from NULLABLE to NONNULL) in parameter arg3 in test.pkg.Foo.foo(String[] arg1, String[] arg2, String[][] arg3) [InvalidNullConversion]
                    api.txt:5: error: Attempted to remove nullability from java.lang.String (was NULLABLE) in parameter arg3 in test.pkg.Foo.foo(String[] arg1, String[] arg2, String[][] arg3) [InvalidNullConversion]
                """,
        )
    }

    @Test
    fun `Class type argument nullness changes`() {
        check(
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method public java.util.Map<java.lang.Number?, java.util.List<java.lang.String>!> foo();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    signature(
                        """
                            // Signature format: 5.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public java.util.Map<java.lang.Number!, java.util.List<java.lang.String?>> foo();
                              }
                            }
                        """
                    )
                ),
            expectedIssues =
                """
                    api.txt:5: error: Attempted to change nullability of java.lang.String (from NONNULL to NULLABLE) in method test.pkg.Foo.foo() [InvalidNullConversion]
                    api.txt:5: error: Attempted to remove nullability from java.lang.Number (was NULLABLE) in method test.pkg.Foo.foo() [InvalidNullConversion]
                """,
        )
    }

    @Test
    fun `Outer class type arguments nullness changes`() {
        check(
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method public test.pkg.Outer<java.lang.String>.Inner foo();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    signature(
                        """
                            // Signature format: 5.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public test.pkg.Outer<java.lang.String?>.Inner foo();
                              }
                            }
                        """
                    )
                ),
            expectedIssues =
                """
                    api.txt:5: error: Attempted to change nullability of java.lang.String (from NONNULL to NULLABLE) in method test.pkg.Foo.foo() [InvalidNullConversion]
                """,
        )
    }

    @Test
    fun `Wildcard bounds nullness changes`() {
        check(
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method public java.util.Map<? extends java.lang.Number?, ? super java.lang.String> foo();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    signature(
                        """
                            // Signature format: 5.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public java.util.Map<? extends java.lang.Number, ? super java.lang.String!> foo();
                              }
                            }
                        """
                    )
                ),
            expectedIssues =
                """
                    api.txt:5: warning: Attempted to change nullability of java.lang.Number (from NULLABLE to NONNULL) in method test.pkg.Foo.foo() (ErrorWhenNew) [InvalidNullConversion]
                    api.txt:5: error: Attempted to remove nullability from java.lang.String (was NONNULL) in method test.pkg.Foo.foo() [InvalidNullConversion]
                """,
        )
    }
}
