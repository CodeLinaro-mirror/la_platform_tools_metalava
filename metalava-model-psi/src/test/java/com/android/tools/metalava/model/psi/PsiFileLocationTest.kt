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

import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import kotlin.test.Test
import kotlin.test.assertEquals

class PsiFileLocationTest : BaseModelTest() {
    /**
     * Casts [item] to a [PsiItem] and gets the source psi of its underlying PsiElement. Generates a
     * baseline key, checking that the element ID of that key matches [expectedKey].
     */
    private fun checkBaselineKeyFromPsi(item: Item, expectedKey: String) {
        val psi = (item as PsiItem).psi()
        val baselineKey = PsiFileLocation.getBaselineKey(psi)
        assertEquals(expectedKey, baselineKey.elementId())
    }

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
            checkBaselineKeyFromPsi(testKtPropertyItem, "test.pkg.TestKt#propertyInTestKt")
            val fooPropertyItem = codebase.assertClass("test.pkg.Foo").properties().single()
            checkBaselineKeyFromPsi(fooPropertyItem, "test.pkg.Foo#propertyInFoo")
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
            checkBaselineKeyFromPsi(
                foo,
                "test.pkg.Foo#foo(java.lang.String, java.util.List<? extends T>)"
            )
        }
    }
}
