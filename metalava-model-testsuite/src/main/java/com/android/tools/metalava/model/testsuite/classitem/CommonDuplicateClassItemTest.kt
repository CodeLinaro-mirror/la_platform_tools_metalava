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

package com.android.tools.metalava.model.testsuite.classitem

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for implementations of [ClassItem]. */
class CommonDuplicateClassItemTest : BaseModelTest() {

    private fun runDuplicateTest(test: CodebaseContext.() -> Unit) {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Foo {}
                    """
                ),
                java(
                    "src2/test/pkg/Foo.java",
                    """
                        package test.pkg;

                        public class Foo {}
                    """
                )
            ),
            test = test,
        )
    }

    private fun CodebaseContext.checkCodebase(codebase: Codebase) {
        val fooClass = codebase.assertClass("test.pkg.Foo")
        assertEquals(
            "TESTROOT/src/test/pkg/Foo.java",
            removeTestSpecificDirectories(fooClass.fileLocation.path.toString())
        )

        val fooLocations =
            codebase
                .getPackages()
                .allClasses()
                .filter { it.qualifiedName() == "test.pkg.Foo" }
                .joinToString("\n") {
                    removeTestSpecificDirectories(it.fileLocation.path.toString())
                }
        assertEquals(
            """
                TESTROOT/src/test/pkg/Foo.java
            """
                .trimIndent(),
            fooLocations
        )
    }

    @Test
    fun `Test duplicate classes`() {
        runDuplicateTest { checkCodebase(codebase) }
    }
}
