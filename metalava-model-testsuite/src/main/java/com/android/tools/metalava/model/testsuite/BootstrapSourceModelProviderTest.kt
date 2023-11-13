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

import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Provides a set of tests that are geared towards helping to bootstrap a new model.
 *
 * The basic idea is that each test (in numerical order) requires a small increment over the
 * previous test so that a developer would start by running the first test, making it pass,
 * submitting the changes and then moving on to the next test.
 */
@RunWith(Parameterized::class)
class BootstrapSourceModelProviderTest(parameters: TestParameters) : BaseModelTest(parameters) {

    @Test
    fun `010 - check source model provider exists`() {
        // Do nothing.
    }

    @Test
    fun `020 - check empty file`() {
        runSourceCodebaseTest(java("")) { codebase -> assertNotNull(codebase) }
    }

    @Test
    fun `030 - check simplest class`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    class Test {
                    }
                """
            ),
        ) { codebase ->
            val classItem = codebase.assertClass("test.pkg.Test")
            assertEquals("test.pkg.Test", classItem.qualifiedName())
        }
    }

    @Test
    fun `040 - check package exists`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    class Test {
                    }
                """
            ),
        ) { codebase ->
            val packageItem = codebase.assertPackage("test.pkg")
            assertEquals("test.pkg", packageItem.qualifiedName())
            assertEquals(1, packageItem.topLevelClasses().count(), message = "")
        }
    }

    @Test
    fun `050 - check field exists`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    class Test {
                        int field;
                    }
                """
            ),
        ) { codebase ->
            val testClass = codebase.assertClass("test.pkg.Test")
            val fieldItem = testClass.assertField("field")
            assertEquals("field", fieldItem.name())
            assertEquals(testClass, fieldItem.containingClass())
        }
    }

    @Test
    fun `060 - check method exists`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    class Test {
                        void method();
                    }
                """
            ),
        ) { codebase ->
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodItem = testClass.assertMethod("method", "")
            assertEquals("method", methodItem.name())
        }
    }

    @Test
    fun `070 - check constructor exists`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    class Test {
                        public Test() {}
                    }
                """
            ),
        ) { codebase ->
            val testClass = codebase.assertClass("test.pkg.Test")
            val constructorItem = testClass.assertConstructor("")
            assertEquals("Test", constructorItem.name())
        }
    }

    @Test
    fun `080 - check inner class`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    class Test {
                      class InnerTestClass {}
                    }
                """
            ),
        ) { codebase ->
            val classItem = codebase.assertClass("test.pkg.Test")
            val innerClassItem = codebase.assertClass("test.pkg.Test.InnerTestClass")
            assertEquals("test.pkg.Test.InnerTestClass", innerClassItem.qualifiedName())
            assertEquals("Test.InnerTestClass", innerClassItem.fullName())
            assertEquals("InnerTestClass", innerClassItem.simpleName())
            assertEquals(classItem, innerClassItem.containingClass())
            assertEquals(1, classItem.innerClasses().count(), message = "")
        }
    }

    @Test
    fun `090 - check class hierarchy`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    interface SuperInterface{}
                    abstract class SuperClass implements SuperInterface{}

                    interface SuperChildInterface{}
                    interface ChildInterface extends SuperChildInterface,SuperInterface{}

                    class Test extends SuperClass implements ChildInterface{}
                """
            ),
        ) { codebase ->
            val classItem = codebase.assertClass("test.pkg.Test")
            val superClassItem = codebase.assertClass("test.pkg.SuperClass")
            val superInterfaceItem = codebase.assertClass("test.pkg.SuperInterface")
            val childInterfaceItem = codebase.assertClass("test.pkg.ChildInterface")
            val superChildInterfaceItem = codebase.assertClass("test.pkg.SuperChildInterface")
            assertEquals(superClassItem, classItem.superClass())
            assertEquals(3, classItem.allInterfaces().count(), message = "")
            assertEquals(true, classItem.allInterfaces().contains(childInterfaceItem))
            assertEquals(true, classItem.allInterfaces().contains(superInterfaceItem))
            assertEquals(true, classItem.allInterfaces().contains(superChildInterfaceItem))
            assertEquals(3, childInterfaceItem.allInterfaces().count(), message = "")
            assertEquals(true, childInterfaceItem.allInterfaces().contains(superChildInterfaceItem))
            assertEquals(true, childInterfaceItem.allInterfaces().contains(childInterfaceItem))
            assertEquals(true, classItem.allInterfaces().contains(superInterfaceItem))
        }
    }

    @Test
    fun `100 - check class types`() {
        runSourceCodebaseTest(
            java(
                """
                  package test.pkg;

                  interface TestInterface{}
                  enum TestEnum {}
                  @interface TestAnnotation {}
                """
            ),
        ) { codebase ->
            val interfaceItem = codebase.assertClass("test.pkg.TestInterface")
            val enumItem = codebase.assertClass("test.pkg.TestEnum")
            val annotationItem = codebase.assertClass("test.pkg.TestAnnotation")
            assertEquals(true, interfaceItem.isInterface())
            assertEquals(true, enumItem.isEnum())
            assertEquals(true, annotationItem.isAnnotationType())
        }
    }

    @Test
    fun `110 - advanced package test`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        class Test {
                            class Inner {}
                        }
                    """
                ),
                java("""
                        package test;
                     """),
            ),
        ) { codebase ->
            val packageItem = codebase.assertPackage("test.pkg")
            val parentPackageItem = codebase.assertPackage("test")
            val rootPackageItem = codebase.assertPackage("")
            val classItem = codebase.assertClass("test.pkg.Test")
            val innerClassItem = codebase.assertClass("test.pkg.Test.Inner")
            assertEquals(1, packageItem.topLevelClasses().count())
            assertEquals(0, parentPackageItem.topLevelClasses().count())
            assertEquals(parentPackageItem, packageItem.containingPackage())
            assertEquals(rootPackageItem, parentPackageItem.containingPackage())
            assertEquals(null, rootPackageItem.containingPackage())
            assertEquals(packageItem, classItem.containingPackage())
            assertEquals(packageItem, innerClassItem.containingPackage())
        }
    }

    @Test
    fun `120 - check modifiers`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public final class Test1 {
                        private int var1;
                        protected static final int var2;
                        int var3;
                    }
                """
            ),
        ) { codebase ->
            val packageItem = codebase.assertPackage("test.pkg")
            val classItem1 = codebase.assertClass("test.pkg.Test1")
            val fieldItem1 = classItem1.assertField("var1")
            val fieldItem2 = classItem1.assertField("var2")
            val fieldItem3 = classItem1.assertField("var3")
            val packageMod = packageItem.mutableModifiers()
            val classMod1 = classItem1.mutableModifiers()
            val fieldMod1 = fieldItem1.mutableModifiers()
            val fieldMod2 = fieldItem2.mutableModifiers()
            val fieldMod3 = fieldItem3.mutableModifiers()
            assertEquals(true, packageMod.isPublic())
            assertEquals(true, classMod1.isPublic())
            assertEquals(true, fieldMod1.isPrivate())
            assertEquals(false, fieldMod1.isPackagePrivate())
            assertEquals(false, fieldMod2.isPrivate())
            assertEquals(true, fieldMod2.asAccessibleAs(fieldMod1))
            assertEquals(true, fieldMod3.isPackagePrivate())
        }
    }

    /**
     * Check for the following:
     * 1) If a class from classpath is needed by some source class, the corresponding classItem is
     *    created
     * 2) While classpath may contain a lot of classes , only create classItems for the classes
     *    required by source classes directly or indirectly (e.g. superclass of superclass)
     */
    @Test
    fun `130 - check classes from classpath`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    import java.util.Date;

                    class Test extends Date{}
                """
            ),
        ) { codebase ->
            val classItem = codebase.assertClass("test.pkg.Test")
            val utilClassItem = codebase.assertClass("java.util.Date")
            val objectClassItem = codebase.assertClass("java.lang.Object")
            assertEquals(utilClassItem, classItem.superClass())
            assertEquals(objectClassItem, utilClassItem.superClass())
            assertEquals(3, utilClassItem.allInterfaces().count())
        }
    }

    @Test
    fun `130 - test missing symbols`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    interface Interface{}
                    class Test extends UnresolvedSuper implements Interface, UnresolvedInterface {}
                """
            ),
        ) { codebase ->
            val classItem = codebase.assertClass("test.pkg.Test")
            assertEquals(null, classItem.superClass())
            assertEquals(1, classItem.allInterfaces().count())
        }
    }
}
