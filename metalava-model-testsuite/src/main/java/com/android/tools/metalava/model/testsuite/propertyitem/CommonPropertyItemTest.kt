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
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.testTypeString
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/** Common tests for implementations of [PropertyItem]. */
@SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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
            val valueGetterOnCompanion = fooCompanion.assertMethod("getValue", emptyList())

            assertThat(fooCompanion.properties()).hasSize(3)
            val constantPropertyOnCompanion = fooCompanion.assertProperty("constant")
            val jvmPropertyOnCompanion = fooCompanion.assertProperty("jvmField")
            val valuePropertyOnCompanion = fooCompanion.assertProperty("value")

            assertThat(jvmPropertyOnCompanion.getter).isNull()
            assertThat(constantPropertyOnCompanion.getter).isNull()
            assertThat(valuePropertyOnCompanion.getter).isEqualTo(valueGetterOnCompanion)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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
            val stringExtension =
                fileFacadeClass.assertProperty(
                    "stringExtension",
                    receiverTypeString = "java.lang.String"
                )
            assertThat(stringExtension.getter).isNotNull()
            assertThat(stringExtension.setter).isNotNull()
            assertThat(stringExtension.backingField).isNull()
            assertThat(stringExtension.constructorParameter).isNull()
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
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
            val valueClassExtension =
                fileFacadeClass.assertProperty(
                    "valueClassExtension",
                    receiverTypeString = "test.pkg.IntValue"
                )
            fileFacadeClass.assertMethod("getValueClassExtension-Vxmw0xk", listOf("int"))
            fileFacadeClass.assertMethod("setValueClassExtension-6VC4vj0", listOf("int", "int"))
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
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

            fooClass
                .assertProperty("intProperty", receiverTypeString = "int")
                .receiver
                .assertPrimitiveTypeItem { assertEquals(kind, PrimitiveTypeItem.Primitive.INT) }

            fooClass
                .assertProperty("stringProperty", receiverTypeString = "java.lang.String")
                .receiver
                .assertClassTypeItem { assertTrue(isString()) }

            fooClass
                .assertProperty("stringArrayProperty", receiverTypeString = "java.lang.String[]")
                .receiver
                .assertArrayTypeItem {
                    componentType.assertClassTypeItem { assertTrue(isString()) }
                }

            fooClass
                .assertProperty(
                    "stringListProperty",
                    receiverTypeString = "java.util.List<java.lang.String>"
                )
                .receiver
                .assertClassTypeItem {
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

            val noTypeParameterProperty =
                fooClass.assertProperty(
                    "noTypeParameterProperty",
                    receiverTypeString = "java.lang.String"
                )
            assertThat(noTypeParameterProperty.typeParameterList).isEmpty()

            // val <T> T.oneTypeParameterReceiver
            val oneTypeParameterReceiver =
                fooClass.assertProperty("oneTypeParameterReceiver", receiverTypeString = "T")
            val oneTypeParameterReceiverT = oneTypeParameterReceiver.typeParameterList.single()
            assertThat(oneTypeParameterReceiverT.name()).isEqualTo("T")
            oneTypeParameterReceiverT.assertUsesDefaultTypeBounds()
            assertThat(oneTypeParameterReceiverT.isReified()).isFalse()
            oneTypeParameterReceiver.receiver.assertVariableTypeItem {
                assertEquals(asTypeParameter, oneTypeParameterReceiverT)
            }

            // val <T> List<T>.oneTypeParameterListReceiver
            val oneTypeParameterListReceiver =
                fooClass.assertProperty(
                    "oneTypeParameterListReceiver",
                    receiverTypeString = "java.util.List<T>"
                )
            val oneTypeParameterListReceiverT =
                oneTypeParameterListReceiver.typeParameterList.single()
            assertThat(oneTypeParameterListReceiverT.name()).isEqualTo("T")
            oneTypeParameterListReceiverT.assertUsesDefaultTypeBounds()
            assertThat(oneTypeParameterListReceiverT.isReified()).isFalse()
            oneTypeParameterListReceiver.receiver.assertClassTypeItem {
                assertEquals(qualifiedName, "java.util.List")
                arguments.single().assertVariableTypeItem {
                    assertEquals(asTypeParameter, oneTypeParameterListReceiverT)
                }
            }

            // val <T : String> T.oneTypeParameterWithBoundsReceiver
            val oneTypeParameterWithBoundsReceiver =
                fooClass.assertProperty(
                    "oneTypeParameterWithBoundsReceiver",
                    receiverTypeString = "T"
                )
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
            val twoTypeParameterMapReceiver =
                fooClass.assertProperty(
                    "twoTypeParameterMapReceiver",
                    receiverTypeString = "java.util.Map<T1,T2>"
                )
            val twoTypeParameterMapReceiverT1 = twoTypeParameterMapReceiver.typeParameterList[0]
            assertThat(twoTypeParameterMapReceiverT1.name()).isEqualTo("T1")
            twoTypeParameterMapReceiverT1.assertUsesDefaultTypeBounds()
            assertThat(twoTypeParameterMapReceiverT1.isReified()).isFalse()
            val twoTypeParameterMapReceiverT2 = twoTypeParameterMapReceiver.typeParameterList[1]
            assertThat(twoTypeParameterMapReceiverT2.name()).isEqualTo("T2")
            twoTypeParameterMapReceiverT2.assertUsesDefaultTypeBounds()
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
                fooClass.assertProperty(
                    "twoTypeParameterWithBoundsMapReceiver",
                    receiverTypeString = "java.util.Map<T1,T2>"
                )
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
            val typeParameterExtension =
                fooClass.assertProperty("typeParameterExtension", receiverTypeString = "T")
            val typeParameterExtensionT = typeParameterExtension.typeParameterList.single()
            assertThat(typeParameterExtensionT.name()).isEqualTo("T")
            typeParameterExtensionT.assertUsesDefaultTypeBounds()
            assertThat(typeParameterExtensionT.isReified()).isFalse()
            typeParameterExtension.type().assertVariableTypeItem {
                assertEquals(asTypeParameter, typeParameterExtensionT)
            }
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
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

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Test setter visibility`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                open class Foo {
                    val noSet = 0
                    var publicSet = 0
                    var protectedSetOnPublic = 0
                        protected set
                    var privateSetOnPublic = 0
                        private set
                    protected var protectedSet = 0
                    @PublishedApi
                    internal var internalSet = 0
                }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val noSet = fooClass.assertProperty("noSet")
            assertThat(noSet.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(noSet.setterVisibility).isNull()

            val publicSet = fooClass.assertProperty("publicSet")
            assertThat(publicSet.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(publicSet.setterVisibility).isEqualTo(VisibilityLevel.PUBLIC)

            val protectedSetOnPublic = fooClass.assertProperty("protectedSetOnPublic")
            assertThat(protectedSetOnPublic.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(protectedSetOnPublic.setterVisibility).isEqualTo(VisibilityLevel.PROTECTED)

            val privateSetOnPublic = fooClass.assertProperty("privateSetOnPublic")
            assertThat(privateSetOnPublic.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(privateSetOnPublic.setterVisibility).isEqualTo(VisibilityLevel.PRIVATE)

            val protectedSet = fooClass.assertProperty("protectedSet")
            assertThat(protectedSet.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PROTECTED)
            assertThat(protectedSet.setterVisibility).isEqualTo(VisibilityLevel.PROTECTED)
        }
    }

    @Test
    fun `Test equals, hashCode, toString, baselineKey for properties with and without receivers`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                class Foo {
                    val foo = 0
                    val String.foo
                        get() = 0
                    val <T> T.foo
                        get() = 0
                }
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    property public int foo;
                    property public int String.foo;
                    property public <T> int T.foo;
                  }
                }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val noReceiver = fooClass.assertProperty("foo")
            assertThat(noReceiver).isEqualTo(noReceiver)
            val noReceiverHashCode = noReceiver.hashCode()
            assertThat(noReceiver.toString()).isEqualTo("property test.pkg.Foo#foo")
            assertThat(noReceiver.baselineKey.elementId()).isEqualTo("test.pkg.Foo#foo")

            val stringReceiver =
                fooClass.assertProperty("foo", receiverTypeString = "java.lang.String")
            assertThat(stringReceiver).isEqualTo(stringReceiver)
            val stringReceiverHashCode = stringReceiver.hashCode()
            assertThat(stringReceiver.toString())
                .isEqualTo("property test.pkg.Foo#java.lang.String.foo")
            assertThat(stringReceiver.baselineKey.elementId())
                .isEqualTo("test.pkg.Foo#java.lang.String.foo")

            val typeParamReceiver = fooClass.assertProperty("foo", receiverTypeString = "T")
            assertThat(typeParamReceiver).isEqualTo(typeParamReceiver)
            val typeParamReceiverHashCode = typeParamReceiver.hashCode()
            assertThat(typeParamReceiver.toString()).isEqualTo("property test.pkg.Foo#T.foo")
            assertThat(typeParamReceiver.baselineKey.elementId()).isEqualTo("test.pkg.Foo#T.foo")

            assertThat(noReceiver).isNotEqualTo(stringReceiver)
            assertThat(stringReceiver).isNotEqualTo(typeParamReceiver)
            assertThat(typeParamReceiver).isNotEqualTo(noReceiver)

            assertThat(noReceiverHashCode).isNotEqualTo(stringReceiverHashCode)
            assertThat(stringReceiverHashCode).isNotEqualTo(typeParamReceiverHashCode)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Test properties from the classpath`() {
        /*
        Compiled from the following file:
        package other.pkg
        open class ClasspathParent<T> {
            private val privateVal: T? = null
            val publicVal: T? = null
        }
        class ClasspathChild: ClasspathParent<Int>()
         */
        val classpathGzip =
            base64gzip(
                "test.jar",
                // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.9+10-b1163.91)
                "" +
                    "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                    "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                    "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9PBDn" +
                    "l2SkFukXZKfrO+ckFhcXJJZkBCQWpeaV6CWD+KVBsdnCjiK2m3f/vLXsrdoy" +
                    "09y/27KEDq49yn4x6IKTokC2p7PAJxPPtpRZb/NuzGSRb7Y/Gr9AgcPO48Vi" +
                    "e689k9m6Mn8Xnd/7vO7czb+fv1/PZ1j/YU6i8qVawd+HHc/bc+wIcl73+7iN" +
                    "2EK/RsMp36Tys5bXnPnb0G77P/l3SMmX239XizRvP7H0gYHK9xSNr3bNsVrm" +
                    "mz2Unio1mjQv/h1kmHM1z6YhLa3aSb7/VuMbk8oDARpT19+eozFVmvfQ2yiR" +
                    "73+CF942nL7wU6WE26xsyYn1JlxM9/hYpqa2TRf+HP489aHi9MZWm2brnqcz" +
                    "sg1Xb033LtrquIRZ8pd8y7PYdZaF127eUHOcaSiZfOAA25H6aWq3FNf/nrTQ" +
                    "dO6aHWEXi5YLX//U46l++fXRl0seLVku3P82yvra7yM/ry95evPrnuc5Vq7f" +
                    "qqx+lvw7qnhp4TyX2rPe5zIXxTjnWr2cMt1Muywzu3nm05+smvE8Jnt/u3xz" +
                    "Wue+UeHm68UBp40DeY8HeV56endp2JdcvkV6omuvpWj0uaXnCpr3lLmtW3G4" +
                    "Wa1/aefzJdVCvY/6LKao3ZjBnxcn2d2+JLPpiUJqoENfQbXY2oeciZPOxame" +
                    "kf6ZfyKy5Qv7xFqvifkhaj4hdUqfa6fVmemb1XXmfFvEoDeNo1hE/3Bcxr/G" +
                    "6Ptis+Zmz9J/F/nzvVz1uVsa5z8YarW0T4ldvu+27o1QkdU3ViQJRz955Cda" +
                    "XriedVmBPH/UCQ2diQZc/SJBXKytc5qWTwiN+McOSnks35g/6DIxMCxjxpfy" +
                    "5LCnPOeMzJwUaMIL8PU+7CBQO3tLpvTqaz+u/qr9/DhhFYeciCO3k0LXii+X" +
                    "LmdNL8vdNfFNmM237T/4bTjlllR8ce+c/MLzy4b8e3sqd282lp+/Pp/h24Zj" +
                    "Ab2+4Sr/atc/Nlda99XW/K5buNtbJgln2z+Law4KLzyTWHFcfod84dWPzKku" +
                    "7irs7uw8W+vzRSYkF/AtnfSs4+C9GznTHi54oNSv5HyuZGlaxI/Vyd8Uq6q1" +
                    "3hxVm3TKXrht+86HSyL/ss9uS9ScEsTA/HriP43La/zW7D28wU7Kb0fY1V92" +
                    "UxdZS4W5H767aPrnNbtC09V/flc/vurGytrpiR//H1ypvvVD3crbts7GJo+2" +
                    "JczgWSr0vPlfr8e6n74uXeLHtttkfXJZ9rM/Ytck2aNnFRTfxR1tfhI259SW" +
                    "gN0yGsq/F2luVXm+MWjjQ7djXsnsYmtfp58qqW7hnvIzku/J9riAKrWkxIkX" +
                    "Trqwe7c8zWeO6QncsnHOzpw1KRvbQrt+P9GcnL6H1fqAS8NJBlEfwVbxBClj" +
                    "XYPeyDNbJ1yWuDzj+ooskX3gsiTO20+8jZGBIYgJX4xKAzG8KMtNzMzTy84v" +
                    "ycnMi8/NTynNSU1OSEhIA2KWJD82jYCkC0kMYLO/Ku3ZKwzUKQEupxiZRBgQ" +
                    "piOXYaCCEhXgKjbRTUFOj/IoJtQTLv3QDUMOCjkUw34yEUrQ6GYhe10axazT" +
                    "LHiDMsCblQ2kjAUINYCOUWcF8QBTsgZbZQYAAA=="
            )
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                import other.pkg.ClasspathChild
                class SourceClass {
                    fun foo(): ClasspathChild? = null
                }
                """
            ),
            testFixture = TestFixture(additionalClassPath = listOf(classpathGzip.toFile()))
        ) {
            val sourceClass = codebase.assertClass("test.pkg.SourceClass")
            val classpathClassType = sourceClass.assertMethod("foo", emptyList()).returnType()
            classpathClassType.assertClassTypeItem {
                val classpathClass = resolveClass(codebase)!!
                val property = classpathClass.assertProperty("publicVal")
                property.type().assertClassTypeItem {
                    assertThat(qualifiedName).isEqualTo("java.lang.Integer")
                }
            }
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Test getter annotations on properties`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg

                @JvmInline value class IntValue(val value: Int)

                annotation class Hide

                class Foo {
                    @get:Hide val intProperty: Int = 0
                    @get:Hide val intValueProperty: IntValue = IntValue(0)
                }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val intProperty = fooClass.assertProperty("intProperty")
            assertThat(intProperty.annotationNames()).containsExactly("test.pkg.Hide")
            val intValueProperty = fooClass.assertProperty("intValueProperty")
            assertThat(intValueProperty.annotationNames()).containsExactly("test.pkg.Hide")
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN, InputFormat.SIGNATURE)
    @Test
    fun `Test context parameters on property`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                class Foo {
                    val noContextParams: Int = 0

                    context(s: String)
                    val oneContextParam: Int get() = 1

                    context(s: String, i: Int)
                    val twoContextParams: Int get() = 2

                    context(_: String)
                    val unnamedContextParam: Int get() = 3
                }
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public int noContextParams;
                    property public int oneContextParam(context String s);
                    property public int twoContextParams(context String s, context int i);
                    property public int unnamedContextParam(context String);
                  }
                }
                """
            )
        ) {
            fun ParameterItem.checkValues(
                expectedParent: PropertyItem,
                expectedName: String,
                expectedIndex: Int,
                expectedTypeString: String
            ) {
                assertThat(parent()).isEqualTo(expectedParent)
                assertThat(containingCallable()).isNull()
                assertThat(name()).isEqualTo(expectedName)
                // "_" is used as a non-public name
                assertThat(publicName()).isEqualTo(expectedName.takeUnless { it == "_" })
                assertThat(parameterIndex).isEqualTo(expectedIndex)
                assertThat(hasDefaultValue()).isFalse()
                assertThat(type().toTypeString()).isEqualTo(expectedTypeString)
            }

            val fooClass = codebase.assertClass("test.pkg.Foo")

            val noContextParams = fooClass.assertProperty("noContextParams")
            assertThat(noContextParams.contextParameters).isEmpty()

            val oneContextParam =
                fooClass.assertProperty(
                    "oneContextParam",
                    contextParameterTypeStrings = listOf("java.lang.String")
                )
            assertThat(oneContextParam.contextParameters).hasSize(1)
            oneContextParam.contextParameters[0].checkValues(
                expectedParent = oneContextParam,
                expectedName = "s",
                expectedIndex = 0,
                expectedTypeString = "java.lang.String"
            )

            val twoContextParams =
                fooClass.assertProperty(
                    "twoContextParams",
                    contextParameterTypeStrings = listOf("java.lang.String", "int")
                )
            assertThat(twoContextParams.contextParameters).hasSize(2)
            twoContextParams.contextParameters[0].checkValues(
                expectedParent = twoContextParams,
                expectedName = "s",
                expectedIndex = 0,
                expectedTypeString = "java.lang.String"
            )
            twoContextParams.contextParameters[1].checkValues(
                expectedParent = twoContextParams,
                expectedName = "i",
                expectedIndex = 1,
                expectedTypeString = "int"
            )

            val unnamedContextParam =
                fooClass.assertProperty(
                    "unnamedContextParam",
                    contextParameterTypeStrings = listOf("java.lang.String")
                )
            assertThat(unnamedContextParam.contextParameters).hasSize(1)
            unnamedContextParam.contextParameters[0].checkValues(
                expectedParent = unnamedContextParam,
                expectedName = "_",
                expectedIndex = 0,
                expectedTypeString = "java.lang.String"
            )
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN, InputFormat.SIGNATURE)
    @Test
    fun `Test toString, equals, and baseline ID for property with context parameters`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                class Foo {
                    context(s: String)
                    val oneContextParam: Int
                        get() = 0

                    context(s: String, i: Int)
                    val twoContextParam: Int
                        get() = 0

                    val withAndWithoutContextParam: Int = 0
                    context(_: String)
                    val withAndWithoutContextParam: Int
                        get() = 0
                }
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public int oneContextParam(context String s);
                    property public int twoContextParam(context String s, context int i);
                    property public int withAndWithoutContextParam(context String);
                    property public int withAndWithoutContextParam;
                  }
                }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val oneContextParam =
                fooClass.assertProperty(
                    "oneContextParam",
                    contextParameterTypeStrings = listOf("java.lang.String")
                )
            assertThat(oneContextParam.toString())
                .isEqualTo("property test.pkg.Foo#oneContextParam(context java.lang.String)")
            assertThat(oneContextParam.baselineElementId())
                .isEqualTo("test.pkg.Foo#oneContextParam(context java.lang.String)")

            val twoContextParam =
                fooClass.assertProperty(
                    "twoContextParam",
                    contextParameterTypeStrings = listOf("java.lang.String", "int")
                )
            assertThat(twoContextParam.toString())
                .isEqualTo(
                    "property test.pkg.Foo#twoContextParam(context java.lang.String, context int)"
                )
            assertThat(twoContextParam.baselineElementId())
                .isEqualTo("test.pkg.Foo#twoContextParam(context java.lang.String, context int)")

            val withContextParam =
                fooClass.assertProperty(
                    "withAndWithoutContextParam",
                    contextParameterTypeStrings = listOf("java.lang.String")
                )
            val withoutContextParam = fooClass.assertProperty("withAndWithoutContextParam")
            assertThat(withContextParam).isNotEqualTo(withoutContextParam)
            assertThat(withContextParam.hashCode()).isNotEqualTo(withoutContextParam.hashCode())
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN, InputFormat.SIGNATURE)
    @Test
    fun `Test findCorrespondingItemIn for properties`() {
        val kotlinFile =
            kotlin(
                """
                package test.pkg
                class Foo {
                    val foo: Int = 0
                    val String.foo: Int get() = 0
                    context(s: String) val foo: Int get() = 0
                }
                """
            )
        val signatureFile =
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    property public int foo;
                    property public int String.foo;
                    property public int foo(context String s);
                  }
                }
                """
            )
        // Use `runCodebaseTest` twice to create two separate codebases for testing
        runCodebaseTest(kotlinFile, signatureFile) {
            // This is the codebase created by the first `runCodebaseTest`
            val previousCodebase = codebase
            runCodebaseTest(kotlinFile, signatureFile) {
                // This is the codebase created by the second `runCodebaseTest`
                val currentCodebase = codebase
                // Verify that there are two separate codebases
                assertThat(previousCodebase).isNotSameInstanceAs(currentCodebase)

                val previousClass = previousCodebase.assertClass("test.pkg.Foo")
                val currentClass = currentCodebase.assertClass("test.pkg.Foo")

                val previousRegularVal = previousClass.assertProperty("foo")
                val currentRegularVal = currentClass.assertProperty("foo")
                assertThat(currentRegularVal.findCorrespondingItemIn(previousCodebase))
                    .isSameInstanceAs(previousRegularVal)

                val previousExtensionVal = previousClass.assertProperty("foo", "java.lang.String")
                val currentExtensionVal = currentClass.assertProperty("foo", "java.lang.String")
                assertThat(currentExtensionVal.findCorrespondingItemIn(previousCodebase))
                    .isSameInstanceAs(previousExtensionVal)

                val previousContextVal =
                    previousClass.assertProperty(
                        "foo",
                        contextParameterTypeStrings = listOf("java.lang.String")
                    )
                val currentContextVal =
                    currentClass.assertProperty(
                        "foo",
                        contextParameterTypeStrings = listOf("java.lang.String")
                    )
                assertThat(currentContextVal.findCorrespondingItemIn(previousCodebase))
                    .isSameInstanceAs(previousContextVal)

                val previousContextParam = previousContextVal.contextParameters[0]
                val currentContextParam = currentContextVal.contextParameters[0]
                assertThat(currentContextParam.findCorrespondingItemIn(previousCodebase))
                    .isSameInstanceAs(previousContextParam)
            }
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Test getter and setter for properties with context parameters`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                class Foo {
                    context(_: String)
                    var oneContextParam: Int
                        get() = 0
                        set(value) {}

                    context(_: String, _: Int)
                    var twoContextParam: Int
                        get() = 0
                        set(value) {}

                    context(_: String)
                    var String.withContextParamAndReceiver: Int
                        get() = 0
                        set(value) {}
                }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val oneContextParam =
                fooClass.assertProperty(
                    "oneContextParam",
                    contextParameterTypeStrings = listOf("java.lang.String")
                )
            assertThat(oneContextParam.getter).isNotNull()
            assertThat(oneContextParam.getter.toString())
                .isEqualTo("method test.pkg.Foo.getOneContextParam(String)")
            assertThat(oneContextParam.setter).isNotNull()
            assertThat(oneContextParam.setter.toString())
                .isEqualTo("method test.pkg.Foo.setOneContextParam(String,int)")

            val twoContextParam =
                fooClass.assertProperty(
                    "twoContextParam",
                    contextParameterTypeStrings = listOf("java.lang.String", "int")
                )
            assertThat(twoContextParam.getter).isNotNull()
            assertThat(twoContextParam.getter.toString())
                .isEqualTo("method test.pkg.Foo.getTwoContextParam(String,int)")
            assertThat(twoContextParam.setter).isNotNull()
            assertThat(twoContextParam.setter.toString())
                .isEqualTo("method test.pkg.Foo.setTwoContextParam(String,int,int)")

            val withContextParamAndReceiver =
                fooClass.assertProperty(
                    "withContextParamAndReceiver",
                    receiverTypeString = "java.lang.String",
                    contextParameterTypeStrings = listOf("java.lang.String")
                )
            assertThat(withContextParamAndReceiver.getter).isNotNull()
            assertThat(withContextParamAndReceiver.getter.toString())
                .isEqualTo("method test.pkg.Foo.getWithContextParamAndReceiver(String,String)")
            assertThat(withContextParamAndReceiver.setter).isNotNull()
            assertThat(withContextParamAndReceiver.setter.toString())
                .isEqualTo("method test.pkg.Foo.setWithContextParamAndReceiver(String,String,int)")
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Test getter and setter for properties with value class context parameters`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                    package test.pkg
                    @JvmInline value class IntValue(val value: Int)
                    class Foo {
                        @get:JvmName("getWithJvmName")
                        @set:JvmName("setWithJvmName")
                        context(_: IntValue)
                        var varWithJvmName: Int
                            get() = 0
                            set(value) {}

                        context(_: IntValue)
                        var varWithoutJvmName: Int
                            get() = 0
                            set(value) {}
                    }
                    """
                )
            ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 2.3.20 (JRE 21.0.9+10-b1163.91)
                    "" +
                        "H4sIAAAAAAAA/41WeTjU6xf/muwRjbFlr0myjIlBU6PMFWMbhoQIM9luZBBT" +
                        "STOG3HSzDcZy25AluyzZu6XIvmSNwZVdjH0ZKven/uiq5/l173mf89/7fM77" +
                        "nuecz+eDM97DDgG4ubkBAJADdgcE4AewepZoFUNTfVUs2tRQX++sJQyrf86S" +
                        "A2ADMtrA200AsIJtaTYxVoG95TdWUWxraS+2gHerj0xcgRlhlQ2xb0nZJRaL" +
                        "Rio+ikYtLUpWi22qjY0tYxOjEyAAZ8zFXSB0tAC5U0lrJ3H/9x2iAA/g5+Lr" +
                        "p+rt4aZqSPSzIlwmucCcLhN8fb89hWxp7CVxDrI9v+aBegR1eoJ1OqIj0zHg" +
                        "301USKfJp/HQTb1tDe7K8SvLtzX8UauKcspplhVZ3GvzlK0QHVcE8MTcMVII" +
                        "kXrBI9cmHsVs+J2mv+Ezj/xrc/5a28MXf3/+LACULVXoDD6T1LL7S8UlItsO" +
                        "3oxVAsklLySqqNtp5qTPjbVK5UzrCwrijKwYGFlamxgoxSrgCB//fgVzgzMc" +
                        "wfOlTG6+qXZ8Oy9f+TONMoK1cdfVdAeR8kRNtU2H0nXxTfsPZSqazOX4njt4" +
                        "A9mb7fkD89WY1GS5yBBeqdcg3Ycml6L6HwSHjXv2zlqRjPEnyJpd/eOUx+f5" +
                        "E+BN5YZBSys2j65KOtX7XL2wFVif0L8k3FmUeuvlb6tI3l/Lg3PmAiLYNSTS" +
                        "H9xm48tOWySBfRh+pQnudvGqF2nT5shRbUhnjWKhrRA9eyyPwCsuHJvYS5uM" +
                        "O2Ml60p+i2iOFNCI0Vz+XJfUihLG3e9pHVBvN349y+276ukedK/KU4Md7JZq" +
                        "sKmJsN5E4fjL4K+Pe5UV4HD7xxU/1Nn2+Ei0PrbzYJXQTYlRhMkHvaOa5fX4" +
                        "jSwZO9BAlsfEHbcBOOtAESUsVPxV40LvYJF963yJYv+EVV1m5207xDDSpvCC" +
                        "b7MftqjRSEnHS39ZcISN7pIgumo7rn7fmq5H3L+1MvY8J1syLrs7asNyKy0/" +
                        "04LnrWQq0mRFttUCuXmDsHrdS3+ydhR3MLeZ3sJhSuHjikuWtpXuEh7AuEHs" +
                        "sRwYPVPUp1lGbEHwsL9JX/zYFvCxeK25lER7PLoleH7tbG/5I5uIa4XCo7G8" +
                        "igTtK8sRNJNEenJV7YkTw4OkLLFBldV7y9CGVFeXLnfUMa1mEesKK0QzRzFh" +
                        "ZVhrX26Fq8+1utyPAaS83suQPC5Nr5ic1mwP69xg5ju/Gy9s8yoyPM4GZbFi" +
                        "PrT2C33aSBbug4fAz59KsY/XyEAilpS6yVYySZ1i6+HgWQfqFFUsj6pwev9p" +
                        "FnZ5pHh87/RsaDlNDKmjSKImrLawwWRGepKet2jToA4rx7kp1081kJKCXvFF" +
                        "eqee1p+Rz5gar78w+9RU6v0kxVEn875zdkDrn9CK7UQH8A0VSotTpVTZBfdl" +
                        "s4YFQd45M2ld6ZGIim7WRjV/6doNIyd2tS0BTgbtkHMaPImzYJ5maTICG16u" +
                        "qYDNCbv8EotJZB5zD8WQ9auXX5Eao9oFkjZe9/yu24IN/8y5luDr0ffGosom" +
                        "u489GD5VddFh/Vl0FuWcc5XLKnS4JniBfAvxvnh6b8Nat5zIx1nhuhhRyJSM" +
                        "TeDzE/bybTO50OG6APJGWPobig6UJRQc9OidHDjgfYnVMdRieuR78gIFLPEo" +
                        "gC3pbTU2JxQhAamsDqIGCfCN8+eORKNdtjteYmyGqN5Pbx+RELuajGS9Q9dK" +
                        "TEk5S4ZIhkgNrilfm7ZHJvrAJ2SZI3fdJL3hLJkvdKQi6W6NZgeAda6f0RF4" +
                        "Nx3pe3n9yEQWWDMhtBjFtXQIxkgfAh2EWNYWiNSJBL85cxEXE6MhLIToCemK" +
                        "J+I/Fnok9scdOiKwzWn24Pl1tSX8gchthbGTxua5DfIf2062oxbqUQufWJvM" +
                        "k1RhZTWbTqGi5fxiu6zfCJzhNcSqQruZyhrihEsxk9nAh+Ak9wjc3AxBK8VJ" +
                        "iHR2YNNoBINGbUf4+Ac3IW15fF+s182KDdS8Z6V21J3DT+bgJt0nU+a20OlS" +
                        "G4q3dB8/CQ6hC+maz8SM0SVK3hC1HmunQorple/nlRzEGtlIhT4dAY7a0x2F" +
                        "xxdYBCfyYaK1OO8T/urFNMaVdOMef9Veg/354Rl8mRYYJJT79GIcJqX6We1i" +
                        "VqANYhOiVTF0GlGREu2U2HQukWCyJpDgH6fdLCkzc/QWY6A3Jk/UX+N5LuPo" +
                        "s1MDNozrAZ/k+sZ7WpYwGQx9c39M5TkT8UOlyCS1olI989RAvZ5e978jmg6b" +
                        "+zMstGHqPRTYqeiRspixQPlxdqhgo6FWmVWqLNjBI3IaMfQLSllSd51OeXHo" +
                        "Em6WEPby5pBAdWtm3+q6Y1UrTM5pzbueGbP454DAyz/yFGiUdt/gd6Kn0Ga3" +
                        "ZOn5q6+p0hGbiuHr16rvFWR27XEfkzkY/3KmMEsVq5Vahlnfx1R0N+G1Z/GO" +
                        "hR2QNXJeY+/GyXs8FISDYntBRSGLUJb0w0Xn6aTQgxHq0TcFK3QC42nmh/Nk" +
                        "y6ioOXQ2lyHSglweGg3tcrYI5Vc7WkA99ur2lUkeabMmh8HhaSRTGI4eehpW" +
                        "1Wj/jr9+KCTwvtEeRojjwSH5shRk+Ry+qQZxd58rfHaENx+MHjkwWUrYmwjq" +
                        "4+gEVerILU1xELkHoensypX3cSV2oDGQ0IuvGjtpvWIUDgIAKvvPhlpiZ6i/" +
                        "ab0n4RIR5uHld/kS0dHTy5l02eXbdDvh8XjXnbxI7cAn1ybXAV9rWMv4d4N3" +
                        "UMS+6jgbCAL8U2W3xn9xFN/Hv/qLH+F27+gXY/BP3NnJf7MJP6Ltbg74OzRR" +
                        "DuBnW/4j0O4OSHwHNMcF/KfO4ow5OL/cZ985x9l2Pv4FGPgfyblK9J4JAAA="
                )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val varWithJvmName =
                fooClass.assertProperty(
                    "varWithJvmName",
                    contextParameterTypeStrings = listOf("test.pkg.IntValue")
                )
            assertThat(varWithJvmName.getter).isNotNull()
            assertThat(varWithJvmName.getter.toString())
                .isEqualTo("method test.pkg.Foo.getWithJvmName(int)")
            assertThat(varWithJvmName.setter).isNotNull()
            assertThat(varWithJvmName.setter.toString())
                .isEqualTo("method test.pkg.Foo.setWithJvmName(int,int)")

            val varWithoutJvmName =
                fooClass.assertProperty(
                    "varWithoutJvmName",
                    contextParameterTypeStrings = listOf("test.pkg.IntValue")
                )
            assertThat(varWithoutJvmName.getter).isNull()
            assertThat(varWithoutJvmName.setter).isNull()
        }
    }
}
