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

package com.android.tools.metalava.model.testsuite.fielditem

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.testsuite.memberitem.CommonCopyMemberItemTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** Common tests for [FieldItem.duplicate]. */
class CommonCopyFieldItemTest : CommonCopyMemberItemTest<FieldItem>() {

    override fun getMember(sourceClassItem: ClassItem) = sourceClassItem.assertField("field")

    override fun copyMember(sourceMemberItem: FieldItem, targetClassItem: ClassItem) =
        sourceMemberItem.duplicate(targetClassItem)

    @Test
    fun `test copy field from interface to class uses public visibility`() {
        runCopyTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public interface Source {
                            field public int field;
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
                            int field;
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
    fun `test copy field from interface to class does copy static modifier`() {
        runCopyTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public interface Source {
                            field public static int field;
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
                            static int field;
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
            // Make sure that the static modifier is copied from an interface field to a class.
            assertTrue(sourceMemberItem.modifiers.isStatic())
            assertTrue(copiedMemberItem.modifiers.isStatic())
        }
    }

    @Test
    fun `test copy field from interface to class does set implicit static modifier`() {
        runCopyTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        import java.io.IOException;

                        interface Source  {
                            int field;
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
            // Make sure that the static modifier is copied from an interface field to a class.
            assertTrue(sourceMemberItem.modifiers.isStatic())
            assertTrue(copiedMemberItem.modifiers.isStatic())
        }
    }

    @Test
    fun `test copy non deprecated field from non deprecated class to deprecated class treats field as deprecated`() {
        runCopyTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Source {
                            field public int field;
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
                            public int field;
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
    fun `test copy non deprecated field from deprecated class to non deprecated class treats field as not deprecated`() {
        runCopyTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          @Deprecated public class Source {
                            field public int field;
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
                            public int field;
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
    fun `test copy deprecated field from one class to another keeps field as deprecated`() {
        runCopyTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Source {
                            field @Deprecated public int field;
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
                            @Deprecated public int field;
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
