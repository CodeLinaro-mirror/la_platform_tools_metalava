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
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.fail
import org.junit.Test

/** Common tests for implementations of [ClassItem]. */
class CommonDuplicateClassItemTest : BaseModelTest() {

    private fun runDuplicateTest(codebaseChecker: CodebaseContext.() -> Unit) {
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
        ) {
            val providerSpecificIssues =
                when (codebaseCreatorConfig.providerName) {
                    "psi" ->
                        "MAIN_SRC/src2/test/pkg/Foo.java:3: warning: Attempted to register test.pkg.Foo twice; once from MAIN_SRC/src/test/pkg/Foo.java and this one from MAIN_SRC/src2/test/pkg/Foo.java [DuplicateSourceClass]"
                    "turbine" ->
                        "MAIN_SRC/src2/test/pkg/Foo.java:3:14: error: duplicate declaration of test.pkg.Foo [InvalidSyntax]"
                    else -> fail("Unknown provider ${codebaseCreatorConfig.providerName}")
                }
            assertAndRemoveReportedIssues(providerSpecificIssues)

            // If the codebase creator can continue after encountering errors while parsing then
            // check the status of the Codebase, otherwise do not.
            if (codebaseCreatorHasCapability(Capability.LAX_PARSER)) {
                codebaseChecker()
            }
        }
    }

    private fun CodebaseContext.checkCodebase(codebase: Codebase) {
        val fooClass = codebase.assertClass("test.pkg.Foo")
        assertEquals(
            "MAIN_SRC/src/test/pkg/Foo.java",
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
                MAIN_SRC/src/test/pkg/Foo.java
            """
                .trimIndent(),
            fooLocations
        )
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test duplicate classes`() {
        runDuplicateTest { checkCodebase(codebase) }
    }
}
