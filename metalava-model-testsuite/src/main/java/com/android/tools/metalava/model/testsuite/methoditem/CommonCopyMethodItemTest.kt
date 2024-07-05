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
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testsuite.memberitem.CommonCopyMemberItemTest
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
class CommonCopyMethodItemTest : CommonCopyMemberItemTest<MethodItem>() {

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

    override fun supportsInputFormat(): Boolean {
        return (inputFormat in copyMethod.supportedInputFormats)
    }

    override fun getMember(sourceClassItem: ClassItem) = sourceClassItem.assertMethod("method", "")

    override fun copyMember(sourceMemberItem: MethodItem, targetClassItem: ClassItem) =
        copyMethod.copy(sourceMemberItem, targetClassItem)

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
            assertEquals(VisibilityLevel.PUBLIC, sourceMemberItem.modifiers.getVisibilityLevel())
            assertEquals(VisibilityLevel.PUBLIC, copiedMemberItem.modifiers.getVisibilityLevel())
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
            assertTrue(sourceMemberItem.modifiers.isDefault())
            assertFalse(copiedMemberItem.modifiers.isDefault())
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
            assertTrue(sourceMemberItem.modifiers.isStatic())
            assertTrue(copiedMemberItem.modifiers.isStatic())
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
            assertFalse(sourceMemberItem.modifiers.isFinal())
            assertTrue(targetClassItem.modifiers.isFinal())
            assertFalse(copiedMemberItem.modifiers.isFinal())
        }
    }

    @Test
    fun `test copy non deprecated method from non deprecated class to deprecated class treats method as deprecated`() {
        runCopyTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Source {
                            method public void method();
                          }
                          @Deprecated public class Target implements Source {
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

                        /** @deprecated */
                        @Deprecated
                        public final class Target implements Source {}
                    """
                ),
            ),
        ) {
            // Make sure that the source class and member are not deprecated but the target
            // class is explicitly deprecated.
            sourceClassItem.assertNotDeprecated()
            sourceMemberItem.assertNotDeprecated()
            targetClassItem.assertExplicitlyDeprecated()

            // Make sure that the copied member is implicitly deprecated because it inherits it from
            // the deprecated target class.
            copiedMemberItem.assertImplicitlyDeprecated()
        }
    }

    @Test
    fun `test copy non deprecated method from deprecated class to non deprecated class treats method as deprecated`() {
        runCopyTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          @Deprecated public class Source {
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

                        /** @deprecated */
                        @Deprecated
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
            // Make sure that the source member is implicitly deprecated due to being inside an
            // explicitly deprecated class but the target class is not deprecated at all.
            sourceClassItem.assertExplicitlyDeprecated()
            sourceMemberItem.assertImplicitlyDeprecated()
            targetClassItem.assertNotDeprecated()

            // Make sure that the copied member is not implicitly deprecated because implicit
            // deprecation is ignored when copying.
            copiedMemberItem.assertNotDeprecated()
        }
    }

    @Test
    fun `test copy deprecated method from one class to another keeps method as deprecated`() {
        runCopyTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Source {
                            method @Deprecated public void method();
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

                        public class Source  {
                            /** @deprecated */
                            @Deprecated public void method() {}
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
            // Make sure that the source class and target class are not deprecated by the source
            // field is explicitly deprecated.
            sourceClassItem.assertNotDeprecated()
            sourceMemberItem.assertExplicitlyDeprecated()
            targetClassItem.assertNotDeprecated()

            // Make sure that the copied member is deprecated as its explicitly deprecated status is
            // copied.
            copiedMemberItem.assertExplicitlyDeprecated()
        }
    }
}
