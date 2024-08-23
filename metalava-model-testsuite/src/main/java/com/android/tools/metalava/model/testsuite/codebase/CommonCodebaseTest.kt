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

package com.android.tools.metalava.model.testsuite.codebase

import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for implementations of [MethodItem]. */
class CommonCodebaseTest : BaseModelTest() {

    @Test
    fun `Test getTopLevelClassesFromSource`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Outer {
                        private Outer() {}
                        public class Middle {
                            private Middle() {}
                            public class Inner {
                                private Inner(O o) {}
                            }
                        }
                    }
                """
            ),
        ) {
            val classes = codebase.getTopLevelClassesFromSource()

            assertEquals(listOf(codebase.assertClass("test.pkg.Outer")), classes)
        }
    }
}
