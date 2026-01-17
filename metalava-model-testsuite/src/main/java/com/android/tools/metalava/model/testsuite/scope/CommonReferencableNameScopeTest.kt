/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite.scope

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.InvalidReferencableItem
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.scope.NameClassification
import com.android.tools.metalava.model.scope.ReferencableNameScope
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Test

/** Common tests for [ReferencableNameScope] implementations that require custom setup. */
class CommonReferencableNameScopeTest : BaseModelTest() {
    /**
     * Check that the resolution performed by [ReferencableNameScope] mechanism matches the
     * resolution done by the underlying models.
     *
     * The rules are complicated so this uses the underlying model's resolution process to verify
     * that it is behaving correctly by adding a field with the [simpleName] in the referencing
     * class and getting the [ClassItem] to which it resolves.
     *
     * @param scopeClass the qualified name of the class within which [simpleName] will be resolved.
     * @param simpleName the simple name to resolve in [scopeClass].
     * @param expectedUnderlyingClass the expected fully qualified name of the underlying model's
     *   resolution of [simpleName] and the class to which the [ReferencableNameScope] mechanism has
     *   resolved [simpleName].
     * @param expectedResolvedClass the expected fully qualified name of the class to which the
     *   [ReferencableNameScope] mechanism has resolved [simpleName]. Should be the same as
     *   [expectedUnderlyingClass].
     */
    @Suppress("SameParameterValue")
    private fun CodebaseContext.checkReferencableNameScopeAgainstUnderlyingModel(
        scopeClass: String,
        simpleName: String,
        expectedUnderlyingClass: String?,
        // By default, the resolved class should match the underlying class.
        expectedResolvedClass: String? = expectedUnderlyingClass,
        expectedErrorMessage: String = "",
    ) {
        val testClass = codebase.assertClass(scopeClass)

        // Verify that the type name resolves to the expected class by checking a field of that
        // type name.
        val testField = testClass.fields().single()
        val fieldClass = (testField.type() as ClassTypeItem).asClass()
        val expectedUnderlyingClass = expectedUnderlyingClass?.let { codebase.resolveClass(it)!! }
        assertSame(expectedUnderlyingClass, fieldClass, message = "field class")

        // Verify that the resolution is correct.
        val resolved = testClass.resolveReferencableItem(simpleName, NameClassification.AMBIGUOUS)
        if (expectedResolvedClass == null) {
            val error = resolved as InvalidReferencableItem
            assertEquals(expectedErrorMessage, error.message)
        } else {
            val expectedResolvedClassItem = codebase.resolveClass(expectedResolvedClass)!!
            assertSame(expectedResolvedClassItem, resolved, message = "resolved class")
        }
    }

