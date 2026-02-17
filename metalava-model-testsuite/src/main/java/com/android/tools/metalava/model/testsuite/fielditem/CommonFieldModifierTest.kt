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

package com.android.tools.metalava.model.testsuite.fielditem

import com.android.tools.metalava.model.ModifierKeyword
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Test

class CommonFieldModifierTest : BaseModelTest() {

    @Test
    fun `Test transient`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        field public transient int field;
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public  class Foo {
                        private Foo() {}
                        public transient int field;
                    }
                """
            ),
        ) {
            val testItem = codebase.assertClass("test.pkg.Foo").assertField("field")
            val expectedListWithVisibility =
                listOf(ModifierKeyword.PUBLIC_KEYWORD, ModifierKeyword.TRANSIENT_KEYWORD)
            assertEquals(expectedListWithVisibility, testItem.modifiers.keywordList)
        }
    }
}
