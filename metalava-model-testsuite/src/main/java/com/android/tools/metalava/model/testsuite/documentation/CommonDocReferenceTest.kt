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

package com.android.tools.metalava.model.testsuite.documentation

import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for references from within documentation comments. */
class CommonDocReferenceTest : BaseModelTest() {
    private fun checkItemDocumentationPrint(
        item: SelectableItem,
        expectedOutput: String,
        message: String? = null
    ) {
        val documentation = item.documentation
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { documentation.print(it) }
        val actualOutput = stringWriter.toString()
        assertEquals(expectedOutput.trimIndent(), actualOutput, message)
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    @Suppress("RedundantThrows")
    fun `Test @throws resolution`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import java.util.ConcurrentModificationException;
                        public class Test<X extends Throwable> {
                            /**
                             * @throws X because reason 1.
                             * @throws Y because reason 2.
                             * @throws TestException    because reason 3.
                             * @throws IllegalArgumentException because reason 4.
                             * @throws java.io.IOException because reason 5.
                             * @throws ConcurrentModificationException because reason 6.
                             */
                            public <Y extends Throwable> void method() throws X, Y, java.io.IOException {}

                            public class TestException extends RuntimeException {}
                        }
                    """
                ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val testMethod = testClass.methods().single()
            checkItemDocumentationPrint(
                testMethod,
                expectedOutput =
                    """
                        /**
                         * @throws X because reason 1.
                         * @throws Y because reason 2.
                         * @throws java.io.IOException because reason 5.
                         * @throws java.lang.IllegalArgumentException because reason 4.
                         * @throws java.util.ConcurrentModificationException because reason 6.
                         * @throws test.pkg.Test.TestException because reason 3.
                         */

                    """,
            )
        }
    }
}
