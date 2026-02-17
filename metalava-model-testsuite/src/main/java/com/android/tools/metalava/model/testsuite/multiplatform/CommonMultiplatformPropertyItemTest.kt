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

import com.android.tools.metalava.model.multiplatform.transformValues
import com.android.tools.metalava.model.testing.FilterAction.EXCLUDE
import com.android.tools.metalava.model.testing.FilterByProvider
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createNativeModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@FilterByProvider("psi", "k1", action = EXCLUDE)
class CommonMultiplatformPropertyItemTest : BaseModelTest() {
    @Test
    fun `Definition of expect actual property`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo {
                    val foo: Int
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo {
                    actual val foo: Int = 0
                }
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo {
                    actual val foo: Int = 0
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
            val fooProperty = fooClass.assertProperty("foo")
            fooProperty.assertSourceSets("commonMain", "androidMain", "nativeMain")
            assertThat(fooProperty.containingItem).isEqualTo(fooClass)
            assertThat(fooProperty.name).isEqualTo("foo")
            assertThat(fooProperty.receiver).isNull()
            assertThat(fooProperty.toString()).isEqualTo("multiplatform property test.pkg.Foo#foo")
        }
    }

    @Test
    fun `Definition of non expect actual properties`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Common.kt",
                """
                package test.pkg
                class Common {
                    val common: Int = 0
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Android.kt",
                """
                package test.pkg
                class Android {
                    val android: Int = 0
                }
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                )
        ) {
            val commonClass = multiplatformCodebase.assertClass("test.pkg.Common")
            val commonProperty = commonClass.assertProperty("common")
            commonProperty.assertSourceSets("commonMain", "androidMain")
            assertThat(commonProperty.containingItem).isEqualTo(commonClass)
            assertThat(commonProperty.name).isEqualTo("common")
            assertThat(commonProperty.receiver).isNull()
            assertThat(commonProperty.toString())
                .isEqualTo("multiplatform property test.pkg.Common#common")

            val androidClass = multiplatformCodebase.assertClass("test.pkg.Android")
            val androidProperty = androidClass.assertProperty("android")
            androidProperty.assertSourceSets("androidMain")
            assertThat(androidProperty.containingItem).isEqualTo(androidClass)
            assertThat(androidProperty.name).isEqualTo("android")
            assertThat(androidProperty.receiver).isNull()
            assertThat(androidProperty.toString())
                .isEqualTo("multiplatform property test.pkg.Android#android")
        }
    }

    @Test
    fun `Definition of expect actual extension property`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo {
                    val String.foo: Int
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo {
                    actual val String.foo: Int = 0
                }
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo {
                    actual val String.foo: Int = 0
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
            val fooProperty = fooClass.assertProperty("foo", "kotlin.String")
            fooProperty.assertSourceSets("commonMain", "androidMain", "nativeMain")
            assertThat(fooProperty.containingItem).isEqualTo(fooClass)
            assertThat(fooProperty.name).isEqualTo("foo")
            assertThat(fooProperty.receiver?.toTypeString()).isEqualTo("kotlin.String")
            assertThat(fooProperty.toString())
                .isEqualTo("multiplatform property test.pkg.Foo#kotlin.String.foo")
        }
    }

    @Test
    fun `Definition of expect actual property with different modifiers by source set`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect open class Foo {
                    internal val foo: Int
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual open class Foo {
                    public actual val foo: Int = 0
                }
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual open class Foo {
                    protected actual val foo: Int = 0
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
            val fooProperty = fooClass.assertProperty("foo")
            fooProperty.assertSourceSets("commonMain", "androidMain", "nativeMain")
            fooProperty.modifiers
                .transformValues { it.getVisibilityString() }
                .assertSourceSetValues(
                    "commonMain" to "internal",
                    "androidMain" to "public",
                    "nativeMain" to "protected",
                )
        }
    }

    @Test
    fun `Definition of properties with receivers that only differ by nullability`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                class Foo {
                    val String.string: Int
                        get() = 0
                    val String?.string: Int
                        get() = 0
                    val List<String>.list: Int
                        get() = 0
                    val List<String?>.list: Int
                        get() = 0
                }
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")

            val stringNonNullable = fooClass.assertProperty("string", "kotlin.String")
            assertThat(stringNonNullable.toString())
                .isEqualTo("multiplatform property test.pkg.Foo#kotlin.String.string")
            val stringNullable = fooClass.assertProperty("string", "kotlin.String?")
            assertThat(stringNullable.toString())
                .isEqualTo("multiplatform property test.pkg.Foo#kotlin.String?.string")
            assertThat(stringNonNullable).isNotEqualTo(stringNullable)

            val listNonNullable =
                fooClass.assertProperty("list", "kotlin.collections.List<kotlin.String>")
            assertThat(listNonNullable.toString())
                .isEqualTo(
                    "multiplatform property test.pkg.Foo#kotlin.collections.List<kotlin.String>.list"
                )
            val listNullable =
                fooClass.assertProperty("list", "kotlin.collections.List<kotlin.String?>")
            assertThat(listNullable.toString())
                .isEqualTo(
                    "multiplatform property test.pkg.Foo#kotlin.collections.List<kotlin.String?>.list"
                )
            assertThat(listNonNullable).isNotEqualTo(listNullable)

            val fooProperties = fooClass.properties
            assertThat(fooProperties.size).isEqualTo(4)
            assertThat(fooProperties)
                .containsExactly(stringNonNullable, stringNullable, listNonNullable, listNullable)
        }
    }

    @Test
    fun `Definition of top level expect actual extension property`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
               package test.pkg
               expect val Int.foo: Int
               """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                   package test.pkg
                   actual val Int.foo = 0
                   val Int.android = 0
                   """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                   package test.pkg
                   actual val Int.foo = 0
                   val Int.native = 0
                   """
            )

        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                ),
        ) {
            val testPkg = multiplatformCodebase.assertPackage("test.pkg")
            testPkg.assertSourceSets("androidMain", "commonMain", "nativeMain")

            val fooProperty = testPkg.assertProperty("foo", "kotlin.Int")
            fooProperty.assertSourceSets("androidMain", "commonMain", "nativeMain")

            val androidProperty = testPkg.assertProperty("android", "kotlin.Int")
            androidProperty.assertSourceSets("androidMain")

            val nativeProperty = testPkg.assertProperty("native", "kotlin.Int")
            nativeProperty.assertSourceSets("nativeMain")

            assertThat(testPkg.topLevelProperties).hasSize(3)
            assertThat(testPkg.topLevelProperties)
                .containsExactly(fooProperty, androidProperty, nativeProperty)

            // Verify that the facade classes in the underlying model to hold the top level
            // properties are not included in the multiplatform model.
            assertThat(testPkg.topLevelClasses()).isEmpty()
            assertThat(testPkg.allClasses().toList()).isEmpty()
        }
    }
}
