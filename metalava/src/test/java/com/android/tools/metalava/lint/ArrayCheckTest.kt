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
import com.android.tools.metalava.androidxNonNullSource
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.cli.lint.ARG_API_LINT
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.Test

class ArrayCheckTest : DriverTest() {
    @Test
    fun `Return collections instead of arrays - java`() {
        check(
            extraArguments = arrayOf(ARG_API_LINT, ARG_HIDE, "AutoBoxing"),
            expectedIssues =
                """
                    src/android/pkg/ArrayTest.java:8: warning: Method should return Collection<Object> (or subclass) instead of raw array; was `java.lang.Object[]` [ArrayReturn]
                    src/android/pkg/ArrayTest.java:9: warning: Method parameter should be Collection<Number> (or subclass) instead of raw array; was `java.lang.Number[]` [ArrayReturn]
                    src/android/pkg/ArrayTest.java:10: warning: Method parameter should be Collection<T> (or subclass) instead of raw array; was `T[]` [ArrayReturn]
                    src/android/pkg/ArrayTest.java:11: warning: Method parameter should be Collection<T> (or subclass) instead of raw array; was `T[]` [ArrayReturn]
                    src/android/pkg/ArrayTest.java:12: warning: Method parameter should be Collection<byte[]> (or subclass) instead of raw array; was `byte[][]` [ArrayReturn]
                    src/android/pkg/ArrayTest.java:13: warning: Method parameter should be Collection<Number[]> (or subclass) instead of raw array; was `java.lang.Number[][]` [ArrayReturn]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;

                            import androidx.annotation.NonNull;
                            import androidx.annotation.Nullable;

                            public class ArrayTest {
                                @NonNull
                                public Object[] error1() { throw new RuntimeException(); }
                                public void error2(@NonNull Number[] i) { }
                                public <T> void error3(@NonNull T[] i) { }
                                public <T extends ArrayTest> void error4(@NonNull T[] i) { }
                                public void error5(@NonNull byte[][] i) { }
                                public void error6(@NonNull Number[][] i) { }

                                @NonNull
                                public int[] ok1() { throw new RuntimeException(); }
                                @NonNull
                                public String[] ok2() { throw new RuntimeException(); }
                                public void ok3(@Nullable int[] i) { }
                                public void ok4(@NonNull Number... args) { }
                            }
                        """
                    ),
                    androidxNonNullSource,
                    androidxNullableSource,
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Return collections instead of arrays - kotlin`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            fun okMethod(vararg values: Integer, foo: Float, bar: Float)
                        """
                    ),
                    androidxNonNullSource,
                    androidxNullableSource,
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Allow arrays for kotlin only APIs`() {
        check(
            apiLint = "",
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                    src/test/pkg/ConstructorCanBeUsedFromJava.kt:2: warning: Method parameter should be Collection<Number> (or subclass) instead of raw array; was `java.lang.Number[]` [ArrayReturn]
                    src/test/pkg/IntValue.kt:2: error: Value classes should not be public in APIs targeting Java clients. [ValueClassDefinition]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            @JvmInline value class IntValue(val value: Int)
                        """
                    ),
                    kotlin(
                        """
                            package test.pkg
                            class ConstructorCanBeUsedFromJava(i: Int, arr: Array<Number>)
                        """
                    ),
                    kotlin(
                        """
                            package test.pkg
                            // IntValue is a value class type, which can't be used from Java
                            // Arrays can be used for Kotlin only APIs, so this usage is okay
                            class ConstructorCannotBeUsedFromJava(iv: IntValue, arr: Array<Number>)
                        """
                    ),
                ),
        )
    }
}
