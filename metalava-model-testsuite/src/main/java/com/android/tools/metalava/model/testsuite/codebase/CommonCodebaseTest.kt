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
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
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

    @Test
    fun `Test resolve nested class sets correct containing class`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {
                        private Test() {}
                    }
                """
            ),
        ) {
            val entryClass = codebase.assertResolvedClass("java.util.Map.Entry")
            val mapClass = codebase.assertResolvedClass("java.util.Map")
            assertSame(entryClass.containingClass(), mapClass)
        }
    }

    @Test
    fun `Test resolve package`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {}
                """
            ),
        ) {
            // Make sure that the `java` package has not been created yet.
            assertNull(codebase.findPackage("java"), message = "find java package")

            // Resolve and create the `java` package.
            codebase.assertResolvedPackage("java")

            // Make sure that resolving an unknown package does not create it.
            assertNull(codebase.resolvePackage("unknown"), message = "resolve unknown package")
        }
    }

    @Test
    fun `Test ApiFlags passed through to codebase config`() {
        val apiFlags = ApiFlags(emptyMap())
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {}
                """
            ),
            testFixture =
                TestFixture(
                    apiFlags = apiFlags,
                ),
        ) {
            // Make sure that the `apiFlags` has been passed through to the Codebase.Config.
            assertSame(apiFlags, codebase.config.apiFlags)
        }
    }
}
