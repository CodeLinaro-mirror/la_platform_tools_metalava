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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Common tests for implementations of [ClassItem]. */
@RunWith(Parameterized::class)
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
        ) { codebase ->
            val testClass = codebase.assertClass("test.pkg.Test")
            assertEquals("Test", testClass.fullName())
            assertEquals("test/pkg/Test", testClass.internalName())
            assertEquals("test.pkg.Test", testClass.qualifiedName())
            assertEquals("test.pkg.Test", testClass.qualifiedNameWithDollarInnerClasses())
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
        ) { codebase ->
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.methods().single()

            // This should not find the method as `findMethod` splits parameters by `,` so it looks
            // for one parameter of type `java.util.Map<String` and one of type `Integer>`.
            val foundMethod = fooClass.findMethod("foo", "java.util.Map<String, Integer>")
            assertNull(
                foundMethod,
                message = "unexpectedly found method with multiple type parameters"
            )

            // This should find the method.
            assertSame(fooMethod, fooClass.findMethod("foo", "java.util.Map"))
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
        ) { codebase ->
            val fooInterface = codebase.assertClass("test.pkg.Foo")

            assertNull(fooInterface.superClassType())
            assertNull(fooInterface.superClass())

            val interfaceList = fooInterface.interfaceTypes().map { it.asClass() }
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
        ) { codebase ->
            val interfaceA = codebase.assertClass("test.pkg.A")
            val interfaceB = codebase.assertClass("test.pkg.B")
            val interfaceC = codebase.assertClass("test.pkg.C")
            val fooInterface = codebase.assertClass("test.pkg.Foo")

            assertNull(fooInterface.superClassType()?.asClass())
            assertNull(fooInterface.superClass())

            val interfaceList = fooInterface.interfaceTypes().map { it.asClass() }
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
        ) { codebase ->
            val objectClass = codebase.assertClass("java.lang.Object")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            assertSame(objectClass, fooClass.superClassType()?.asClass())
            assertSame(objectClass, fooClass.superClass())

            val interfaceList = fooClass.interfaceTypes().map { it.asClass() }
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
        ) { codebase ->
            val barClass = codebase.assertClass("test.pkg.Bar")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            assertSame(barClass, fooClass.superClassType()?.asClass())
            assertSame(barClass, fooClass.superClass())

            val interfaceList = fooClass.interfaceTypes().map { it.asClass() }
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
        ) { codebase ->
            val interfaceA = codebase.assertClass("test.pkg.A")
            val interfaceB = codebase.assertClass("test.pkg.B")
            val interfaceC = codebase.assertClass("test.pkg.C")
            val objectClass = codebase.assertClass("java.lang.Object")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            assertSame(objectClass, fooClass.superClassType()?.asClass())
            assertSame(objectClass, fooClass.superClass())

            val interfaceList = fooClass.interfaceTypes().map { it.asClass() }
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
        ) { codebase ->
            val barClass = codebase.assertClass("test.pkg.Bar")
            val interfaceA = codebase.assertClass("test.pkg.A")
            val interfaceB = codebase.assertClass("test.pkg.B")
            val interfaceC = codebase.assertClass("test.pkg.C")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            assertSame(barClass, fooClass.superClassType()?.asClass())
            assertSame(barClass, fooClass.superClass())

            val interfaceList = fooClass.interfaceTypes().map { it.asClass() }
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), interfaceList)

            val allInterfaces = fooClass.allInterfaces().toList()
            assertEquals(listOf(interfaceA, interfaceB, interfaceC), allInterfaces)
        }
    }

    @Test
    fun `Test deprecated class by javadoc tag`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    /**
                     * @deprecated
                     */
                    public class Bar {}
                """
            ),
        ) { codebase ->
            val barClass = codebase.assertClass("test.pkg.Bar")
            assertEquals(true, barClass.deprecated)
            assertEquals(true, barClass.originallyDeprecated)
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
        ) { codebase ->
            val barClass = codebase.assertClass("test.pkg.Bar")
            assertEquals(true, barClass.deprecated)
            assertEquals(true, barClass.originallyDeprecated)
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
        ) { codebase ->
            val barClass = codebase.assertClass("test.pkg.Bar")
            assertEquals(false, barClass.deprecated)
            assertEquals(false, barClass.originallyDeprecated)
        }
    }

    @Test
    fun `Test basic mapTypeVariables`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Parent<M, N> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Child<X, Y> extends Parent<X, Y> {}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Child<X, Y> extends test.pkg.Parent<X,Y> {
                          }
                          public class Parent<M, N> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        open class Parent<M, N>
                        class Child<X, Y> : Parent<X, Y>()
                    """
                        .trimIndent()
                )
            )
        ) { codebase ->
            val parent = codebase.assertClass("test.pkg.Parent")
            val child = codebase.assertClass("test.pkg.Child")
            assertEquals(mapOf("M" to "X", "N" to "Y"), child.mapTypeVariables(parent))

            // Not valid uses of mapTypeVariables
            assertEquals(emptyMap(), parent.mapTypeVariables(child))
            assertEquals(emptyMap(), child.mapTypeVariables(child))
        }
    }

    @Test
    fun `Test mapTypeVariables with multiple layers of super classes`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Class4<I> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Class3<G, H> extends Class4<G> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Class2<D, E, F> extends Class3<D, F> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Class1<A, B, C> extends Class2<B, C, A> {}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Class1<A, B, C> extends test.pkg.Class2<B,C,A> {
                          }
                          public class Class2<D, E, F> extends test.pkg.Class3<D,F> {
                          }
                          public class Class3<G, H> extends test.pkg.Class4<G> {
                          }
                          public class Class4<I> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        open class Class4<I>
                        open class Class3<G, H> : Class4<G>
                        open class Class2<D, E, F> : Class3<D, F>
                        class Class1<A, B, C> : Class2<B, C, A>
                    """
                        .trimIndent()
                )
            )
        ) { codebase ->
            val c4 = codebase.assertClass("test.pkg.Class4")
            val c3 = codebase.assertClass("test.pkg.Class3")
            val c2 = codebase.assertClass("test.pkg.Class2")
            val c1 = codebase.assertClass("test.pkg.Class1")

            assertEquals(mapOf("I" to "G"), c3.mapTypeVariables(c4))

            assertEquals(mapOf("G" to "D", "H" to "F"), c2.mapTypeVariables(c3))
            assertEquals(mapOf("I" to "D"), c2.mapTypeVariables(c4))

            assertEquals(mapOf("D" to "B", "E" to "C", "F" to "A"), c1.mapTypeVariables(c2))
            assertEquals(mapOf("G" to "B", "H" to "A"), c1.mapTypeVariables(c3))
            assertEquals(mapOf("I" to "B"), c1.mapTypeVariables(c4))
        }
    }

    @Test
    fun `Test mapTypeVariables with concrete classes`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Grandparent<A, B> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Parent<T> extends Grandparent<T, Parent<T>> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Child extends Parent<Child> {}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Child extends test.pkg.Parent<test.pkg.Child> {
                          }
                          public class Grandparent<A, B> {
                          }
                          public class Parent<T> extends test.pkg.Grandparent<T,test.pkg.Parent<T>> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        open class Grandparent<A, B>
                        open class Parent<T> : Grandparent<T, Parent<T>>
                        class Child : Parent<Child>
                    """
                        .trimIndent()
                )
            )
        ) { codebase ->
            val grandparent = codebase.assertClass("test.pkg.Grandparent")
            val parent = codebase.assertClass("test.pkg.Parent")
            val child = codebase.assertClass("test.pkg.Child")

            assertEquals(
                mapOf("A" to "T", "B" to "test.pkg.Parent"),
                parent.mapTypeVariables(grandparent)
            )
            assertEquals(mapOf("T" to "test.pkg.Child"), child.mapTypeVariables(parent))
            assertEquals(
                mapOf("A" to "test.pkg.Child", "B" to "test.pkg.Parent"),
                child.mapTypeVariables(grandparent)
            )
        }
    }

    @Test
    fun `Test mapTypeVariables with interfaces`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public interface Interface3<G, H> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public interface Interface2<E, F> extends Interface3<E, F> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public interface Interface1<C, D> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Class<A, B> implements Interface1<A, B>, Interface2<B, A>{}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Class<A, B> implements test.pkg.Interface1<A,B> test.pkg.Interface2<B,A> {
                          }
                          public interface Interface1<C, D> {
                          }
                          public interface Interface2<E, F> extends test.pkg.Interface3<E,F> {
                          }
                          public interface Interface3<G, H> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        interface Interface3<G, H>
                        interface Interface2<E, F> : Interface3<E, F>
                        interface Interface1<C, D>
                        class Class<A, B> : Interface1<A, B>, Interface2<B, A>
                    """
                        .trimIndent()
                )
            )
        ) { codebase ->
            val i3 = codebase.assertClass("test.pkg.Interface3")
            val i2 = codebase.assertClass("test.pkg.Interface2")
            val i1 = codebase.assertClass("test.pkg.Interface1")
            val c = codebase.assertClass("test.pkg.Class")

            assertEquals(mapOf("C" to "A", "D" to "B"), c.mapTypeVariables(i1))

            assertEquals(mapOf("G" to "E", "H" to "F"), i2.mapTypeVariables(i3))
            assertEquals(mapOf("E" to "B", "F" to "A"), c.mapTypeVariables(i2))
            assertEquals(mapOf("G" to "B", "H" to "A"), c.mapTypeVariables(i3))
        }
    }

    @Test
    fun `Test mapTypeVariables with diamond interface`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public interface Root<T> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public interface Interface1<T1> extends Root<T1> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public interface Interface2<T2> extends Root<T2> {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;
                        public class Child<X, Y> implements Interface1<X>, Interface2<Y> {}
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 5.0
                        package test.pkg {
                          public class Child<X, Y> implements test.pkg.Interface1<X> test.pkg.Interface2<Y> {
                          }
                          public interface Interface1<T1> extends test.pkg.Root<T1> {
                          }
                          public interface Interface2<T2> extends test.pkg.Root<T2> {
                          }
                          public interface Root<T> {
                          }
                        }
                    """
                        .trimIndent()
                )
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        interface Root<T>
                        interface Interface1<T1> : Root<T1>
                        interface Interface2<T2> : Root<T2>
                        class Child<X, Y> : Interface1<X>, Interface2<Y>
                    """
                        .trimIndent()
                )
            )
        ) { codebase ->
            val root = codebase.assertClass("test.pkg.Root")
            val i1 = codebase.assertClass("test.pkg.Interface1")
            val i2 = codebase.assertClass("test.pkg.Interface2")
            val child = codebase.assertClass("test.pkg.Child")
            assertEquals(mapOf("T" to "T1"), i1.mapTypeVariables(root))
            assertEquals(mapOf("T" to "T2"), i2.mapTypeVariables(root))
            assertEquals(
                mapOf("T1" to "X"),
                child.mapTypeVariables(i1),
            )
            assertEquals(
                mapOf("T2" to "Y"),
                child.mapTypeVariables(i2),
            )
            assertEquals(
                mapOf("T" to "X"),
                child.mapTypeVariables(root),
            )
        }
    }
}
