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

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.testing.testTypeString
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
            inputSet(
                kotlin(
                    """
                    @file:JvmName("Foo")
                    package test.pkg

                    @JvmInline value class IntValue(val value: Int)

                    var IntValue.valueClassExtension
                        get() = 0
                        set(value) {}
                """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/31VeTQUahufGEyWDDMZcuuTyAwZY7sIlRgZGlkmsq/F3MFY" +
                        "xtUd+9Ike3TlypKJUbh2siS6do09W5Y0TLJl38ro6n7nfJ86t573/P54z3nf" +
                        "3/O+z3me38/IgB0IBYBAIAAAcBJwMKAAIACLxmnJYgx15bBahhhdtCkOidXd" +
                        "6wAA1rD0l5cNZJG9fAay0l307jIT1CvFSaY3Uh97BoPt9c0rN1nWl/WS1qfT" +
                        "ZcyWu+Ta2+lTTAaTDWBkwAUqgiCK1PYTqOzD6LvpYfsgXfchyXkSXOQwHiQz" +
                        "Bzff60gnNwcfn0Bct88oDrw3Ueb2sjSxzfBBi37IXshgV/xrvhxoX4RSqAlH" +
                        "bmi2cJ+n8U+xj5qSYyX9yXS+PbYj1vaTDTlcKaES2i4pYI9dLG/jOUylYtxt" +
                        "kU5/4tZiUO2nxXMs1tJSMKAqWk7YJINJ3mnRkiSq2rYXVBaoCodXEis7AwZI" +
                        "0jsb6YH5C80CDgkyZvaN4PZ3vBRX9YSULPEjaofigOHTiyPIRtcVtk2NxgTz" +
                        "N+uplaZ/VBBipRuZBXdlNqkcfs5+OXZPx0rza80U4wQow+yjJXXo87GJUdA+" +
                        "bqUYhRiS+HUWXcjZ+jShrLc4zYYystCsBwu/HfruqvWLqoZO1OUjHavP3kag" +
                        "0qAUmc/E7TxUQGzVwnKDJ0I95NDRIU+b4Qmxi503ch46ghq55QOS9MYUx+t+" +
                        "/1h4NBFuTH4Vh+O/dbNYOuxPBPdAiZl8eFoYnp7XFgW5lHESHziikO8BUk5W" +
                        "jmJ1ZnZrOhlZdhmyqok5DGxTxJbNsdCapy7KIn2wusgdgCXpN/DjKPUUiOoS" +
                        "p+Q9qB412rL6Q5Vt2dXNuU6UbDVktBulYl/eJKwBtufHnxjlnasu5b7nMojS" +
                        "EXnmdemMT4SpxmP/q6PtS08JOwO/dnrqgU+lzlkXWGRUE8inZ2/+eTxLpbEp" +
                        "EcwD7zg0mzl6kQoIuB8SrKH5rHDZq9Bzcd0oK55Q+hcTvoh57VTO39/FLCtK" +
                        "brnCP3MzVDY+2+nY0G8nvTe1LydKytbkERTa76hZmJLwaUeHp+qfH/ZISq3J" +
                        "w2v/lUqiD/El3abFJhjOz5lYkk8NoH9HQGwSnSx3Fwgt6DzcuaMJCwl+nxoe" +
                        "HYkUyJLCSQhllP+ibrb2pNq0Qocwf73MUuFBZYI2s4fmksw/SNeFyzzYMCtz" +
                        "0yy9lTDiqpZhU6ZfSehf1G594k9zMTV/MpWWeoaa2ywXbkj1Mm7SMxVHyEXm" +
                        "aJ7dkLkHI01uxwymFN1brl6aFBXDSM1RbsKy1kadZT8oyGW5H58yIS+AaoIi" +
                        "zMvHBApPvN3IZLVpJvBOLPvYMypuiak08jrffXHYY+NT23jDOuVipmh6wQZk" +
                        "LKh3BrzI+cds8ggsu2epn+fiFYl1i9y5puLZ/uCw4GjX6eZ1xLbAhGWPVD2E" +
                        "Cl2uQBN+0ljhp7C3yZskWqVQVMdP5emwba0YgpAfxQsExbNmIfQtBe4xiIMu" +
                        "Y7qWYP2sqidQnmROiytBKa+I3rPj3EQ2mNRcizoPjEHBap1t+arfks/LaAiL" +
                        "yIZvFUMt34gbNtkwJLeTg2KYe0GSxQz1nCa7/Hq7dIQT+2mZ8K0VLbtATCDD" +
                        "vyknUwxNdr50ZyFnzVpyBZ599g4/66Nqxs67TMcHm5pnNWnjYkMD7jq8Ac4P" +
                        "Xe35rryP9GX/z1SSOwomOjzW8phnoNkU5hVsQ7rrI+Ar4OP1ZmjskR/Tsas5" +
                        "JrogmvDcu615OfiL+nBQTTjVgADAPNeP1EfwoProEon/FR53Yyyx/wI4kKAe" +
                        "t6Y1/3OOEgIK0tIXmCyXBt3hAQu0w8sFxXgQuVhHDqm9CyOx7fJSQSAXpLXa" +
                        "w8eXgqCOgxi1HtvNTvqDeJWy+s+7uxyTwC2Rjj5Y346GiFq6RHu4/LBThrfU" +
                        "9Eqb2nw8K5Su7aIqvNM39ZZOvG0dOeQz2yqux7g0ofGxL/WKcAzN/70CTnTV" +
                        "VOzYLBPHqbsbWlXTbVoUmBhnxVtNmz9BjrDgoaR/kLjrAFafUvKeyO5FxO/O" +
                        "KCFPO/dglBsH0V1Wdqsn76cJlsMH8BhaRhj5Atvpjp7INK4Oqtfr5ci3A94O" +
                        "sPvR1BtWIgt4dKfCHNYqHy+7eYEw20qrs4bPWMygZ6Z2hmLXiDeqb1yjpOeL" +
                        "JuNnruY60Si4wrG6Ppafq4qtTYhOMnKhFYJRr0g6uVg+wuf4mSS0wZrlmuBH" +
                        "lI5jOTXWt5WM9s5eC8ik09jrIfDcjx5CL8VW6y4Am57HTOl4Y5I4GvdSfabW" +
                        "BaNPuaJe+YLy215k83kkbRmlG6A3lkT8btsqRk973Z9YjZLsFi+ghjBqVjFC" +
                        "uQa720U9Hdx+5tM1RhaSpe+ljLtX+T3ji7F3sYlOWw9rh71LFqlhx2FDJfMM" +
                        "zk2yfVjLILkYqM/2iU2Fw+PwNPByaJiYJt8vOKqu/rxyaGz9l54JMvFOWD8E" +
                        "AGyz/ahnRPfxP8N0d8B7IAlEkhvew86d6Ozrdt3J3t7+xj6Ajoac8EnGDAOO" +
                        "2TdQmTZDmOOvL7vNHXscAf/Yo2hrOVNinwrxjz0eYoMC/p/uoHV+8eev43tu" +
                        "/S3LwRGAfcUQ/H3T/ZbkYE0EvyJhAf9tdr69f/Cbol/dl+f6YR2NDDg4vxwD" +
                        "7i/o/gMyuL7s/gZTKCjJyAgAAA=="
                )
        ) {
            val fileFacadeClass = codebase.assertClass("test.pkg.Foo")
            assertThat(fileFacadeClass.properties()).hasSize(1)

            // extension property has getter and setter, but no backing field
            val valueClassExtension = fileFacadeClass.assertProperty("valueClassExtension")
            fileFacadeClass.assertMethod("getValueClassExtension-Vxmw0xk", "int")
            fileFacadeClass.assertMethod("setValueClassExtension-6VC4vj0", "int,int")
            assertThat(valueClassExtension.backingField).isNull()
            assertThat(valueClassExtension.constructorParameter).isNull()

            // the extension property receiver is a value class type
            valueClassExtension.receiver.assertClassTypeItem {
                assertEquals(qualifiedName, "test.pkg.IntValue")
            }
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

    @Test
    fun `Test property receivers`() {
        runCodebaseTest(
            kotlin(
                """
                    @file:JvmName("Foo")
                    package test.pkg
                    val noReceiverProperty = 0
                    // Extension properties can't have backing fields, so they need defined getters
                    val Int.intProperty
                        get() = 0
                    val String.stringProperty
                        get() = 0
                    val Array<String>.stringArrayProperty
                        get() = 0
                    val List<String>.stringListProperty
                        get() = 0
                """
            ),
            // Skip getters in the signature file since they aren't important to the test
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo {
                        property public static int noReceiverProperty;
                        property public static int int.intProperty;
                        property public static int String.stringProperty;
                        property public static int String[].stringArrayProperty;
                        property public static int java.util.List<String>.stringListProperty;
                      }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public final class Foo {
                        property public static noReceiverProperty: int;
                        property public static int.intProperty: int;
                        property public static String.stringProperty: int;
                        property public static String[].stringArrayProperty: int;
                        property public static java.util.List<String>.stringListProperty: int;
                      }
                    }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            assertNull(fooClass.assertProperty("noReceiverProperty").receiver)

            fooClass.assertProperty("intProperty").receiver.assertPrimitiveTypeItem {
                assertEquals(kind, PrimitiveTypeItem.Primitive.INT)
            }

            fooClass.assertProperty("stringProperty").receiver.assertClassTypeItem {
                assertTrue(isString())
            }

            fooClass.assertProperty("stringArrayProperty").receiver.assertArrayTypeItem {
                componentType.assertClassTypeItem { assertTrue(isString()) }
            }

            fooClass.assertProperty("stringListProperty").receiver.assertClassTypeItem {
                assertEquals(qualifiedName, "java.util.List")
                arguments.single().assertClassTypeItem { assertTrue(isString()) }
            }
        }
    }

    @Test
    fun `Test property type parameters, receiver types`() {
        runCodebaseTest(
            kotlin(
                """
                    @file:JvmName("Foo")
                    package test.pkg
                    val String.noTypeParameterProperty = 0
                    // Property type parameters must be used in the receiver type
                    // Extension properties can't have backing fields, so they need defined getters
                    val <T> T.oneTypeParameterReceiver
                        get() = 0
                    val <T> List<T>.oneTypeParameterListReceiver
                        get() = 0
                    val <T : String> T.oneTypeParameterWithBoundsReceiver
                        get() = 0
                    val <T1, T2> Map<T1, T2>.twoTypeParameterMapReceiver
                        get() = 0
                    val <T1 : String, T2 : List<T1>> Map<T1, T2>.twoTypeParameterWithBoundsMapReceiver
                        get() = 0
                """
            ),
            // Skip getters in the signature file since they aren't important to the test
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo {
                        property public static int String.noTypeParameterProperty;
                        property public static <T> int T.oneTypeParameterReceiver;
                        property public static <T> int java.util.List<T>.oneTypeParameterListReceiver;
                        property public static <T extends String> int T.oneTypeParameterWithBoundsReceiver;
                        property public static <T1, T2> int java.util.Map<T1,T2>.twoTypeParameterMapReceiver;
                        property public static <T1 extends String, T2 extends java.util.List<T1>> int java.util.Map<T1,T2>.twoTypeParameterWithBoundsMapReceiver;
                      }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public final class Foo {
                        property public static String.noTypeParameterProperty: int;
                        property public static <T> T.oneTypeParameterReceiver: int;
                        property public static <T> java.util.List<T>.oneTypeParameterListReceiver: int;
                        property public static <T extends String> T.oneTypeParameterWithBoundsReceiver: int;
                        property public static <T1, T2> java.util.Map<T1,T2>.twoTypeParameterMapReceiver: int;
                        property public static <T1 extends String, T2 extends java.util.List<T1>> java.util.Map<T1,T2>.twoTypeParameterWithBoundsMapReceiver: int;
                      }
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val noTypeParameterProperty = fooClass.assertProperty("noTypeParameterProperty")
            assertThat(noTypeParameterProperty.typeParameterList).isEmpty()

            // val <T> T.oneTypeParameterReceiver
            val oneTypeParameterReceiver = fooClass.assertProperty("oneTypeParameterReceiver")
            val oneTypeParameterReceiverT = oneTypeParameterReceiver.typeParameterList.single()
            assertThat(oneTypeParameterReceiverT.name()).isEqualTo("T")
            assertThat(oneTypeParameterReceiverT.typeBounds()).isEmpty()
            assertThat(oneTypeParameterReceiverT.isReified()).isFalse()
            oneTypeParameterReceiver.receiver.assertVariableTypeItem {
                assertEquals(asTypeParameter, oneTypeParameterReceiverT)
            }

            // val <T> List<T>.oneTypeParameterListReceiver
            val oneTypeParameterListReceiver =
                fooClass.assertProperty("oneTypeParameterListReceiver")
            val oneTypeParameterListReceiverT =
                oneTypeParameterListReceiver.typeParameterList.single()
            assertThat(oneTypeParameterListReceiverT.name()).isEqualTo("T")
            assertThat(oneTypeParameterListReceiverT.typeBounds()).isEmpty()
            assertThat(oneTypeParameterListReceiverT.isReified()).isFalse()
            oneTypeParameterListReceiver.receiver.assertClassTypeItem {
                assertEquals(qualifiedName, "java.util.List")
                arguments.single().assertVariableTypeItem {
                    assertEquals(asTypeParameter, oneTypeParameterListReceiverT)
                }
            }

            // val <T : String> T.oneTypeParameterWithBoundsReceiver
            val oneTypeParameterWithBoundsReceiver =
                fooClass.assertProperty("oneTypeParameterWithBoundsReceiver")
            val oneTypeParameterWithBoundsReceiverT =
                oneTypeParameterWithBoundsReceiver.typeParameterList.single()
            assertThat(oneTypeParameterWithBoundsReceiverT.name()).isEqualTo("T")
            assertThat(oneTypeParameterWithBoundsReceiverT.typeBounds().single().isString())
                .isTrue()
            assertThat(oneTypeParameterWithBoundsReceiverT.isReified()).isFalse()
            oneTypeParameterWithBoundsReceiver.receiver.assertVariableTypeItem {
                assertEquals(asTypeParameter, oneTypeParameterReceiverT)
            }

            // val <T1, T2> Map<T1, T2>.twoTypeParameterMapReceiver
            val twoTypeParameterMapReceiver = fooClass.assertProperty("twoTypeParameterMapReceiver")
            val twoTypeParameterMapReceiverT1 = twoTypeParameterMapReceiver.typeParameterList[0]
            assertThat(twoTypeParameterMapReceiverT1.name()).isEqualTo("T1")
            assertThat(twoTypeParameterMapReceiverT1.typeBounds()).isEmpty()
            assertThat(twoTypeParameterMapReceiverT1.isReified()).isFalse()
            val twoTypeParameterMapReceiverT2 = twoTypeParameterMapReceiver.typeParameterList[1]
            assertThat(twoTypeParameterMapReceiverT2.name()).isEqualTo("T2")
            assertThat(twoTypeParameterMapReceiverT2.typeBounds()).isEmpty()
            assertThat(twoTypeParameterMapReceiverT2.isReified()).isFalse()
            twoTypeParameterMapReceiver.receiver.assertClassTypeItem {
                assertEquals(qualifiedName, "java.util.Map")
                assertEquals(arguments.size, 2)
                arguments[0].assertVariableTypeItem {
                    assertEquals(asTypeParameter, twoTypeParameterMapReceiverT1)
                }
                arguments[1].assertVariableTypeItem {
                    assertEquals(asTypeParameter, twoTypeParameterMapReceiverT2)
                }
            }

            // val <T1 : String, T2 : List<T1>> Map<T1, T2>.twoTypeParameterWithBoundsMapReceiver
            val twoTypeParameterWithBoundsMapReceiver =
                fooClass.assertProperty("twoTypeParameterWithBoundsMapReceiver")
            val twoTypeParameterWithBoundsMapReceiverT1 =
                twoTypeParameterWithBoundsMapReceiver.typeParameterList[0]
            assertThat(twoTypeParameterWithBoundsMapReceiverT1.name()).isEqualTo("T1")
            assertThat(twoTypeParameterWithBoundsMapReceiverT1.typeBounds().single().isString())
                .isTrue()
            assertThat(twoTypeParameterWithBoundsMapReceiverT1.isReified()).isFalse()
            val twoTypeParameterWithBoundsMapReceiverT2 =
                twoTypeParameterWithBoundsMapReceiver.typeParameterList[1]
            assertThat(twoTypeParameterWithBoundsMapReceiverT2.name()).isEqualTo("T2")
            twoTypeParameterWithBoundsMapReceiverT2.typeBounds().single().assertClassTypeItem {
                assertEquals(qualifiedName, "java.util.List")
                arguments.single().assertVariableTypeItem {
                    assertEquals(asTypeParameter, twoTypeParameterWithBoundsMapReceiverT1)
                }
            }
            assertThat(twoTypeParameterWithBoundsMapReceiverT2.isReified()).isFalse()
            twoTypeParameterWithBoundsMapReceiver.receiver.assertClassTypeItem {
                assertEquals(qualifiedName, "java.util.Map")
                assertEquals(arguments.size, 2)
                arguments[0].assertVariableTypeItem {
                    assertEquals(asTypeParameter, twoTypeParameterWithBoundsMapReceiverT1)
                }
                arguments[1].assertVariableTypeItem {
                    assertEquals(asTypeParameter, twoTypeParameterWithBoundsMapReceiverT2)
                }
            }
        }
    }

    @Test
    fun `Test property type parameters, property type`() {
        runCodebaseTest(
            kotlin(
                """
                    @file:JvmName("Foo")
                    package test.pkg
                    val <T> T.typeParameterExtension
                        get() = this
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo {
                        method public static <T> T getTypeParameterExtension(T);
                        property public static <T> T T.typeParameterExtension;
                      }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public final class Foo {
                        method public static <T> getTypeParameterExtension(receiver: T): T;
                        property public static <T> T.typeParameterExtension: T;
                      }
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            // Verify that the type parameter list is also used for the property type
            val typeParameterExtension = fooClass.assertProperty("typeParameterExtension")
            val typeParameterExtensionT = typeParameterExtension.typeParameterList.single()
            assertThat(typeParameterExtensionT.name()).isEqualTo("T")
            assertThat(typeParameterExtensionT.typeBounds()).isEmpty()
            assertThat(typeParameterExtensionT.isReified()).isFalse()
            typeParameterExtension.type().assertVariableTypeItem {
                assertEquals(asTypeParameter, typeParameterExtensionT)
            }
        }
    }

    @Test
    fun `Test type of primitive property overriding type parameter type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    interface Parent<T> {
                        val foo: T
                    }
                    class Child: Parent<Int> {
                        override val foo: Int = 0
                    }
                """
            )
        ) {
            val childClass = codebase.assertClass("test.pkg.Child")
            val fooProperty = childClass.assertProperty("foo")
            // Since foo overrides of a property with type T, the primitive type needs to be boxed.
            fooProperty.type().assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("java.lang.Integer")
            }
        }
    }
}
