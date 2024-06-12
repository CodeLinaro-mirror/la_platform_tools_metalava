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

package com.android.tools.metalava.lint

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.ARG_ERROR
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.libcoreNonNullSource
import com.android.tools.metalava.libcoreNullableSource
import com.android.tools.metalava.testing.java
import org.junit.Test
import org.junit.runners.Parameterized

class AccessorNullabilityTest : DriverTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    data class TestParams(
        val name: String,
        val getterType: String,
        val setterType: String = getterType,
        val expectedIssues: String? = null,
    ) {
        override fun toString() = name

        val expectedFail =
            if (expectedIssues == null) {
                null
            } else {
                DefaultLintErrorMessage
            }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters fun params() = params

        private val params =
            listOf(
                // Passing cases
                TestParams(
                    name = "matching primitive",
                    getterType = "int",
                ),
                TestParams(
                    name = "matching simple class",
                    getterType = "String",
                ),
                TestParams(
                    name = "matching primitive array",
                    getterType = "int[]",
                ),
                TestParams(
                    name = "matching string array",
                    getterType = "java.lang.@Nullable String @NonNull []",
                ),
                TestParams(
                    name = "matching string 2d array",
                    getterType = "java.lang.@NonNull String[] @Nullable []",
                ),
                TestParams(
                    name = "matching class with argument",
                    getterType = "java.util.List<@NonNull String>",
                ),
                TestParams(
                    name = "matching class with nested argument",
                    getterType = "java.util.@NonNull List<java.util.@Nullable List<String>>",
                ),
                TestParams(
                    name = "matching class with two arguments",
                    getterType = "java.util.@Nullable Map<@NonNull Number, String>",
                ),
                TestParams(
                    name = "matching extends bound",
                    getterType = "java.util.@Nullable List<? extends String>",
                ),
                TestParams(
                    name = "matching super bound",
                    getterType = "java.util.@Nullable List<? super @Nullable String>",
                ),
                TestParams(
                    name = "matching type parameter",
                    getterType = "@NonNull T",
                ),

                // Failing cases
                TestParams(
                    name = "mismatched simple class",
                    getterType = "String",
                    setterType = "@Nullable String",
                    expectedIssues =
                        "src/test/pkg/Foo.java:7: error: Nullability of java.lang.String! in getter method test.pkg.Foo.getValue() does not match java.lang.String? in corresponding setter method test.pkg.Foo.setValue(String) [GetterSetterNullability]",
                ),
                TestParams(
                    name = "mismatched primitive array",
                    getterType = "int @NonNull []",
                    setterType = "int[]",
                    expectedIssues =
                        "src/test/pkg/Foo.java:7: error: Nullability of int[] in getter method test.pkg.Foo.getValue() does not match int[]! in corresponding setter method test.pkg.Foo.setValue(int[]) [GetterSetterNullability]",
                ),
                TestParams(
                    name = "mismatched string array",
                    getterType = "java.lang.@Nullable String[]",
                    setterType = "java.lang.@Nullable String @NonNull []",
                    expectedIssues =
                        "src/test/pkg/Foo.java:7: error: Nullability of java.lang.String?[]! in getter method test.pkg.Foo.getValue() does not match java.lang.String?[] in corresponding setter method test.pkg.Foo.setValue(String[]) [GetterSetterNullability]",
                ),
                TestParams(
                    name = "mismatched string 2d array",
                    getterType = "String[] @Nullable []",
                    setterType = "String @NonNull [] @Nullable []",
                    expectedIssues =
                        "src/test/pkg/Foo.java:7: error: Nullability of java.lang.String![]?[]! in getter method test.pkg.Foo.getValue() does not match java.lang.String![]?[] in corresponding setter method test.pkg.Foo.setValue(String[][]) [GetterSetterNullability]",
                ),
                TestParams(
                    name = "mismatched class with argument",
                    getterType = "java.util.List<@NonNull String>",
                    setterType = "java.util.List<String>",
                    expectedIssues =
                        "src/test/pkg/Foo.java:7: error: Nullability of java.lang.String in getter method test.pkg.Foo.getValue() does not match java.lang.String! in corresponding setter method test.pkg.Foo.setValue(java.util.List<java.lang.String>) [GetterSetterNullability]",
                ),
                TestParams(
                    name = "mismatched class with nested argument",
                    getterType = "java.util.@NonNull List<java.util.@Nullable List<String>>",
                    setterType =
                        "java.util.@NonNull List<java.util.@Nullable List<@NonNull String>>",
                    expectedIssues =
                        "src/test/pkg/Foo.java:7: error: Nullability of java.lang.String! in getter method test.pkg.Foo.getValue() does not match java.lang.String in corresponding setter method test.pkg.Foo.setValue(java.util.List<java.util.List<java.lang.String>>) [GetterSetterNullability]",
                ),
                TestParams(
                    name = "mismatched class with two arguments",
                    getterType = "java.util.@Nullable Map<@NonNull Number, String>",
                    setterType = "java.util.@Nullable Map<@Nullable Number, @NonNull String>",
                    expectedIssues =
                        """
                        src/test/pkg/Foo.java:7: error: Nullability of java.lang.Number in getter method test.pkg.Foo.getValue() does not match java.lang.Number? in corresponding setter method test.pkg.Foo.setValue(java.util.Map<java.lang.Number,java.lang.String>) [GetterSetterNullability]
                        src/test/pkg/Foo.java:7: error: Nullability of java.lang.String! in getter method test.pkg.Foo.getValue() does not match java.lang.String in corresponding setter method test.pkg.Foo.setValue(java.util.Map<java.lang.Number,java.lang.String>) [GetterSetterNullability]
                        """,
                ),
                TestParams(
                    name = "mismatched extends bound",
                    getterType = "java.util.@Nullable List<? extends String>",
                    setterType = "java.util.@Nullable List<? extends @NonNull String>",
                    expectedIssues =
                        "src/test/pkg/Foo.java:7: error: Nullability of java.lang.String! in getter method test.pkg.Foo.getValue() does not match java.lang.String in corresponding setter method test.pkg.Foo.setValue(java.util.List<? extends java.lang.String>) [GetterSetterNullability]",
                ),
                TestParams(
                    name = "mismatched super bound",
                    getterType = "java.util.@Nullable List<? super @Nullable String>",
                    setterType = "java.util.@Nullable List<? super @NonNull String>",
                    expectedIssues =
                        "src/test/pkg/Foo.java:7: error: Nullability of java.lang.String? in getter method test.pkg.Foo.getValue() does not match java.lang.String in corresponding setter method test.pkg.Foo.setValue(java.util.List<? super java.lang.String>) [GetterSetterNullability]",
                ),
                TestParams(
                    name = "mismatched type parameter",
                    getterType = "@NonNull T",
                    setterType = "T",
                    expectedIssues =
                        "src/test/pkg/Foo.java:7: error: Nullability of T in getter method test.pkg.Foo.getValue() does not match T! in corresponding setter method test.pkg.Foo.setValue(T) [GetterSetterNullability]",
                ),
            )
    }

    @Test
    fun `Nullability of getters and setters must match`() {
        check(
            apiLint = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import libcore.util.NonNull;
                            import libcore.util.Nullable;

                            public class Foo<T> {
                                public ${params.getterType} getValue() {}
                                public void setValue(${params.setterType} arg) {}
                            }
                        """
                    ),
                    libcoreNonNullSource,
                    libcoreNullableSource,
                ),
            extraArguments =
                arrayOf(
                    ARG_ERROR,
                    "GetterSetterNullability",
                    ARG_HIDE,
                    "MissingNullability",
                    ARG_HIDE,
                    "NullableCollection",
                    ARG_HIDE,
                    "ArrayReturn"
                ),
            expectedFail = params.expectedFail,
            expectedIssues = params.expectedIssues,
        )
    }
}
