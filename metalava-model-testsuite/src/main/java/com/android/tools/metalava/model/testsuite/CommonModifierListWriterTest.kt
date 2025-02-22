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

import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ModifierListWriter
import com.android.tools.metalava.testing.java
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for implementations of [ModifierListWriter]. */
class CommonModifierListWriterTest : BaseModelTest() {

    private fun Item.writeKeywords(normalizeFinal: Boolean = false): String {
        val stringWriter = StringWriter()
        val writer = ModifierListWriter.forSignature(stringWriter, skipNullnessAnnotations = true)
        writer.writeKeywords(this, normalizeFinal = normalizeFinal)
        return stringWriter.toString().trimEnd()
    }

    @Test
    fun `modifiers public`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        method public void method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        private Test() {}

                        public void method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.methods().single()

            assertEquals("public", methodItem.writeKeywords())
        }
    }

    @Test
    fun `modifiers public final method in open class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        method public final void method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        private Test() {}

                        public final void method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.methods().single()

            assertEquals("public final", methodItem.writeKeywords())
        }
    }

    @Test
    fun `modifiers public explicitly final method in final class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public final class Test {
                        method public final void method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public final class Test {
                        private Test() {}

                        public final void method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.methods().single()

            // This test reveals some inconsistencies between the models in how they handle a final
            // method in a final class. Psi ignores the `final` keyword on the method but text and
            // turbine do not. It is not 100% clear which is the correct behavior but at the moment
            // this treats the latter two behavior as correct.
            assertEquals("public final", methodItem.writeKeywords())
        }
    }

    @Test
    fun `modifiers public explicitly final method in final class - normalized`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public final class Test {
                        method public final void method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public final class Test {
                        private Test() {}

                        public final void method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.methods().single()

            assertEquals("public", methodItem.writeKeywords(normalizeFinal = true))
        }
    }

    @Test
    fun `modifiers public implicitly final method in final class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public final class Test {
                        method public void method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public final class Test {
                        private Test() {}

                        public void method() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.methods().single()

            assertEquals("public", methodItem.writeKeywords())
        }
    }
}
