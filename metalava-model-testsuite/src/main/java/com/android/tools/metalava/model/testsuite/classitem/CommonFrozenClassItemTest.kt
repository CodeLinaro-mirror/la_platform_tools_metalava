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
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

/** Common tests for [ClassItem.freeze] and related functionality. */
class CommonFrozenClassItemTest : BaseModelTest() {
    @Test
    fun `Test freeze class and super types`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                       // Signature format: 2.0
                       package test.pkg {
                         public class PublicClass extends test.pkg.SuperClass implements test.pkg.SuperInterface {
                           ctor public PublicClass();
                         }
                         public class SuperClass {
                           ctor public SuperClass();
                           method public void foo();
                         }
                         public interface SuperInterface {
                           method public void bar();
                         }
                       } 
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class SuperClass {
                            public void foo() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public interface SuperInterface {
                            void bar();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class PublicClass extends SuperClass implements SuperInterface {}
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        open class SuperClass {
                            fun foo() {}
                        }
                    """
                ),
                kotlin(
                    """
                        package test.pkg
                        interface SuperInterface {
                            fun bar()
                        }
                    """
                ),
                kotlin(
                    """
                        package test.pkg
                        class PublicClass: SuperClass(), SuperInterface
                    """
                ),
            ),
        ) {
            val superClass = codebase.assertClass("test.pkg.SuperClass")
            val superInterface = codebase.assertClass("test.pkg.SuperInterface")
            val publicClass = codebase.assertClass("test.pkg.PublicClass")

            // Make sure that the classes are not frozen when initially loaded.
            assertFalse(superClass.frozen, message = "SuperClass.frozen")
            assertFalse(superInterface.frozen, message = "SuperInterface.frozen")
            assertFalse(publicClass.frozen, message = "PublicClass.frozen")

            // Freeze the class.
            publicClass.freeze()

            // Make sure that the classes are frozen after calling freeze(). SuperClass should be
            // frozen because its subclass was frozen. Similarly, SuperInterface should be frozen
            // as its subclass was frozen.
            assertTrue(superClass.frozen, message = "SuperClass.frozen")
            assertTrue(superInterface.frozen, message = "SuperInterface.frozen")
            assertTrue(publicClass.frozen, message = "PublicClass.frozen")
        }
    }

    @Test
    fun `Test addMethod on frozen class`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                       // Signature format: 2.0
                       package test.pkg {
                         public class PublicClass extends test.pkg.SuperClass {
                           ctor public PublicClass();
                         }
                         public class SuperClass {
                           ctor public SuperClass();
                           method public void foo();
                         }
                       } 
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;
                        class SuperClass {
                            public void foo() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class PublicClass extends SuperClass {}
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        open class SuperClass {
                            fun foo() {}
                        }
                    """
                ),
                kotlin(
                    """
                        package test.pkg
                        class PublicClass: SuperClass()
                    """
                ),
            ),
        ) {
            val superClass = codebase.assertClass("test.pkg.SuperClass")
            val superClassMethod = superClass.methods().single()
            val publicClass = codebase.assertClass("test.pkg.PublicClass")

            // Freeze the class.
            publicClass.freeze()

            // Duplicate and add a method, this should throw an exception.
            val duplicateMethod = superClassMethod.duplicate(publicClass)
            val error =
                assertThrows(IllegalStateException::class.java) {
                    publicClass.addMethod(duplicateMethod)
                }
            assertEquals("Cannot modify frozen class test.pkg.PublicClass", error.message)
        }
    }
}
