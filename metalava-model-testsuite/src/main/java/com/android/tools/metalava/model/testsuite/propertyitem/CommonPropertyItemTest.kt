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

package com.android.tools.metalava.model.testsuite.propertyitem

import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test

/** Common tests for implementations of [PropertyItem]. */
class CommonPropertyItemTest : BaseModelTest() {

    @Test
    fun `Test access type parameter of outer class`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Outer<O> {
                      }
                      public class Outer.Middle {
                      }
                      public abstract class Outer.Middle.Inner {
                        property public abstract O property;
                      }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Outer<O> private constructor() {
                        inner class Middle private constructor() {
                            abstract inner class Inner private constructor() {
                                abstract val property: O
                            }
                        }
                    }
                """
            ),
        ) {
            val oTypeParameter = codebase.assertClass("test.pkg.Outer").typeParameterList.single()
            val propertyType =
                codebase
                    .assertClass("test.pkg.Outer.Middle.Inner")
                    .assertProperty("property")
                    .type()

            propertyType.assertReferencesTypeParameter(oTypeParameter)
        }
    }

    @Test
    fun `Test deprecated getter and setter by annotation`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    class Bar {
                        private var fooImpl: String = ""
                        @Deprecated("blah")
                        var foo: String
                            get() = fooImpl
                            @Deprecated("blah")
                            set(value) {fooImpl = value}
                    }
                """
            ),
        ) {
            val barClass = codebase.assertClass("test.pkg.Bar")
            val property = barClass.properties().single()
            val methods = barClass.methods()
            val getter = methods.single { it.name() == "getFoo" }
            val setter = methods.single { it.name() == "setFoo" }
            assertEquals("property originallyDeprecated", true, property.originallyDeprecated)
            assertEquals("getter originallyDeprecated", true, getter.originallyDeprecated)
            assertEquals("setter originallyDeprecated", true, setter.originallyDeprecated)
        }
    }

    @Test
    fun `Test property delegate to Kotlin object`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    import kotlin.properties.ReadOnlyProperty
                    import kotlin.reflect.KProperty
                    class Foo {
                        val field: String by object : ReadOnlyProperty<Foo, String> {
                            fun getValue(thisRef: T, property: KProperty<*>) = "foo"
                        }
                    }
                """
            ),
            // No signature file as it does not care about field values that are not constant
            // literals.
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fieldType = fooClass.fields().single().type()
            fieldType.assertClassTypeItem {
                // The type of the field is NOT `String` (that is the type of the property). The
                // type of the field is the property delegate.
                assertThat(qualifiedName).isEqualTo("kotlin.properties.ReadOnlyProperty")
            }

            val propertyType = fooClass.properties().single().type()
            propertyType.assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("java.lang.String")
            }
        }
    }

    @Test
    fun `Test property delegate to generic Kotlin object`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    val targetList: List<String?> = emptyList()
                    class Foo {
                        val delegatingList by ::targetList
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fieldItem = fooClass.fields().single()
            assertThat(fieldItem.name()).isEqualTo("delegatingList\$delegate")
            val fieldType = fieldItem.type()
            fieldType.assertClassTypeItem {
                assertThat(toTypeString(kotlinStyleNulls = true))
                    .isEqualTo(
                        "kotlin.reflect.KProperty0<? extends java.util.List<? extends java.lang.String?>>"
                    )
            }

            val propertyItem = fooClass.properties().single()
            assertThat(propertyItem.name()).isEqualTo("delegatingList")
            val propertyType = propertyItem.type()
            propertyType.assertClassTypeItem {
                assertThat(toTypeString(kotlinStyleNulls = true))
                    .isEqualTo("java.util.List<java.lang.String?>")
            }
        }
    }

    @Test
    fun `Test property delegate to lambda Kotlin object`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    val targetList: (Int, String?) -> Boolean = {}
                    class Foo {
                        val delegatingList by ::targetList
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fieldItem = fooClass.fields().single()
            assertThat(fieldItem.name()).isEqualTo("delegatingList\$delegate")
            val fieldType = fieldItem.type()
            fieldType.assertClassTypeItem {
                assertThat(toTypeString(kotlinStyleNulls = true))
                    .isEqualTo(
                        "kotlin.reflect.KProperty0<? extends kotlin.jvm.functions.Function2<? super java.lang.Integer,? super java.lang.String?,? extends java.lang.Boolean>>"
                    )
            }

            val propertyItem = fooClass.properties().single()
            assertThat(propertyItem.name()).isEqualTo("delegatingList")
            val propertyType = propertyItem.type()
            propertyType.assertClassTypeItem {
                assertThat(toTypeString(kotlinStyleNulls = true))
                    .isEqualTo(
                        "kotlin.jvm.functions.Function2<java.lang.Integer,java.lang.String?,java.lang.Boolean>"
                    )
            }
        }
    }

    @Test
    fun `Test abstract property of non-null string`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        val property: String
                             get() = ""
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val propertyType = fooClass.properties().single().type()
            propertyType.assertClassTypeItem {
                assertThat(toTypeString(kotlinStyleNulls = true)).isEqualTo("java.lang.String")
            }

            val getter = fooClass.methods().single()
            assertThat(getter.kotlinLikeDescription())
                .isEqualTo("fun getProperty(): java.lang.String")
        }
    }

    @Test
    fun `Test abstract property of nullable string`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        val property: String?
                             get() = null
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val propertyType = fooClass.properties().single().type()
            propertyType.assertClassTypeItem {
                assertThat(toTypeString(kotlinStyleNulls = true)).isEqualTo("java.lang.String?")
            }

            val getter = fooClass.methods().single()
            assertThat(getter.kotlinLikeDescription())
                .isEqualTo("fun getProperty(): java.lang.String?")
        }
    }

    @Test
    fun `Test abstract property of list of non-null string`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        val property: List<String>
                             get() = emptyList()
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val propertyType = fooClass.properties().single().type()
            propertyType.assertClassTypeItem {
                assertThat(toTypeString(kotlinStyleNulls = true))
                    .isEqualTo("java.util.List<java.lang.String>")
            }

            val getter = fooClass.methods().single()
            assertThat(getter.kotlinLikeDescription())
                .isEqualTo("fun getProperty(): java.util.List<java.lang.String>")
        }
    }

    @Test
    fun `Test abstract property of list of nullable string`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        val property: List<String?>
                             get() = emptyList()
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val propertyType = fooClass.properties().single().type()
            propertyType.assertClassTypeItem {
                assertThat(toTypeString(kotlinStyleNulls = true))
                    .isEqualTo("java.util.List<java.lang.String?>")
            }

            val getter = fooClass.methods().single()
            assertThat(getter.kotlinLikeDescription())
                .isEqualTo("fun getProperty(): java.util.List<java.lang.String?>")
        }
    }

    @Test
    fun `Test abstract mutable property of non-null string`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        var property: String
                            get() = ""
                            set(value) {field = value}
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val propertyType = fooClass.properties().single().type()
            propertyType.assertClassTypeItem {
                assertThat(toTypeString(kotlinStyleNulls = true)).isEqualTo("java.lang.String")
            }

            val methods =
                fooClass.methods().map { it.kotlinLikeDescription() }.sorted().joinToString("\n")
            assertThat(methods)
                .isEqualTo(
                    """
                        fun getProperty(): java.lang.String
                        fun setProperty(value: java.lang.String): void
                    """
                        .trimIndent()
                )
        }
    }

    @Test
    fun `Test abstract mutable property of nullable string`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        var property: String?
                            get() = null
                            set(value) {field = value}
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val propertyType = fooClass.properties().single().type()
            propertyType.assertClassTypeItem {
                assertThat(toTypeString(kotlinStyleNulls = true)).isEqualTo("java.lang.String?")
            }

            val methods =
                fooClass.methods().map { it.kotlinLikeDescription() }.sorted().joinToString("\n")
            assertThat(methods)
                .isEqualTo(
                    """
                        fun getProperty(): java.lang.String?
                        fun setProperty(value: java.lang.String?): void
                    """
                        .trimIndent()
                )
        }
    }

    @Test
    fun `Test abstract mutable property of list of non-null string`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        var property: List<String>
                            get() = emptyList()
                            set(value) {field = value}
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val propertyType = fooClass.properties().single().type()
            propertyType.assertClassTypeItem {
                assertThat(toTypeString(kotlinStyleNulls = true))
                    .isEqualTo("java.util.List<java.lang.String>")
            }

            val methods =
                fooClass.methods().map { it.kotlinLikeDescription() }.sorted().joinToString("\n")
            assertThat(methods)
                .isEqualTo(
                    """
                        fun getProperty(): java.util.List<java.lang.String>
                        fun setProperty(value: java.util.List<java.lang.String>): void
                    """
                        .trimIndent()
                )
        }
    }

    @Test
    fun `Test abstract mutable property of list of nullable string`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        var property: List<String?>
                            get() = emptyList()
                            set(value) {field = value}
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val propertyType = fooClass.properties().single().type()
            propertyType.assertClassTypeItem {
                assertThat(toTypeString(kotlinStyleNulls = true))
                    .isEqualTo("java.util.List<java.lang.String?>")
            }

            val methods =
                fooClass.methods().map { it.kotlinLikeDescription() }.sorted().joinToString("\n")
            assertThat(methods)
                .isEqualTo(
                    """
                        fun getProperty(): java.util.List<java.lang.String?>
                        fun setProperty(value: java.util.List<java.lang.String?>): void
                    """
                        .trimIndent()
                )
        }
    }

    @Test
    fun `Test mutable non-null generic property overriding property exposing public setter`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    abstract class Baz<T> {
                        abstract var property: T
                            internal set
                    }

                    class Foo<T>(initialValue: T) : Baz<T> {
                        override var property: T = initialValue
                            public set
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val methods =
                fooClass.methods().map { it.kotlinLikeDescription() }.sorted().joinToString("\n")
            assertThat(methods)
                .isEqualTo(
                    """
                        fun getProperty(): T
                        fun setProperty(<set-?>: T): void
                    """
                        .trimIndent()
                )
        }
    }

    @Test
    fun `Test mutable nullable generic property overriding property exposing public setter`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    abstract class Baz<T> {
                        abstract var property: T?
                            internal set
                    }

                    class Foo<T> : Baz<T> {
                        override var property: T? = null
                            public set
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val methods =
                fooClass.methods().map { it.kotlinLikeDescription() }.sorted().joinToString("\n")
            assertThat(methods)
                .isEqualTo(
                    """
                        fun getProperty(): T?
                        fun setProperty(<set-?>: T?): void
                    """
                        .trimIndent()
                )
        }
    }

    @Test
    fun `Test mutable list of nullable property overriding property exposing public setter`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    abstract class Baz {
                        abstract var property: List<String?>
                            internal set
                    }

                    class Foo : Baz<T> {
                        override var property: List<String?> = emptyList()
                            public set
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val methods =
                fooClass.methods().map { it.kotlinLikeDescription() }.sorted().joinToString("\n")
            assertThat(methods)
                .isEqualTo(
                    """
                        fun getProperty(): java.util.List<java.lang.String?>
                        fun setProperty(<set-?>: java.util.List<java.lang.String?>): void
                    """
                        .trimIndent()
                )
        }
    }
}
