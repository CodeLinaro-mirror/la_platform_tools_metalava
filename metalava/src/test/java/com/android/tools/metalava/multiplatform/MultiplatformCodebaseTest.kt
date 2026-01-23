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
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.FilterAction
import com.android.tools.metalava.model.testing.FilterByProvider
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createNativeModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@RequiresCapabilities(Capability.KOTLIN, Capability.MULTIPLATFORM)
// K1 does not support KMP, but the capabilities above are configured for psi altogether and not
// differentiated between K1 and K2.
@FilterByProvider("psi", "k1", action = FilterAction.EXCLUDE)
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
            api =
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
}
