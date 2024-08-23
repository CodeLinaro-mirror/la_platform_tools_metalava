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

package com.android.tools.metalava.model.testsuite.callableitem

import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.getCallableParameterDescriptorUsingDots
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runners.Parameterized

class CallableParameterDescriptorUsingDotsTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    data class TestParams(
        @Language("JAVA") val inputSource: String,
        val constructor: Boolean = false,
        val callableGetter: (Codebase) -> CallableItem = {
            val classItem = it.assertClass("test.pkg.Foo")
            val callables = if (constructor) classItem.constructors() else classItem.methods()
            callables.single()
        },
        val expectedResult: String?,
    ) {
        override fun toString(): String {
            return expectedResult.toString()
        }
    }

    companion object : Assertions {
        private val params =
            listOf(
                TestParams(
                    inputSource =
                        """
                            package test.pkg;
                            public class Foo {
                                public void method() {}
                            }
                        """,
                    expectedResult = "()",
                ),
                TestParams(
                    inputSource =
                        """
                            package test.pkg;
                            public class Foo {
                                public int method(int p, String s) {return p;}
                            }
                        """,
                    expectedResult = "(ILjava.lang.String;)",
                ),
                TestParams(
                    inputSource =
                        """
                            package test.pkg;
                            public class Foo {
                                public Foo(long p, byte b) {}
                            }
                        """,
                    constructor = true,
                    expectedResult = "(JB)",
                ),
                TestParams(
                    inputSource =
                        """
                            package test.pkg;
                            import java.util.Map;
                            public class Foo {
                                public class Bar {
                                    public Bar(Map.Entry<String, Integer> e) {}
                                }
                            }
                        """,
                    constructor = true,
                    callableGetter = { it.assertClass("test.pkg.Foo.Bar").constructors().single() },
                    // This is `null` because the PSI helper cannot resolve Bar to a class.
                    expectedResult = null,
                ),
                TestParams(
                    inputSource =
                        """
                            package test.pkg;
                            import java.util.Map;
                            public class Foo {
                                public static class Bar {
                                    public Bar(Map.Entry<String, Integer> e) {}
                                }
                            }
                        """,
                    constructor = true,
                    callableGetter = { it.assertClass("test.pkg.Foo.Bar").constructors().single() },
                    expectedResult = "(Ljava.util.Map.Entry;)",
                ),
            )

        @JvmStatic @Parameterized.Parameters fun params() = params
    }

    @Test
    fun `Test getCallableParameterDescriptorUsingDots`() {
        runCodebaseTest(java(params.inputSource)) {
            val callable = params.callableGetter(codebase)
            assertEquals(params.expectedResult, callable.getCallableParameterDescriptorUsingDots())
        }
    }
}
