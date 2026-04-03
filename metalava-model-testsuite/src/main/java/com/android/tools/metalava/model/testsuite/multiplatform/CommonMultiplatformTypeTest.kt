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

package com.android.tools.metalava.model.testsuite.multiplatform

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.createModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.defaultJsPlatforms
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CommonMultiplatformTypeTest : BaseModelTest() {
    @Test
    fun `Test usage of dynamic type for js source set`() {
        val jsSource =
            kotlin(
                "jsMain/src/test/pkg/Foo.kt",
                """
                package test.pkg
                interface Foo {
                    fun foo(): dynamic
                }
                """
            )
        runMultiplatformCodebaseTest(
            inputSet(jsSource),
            projectDescription =
                createProjectDescription(
                    createModuleDescription(
                        moduleName = "jsMain",
                        android = false,
                        kotlinPlatforms = defaultJsPlatforms,
                        sourceFiles = arrayOf(jsSource),
                        dependsOn = emptyList(),
                    ),
                )
        ) {
            val jsCodebase = multiplatformCodebase.sourceSetToCodebase["jsMain"]!!
            val fooClass = jsCodebase.assertClass("test.pkg.Foo")

            val dynamicType = fooClass.assertMethod("foo", emptyList()).returnType()
            dynamicType.assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("dynamic")
                assertThat(outerClassType).isNull()
                assertThat(arguments).isEmpty()
            }
        }
    }
}
