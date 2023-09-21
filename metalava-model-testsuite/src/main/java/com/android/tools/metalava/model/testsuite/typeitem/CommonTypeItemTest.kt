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

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.TestParameters
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CommonTypeItemTest(parameters: TestParameters) : BaseModelTest(parameters) {
    @Test
    fun `Test primitive types`() {
        runCodebaseTest(
            java(
                """
                    public class Foo {
                        public void foo(
                            boolean p0,
                            byte p1,
                            char p2,
                            double p3,
                            float p4,
                            int p5,
                            long p6,
                            short p7
                        ) {}
                    }
                """
            ),
            kotlin(
                """
                    class Foo {
                        fun foo(
                            p0: Boolean,
                            p1: Byte,
                            p2: Char,
                            p3: Double,
                            p4: Float,
                            p5: Int,
                            p6: Long,
                            p7: Short
                        ) = Unit
                    }
                """
            )
        ) { codebase ->
            val method = codebase.assertClass("Foo").methods().single()

            val returnType = method.returnType()
            assertThat(returnType).isInstanceOf(PrimitiveTypeItem::class.java)
            assertThat((returnType as PrimitiveTypeItem).kind)
                .isEqualTo(PrimitiveTypeItem.Primitive.VOID)

            val expectedParamTypes =
                listOf(
                    PrimitiveTypeItem.Primitive.BOOLEAN,
                    PrimitiveTypeItem.Primitive.BYTE,
                    PrimitiveTypeItem.Primitive.CHAR,
                    PrimitiveTypeItem.Primitive.DOUBLE,
                    PrimitiveTypeItem.Primitive.FLOAT,
                    PrimitiveTypeItem.Primitive.INT,
                    PrimitiveTypeItem.Primitive.LONG,
                    PrimitiveTypeItem.Primitive.SHORT
                )

            val params = method.parameters().map { it.type() }
            assertThat(params).hasSize(expectedParamTypes.size)
            for ((param, expectedKind) in params.zip(expectedParamTypes)) {
                assertThat(param).isInstanceOf(PrimitiveTypeItem::class.java)
                assertThat((param as PrimitiveTypeItem).kind).isEqualTo(expectedKind)
            }
        }
    }

    @Test
    fun `Test array types`() {
        runCodebaseTest(
            java(
                """
                    public class Foo {
                        public void foo(
                            java.lang.String[] p0,
                            java.lang.String[][] p1,
                            java.lang.String... p2
                        ) {}
                    }
                """
            ),
            kotlin(
                """
                    class Foo {
                        fun foo(
                            p0: Array<String>,
                            p1: Array<Array<String>>,
                            vararg p2: String
                        ) = Unit
                    }
                """
            )
        ) { codebase ->
            val method = codebase.assertClass("Foo").methods().single()

            val paramTypes = method.parameters().map { it.type() }
            assertThat(paramTypes).hasSize(3)

            // String[] / Array<String>
            val simpleArray = paramTypes[0]
            assertThat(simpleArray).isInstanceOf(ArrayTypeItem::class.java)
            assertThat((simpleArray as ArrayTypeItem).componentType.isString()).isTrue()
            assertThat(simpleArray.isVarargs).isFalse()

            // String[][] / Array<Array<String>>
            val twoDimensionalArray = paramTypes[1]
            assertThat(twoDimensionalArray).isInstanceOf(ArrayTypeItem::class.java)
            assertThat((twoDimensionalArray as ArrayTypeItem).isVarargs).isFalse()
            val innerArray = twoDimensionalArray.componentType
            assertThat(innerArray).isInstanceOf(ArrayTypeItem::class.java)
            assertThat((innerArray as ArrayTypeItem).componentType.isString()).isTrue()
            assertThat(innerArray.isVarargs).isFalse()

            // String... / vararg String
            val varargs = paramTypes[2]
            assertThat(twoDimensionalArray).isInstanceOf(ArrayTypeItem::class.java)
            assertThat((varargs as ArrayTypeItem).componentType.isString()).isTrue()
            assertThat(varargs.isVarargs).isTrue()
        }
    }

    @Test
    fun `Test wildcard types`() {
        runCodebaseTest(
            java(
                """
                    public class Foo<T> {
                        public void foo(
                            Foo<?> p0,
                            Foo<? extends java.lang.String> p1,
                            Foo<? super java.lang.String> p2
                        ) {}
                    }
                """
            ),
            kotlin(
                """
                    class Foo<T> {
                        fun foo(
                            p0: Foo<*>,
                            p1: Foo<out String>,
                            p2: Foo<in String>
                        ) = Unit
                    }
                """
            )
        ) { codebase ->
            val method = codebase.assertClass("Foo").methods().single()

            val wildcardTypes =
                method.parameters().map {
                    val paramType = it.type()
                    assertThat(paramType).isInstanceOf(ClassTypeItem::class.java)
                    assertThat((paramType as ClassTypeItem).parameters).hasSize(1)
                    paramType.parameters.single()
                }
            assertThat(wildcardTypes).hasSize(3)

            // Foo<?> / Foo<*>
            // Unbounded wildcards implicitly have an Object extends bound
            val unboundedWildcard = wildcardTypes[0]
            assertThat(unboundedWildcard).isInstanceOf(WildcardTypeItem::class.java)
            val unboundedExtendsBound = (unboundedWildcard as WildcardTypeItem).extendsBound
            assertThat(unboundedExtendsBound).isNotNull()
            assertThat(unboundedExtendsBound!!.isJavaLangObject()).isTrue()
            assertThat(unboundedWildcard.superBound).isNull()

            // Foo<? extends String> / Foo<out String>
            val extendsBoundWildcard = wildcardTypes[1]
            assertThat(extendsBoundWildcard).isInstanceOf(WildcardTypeItem::class.java)
            val extendsBound = (extendsBoundWildcard as WildcardTypeItem).extendsBound
            assertThat(extendsBound).isNotNull()
            assertThat(extendsBound!!.isString()).isTrue()
            assertThat(extendsBoundWildcard.superBound).isNull()

            // Foo<? super String> / Foo<in String>
            // A super bounded wildcard implicitly has an Object extends bound
            val superBoundWildcard = wildcardTypes[2]
            assertThat(superBoundWildcard).isInstanceOf(WildcardTypeItem::class.java)
            val superExtendsBound = (superBoundWildcard as WildcardTypeItem).extendsBound
            assertThat(superExtendsBound).isNotNull()
            assertThat(superExtendsBound!!.isJavaLangObject()).isTrue()
            val superBound = superBoundWildcard.superBound
            assertThat(superBound).isNotNull()
            assertThat(superBound!!.isString()).isTrue()
        }
    }

    @Test
    fun `Test variable types`() {
        runCodebaseTest(
            java(
                """
                    public class Foo<C> {
                        public <M> void foo(C p0, M p1) {}
                    }
                """
            ),
            kotlin(
                """
                    class Foo<C> {
                        fun <M> foo(p0: C, p1: M) = Unit
                    }
                """
            )
        ) { codebase ->
            val clz = codebase.assertClass("Foo")
            val classTypeParam = clz.typeParameterList().typeParameters().single()
            val method = clz.methods().single()
            val methodTypeParam = method.typeParameterList().typeParameters().single()
            val paramTypes = method.parameters().map { it.type() }
            assertThat(paramTypes).hasSize(2)

            val classTypeVariable = paramTypes[0]
            assertThat(classTypeVariable).isInstanceOf(VariableTypeItem::class.java)
            assertThat((classTypeVariable as VariableTypeItem).name).isEqualTo("C")
            assertThat(classTypeVariable.asTypeParameter).isEqualTo(classTypeParam)

            val methodTypeVariable = paramTypes[1]
            assertThat(methodTypeVariable).isInstanceOf(VariableTypeItem::class.java)
            assertThat((methodTypeVariable as VariableTypeItem).name).isEqualTo("M")
            assertThat(methodTypeVariable.asTypeParameter).isEqualTo(methodTypeParam)
        }
    }

    @Test
    fun `Test class types`() {
        runCodebaseTest(
            java(
                """
                    public class Foo {
                        public <T> void foo(
                            java.lang.String p0,
                            java.util.List<java.lang.String> p1,
                            java.util.List<java.lang.String[]> p2,
                            java.util.Map<java.lang.String, Foo> p3
                        ) {}
                    }
                """
            ),
            kotlin(
                """
                    class Foo {
                        fun <T> foo(
                            p0: String,
                            p1: List<String>,
                            p2: List<Array<String>>,
                            p3: Map<String, Foo>
                        ) = Unit
                    }
                """
            )
        ) { codebase ->
            val method = codebase.assertClass("Foo").methods().single()
            val paramTypes = method.parameters().map { it.type() }

            val stringType = paramTypes[0]
            assertThat(stringType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((stringType as ClassTypeItem).qualifiedName).isEqualTo("java.lang.String")
            assertThat(stringType.parameters).isEmpty()

            // List<String>
            val stringListType = paramTypes[1]
            assertThat(stringListType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((stringListType as ClassTypeItem).qualifiedName).isEqualTo("java.util.List")
            assertThat(stringListType.parameters).hasSize(1)
            assertThat(stringListType.parameters.single().isString()).isTrue()

            // List<String[]> / List<Array<String>>
            val arrayListType = paramTypes[2]
            assertThat(arrayListType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((arrayListType as ClassTypeItem).qualifiedName).isEqualTo("java.util.List")
            assertThat(arrayListType.parameters).hasSize(1)
            val arrayType = arrayListType.parameters.single()
            assertThat(arrayType).isInstanceOf(ArrayTypeItem::class.java)
            assertThat((arrayType as ArrayTypeItem).componentType.isString()).isTrue()

            // Map<String, Foo>
            val mapType = paramTypes[3]
            assertThat(mapType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((mapType as ClassTypeItem).qualifiedName).isEqualTo("java.util.Map")
            assertThat(mapType.parameters).hasSize(2)
            val mapKeyType = mapType.parameters.first()
            assertThat(mapKeyType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((mapKeyType as ClassTypeItem).isString()).isTrue()
            val mapValueType = mapType.parameters.last()
            assertThat(mapValueType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((mapValueType as ClassTypeItem).qualifiedName).isEqualTo("Foo")
        }
    }

    @Test
    fun `Test inner types`() {
        runCodebaseTest(
            java(
                """
                    public class Outer<O> {
                        public class Inner<I> {
                        }

                        public <P1, P2> Outer<P1>.Inner<P2> foo() {
                            return new Outer<P1>.Inner<P2>();
                        }
                    }
                """
            ),
            kotlin(
                """
                    class Outer<O> {
                        inner class Inner<I>

                        fun <P1, P2> foo(): Outer<P1>.Inner<P2> {
                            return Outer<P1>.Inner<P2>()
                        }
                    }
                """
            )
        ) { codebase ->
            val method = codebase.assertClass("Outer").methods().single()
            // Outer<P1>.Inner<P2>
            val innerType = method.returnType()
            assertThat(innerType).isInstanceOf(ClassTypeItem::class.java)
            assertThat(innerType.toCanonicalType()).isEqualTo("Outer<P1>.Inner<P2>")
            assertThat((innerType as ClassTypeItem).qualifiedName).isEqualTo("Outer.Inner")
            assertThat(innerType.parameters).hasSize(1)
            val innerTypeParameter = innerType.parameters.single()
            assertThat((innerTypeParameter as VariableTypeItem).name).isEqualTo("P2")
        }
    }
}