    /**
     * Check that when there is a collision between a class in the same package as the referencing
     * class and a class in the imports have the same [simpleName] that it is resolved correctly.
     *
     * The rules are complicated so this uses the underlying model's resolution process to verify
     * that it is behaving correctly by adding a field with the [simpleName] in the referencing
     * class and getting the [ClassItem] to which it resolves.
     *
     * @param simpleName the simple name of the colliding classes.
     * @param import the import statement to use, if any.
     * @param expectedUnderlyingClass the expected fully qualified name of the underlying model's
     *   resolution of [simpleName] and the class to which the [ReferencableNameScope] mechanism has
     *   resolved [simpleName].
     */
    private fun checkImportAndPackageClassCollision(
        simpleName: String,
        import: String,
        expectedUnderlyingClass: String,
    ) {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        $import
                        public class Test {
                            public $simpleName field;
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class $simpleName {
                        }
                    """
                ),
            ),
        ) {
            // Check that [simpleName] within "test.pkg.Test" refers to [expectedUnderlyingClass]
            // when referenced from the field and when resolving using [ReferencableNameScope].
            checkReferencableNameScopeAgainstUnderlyingModel(
                "test.pkg.Test",
                simpleName,
                expectedUnderlyingClass,
            )
        }
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test import and package collision - explicit java-lang-String import - import should win`() {
        checkImportAndPackageClassCollision(
            simpleName = "String",
            import = "import java.lang.String;",
            expectedUnderlyingClass = "java.lang.String",
        )
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test import and package collision - explicit java-lang wildcard import - package should win`() {
        checkImportAndPackageClassCollision(
            simpleName = "String",
            import = "import java.lang.*;",
            expectedUnderlyingClass = "test.pkg.String",
        )
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test import and package collision - implicit java-lang wildcard import - package should win`() {
        checkImportAndPackageClassCollision(
            simpleName = "String",
            import = "",
            expectedUnderlyingClass = "test.pkg.String",
        )
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test import and package collision - explicit java-util-List import - import should win`() {
        checkImportAndPackageClassCollision(
            simpleName = "List",
            import = "import java.util.List;",
            expectedUnderlyingClass = "java.util.List",
        )
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test import and package collision - explicit java-util wildcard import - package should win`() {
        checkImportAndPackageClassCollision(
            simpleName = "List",
            import = "import java.util.*;",
            expectedUnderlyingClass = "test.pkg.List",
        )
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test inheritance of nested classes of the base class`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package other.pkg;
                        public class Base {
                            public static class Foo {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import other.pkg.Base;
                        public class Derived extends Base {
                            // Should resolve to Base.Foo.
                            public Foo field;
                        }
                    """
                ),
            ),
        ) {
            checkReferencableNameScopeAgainstUnderlyingModel(
                scopeClass = "test.pkg.Derived",
                simpleName = "Foo",
                expectedUnderlyingClass = "other.pkg.Base.Foo",
            )
        }
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test inheritance of nested classes of a base interface`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package other.pkg;
                        public interface Base {
                            class Foo {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import other.pkg.Base;
                        public class Derived implements Base {
                            // Should resolve to Base.Foo.
                            public Foo field;
                        }
                    """
                ),
            ),
        ) {
            checkReferencableNameScopeAgainstUnderlyingModel(
                "test.pkg.Derived",
                "Foo",
                expectedUnderlyingClass = "other.pkg.Base.Foo",
            )
        }
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test non-inheritance of top-level class of base class`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package other.pkg;
                        public class Base {
                        }
                    """
                ),
                java(
                    """
                        package other.pkg;
                        public class Foo {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import other.pkg.Base;
                        public class Derived extends Base {
                            // Should not resolve to other.pkg.Foo.
                            public Foo field;
                        }
                    """
                ),
            ),
        ) {
            checkReferencableNameScopeAgainstUnderlyingModel(
                "test.pkg.Derived",
                "Foo",
                expectedUnderlyingClass = null,
                expectedErrorMessage = "Could not resolve 'Foo' in 'class test.pkg.Derived'",
            )
        }
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test non-inheritance of outer classes of base class`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package other.pkg;
                        public class Base {
                            public static class Nested {}
                            public static class Foo {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import other.pkg.Base;
                        public class Derived extends Base.Nested {
                            // Should resolve to Base.Foo.
                            public Foo field;
                        }
                    """
                ),
            ),
        ) {
            checkReferencableNameScopeAgainstUnderlyingModel(
                "test.pkg.Derived",
                "Foo",
                expectedUnderlyingClass = null,
                expectedErrorMessage = "Could not resolve 'Foo' in 'class test.pkg.Derived'",
            )
        }
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test inheritance and class collision - local nested class wins`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package other.pkg;
                        public class Base {
                            public static class Foo {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import other.pkg.Base;
                        public class Derived extends Base {
                            public static class Foo {}

                            // Should resolve to `Derived.Foo` and not `Base.Foo`. That is
                            // because it searches nested classes before super classes.
                            public Foo field;
                        }
                    """
                ),
            ),
        ) {
            checkReferencableNameScopeAgainstUnderlyingModel(
                "test.pkg.Derived",
                "Foo",
                expectedUnderlyingClass = "test.pkg.Derived.Foo",
            )
        }
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test inheritance and class collision - super nested class wins before enclosing class`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package other.pkg;
                        public class Base {
                            public static class BaseNested {
                                public static class Foo {}
                            }
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import other.pkg.Base;
                        public class Derived extends Base {
                            public static class Nested extends BaseNested {
                                // Should resolve to BaseNested.Foo and not Derived.Foo. That is
                                // because it searches super classes before enclosing classes.
                                public Foo field;
                            }

                            public static class Foo {}
                        }
                    """
                ),
            ),
        ) {
            checkReferencableNameScopeAgainstUnderlyingModel(
                "test.pkg.Derived.Nested",
                "Foo",
                expectedUnderlyingClass = "other.pkg.Base.BaseNested.Foo",
            )
        }
    }
}
