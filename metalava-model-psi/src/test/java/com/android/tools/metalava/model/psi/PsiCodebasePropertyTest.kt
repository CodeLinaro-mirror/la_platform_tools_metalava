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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PsiCodebasePropertyTest : BaseModelTest() {
    @Test
    fun `Test non-KMP codebase without project description`() {
        runCodebaseTest(
            kotlin(
                "main/src/test/pkg/Foo.kt",
                """
                    package test.pkg
                    class Foo
                """
            )
        ) {
            assertThat((codebase as PsiBasedCodebase).isMultiplatform).isFalse()
        }
    }

    @Test
    fun `Test non-KMP codebase with project description`() {
        val source =
            kotlin(
                "main/src/test/pkg/Foo.kt",
                """
                    package test.pkg
                    class Foo
                """
            )
        runCodebaseTest(
            inputSet(source),
            projectDescription =
                createProjectDescription(
                    createModuleDescription(
                        moduleName = "main",
                        android = true,
                        sourceFiles = arrayOf(source),
                        dependsOn = emptyList()
                    )
                )
        ) {
            assertThat((codebase as PsiBasedCodebase).isMultiplatform).isFalse()
        }
    }

    @Test
    fun `Test KMP codebase`() {
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
                "androidMain/src/test/pkg/Foo.android.kt",
                """
                    package test.pkg
                    actual class Foo
                """
            )
        runCodebaseTest(
            inputSet(commonSource, androidSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                )
        ) {
            assertThat((codebase as PsiBasedCodebase).isMultiplatform).isTrue()
        }
    }
}
