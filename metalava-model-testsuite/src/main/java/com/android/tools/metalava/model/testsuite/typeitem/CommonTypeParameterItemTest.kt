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

package com.android.tools.metalava.model.testsuite.typeitem

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.StripJavaLangPrefix
import com.android.tools.metalava.model.TypeStringConfiguration
import com.android.tools.metalava.model.testing.classTypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.TestFileCacheRule
import com.android.tools.metalava.testing.cacheIn
import com.android.tools.metalava.testing.jarFromSources
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.ClassRule
import org.junit.Test

class CommonTypeParameterItemTest : BaseModelTest() {

    companion object {
        /** Create a [TestFileCache] whose lifespan encompasses all the tests in this class. */
        @ClassRule @JvmField val testFileCacheRule = TestFileCacheRule()

        /** Jar file containing an `other.pkg.Other` class with a `Nested` class. */
        @Suppress("TypeParameterExplicitlyExtendsObject")
        private val typeParametersJar =
            jarFromSources(
                    "type-parameters.jar",
                    java(
                        """
                            package test.pkg;
                            public class Test<A, B extends Object, C extends Comparable<C>, D extends Object & Comparable<D>> {
                                private Test() {}
                            }
                        """
                    ),
                )
                .cacheIn(testFileCacheRule)
    }

