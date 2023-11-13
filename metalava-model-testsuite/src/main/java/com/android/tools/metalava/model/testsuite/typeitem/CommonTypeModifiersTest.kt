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
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.isNullnessAnnotation
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.TestParameters
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CommonTypeModifiersTest(parameters: TestParameters) : BaseModelTest(parameters) {

    private fun TypeItem.annotationNames(): List<String?> {
        return modifiers.annotations().map { it.qualifiedName }
    }

    private fun Item.annotationNames(): List<String?> {
        return modifiers.annotations().map { it.qualifiedName }
    }

    @Test
    fun `Test annotation on basic types`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public @A int foo1() {}
                        public @A String foo2() {}
                        public <T> @A T foo3() {}
                    }
                    @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE)
                    public @interface A {}
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        fun foo1(): @A Int {}
                        fun foo2(): @A String {}
                        fun <T> foo3(): @A T {}
                    }
                    @Target(AnnotationTarget.TYPE)
                    annotation class A
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public @test.pkg.A int foo1();
                        method public @test.pkg.A String foo2();
                        method public <T> @test.pkg.A T foo3();
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val methods = codebase.assertClass("test.pkg.Foo").methods()
            assertThat(methods).hasSize(3)

            // @test.pkg.A int
            val primitiveMethod = methods[0]
            val primitive = primitiveMethod.returnType()
            assertThat(primitive).isInstanceOf(PrimitiveTypeItem::class.java)
            assertThat(primitive.annotationNames()).containsExactly("test.pkg.A")
            assertThat(primitiveMethod.annotationNames()).isEmpty()

            // @test.pkg.A String
            val stringMethod = methods[1]
            val string = stringMethod.returnType()
            assertThat(string).isInstanceOf(ClassTypeItem::class.java)
            assertThat(string.annotationNames()).containsExactly("test.pkg.A")
            val stringMethodAnnotations = stringMethod.annotationNames()
            // The Kotlin version puts a nullability annotation on the method
            if (stringMethodAnnotations.isNotEmpty()) {
                assertThat(stringMethodAnnotations)
                    .containsExactly("org.jetbrains.annotations.NotNull")
            }

            // @test.pkg.A T
            val variableMethod = methods[2]
            val variable = variableMethod.returnType()
            val typeParameter = variableMethod.typeParameterList().typeParameters().single()
            assertThat(variable).isInstanceOf(VariableTypeItem::class.java)
            assertThat((variable as VariableTypeItem).asTypeParameter).isEqualTo(typeParameter)
            assertThat(variable.annotationNames()).containsExactly("test.pkg.A")
            assertThat(variableMethod.annotationNames()).isEmpty()
        }
    }

    @Test
    fun `Test type-use annotations with multiple allowed targets`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public @A int foo1() {}
                        public @A String foo2() {}
                        public @A <T> T foo3() {}
                    }
                    @java.lang.annotation.Target({ java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE_USE })
                    public @interface A {}
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        @A fun foo(): @A Int {}
                        @A fun foo(): @A String {}
                        @A fun <T> foo(): @A T {}
                    }
                    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
                    annotation class A
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method @test.pkg.A public @test.pkg.A int foo1();
                        method @test.pkg.A public @test.pkg.A String foo2();
                        method @test.pkg.A public <T> @test.pkg.A T foo3();
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val methods = codebase.assertClass("test.pkg.Foo").methods()
            assertThat(methods).hasSize(3)

            // @test.pkg.A int
            val primitiveMethod = methods[0]
            val primitive = primitiveMethod.returnType()
            assertThat(primitive).isInstanceOf(PrimitiveTypeItem::class.java)
            assertThat(primitive.annotationNames()).containsExactly("test.pkg.A")
            assertThat(primitiveMethod.annotationNames()).containsExactly("test.pkg.A")

            // @test.pkg.A String
            val stringMethod = methods[1]
            val string = stringMethod.returnType()
            assertThat(string).isInstanceOf(ClassTypeItem::class.java)
            assertThat(string.annotationNames()).containsExactly("test.pkg.A")
            // The Kotlin version puts a nullability annotation on the method
            val stringMethodAnnotations =
                stringMethod.annotationNames().filter { !isNullnessAnnotation(it.orEmpty()) }
            assertThat(stringMethodAnnotations).containsExactly("test.pkg.A")

            // @test.pkg.A T
            val variableMethod = methods[2]
            val variable = variableMethod.returnType()
            val typeParameter = variableMethod.typeParameterList().typeParameters().single()
            assertThat(variable).isInstanceOf(VariableTypeItem::class.java)
            assertThat((variable as VariableTypeItem).asTypeParameter).isEqualTo(typeParameter)
            assertThat(variable.annotationNames()).containsExactly("test.pkg.A")
            assertThat(variableMethod.annotationNames()).containsExactly("test.pkg.A")
        }
    }

    @Test
    fun `Test kotlin type-use annotations with multiple allowed targets on non-type target`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        // @A can be applied to a function or type.
                        // Because of the positioning, it should apply to the function here.
                        @A fun foo(): Int {}
                        @A fun foo(): String {}
                        @A fun <T> foo(): T {}
                    }
                    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
                    annotation class A
                """
            )
        ) { codebase ->
            val methods = codebase.assertClass("test.pkg.Foo").methods()
            assertThat(methods).hasSize(3)

            val primitiveMethod = methods[0]
            val primitive = primitiveMethod.returnType()
            assertThat(primitive).isInstanceOf(PrimitiveTypeItem::class.java)
            assertThat(primitive.annotationNames()).isEmpty()
            assertThat(primitiveMethod.annotationNames()).containsExactly("test.pkg.A")

            val stringMethod = methods[1]
            val string = stringMethod.returnType()
            assertThat(string).isInstanceOf(ClassTypeItem::class.java)
            assertThat(string.annotationNames()).isEmpty()
            assertThat(stringMethod.annotationNames())
                .containsExactly("org.jetbrains.annotations.NotNull", "test.pkg.A")

            val variableMethod = methods[2]
            val variable = variableMethod.returnType()
            val typeParameter = variableMethod.typeParameterList().typeParameters().single()
            assertThat(variable).isInstanceOf(VariableTypeItem::class.java)
            assertThat((variable as VariableTypeItem).asTypeParameter).isEqualTo(typeParameter)
            assertThat(variable.annotationNames()).isEmpty()
            assertThat(variableMethod.annotationNames()).containsExactly("test.pkg.A")
        }
    }

    @Test
    fun `Test filtering of annotations based on target usages`() {
        runCodebaseTest(
            java(
                """
                package test.pkg;
                public class Foo {
                    public @A String bar(@A int arg) {}
                    public @A String baz;
                }

                @java.lang.annotation.Target({ java.lang.annotation.ElementType.TYPE_USE, java.lang.annotation.ElementType.PARAMETER })
                public @interface A {}
            """
                    .trimIndent()
            )
        ) { codebase ->
            val fooClass = codebase.assertClass("test.pkg.Foo")

            // @A is TYPE_USE and PARAMETER, so it should not appear on the method
            val method = fooClass.methods().single()
            assertThat(method.annotationNames()).isEmpty()
            val methodReturn = method.returnType()
            assertThat(methodReturn.annotationNames()).containsExactly("test.pkg.A")

            // @A is TYPE_USE and PARAMETER, so it should appear on the parameter as well as type
            val methodParam = method.parameters().single()
            assertThat(methodParam.annotationNames()).containsExactly("test.pkg.A")
            val methodParamType = methodParam.type()
            assertThat(methodParamType.annotationNames()).containsExactly("test.pkg.A")

            // @A is TYPE_USE and PARAMETER, so it should not appear on the field
            val field = fooClass.fields().single()
            assertThat(field.annotationNames()).isEmpty()
            val fieldType = field.type()
            assertThat(fieldType.annotationNames()).containsExactly("test.pkg.A")
        }
    }

    @Test
    fun `Test annotations on qualified class type`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public test.pkg.@test.pkg.A Foo foo() {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public test.pkg.@test.pkg.A Foo foo();
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            assertThat(method.annotationNames()).isEmpty()

            val returnType = method.returnType()
            assertThat(returnType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((returnType as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Foo")
            assertThat(returnType.annotationNames()).containsExactly("test.pkg.A")
        }
    }

    @Test
    fun `Test annotations on class type parameters`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Foo {
                        public java.util.@test.pkg.A Map<java.lang.@test.pkg.B @test.pkg.C String, java.lang.@test.pkg.D String> foo() {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public java.util.@test.pkg.A Map<java.lang.@test.pkg.B @test.pkg.C String, java.lang.@test.pkg.D String> foo();
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            assertThat(method.annotationNames()).isEmpty()

            val mapType = method.returnType()
            assertThat(mapType).isInstanceOf(ClassTypeItem::class.java)
            assertThat(mapType.annotationNames()).containsExactly("test.pkg.A")
            assertThat((mapType as ClassTypeItem).parameters).hasSize(2)

            // java.lang.@test.pkg.B @test.pkg.C String
            val string1 = mapType.parameters[0]
            assertThat(string1.isString()).isTrue()
            assertThat(string1.annotationNames()).containsExactly("test.pkg.B", "test.pkg.C")

            // java.lang.@test.pkg.D String
            val string2 = mapType.parameters[1]
            assertThat(string2.isString()).isTrue()
            assertThat(string2.annotationNames()).containsExactly("test.pkg.D")
        }
    }

    @Test
    fun `Test annotations on array type and component type`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public test.pkg.@test.pkg.A @test.pkg.B Foo @test.pkg.B @test.pkg.C [] foo() {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public test.pkg.@test.pkg.A @test.pkg.B Foo @test.pkg.B @test.pkg.C [] foo();
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            assertThat(method.annotationNames()).isEmpty()

            val arrayType = method.returnType()
            assertThat(arrayType).isInstanceOf(ArrayTypeItem::class.java)
            assertThat(arrayType.annotationNames()).containsExactly("test.pkg.B", "test.pkg.C")

            val componentType = (arrayType as ArrayTypeItem).componentType
            assertThat(componentType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((componentType as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Foo")
            assertThat(componentType.annotationNames()).containsExactly("test.pkg.A", "test.pkg.B")
        }
    }

    @Test
    fun `Test annotations on multidimensional array`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public test.pkg.@test.pkg.A Foo @test.pkg.B [] @test.pkg.C [] @test.pkg.D [] foo() {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public test.pkg.@test.pkg.A Foo @test.pkg.B [] @test.pkg.C [] @test.pkg.D [] foo();
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            assertThat(method.annotationNames()).isEmpty()

            val outerArray = method.returnType()
            assertThat(outerArray).isInstanceOf(ArrayTypeItem::class.java)
            assertThat(outerArray.annotationNames()).containsExactly("test.pkg.B")

            val middleArray = (outerArray as ArrayTypeItem).componentType
            assertThat(middleArray).isInstanceOf(ArrayTypeItem::class.java)
            assertThat(middleArray.annotationNames()).containsExactly("test.pkg.C")

            val innerArray = (middleArray as ArrayTypeItem).componentType
            assertThat(innerArray).isInstanceOf(ArrayTypeItem::class.java)
            assertThat(innerArray.annotationNames()).containsExactly("test.pkg.D")

            val componentType = (innerArray as ArrayTypeItem).componentType
            assertThat(componentType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((componentType as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Foo")
            assertThat(componentType.annotationNames()).containsExactly("test.pkg.A")
        }
    }

    @Test
    fun `Test annotations on multidimensional vararg array`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public void foo(test.pkg.@test.pkg.A Foo @test.pkg.B [] @test.pkg.C [] @test.pkg.D ... arg) {}
                    }
                """
                    .trimIndent()
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo {
                        method public void foo(test.pkg.@test.pkg.A Foo @test.pkg.B [] @test.pkg.C [] @test.pkg.D ...);
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val outerArray =
                codebase.assertClass("test.pkg.Foo").methods().single().parameters().single().type()
            assertThat(outerArray).isInstanceOf(ArrayTypeItem::class.java)
            assertThat((outerArray as ArrayTypeItem).isVarargs).isTrue()
            assertThat(outerArray.annotationNames()).containsExactly("test.pkg.B")

            val middleArray = outerArray.componentType
            assertThat(middleArray).isInstanceOf(ArrayTypeItem::class.java)
            assertThat(middleArray.annotationNames()).containsExactly("test.pkg.C")

            val innerArray = (middleArray as ArrayTypeItem).componentType
            assertThat(innerArray).isInstanceOf(ArrayTypeItem::class.java)
            assertThat(innerArray.annotationNames()).containsExactly("test.pkg.D")

            val componentType = (innerArray as ArrayTypeItem).componentType
            assertThat(componentType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((componentType as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Foo")
            assertThat(componentType.annotationNames()).containsExactly("test.pkg.A")
        }
    }

    @Test
    fun `Test inner parameterized types with annotations`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Outer<O> {
                        public class Inner<I> {
                        }

                        public <P1, P2> test.pkg.@test.pkg.A Outer<@test.pkg.B P1>.@test.pkg.C Inner<@test.pkg.D P2> foo() {
                            return new Outer<P1>.Inner<P2>();
                        }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 3.0
                    package test.pkg {
                      public class Outer<O> {
                        ctor public Outer();
                        method public <P1, P2> test.pkg.@test.pkg.A Outer<@test.pkg.B P1!>.@test.pkg.C Inner<@test.pkg.D P2!>! foo();
                      }
                      public class Outer.Inner<I> {
                        ctor public Outer.Inner();
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val method = codebase.assertClass("test.pkg.Outer").methods().single()
            val methodTypeParameters = method.typeParameterList().typeParameters()
            assertThat(methodTypeParameters).hasSize(2)
            val p1 = methodTypeParameters[0]
            val p2 = methodTypeParameters[1]

            // Outer<P1>.Inner<P2>
            val innerType = method.returnType()
            assertThat(innerType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((innerType as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Outer.Inner")
            assertThat(innerType.parameters).hasSize(1)
            assertThat(innerType.annotationNames()).containsExactly("test.pkg.C")

            val innerTypeParameter = innerType.parameters.single()
            assertThat(innerTypeParameter).isInstanceOf(VariableTypeItem::class.java)
            assertThat((innerTypeParameter as VariableTypeItem).name).isEqualTo("P2")
            assertThat(innerTypeParameter.asTypeParameter).isEqualTo(p2)
            assertThat(innerTypeParameter.annotationNames()).containsExactly("test.pkg.D")

            val outerType = innerType.outerClassType
            assertThat(outerType).isNotNull()
            assertThat(outerType!!.qualifiedName).isEqualTo("test.pkg.Outer")
            assertThat(outerType.outerClassType).isNull()
            assertThat(outerType.parameters).hasSize(1)
            assertThat(outerType.annotationNames()).containsExactly("test.pkg.A")

            val outerClassParameter = outerType.parameters.single()
            assertThat(outerClassParameter).isInstanceOf(VariableTypeItem::class.java)
            assertThat((outerClassParameter as VariableTypeItem).name).isEqualTo("P1")
            assertThat(outerClassParameter.asTypeParameter).isEqualTo(p1)
            assertThat(outerClassParameter.annotationNames()).containsExactly("test.pkg.B")
        }
    }

    @Test
    fun `Test interface types`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo implements test.pkg.@test.pkg.A Bar, test.pkg.Baz {}
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo implements test.pkg.@test.pkg.A Bar, test.pkg.Baz {
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val foo = codebase.assertClass("test.pkg.Foo")
            val interfaces = foo.interfaceTypes()
            assertThat(interfaces).hasSize(2)

            val bar = interfaces[0]
            assertThat(bar).isInstanceOf(ClassTypeItem::class.java)
            assertThat((bar as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Bar")
            val annotations = bar.modifiers.annotations()
            assertThat(annotations).hasSize(1)
            assertThat(annotations.single().qualifiedName).isEqualTo("test.pkg.A")

            val baz = interfaces[1]
            assertThat(baz).isInstanceOf(ClassTypeItem::class.java)
            assertThat((baz as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Baz")
        }
    }

    @Test
    fun `Test super class type`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo extends test.pkg.@test.pkg.A Bar {}
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo extends test.pkg.@test.pkg.A Bar {
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val foo = codebase.assertClass("test.pkg.Foo")
            val superClass = foo.superClassType()
            assertThat(superClass).isNotNull()
            assertThat(superClass).isInstanceOf(ClassTypeItem::class.java)
            assertThat((superClass as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Bar")
            assertThat(superClass.annotationNames()).containsExactly("test.pkg.A")
        }
    }

    @Test
    fun `Test super class and interface types of interface`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public interface Foo extends test.pkg.@test.pkg.A Bar, test.pkg.@test.pkg.B Baz<@test.pkg.C String>, test.pkg.Biz {}
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public interface Foo extends test.pkg.@test.pkg.A Bar test.pkg.@test.pkg.B Baz<@test.pkg.C String> test.pkg.Biz {
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val foo = codebase.assertClass("test.pkg.Foo")

            val bar = foo.superClassType()
            assertThat(bar).isNotNull()
            assertThat(bar).isInstanceOf(ClassTypeItem::class.java)
            assertThat((bar as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Bar")
            assertThat(bar.annotationNames()).containsExactly("test.pkg.A")

            val interfaces = foo.interfaceTypes()
            assertThat(interfaces).hasSize(2)

            val baz = interfaces[0]
            assertThat(baz).isInstanceOf(ClassTypeItem::class.java)
            assertThat((baz as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Baz")
            assertThat(baz.parameters).hasSize(1)
            assertThat(baz.annotationNames()).containsExactly("test.pkg.B")

            val bazParam = baz.parameters.single()
            assertThat(bazParam.isString()).isTrue()
            assertThat(bazParam.annotationNames()).containsExactly("test.pkg.C")

            val biz = interfaces[1]
            assertThat(biz).isInstanceOf(ClassTypeItem::class.java)
            assertThat((biz as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Biz")
            assertThat(biz.annotationNames()).isEmpty()
        }
    }

    @Test
    fun `Test annotated array types in multiple contexts`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public test.pkg.Foo @test.pkg.A [] method(test.pkg.Foo @test.pkg.A [] arg) {}
                        public test.pkg.Foo @test.pkg.A [] field;
                    }
                """
            ),
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo {
                        method public test.pkg.Foo @test.pkg.A [] method(test.pkg.Foo @test.pkg.A []);
                        field public test.pkg.Foo @test.pkg.A [] field;
                        property public test.pkg.Foo @test.pkg.A [] prop;
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val foo = codebase.assertClass("test.pkg.Foo")
            val method = foo.methods().single()
            val returnType = method.returnType()
            val paramType = method.parameters().single().type()
            val fieldType = foo.fields().single().type()
            // Properties can't be defined in java, this is only present for signature type
            val propertyType = foo.properties().singleOrNull()?.type()

            // Do full check for one type, then verify the others are equal
            assertThat(returnType).isInstanceOf(ArrayTypeItem::class.java)
            assertThat(returnType.annotationNames()).containsExactly("test.pkg.A")
            val componentType = (returnType as ArrayTypeItem).componentType
            assertThat(componentType).isInstanceOf(ClassTypeItem::class.java)
            assertThat((componentType as ClassTypeItem).qualifiedName).isEqualTo("test.pkg.Foo")
            assertThat(componentType.annotationNames()).isEmpty()

            assertThat(returnType).isEqualTo(paramType)
            assertThat(returnType).isEqualTo(fieldType)
            if (propertyType != null) {
                assertThat(returnType).isEqualTo(propertyType)
            }
        }
    }
}
