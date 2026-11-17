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

package com.android.tools.metalava.model.testsuite.methoditem

import com.android.tools.metalava.model.ModifierKeyword
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Test

@SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA)
class CommonMethodModifierTest : BaseModelTest() {
    private fun checkMethodModifiers(
        javaClassModifiers: String = "",
        javaMethodModifiers: String = "",
        javaMethodParameters: String = "",
        expectedModifierKeywords: List<ModifierKeyword>,
    ) {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public $javaClassModifiers class Foo {
                        method public $javaMethodModifiers void method($javaMethodParameters);
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public $javaClassModifiers class Foo {
                        private Foo() {}
                        public $javaMethodModifiers void method($javaMethodParameters) {}
                    }
                """
            ),
        ) {
            val testItem = codebase.assertClass("test.pkg.Foo").methods().single()
            val expectedListWithVisibility =
                listOf(ModifierKeyword.PUBLIC_KEYWORD) + expectedModifierKeywords
            assertEquals(expectedListWithVisibility, testItem.modifiers.keywordList)
        }
    }

    @Test
    fun `Test varargs`() {
        checkMethodModifiers(
            javaMethodParameters = "String... args",
            // Adding a vararg parameter should not affect the method's modifiers.
            expectedModifierKeywords = emptyList(),
        )
    }

    @Test
    fun `Test final method in final class`() {
        checkMethodModifiers(
            javaClassModifiers = "final",
            javaMethodModifiers = "final",
            // The final should be dropped from the method as it is redundant as the class is final.
            expectedModifierKeywords = emptyList(),
        )
    }

    @Test
    fun `Test non-final method in final class`() {
        checkMethodModifiers(
            javaClassModifiers = "final",
            javaMethodModifiers = "",
            // Being inside a final class should not affect the modifiers of the method.
            expectedModifierKeywords = emptyList(),
        )
    }

    @Test
    fun `Test final method in non-final class`() {
        checkMethodModifiers(
            javaClassModifiers = "",
            javaMethodModifiers = "final",
            // A final method should keep the final modifier when in a non-final class.
            expectedModifierKeywords = listOf(ModifierKeyword.FINAL_KEYWORD),
        )
    }
}
