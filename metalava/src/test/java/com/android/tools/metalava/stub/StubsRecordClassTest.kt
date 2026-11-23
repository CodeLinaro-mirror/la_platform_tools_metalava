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

package com.android.tools.metalava.stub

import com.android.tools.metalava.model.text.FORMAT_V6_WITHOUT_JAVA_RECORD_CLASSES
import com.android.tools.metalava.model.text.FORMAT_V6_WITH_JAVA_STYLE
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import org.junit.Test

class StubsRecordClassTest : AbstractStubsTest() {
    @Test
    fun `Test record class with java-record-classes=no`() {
        checkStubs(
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
                      public final class Test {
                        ctor public Test(int);
                        method public int c();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public final class Test {
                            public Test(int c) { throw new RuntimeException("Stub!"); }
                            public int c() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            javaLanguageLevel = "16", // required for records
        )
    }

    @Test
    fun `Test record class with int components and additional String constructor`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_STYLE,
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
                        record_component #0 c1: int;
                        record_component #1 c2: int;
                        ctor public Test(int, int);
                        ctor public Test(@NonNull String, @NonNull String);
                        method public int c1();
                        method public int c2();
                        method public int sum();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public record Test(int c1, int c2) {
                            public Test(@android.annotation.NonNull java.lang.String c1, @android.annotation.NonNull java.lang.String c2) { this(0, 0); throw new RuntimeException("Stub!"); }
                            public int c1() { throw new RuntimeException("Stub!"); }
                            public int c2() { throw new RuntimeException("Stub!"); }
                            public int sum() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            javaLanguageLevel = "16", // required for records
        )
    }

    @Test
    fun `Test record class with String components and additional int constructor`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_STYLE,
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
                        record_component #0 @NonNull c1: String;
                        record_component #1 @NonNull c2: String;
                        ctor public Test(int, int);
                        ctor public Test(@NonNull String, @NonNull String);
                        method @NonNull public String c1();
                        method @NonNull public String c2();
                        method @NonNull public String sum();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public record Test(@android.annotation.NonNull java.lang.String c1, @android.annotation.NonNull java.lang.String c2) {
                            public Test(int c1, int c2) { this("", ""); throw new RuntimeException("Stub!"); }
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
            javaLanguageLevel = "16", // required for records
        )
    }

    @Test
    fun `Test record class with int component and overrides of Object methods`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_STYLE,
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
                        record_component #0 c: int;
                        ctor public Test(int);
                        method public int c();
                      }
                    }
                """,
            // Includes extra overrides that are not present in the signature file.
            checkTextStubEquivalence = false,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public record Test(int c) {
                            public int c() { throw new RuntimeException("Stub!"); }
                            public boolean equals(java.lang.Object obj) { throw new RuntimeException("Stub!"); }
                            public int hashCode() { throw new RuntimeException("Stub!"); }
                            @android.annotation.NonNull
                            public java.lang.String toString() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            javaLanguageLevel = "16", // required for records
        )
    }

    @Test
    fun `Test record class with generic component`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_STYLE,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public record Test<T>(T c) {}
                        """
                    ),
                ),
            api =
                """
                    package test.pkg {
                      public record Test<T> {
                        record_component #0 c: T;
                        ctor public Test(T);
                        method public T c();
                      }
                    }
               """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public record Test<T>(T c) {
                            public T c() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            javaLanguageLevel = "16", // required for records
        )
    }

    @Test
    fun `Test record class implementing an interface`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_STYLE,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public interface Interface {
                                int c();
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public record Test(int c) implements Interface {}
                        """
                    ),
                ),
            api =
                """
                    package test.pkg {
                      public interface Interface {
                        method public int c();
                      }
                      public record Test implements test.pkg.Interface {
                        record_component #0 c: int;
                        ctor public Test(int);
                        method public int c();
                      }
                    }
               """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public record Test(int c) implements test.pkg.Interface {
                            public int c() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            javaLanguageLevel = "16", // required for records
        )
    }
}
