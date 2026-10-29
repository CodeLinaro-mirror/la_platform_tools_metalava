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

package com.android.tools.metalava.multiplatform

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createNativeModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.android.tools.metalava.testing.signature
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@RequiresCapabilities(Capability.KOTLIN, Capability.MULTIPLATFORM)
class MultiplatformCodebaseTest : DriverTest() {
    @Test
    fun `Test creation of multiplatform codebase`() {
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
        check(
            sourceFiles = arrayOf(commonSource, androidSource, nativeSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createNativeModuleDescription(arrayOf(nativeSource)),
                ),
            enableMultiplatform = true,
            expectedApiSignature =
                """
                package test.pkg {
                  public final class Foo {
                    ctor public Foo();
                  }
                }
                """,
        ) {
            assertThat(multiplatformCodebase).isNotNull()
            multiplatformCodebase!!.assertSourceSets("commonMain", "androidMain", "nativeMain")
            multiplatformCodebase.assertClass("test.pkg.Foo")
        }
    }

    @Test
    fun `Test nullability of annotated java type`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                class Common
                """
            )
        val androidSource =
            java(
                "androidMain/src/test/pkg/Foo.java",
                """
                package test.pkg;
                import org.jspecify.annotations.NonNull;
                public class Foo {
                    public @NonNull String foo() { return ""; }
                }
                """
            )
        val jspecifyNonNull =
            kotlin(
                "androidMain/src/org/jspecify/annotations/NonNull.kt",
                """
                package org.jspecify.annotations
                @Target(AnnotationTarget.TYPE)
                annotation class NonNull
                """
            )
        check(
            sourceFiles = arrayOf(androidSource, commonSource, jspecifyNonNull),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource, jspecifyNonNull)),
                ),
            enableMultiplatform = true,
        ) {
            val androidCodebase = multiplatformCodebase!!.sourceSetToCodebase["androidMain"]
            val fooClass = androidCodebase!!.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", emptyList())
            assertThat(fooMethod.returnType().modifiers.nullability)
                .isEqualTo(TypeNullability.NONNULL)
        }
    }

    @Test
    fun `Test creation of multiplatform codebase with no regular codebase`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                expect class Foo
                """
            )
        val nativeSource =
            kotlin(
                "nativeMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                actual class Foo
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
            skipSourceArgs = true,
        ) {
            assertThat(codebase).isNull()
            assertThat(multiplatformCodebase).isNotNull()

            multiplatformCodebase!!.assertSourceSets("commonMain", "nativeMain")
            multiplatformCodebase.assertClass("test.pkg.Foo")
        }
    }

    @Test
    fun `Test creation of multiplatform codebase from signature files`() {
        check(
            multiplatformSignatureSource =
                listOf(
                    signature(
                        "commonMain.txt",
                        """
                            // Signature format: 5.0
                            package test.pkg {
                              public final class Common extends kotlin.Any {
                                ctor public Common();
                              }
                            }
                            """
                    )
                ),
            enableMultiplatform = true,
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
}
