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

package com.android.tools.metalava

import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createNativeModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

@RequiresCapabilities(Capability.MULTIPLATFORM)
class MultiplatformApiFileTest : DriverTest() {
    @Test
    fun `Test signature for common source set`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Common.kt",
                """
                package test.pkg
                class Common
                """
            )

        check(
            sourceFiles = arrayOf(commonSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
            enableMultiplatform = true,
            skipSourceArgs = true, // Don't create a regular Codebase
            multiplatformApi =
                mapOf(
                    "commonMain.txt" to
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Common extends kotlin.Any {
                            ctor public Common();
                          }
                        }
                        """,
                )
        )
    }

    @Test
    fun `Test signatures for multiple source sets`() {
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
                class Native
                """
            )

        check(
            sourceFiles = arrayOf(commonSource, androidSource, nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                ),
            enableMultiplatform = true,
            skipSourceArgs = true, // Don't create a regular Codebase
            multiplatformApi =
                mapOf(
                    "commonMain.txt" to
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Common extends kotlin.Any {
                            ctor public Common();
                          }
                        }
                        """,
                    "androidMain.txt" to
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Android extends kotlin.Any {
                            ctor public Android();
                          }
                        }
                        """,
                    "nativeMain.txt" to
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Native extends kotlin.Any {
                            ctor public Native();
                          }
                        }
                        """,
                )
        )
    }

    @Test
    fun `Test signature changes in platform source set`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo {
                    fun commonUnchanged()
                    fun commonChanged()
                }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo_android.kt",
                """
                package test.pkg
                actual class Foo {
                    actual fun commonUnchanged() = Unit
                    @Deprecated("deprecated in android")
                    actual fun commonChanged() = Unit
                    fun android() = Unit
                }
                """
            )

        check(
            sourceFiles = arrayOf(commonSource, androidSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                ),
            enableMultiplatform = true,
            skipSourceArgs = true, // Don't create a regular Codebase
            multiplatformApi =
                mapOf(
                    "commonMain.txt" to
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Foo extends kotlin.Any {
                            method public void commonChanged();
                            method public void commonUnchanged();
                          }
                        }
                        """,
                    // Delta API file contains `commonChanged` because the signature is different
                    // from in commonMain, but `commonUnchanged` is not included because the
                    // signature would be the same.
                    "androidMain.txt" to
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Foo extends kotlin.Any {
                            ctor public Foo();
                            method public void android();
                            method @Deprecated public void commonChanged();
                          }
                        }
                        """,
                )
        )
    }

    @Test
    fun `Test signatures with no common source set`() {
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
                class Native
                """
            )

        check(
            sourceFiles = arrayOf(androidSource, nativeSource),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource), dependsOn = emptyList()),
                    createNativeModuleDescription(arrayOf(nativeSource), dependsOn = emptyList()),
                ),
            enableMultiplatform = true,
            skipSourceArgs = true, // Don't create a regular Codebase
            multiplatformApi =
                mapOf(
                    "androidMain.txt" to
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Android extends kotlin.Any {
                            ctor public Android();
                          }
                        }
                        """,
                    "nativeMain.txt" to
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Native extends kotlin.Any {
                            ctor public Native();
                          }
                        }
                        """,
                )
        )
    }

    @Test
    fun `Test inclusion of empty top level declarations facade class`() {
        val commonSource =
            arrayOf(
                kotlin(
                    "commonMain/src/test/pkg/Common.kt",
                    """
                    package test.pkg
                    class Common
                    internal fun common() = Unit
                    """
                )
            )
        check(
            sourceFiles = commonSource,
            projectDescription =
                createProjectDescription(createCommonModuleDescription(commonSource)),
            enableMultiplatform = true,
            skipSourceArgs = true, // Don't create a regular Codebase
            multiplatformApi =
                mapOf(
                    "commonMain.txt" to
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Common extends kotlin.Any {
                            ctor public Common();
                          }
                        }
                        """
                )
        )
    }

    @Test
    fun `Test private nested class as superclass`() {
        val androidSource =
            arrayOf(
                java(
                    "androidMain/src/test/pkg/OuterClass.java",
                    """
                    package test.pkg;
                    public class OuterClass {
                        private class PrivateNestedClass {}
                        public class NestedClassWithSuper extends PrivateNestedClass {}
                    }
                    """
                )
            )
        check(
            sourceFiles = androidSource,
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(androidSource, dependsOn = emptyList())
                ),
            enableMultiplatform = true,
            skipSourceArgs = true, // Don't create a regular Codebase
            multiplatformApi =
                mapOf(
                    "androidMain.txt" to
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public class OuterClass extends kotlin.Any {
                            ctor public OuterClass();
                          }
                          public class OuterClass.NestedClassWithSuper extends kotlin.Any {
                            ctor public OuterClass.NestedClassWithSuper();
                          }
                        }
                        """
                )
        )
    }

    @Test
    fun `Test callables that differ by nullability`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo(s: String) {
                    fun foo(s: String?): Unit
                }
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo_native.kt",
                """
                package test.pkg
                actual class Foo actual constructor(s: String) {
                    constructor(s: String?) : this(s ?: "")
                    actual fun foo(s: String?) = Unit
                    fun foo(s: String) = Unit
                }
                """
            )
        check(
            sourceFiles = arrayOf(commonSource, nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                ),
            enableMultiplatform = true,
            skipSourceArgs = true, // Don't create a regular Codebase
            multiplatformApi =
                mapOf(
                    "commonMain.txt" to
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Foo extends kotlin.Any {
                            ctor public Foo(kotlin.String s);
                            method public void foo(kotlin.String? s);
                          }
                        }
                        """,
                    "nativeMain.txt" to
                        """
                        // Signature format: 5.0
                        package test.pkg {
                          public final class Foo extends kotlin.Any {
                            ctor public Foo(kotlin.String? s);
                            method public void foo(kotlin.String s);
                          }
                        }
                        """,
                )
        )
    }
}
