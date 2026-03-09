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
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testing.testTypeString
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.KnownSourceFiles.notTypeUseNonNullSource
import com.android.tools.metalava.testing.KnownSourceFiles.notTypeUseNullableSource
import com.android.tools.metalava.testing.KnownSourceFiles.typeUseOnlyNonNullSource
import com.android.tools.metalava.testing.KnownSourceFiles.typeUseOnlyNullableSource
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

/** Common tests for implementations of [ClassItem]. */
class CommonClassItemTest : BaseModelTest() {

    @Test
    fun `empty class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        public Test() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            assertEquals("Test", testClass.fullName())
            assertEquals("test/pkg/Test", testClass.internalName())
            assertEquals("test.pkg.Test", testClass.qualifiedName())
            assertEquals(1, testClass.constructors().size)
            assertEquals(emptyList(), testClass.methods())
            assertEquals(emptyList(), testClass.fields())
            assertEquals(emptyList(), testClass.properties())
        }
    }

    @Test
    fun `Find method with type parameterized by two types`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public void foo(java.util.Map<String, Integer>);
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Foo {
                        public void foo(java.util.Map<String, Integer> map) {}
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.methods().single()

            // This should not find the method as `findMethod` splits parameters by `,` so it looks
            // for one parameter of type `java.util.Map<String` and one of type `Integer>`.
            val foundMethod = fooClass.findBytecodeMethod("foo", "java.util.Map<String, Integer>")
            assertNull(
                foundMethod,
                message = "unexpectedly found method with multiple type parameters"
            )

            // This should find the method.
            assertSame(fooMethod, fooClass.findBytecodeMethod("foo", "java.util.Map"))
        }
    }

    @Test
    fun `Find method by multiple erased types`() {
        runCodebaseTest(
            java(
                """
                package test.pkg;
                import java.util.List;
                import java.util.Map;
                public class Foo {
                    public void multipleSimpleParams(int i, float f, String s) {}
                    public <T1, T2 extends Integer> void multipleVariableParams(T1 t1, T2 t2, List<T1> t1s, Map<T2, T1> map) {}
                }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            assertNotNull(
                fooClass.findBytecodeMethod("multipleSimpleParams", "int,float,java.lang.String")
            )
            assertNotNull(
                fooClass.findBytecodeMethod(
                    "multipleVariableParams",
                    "java.lang.Object,java.lang.Integer,java.util.List,java.util.Map"
                )
            )
        }
    }

    @Test
    fun `Test access type parameter of outer class in type parameters`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Outer<O> {
                      }
                      public class Outer.Middle {
                      }
                      public class Outer.Middle.Inner<T extends O> {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Outer<O> {
                        private Outer() {}
                        public class Middle {
                            private Middle() {}
                            public class Inner<T extends O> {
                                private Inner() {}
                            }
                        }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Outer<O> private constructor() {
                        inner class Middle private constructor() {
                            inner class Inner<T: O> private constructor()
                        }
                    }
                """
            ),
        ) {
            val oTypeParameter = codebase.assertClass("test.pkg.Outer").typeParameterList.single()
            val extendsType =
                codebase
                    .assertClass("test.pkg.Outer.Middle.Inner")
                    .typeParameterList
                    .first()
                    .typeBounds()
                    .first()

            extendsType.assertReferencesTypeParameter(oTypeParameter)
        }
    }

    @Test
    fun `Test access type parameter of outer class in extends type`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Outer<O> {
                      }
                      public class Outer.Middle {
                      }
                      public abstract class Outer.Middle.Inner extends test.pkg.Outer.GenericClass<O> {
                      }
                      public abstract static class Outer.GenericClass<T> {
                        method public abstract T method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Outer<O> {
                        private Outer() {}
                        public static abstract class GenericClass<T> {
                            private GenericClass() {}
                            public abstract T method();
                        }
                        public class Middle {
                            private Middle() {}
                            public abstract class Inner extends GenericClass<O> {
                                private Inner() {}
                            }
                        }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Outer<O> private constructor() {
                        abstract class GenericClass<T> private constructor() {
                            abstract fun method(): T
                        }
                        inner class Middle private constructor() {
                            abstract inner class Inner(o: O): GenericClass<O>()
                        }
                    }
                """
            ),
        ) {
            val oTypeParameter = codebase.assertClass("test.pkg.Outer").typeParameterList.single()
            val extendsType = codebase.assertClass("test.pkg.Outer.Middle.Inner").superClassType()!!
            val typeArgument = extendsType.arguments.single()

            typeArgument.assertReferencesTypeParameter(oTypeParameter)
        }
    }

    @Test
    fun `Test access type parameter of outer class in interface type`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Outer<O> {
                      }
                      public class Outer.Middle {
                      }
                      public abstract class Outer.Middle.Inner implements test.pkg.Outer.GenericInterface<O> {
                      }
                      public interface Outer.GenericInterface<T> {
                        method public abstract T method();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Outer<O> {
                        private Outer() {}
                        public interface GenericInterface<T> {
                            T method();
                        }
                        public class Middle {
                            private Middle() {}
                            public abstract class Inner implements GenericInterface<O> {
                                private Inner() {}
                            }
                        }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Outer<O> private constructor() {
                        interface GenericInterface<T> {
                            fun method(): T
                        }
                        inner class Middle private constructor() {
                            abstract inner class Inner(o: O): GenericInterface<O>
                        }
                    }
                """
            ),
        ) {
            val oTypeParameter = codebase.assertClass("test.pkg.Outer").typeParameterList.single()
            val implementsType =
                codebase.assertClass("test.pkg.Outer.Middle.Inner").interfaceTypes().single()
            val typeArgument = implementsType.arguments.single()

            typeArgument.assertReferencesTypeParameter(oTypeParameter)
        }
    }

    @Test
    fun `Test interface no extends list`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface Foo {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public interface Foo {}
                """
            ),
            kotlin(
                """
                    package test.pkg

                    interface Foo
                """
            ),
        ) {
            val fooInterface = codebase.assertClass("test.pkg.Foo")

            assertNull(fooInterface.superClassType())
            assertNull(fooInterface.superClass())

            val interfaceList = fooInterface.interfaceTypes().map { it.resolveClass(codebase) }
            assertEquals(emptyList(), interfaceList)

            val allInterfaces = fooInterface.allInterfaces().toList()
            assertEquals(listOf(fooInterface), allInterfaces)
        }
    }

    @Test
    fun `Test interface extends list`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface A {
                      }
                      public interface B {
                      }
                      public interface C {
                      }
                      public interface Foo extends test.pkg.A, test.pkg.B, test.pkg.C {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public interface A {}
                    public interface B {}
                    public interface C {}
                    public interface Foo extends A, B, C {}
                """
            ),
            kotlin(
                """
                    package test.pkg

                    interface A
                    interface B
                    interface C
                    interface Foo: A, B, C
                """
            ),
        ) {
            val interfaceA = codebase.assertClass("test.pkg.A")
            val interfaceB = codebase.assertClass("test.pkg.B")
            val interfaceC = codebase.assertClass("test.pkg.C")
            val fooInterface = codebase.assertClass("test.pkg.Foo")

            assertNull(fooInterface.superClassType()?.resolveClass(codebase))
            assertNull(fooInterface.superClass())

            val interfaceList = fooInterface.interfaceTypes().map { it.resolveClass(codebase) }
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), interfaceList)

            val allInterfaces = fooInterface.allInterfaces().toList()
            assertEquals(listOf(fooInterface, interfaceA, interfaceB, interfaceC), allInterfaces)
        }
    }

    @Test
    fun `Test class no super class or implements lists`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Foo {}
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Foo
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            // Get the super class to force it to be loaded.
            val fooSuperClass = fooClass.superClass()

            // Now get the object class.
            val objectClass = codebase.assertClass("java.lang.Object", expectedEmit = false)

            assertSame(objectClass, fooSuperClass)

            val interfaceList = fooClass.interfaceTypes().map { it.resolveClass(codebase) }
            assertEquals(emptyList(), interfaceList)

            val allInterfaces = fooClass.allInterfaces().toList()
            assertEquals(emptyList(), allInterfaces)
        }
    }

    @Test
    fun `Test class super class no implements lists`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                      }
                      public class Foo extends test.pkg.Bar {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {}
                    public class Foo extends Bar {}
                """
            ),
            kotlin(
                """
                    package test.pkg

                    open class Bar
                    class Foo: Bar()
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            assertSame(barClass, fooClass.superClassType()?.resolveClass(codebase))
            assertSame(barClass, fooClass.superClass())

            val interfaceList = fooClass.interfaceTypes().map { it.resolveClass(codebase) }
            assertEquals(emptyList(), interfaceList)

            val allInterfaces = fooClass.allInterfaces().toList()
            assertEquals(emptyList(), allInterfaces)
        }
    }

    @Test
    fun `Test class no super class but implements lists`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface A {
                      }
                      public interface B {
                      }
                      public interface C {
                      }
                      public class Foo implements test.pkg.A, test.pkg.B, test.pkg.C {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public interface A {}
                    public interface B {}
                    public interface C {}
                    public class Foo implements A, B, C {}
                """
            ),
            kotlin(
                """
                    package test.pkg

                    interface A
                    interface B
                    interface C
                    class Foo: A, B, C
                """
            ),
        ) {
            val interfaceA = codebase.assertClass("test.pkg.A")
            val interfaceB = codebase.assertClass("test.pkg.B")
            val interfaceC = codebase.assertClass("test.pkg.C")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            // Get the super class to force it to be loaded.
            val fooSuperClass = fooClass.superClass()

            // Now get the object class.
            val objectClass = codebase.assertClass("java.lang.Object", expectedEmit = false)

            assertSame(objectClass, fooSuperClass)

            val interfaceList = fooClass.interfaceTypes().map { it.resolveClass(codebase) }
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), interfaceList)

            val allInterfaces = fooClass.allInterfaces().toList()
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), allInterfaces)
        }
    }

    @Test
    fun `Test class super class and implements lists`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                      }
                      public interface A {
                      }
                      public interface B {
                      }
                      public interface C {
                      }
                      public class Foo extends test.pkg.Bar implements test.pkg.A, test.pkg.B, test.pkg.C {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {}
                    public interface A {}
                    public interface B {}
                    public interface C {}
                    public class Foo extends Bar implements A, B, C {}
                """
            ),
            kotlin(
                """
                    package test.pkg

                    open class Bar
                    interface A
                    interface B
                    interface C
                    class Foo: Bar(), A, B, C
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            val interfaceA = codebase.assertClass("test.pkg.A")
            val interfaceB = codebase.assertClass("test.pkg.B")
            val interfaceC = codebase.assertClass("test.pkg.C")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            assertSame(barClass, fooClass.superClassType()?.resolveClass(codebase))
            assertSame(barClass, fooClass.superClass())

            val interfaceList = fooClass.interfaceTypes().map { it.resolveClass(codebase) }
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), interfaceList)

            val allInterfaces = fooClass.allInterfaces().toList()
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), allInterfaces)
        }
    }

    @Test
    fun `Test class super class generic type`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 4.0
                        package test.pkg {
                          public class Generic<T, U> {
                          }
                          public class Foo extends test.pkg.Generic<String?, Integer> {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                typeUseOnlyNonNullSource,
                typeUseOnlyNullableSource,
                java(
                    """
                        package test.pkg;
                        import type.use.only.*;
                        public class Generic<T, U> {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import type.use.only.*;
                        public class Foo extends Generic<@Nullable String, @NonNull Integer> {
                        }
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        open class Generic<T, U>
                    """
                ),
                kotlin(
                    """
                        package test.pkg

                        class Foo: Generic<String?, Integer>()
                    """
                ),
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val superClassType = fooClass.superClassType()!!
            assertEquals(
                "test.pkg.Generic<java.lang.String?,java.lang.Integer>",
                superClassType.testTypeString(kotlinStyleNulls = true)
            )
        }
    }

    @Test
    fun `Test class super interface generic type`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 4.0
                        package test.pkg {
                          public interface Generic<T, U> {
                          }
                          public class Foo implements test.pkg.Generic<String?, Integer> {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                typeUseOnlyNonNullSource,
                typeUseOnlyNullableSource,
                java(
                    """
                        package test.pkg;
                        import type.use.only.*;
                        public interface Generic<T, U> {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import type.use.only.*;
                        public class Foo implements Generic<@Nullable String, @NonNull Integer> {
                        }
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        interface Generic<T, U>
                    """
                ),
                kotlin(
                    """
                        package test.pkg

                        class Foo: Generic<String?, Integer>
                    """
                ),
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val superClassType = fooClass.interfaceTypes().single()
            assertEquals(
                "test.pkg.Generic<java.lang.String?,java.lang.Integer>",
                superClassType.testTypeString(kotlinStyleNulls = true)
            )
        }
    }

    @Test
    fun `Test class Object has no super class type`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package java.lang {
                      public class Object {
                      }
                    }
                """
            ),
            java(
                """
                    package java.lang;

                    public class Object {}
                """
            ),
        ) {
            val objectClass = codebase.assertClass("java.lang.Object")

            // Must have no super class type, otherwise it could lead to stack overflows when
            // recursing up the hierarchy.
            assertNull(objectClass.superClassType())
        }
    }

    @Test
    fun `Test deprecated class by javadoc tag`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * @noinspection DeprecatedIsStillUsed
                     * @deprecated
                     */
                    public class Bar {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            barClass.assertExplicitlyDeprecated()
        }
    }

    @Test
    fun `Test class is not treated as deprecated by @deprecatedSince`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * @deprecatedSince this should not be treated as deprecated.
                     * @noinspection JavadocDeclaration, DeprecatedIsStillUsed
                     */
                    public class Bar {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            barClass.assertNotDeprecated()
        }
    }

    @Test
    fun `Test class is treated as deprecated if @deprecated comes after @deprecatedSince`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * @deprecatedSince this should not be treated as deprecated.
                     * @noinspection JavadocDeclaration, DeprecatedIsStillUsed
                     * @deprecated Really deprecated
                     */
                    public class Bar {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            barClass.assertExplicitlyDeprecated()
        }
    }

    @Test
    fun `Test deprecated class by annotation`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @Deprecated public class Bar {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    /** @noinspection DeprecatedIsStillUsed */
                    @Deprecated
                    public class Bar {}
                """
            ),
            kotlin(
                """
                    package test.pkg

                    @Deprecated
                    class Bar {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            barClass.assertExplicitlyDeprecated()
        }
    }

    @Test
    fun `Test not deprecated class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {}
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Bar {}
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            barClass.assertNotDeprecated()
        }
    }

    @Test
    fun `Test duplicate without type substitutions`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        class HiddenClass {
                            public void foo() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class PublicClass extends HiddenClass {}
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        internal open class HiddenClass {
                            fun foo() {}
                        }
                    """
                ),
                kotlin(
                    """
                        package test.pkg
                        class PublicClass: HiddenClass()
                    """
                ),
            ),
        ) {
            val hiddenClass = codebase.assertResolvedClass("test.pkg.HiddenClass")
            val hiddenClassMethod = hiddenClass.methods().single()
            val publicClass = codebase.assertClass("test.pkg.PublicClass")

            val inheritedMethod = hiddenClassMethod.duplicate(publicClass)
            assertSame(hiddenClass, inheritedMethod.inheritedFrom)
            assertTrue(inheritedMethod.inheritedFromAncestor)

            assertEquals("fun foo(): void", inheritedMethod.kotlinLikeDescription())
        }
    }

    @Test
    fun `Test duplicate with type substitutions`() {
        runSourceCodebaseTest(
            inputSet(
                typeUseOnlyNonNullSource,
                typeUseOnlyNullableSource,
                java(
                    """
                        package test.pkg;
                        import java.util.List;
                        import type.use.only.*;
                        class HiddenClass<T extends @Nullable Object> {
                            public void t(T t) {}
                            public void optionalT(@Nullable T optionalT) {}
                            public void requiredT(@NonNull T requiredT) {}

                            public void listOfT(@NonNull List<? extends T> listOfT) {}
                            public void listOfOptionalT(@NonNull List<? extends @Nullable T>listOfOptionalT) {}
                            public void listOfRequiredT(@NonNull List<? extends @NonNull T>listOfRequiredT) {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import type.use.only.*;
                        public class NonNullStringClass extends HiddenClass<@NonNull String> {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import type.use.only.*;
                        public class NullableIntegerClass extends HiddenClass<@Nullable Integer> {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class UndefinedTypeVariableClass<U> extends HiddenClass<U> {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class PlatformNumberClass extends HiddenClass<Number> {}
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        internal class HiddenClass<T> {
                            fun t(t: T) {}
                            fun optionalT(optionalT: T?) {}
                            fun requiredT(requiredT: T & Any) {}

                            fun listOfT(listOfT: List<T>) {}
                            fun listOfOptionalT(listOfOptionalT: List<T?>) {}
                            fun listOfRequiredT(listOfRequiredT: List<T & Any>) {}
                        }
                    """
                ),
                kotlin(
                    """
                        package test.pkg
                        class NonNullStringClass: HiddenClass<String>()
                    """
                ),
                kotlin(
                    """
                        package test.pkg
                        class NullableIntegerClass: HiddenClass<Integer?>()
                    """
                ),
                kotlin(
                    """
                        package test.pkg
                        class UndefinedTypeVariableClass<U>: HiddenClass<U>()
                    """
                ),
                // Need to use Java here as there is no way in Kotlin to specify a type with
                // platform nullability.
                java(
                    """
                        package test.pkg;
                        public class PlatformNumberClass extends HiddenClass<Number> {}
                    """
                ),
            ),
        ) {
            val hiddenClass = codebase.assertResolvedClass("test.pkg.HiddenClass")

            /**
             * Append the result of duplicating `hiddenClass` into [destinationClassName] to this
             * [StringBuilder].
             */
            fun StringBuilder.appendResultOfDuplicatingInto(destinationClassName: String) {
                val destinationClass = codebase.assertClass(destinationClassName)
                append("Duplicating into $destinationClass\n")
                for (method in hiddenClass.methods().sortedBy { it.name() }.reversed()) {
                    val name = method.name()
                    val inheritedMethod = method.duplicate(destinationClass)
                    assertSame(hiddenClass, inheritedMethod.inheritedFrom)
                    assertTrue(inheritedMethod.inheritedFromAncestor)

                    val parameterType = inheritedMethod.parameters().single().type()
                    append("  ")
                    append(name)
                    append(" -> ")
                    append(parameterType.testTypeString(kotlinStyleNulls = true))
                    append("\n")
                }
                append("\n")
            }

            // Concatenate result of duplicating HiddenClass into different classes with different
            // substitution types.
            val result = buildString {
                appendResultOfDuplicatingInto("test.pkg.NonNullStringClass")
                appendResultOfDuplicatingInto("test.pkg.NullableIntegerClass")
                appendResultOfDuplicatingInto("test.pkg.UndefinedTypeVariableClass")
                appendResultOfDuplicatingInto("test.pkg.PlatformNumberClass")
            }

            // Check the result.
            // TODO(b/483318672): Non-null type variables are not marked as non-null (i.e. by
            //   appending ` & Any` to make it a non-null intersection type).
            assertEquals(
                """
                    Duplicating into class test.pkg.NonNullStringClass
                      t -> java.lang.String
                      requiredT -> java.lang.String
                      optionalT -> java.lang.String?
                      listOfT -> java.util.List<? extends java.lang.String>
                      listOfRequiredT -> java.util.List<? extends java.lang.String>
                      listOfOptionalT -> java.util.List<? extends java.lang.String?>

                    Duplicating into class test.pkg.NullableIntegerClass
                      t -> java.lang.Integer?
                      requiredT -> java.lang.Integer
                      optionalT -> java.lang.Integer?
                      listOfT -> java.util.List<? extends java.lang.Integer?>
                      listOfRequiredT -> java.util.List<? extends java.lang.Integer>
                      listOfOptionalT -> java.util.List<? extends java.lang.Integer?>

                    Duplicating into class test.pkg.UndefinedTypeVariableClass
                      t -> U
                      requiredT -> U
                      optionalT -> U?
                      listOfT -> java.util.List<? extends U>
                      listOfRequiredT -> java.util.List<? extends U>
                      listOfOptionalT -> java.util.List<? extends U?>

                    Duplicating into class test.pkg.PlatformNumberClass
                      t -> java.lang.Number!
                      requiredT -> java.lang.Number
                      optionalT -> java.lang.Number?
                      listOfT -> java.util.List<? extends java.lang.Number!>
                      listOfRequiredT -> java.util.List<? extends java.lang.Number>
                      listOfOptionalT -> java.util.List<? extends java.lang.Number?>
                  """
                    .trimIndent(),
                result.trim()
            )
        }
    }

    @Test
    fun `Test duplicate with type substitutions and not type use nullability annotations`() {
        // Test for behavior of MethodItem.duplicate(ClassItem) in Java when the type parameter is
        // used in the return type and is either unannotated, or annotated with a non-type use
        // nullability annotation.
        runSourceCodebaseTest(
            inputSet(
                notTypeUseNonNullSource,
                notTypeUseNullableSource,
                java(
                    """
                        package test.pkg;
                        import java.util.List;
                        import not.type.use.*;
                        abstract class HiddenClass<T> {
                            public T t();
                            @NonNull public T nonNullT();
                            @Nullable public T nullableT();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public abstract class PublicClass extends HiddenClass<String> {}
                    """
                ),
            ),
        ) {
            val hiddenClass = codebase.assertResolvedClass("test.pkg.HiddenClass")
            val publicClass = codebase.assertClass("test.pkg.PublicClass")

            val expectedTypesAndNullability =
                mapOf(
                    "t" to Pair("java.lang.String!", TypeNullability.PLATFORM),
                    "nonNullT" to Pair("java.lang.String", TypeNullability.NONNULL),
                    "nullableT" to Pair("java.lang.String?", TypeNullability.NULLABLE),
                )

            for (method in hiddenClass.methods().sortedBy { it.name() }) {
                val name = method.name()
                val inheritedMethod = method.duplicate(publicClass)
                assertSame(hiddenClass, inheritedMethod.inheritedFrom)
                assertTrue(inheritedMethod.inheritedFromAncestor)

                val returnType = inheritedMethod.returnType()
                val (expectedType, expectedNullability) = expectedTypesAndNullability[name]!!
                assertWithMessage("testing type of $name")
                    .that(returnType.testTypeString(kotlinStyleNulls = true))
                    .isEqualTo(expectedType)

                assertWithMessage("testing type nullability of $name")
                    .that(returnType.modifiers.nullability)
                    .isEqualTo(expectedNullability)
            }
        }
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test duplicated documentation is fully qualified in originating context`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import java.util.List;
                        class HiddenClass {
                            // The following reference to List should be fully qualified here
                            // because its destination knows nothing about `List`.
                            /** {@link List} */
                            public void method() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class PublicClass extends HiddenClass {}
                    """
                ),
            ),
        ) {
            val hiddenClass = codebase.assertResolvedClass("test.pkg.HiddenClass")
            val publicClass = codebase.assertClass("test.pkg.PublicClass")

            val hiddenMethod = hiddenClass.methods().single()
            val inheritedMethod = hiddenMethod.duplicate(publicClass)

            inheritedMethod.assertPrintedDocumentation(
                expectedOutput =
                    """
                        /** {@link java.util.List List} */
                    """
            )
        }
    }

    @Test
    fun `Test type for inner class with an outer class with type parameter`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Outer<T> {
                        public class Inner {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Outer<T> {
                        inner class Inner {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Outer<T> {
                      }
                      public class Outer.Inner {
                      }
                    }
                """
            )
        ) {
            val innerClass = codebase.assertClass("test.pkg.Outer.Inner")
            val outerClass = codebase.assertClass("test.pkg.Outer")
            val outerClassParameter = outerClass.typeParameterList.single()

            val innerType = innerClass.type()
            assertThat(innerType).isInstanceOf(ClassTypeItem::class.java)
            assertThat(innerType.qualifiedName).isEqualTo("test.pkg.Outer.Inner")

            val outerType = innerType.outerClassType
            assertThat(outerType).isNotNull()
            assertThat(outerType!!.qualifiedName).isEqualTo("test.pkg.Outer")

            val outerClassVariable = outerType.arguments.single()
            outerClassVariable.assertReferencesTypeParameter(outerClassParameter)
            assertThat((outerClassVariable as VariableTypeItem).name).isEqualTo("T")
        }
    }

    @Test
    fun `Test type for a nested but not inner class with an outer class with type parameter`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Outer<T> {
                        public static class Inner {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Outer<T> {
                        class Inner {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Outer<T> {
                      }
                      public static class Outer.Inner {
                      }
                    }
                """
            )
        ) {
            val innerClass = codebase.assertClass("test.pkg.Outer.Inner")
            val outerClass = codebase.assertClass("test.pkg.Outer")

            val innerType = innerClass.type()
            assertThat(innerType).isInstanceOf(ClassTypeItem::class.java)
            assertThat(innerType.qualifiedName).isEqualTo("test.pkg.Outer.Inner")

            val outerType = innerType.outerClassType
            assertThat(outerType).isNotNull()
            assertThat(outerType!!.qualifiedName).isEqualTo("test.pkg.Outer")

            assertThat(outerType.arguments).isEmpty()
        }
    }

    @Test
    fun `Test substituting outer class in type for inner class with an outer class with type parameter`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Outer<T> {
                        public class Inner {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Outer<T> {
                        inner class Inner {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Outer<T> {
                      }
                      public class Outer.Inner {
                      }
                    }
                """
            )
        ) {
            val innerClass = codebase.assertClass("test.pkg.Outer.Inner")

            val innerType = innerClass.type()
            assertThat(innerType.toTypeString()).isEqualTo("test.pkg.Outer<T>.Inner")

            val outerClassType = innerType.outerClassType
            assertNotNull(outerClassType)
            assertThat(outerClassType.toTypeString()).isEqualTo("test.pkg.Outer<T>")

            val noArgsOuterClassType = outerClassType.substitute(arguments = emptyList())
            assertThat(noArgsOuterClassType.toTypeString()).isEqualTo("test.pkg.Outer")

            // Replace the outer type with one without any arguments.
            val innerWithNewOuterClassType =
                innerType.substitute(outerClassType = noArgsOuterClassType)

            // Make sure that the replacement actually changed the outer class type.
            assertThat(innerWithNewOuterClassType.toTypeString()).isEqualTo("test.pkg.Outer.Inner")
            assertThat(innerWithNewOuterClassType.outerClassType)
                .isSameInstanceAs(noArgsOuterClassType)
        }
    }

    @Test
    fun `Check TypeParameterItem is not a ClassItem`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Generic<T> {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class Generic<T> {
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Generic<T>
                """
            )
        ) {
            val genericClass = codebase.assertClass("test.pkg.Generic")
            val typeParameter = genericClass.typeParameterList.single()

            assertThat(genericClass).isInstanceOf(ClassItem::class.java)
            assertThat(genericClass).isNotInstanceOf(TypeParameterItem::class.java)

            assertThat(typeParameter).isInstanceOf(TypeParameterItem::class.java)
            assertThat(typeParameter).isNotInstanceOf(ClassItem::class.java)
        }
    }

    @Test
    fun `Check pathological type parameter conflicting with primitive type`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public abstract class Generic<int> {
                        method public abstract int method();
                      }
                    }
                """
            ),
            // Java does not support using a primitive type name as a type parameter name.
            kotlin(
                """
                    package test.pkg
                    abstract class Generic<Int> {
                        abstract fun method(): Int
                    }
                """
            )
        ) {
            val genericClass = codebase.assertClass("test.pkg.Generic")
            val typeParameter = genericClass.typeParameterList.single()

            val methodReturnType = genericClass.methods().single().returnType()
            methodReturnType.assertReferencesTypeParameter(typeParameter)
        }
    }

    @Test
    fun `Test implicit nullability and annotations of ClassItem type()`() {
        val typeUseAnnotation =
            java(
                """
                    package test.pkg;
                    import java.lang.annotation.ElementType;
                    import java.lang.annotation.Target;

                    @Target(ElementType.TYPE_USE)
                    @interface TypeUse {}
                """
            )
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          @test.pkg.TypeUse public class Foo {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                typeUseAnnotation,
                java(
                    """
                        package test.pkg;

                        @TypeUse
                        public class Foo {}
                    """
                ),
            ),
            inputSet(
                typeUseAnnotation,
                kotlin(
                    """
                        package test.pkg
                        class Foo
                    """
                ),
            ),
        ) {
            val classType = codebase.assertClass("test.pkg.Foo").type()
            val modifiers = classType.modifiers

            // Class types are always non-null without needing an annotation
            assertThat(modifiers.nullability).isEqualTo(TypeNullability.NONNULL)

            // Class types do not have any annotations.
            assertThat(modifiers.annotations).isEmpty()
        }
    }

    private fun CodebaseContext.checkClassOrigin(
        name: String,
        expectedOrigin: ClassOrigin,
    ) {
        // Make sure to resolve any class requested just in case it is on the class path.
        val testClass =
            codebase.assertResolvedClass(
                name,
                expectedEmit = expectedOrigin == ClassOrigin.COMMAND_LINE,
            )
        assertEquals(expectedOrigin, testClass.origin, message = "$name origin")
    }

    @Test
    fun `Test origin`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        private Test() {}
                    }
                """
            ),
        ) {
            checkClassOrigin(
                "test.pkg.Test",
                expectedOrigin = ClassOrigin.COMMAND_LINE,
            )
            checkClassOrigin(
                "java.lang.String",
                expectedOrigin = ClassOrigin.CLASS_PATH,
            )

            // Some models may not return an unknown class but those that do should treat it as
            // coming from the class path.
            codebase.resolveClass("Unknown")?.let { testClass ->
                assertEquals(ClassOrigin.CLASS_PATH, testClass.origin, message = "Unknown")
            }
        }
    }

    @Test
    fun `Test origin source path`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Test {
                            private Test() {}
                        }
                    """
                ),
                sourcePathFiles =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                public class SourcePathClass {}
                            """
                        )
                    ),
            )
        ) {
            checkClassOrigin(
                "test.pkg.SourcePathClass",
                expectedOrigin = ClassOrigin.SOURCE_PATH,
            )
        }
    }

    @Test
    fun `Test class on source path`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Test {
                            private Test() {}
                        }
                    """
                ),
                sourcePathFiles =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                public class SourcePathClass {}
                            """
                        )
                    ),
            )
        ) {
            // Make sure that a class defined on the source class path can be resolved but is not
            // emitted.
            codebase.assertResolvedClass("test.pkg.SourcePathClass")
        }
    }

    @Test
    fun `Test class excluded by package filter`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Test {
                            private Test() {}
                        }
                    """
                ),
                java(
                    """
                        package test.excluded.pkg;

                        public class Excluded {
                            private Excluded() {}
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    apiPackages = PackageFilter.parse("test.pkg"),
                ),
        ) {
            // Make sure that a class defined excluded by a package filter can be resolved but is
            // not emitted.
            codebase.assertResolvedClass("test.excluded.pkg.Excluded")
        }
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test class location (java)`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {
                        private Test() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            assertEquals(
                "MAIN_SRC/src/test/pkg/Test.java:3",
                removeTestSpecificDirectories(testClass.fileLocation.toString())
            )
        }
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test class location (kotlin)`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    class Test private constructor() {
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            assertEquals(
                "MAIN_SRC/src/test/pkg/Test.kt:3",
                removeTestSpecificDirectories(testClass.fileLocation.toString())
            )
        }
    }

    @RequiresCapabilities(Capability.SIGNATURE)
    @Test
    fun `Test class location (signature)`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                      }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            assertEquals(
                "MAIN_SRC/api.txt:3",
                removeTestSpecificDirectories(testClass.fileLocation.toString())
            )
        }
    }

    /**
     * This test is to make sure that older signature files without any exhaustivity modifiers are
     * read as non-exhaustive.
     */
    @Test
    fun `modifiers show nonexhaustive when no exhaustivity modifier is present in signature`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public abstract sealed class SealedClass {
                      }
                      public sealed interface SealedInterface {
                      }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    public sealed class SealedClass {}
                    private class PrivateChildClass : SealedClass()
                    public sealed interface SealedInterface {}
                    private class PrivateInterfaceImplementor : SealedInterface
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")
            assertEquals(false, testClass.modifiers.isExhaustive())

            val testInterface = codebase.assertClass("test.pkg.SealedInterface")
            assertEquals(false, testInterface.modifiers.isExhaustive())
        }
    }

    @Test
    fun `modifiers show exhaustive when class or interface is marked as exhaustive in signature`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public abstract sealed exhaustive class SealedClass {
                      }
                      public sealed exhaustive interface SealedInterface {
                      }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    public sealed class SealedClass {}
                    public sealed interface SealedInterface {}
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")
            assertEquals(true, testClass.modifiers.isExhaustive())

            val testInterface = codebase.assertClass("test.pkg.SealedInterface")
            assertEquals(true, testInterface.modifiers.isExhaustive())
        }
    }

    @Test
    fun `modifiers show nonexhaustive when class or interface is marked as nonexhaustive in signature`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public abstract sealed nonexhaustive class SealedClass {
                      }
                      public sealed nonexhaustive interface SealedInterface {
                      }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    public sealed class SealedClass {}
                    private class PrivateChildClass : SealedClass()

                    public sealed interface SealedInterface
                    private class PrivateInterfaceImplementor : SealedInterface
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.SealedClass")
            assertEquals(false, testClass.modifiers.isExhaustive())
        }
    }

    @Test
    fun `subclasses are populated correctly for sealed classes and interfaces, and indirect subclasses aren't tracked`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public final class MyIndirectInterfaceImplementor extends test.pkg.MyInterfaceImplementorA {
                        ctor public MyIndirectInterfaceImplementor();
                      }
                      public final class MyIndirectSubclass extends test.pkg.MySubclassA {
                        ctor public MyIndirectSubclass();
                      }
                      public abstract sealed exhaustive class MyInterfaceImplementorA implements test.pkg.ParentInterface {
                      }
                      public final class MyInterfaceImplementorB implements test.pkg.ParentInterface {
                        ctor public MyInterfaceImplementorB();
                      }
                      public abstract sealed exhaustive class MySubclassA extends test.pkg.ParentClass {
                      }
                      public final class MySubclassB extends test.pkg.ParentClass {
                        ctor public MySubclassB();
                      }
                      public abstract sealed exhaustive class ParentClass {
                      }
                      public sealed exhaustive interface ParentInterface {
                      }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    sealed class ParentClass
                    sealed class MySubclassA : ParentClass()
                    class MySubclassB : ParentClass()
                    class MyIndirectSubclass : MySubclassA()

                    sealed interface ParentInterface
                    sealed class MyInterfaceImplementorA : ParentInterface
                    class MyInterfaceImplementorB : ParentInterface
                    class MyIndirectInterfaceImplementor : MyInterfaceImplementorA()
                """
            )
        ) {
            val testClass = codebase.assertClass("test.pkg.ParentClass")
            assertEquals(2, testClass.sealedClassDirectSubclasses().size)
            assertTrue(
                "test.pkg.MySubclassA" in
                    testClass.sealedClassDirectSubclasses().map { it.qualifiedName() }
            )
            assertTrue(
                "test.pkg.MySubclassB" in
                    testClass.sealedClassDirectSubclasses().map { it.qualifiedName() }
            )

            val testInterface = codebase.assertClass("test.pkg.ParentInterface")
            assertEquals(2, testInterface.sealedClassDirectSubclasses().size)
            assertTrue(
                "test.pkg.MyInterfaceImplementorA" in
                    testInterface.sealedClassDirectSubclasses().map { it.qualifiedName() }
            )
            assertTrue(
                "test.pkg.MyInterfaceImplementorB" in
                    testInterface.sealedClassDirectSubclasses().map { it.qualifiedName() }
            )

            val subClass = codebase.assertClass("test.pkg.MySubclassA")
            assertEquals(1, subClass.sealedClassDirectSubclasses().size)
            assertTrue(
                "test.pkg.MyIndirectSubclass" in
                    subClass.sealedClassDirectSubclasses().map { it.qualifiedName() }
            )

            val ifaceImplementor = codebase.assertClass("test.pkg.MyInterfaceImplementorA")
            assertEquals(1, ifaceImplementor.sealedClassDirectSubclasses().size)
            assertTrue(
                "test.pkg.MyIndirectInterfaceImplementor" in
                    ifaceImplementor.sealedClassDirectSubclasses().map { it.qualifiedName() }
            )
        }
    }
}
