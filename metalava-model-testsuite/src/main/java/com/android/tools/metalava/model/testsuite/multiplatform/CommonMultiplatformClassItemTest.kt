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

import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.multiplatform.transformValues
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createNativeModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.KOTLIN)
class CommonMultiplatformClassItemTest : BaseModelTest() {
    @Test
    fun `Definition of expect actual class`() {
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
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    """
                )
            ),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                ),
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            fooClass.assertSourceSets("androidMain", "commonMain", "nativeMain")
        }
    }

    @Test
    fun `Definition of classes per source set`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/CommonClass.kt",
                """
                package test.pkg
                class CommonClass
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/AndroidClass.kt",
                """
                package test.pkg
                class AndroidClass
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/NativeClass.kt",
                """
                package test.pkg
                class NativeClass
                """
            )

        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class CommonClass extends kotlin.Any {
                        ctor public CommonClass();
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class AndroidClass extends kotlin.Any {
                        ctor public AndroidClass();
                      }
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class NativeClass extends kotlin.Any {
                        ctor public NativeClass();
                      }
                    }
                    """
                )
            ),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                ),
        ) {
            val commonClass = multiplatformCodebase.assertClass("test.pkg.CommonClass")
            commonClass.assertSourceSets("androidMain", "commonMain", "nativeMain")

            val androidClass = multiplatformCodebase.assertClass("test.pkg.AndroidClass")
            androidClass.assertSourceSets("androidMain")

            val nativeClass = multiplatformCodebase.assertClass("test.pkg.NativeClass")
            nativeClass.assertSourceSets("nativeMain")
        }
    }

    @Test
    fun `Definition of clashing classes in unrelated source sets`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/CommonClass.kt",
                """
                package test.pkg
                class CommonClass
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/AndroidClass.kt",
                """
                package test.pkg
                class Foo
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/NativeClass.kt",
                """
                package test.pkg
                class Foo
                """
            )

        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class CommonClass extends kotlin.Any {
                        ctor public CommonClass();
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                        ctor public Foo();
                      }
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                        ctor public Foo();
                      }
                    }
                    """
                )
            ),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                ),
        ) {
            val commonClass = multiplatformCodebase.assertClass("test.pkg.CommonClass")
            commonClass.assertSourceSets("androidMain", "commonMain", "nativeMain")

            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            fooClass.assertSourceSets("androidMain", "nativeMain")
        }
    }

    @Test
    fun `Definition of expect actual class with different annotations`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                   package test.pkg
                   annotation class CommonAnnotation
                   @CommonAnnotation expect class Foo
                   """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                   package test.pkg
                   annotation class AndroidAnnotation
                   @AndroidAnnotation actual class Foo
                   """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                   package test.pkg
                   annotation class NativeAnnotation
                   @NativeAnnotation class Foo
                   """
            )

        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public @interface CommonAnnotation {
                        ctor public CommonAnnotation();
                      }
                      @test.pkg.CommonAnnotation public final class Foo extends kotlin.Any {
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public @interface AndroidAnnotation {
                        ctor public AndroidAnnotation();
                      }
                      @test.pkg.AndroidAnnotation public final class Foo extends kotlin.Any {
                        ctor public Foo();
                      }
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public @interface NativeAnnotation {
                        ctor public NativeAnnotation();
                      }
                      @test.pkg.NativeAnnotation public final class Foo extends kotlin.Any {
                        ctor public Foo();
                      }
                    }
                    """
                )
            ),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                ),
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            fooClass.modifiers
                .transformValues { modifiers -> modifiers.annotations().map { it.qualifiedName } }
                .assertSourceSetValues(
                    "commonMain" to listOf("test.pkg.CommonAnnotation"),
                    "androidMain" to listOf("test.pkg.AndroidAnnotation"),
                    "nativeMain" to listOf("test.pkg.NativeAnnotation"),
                )
        }
    }

    @Test
    fun `Definition of expect actual class with different visibilities`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                   package test.pkg
                   internal expect class Foo
                   """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                   package test.pkg
                   public actual class Foo
                   """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                   package test.pkg
                   internal class Foo
                   """
            )

        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            inputSet(
                // This file wouldn't be output by metalava because it has an internal class.
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      internal final class Foo extends kotlin.Any {
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                      }
                    }
                    """
                ),
                // This file wouldn't be output by metalava because it has an internal class.
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      internal final class Foo extends kotlin.Any {
                      }
                    }
                    """
                )
            ),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                ),
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            fooClass.modifiers
                .transformValues { it.getVisibilityString() }
                .assertSourceSetValues(
                    "commonMain" to "internal",
                    "androidMain" to "public",
                    "nativeMain" to "internal",
                )
        }
    }

    @Test
    fun `Definition of expect actual class with different superclasses`() {
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
                open class AndroidSuperclass
                actual class Foo : AndroidSuperclass()
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                open class NativeSuperclass
                actual class Foo : NativeSuperclass()
                """
            )

        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class AndroidSuperclass extends kotlin.Any {
                        ctor public AndroidSuperclass();
                      }
                      public final class Foo extends test.pkg.AndroidSuperclass {
                        ctor public Foo();
                      }
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class NativeSuperclass extends kotlin.Any {
                        ctor public NativeSuperclass();
                      }
                      public final class Foo extends test.pkg.NativeSuperclass {
                        ctor public Foo();
                      }
                    }
                    """
                )
            ),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                ),
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            fooClass.superClassType
                .transformValues { it?.qualifiedName }
                .assertSourceSetValues(
                    "commonMain" to "kotlin.Any",
                    "androidMain" to "test.pkg.AndroidSuperclass",
                    "nativeMain" to "test.pkg.NativeSuperclass",
                )
        }
    }

    @Test
    fun `Definition of expect actual class with different interfaces`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                interface CommonInterface
                expect class Foo : CommonInterface
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                interface AndroidInterface
                actual class Foo : AndroidInterface, CommonInterface
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                interface NativeInterface1
                interface NativeInterface2
                actual class Foo : NativeInterface1, NativeInterface2, CommonInterface
                """
            )

        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public interface CommonInterface {
                      }
                      public final class Foo implements test.pkg.CommonInterface {
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public interface AndroidInterface {
                      }
                      public final class Foo implements test.pkg.AndroidInterface test.pkg.CommonInterface {
                        ctor public Foo();
                      }
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo implements test.pkg.CommonInterface test.pkg.NativeInterface1 test.pkg.NativeInterface2 {
                        ctor public Foo();
                      }
                      public interface NativeInterface1 {
                      }
                      public interface NativeInterface2 {
                      }
                    }
                    """
                )
            ),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                ),
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            fooClass.interfaceTypes
                .transformValues { interfaceTypes ->
                    interfaceTypes.map { it.qualifiedName }.sorted()
                }
                .assertSourceSetValues(
                    "commonMain" to listOf("test.pkg.CommonInterface"),
                    "androidMain" to
                        listOf("test.pkg.AndroidInterface", "test.pkg.CommonInterface"),
                    "nativeMain" to
                        listOf(
                            "test.pkg.CommonInterface",
                            "test.pkg.NativeInterface1",
                            "test.pkg.NativeInterface2",
                        ),
                )
        }
    }

    @Test
    fun `Test nested classes`() {
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
                "androidMain/src/test/pkg/Outer_android.kt",
                """
                package test.pkg
                actual class Outer {
                    actual class CommonMiddle {
                        actual class CommonInner
                    }
                    class AndroidMiddle {
                        class AndroidInner
                    }
                }
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Outer_native.kt",
                """
                package test.pkg
                actual class Outer {
                    actual class CommonMiddle {
                        actual class CommonInner
                        class NativeInner
                    }
                }
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Outer extends kotlin.Any {
                      }
                      public final class Outer.CommonMiddle extends kotlin.Any {
                      }
                      public final class Outer.CommonMiddle.CommonInner extends kotlin.Any {
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Outer.AndroidMiddle extends kotlin.Any {
                        ctor public Outer.AndroidMiddle();
                      }
                      public final class Outer.AndroidMiddle.AndroidInner extends kotlin.Any {
                        ctor public Outer.AndroidMiddle.AndroidInner();
                      }
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Outer.CommonMiddle.NativeInner extends kotlin.Any {
                        ctor public Outer.CommonMiddle.NativeInner();
                      }
                    }
                    """
                ),
            ),
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
            val androidMiddleClass =
                multiplatformCodebase.assertClass("test.pkg.Outer.AndroidMiddle")
            androidMiddleClass.assertSourceSets("androidMain")
            val androidInnerClass =
                multiplatformCodebase.assertClass("test.pkg.Outer.AndroidMiddle.AndroidInner")
            androidInnerClass.assertSourceSets("androidMain")
            val nativeInnerClass =
                multiplatformCodebase.assertClass("test.pkg.Outer.CommonMiddle.NativeInner")
            nativeInnerClass.assertSourceSets("nativeMain")

            assertThat(outerClass.nestedClasses)
                .containsExactly(commonMiddleClass, androidMiddleClass)
            assertThat(commonMiddleClass.nestedClasses)
                .containsExactly(commonInnerClass, nativeInnerClass)
            assertThat(androidMiddleClass.nestedClasses).containsExactly(androidInnerClass)
            assertThat(commonInnerClass.nestedClasses).isEmpty()
            assertThat(androidInnerClass.nestedClasses).isEmpty()
            assertThat(nativeInnerClass.nestedClasses).isEmpty()

            assertThat(outerClass.allClasses().toList())
                .containsExactly(
                    outerClass,
                    commonMiddleClass,
                    androidMiddleClass,
                    commonInnerClass,
                    nativeInnerClass,
                    androidInnerClass,
                )
            assertThat(commonMiddleClass.allClasses().toList())
                .containsExactly(commonMiddleClass, commonInnerClass, nativeInnerClass)
            assertThat(androidMiddleClass.allClasses().toList())
                .containsExactly(androidMiddleClass, androidInnerClass)
            assertThat(commonInnerClass.allClasses().toList()).containsExactly(commonInnerClass)
            assertThat(androidInnerClass.allClasses().toList()).containsExactly(androidInnerClass)
            assertThat(nativeInnerClass.allClasses().toList()).containsExactly(nativeInnerClass)
        }
    }

    @Test
    fun `Definition of typealiases`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Common.kt",
                """
                package test.pkg
                typealias Common = String
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Android.kt",
                """
                package test.pkg
                typealias Android = List<Int>
                """
            )

        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource),
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public typealias Common = kotlin.String;
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public typealias Android = kotlin.collections.List<kotlin.Int>;
                    }
                    """
                )
            ),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                )
        ) {
            val common = multiplatformCodebase.assertClass("test.pkg.Common")
            common.assertSourceSets("commonMain", "androidMain")
            common.classKind.assertSourceSetValues(
                "commonMain" to ClassKind.TYPEALIAS,
                "androidMain" to ClassKind.TYPEALIAS,
            )
            common.optionalAliasedType
                .transformValues { it?.toTypeString() }
                .assertSourceSetValues(
                    "commonMain" to "kotlin.String",
                    "androidMain" to "kotlin.String",
                )

            val android = multiplatformCodebase.assertClass("test.pkg.Android")
            android.assertSourceSets("androidMain")
            android.classKind.assertSourceSetValues(
                "androidMain" to ClassKind.TYPEALIAS,
            )
            android.optionalAliasedType
                .transformValues { it?.toTypeString() }
                .assertSourceSetValues(
                    "androidMain" to "kotlin.collections.List<kotlin.Int>",
                )
        }
    }

    @Test
    fun `Definition of expect class with actual typealias`() {
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
                   actual typealias Foo = String
                   """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                   package test.pkg
                   actual typealias Foo = List<Int>
                   """
            )

        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public typealias Foo = kotlin.String;
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public typealias Foo = kotlin.collections.List<kotlin.Int>;
                    }
                    """
                )
            ),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                ),
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            fooClass.assertSourceSets("androidMain", "nativeMain", "commonMain")
            fooClass.classKind.assertSourceSetValues(
                "commonMain" to ClassKind.CLASS,
                "androidMain" to ClassKind.TYPEALIAS,
                "nativeMain" to ClassKind.TYPEALIAS,
            )
            fooClass.optionalAliasedType
                .transformValues { it?.toTypeString() }
                .assertSourceSetValues(
                    "commonMain" to null,
                    "androidMain" to "kotlin.String",
                    "nativeMain" to "kotlin.collections.List<kotlin.Int>",
                )
        }
    }

    @Test
    fun `Test listing properties of a class`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo {
                    val common: Int
                    val String.common: Int
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo {
                    actual val common: Int = 0
                    actual val String.common: Int
                        get() = 0
                    val android: Int = 0
                    val String.android: Int
                        get() = 0
                    val <T> T.android: Int
                        get() = 0
                }
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource),
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                        property public kotlin.Int common;
                        property public kotlin.Int kotlin.String.common;
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                        ctor public Foo();
                        property public kotlin.Int android;
                        property public kotlin.Int kotlin.String.android;
                        property public <T> kotlin.Int T.android;
                      }
                    }
                    """
                )
            ),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            val commonNoReceiver = fooClass.assertProperty("common")
            commonNoReceiver.assertSourceSets("commonMain", "androidMain")
            val commonStringReceiver = fooClass.assertProperty("common", "kotlin.String")
            commonStringReceiver.assertSourceSets("commonMain", "androidMain")
            assertThat(commonNoReceiver).isNotEqualTo(commonStringReceiver)

            val androidNoReceiver = fooClass.assertProperty("android")
            androidNoReceiver.assertSourceSets("androidMain")
            val androidStringReceiver = fooClass.assertProperty("android", "kotlin.String")
            androidStringReceiver.assertSourceSets("androidMain")
            val androidVariableReceiver = fooClass.assertProperty("android", "T")
            androidVariableReceiver.assertSourceSets("androidMain")
            assertThat(androidNoReceiver).isNotEqualTo(androidStringReceiver)
            assertThat(androidNoReceiver).isNotEqualTo(androidVariableReceiver)
            assertThat(androidStringReceiver).isNotEqualTo(androidVariableReceiver)

            assertThat(fooClass.properties)
                .containsExactly(
                    commonNoReceiver,
                    commonStringReceiver,
                    androidNoReceiver,
                    androidStringReceiver,
                    androidVariableReceiver
                )
        }
    }

    @Test
    fun `Test listing methods of a class`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo {
                    fun commonMethod(i: Int, s: String): Unit
                    fun commonMethod(i: Int, s: String?): Unit
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo {
                    actual fun commonMethod(i: Int, s: String) = Unit
                    actual fun commonMethod(i: Int, s: String?) = Unit
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
                    actual fun commonMethod(i: Int, s: String) = Unit
                    actual fun commonMethod(i: Int, s: String?) = Unit
                    fun nativeMethod(s: String?) = Unit
                }
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                        method public void commonMethod(kotlin.Int i, kotlin.String s);
                        method public void commonMethod(kotlin.Int i, kotlin.String? s);
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Foo extends kotlin.Any {
                            ctor public Foo();
                            method public void androidMethod(kotlin.String? s);
                          }
                        }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                        ctor public Foo();
                        method public void nativeMethod(kotlin.String? s);
                      }
                    }
                    """
                )
            ),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")

            val commonNonNull =
                fooClass.assertMethod("commonMethod", listOf("kotlin.Int", "kotlin.String"))
            commonNonNull.assertSourceSets("commonMain", "androidMain", "nativeMain")
            val commonNullable =
                fooClass.assertMethod("commonMethod", listOf("kotlin.Int", "kotlin.String?"))
            commonNullable.assertSourceSets("commonMain", "androidMain", "nativeMain")
            assertThat(commonNonNull).isNotEqualTo(commonNullable)

            val androidMethod = fooClass.assertMethod("androidMethod", listOf("kotlin.String?"))
            androidMethod.assertSourceSets("androidMain")

            val nativeMethod = fooClass.assertMethod("nativeMethod", listOf("kotlin.String?"))
            nativeMethod.assertSourceSets("nativeMain")

            assertThat(fooClass.methods)
                .containsExactly(commonNonNull, commonNullable, androidMethod, nativeMethod)
        }
    }

    @Test
    fun `Test listing constructors of a class`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo(i: Int, s: String)
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo actual constructor(i: Int, s: String) {
                    constructor(s: String?): this(0, s)
                }
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo actual constructor(i: Int, s: String) {
                    constructor(i: Int, s: String?) : this(i, s ?: "")
                }
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource, androidSource, nativeSource),
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                        ctor public Foo(kotlin.Int i, kotlin.String s);
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                        ctor public Foo(kotlin.String? s);
                      }
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                        ctor public Foo(kotlin.Int i, kotlin.String? s);
                      }
                    }
                    """
                )
            ),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")

            val commonCtor = fooClass.assertConstructor(listOf("kotlin.Int", "kotlin.String"))
            commonCtor.assertSourceSets("commonMain", "androidMain", "nativeMain")

            val androidCtor = fooClass.assertConstructor(listOf("kotlin.String?"))
            androidCtor.assertSourceSets("androidMain")

            val nativeCtor = fooClass.assertConstructor(listOf("kotlin.Int", "kotlin.String?"))
            nativeCtor.assertSourceSets("nativeMain")
            assertThat(commonCtor).isNotEqualTo(nativeCtor)

            assertThat(fooClass.constructors).containsExactly(commonCtor, androidCtor, nativeCtor)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Test nested and inner classes`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Outer.kt",
                """
                package test.pkg
                class Outer {
                    inner class Inner
                    class Nested
                }
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
        ) {
            val commonCodebase = multiplatformCodebase.sourceSetToCodebase["commonMain"]
            val outerClass = commonCodebase!!.assertClass("test.pkg.Outer")
            assertThat(outerClass.modifiers.isStatic()).isFalse()
            val innerClass = commonCodebase.assertClass("test.pkg.Outer.Inner")
            assertThat(innerClass.modifiers.isStatic()).isFalse()
            val nestedClass = commonCodebase.assertClass("test.pkg.Outer.Nested")
            assertThat(nestedClass.modifiers.isStatic()).isTrue()
        }
    }
}
