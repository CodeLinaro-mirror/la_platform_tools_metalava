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

package com.android.tools.metalava.model.testsuite.typeitem

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CommonTypeAliasTest : BaseModelTest() {

    @Test
    fun `Test typealias of generic type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    class GenericBar<A, B>

                    typealias StringIntBar = GenericBar<String?, Int>

                    class Foo<T> {
                        fun method(): StringIntBar? = null
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val method = fooClass.methods().single()
            assertThat(method.kotlinLikeDescription())
                .isEqualTo(
                    "fun method(): test.pkg.GenericBar<java.lang.String?,java.lang.Integer>?"
                )
        }
    }

    /**
     * Sets up a test with an expect/actual type alias. The type alias is defined as an expect class
     * in commonMain, and an actual type alias in androidMain. It is used as a method return type
     * from both source sets.
     *
     * @param typeAlias The name and type parameters of the type alias, e.g. "Foo" or "Foo<T>".
     * @param aliasedType The value of the type alias actual in androidMain.
     * @param typeAliasUsage The usage of the type alias as a method return type in both commonMain
     *   and androidMain, e.g. "Foo?" or "Foo<String>".
     * @param assertion Test that will run on the usages of the type alias, for both the commonMain
     *   and androidMain usage.
     */
    private fun checkExpectActualTypealias(
        typeAlias: String,
        aliasedType: String,
        typeAliasUsage: String,
        assertion: (TypeItem) -> Unit,
    ) {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                    package test.pkg
                    expect class $typeAlias
                    fun common(): $typeAliasUsage = error("unimplemented")
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo.android.kt",
                """
                    package test.pkg
                    actual typealias $typeAlias = $aliasedType
                    fun android(): $typeAliasUsage = error("unimplemented")
                """
            )
        runCodebaseTest(
            inputSet(
                androidSource,
                commonSource,
            ),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
        ) {
            val commonMethod = codebase.assertClass("test.pkg.FooKt").assertMethod("common", "")
            assertion(commonMethod.returnType())

            val androidMethod =
                codebase.assertClass("test.pkg.Foo_androidKt").assertMethod("android", "")
            assertion(androidMethod.returnType())
        }
    }

    /**
     * Asserts that [typeItem] is either a [ClassTypeItem] or a [WildcardTypeItem]. Returns the
     * [ClassTypeItem] or the extends bound of the [WildcardTypeItem].
     *
     * This is to be used when there are K1/K2 differences between when type arguments end up as
     * wildcards or plain usages of a type.
     */
    private fun getClassOrWildcardExtendsBound(typeItem: TypeItem): TypeItem {
        return typeItem as? ClassTypeItem
            ?: (typeItem as? WildcardTypeItem)?.extendsBound
            ?: error("expected class type or wildcard type with extends bound, was $typeItem")
    }

    @Test
    fun `Test usage of simple typealias expect actual`() {
        checkExpectActualTypealias(
            typeAlias = "Foo",
            aliasedType = "String",
            typeAliasUsage = "Foo",
        ) {
            assertThat(it.isString()).isTrue()
            assertThat(it.modifiers.isNullable).isFalse()
        }
    }

    @Test
    fun `Test usage of nullable typealias expect actual`() {
        checkExpectActualTypealias(
            typeAlias = "Foo",
            aliasedType = "String?",
            typeAliasUsage = "Foo",
        ) {
            assertThat(it.isString()).isTrue()
            assertThat(it.modifiers.isNullable).isTrue()
        }
    }

    @Test
    fun `Test usage of typealias expect actual used as nullable`() {
        checkExpectActualTypealias(
            typeAlias = "Foo",
            aliasedType = "String",
            typeAliasUsage = "Foo?",
        ) {
            assertThat(it.isString()).isTrue()
            assertThat(it.modifiers.isNullable).isTrue()
        }
    }

    @Test
    fun `Test usage of generic typealias expect actual`() {
        checkExpectActualTypealias(
            typeAlias = "Foo<T>",
            aliasedType = "List<T>",
            typeAliasUsage = "Foo<String>",
        ) {
            it.assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("java.util.List")
                assertThat(modifiers.isNullable).isFalse()
                val stringType = getClassOrWildcardExtendsBound(arguments.single())
                assertThat(stringType.isString()).isTrue()
                assertThat(stringType.modifiers.isNullable).isFalse()
            }
        }
    }

    @Test
    fun `Test usage of nullable generic typealias expect actual`() {
        checkExpectActualTypealias(
            typeAlias = "Foo<T>",
            aliasedType = "List<T?>",
            typeAliasUsage = "Foo<String>",
        ) {
            it.assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("java.util.List")
                assertThat(modifiers.isNullable).isFalse()
                val stringType = getClassOrWildcardExtendsBound(arguments.single())
                assertThat(stringType.isString()).isTrue()
                assertThat(stringType.modifiers.isNullable).isTrue()
            }
        }
    }

    @Test
    fun `Test usage of generic typealias expect actual used as nullable`() {
        checkExpectActualTypealias(
            typeAlias = "Foo<T>",
            aliasedType = "List<T>",
            typeAliasUsage = "Foo<String?>",
        ) {
            it.assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("java.util.List")
                assertThat(modifiers.isNullable).isFalse()
                val stringType = getClassOrWildcardExtendsBound(arguments.single())
                assertThat(stringType.isString()).isTrue()
                assertThat(stringType.modifiers.isNullable).isTrue()
            }
        }
    }

    @Test
    fun `Test usage of generic typealias expect actual non class type`() {
        checkExpectActualTypealias(
            typeAlias = "Foo<T>",
            aliasedType = "Array<T>",
            typeAliasUsage = "Foo<String>",
        ) {
            it.assertArrayTypeItem {
                assertThat(modifiers.isNullable).isFalse()
                assertThat(componentType.isString()).isTrue()
                assertThat(componentType.modifiers.isNullable).isFalse()
            }
        }
    }

    @Test
    fun `Test usage of generic typealias expect actual reversed generics`() {
        checkExpectActualTypealias(
            typeAlias = "Foo<V, K>",
            aliasedType = "Map<K, V>",
            typeAliasUsage = "Foo<String, Number>",
        ) {
            it.assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("java.util.Map")
                assertThat(modifiers.isNullable).isFalse()
                assertThat(arguments).hasSize(2)
                getClassOrWildcardExtendsBound(arguments[0]).assertClassTypeItem {
                    assertThat(qualifiedName).isEqualTo("java.lang.Number")
                    assertThat(modifiers.isNullable).isFalse()
                }
                getClassOrWildcardExtendsBound(arguments[1]).assertClassTypeItem {
                    assertThat(qualifiedName).isEqualTo("java.lang.String")
                    assertThat(modifiers.isNullable).isFalse()
                }
            }
        }
    }

    @Test
    fun `Test usage of actual typealias as parent class`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/ExpectActualParentClass.kt",
                """
                    package test.pkg
                    class CommonChildClass : ExpectActualParentClass()
                    expect open class ExpectActualParentClass()
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/ExpectActualParentClass.android.kt",
                """
                    package test.pkg
                    open class ActualParentClass
                    actual typealias ExpectActualParentClass = ActualParentClass
                """
            )
        runCodebaseTest(
            inputSet(
                androidSource,
                commonSource,
            ),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
        ) {
            val actualParentClass = codebase.assertClass("test.pkg.ActualParentClass")
            val actualParentClassType = actualParentClass.type()
            val commonChildClass = codebase.assertClass("test.pkg.CommonChildClass")
            assertThat(commonChildClass.superClassType()).isEqualTo(actualParentClassType)
        }
    }

    @Test
    fun `Test usage of nullable actual typealias as parent class`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/ExpectActualParentClass.kt",
                """
                    package test.pkg
                    class CommonChildClass : ExpectActualParentClass()
                    expect open class ExpectActualParentClass()
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/ExpectActualParentClass.android.kt",
                """
                    package test.pkg
                    open class ActualParentClass
                    actual typealias ExpectActualParentClass = ActualParentClass?
                """
            )
        runCodebaseTest(
            inputSet(
                androidSource,
                commonSource,
            ),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
        ) {
            val actualParentClass = codebase.assertClass("test.pkg.ActualParentClass")
            val actualParentClassType = actualParentClass.type()
            val commonChildClass = codebase.assertClass("test.pkg.CommonChildClass")
            assertThat(commonChildClass.superClassType()).isEqualTo(actualParentClassType)
            assertThat(commonChildClass.superClassType()!!.modifiers.isNullable).isFalse()
        }
    }
}
