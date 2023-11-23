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

package com.android.tools.metalava.model.testsuite.typeitem

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeStringConfiguration
import com.android.tools.metalava.model.isNullnessAnnotation
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.TestParameters
import com.android.tools.metalava.testing.KnownSourceFiles.intRangeTypeUseSource
import com.android.tools.metalava.testing.KnownSourceFiles.libcoreNonNullSource
import com.android.tools.metalava.testing.KnownSourceFiles.libcoreNullableSource
import com.android.tools.metalava.testing.java
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CommonTypeStringTest(combinedParameters: CombinedParameters) :
    BaseModelTest(combinedParameters.baseParameters) {

    data class TypeStringParameters(
        val name: String,
        val sourceType: String = name,
        val typeStringConfiguration: TypeStringConfiguration = TypeStringConfiguration(),
        val expectedTypeString: String = sourceType,
        val typeParameters: String? = null,
        val extraJavaSourceFiles: List<TestFile> = emptyList()
    ) {
        override fun toString(): String {
            return name
        }

        companion object {
            fun forDefaultAndKotlinNulls(
                name: String,
                sourceType: String = name,
                expectedDefaultTypeString: String = sourceType,
                expectedKotlinNullsTypeString: String = sourceType,
                typeParameters: String? = null,
                extraJavaSourceFiles: List<TestFile> = emptyList()
            ): List<TypeStringParameters> {
                return fromConfigurations(
                    name = name,
                    sourceType = sourceType,
                    configs =
                        listOf(
                            ConfigurationTestCase(
                                name = "default",
                                configuration = TypeStringConfiguration(),
                                expectedTypeString = expectedDefaultTypeString
                            ),
                            ConfigurationTestCase(
                                name = "kotlin nulls",
                                configuration = TypeStringConfiguration(kotlinStyleNulls = true),
                                expectedTypeString = expectedKotlinNullsTypeString
                            )
                        ),
                    typeParameters = typeParameters,
                    extraJavaSourceFiles = extraJavaSourceFiles
                )
            }

            fun fromConfigurations(
                name: String,
                sourceType: String,
                configs: List<ConfigurationTestCase>,
                typeParameters: String? = null,
                extraJavaSourceFiles: List<TestFile> = emptyList()
            ): List<TypeStringParameters> {
                return configs.map {
                    TypeStringParameters(
                        name = "$name - ${it.name}",
                        sourceType = sourceType,
                        typeStringConfiguration = it.configuration,
                        expectedTypeString = it.expectedTypeString,
                        typeParameters = typeParameters,
                        extraJavaSourceFiles = extraJavaSourceFiles
                    )
                }
            }
        }
    }

    data class ConfigurationTestCase(
        val name: String,
        val configuration: TypeStringConfiguration,
        val expectedTypeString: String
    )

    data class CombinedParameters(
        val baseParameters: TestParameters,
        val typeStringParameters: TypeStringParameters,
    ) {
        override fun toString(): String {
            return "$baseParameters,$typeStringParameters"
        }
    }

    private val parameters = combinedParameters.typeStringParameters

    private fun javaTestFiles() =
        inputSet(
            java(
                """
                package test.pkg;
                public class Foo {
                    public ${parameters.typeParameters.orEmpty()} void foo(${parameters.sourceType} arg) {}
                }
            """
            ),
            *parameters.extraJavaSourceFiles.toTypedArray()
        )

    private fun signatureTestFile() =
        inputSet(
            signature(
                """
                // Signature format: 5.0
                // - kotlin-name-type-order=yes
                // - include-type-use-annotations=yes
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public ${parameters.typeParameters.orEmpty()} foo(_: ${parameters.sourceType}): void;
                  }
                }
            """
            )
        )

    @Test
    fun `Type string`() {
        runCodebaseTest(javaTestFiles(), signatureTestFile()) { codebase ->
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            val param = method.parameters().single()
            val type = param.type()
            val typeString =
                type.toTypeString(
                    annotations = parameters.typeStringConfiguration.annotations,
                    kotlinStyleNulls = parameters.typeStringConfiguration.kotlinStyleNulls,
                    filter = parameters.typeStringConfiguration.filter,
                    context = param
                )
            assertThat(typeString).isEqualTo(parameters.expectedTypeString)
        }
    }

    companion object {
        // Turbine needs this type defined in order to use it in tests
        private val innerParameterizedTypeSource =
            java(
                """
                package test.pkg;
                public class Outer<P1> {
                    public class Inner<P2> {}
                }
            """
                    .trimIndent()
            )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun combinedTestParameters(): Iterable<CombinedParameters> {
            return testParameters().flatMap { baseParameters ->
                testCases.map { CombinedParameters(baseParameters, it) }
            }
        }

        private val testCases =
            // Test primitives besides void (the test setup puts the type in parameter position, and
            // void can't be a parameter type).
            PrimitiveTypeItem.Primitive.values()
                .filter { it != PrimitiveTypeItem.Primitive.VOID }
                .map { TypeStringParameters(name = it.primitiveName) } +
                // Test additional types
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "string",
                    sourceType = "String",
                    expectedDefaultTypeString = "java.lang.String",
                    expectedKotlinNullsTypeString = "java.lang.String!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "number",
                    sourceType = "Number",
                    expectedDefaultTypeString = "java.lang.Number",
                    expectedKotlinNullsTypeString = "java.lang.Number!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "int array",
                    sourceType = "int[]",
                    expectedKotlinNullsTypeString = "int[]!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "string array",
                    sourceType = "String[]",
                    expectedDefaultTypeString = "java.lang.String[]",
                    expectedKotlinNullsTypeString = "java.lang.String![]!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "int varargs",
                    sourceType = "int..."
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "string varargs",
                    sourceType = "String...",
                    expectedDefaultTypeString = "java.lang.String...",
                    expectedKotlinNullsTypeString = "java.lang.String!..."
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "string list",
                    sourceType = "java.util.List<java.lang.String>",
                    expectedKotlinNullsTypeString = "java.util.List<java.lang.String!>!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "extends string list",
                    sourceType = "java.util.List<? extends java.lang.String>",
                    expectedKotlinNullsTypeString = "java.util.List<? extends java.lang.String>!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "T",
                    expectedKotlinNullsTypeString = "T!",
                    typeParameters = "<T>"
                ) +
                TypeStringParameters.fromConfigurations(
                    name = "null annotated string",
                    sourceType = "@libcore.util.Nullable String",
                    configs =
                        listOf(
                            ConfigurationTestCase(
                                name = "default",
                                configuration = TypeStringConfiguration(),
                                expectedTypeString = "java.lang.String"
                            ),
                            ConfigurationTestCase(
                                name = "annotated",
                                configuration = TypeStringConfiguration(annotations = true),
                                expectedTypeString = "java.lang.@libcore.util.Nullable String"
                            ),
                            ConfigurationTestCase(
                                name = "kotlin nulls",
                                configuration = TypeStringConfiguration(kotlinStyleNulls = true),
                                expectedTypeString = "java.lang.String?"
                            ),
                            ConfigurationTestCase(
                                name = "annotated and kotlin nulls",
                                configuration =
                                    TypeStringConfiguration(
                                        annotations = true,
                                        kotlinStyleNulls = true
                                    ),
                                expectedTypeString = "java.lang.String?"
                            ),
                        ),
                    extraJavaSourceFiles = listOf(libcoreNullableSource)
                ) +
                TypeStringParameters.fromConfigurations(
                    name = "null annotated string list",
                    sourceType =
                        "java.util.@libcore.util.Nullable List<java.lang.@libcore.util.NonNull String>",
                    configs =
                        listOf(
                            ConfigurationTestCase(
                                name = "default",
                                configuration = TypeStringConfiguration(),
                                expectedTypeString = "java.util.List<java.lang.String>"
                            ),
                            ConfigurationTestCase(
                                name = "annotated",
                                configuration = TypeStringConfiguration(annotations = true),
                                expectedTypeString =
                                    "java.util.@libcore.util.Nullable List<java.lang.@libcore.util.NonNull String>"
                            ),
                            ConfigurationTestCase(
                                name = "kotlin nulls",
                                configuration = TypeStringConfiguration(kotlinStyleNulls = true),
                                expectedTypeString = "java.util.List<java.lang.String>?"
                            ),
                            ConfigurationTestCase(
                                name = "annotated and kotlin nulls",
                                configuration =
                                    TypeStringConfiguration(
                                        annotations = true,
                                        kotlinStyleNulls = true
                                    ),
                                expectedTypeString = "java.util.List<java.lang.String>?"
                            ),
                        ),
                    extraJavaSourceFiles = listOf(libcoreNonNullSource, libcoreNullableSource)
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "string to number map",
                    sourceType = "java.util.Map<String, Number>",
                    expectedDefaultTypeString = "java.util.Map<java.lang.String,java.lang.Number>",
                    expectedKotlinNullsTypeString =
                        "java.util.Map<java.lang.String!,java.lang.Number!>!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "2d string array",
                    sourceType = "String[][]",
                    expectedDefaultTypeString = "java.lang.String[][]",
                    expectedKotlinNullsTypeString = "java.lang.String![]![]!"
                ) +
                TypeStringParameters.fromConfigurations(
                    name = "null annotated string array",
                    sourceType = "@libcore.util.Nullable String @libcore.util.Nullable []",
                    configs =
                        listOf(
                            ConfigurationTestCase(
                                name = "default",
                                configuration = TypeStringConfiguration(),
                                expectedTypeString = "java.lang.String[]"
                            ),
                            ConfigurationTestCase(
                                name = "annotated",
                                configuration = TypeStringConfiguration(annotations = true),
                                expectedTypeString =
                                    "java.lang.@libcore.util.Nullable String @libcore.util.Nullable []"
                            ),
                            ConfigurationTestCase(
                                name = "kotlin nulls",
                                configuration = TypeStringConfiguration(kotlinStyleNulls = true),
                                expectedTypeString = "java.lang.String?[]?"
                            ),
                            ConfigurationTestCase(
                                name = "annotated and kotlin nulls",
                                configuration =
                                    TypeStringConfiguration(
                                        annotations = true,
                                        kotlinStyleNulls = true
                                    ),
                                expectedTypeString = "java.lang.String?[]?"
                            ),
                        ),
                    extraJavaSourceFiles = listOf(libcoreNullableSource)
                ) +
                TypeStringParameters.fromConfigurations(
                    name = "null annotated string varargs",
                    sourceType = "@libcore.util.Nullable String @libcore.util.NonNull ...",
                    configs =
                        listOf(
                            ConfigurationTestCase(
                                name = "default",
                                configuration = TypeStringConfiguration(),
                                expectedTypeString = "java.lang.String..."
                            ),
                            ConfigurationTestCase(
                                name = "annotated",
                                configuration = TypeStringConfiguration(annotations = true),
                                expectedTypeString =
                                    "java.lang.@libcore.util.Nullable String @libcore.util.NonNull ..."
                            ),
                            ConfigurationTestCase(
                                name = "kotlin nulls",
                                configuration = TypeStringConfiguration(kotlinStyleNulls = true),
                                expectedTypeString = "java.lang.String?..."
                            ),
                            ConfigurationTestCase(
                                name = "annotated and kotlin nulls",
                                configuration =
                                    TypeStringConfiguration(
                                        annotations = true,
                                        kotlinStyleNulls = true
                                    ),
                                expectedTypeString = "java.lang.String?..."
                            ),
                        ),
                    extraJavaSourceFiles = listOf(libcoreNonNullSource, libcoreNullableSource)
                ) +
                TypeStringParameters.fromConfigurations(
                    name = "null annotated T",
                    sourceType = "@libcore.util.NonNull T",
                    configs =
                        listOf(
                            ConfigurationTestCase(
                                name = "default",
                                configuration = TypeStringConfiguration(),
                                expectedTypeString = "T"
                            ),
                            ConfigurationTestCase(
                                name = "annotated",
                                configuration = TypeStringConfiguration(annotations = true),
                                expectedTypeString = "@libcore.util.NonNull T"
                            ),
                            ConfigurationTestCase(
                                name = "kotlin nulls",
                                configuration = TypeStringConfiguration(kotlinStyleNulls = true),
                                expectedTypeString = "T"
                            ),
                            ConfigurationTestCase(
                                name = "annotated and kotlin nulls",
                                configuration =
                                    TypeStringConfiguration(
                                        annotations = true,
                                        kotlinStyleNulls = true
                                    ),
                                expectedTypeString = "T"
                            ),
                        ),
                    typeParameters = "<T>",
                    extraJavaSourceFiles = listOf(libcoreNonNullSource)
                ) +
                TypeStringParameters(
                    name = "super T comparable",
                    sourceType = "java.lang.Comparable<? super T>",
                    typeParameters = "<T>"
                ) +
                TypeStringParameters.fromConfigurations(
                    name = "null annotated extends T collection",
                    sourceType =
                        "java.util.@libcore.util.Nullable Collection<? extends @libcore.util.Nullable T>",
                    configs =
                        listOf(
                            ConfigurationTestCase(
                                name = "default",
                                configuration = TypeStringConfiguration(),
                                expectedTypeString = "java.util.Collection<? extends T>"
                            ),
                            ConfigurationTestCase(
                                name = "annotated",
                                configuration = TypeStringConfiguration(annotations = true),
                                expectedTypeString =
                                    "java.util.@libcore.util.Nullable Collection<? extends @libcore.util.Nullable T>"
                            ),
                            ConfigurationTestCase(
                                name = "kotlin nulls",
                                configuration = TypeStringConfiguration(kotlinStyleNulls = true),
                                expectedTypeString = "java.util.Collection<? extends T?>?"
                            ),
                            ConfigurationTestCase(
                                name = "annotated and kotlin nulls",
                                configuration =
                                    TypeStringConfiguration(
                                        annotations = true,
                                        kotlinStyleNulls = true
                                    ),
                                expectedTypeString = "java.util.Collection<? extends T?>?"
                            ),
                        ),
                    typeParameters = "<T>",
                    extraJavaSourceFiles = listOf(libcoreNullableSource)
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "int array list",
                    sourceType = "java.util.List<int[]>",
                    expectedKotlinNullsTypeString = "java.util.List<int[]!>!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "2d boolean array list",
                    sourceType = "java.util.List<boolean[][]>",
                    expectedKotlinNullsTypeString = "java.util.List<boolean[]![]!>!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "inner class type",
                    sourceType = "java.util.Map.Entry",
                    expectedKotlinNullsTypeString = "java.util.Map.Entry!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "extends number to super number map",
                    sourceType =
                        "java.util.Map<? extends java.lang.Number,? super java.lang.Number>",
                    expectedKotlinNullsTypeString =
                        "java.util.Map<? extends java.lang.Number,? super java.lang.Number>!"
                ) +
                TypeStringParameters.fromConfigurations(
                    name = "annotated integer list",
                    sourceType =
                        "java.util.List<java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer>",
                    configs =
                        listOf(
                            ConfigurationTestCase(
                                name = "default",
                                configuration = TypeStringConfiguration(),
                                expectedTypeString = "java.util.List<java.lang.Integer>"
                            ),
                            ConfigurationTestCase(
                                name = "annotated",
                                configuration = TypeStringConfiguration(annotations = true),
                                expectedTypeString =
                                    "java.util.List<java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer>"
                            ),
                            ConfigurationTestCase(
                                name = "kotlin nulls",
                                configuration = TypeStringConfiguration(kotlinStyleNulls = true),
                                expectedTypeString = "java.util.List<java.lang.Integer!>!"
                            ),
                            ConfigurationTestCase(
                                name = "annotated and kotlin nulls",
                                configuration =
                                    TypeStringConfiguration(
                                        annotations = true,
                                        kotlinStyleNulls = true
                                    ),
                                expectedTypeString =
                                    "java.util.List<java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer!>!"
                            ),
                            ConfigurationTestCase(
                                name = "annotated with negative filter",
                                configuration =
                                    TypeStringConfiguration(
                                        annotations = true,
                                        filter = { false },
                                    ),
                                expectedTypeString = "java.util.List<java.lang.Integer>"
                            ),
                            ConfigurationTestCase(
                                name = "annotated with negative filter and kotlin nulls",
                                configuration =
                                    TypeStringConfiguration(
                                        annotations = true,
                                        filter = { false },
                                        kotlinStyleNulls = true
                                    ),
                                expectedTypeString = "java.util.List<java.lang.Integer!>!"
                            ),
                            ConfigurationTestCase(
                                name = "annotated with positive filter",
                                configuration =
                                    TypeStringConfiguration(
                                        annotations = true,
                                        filter = { true },
                                    ),
                                expectedTypeString =
                                    "java.util.List<java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer>"
                            )
                        ),
                    extraJavaSourceFiles = listOf(intRangeTypeUseSource)
                ) +
                TypeStringParameters.fromConfigurations(
                    name = "annotated primitive",
                    sourceType = "@androidx.annotation.IntRange(from=5,to=10) int",
                    configs =
                        listOf(
                            ConfigurationTestCase(
                                name = "default",
                                configuration = TypeStringConfiguration(),
                                expectedTypeString = "int"
                            ),
                            ConfigurationTestCase(
                                name = "annotated",
                                configuration = TypeStringConfiguration(annotations = true),
                                expectedTypeString =
                                    "@androidx.annotation.IntRange(from=5,to=10) int"
                            ),
                            ConfigurationTestCase(
                                name = "kotlin nulls",
                                configuration = TypeStringConfiguration(kotlinStyleNulls = true),
                                expectedTypeString = "int"
                            )
                        ),
                    extraJavaSourceFiles = listOf(intRangeTypeUseSource)
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "parameterized inner type",
                    sourceType = "test.pkg.Outer<java.lang.String>.Inner<java.lang.Integer>",
                    expectedKotlinNullsTypeString =
                        "test.pkg.Outer<java.lang.String!>.Inner<java.lang.Integer!>!",
                    extraJavaSourceFiles = listOf(innerParameterizedTypeSource)
                ) +
                TypeStringParameters.fromConfigurations(
                    name = "null annotated parameterized inner type",
                    sourceType =
                        "test.pkg.Outer<java.lang.@libcore.util.Nullable String>.@libcore.util.Nullable Inner<java.lang.@libcore.util.NonNull Integer>",
                    configs =
                        listOf(
                            ConfigurationTestCase(
                                name = "default",
                                configuration = TypeStringConfiguration(),
                                expectedTypeString =
                                    "test.pkg.Outer<java.lang.String>.Inner<java.lang.Integer>"
                            ),
                            ConfigurationTestCase(
                                name = "annotated",
                                configuration = TypeStringConfiguration(annotations = true),
                                expectedTypeString =
                                    "test.pkg.Outer<java.lang.@libcore.util.Nullable String>.@libcore.util.Nullable Inner<java.lang.@libcore.util.NonNull Integer>"
                            ),
                            ConfigurationTestCase(
                                name = "kotlin nulls",
                                configuration = TypeStringConfiguration(kotlinStyleNulls = true),
                                expectedTypeString =
                                    "test.pkg.Outer<java.lang.String?>.Inner<java.lang.Integer>?"
                            )
                        ),
                    extraJavaSourceFiles =
                        listOf(
                            innerParameterizedTypeSource,
                            libcoreNullableSource,
                            libcoreNonNullSource
                        )
                ) +
                TypeStringParameters.fromConfigurations(
                    name = "multiple annotations integer list",
                    sourceType =
                        "java.util.List<java.lang.@libcore.util.Nullable @androidx.annotation.IntRange(from=5,to=10) Integer>",
                    listOf(
                        ConfigurationTestCase(
                            name = "default",
                            configuration = TypeStringConfiguration(),
                            expectedTypeString = "java.util.List<java.lang.Integer>"
                        ),
                        ConfigurationTestCase(
                            name = "annotated",
                            configuration = TypeStringConfiguration(annotations = true),
                            expectedTypeString =
                                "java.util.List<java.lang.@libcore.util.Nullable @androidx.annotation.IntRange(from=5,to=10) Integer>"
                        ),
                        ConfigurationTestCase(
                            name = "kotlin nulls",
                            configuration = TypeStringConfiguration(kotlinStyleNulls = true),
                            expectedTypeString = "java.util.List<java.lang.Integer?>!"
                        ),
                        ConfigurationTestCase(
                            name = "annotated and kotlin nulls",
                            configuration =
                                TypeStringConfiguration(
                                    annotations = true,
                                    kotlinStyleNulls = true
                                ),
                            expectedTypeString =
                                "java.util.List<java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer?>!"
                        ),
                        ConfigurationTestCase(
                            name = "annotated with filter",
                            configuration =
                                TypeStringConfiguration(
                                    annotations = true,
                                    // Filter that removes nullness annotations
                                    filter = {
                                        (it as? ClassItem)?.qualifiedName()?.let { name ->
                                            isNullnessAnnotation(name)
                                        } != true
                                    }
                                ),
                            expectedTypeString =
                                "java.util.List<java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer>"
                        ),
                        ConfigurationTestCase(
                            name = "annotated and kotlin nulls with filter",
                            configuration =
                                TypeStringConfiguration(
                                    annotations = true,
                                    kotlinStyleNulls = true,
                                    // Filter that removes nullness annotations, but Kotlin-nulls
                                    // should still be present
                                    filter = {
                                        (it as? ClassItem)?.qualifiedName()?.let { name ->
                                            isNullnessAnnotation(name)
                                        } != true
                                    }
                                ),
                            expectedTypeString =
                                "java.util.List<java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer?>!"
                        ),
                    ),
                    extraJavaSourceFiles = listOf(libcoreNullableSource, intRangeTypeUseSource)
                )
    }
}
