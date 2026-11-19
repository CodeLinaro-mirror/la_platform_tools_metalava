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
import com.android.tools.metalava.model.ExternalReferencableItem
import com.android.tools.metalava.model.InvalidReferencableItem
import com.android.tools.metalava.model.ReferencableMethodSet
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.scope.NameClassification
import com.android.tools.metalava.model.scope.ReferencableNameScope
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
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
        val fieldClass = (testField.type() as ClassTypeItem).resolveClass(codebase)
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

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test import and package collision - explicit java-lang-String import - import should win`() {
        checkImportAndPackageClassCollision(
            simpleName = "String",
            import = "import java.lang.String;",
            expectedUnderlyingClass = "java.lang.String",
        )
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test import and package collision - explicit java-lang wildcard import - package should win`() {
        checkImportAndPackageClassCollision(
            simpleName = "String",
            import = "import java.lang.*;",
            expectedUnderlyingClass = "test.pkg.String",
        )
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test import and package collision - implicit java-lang wildcard import - package should win`() {
        checkImportAndPackageClassCollision(
            simpleName = "String",
            import = "",
            expectedUnderlyingClass = "test.pkg.String",
        )
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test import and package collision - explicit java-util-List import - import should win`() {
        checkImportAndPackageClassCollision(
            simpleName = "List",
            import = "import java.util.List;",
            expectedUnderlyingClass = "java.util.List",
        )
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test import and package collision - explicit java-util wildcard import - package should win`() {
        checkImportAndPackageClassCollision(
            simpleName = "List",
            import = "import java.util.*;",
            expectedUnderlyingClass = "test.pkg.List",
        )
    }

    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
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

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test externalPackagePrefixes`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Test {
                        }
                    """
                ),
                java(
                    """
                        package androidx.annotation;
                        public @interface Nullable {
                        }
                    """
                ),
            ),
            testFixture = TestFixture(externalPackagePrefixes = setOf("androidx", "ext.pkg")),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val nullableClass = codebase.assertClass("androidx.annotation.Nullable")

            // A reference to an external package not in the codebase at all resolves to
            // ExternalReferencableItem.
            assertSame(
                ExternalReferencableItem,
                testClass.resolveReferencableItem("ext.pkg.Foo", NameClassification.CLASS),
            )
            assertSame(
                ExternalReferencableItem,
                testClass.resolveReferencableItem(
                    "ext.pkg.Foo.bar",
                    NameClassification.CALLABLE_SET
                ),
            )
            assertSame(
                ExternalReferencableItem,
                testClass.resolveReferencableItem("ext.pkg", NameClassification.AMBIGUOUS),
            )

            // Exact prefix match when packages are not allowed is an error.
            assertEquals(
                InvalidReferencableItem(
                    "Could not resolve a class called 'ext.pkg' as could not find a package or class called 'ext' in 'class test.pkg.Test'"
                ),
                testClass.resolveReferencableItem("ext.pkg", NameClassification.CLASS),
            )

            // A reference to an external subpackage/class when part of the prefix ("androidx" and
            // "androidx.annotation") exists in the codebase resolves to ExternalReferencableItem.
            assertSame(
                ExternalReferencableItem,
                testClass.resolveReferencableItem(
                    "androidx.core.app.NotificationCompat",
                    NameClassification.CLASS,
                ),
            )
            assertSame(
                ExternalReferencableItem,
                testClass.resolveReferencableItem(
                    "androidx.annotation.NonExistent",
                    NameClassification.CLASS,
                ),
            )

            // A class that exists in the codebase under an external package prefix still resolves
            // to the ClassItem.
            assertSame(
                nullableClass,
                testClass.resolveReferencableItem(
                    "androidx.annotation.Nullable",
                    NameClassification.CLASS,
                ),
            )

            // A non-existent member on a class that exists in the codebase under an external
            // package prefix still returns an InvalidReferencableItem.
            assertEquals(
                InvalidReferencableItem(
                    "Could not resolve a class called 'androidx.annotation.Nullable.NonExistent' as could not find a class called 'NonExistent' in 'class androidx.annotation.Nullable'"
                ),
                testClass.resolveReferencableItem(
                    "androidx.annotation.Nullable.NonExistent",
                    NameClassification.CLASS,
                ),
            )

            // A package name that only shares a character prefix (not a dot-separated segment) does
            // not match.
            assertEquals(
                InvalidReferencableItem(
                    "Could not resolve a class called 'androidx_extra.Foo' as could not find a package or class called 'androidx_extra' in 'class test.pkg.Test'"
                ),
                testClass.resolveReferencableItem("androidx_extra.Foo", NameClassification.CLASS),
            )
        }
    }

    /**
     * Verify that subclasses inherit fields and methods from their supertypes, while a supertype's
     * own simple name, type parameters, and constructors are not inherited.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test non-inheritance of base class simple name, type parameters, and constructors`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package other.pkg;
                        public class Base<B> {
                            public int baseField;
                            public Base() {}
                            public void baseMethod() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Derived extends other.pkg.Base<String> {
                        }
                    """
                ),
            ),
        ) {
            val baseClass = codebase.assertClass("other.pkg.Base")
            val derivedClass = codebase.assertClass("test.pkg.Derived")

            // Inherited fields and methods are in scope on Derived.
            assertSame(
                baseClass.assertField("baseField"),
                derivedClass.resolveReferencableItem("baseField", NameClassification.AMBIGUOUS),
            )
            assertEquals(
                ReferencableMethodSet(baseClass, "baseMethod"),
                derivedClass.resolveReferencableItem("baseMethod", NameClassification.AMBIGUOUS),
            )

            // Supertypes' own simple names, type parameters, and constructors are not inherited.
            assertEquals(
                InvalidReferencableItem("Could not resolve 'Base' in 'class test.pkg.Derived'"),
                derivedClass.resolveReferencableItem("Base", NameClassification.AMBIGUOUS),
            )
            assertEquals(
                InvalidReferencableItem(
                    "Could not resolve a method/constructor called 'Base' in 'class test.pkg.Derived'"
                ),
                derivedClass.resolveReferencableItem("Base", NameClassification.CALLABLE_SET),
            )
            assertEquals(
                InvalidReferencableItem("Could not resolve 'B' in 'class test.pkg.Derived'"),
                derivedClass.resolveReferencableItem("B", NameClassification.AMBIGUOUS),
            )
        }
    }

    /**
     * Verify how `java.lang.Object` methods resolve on interfaces and annotation types when no
     * superinterface declares or overloads them, without shadowing superinterface declarations or
     * exposing non-public `Object` methods.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test interface scope resolves java-lang-Object methods`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public interface MyInterface {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public interface EmptyInterface {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public interface RedeclaringInterface {
                            String toString();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public interface SubInterface extends EmptyInterface, RedeclaringInterface {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public @interface MyAnnotation {
                        }
                    """
                ),
            ),
        ) {
            val myInterface = codebase.assertClass("test.pkg.MyInterface")
            val redeclaringInterface = codebase.assertClass("test.pkg.RedeclaringInterface")
            val subInterface = codebase.assertClass("test.pkg.SubInterface")
            val myAnnotation = codebase.assertClass("test.pkg.MyAnnotation")
            val annotationClass = codebase.assertResolvedClass("java.lang.annotation.Annotation")

            // TODO(b/447588621): Interfaces implicitly declare public instance methods of
            //  java.lang.Object even though ClassItem.superClass() is null.
            assertEquals(
                InvalidReferencableItem(
                    "Could not resolve 'toString' in 'class test.pkg.MyInterface'"
                ),
                myInterface.resolveReferencableItem("toString", NameClassification.AMBIGUOUS),
            )
            assertEquals(
                InvalidReferencableItem(
                    "Could not resolve a method/constructor called 'MyInterface.toString' as could not find a method/constructor called 'toString' in 'class test.pkg.MyInterface'"
                ),
                myInterface.resolveReferencableItem(
                    "MyInterface.toString",
                    NameClassification.CALLABLE_SET,
                ),
            )

            // Protected java.lang.Object methods (JLS 9.2) are not members of an interface.
            assertEquals(
                InvalidReferencableItem(
                    "Could not resolve 'clone' in 'class test.pkg.MyInterface'"
                ),
                myInterface.resolveReferencableItem("clone", NameClassification.AMBIGUOUS),
            )
            assertEquals(
                InvalidReferencableItem(
                    "Could not resolve 'finalize' in 'class test.pkg.MyInterface'"
                ),
                myInterface.resolveReferencableItem("finalize", NameClassification.AMBIGUOUS),
            )

            // Superinterface declarations take precedence over java.lang.Object even when preceded
            // by an unrelated superinterface that does not declare the method.
            assertEquals(
                ReferencableMethodSet(redeclaringInterface, "toString"),
                subInterface.resolveReferencableItem("toString", NameClassification.AMBIGUOUS),
            )

            // Annotation types inherit toString() from java.lang.annotation.Annotation, while
            // non-redeclared public instance methods like getClass() fall back to java.lang.Object
            // and protected Object methods like clone() remain unresolved.
            assertEquals(
                ReferencableMethodSet(annotationClass, "toString"),
                myAnnotation.resolveReferencableItem("toString", NameClassification.AMBIGUOUS),
            )

            // TODO(b/447588621): Public instance methods of java.lang.Object not redeclared on
            //  java.lang.annotation.Annotation should fall back to java.lang.Object.
            assertEquals(
                InvalidReferencableItem(
                    "Could not resolve 'getClass' in 'class test.pkg.MyAnnotation'"
                ),
                myAnnotation.resolveReferencableItem("getClass", NameClassification.AMBIGUOUS),
            )
            assertEquals(
                InvalidReferencableItem(
                    "Could not resolve 'clone' in 'class test.pkg.MyAnnotation'"
                ),
                myAnnotation.resolveReferencableItem("clone", NameClassification.AMBIGUOUS),
            )
        }
    }

    /**
     * Verify that Kotlin typealiases and synthetic top-level declaration file-facade classes do not
     * fall back to `java.lang.Object` methods even though their [ClassItem.superClass] is `null`.
     */
    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Test typealias and file-facade scopes do not resolve Object methods`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                    package test.pkg
                    typealias MyTypeAlias = String
                    fun topLevelFunction() {}
                """,
            )
        runMultiplatformCodebaseTest(
            inputSet(commonSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
        ) {
            val commonCodebase = multiplatformCodebase.sourceSetToCodebase["commonMain"]!!
            val myTypeAlias = commonCodebase.assertTypeAlias("test.pkg.MyTypeAlias")
            assertNull(myTypeAlias.superClass())
            assertEquals(
                InvalidReferencableItem(
                    "Could not resolve 'toString' in 'typealias test.pkg.MyTypeAlias'"
                ),
                myTypeAlias.resolveReferencableItem("toString", NameClassification.AMBIGUOUS),
            )

            val facadeName = "test.pkg.${ClassItem.TOP_LEVEL_DECLARATION_FACADE_NAME}"
            val fileFacade = commonCodebase.assertClass(facadeName)
            assertTrue(fileFacade.isFileFacade)
            assertNull(fileFacade.superClass())
            assertEquals(
                InvalidReferencableItem("Could not resolve 'toString' in 'class $facadeName'"),
                fileFacade.resolveReferencableItem("toString", NameClassification.AMBIGUOUS),
            )
        }
    }
}
