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

package com.android.tools.metalava.model.testsuite.methoditem

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runners.Parameterized

/**
 * Common tests for [MethodItem.duplicate] and [ClassItem.inheritMethodFromNonApiAncestor].
 *
 * These methods do very similar jobs, i.e. take a [MethodItem] from one [ClassItem], create a copy
 * of it in some way, and then add it to another [ClassItem].
 */
class CommonCopyMethodItemTest : BaseModelTest() {

    enum class CopyMethod(
        val supportedInputFormats: Set<InputFormat> = setOf(InputFormat.JAVA, InputFormat.SIGNATURE)
    ) {
        DUPLICATE {
            override fun copy(
                sourceMethodItem: MethodItem,
                targetClassItem: ClassItem
            ): MethodItem {
                return sourceMethodItem.duplicate(targetClassItem)
            }
        },
        INHERIT(supportedInputFormats = setOf(InputFormat.JAVA)) {
            override fun copy(
                sourceMethodItem: MethodItem,
                targetClassItem: ClassItem
            ): MethodItem {
                return targetClassItem.inheritMethodFromNonApiAncestor(sourceMethodItem)
            }
        };

        abstract fun copy(sourceMethodItem: MethodItem, targetClassItem: ClassItem): MethodItem
    }

    companion object {
        @JvmStatic @Parameterized.Parameters fun comparisons() = CopyMethod.values()
    }

    /**
     * Set by injection by [Parameterized] after class initializers are called.
     *
     * Anything that accesses this, either directly or indirectly must do it after initialization,
     * e.g. from lazy fields or in methods called from test methods.
     *
     * See [codebaseCreatorConfig] for more info.
     */
    @Parameterized.Parameter(0) lateinit var copyMethod: CopyMethod

    private class CopyContext(
        override val codebase: Codebase,
        val sourceClassItem: ClassItem,
        val targetClassItem: ClassItem,
        val sourceMethodItem: MethodItem,
        val targetMethodItem: MethodItem,
    ) : CodebaseContext<Codebase>

    private fun runCopyTest(
        vararg inputs: InputSet,
        test: CopyContext.() -> Unit,
    ) {
        // If the copy method does not support the current input format then just return.
        if (inputFormat !in copyMethod.supportedInputFormats) return

        runCodebaseTest(
            *inputs,
        ) {
            val sourceClassItem = codebase.assertClass("test.pkg.Source")
            val targetClassItem = codebase.assertClass("test.pkg.Target")

            val sourceMethodItem = sourceClassItem.assertMethod("method", "")
            val targetMethodItem = copyMethod.copy(sourceMethodItem, targetClassItem)

            val context =
                CopyContext(
                    codebase,
                    sourceClassItem,
                    targetClassItem,
                    sourceMethodItem,
                    targetMethodItem,
                )

            context.test()
        }
    }

    @Test
    fun `test copy method from interface to class uses public visibility`() {
        runCopyTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public interface Source {
                            method public void method();
                          }
                          public class Target implements Source {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;

                        import java.io.IOException;

                        public interface Source  {
                            void method();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Target implements Source {}
                    """
                ),
            ),
        ) {
            // Make sure that the visibility level is public.
            assertEquals(VisibilityLevel.PUBLIC, sourceMethodItem.modifiers.getVisibilityLevel())
            assertEquals(VisibilityLevel.PUBLIC, targetMethodItem.modifiers.getVisibilityLevel())
        }
    }

    @Test
    fun `test copy method from interface to class does not copy default modifier`() {
        runCopyTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public interface Source {
                            method public default void method();
                          }
                          public class Target implements Source {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;

                        import java.io.IOException;

                        interface Source  {
                            default void method() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Target implements Source {}
                    """
                ),
            ),
        ) {
            // Make sure that the default modifier is not copied from an interface method to a
            // class.
            assertTrue(sourceMethodItem.modifiers.isDefault())
            assertFalse(targetMethodItem.modifiers.isDefault())
        }
    }

    @Test
    fun `test copy method from interface to class does copy static modifier`() {
        runCopyTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public interface Source {
                            method public static void method();
                          }
                          public class Target implements Source {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;

                        import java.io.IOException;

                        interface Source  {
                            static void method() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Target implements Source {}
                    """
                ),
            ),
        ) {
            // Make sure that the static modifier is copied from an interface method to a class.
            assertTrue(sourceMethodItem.modifiers.isStatic())
            assertTrue(targetMethodItem.modifiers.isStatic())
        }
    }

    @Test
    fun `test copy non-final method from non-final class to final class does not change final`() {
        runCopyTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Source {
                            method public void method();
                          }
                          public final class Target implements Source {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;

                        import java.io.IOException;

                        public class Source  {
                            public void method() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public final class Target implements Source {}
                    """
                ),
            ),
        ) {
            // Make sure that the final modifier is not added to a non-final method copied from a
            // non-final class to a final class.
            assertFalse(sourceClassItem.modifiers.isFinal())
            assertFalse(sourceMethodItem.modifiers.isFinal())
            assertTrue(targetClassItem.modifiers.isFinal())
            assertFalse(targetMethodItem.modifiers.isFinal())
        }
    }
}
