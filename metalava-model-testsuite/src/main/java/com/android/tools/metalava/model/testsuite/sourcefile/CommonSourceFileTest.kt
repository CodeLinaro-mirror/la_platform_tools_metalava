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

package com.android.tools.metalava.model.testsuite.sourcefile

import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Import
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for implementations of [SourceFile]. */
class CommonSourceFileTest : BaseModelTest() {
    internal class FilterHidden : FilterPredicate {
        override fun test(item: SelectableItem): Boolean = !item.isHiddenOrRemoved()
    }

    @Test
    fun `test sourcefile imports`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        import test.pkg2.Test1.FIELD;
                        import test.pkg2.Test1.method;
                        import test.pkg1.*;
                        import test.Test.Inner;
                        import test.Test;
                        import empty.*;
                        import test.pkg1.Test2;
                        import java.util.*;

                        /** {@link method} {@link Inner} {@link Test}*/
                        public class Test {
                            /** {@link FIELD} */
                            public static int FIELD;
                        }

                        class Outer {
                            class Inner {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg2;

                        class Test1 {
                            public static final int FIELD = 7;

                            public static void method1(int a) {}
                            public static int method() { return 7;}
                        }
                     """
                ),
                java(
                    """
                        package test.pkg1;

                        public class Test1 {}

                        public class Test2 {}
                    """
                ),
                java(
                    """
                        package test;

                        /** @hide */
                        public class Test {
                            class Inner {}
                        }
                    """
                ),
                java("""
                        package empty;
                    """),
            )
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val sourceFile = classItem.sourceFile()!!

            // Create the Import objects that are expected.
            val classItem1 = codebase.assertClass("test.Test")
            val classImport = Import(classItem1)

            val innerClassItem = codebase.assertClass("test.Test.Inner")
            val innerClassImport = Import(innerClassItem)

            val pkgItem = codebase.assertPackage("test.pkg1")
            val packageImport = Import(pkgItem)

            // Only class imports that are referenced in documentation are included.
            // The wildcard imports are always included (except for empty packages and packages from
            // classpath).
            // Method and Field imports don't seem to resolve and are not included.
            val allImports = sourceFile.getImports()
            assertEquals(
                setOf(classImport, innerClassImport, packageImport),
                allImports,
                message = "unfiltered imports"
            )

            val notHiddenImports = sourceFile.getImports(FilterHidden())
            assertEquals(setOf(packageImport), notHiddenImports, message = "filtered hidden")
        }
    }

    @Test
    fun `test sourcefile imports from classpath`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        import java.util.List;
                        import java.util.Set;

                        /** {@link List} {@link Set}*/
                        public class Foo {
                            public static List<String> LIST_FIELD;
                            public static Set<String> SET_FIELD;
                        }
                    """
                ),
            )
        ) {
            val classItem = codebase.assertClass("test.pkg.Foo")
            val sourceFile = classItem.sourceFile()!!

            // Get the imports before resolving java.util.Set to see how the getImports(...) methods
            // behave with unresolved classes.
            val allImports = sourceFile.getImports()

            // Create the Import objects that are expected.
            val listClassItem = codebase.assertResolvedClass("java.util.List")
            val listClassImport = Import(listClassItem)

            val setClassItem = codebase.assertResolvedClass("java.util.Set")
            val setClassImport = Import(setClassItem)

            // Makes sure that classes from the classpath are included in the imports.
            assertEquals(
                setOf(listClassImport, setClassImport),
                allImports,
                message = "unfiltered imports"
            )
        }
    }

    @Test
    fun `test sourcefile classes`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {}

                    public class Outer {
                        class Inner {}
                    }
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val outerClassItem = codebase.assertClass("test.pkg.Outer")
            val sourceFile = classItem.sourceFile()!!

            assertEquals(listOf(classItem, outerClassItem), sourceFile.classes().toList())
        }
    }
}
