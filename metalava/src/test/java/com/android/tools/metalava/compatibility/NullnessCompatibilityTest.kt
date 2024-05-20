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
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class NullnessCompatibilityTest : DriverTest() {
    @Test
    fun `Compare signatures with Kotlin nullability from signature`() {
        check(
            expectedIssues =
                """
                    load-api.txt:5: error: Attempted to remove @NonNull annotation from parameter str in test.pkg.Foo.method1(int p, Integer int2, int p1, String str, java.lang.String... args) [InvalidNullConversion]
                    load-api.txt:7: error: Attempted to change parameter from @Nullable to @NonNull: incompatible change for parameter str in test.pkg.Foo.method3(String str, int p, int int2) [InvalidNullConversion]
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
                    src/test/pkg/test.kt:2: error: Attempted to change parameter from @Nullable to @NonNull: incompatible change for parameter str1 in test.pkg.TestKt.fun1(String str1, String str2, java.util.List<java.lang.String> list) [InvalidNullConversion]
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
    fun `Flag invalid nullness changes`() {
        check(
            expectedIssues =
                """
                    load-api.txt:6: error: Attempted to remove @Nullable annotation from method test.pkg.MyTest.convert3(Float) [InvalidNullConversion]
                    load-api.txt:6: error: Attempted to remove @Nullable annotation from parameter arg1 in test.pkg.MyTest.convert3(Float arg1) [InvalidNullConversion]
                    load-api.txt:7: error: Attempted to remove @NonNull annotation from method test.pkg.MyTest.convert4(Float) [InvalidNullConversion]
                    load-api.txt:7: error: Attempted to remove @NonNull annotation from parameter arg1 in test.pkg.MyTest.convert4(Float arg1) [InvalidNullConversion]
                    load-api.txt:8: error: Attempted to change parameter from @Nullable to @NonNull: incompatible change for parameter arg1 in test.pkg.MyTest.convert5(Float arg1) [InvalidNullConversion]
                    load-api.txt:9: error: Attempted to change method return from @NonNull to @Nullable: incompatible change for method test.pkg.MyTest.convert6(Float) [InvalidNullConversion]
                """,
            format = FileFormat.V2,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public class MyTest {
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
                      public class MyTest {
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
                    src/test/pkg/Outer.kt:5: error: Attempted to change method return from @NonNull to @Nullable: incompatible change for method test.pkg.Outer.method2(String,String) [InvalidNullConversion]
                    src/test/pkg/Outer.kt:5: error: Attempted to change parameter from @Nullable to @NonNull: incompatible change for parameter string in test.pkg.Outer.method2(String string, String maybeString) [InvalidNullConversion]
                    src/test/pkg/Outer.kt:6: error: Attempted to change parameter from @Nullable to @NonNull: incompatible change for parameter string in test.pkg.Outer.method3(String maybeString, String string) [InvalidNullConversion]
                    src/test/pkg/Outer.kt:8: error: Attempted to change method return from @NonNull to @Nullable: incompatible change for method test.pkg.Outer.Inner.method2(String,String) [InvalidNullConversion]
                    src/test/pkg/Outer.kt:8: error: Attempted to change parameter from @Nullable to @NonNull: incompatible change for parameter string in test.pkg.Outer.Inner.method2(String string, String maybeString) [InvalidNullConversion]
                    src/test/pkg/Outer.kt:9: error: Attempted to change parameter from @Nullable to @NonNull: incompatible change for parameter string in test.pkg.Outer.Inner.method3(String maybeString, String string) [InvalidNullConversion]
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
}
