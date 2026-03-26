/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.metalava.model.text.FORMAT_V6_WITHOUT_JAVA_RECORD_CLASSES
import com.android.tools.metalava.model.text.FORMAT_V6_WITH_JAVA_RECORD_CLASSES
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import org.junit.Test

class RecordClassTest : DriverTest() {
    @Test
    fun `Test record class with java-record-classes=no`() {
        check(
            format = FORMAT_V6_WITHOUT_JAVA_RECORD_CLASSES,
            sourceFiles =
                arrayOf(
                    KnownSourceFiles.typeUseOnlyNonNullSource,
                    java(
                        """
                            package test.pkg;

                            import type.use.only.NonNull;

                            public record Test(int c) {
                            }
                        """
                    ),
                ),
            api =
                """
                    package test.pkg {
                      public class Test {
                        ctor public Test(int);
                        method public int c();
                      }
                    }
                """,
            checkCompilation = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            public Test(int c) { throw new RuntimeException("Stub!"); }
                            public int c() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test record class with int components and additional String constructor`() {
        check(
            format = FORMAT_V6_WITH_JAVA_RECORD_CLASSES,
            sourceFiles =
                arrayOf(
                    KnownSourceFiles.typeUseOnlyNonNullSource,
                    java(
                        """
                            package test.pkg;

                            import type.use.only.NonNull;

                            public record Test(int c1, int c2) {
                                public Test(@NonNull String c1, @NonNull String c2) {}

                                public int sum() {return c1 + c2;}
                            }
                        """
                    ),
                ),
            api =
                """
                    package test.pkg {
                      public record Test {
                        ctor public Test(int, int);
                        ctor public Test(@NonNull String, @NonNull String);
                        method public int c1();
                        method public int c2();
                        method public int sum();
                      }
                    }
                """,
            // TODO(b/482390286): Will not compile as it does not declare its components correctly.
            checkCompilation = false,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public record Test {
                            public Test(int c1, int c2) { throw new RuntimeException("Stub!"); }
                            public Test(@android.annotation.NonNull java.lang.String c1, @android.annotation.NonNull java.lang.String c2) { throw new RuntimeException("Stub!"); }
                            public int c1() { throw new RuntimeException("Stub!"); }
                            public int c2() { throw new RuntimeException("Stub!"); }
                            public int sum() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test record class with String components and additional int constructor`() {
        check(
            format = FORMAT_V6_WITH_JAVA_RECORD_CLASSES,
            sourceFiles =
                arrayOf(
                    KnownSourceFiles.typeUseOnlyNonNullSource,
                    java(
                        """
                            package test.pkg;

                            import type.use.only.NonNull;

                            public record Test(@NonNull String c1, @NonNull String c2) {
                                public Test(int c1, int c2) {}

                                public @NonNull String sum() {return c1 + c2;}
                            }
                        """
                    ),
                ),
            api =
                """
                    package test.pkg {
                      public record Test {
                        ctor public Test(int, int);
                        ctor public Test(@NonNull String, @NonNull String);
                        method @NonNull public String c1();
                        method @NonNull public String c2();
                        method @NonNull public String sum();
                      }
                    }
                """,
            // TODO(b/482390286): Will not compile as it does not declare its components correctly.
            checkCompilation = false,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public record Test {
                            public Test(int c1, int c2) { throw new RuntimeException("Stub!"); }
                            public Test(@android.annotation.NonNull java.lang.String c1, @android.annotation.NonNull java.lang.String c2) { throw new RuntimeException("Stub!"); }
                            @android.annotation.NonNull
                            public java.lang.String c1() { throw new RuntimeException("Stub!"); }
                            @android.annotation.NonNull
                            public java.lang.String c2() { throw new RuntimeException("Stub!"); }
                            @android.annotation.NonNull
                            public java.lang.String sum() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test record class with int component and overrides of Object methods`() {
        check(
            format = FORMAT_V6_WITH_JAVA_RECORD_CLASSES,
            sourceFiles =
                arrayOf(
                    KnownSourceFiles.typeUseOnlyNonNullSource,
                    java(
                        """
                            package test.pkg;

                            import type.use.only.NonNull;

                            public record Test(int c) {
                                @Override public boolean equals(Object obj) {
                                    return false;
                                }
                                @Override public int hashCode() {
                                    return 0;
                                }
                                @SuppressWarnings("NullableProblems")
                                @Override public @NonNull String toString() {
                                    return "";
                                }
                            }
                        """
                    ),
                ),
            api =
                """
                    package test.pkg {
                      public record Test {
                        ctor public Test(int);
                        method public int c();
                      }
                    }
                """,
            // TODO(b/482390286): Will not compile as it does not declare its components correctly.
            checkCompilation = false,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public record Test {
                            public Test(int c) { throw new RuntimeException("Stub!"); }
                            public int c() { throw new RuntimeException("Stub!"); }
                            public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }
                            public int hashCode() { throw new RuntimeException("Stub!"); }
                            @android.annotation.NonNull
                            public java.lang.String toString() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }
}
