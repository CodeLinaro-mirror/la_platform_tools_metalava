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
import com.android.tools.metalava.model.testing.testTypeString
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
            val property = barClass.assertProperty("foo")
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
                assertThat(testTypeString(kotlinStyleNulls = true))
                    .isEqualTo(
                        "kotlin.reflect.KProperty0<? extends java.util.List<? extends java.lang.String?>>"
                    )
            }

            val propertyItem = fooClass.properties().single()
            assertThat(propertyItem.name()).isEqualTo("delegatingList")
            val propertyType = propertyItem.type()
            propertyType.assertClassTypeItem {
                assertThat(testTypeString(kotlinStyleNulls = true))
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
                assertThat(testTypeString(kotlinStyleNulls = true))
                    .isEqualTo(
                        "kotlin.reflect.KProperty0<? extends kotlin.jvm.functions.Function2<? super java.lang.Integer,? super java.lang.String?,? extends java.lang.Boolean>>"
                    )
            }

            val propertyItem = fooClass.properties().single()
            assertThat(propertyItem.name()).isEqualTo("delegatingList")
            val propertyType = propertyItem.type()
            propertyType.assertClassTypeItem {
                assertThat(testTypeString(kotlinStyleNulls = true))
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
                assertThat(testTypeString(kotlinStyleNulls = true)).isEqualTo("java.lang.String")
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
                assertThat(testTypeString(kotlinStyleNulls = true)).isEqualTo("java.lang.String?")
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
                assertThat(testTypeString(kotlinStyleNulls = true))
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
                assertThat(testTypeString(kotlinStyleNulls = true))
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
                assertThat(testTypeString(kotlinStyleNulls = true)).isEqualTo("java.lang.String")
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
                assertThat(testTypeString(kotlinStyleNulls = true)).isEqualTo("java.lang.String?")
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
                assertThat(testTypeString(kotlinStyleNulls = true))
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
                assertThat(testTypeString(kotlinStyleNulls = true))
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

    @Test
    fun `Test companion property`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class Foo {
                        companion object {
                            val value: Int = 0
                            const val constant: Int = 1
                            @JvmField val jvmField: Int = 2
                        }
                    }
                """
            )
        ) {
            val foo = codebase.assertClass("test.pkg.Foo")
            assertThat(foo.methods()).isEmpty()
            assertThat(foo.properties()).isEmpty()
            foo.assertField("constant")
            foo.assertField("jvmField")

            val fooCompanion = codebase.assertClass("test.pkg.Foo.Companion")
            assertThat(fooCompanion.fields()).isEmpty()
            assertThat(fooCompanion.methods()).hasSize(1)
            val valueGetterOnCompanion = fooCompanion.assertMethod("getValue", "")

            assertThat(fooCompanion.properties()).hasSize(3)
            val constantPropertyOnCompanion = fooCompanion.assertProperty("constant")
            val jvmPropertyOnCompanion = fooCompanion.assertProperty("jvmField")
            val valuePropertyOnCompanion = fooCompanion.assertProperty("value")

            assertThat(jvmPropertyOnCompanion.getter).isNull()
            assertThat(constantPropertyOnCompanion.getter).isNull()
            assertThat(valuePropertyOnCompanion.getter).isEqualTo(valueGetterOnCompanion)
        }
    }

    @Test
    fun `Test top level properties`() {
        runCodebaseTest(
            kotlin(
                """
                    @file:JvmName("Foo")
                    package test.pkg

                    var variable = 0

                    val valWithNoBackingField
                        get() = 0

                    const val CONST = 0

                    @JvmField
                    val jvmField = 0
                """
            )
        ) {
            val fileFacadeClass = codebase.assertClass("test.pkg.Foo")
            assertThat(fileFacadeClass.properties()).hasSize(4)

            // var property with getter, setter, and backing field
            val variable = fileFacadeClass.assertProperty("variable")
            assertThat(variable.getter).isNotNull()
            assertThat(variable.setter).isNotNull()
            assertThat(variable.backingField).isNotNull()
            assertThat(variable.constructorParameter).isNull()

            // val property with getter, no setter or backing field
            val valWithNoBackingField = fileFacadeClass.assertProperty("valWithNoBackingField")
            assertThat(valWithNoBackingField.getter).isNotNull()
            assertThat(valWithNoBackingField.setter).isNull()
            assertThat(valWithNoBackingField.backingField).isNull()
            assertThat(valWithNoBackingField.constructorParameter).isNull()

            // const val doesn't have accessors, but does have backing field
            val constVal = fileFacadeClass.assertProperty("CONST")
            assertThat(constVal.getter).isNull()
            assertThat(constVal.setter).isNull()
            assertThat(constVal.backingField).isNotNull()
            assertThat(constVal.constructorParameter).isNull()

            // jvmfield val doesn't have accessors, but does have backing field
            val jvmField = fileFacadeClass.assertProperty("jvmField")
            assertThat(jvmField.getter).isNull()
            assertThat(jvmField.setter).isNull()
            assertThat(jvmField.backingField).isNotNull()
            assertThat(jvmField.constructorParameter).isNull()
        }
    }

    @Test
    fun `Test top level extension properties`() {
        runCodebaseTest(
            kotlin(
                """
                    @file:JvmName("Foo")
                    package test.pkg

                    var String.stringExtension
                        get() = 0
                        set(value) {}
                """
            )
        ) {
            val fileFacadeClass = codebase.assertClass("test.pkg.Foo")
            assertThat(fileFacadeClass.properties()).hasSize(1)

            // extension property has getter and setter, but no backing field
            val stringExtension = fileFacadeClass.assertProperty("stringExtension")
            assertThat(stringExtension.getter).isNotNull()
            assertThat(stringExtension.setter).isNotNull()
            assertThat(stringExtension.backingField).isNull()
            assertThat(stringExtension.constructorParameter).isNull()
        }
    }

    @Test
    fun `Value class extension properties`() {
        runCodebaseTest(
            kotlin(
                """
                    @file:JvmName("Foo")
                    package test.pkg

                    value class IntValue(val value: Int)

                    var IntValue.valueClassExtension
                        get() = 0
                        set(value) {}
                """
            )
        ) {
            val fileFacadeClass = codebase.assertClass("test.pkg.Foo")
            assertThat(fileFacadeClass.properties()).hasSize(1)

            // extension property has getter and setter, but no backing field
            val valueClassExtension = fileFacadeClass.assertProperty("valueClassExtension")
            assertThat(valueClassExtension.getter).isNotNull()
            assertThat(valueClassExtension.setter).isNotNull()
            assertThat(valueClassExtension.backingField).isNull()
            assertThat(valueClassExtension.constructorParameter).isNull()
        }
    }

    fun `Test final modifier for properties`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class FinalClass {
                        val propertyInFinalClass = 0
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    open class OpenClass {
                        val finalPropertyInOpenClass = 0
                        open val openPropertyInOpenClass = 0
                    }
                """
            )
        ) {
            val finalClass = codebase.assertClass("test.pkg.FinalClass")
            assertThat(finalClass.modifiers.isFinal()).isTrue()
            // Properties in final classes are final, so using the modifier would be redundant.
            assertThat(finalClass.assertProperty("propertyInFinalClass").modifiers.isFinal())
                .isFalse()

            val openClass = codebase.assertClass("test.pkg.OpenClass")
            assertThat(openClass.modifiers.isFinal()).isFalse()
            assertThat(openClass.assertProperty("finalPropertyInOpenClass").modifiers.isFinal())
                .isTrue()
            assertThat(openClass.assertProperty("openPropertyInOpenClass").modifiers.isFinal())
                .isFalse()
        }
    }

    @Test
    fun `JvmStatic property in object is static`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    import kotlin.jvm.JvmStatic
                    object Foo {
                        @JvmStatic
                        val jvmStaticProperty = 0
                        val notStaticProperty = 0
                    }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val jvmStaticProperty = fooClass.assertProperty("jvmStaticProperty")
            assertThat(jvmStaticProperty.modifiers.isStatic()).isTrue()
            val notStaticProperty = fooClass.assertProperty("notStaticProperty")
            assertThat(notStaticProperty.modifiers.isStatic()).isFalse()
        }
    }

    @Test
    fun `Test abstract and default modifier on properties`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    interface Interface {
                        // interface properties cannot have initializers
                        // if no getter is defined, the property is abstract
                        val abstractVal: Int
                        // getter is defined, the property is default
                        val defaultVal: Int
                            get() = 0

                        companion object {
                            // this shouldn't be default because the immediate container is an
                            // object, not an interface
                            val interfaceCompanionValWithGetter: Int
                                get() = 0
                        }
                    }

                    abstract class AbstractClass {
                        abstract val abstractVal: Int

                        val nonAbstractValWithInitializer: Int = 0
                        val nonAbstractValWithGetter: Int
                            get() = 0

                        // lateinit cannot be paired with abstract
                        lateinit var nonAbstractLateinitVar: String

                        val notAbstractValInInitBlock: Int
                        init {
                            notAbstractValInInitBlock = 0
                        }
                    }
                """
            )
        ) {
            val interfaceClass = codebase.assertClass("test.pkg.Interface")
            val abstractInterfaceProperty = interfaceClass.assertProperty("abstractVal")
            assertThat(abstractInterfaceProperty.modifiers.isAbstract()).isTrue()
            assertThat(abstractInterfaceProperty.modifiers.isDefault()).isFalse()
            val defaultInterfaceProperty = interfaceClass.assertProperty("defaultVal")
            assertThat(defaultInterfaceProperty.modifiers.isAbstract()).isFalse()
            assertThat(defaultInterfaceProperty.modifiers.isDefault()).isTrue()

            val interfaceCompanion = interfaceClass.nestedClasses().single()
            val interfaceCompanionProperty =
                interfaceCompanion.assertProperty("interfaceCompanionValWithGetter")
            assertThat(interfaceCompanionProperty.modifiers.isAbstract()).isFalse()
            assertThat(interfaceCompanionProperty.modifiers.isDefault()).isFalse()

            val abstractClass = codebase.assertClass("test.pkg.AbstractClass")
            val abstractClassProperty = abstractClass.assertProperty("abstractVal")
            assertThat(abstractClassProperty.modifiers.isAbstract()).isTrue()
            assertThat(abstractClassProperty.modifiers.isDefault()).isFalse()

            val nonAbstractPropertiesFromAbstractClass =
                listOf(
                    abstractClass.assertProperty("nonAbstractValWithInitializer"),
                    abstractClass.assertProperty("nonAbstractValWithGetter"),
                    abstractClass.assertProperty("nonAbstractLateinitVar"),
                    abstractClass.assertProperty("notAbstractValInInitBlock"),
                )

            for (property in nonAbstractPropertiesFromAbstractClass) {
                assertThat(property.modifiers.isAbstract()).isFalse()
                assertThat(property.modifiers.isDefault()).isFalse()
            }
        }
    }
}
