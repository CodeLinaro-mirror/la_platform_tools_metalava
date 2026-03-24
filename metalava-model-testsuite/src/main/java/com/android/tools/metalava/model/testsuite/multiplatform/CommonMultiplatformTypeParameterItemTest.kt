/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite.multiplatform

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createNativeModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CommonMultiplatformTypeParameterItemTest : BaseModelTest() {
    @Test
    fun `Type parameters on a class`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo<T0, T1, T2>
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo<T0, T1, T2>
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo<T0, T1, T2>
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            assertThat(fooClass.typeParameterList).hasSize(3)
            for ((index, typeParam) in fooClass.typeParameterList.withIndex()) {
                typeParam.assertSourceSets("commonMain", "androidMain", "nativeMain")
                assertThat(typeParam.typeParameterIndex).isEqualTo(index)
                assertThat(typeParam.owner).isEqualTo(fooClass)
                val name = "T$index"
                typeParam.name.assertSourceSetValues(
                    "commonMain" to name,
                    "androidMain" to name,
                    "nativeMain" to name,
                )
                typeParam.isReified.assertSourceSetValues(
                    "commonMain" to false,
                    "androidMain" to false,
                    "nativeMain" to false,
                )
                assertThat(typeParam.toString())
                    .isEqualTo(
                        "multiplatform type parameter #$index of multiplatform class test.pkg.Foo"
                    )
            }
        }
    }

    @Test
    fun `Type parameters on a function`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo {
                    fun <T> foo(): Unit
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo {
                    actual fun <T> foo() = Unit
                }
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo {
                    actual fun <T> foo() = Unit
                }
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", emptyList())
            val typeParam = fooMethod.typeParameterList.single()
            typeParam.assertSourceSets("commonMain", "androidMain", "nativeMain")
            assertThat(typeParam.typeParameterIndex).isEqualTo(0)
            assertThat(typeParam.owner).isEqualTo(fooMethod)
            assertThat(typeParam.toString())
                .isEqualTo(
                    "multiplatform type parameter #0 of multiplatform method test.pkg.Foo#foo()"
                )
        }
    }

    @Test
    fun `Type parameters on a property`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo {
                    val <T> T.foo: Int
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo {
                    actual val <T> T.foo: Int
                        get() = 0
                }
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo {
                    actual val <T> T.foo: Int
                        get() = 0
                }
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            val fooProperty = fooClass.assertProperty("foo", "T")
            val typeParam = fooProperty.typeParameterList.single()
            typeParam.assertSourceSets("commonMain", "androidMain", "nativeMain")
            assertThat(typeParam.typeParameterIndex).isEqualTo(0)
            assertThat(typeParam.owner).isEqualTo(fooProperty)
            assertThat(typeParam.toString())
                .isEqualTo(
                    "multiplatform type parameter #0 of multiplatform property test.pkg.Foo#T.foo"
                )
        }
    }

    @Test
    fun `Type parameter with different names per source set`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo<Common>
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo<Android>
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo<Native>
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            val typeParam = fooClass.typeParameterList.single()
            typeParam.assertSourceSets("commonMain", "androidMain", "nativeMain")
            assertThat(typeParam.typeParameterIndex).isEqualTo(0)
            assertThat(typeParam.owner).isEqualTo(fooClass)
            typeParam.name.assertSourceSetValues(
                "commonMain" to "Common",
                "androidMain" to "Android",
                "nativeMain" to "Native",
            )
        }
    }

    @Test
    fun `Type parameters with different reified values per source set`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo {
                    inline fun <reified T> foo(): Unit
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo {
                    actual inline fun <T> foo() = Unit
                }
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo {
                    actual inline fun <reified T> foo() = Unit
                }
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", emptyList())
            val typeParam = fooMethod.typeParameterList.single()
            typeParam.assertSourceSets("commonMain", "androidMain", "nativeMain")
            typeParam.isReified.assertSourceSetValues(
                "commonMain" to true,
                "androidMain" to false,
                "nativeMain" to true,
            )
        }
    }

    @Test
    fun `Clashing type parameters in unrelated source sets`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Common.kt",
                """
                package test.pkg
                class Common
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                class Foo<Android1, Android2>
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                class Foo<Native>
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            fooClass.assertSourceSets("androidMain", "nativeMain")
            assertThat(fooClass.typeParameterList).hasSize(2)

            val typeParam1 = fooClass.typeParameterList[0]
            typeParam1.assertSourceSets("androidMain", "nativeMain")
            assertThat(typeParam1.typeParameterIndex).isEqualTo(0)
            assertThat(typeParam1.owner).isEqualTo(fooClass)
            typeParam1.name.assertSourceSetValues(
                "androidMain" to "Android1",
                "nativeMain" to "Native",
            )
            assertThat(typeParam1.toString())
                .isEqualTo("multiplatform type parameter #0 of multiplatform class test.pkg.Foo")

            val typeParam2 = fooClass.typeParameterList[1]
            typeParam2.assertSourceSets("androidMain")
            assertThat(typeParam2.typeParameterIndex).isEqualTo(1)
            assertThat(typeParam2.owner).isEqualTo(fooClass)
            typeParam2.name.assertSourceSetValues("androidMain" to "Android2")
            assertThat(typeParam2.toString())
                .isEqualTo("multiplatform type parameter #1 of multiplatform class test.pkg.Foo")
        }
    }
}
