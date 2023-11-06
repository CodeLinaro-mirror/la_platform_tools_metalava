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
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.TestParameters
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CommonTypeParameterItemTest(parameters: TestParameters) : BaseModelTest(parameters) {
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
                    // Signature format: 3.0
                    package test.pkg {
                      public class Foo<T> {
                        ctor public Foo();
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val typeParameter = fooClass.typeParameterList().typeParameters().single()
            val typeBounds = typeParameter.typeBounds()
            assertThat(typeBounds.size).isEqualTo(0)
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
                    // Signature format: 3.0
                    package test.pkg {
                      public class Foo<T extends Comparable<T>> {
                        ctor public Foo();
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val typeParameter = fooClass.typeParameterList().typeParameters().single()
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
                    class Foo<T: Object & Comparable<T>>
                """
            ),
            signature(
                """
                    // Signature format: 3.0
                    package test.pkg {
                      public class Foo<T extends Object & Comparable<T>> {
                        ctor public Foo();
                      }
                    }
                """
                    .trimIndent()
            )
        ) { codebase ->
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val typeParameter = fooClass.typeParameterList().typeParameters().single()
            val typeBounds = typeParameter.typeBounds()
            assertThat(typeBounds.size).isEqualTo(2)
            val (first, second) = typeBounds
            assertThat(first).isInstanceOf(ClassTypeItem::class.java)
            assertThat((first as ClassTypeItem).qualifiedName).isEqualTo("java.lang.Object")
            assertThat(second).isInstanceOf(ClassTypeItem::class.java)
            assertThat((second as ClassTypeItem).qualifiedName).isEqualTo("java.lang.Comparable")
        }
    }
}
