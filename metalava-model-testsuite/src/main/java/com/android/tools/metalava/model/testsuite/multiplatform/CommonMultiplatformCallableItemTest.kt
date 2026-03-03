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
class CommonMultiplatformCallableItemTest : BaseModelTest() {
    @Test
    fun `Definition of expect actual constructor`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo(i: Int)
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo actual constructor(i: Int)
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo actual constructor(i: Int) {
                    constructor(i1: Int, i2: Int): this(i1 + i2)
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
                ),
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")

            val commonConstructor = fooClass.assertConstructor(listOf("kotlin.Int"))
            commonConstructor.assertSourceSets("androidMain", "commonMain", "nativeMain")
            assertThat(commonConstructor.containingItem).isEqualTo(fooClass)
            assertThat(commonConstructor.toString())
                .isEqualTo("multiplatform constructor test.pkg.Foo(kotlin.Int)")

            val nativeConstructor = fooClass.assertConstructor(listOf("kotlin.Int", "kotlin.Int"))
            nativeConstructor.assertSourceSets("nativeMain")
            assertThat(nativeConstructor.containingItem).isEqualTo(fooClass)
            assertThat(nativeConstructor.toString())
                .isEqualTo("multiplatform constructor test.pkg.Foo(kotlin.Int, kotlin.Int)")
        }
    }

    @Test
    fun `Definition of expect actual function`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo {
                    expect fun commonMethod(s: String): Unit
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo {
                    actual fun commonMethod(s: String) = Unit
                    fun androidMethod(s: String?) = Unit
                }
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                 package test.pkg
                 actual class Foo {
                     actual fun commonMethod(s: String) = Unit
                     fun nativeMethod(s1: String, s2: String?) = Unit
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
                ),
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")

            val commonMethod = fooClass.assertMethod("commonMethod", listOf("kotlin.String"))
            commonMethod.assertSourceSets("androidMain", "commonMain", "nativeMain")
            assertThat(commonMethod.containingItem).isEqualTo(fooClass)
            assertThat(commonMethod.toString())
                .isEqualTo("multiplatform method test.pkg.Foo#commonMethod(kotlin.String)")

            val androidMethod = fooClass.assertMethod("androidMethod", listOf("kotlin.String?"))
            androidMethod.assertSourceSets("androidMain")
            assertThat(androidMethod.containingItem).isEqualTo(fooClass)
            assertThat(androidMethod.toString())
                .isEqualTo("multiplatform method test.pkg.Foo#androidMethod(kotlin.String?)")

            val nativeMethod =
                fooClass.assertMethod("nativeMethod", listOf("kotlin.String", "kotlin.String?"))
            nativeMethod.assertSourceSets("nativeMain")
            assertThat(nativeMethod.containingItem).isEqualTo(fooClass)
            assertThat(nativeMethod.toString())
                .isEqualTo(
                    "multiplatform method test.pkg.Foo#nativeMethod(kotlin.String, kotlin.String?)"
                )
        }
    }

    @Test
    fun `Definition of clashing definitions in unrelated source sets`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo
                """
            )
        val androidMain =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo(i: Int) {
                    fun clashingMethod(s: String) = Unit
                }
                """
            )
        val nativeMain =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo(i: Int) {
                    fun clashingMethod(s: String) = Unit
                }
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidMain, nativeMain),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidMain)),
                    createNativeModuleDescription(arrayOf(nativeMain)),
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")

            val clashingConstructor = fooClass.assertConstructor(listOf("kotlin.Int"))
            clashingConstructor.assertSourceSets("androidMain", "nativeMain")

            val clashingMethod = fooClass.assertMethod("clashingMethod", listOf("kotlin.String"))
            clashingMethod.assertSourceSets("androidMain", "nativeMain")
        }
    }

    @Test
    fun `Different throws types in different source sets`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo {
                    expect fun foo(s: String): Unit
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                import kotlin.jvm.Throws
                actual class Foo {
                    @Throws(IllegalStateException::class)
                    actual fun foo(s: String) = Unit
                }
                """
            )

        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                ),
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", listOf("kotlin.String"))
            fooMethod.throwsTypes
                .transformValues { throwsTypes ->
                    throwsTypes.map { throwsType -> throwsType.toTypeString() }
                }
                .assertSourceSetValues(
                    "commonMain" to emptyList(),
                    "androidMain" to listOf("java.lang.IllegalStateException"),
                )
            fooMethod.modifiers
                .transformValues { modifiers -> modifiers.annotations().map { it.qualifiedName } }
                .assertSourceSetValues(
                    "commonMain" to emptyList(),
                    "androidMain" to listOf("kotlin.jvm.Throws"),
                )
        }
    }

    @Test
    fun `Parameter definitions`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo {
                    fun commonMethod(optionalString: String = ""): Unit
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo {
                    actual fun commonMethod(optionalString: String) = Unit
                }
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                 package test.pkg
                 actual class Foo {
                     actual fun commonMethod(optionalString: String) = Unit
                     fun nativeMethod(s0: String, s1: String?, s2: String) = Unit
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
                ),
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")

            val commonMethod = fooClass.assertMethod("commonMethod", listOf("kotlin.String"))
            val commonParameter = commonMethod.parameters.single()
            commonParameter.assertSourceSets("commonMain", "androidMain", "nativeMain")
            assertThat(commonParameter.containingCallable).isEqualTo(commonMethod)
            assertThat(commonParameter.parameterIndex).isEqualTo(0)
            assertThat(commonParameter.toString())
                .isEqualTo(
                    "multiplatform parameter #0 of multiplatform method test.pkg.Foo#commonMethod(kotlin.String)"
                )
            commonParameter.publicName.assertSourceSetValues(
                "commonMain" to "optionalString",
                "androidMain" to "optionalString",
                "nativeMain" to "optionalString",
            )
            commonParameter.hasDefaultValue.assertSourceSetValues(
                "commonMain" to true,
                "androidMain" to true,
                "nativeMain" to true,
            )

            val nativeMethod =
                fooClass.assertMethod(
                    "nativeMethod",
                    listOf("kotlin.String", "kotlin.String?", "kotlin.String")
                )
            assertThat(nativeMethod.parameters).hasSize(3)
            for ((index, parameter) in nativeMethod.parameters.withIndex()) {
                parameter.assertSourceSets("nativeMain")
                assertThat(parameter.containingCallable).isEqualTo(nativeMethod)
                assertThat(parameter.parameterIndex).isEqualTo(index)
                assertThat(parameter.toString())
                    .isEqualTo(
                        "multiplatform parameter #$index of multiplatform method test.pkg.Foo#nativeMethod(kotlin.String, kotlin.String?, kotlin.String)"
                    )
                parameter.publicName.assertSourceSetValues("nativeMain" to "s$index")
                parameter.hasDefaultValue.assertSourceSetValues("nativeMain" to false)
            }
        }
    }

    @Test
    fun `Expect actual parameter definitions with different modifiers`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                annotation class CommonAnnotation
                expect class Foo {
                    fun foo(@CommonAnnotation s: String)
                }
                """
            )
        val androidMain =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                annotation class AndroidAnnotation
                actual class Foo {
                    actual fun foo(@AndroidAnnotation s: String) = Unit
                }
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidMain),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidMain)),
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", listOf("kotlin.String"))
            val stringParameter = fooMethod.parameters.single()
            stringParameter.assertSourceSets("androidMain", "commonMain")
            assertThat(stringParameter.containingCallable).isEqualTo(fooMethod)
            assertThat(stringParameter.parameterIndex).isEqualTo(0)
            stringParameter.modifiers
                .transformValues { modifiers -> modifiers.annotations().map { it.qualifiedName } }
                .assertSourceSetValues(
                    "commonMain" to listOf("test.pkg.CommonAnnotation"),
                    "androidMain" to listOf("test.pkg.AndroidAnnotation"),
                )
        }
    }

    @Test
    fun `Parameters of functions with the same signature in unrelated source sets`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo
                """
            )
        val androidMain =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo {
                    fun clashingMethod(android: String) = Unit
                }
                """
            )
        val nativeMain =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo {
                    fun clashingMethod(native: String = "") = Unit
                }
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidMain, nativeMain),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidMain)),
                    createNativeModuleDescription(arrayOf(nativeMain)),
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            val clashingMethod = fooClass.assertMethod("clashingMethod", listOf("kotlin.String"))
            val clashingParameter = clashingMethod.parameters.single()
            clashingParameter.assertSourceSets("androidMain", "nativeMain")
            assertThat(clashingParameter.containingCallable).isEqualTo(clashingMethod)
            assertThat(clashingParameter.parameterIndex).isEqualTo(0)
            clashingParameter.hasDefaultValue.assertSourceSetValues(
                "androidMain" to false,
                "nativeMain" to true,
            )
            clashingParameter.publicName.assertSourceSetValues(
                "androidMain" to "android",
                "nativeMain" to "native",
            )
        }
    }

    @Test
    fun `Definition of top level expect actual function`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                   package test.pkg
                   expect fun foo(int: Int): Unit
                   """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                   package test.pkg
                   actual fun foo(int: Int) = Unit
                   fun androidMethod(s: String) = Unit
                   """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                   package test.pkg
                   actual fun foo(int: Int) = Unit
                   fun nativeMethod(s: String) = Unit
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

            val fooMethod = testPkg.assertMethod("foo", listOf("kotlin.Int"))
            fooMethod.assertSourceSets("androidMain", "commonMain", "nativeMain")

            val androidMethod = testPkg.assertMethod("androidMethod", listOf("kotlin.String"))
            androidMethod.assertSourceSets("androidMain")

            val nativeMethod = testPkg.assertMethod("nativeMethod", listOf("kotlin.String"))
            nativeMethod.assertSourceSets("nativeMain")

            assertThat(testPkg.topLevelFunctions).hasSize(3)
            assertThat(testPkg.topLevelFunctions)
                .containsExactly(fooMethod, androidMethod, nativeMethod)

            // Verify that the facade classes in the underlying model to hold the top level
            // functions are not included in the multiplatform model.
            assertThat(testPkg.topLevelClasses()).isEmpty()
            assertThat(testPkg.allClasses().toList()).isEmpty()
        }
    }

    @Test
    fun `Definition of suspend function`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                suspend fun foo(int: Int) = Unit
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource),
            projectDescription =
                createProjectDescription(createCommonModuleDescription(arrayOf(commonSource))),
        ) {
            val testPkg = multiplatformCodebase.assertPackage("test.pkg")
            val fooFunction = testPkg.assertMethod("foo", listOf("kotlin.Int"))
            fooFunction.assertSourceSets("commonMain")
            fooFunction.modifiers
                .transformValues { it.isSuspend() }
                .assertSourceSetValues("commonMain" to true)
        }
    }
}
