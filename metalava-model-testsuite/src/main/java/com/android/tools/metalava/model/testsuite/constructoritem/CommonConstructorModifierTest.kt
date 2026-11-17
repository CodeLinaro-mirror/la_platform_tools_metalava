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

package com.android.tools.metalava.model.testsuite.constructoritem

import com.android.tools.metalava.model.ModifierKeyword
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Test

class CommonConstructorModifierTest : BaseModelTest() {
    private fun checkConstructorModifiers(
        javaParameters: String = "",
        expectedModifierKeywords: List<ModifierKeyword>,
    ) {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo($javaParameters);
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public Foo($javaParameters) {}
                    }
                """
            ),
        ) {
            val testItem = codebase.assertClass("test.pkg.Foo").constructors().single()
            val expectedListWithVisibility =
                listOf(ModifierKeyword.PUBLIC_KEYWORD) + expectedModifierKeywords
            assertEquals(expectedListWithVisibility, testItem.modifiers.keywordList)
        }
    }

    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA)
    @Test
    fun `Test varargs`() {
        checkConstructorModifiers(
            javaParameters = "String... args",
            // Adding a vararg parameter should not affect the constructor's modifiers.
            expectedModifierKeywords = emptyList(),
        )
    }
}
