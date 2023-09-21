/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Common tests for implementations of [ClassItem]. */
@RunWith(Parameterized::class)
class CommonClassItemTest(parameters: TestParameters) : BaseModelTest(parameters) {

    @Test
    fun `empty class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        public Test() {}
                    }
                """
            ),
        ) { codebase ->
            val testClass = codebase.assertClass("test.pkg.Test")
            assertEquals("Test", testClass.fullName())
            assertEquals("test/pkg/Test", testClass.internalName())
            assertEquals("test.pkg.Test", testClass.qualifiedName())
            assertEquals("test.pkg.Test", testClass.qualifiedNameWithDollarInnerClasses())
            assertEquals(1, testClass.constructors().size)
            assertEquals(emptyList(), testClass.methods())
            assertEquals(emptyList(), testClass.fields())
            assertEquals(emptyList(), testClass.properties())
        }
    }
}
