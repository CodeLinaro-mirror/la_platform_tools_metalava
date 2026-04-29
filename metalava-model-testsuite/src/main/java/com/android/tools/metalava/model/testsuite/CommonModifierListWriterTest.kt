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

import com.android.tools.metalava.model.FlaggedApiInheritance
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ModifierListWriter
import com.android.tools.metalava.testing.java
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for implementations of [ModifierListWriter]. */
class CommonModifierListWriterTest : BaseModelTest() {

    private fun Item.writeKeywords(
        normalizeFinal: Boolean = false,
        normalizeAbstract: Boolean = false,
    ): String {
        val stringWriter = StringWriter()
        val writer =
            ModifierListWriter.forSignature(
                stringWriter,
                skipNullnessAnnotations = true,
                normalizeFinal = normalizeFinal,
                normalizeAbstract = normalizeAbstract,
                flaggedApiInheritance = FlaggedApiInheritance.NONE,
            )
        writer.writeKeywords(this)
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

            assertEquals("public", methodItem.writeKeywords())
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

    /** Check handling of enum method with `abstract` keyword. */
    private fun checkAbstractEnumMethod(normalizeAbstract: Boolean, expectedKeywords: String) {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    public enum Test {
                        VALUE {
                           public void method() {}
                        },
                        ;

                        public abstract void method();
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.methods().single()

            assertEquals(
                expectedKeywords,
                methodItem.writeKeywords(
                    normalizeAbstract = normalizeAbstract,
                )
            )
        }
    }

    @Test
    fun `Test abstract modifier on enum class method - not normalized`() {
        checkAbstractEnumMethod(
            normalizeAbstract = false,
            expectedKeywords = "public abstract",
        )
    }

    @Test
    fun `Test abstract modifier on enum class method - normalized`() {
        checkAbstractEnumMethod(
            normalizeAbstract = true,
            expectedKeywords = "public",
        )
    }

    /** Check handling of annotation method with `abstract` keyword. */
    private fun checkAbstractAnnotationMethod(
        normalizeAbstract: Boolean,
        expectedKeywords: String
    ) {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    public @interface Test {
                        abstract void method();
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.methods().single()

            assertEquals(
                expectedKeywords,
                methodItem.writeKeywords(
                    normalizeAbstract = normalizeAbstract,
                )
            )
        }
    }

    @Test
    fun `Test abstract modifier on annotation class method - not normalized`() {
        checkAbstractAnnotationMethod(
            normalizeAbstract = false,
            expectedKeywords = "public abstract",
        )
    }

    @Test
    fun `Test abstract modifier on annotation class method - normalized`() {
        checkAbstractAnnotationMethod(
            normalizeAbstract = true,
            expectedKeywords = "public",
        )
    }
}
