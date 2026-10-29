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

package com.android.tools.metalava.lint

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.model.ANDROID_HIDE
import com.android.tools.metalava.model.ANDROID_SYSTEM_API
import com.android.tools.metalava.model.text.FORMAT_V6_WITH_JAVA_STYLE
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import org.junit.Test

class RecordLintTest : DriverTest() {
    @Test
    fun `Test using java-lang-Record class`() {
        val testOtherGetterLocation =
            if (codebaseCreatorConfig.providerName == "turbine") "" else "1:"
        val testConstructorLocation =
            if (codebaseCreatorConfig.providerName == "turbine") "" else "3:"
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                    src/android/pkg/Test.java:$testOtherGetterLocation error: Return type of method android.pkg.Test.other() contains java.lang.Record, that can cause issues for desugared record classes, please use java.lang.Object instead [UsingJavaLangRecord]
                    src/android/pkg/Test.java:$testConstructorLocation error: Type of parameter other in android.pkg.Test(int a, Record other) contains java.lang.Record, that can cause issues for desugared record classes, please use java.lang.Object instead [UsingJavaLangRecord]
                    src/android/pkg/Test.java:5: error: Type of record component android.pkg.Test.other contains java.lang.Record, that can cause issues for desugared record classes, please use java.lang.Object instead [UsingJavaLangRecord]
                    src/android/pkg/UsingJavaLangRecord.java:5: error: Implemented interface of class android.pkg.UsingJavaLangRecord contains java.lang.Record, that can cause issues for desugared record classes, please use java.lang.Object instead [UsingJavaLangRecord]
                    src/android/pkg/UsingJavaLangRecord.java:5: error: Super class of class android.pkg.UsingJavaLangRecord contains java.lang.Record, that can cause issues for desugared record classes, please use java.lang.Object instead [UsingJavaLangRecord]
                    src/android/pkg/UsingJavaLangRecord.java:6: error: Type of field android.pkg.UsingJavaLangRecord.field contains java.lang.Record, that can cause issues for desugared record classes, please use java.lang.Object instead [UsingJavaLangRecord]
                    src/android/pkg/UsingJavaLangRecord.java:7: error: Return type of method android.pkg.UsingJavaLangRecord.method(Record) contains java.lang.Record, that can cause issues for desugared record classes, please use java.lang.Object instead [UsingJavaLangRecord]
                    src/android/pkg/UsingJavaLangRecord.java:7: error: Type of parameter record in android.pkg.UsingJavaLangRecord.method(Record record) contains java.lang.Record, that can cause issues for desugared record classes, please use java.lang.Object instead [UsingJavaLangRecord]
                """,
            sourceFiles =
                arrayOf(
                    KnownSourceFiles.typeUseOnlyNonNullSource,
                    java(
                        """
                            package android.pkg;

                            public class SuperClass<T> {
                            }
                        """
                    ),
                    java(
                        """
                            package android.pkg;

                            public interface Interface<T> {
                            }
                        """
                    ),
                    java(
                        """
                            package android.pkg;

                            import type.use.only.NonNull;

                            public class UsingJavaLangRecord<T extends Record> extends SuperClass<Record> implements Interface<Record> {
                                public final @NonNull Record field;
                                public @NonNull Record method(@NonNull Record record) {return record;}
                            }
                        """
                    ),
                    java(
                        """
                            package android.pkg;

                            import type.use.only.NonNull;

                            public record Test(int a, @NonNull Record other) {
                            }
                        """
                    ),
                ),
            javaLanguageLevel = "16", // required for records
        )
    }

    @Test
    fun `Test system api record class`() {
        check(
            format = FORMAT_V6_WITH_JAVA_STYLE,
            showAnnotations = arrayOf(ANDROID_SYSTEM_API),
            hideAnnotations = arrayOf(ANDROID_HIDE, "test.pkg.HideComponent"),
            apiLint = "", // enabled
            sourceFiles =
                arrayOf(
                    KnownSourceFiles.hideAnnotation,
                    KnownSourceFiles.systemApiSource,
                    java(
                        """
                            package test.pkg;

                            import android.annotation.Hide;
                            import android.annotation.SystemApi;

                            @SystemApi
                            @Hide
                            public record Test(
                                int a,
                                int b
                            ) {}
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    package test.pkg {
                      public record Test {
                        record_component #0 a: int;
                        record_component #1 b: int;
                        ctor public Test(int, int);
                        method public int a();
                        method public int b();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public record Test(int a, int b) {
                            public int a() { throw new RuntimeException("Stub!"); }
                            public int b() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            javaLanguageLevel = "16", // required for records
        )
    }

    @Test
    fun `Test hiding record component and accessor method`() {
        // Turbine does not track the location of the component yet, instead it uses the class
        // location.
        val componentCLocation = if (codebaseCreatorConfig.providerName == "turbine") 5 else 13
        val componentAGetterLocation =
            if (codebaseCreatorConfig.providerName == "turbine") "" else ":1"
        check(
            format = FORMAT_V6_WITH_JAVA_STYLE,
            hideAnnotations = arrayOf(ANDROID_HIDE, "test.pkg.HideComponent"),
            apiLint = "", // enabled
            expectedIssues =
                """
                    src/test/pkg/Test.java$componentAGetterLocation: error: Cannot hide record component getter method test.pkg.Test.a() as it is an indivisible part of a record class [HidingRecordComponent]
                    src/test/pkg/Test.java:$componentCLocation: error: Cannot hide record component test.pkg.Test.c as record components are an indivisible part of a record class [HidingRecordComponent]
                    src/test/pkg/Test.java:16: error: Cannot hide canonical constructor test.pkg.Test(int,int,int) as it is an indivisible part of a record class [HidingRecordComponent]
                    src/test/pkg/Test.java:20: error: Cannot hide record component getter method test.pkg.Test.b() as it is an indivisible part of a record class [HidingRecordComponent]
                """,
            sourceFiles =
                arrayOf(
                    KnownSourceFiles.hideAnnotation,
                    java(
                        """
                            package test.pkg;

                            import android.annotation.Hide;
                            import java.lang.annotation.ElementType;
                            import java.lang.annotation.Target;

                            @Target(ElementType.RECORD_COMPONENT)
                            @Hide
                            public @interface HideComponent {}
                        """,
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.annotation.Hide;

                            public record Test(
                                // This will not apply to the component but will apply to the
                                // constructor parameter and accessor for this component.
                                @Hide
                                int a,
                                int b,
                                // This will only apply to the component.
                                @HideComponent
                                int c
                            ) {
                                @Hide
                                public Test {
                                }

                                @Hide
                                public int b() { return b; }
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    package test.pkg {
                      public record Test {
                        record_component #0 a: int;
                        record_component #1 b: int;
                        record_component #2 c: int;
                        ctor public Test(int, int, int);
                        method public int a();
                        method public int b();
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
                            public record Test(int a, int b, int c) {
                            public int a() { throw new RuntimeException("Stub!"); }
                            public int b() { throw new RuntimeException("Stub!"); }
                            public int c() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            javaLanguageLevel = "16", // required for records
        )
    }

    @Test
    fun `Test using arrays in record component types`() {
        check(
            format = FORMAT_V6_WITH_JAVA_STYLE,
            apiLint = "", // enabled
            expectedIssues =
                """
                    src/test/pkg/Test.java:4: error: Record component test.pkg.Test.a type 'int[]' contains an array type; they do not work correctly with Record methods [ArrayRecordComponent]
                    src/test/pkg/Test.java:4: error: Record component test.pkg.Test.b type 'java.util.List<long[]>' contains an array type; they do not work correctly with Record methods [ArrayRecordComponent]
                """,
            sourceFiles =
                arrayOf(
                    KnownSourceFiles.typeUseOnlyNonNullSource,
                    java(
                        """
                            package test.pkg;
                            import java.util.List;
                            import type.use.only.NonNull;
                            public record Test(int @NonNull [] a, @NonNull List<long @NonNull[]> b) {}
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    // Signature format: 6.0
                    // - style=java
                    package test.pkg {
                      public record Test {
                        record_component #0 @NonNull a: int[];
                        record_component #1 @NonNull b: java.util.List<long[]>;
                        ctor public Test(@NonNull int[], @NonNull java.util.List<long[]>);
                        method @NonNull public int[] a();
                        method @NonNull public java.util.List<long[]> b();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public record Test(@android.annotation.NonNull int[] a, @android.annotation.NonNull java.util.List<long[]> b) {
                            @android.annotation.NonNull
                            public int[] a() { throw new RuntimeException("Stub!"); }
                            @android.annotation.NonNull
                            public java.util.List<long[]> b() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            javaLanguageLevel = "16", // required for records
        )
    }
}
