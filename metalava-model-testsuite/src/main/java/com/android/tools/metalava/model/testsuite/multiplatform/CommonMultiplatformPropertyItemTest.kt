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

import com.android.tools.metalava.model.ClassItem
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
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                        property public kotlin.Int foo;
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
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Common extends kotlin.Any {
                        ctor public Common();
                        property public kotlin.Int common;
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Android extends kotlin.Any {
                        ctor public Android();
                        property public kotlin.Int android;
                      }
                    }
                    """
                ),
            ),
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
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                        property public kotlin.Int kotlin.String.foo;
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
            inputSet(
                // This file wouldn't be output by metalava because it has an internal property.
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo extends kotlin.Any {
                        property internal final kotlin.Int foo;
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo extends kotlin.Any {
                        ctor public Foo();
                        property public final kotlin.Int foo;
                      }
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo extends kotlin.Any {
                        ctor public Foo();
                        property protected final kotlin.Int foo;
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
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo extends kotlin.Any {
                        ctor public Foo();
                        property public kotlin.Int kotlin.collections.List<kotlin.String>.list;
                        property public kotlin.Int kotlin.collections.List<kotlin.String?>.list;
                        property public kotlin.Int kotlin.String.string;
                        property public kotlin.Int kotlin.String?.string;
                      }
                    }
                    """
                )
            ),
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
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class ${'$'}TopLevelDeclarations {
                        property public static final kotlin.Int kotlin.Int.foo;
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class ${'$'}TopLevelDeclarations {
                        property public static final kotlin.Int kotlin.Int.android;
                      }
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class ${'$'}TopLevelDeclarations {
                        property public static final kotlin.Int kotlin.Int.native;
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

    @Test
    fun `Baseline id for top level regular PropertyItem`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                val foo = 0
                val String.foo
                    get() = 0
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource),
            inputSet(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class ${'$'}TopLevelDeclarations {
                        property public static final kotlin.Int foo;
                        property public static final kotlin.Int kotlin.String.foo;
                      }
                    }
                    """
                )
            ),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                )
        ) {
            val commonCodebase = multiplatformCodebase.sourceSetToCodebase["commonMain"]!!
            val facadeClass =
                commonCodebase.assertClass(
                    "test.pkg.${ClassItem.TOP_LEVEL_DECLARATION_FACADE_NAME}"
                )

            val topLevelVal = facadeClass.assertProperty("foo")
            assertThat(topLevelVal.baselineElementId()).isEqualTo("test.pkg#foo")

            val topLevelExtensionVal =
                facadeClass.assertProperty("foo", receiverTypeString = "kotlin.String")
            assertThat(topLevelExtensionVal.baselineElementId())
                .isEqualTo("test.pkg#kotlin.String.foo")
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN, InputFormat.SIGNATURE)
    @Test
    fun `Definition of property with context parameter`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo {
                    context(s: String)
                    expect val foo: Int
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                actual class Foo {
                    context(s: String)
                    actual val foo: Int get() = 0
                }
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                actual class Foo {
                    context(s: String)
                    actual val foo: Int get() = 0
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
                      public class Foo extends kotlin.Any {
                        property public final kotlin.Int foo(context kotlin.String s);
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
                )
        ) {
            val fooClass = multiplatformCodebase.assertClass("test.pkg.Foo")
            val fooVal =
                fooClass.assertProperty(
                    "foo",
                    contextParameterTypeStrings = listOf("kotlin.String")
                )
            fooVal.assertSourceSets("commonMain", "androidMain", "nativeMain")

            assertThat(fooVal.toString())
                .isEqualTo("multiplatform property test.pkg.Foo#foo(context kotlin.String)")
            assertThat(fooVal.baselineKey.elementId())
                .isEqualTo("test.pkg.Foo#foo(context kotlin.String)")

            assertThat(fooVal.contextParameterTypes).hasSize(1)
            assertThat(fooVal.contextParameterTypes[0].toTypeString()).isEqualTo("kotlin.String")

            assertThat(fooVal.contextParameters).hasSize(1)
            assertThat(fooVal.contextParameters[0].toString())
                .isEqualTo(
                    "multiplatform parameter #0 of multiplatform property test.pkg.Foo#foo(context kotlin.String)"
                )
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN, InputFormat.SIGNATURE)
    @Test
    fun `Definition of properties with context parameters with different nullability`() {
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
                "androidMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                actual class Foo {
                    context(s: String)
                    val foo: Int get() = 0
                }
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                actual class Foo {
                    context(s: String)
                    val foo: Int get() = 0

                    context(s: String?)
                    val foo: Int get() = 0
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
                      public class Foo extends kotlin.Any {
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo extends kotlin.Any {
                        property public final kotlin.Int foo(context kotlin.String s);
                      }
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo extends kotlin.Any {
                        property public final kotlin.Int foo(context kotlin.String s);
                        property public final kotlin.Int foo(context kotlin.String? s);
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
            val fooValNonNull =
                fooClass.assertProperty(
                    "foo",
                    contextParameterTypeStrings = listOf("kotlin.String")
                )
            fooValNonNull.assertSourceSets("androidMain", "nativeMain")
            assertThat(fooValNonNull.toString())
                .isEqualTo("multiplatform property test.pkg.Foo#foo(context kotlin.String)")

            val fooValNullable =
                fooClass.assertProperty(
                    "foo",
                    contextParameterTypeStrings = listOf("kotlin.String?")
                )
            fooValNullable.assertSourceSets("nativeMain")
            assertThat(fooValNullable.toString())
                .isEqualTo("multiplatform property test.pkg.Foo#foo(context kotlin.String?)")
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN, InputFormat.SIGNATURE)
    @Test
    fun `Definition of property with context parameter where parameter names differ by source set`() {
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
                "androidMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                actual class Foo {
                    context(android: String)
                    val foo: Int get() = 0
                }
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                actual class Foo {
                    context(native: String)
                    val foo: Int get() = 0
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
                      public class Foo extends kotlin.Any {
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo extends kotlin.Any {
                        property public final kotlin.Int foo(context kotlin.String android);
                      }
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo extends kotlin.Any {
                        property public final kotlin.Int foo(context kotlin.String native);
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
            val fooVal =
                fooClass.assertProperty(
                    "foo",
                    contextParameterTypeStrings = listOf("kotlin.String")
                )
            fooVal.assertSourceSets("androidMain", "nativeMain")
            val contextParameter = fooVal.contextParameters[0]
            contextParameter.publicName.assertSourceSetValues(
                "androidMain" to "android",
                "nativeMain" to "native"
            )
        }
    }
}