    @Test
    fun `Test typeBounds no extends`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo<T> {}
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo<T>
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo<T> {
                        ctor public Foo();
                      }
                    }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val typeParameter = fooClass.typeParameterList.single()
            assertThat(typeParameter.toSource()).isEqualTo("T")
            typeParameter.assertUsesDefaultTypeBounds()
        }
    }

    @Test
    fun `Test typeBounds extends Comparable`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo<T extends Comparable<T>> {}
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo<T: Comparable<T>>
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo<T extends Comparable<T>> {
                        ctor public Foo();
                      }
                    }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val typeParameter = fooClass.typeParameterList.single()
            val typeBounds = typeParameter.typeBounds()
            assertThat(typeBounds.size).isEqualTo(1)
            val typeBound = typeBounds[0]
            assertThat(typeBound).isInstanceOf(ClassTypeItem::class.java)
            assertThat((typeBound as ClassTypeItem).qualifiedName).isEqualTo("java.lang.Comparable")
        }
    }

    @Test
    fun `Test typeBounds multiple`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo<T extends Object & Comparable<T>> {}
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo<T> where T : Object, T : Comparable<T>
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo<T extends Object & Comparable<T>> {
                        ctor public Foo();
                      }
                    }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val typeParameter = fooClass.typeParameterList.single()
            val typeBounds = typeParameter.typeBounds()
            assertThat(typeBounds.size).isEqualTo(2)
            val (first, second) = typeBounds
            assertThat(first).isInstanceOf(ClassTypeItem::class.java)
            assertThat((first as ClassTypeItem).qualifiedName).isEqualTo("java.lang.Object")
            assertThat(second).isInstanceOf(ClassTypeItem::class.java)
            assertThat((second as ClassTypeItem).qualifiedName).isEqualTo("java.lang.Comparable")
        }
    }

    @Test
    fun `Test typeBounds on jar`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Placeholder {}
                """
            ),
            testFixture =
                TestFixture(
                    additionalClassPath = listOf(typeParametersJar.toFile()),
                ),
        ) {
            val testClass = codebase.assertResolvedClass("test.pkg.Test")
            testClass.assertTypeParameterListBounds(
                """
                    A -> [java.lang.Object!]
                    B -> [java.lang.Object!]
                    C -> [java.lang.Comparable<C>!]
                    D -> [java.lang.Object!, java.lang.Comparable<D>!]
                """
            )
        }
    }

    @Test
    fun `Test self-referential type parameter`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo<T extends test.pkg.Foo<T>> {
                        method public <T extends test.pkg.Foo<T>> T foo();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class Foo<T extends Foo<T>> {
                        public <T extends Foo<T>> T foo() {}
                    }
                    """
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo<T : Foo<T>> {
                        fun foo<T : Foo<T>>(): T {}
                    }
                """
            )
        ) {
            val clazz = codebase.assertClass("test.pkg.Foo")
            val classTypeParam = clazz.typeParameterList.single()
            val classTypeParamBound = classTypeParam.typeBounds().single()
            assertThat(classTypeParamBound).isInstanceOf(ClassTypeItem::class.java)
            assertThat((classTypeParamBound as ClassTypeItem).qualifiedName)
                .isEqualTo("test.pkg.Foo")
            assertThat(classTypeParamBound.arguments).hasSize(1)
            val classTypeParamBoundTypeArgument = classTypeParamBound.arguments.single()
            classTypeParamBoundTypeArgument.assertReferencesTypeParameter(classTypeParam)

            val method = clazz.methods().single()
            val methodTypeParam = method.typeParameterList.single()
            val methodTypeParamBound = methodTypeParam.typeBounds().single()
            assertThat(methodTypeParamBound).isInstanceOf(ClassTypeItem::class.java)
            assertThat((methodTypeParamBound as ClassTypeItem).qualifiedName)
                .isEqualTo("test.pkg.Foo")
            assertThat(methodTypeParamBound.arguments).hasSize(1)
            val methodTypeParamBoundTypeArgument = methodTypeParamBound.arguments.single()
            methodTypeParamBoundTypeArgument.assertReferencesTypeParameter(methodTypeParam)
        }
    }

    @Test
    fun `Test type parameters that reference each other`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo<A extends C, B extends A, C> {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class Foo<A extends C, B extends A, C> {}
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo<A : C, B : A, C>
                """
            )
        ) {
            val typeParams = codebase.assertClass("test.pkg.Foo").typeParameterList
            assertThat(typeParams).hasSize(3)
            val a = typeParams[0]
            val b = typeParams[1]
            val c = typeParams[2]

            // A extends C
            val aBound = a.typeBounds().single()
            aBound.assertReferencesTypeParameter(c)

            // B extends A
            val bBound = b.typeBounds().single()
            bBound.assertReferencesTypeParameter(a)

            // C
            c.assertUsesDefaultTypeBounds()
        }
    }

    @Test
    fun `Test method type parameter that references class type parameter`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo<T> {
                        method public <E extends T> void foo();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class Foo<T> {
                        public <E extends T> void foo() {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo<T> {
                        fun <E : T> foo() {}
                    }
                """
            )
        ) {
            val clazz = codebase.assertClass("test.pkg.Foo")
            val clazzTypeParam = clazz.typeParameterList.single()
            assertThat(clazzTypeParam.toSource()).isEqualTo("T")

            val method = clazz.methods().single()
            val methodTypeParam = method.typeParameterList.single()
            assertThat(methodTypeParam.toSource()).isEqualTo("E extends T")
            val methodTypeParamBound = methodTypeParam.typeBounds().single()
            methodTypeParamBound.assertReferencesTypeParameter(clazzTypeParam)
        }
    }

    @Test
    fun `Test type parameter bounds with multiple class parameters`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    import java.util.Map;
                    public class Foo<T extends Map<Integer, String>> {}
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo<T extends java.util.Map<java.lang.Integer, java.lang.String>> {
                      }
                    }
                """
            )
        ) {
            val clazz = codebase.assertClass("test.pkg.Foo")
            val typeParameter = clazz.typeParameterList.single()
            assertThat(typeParameter.isReified()).isFalse()
            // There's an expected space between "java.lang.Integer" and "java.lang.String"
            assertThat(typeParameter.toSource())
                .isEqualTo("T extends java.util.Map<java.lang.Integer, java.lang.String>")

            // There's no expected space between "Integer" and "String"
            val configuration =
                TypeStringConfiguration(stripJavaLangPrefix = StripJavaLangPrefix.ALWAYS)
            assertThat(typeParameter.toSource(configuration))
                .isEqualTo("T extends java.util.Map<Integer,String>")
        }
    }

    @Test
    fun `Test reified type parameter`() {
        runCodebaseTest(
            // reified isn't possible from java source
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        inline fun <reified T: List<String>> foo(): T {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo {
                        ctor public Foo();
                        method @KotlinOnly public inline <reified T extends java.util.List<java.lang.String>> T foo();
                      }
                    }
                """
            )
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            val typeParam = method.typeParameterList.single()
            assertThat(typeParam.isReified()).isTrue()
            assertThat(typeParam.toSource())
                .isEqualTo("reified T extends java.util.List<java.lang.String>")
        }
    }

    @Test
    fun `Test explicit Object bound - java`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo<T extends Object, U extends Object & Comparable<U>> {}
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo<T extends java.lang.Object, U extends java.lang.Object & java.lang.Comparable<U>> {
                      }
                    }
                """
            )
        ) {
            val clazz = codebase.assertClass("test.pkg.Foo")
            val typeParameters = clazz.typeParameterList

            val typeParameterT = typeParameters[0]
            assertThat(typeParameterT.isReified()).isFalse()
            val boundsT = typeParameterT.typeBounds()
            assertThat(boundsT).hasSize(1)
            assertThat(boundsT.single().isJavaLangObject()).isTrue()
            assertThat(typeParameterT.toSource()).isEqualTo("T")

            val typeParameterU = typeParameters[1]
            assertThat(typeParameterU.isReified()).isFalse()
            val boundsU = typeParameterU.typeBounds()
            assertThat(boundsU).hasSize(2)
            assertThat(boundsU[0].isJavaLangObject()).isTrue()
            assertThat((boundsU[1] as ClassTypeItem).qualifiedName)
                .isEqualTo("java.lang.Comparable")
            // Since this is not a single object bound, it is still included
            assertThat(typeParameterU.toSource())
                .isEqualTo("U extends java.lang.Object & java.lang.Comparable<U>")
        }
    }

    @Test
    fun `Test explicit Any bounds - kotlin`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo<T: Any?, U: Any>
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            // TODO(b/488322332): This is wrong, U should not be nullable. That is happening because
            //   Kotlin is dropping Any, even if it non-nullable.
            fooClass.assertTypeParameterListBounds(
                """
                    T -> [java.lang.Object?]
                    U -> [java.lang.Object?]
                """
            )
        }
    }

    @Test
    fun `Test toType`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public <T> T foo() {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public <T> T foo();
                      }
                    }
                """
            )
        ) {
            val method = codebase.assertClass("test.pkg.Foo").assertMethod("foo", emptyList())
            val typeParameter = method.typeParameterList.single()
            val typeVariable = method.returnType()

            typeVariable.assertReferencesTypeParameter(typeParameter)
            assertThat(typeParameter.type()).isEqualTo(typeVariable)
        }
    }

    @Test
    fun `Test type parameter with annotations`() {
        val typeParameterAnnotation =
            java(
                """
                    package test.pkg;
                    import java.lang.annotation.*;
                    import static java.lang.annotation.ElementType.TYPE_PARAMETER;
                    import static java.lang.annotation.RetentionPolicy.SOURCE;
                    @Retention(SOURCE)
                    @Target({TYPE_PARAMETER})
                    public @interface TypeParameterAnnotation {
                    }
                """
            )
        runCodebaseTest(
            inputSet(
                typeParameterAnnotation,
                java(
                    """
                        package test.pkg;
                        public class Foo<@TypeParameterAnnotation T> {
                            private Foo() {}
                        }
                    """
                ),
            ),
            inputSet(
                typeParameterAnnotation,
                kotlin(
                    """
                        package test.pkg
                        class Foo<@TypeParameterAnnotation T>
                        private constructor()
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo<@test.pkg.TypeParameterAnnotation T> {
                          }
                        }
                    """
                ),
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val typeParameter = fooClass.typeParameterList.single()
            val annotation = typeParameter.modifiers.annotations().single()
            assertThat(annotation.qualifiedName).isEqualTo("test.pkg.TypeParameterAnnotation")
        }
    }

    @Test
    fun `Test asErasedType for simple type parameter no extends`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public interface Foo<A> {
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    interface Foo<A> {
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface Foo<A> {
                      }
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val typeParameterItem = fooClass.typeParameterList.single()
            assertThat(typeParameterItem.asErasedType())
                .isEqualTo(classTypeItem("java.lang.Object"))
        }
    }

    @Test
    fun `Test asErasedType for simple type parameter extends Exception`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public interface Foo<A extends Exception> {
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    interface Foo<A : Exception> {
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface Foo<A extends Exception> {
                      }
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val typeParameterItem = fooClass.typeParameterList.single()
            assertThat(typeParameterItem.asErasedType())
                .isEqualTo(classTypeItem("java.lang.Exception"))
        }
    }

    @Test
    fun `Test asErasedType for simple type parameter extends generic class`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public interface Foo<A extends Generic<A>> {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public interface Generic<A> {
                        }
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        interface Foo<A : Generic<A>> {
                        }
                    """
                ),
                kotlin(
                    """
                        package test.pkg
                        interface Generic<A> {
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public interface Foo<A extends test.pkg.Generic<A>> {
                          }
                          public interface Generic<A> {
                          }
                        }
                    """
                ),
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val typeParameterItem = fooClass.typeParameterList.single()
            assertThat(typeParameterItem.asErasedType())
                .isEqualTo(classTypeItem("test.pkg.Generic"))
        }
    }

    @Test
    fun `Test asErasedType when parameter extends another parameter`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public interface Foo<A extends Exception, B extends A, C extends B> {
                        C method();
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    interface Foo<A: Exception, B: A, C: B> {
                        fun method(): C
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface Foo<A extends Exception, B extends A, C extends B> {
                          method C method();
                      }
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val exceptionTypeItem = classTypeItem("java.lang.Exception")
            for (typeParameterItem in fooClass.typeParameterList) {
                assertWithMessage("type parameter ${typeParameterItem.name()}")
                    .that(typeParameterItem.asErasedType())
                    .isEqualTo(exceptionTypeItem)
            }
        }
    }
}
