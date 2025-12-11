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

import com.android.tools.metalava.model.testing.FilterAction.EXCLUDE
import com.android.tools.metalava.model.testing.FilterByProvider
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createNativeModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@FilterByProvider("psi", "k1", action = EXCLUDE)
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
}
