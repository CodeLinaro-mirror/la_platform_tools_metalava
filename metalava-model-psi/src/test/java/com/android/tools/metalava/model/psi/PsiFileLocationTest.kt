/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.tools.metalava.testing.kotlin
import kotlin.test.Test
import kotlin.test.assertEquals

class PsiFileLocationTest : BaseModelTest() {
    @Test
    fun `Baseline key for top level KtProperty`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    "src/test/pkg/Test.kt",
                    """
                        package test.pkg
                        val propertyInTestKt = 0
                    """
                ),
                kotlin(
                    "src/test/pkg/Foo.kt",
                    """
                        @file:JvmName("Foo")
                        package test.pkg
                        val propertyInFoo = 0
                    """
                )
            )
        ) {
            val testKtPropertyItem = codebase.assertClass("test.pkg.TestKt").properties().single()
            assertEquals(
                "test.pkg.TestKt#propertyInTestKt",
                testKtPropertyItem.baselineKey.elementId(),
                message = "from TestKt PropertyItem.baselineKey"
            )
            assertEquals(
                "test.pkg.TestKt#propertyInTestKt",
                testKtPropertyItem.fileLocation.baselineKey?.elementId(),
                message = "from PropertyItem.fileLocation.baselineKey"
            )
            val fooPropertyItem = codebase.assertClass("test.pkg.Foo").properties().single()
            assertEquals(
                "test.pkg.Foo#propertyInFoo",
                fooPropertyItem.baselineKey.elementId(),
                message = "from Foo PropertyItem.baselineKey"
            )
            assertEquals(
                "test.pkg.Foo#propertyInFoo",
                fooPropertyItem.fileLocation.baselineKey?.elementId(),
                message = "from Foo PropertyItem.fileLocation.baselineKey"
            )
        }
    }

    @Test
    fun `Baseline key for KtFunction`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    interface Foo {
                        fun <T> String.foo(arg: List<T>): String
                    }
                """
            )
        ) {
            val foo = codebase.assertClass("test.pkg.Foo").methods().single()
            assertEquals(
                "test.pkg.Foo#foo(String, java.util.List<? extends T>)",
                foo.baselineKey.elementId(),
                message = "from MethodItem.baselineKey"
            )
            assertEquals(
                "test.pkg.Foo#foo(java.lang.String, java.util.List<? extends T>)",
                foo.fileLocation.baselineKey?.elementId(),
                message = "from MethodItem.fileLocation.baselineKey"
            )
        }
    }
}
