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
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createModuleDescription
import com.android.tools.metalava.testing.createNativeModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.defaultJsPlatforms
import com.android.tools.metalava.testing.defaultJvmPlatforms
import com.android.tools.metalava.testing.defaultNativePlatforms
import com.android.tools.metalava.testing.defaultWasmPlatforms
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CommonMultiplatformCodebaseTest : BaseModelTest() {
    @Test
    fun `Test multiplatform codebase with single source set`() {
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                class Foo
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(androidSource),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource), dependsOn = emptyList())
                )
        ) {
            multiplatformCodebase.assertSourceSets("androidMain")
        }
    }

    @Test
    fun `Test multiplatform codebase with multiple source sets`() {
        val commonSource =
            kotlin(
                "common/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class F00
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo
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
            multiplatformCodebase.assertSourceSets("commonMain", "androidMain", "nativeMain")
        }
    }

    @Test
    fun `Test finding and listing packages`() {
        val commonSource =
            kotlin(
                "common/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class F00
                """
            )
        val androidSource =
            arrayOf(
                kotlin(
                    "androidMain/src/test/pkg/Foo_android.kt",
                    """
                    package test.pkg
                    actual class Foo
                    """
                ),
                kotlin(
                    "androidMain/src/test/pkg/android/Android.kt",
                    """
                    package test.pkg.android
                    class Android
                    """
                ),
            )
        val nativeSource =
            arrayOf(
                kotlin(
                    "nativeMain/src/test/pkg/Foo_native.kt",
                    """
                    package test.pkg
                    actual class Foo
                    """
                ),
                kotlin(
                    "nativeMain/src/test/pkg/native/Native.kt",
                    """
                    package test.pkg.native
                    class Native
                    """
                ),
            )

        runMultiplatformCodebaseTest(
            inputSet(commonSource, *androidSource, *nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(androidSource),
                    createNativeModuleDescription(nativeSource),
                )
        ) {
            val testPkg = multiplatformCodebase.assertPackage("test.pkg")
            assertThat(testPkg.qualifiedName).isEqualTo("test.pkg")
            assertThat(testPkg.toString()).isEqualTo("multiplatform package test.pkg")
            testPkg.assertSourceSets("commonMain", "androidMain", "nativeMain")

            val androidPkg = multiplatformCodebase.assertPackage("test.pkg.android")
            assertThat(androidPkg.qualifiedName).isEqualTo("test.pkg.android")
            assertThat(androidPkg.toString()).isEqualTo("multiplatform package test.pkg.android")
            androidPkg.assertSourceSets("androidMain")

            val nativePkg = multiplatformCodebase.assertPackage("test.pkg.native")
            assertThat(nativePkg.qualifiedName).isEqualTo("test.pkg.native")
            assertThat(nativePkg.toString()).isEqualTo("multiplatform package test.pkg.native")
            nativePkg.assertSourceSets("nativeMain")

            val fakePkg = multiplatformCodebase.findPackage("test.pkg.fake")
            assertThat(fakePkg).isNull()

            // The full list of packages may also contain packages from the classpath, but it should
            // have at minimum all packages from source.
            assertThat(multiplatformCodebase.packages)
                .containsAtLeast(testPkg, androidPkg, nativePkg)
        }
    }

    @Test
    fun `Test listing classes of a package`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Outer.kt",
                """
                package test.pkg
                expect class Outer {
                    class CommonMiddle {
                        class CommonInner
                    }
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Android.kt",
                """
                package test.pkg
                class Android
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Native.kt",
                """
                package test.pkg
                class Native {
                    inner class NativeInner
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
            val outerClass = multiplatformCodebase.assertClass("test.pkg.Outer")
            outerClass.assertSourceSets("commonMain", "androidMain", "nativeMain")
            val commonMiddleClass = multiplatformCodebase.assertClass("test.pkg.Outer.CommonMiddle")
            commonMiddleClass.assertSourceSets("commonMain", "androidMain", "nativeMain")
            val commonInnerClass =
                multiplatformCodebase.assertClass("test.pkg.Outer.CommonMiddle.CommonInner")
            commonInnerClass.assertSourceSets("commonMain", "androidMain", "nativeMain")
            val androidClass = multiplatformCodebase.assertClass("test.pkg.Android")
            androidClass.assertSourceSets("androidMain")
            val nativeClass = multiplatformCodebase.assertClass("test.pkg.Native")
            nativeClass.assertSourceSets("nativeMain")
            val nativeInnerClass = multiplatformCodebase.assertClass("test.pkg.Native.NativeInner")
            nativeInnerClass.assertSourceSets("nativeMain")

            val testPkg = multiplatformCodebase.assertPackage("test.pkg")
            assertThat(testPkg.topLevelClasses())
                .containsExactly(outerClass, androidClass, nativeClass)
            assertThat(testPkg.allClasses().toList())
                .containsExactly(
                    outerClass,
                    androidClass,
                    nativeClass,
                    commonMiddleClass,
                    commonInnerClass,
                    nativeInnerClass,
                )
        }
    }

    @Test
    fun `Test resolving classes`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo
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
            // Resolving a class from source
            val fooClass = multiplatformCodebase.resolveClass("test.pkg.Foo")
            assertThat(fooClass).isNotNull()

            // Resolving a fake class should fail
            val fakeClass = multiplatformCodebase.resolveClass("fake.pkg.Class")
            assertThat(fakeClass).isNull()

            // Resolving a classpath class which exists for all source sets
            val stringClass = multiplatformCodebase.resolveClass("kotlin.String")
            assertThat(stringClass).isNotNull()
            stringClass!!.assertSourceSets("commonMain", "androidMain", "nativeMain")

            // Resolving a classpath class which only exists for android
            val jvmName = multiplatformCodebase.resolveClass("kotlin.jvm.JvmName")
            assertThat(jvmName).isNotNull()
            jvmName!!.assertSourceSets("androidMain")

            val requiresOptInLevel =
                multiplatformCodebase.resolveClass("kotlin.RequiresOptIn.Level")
            assertThat(requiresOptInLevel).isNotNull()
            requiresOptInLevel!!.assertSourceSets("commonMain", "androidMain", "nativeMain")
        }
    }

    @Test
    fun `Test Java source file`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo
                """
            )
        val androidSource =
            arrayOf(
                kotlin(
                    "androidMain/src/test/pkg/Foo_android.kt",
                    """
                    package test.pkg
                    actual class Foo
                    """
                ),
                java(
                    "androidMain/src/test/pkg/JavaClass.java",
                    """
                    package test.pkg;
                    public class JavaClass {}
                    """
                )
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo
                """
            )

        runMultiplatformCodebaseTest(
            inputSet(commonSource, *androidSource, nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(androidSource),
                    createNativeModuleDescription(arrayOf(nativeSource))
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            fooClass.assertSourceSets("commonMain", "androidMain", "nativeMain")

            val javaClass = multiplatformCodebase.assertClass("test.pkg.JavaClass")
            javaClass.assertSourceSets("androidMain")
        }
    }

    @Test
    fun `Test baseline keys`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                class Foo {
                    fun foo(s: String?) = Unit
                    val foo = 0
                    val String?.foo
                        get() = 0
                }
                fun foo(s: String) = Unit
                val foo = 0
                val String.foo
                    get() = 0
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                )
        ) {
            val fooPackage = multiplatformCodebase.assertPackage("test.pkg")
            assertThat(fooPackage.baselineKey.elementId()).isEqualTo("test.pkg")
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            assertThat(fooClass.baselineKey.elementId()).isEqualTo("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", listOf("kotlin.String?"))
            assertThat(fooMethod.baselineKey.elementId())
                .isEqualTo("test.pkg.Foo#foo(kotlin.String?)")
            val fooMethodParameter = fooMethod.parameters.single()
            assertThat(fooMethodParameter.baselineKey.elementId())
                .isEqualTo("test.pkg.Foo#foo(kotlin.String?) parameter #0")
            val fooProperty = fooClass.assertProperty("foo")
            assertThat(fooProperty.baselineKey.elementId()).isEqualTo("test.pkg.Foo#foo")
            val fooExtensionProperty = fooClass.assertProperty("foo", "kotlin.String?")
            assertThat(fooExtensionProperty.baselineKey.elementId())
                .isEqualTo("test.pkg.Foo#kotlin.String?.foo")
            val fooTopLevelMethod = fooPackage.assertMethod("foo", listOf("kotlin.String"))
            assertThat(fooTopLevelMethod.baselineKey.elementId())
                .isEqualTo("test.pkg#foo(kotlin.String)")
            val fooTopLevelMethodParameter = fooTopLevelMethod.parameters.single()
            assertThat(fooTopLevelMethodParameter.baselineKey.elementId())
                .isEqualTo("test.pkg#foo(kotlin.String) parameter #0")
            val fooTopLevelProperty = fooPackage.assertProperty("foo")
            assertThat(fooTopLevelProperty.baselineKey.elementId()).isEqualTo("test.pkg#foo")
            val fooTopLevelExtensionProperty = fooPackage.assertProperty("foo", "kotlin.String")
            assertThat(fooTopLevelExtensionProperty.baselineKey.elementId())
                .isEqualTo("test.pkg#kotlin.String.foo")
        }
    }

    @Test
    fun `Test suppressed issues`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                @Suppress("CommonIssue")
                expect class Foo
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                @Suppress("AndroidIssue")
                actual class Foo
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                @Suppress("NativeIssue")
                actual class Foo
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
            assertThat(fooClass.suppressedIssues())
                .containsExactly("CommonIssue", "AndroidIssue", "NativeIssue")
        }
    }

    @Test
    fun `Test file locations`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                class Foo {
                    fun foo(s: String?) = Unit
                    val foo = 0
                    val String?.foo
                        get() = 0
                }
                fun foo(s: String) = Unit
                val foo = 0
                val String.foo
                    get() = 0
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                )
        ) {
            val fooPackage = multiplatformCodebase.assertPackage("test.pkg")
            assertThat(fooPackage.fileLocation).isEqualTo(FileLocation.UNKNOWN)
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            assertThat(fooClass.fileLocation.toString())
                .endsWith("commonMain/src/test/pkg/Foo.kt:2")
            val fooMethod = fooClass.assertMethod("foo", listOf("kotlin.String?"))
            assertThat(fooMethod.fileLocation.toString())
                .endsWith("commonMain/src/test/pkg/Foo.kt:3")
            val fooMethodParameter = fooMethod.parameters.single()
            assertThat(fooMethodParameter.fileLocation.toString())
                .endsWith("commonMain/src/test/pkg/Foo.kt:3")
            val fooProperty = fooClass.assertProperty("foo")
            assertThat(fooProperty.fileLocation.toString())
                .endsWith("commonMain/src/test/pkg/Foo.kt:4")
            val fooExtensionProperty = fooClass.assertProperty("foo", "kotlin.String?")
            assertThat(fooExtensionProperty.fileLocation.toString())
                .endsWith("commonMain/src/test/pkg/Foo.kt:5")
            val fooTopLevelMethod = fooPackage.assertMethod("foo", listOf("kotlin.String"))
            assertThat(fooTopLevelMethod.fileLocation.toString())
                .endsWith("commonMain/src/test/pkg/Foo.kt:8")
            val fooTopLevelMethodParameter = fooTopLevelMethod.parameters.single()
            assertThat(fooTopLevelMethodParameter.fileLocation.toString())
                .endsWith("commonMain/src/test/pkg/Foo.kt:8")
            val fooTopLevelProperty = fooPackage.assertProperty("foo")
            assertThat(fooTopLevelProperty.fileLocation.toString())
                .endsWith("commonMain/src/test/pkg/Foo.kt:9")
            val fooTopLevelExtensionProperty = fooPackage.assertProperty("foo", "kotlin.String")
            assertThat(fooTopLevelExtensionProperty.fileLocation.toString())
                .endsWith("commonMain/src/test/pkg/Foo.kt:10")
        }
    }

    @Test
    fun `Test that processing is limited to common and leaf source sets`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Common.kt",
                """
                package test.pkg
                class Common
                """
            )
        val nonJvmSource =
            kotlin(
                "nonJvmMain/src/test/pkg/NonJvm.kt",
                """
                package test.pkg
                class NonJvm
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Native.kt",
                """
                package test.pkg
                class Native
                """
            )
        val webSource =
            kotlin(
                "webMain/src/test/pkg/Web.kt",
                """
                package test.pkg
                class Web
                """
            )
        val wasmSource =
            kotlin(
                "wasmMain/src/test/pkg/Wasm.kt",
                """
                package test.pkg
                class Wasm
                """
            )
        val jsSource =
            kotlin(
                "jsMain/src/test/pkg/Js.kt",
                """
                package test.pkg
                class Js
                """
            )
        val jvmAndroidSource =
            kotlin(
                "jvmAndroidMain/src/test/pkg/JvmAndroid.kt",
                """
                package test.pkg
                class JvmAndroid
                """
            )
        val jvmSource =
            kotlin(
                "jvmMain/src/test/pkg/Jvm.kt",
                """
                package test.pkg
                class Jvm
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Android.kt",
                """
                package test.pkg
                class Android
                """
            )

        runMultiplatformCodebaseTest(
            inputSet(
                commonSource,
                nonJvmSource,
                nativeSource,
                webSource,
                wasmSource,
                jsSource,
                jvmAndroidSource,
                jvmSource,
                androidSource,
            ),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createModuleDescription(
                        moduleName = "nonJvmMain",
                        android = false,
                        kotlinPlatforms =
                            "$defaultNativePlatforms/$defaultWasmPlatforms/$defaultJsPlatforms",
                        sourceFiles = arrayOf(nonJvmSource),
                    ),
                    createNativeModuleDescription(
                        sourceFiles = arrayOf(nativeSource),
                        dependsOn = listOf("commonMain", "nonJvmMain"),
                    ),
                    createModuleDescription(
                        moduleName = "webMain",
                        android = false,
                        kotlinPlatforms = "$defaultWasmPlatforms/$defaultJsPlatforms",
                        sourceFiles = arrayOf(webSource),
                        dependsOn = listOf("commonMain", "nonJvmMain"),
                    ),
                    createModuleDescription(
                        moduleName = "wasmMain",
                        android = false,
                        kotlinPlatforms = defaultWasmPlatforms,
                        sourceFiles = arrayOf(wasmSource),
                        dependsOn = listOf("commonMain", "nonJvmMain", "webMain"),
                    ),
                    createModuleDescription(
                        moduleName = "jsMain",
                        android = false,
                        kotlinPlatforms = defaultJsPlatforms,
                        sourceFiles = arrayOf(jsSource),
                        dependsOn = listOf("commonMain", "nonJvmMain", "webMain"),
                    ),
                    createModuleDescription(
                        moduleName = "jvmAndroidMain",
                        android = false,
                        kotlinPlatforms = defaultJvmPlatforms,
                        sourceFiles = arrayOf(jvmAndroidSource),
                    ),
                    createModuleDescription(
                        moduleName = "jvmMain",
                        android = false,
                        kotlinPlatforms = defaultJvmPlatforms,
                        sourceFiles = arrayOf(jvmSource),
                        dependsOn = listOf("commonMain", "jvmAndroidMain"),
                    ),
                    createAndroidModuleDescription(
                        sourceFiles = arrayOf(androidSource),
                        dependsOn = listOf("commonMain", "jvmAndroidMain"),
                    ),
                )
        ) {
            val commonClass = multiplatformCodebase.assertClass("test.pkg.Common")
            commonClass.assertSourceSets(
                "commonMain",
                "nativeMain",
                "wasmMain",
                "jsMain",
                "jvmMain",
                "androidMain",
            )

            val nonJvmClass = multiplatformCodebase.assertClass("test.pkg.NonJvm")
            nonJvmClass.assertSourceSets("nativeMain", "wasmMain", "jsMain")

            val nativeClass = multiplatformCodebase.assertClass("test.pkg.Native")
            nativeClass.assertSourceSets("nativeMain")

            val webClass = multiplatformCodebase.assertClass("test.pkg.Web")
            webClass.assertSourceSets("wasmMain", "jsMain")

            val wasmClass = multiplatformCodebase.assertClass("test.pkg.Wasm")
            wasmClass.assertSourceSets("wasmMain")

            val jsClass = multiplatformCodebase.assertClass("test.pkg.Js")
            jsClass.assertSourceSets("jsMain")

            val jvmAndroidClass = multiplatformCodebase.assertClass("test.pkg.JvmAndroid")
            jvmAndroidClass.assertSourceSets("jvmMain", "androidMain")

            val jvmClass = multiplatformCodebase.assertClass("test.pkg.Jvm")
            jvmClass.assertSourceSets("jvmMain")

            val androidClass = multiplatformCodebase.assertClass("test.pkg.Android")
            androidClass.assertSourceSets("androidMain")
        }
    }
}
