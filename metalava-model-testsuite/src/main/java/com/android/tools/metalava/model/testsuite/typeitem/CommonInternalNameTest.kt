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

import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Test
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter

class CommonInternalNameTest : BaseModelTest() {

    @Parameter(0) lateinit var params: TestParams

    data class TestParams(
        val javaType: String,
        val kotlinType: String = javaType,
        val internalName: String,
        val skipForInputFormats: Set<InputFormat> = emptySet(),
    ) {
        fun isVarargs() = javaType.endsWith("...")

        /** Get the [TypeItem] from the method item. */
        fun getTypeItem(methodItem: MethodItem): TypeItem {
            return if (isVarargs()) {
                methodItem.parameters().single().type()
            } else {
                methodItem.returnType()
            }
        }

        override fun toString() = javaType
    }

    companion object {
        private val params =
            listOf(
                TestParams(
                    javaType = "boolean",
                    kotlinType = "Boolean",
                    internalName = "Z",
                ),
                TestParams(
                    javaType = "byte",
                    kotlinType = "Byte",
                    internalName = "B",
                ),
                TestParams(
                    javaType = "char",
                    kotlinType = "Char",
                    internalName = "C",
                ),
                TestParams(
                    javaType = "double",
                    kotlinType = "Double",
                    internalName = "D",
                ),
                TestParams(
                    javaType = "float",
                    kotlinType = "Float",
                    internalName = "F",
                ),
                TestParams(
                    javaType = "int",
                    kotlinType = "Int",
                    internalName = "I",
                ),
                TestParams(
                    javaType = "int[]",
                    kotlinType = "IntArray",
                    internalName = "[I",
                ),
                TestParams(
                    javaType = "int[][]",
                    kotlinType = "Array<IntArray>",
                    internalName = "[[I",
                ),
                TestParams(
                    javaType = "int...",
                    kotlinType = "Int",
                    internalName = "[I",
                ),
                TestParams(
                    javaType = "long",
                    kotlinType = "Long",
                    internalName = "J",
                ),
                TestParams(
                    javaType = "short",
                    kotlinType = "Short",
                    internalName = "S",
                ),
                TestParams(
                    javaType = "void",
                    kotlinType = "Unit",
                    internalName = "V",
                ),
                TestParams(
                    javaType = "java.lang.Number",
                    internalName = "Ljava/lang/Number;",
                ),
                TestParams(
                    javaType = "java.lang.Number[]",
                    kotlinType = "Array<java.lang.Number>",
                    internalName = "[Ljava/lang/Number;",
                ),
                TestParams(
                    javaType = "java.lang.Number...",
                    kotlinType = "java.lang.Number",
                    internalName = "[Ljava/lang/Number;",
                ),
                TestParams(
                    javaType = "java.util.Map.Entry<java.lang.String,java.lang.Number>",
                    internalName = "Ljava/util/Map\$Entry;",
                ),
                TestParams(
                    javaType = "pkg.UnknownClass",
                    internalName = "Lpkg/UnknownClass;",
                    // Does not work for Kotlin as it treats all unknown classes as being
                    // `error.NonExistentClass`.
                    skipForInputFormats = setOf(InputFormat.KOTLIN),
                ),
                TestParams(
                    javaType = "pkg.UnknownClass.Inner",
                    internalName = "Lpkg/UnknownClass\$Inner;",
                    // Does not work for Kotlin as it treats all unknown classes as being
                    // `error.NonExistentClass`.
                    skipForInputFormats = setOf(InputFormat.KOTLIN),
                ),
                TestParams(
                    javaType = "java.util.List<java.lang.Number>",
                    internalName = "Ljava/util/List;",
                ),
                TestParams(
                    javaType = "java.util.List<java.lang.Number>[]",
                    kotlinType = "Array<java.util.List<java.lang.Number>>",
                    internalName = "[Ljava/util/List;",
                ),
                TestParams(
                    javaType = "T",
                    internalName = "Ljava/lang/Object;",
                ),
                TestParams(
                    javaType = "T[]",
                    kotlinType = "Array<T>",
                    internalName = "[Ljava/lang/Object;",
                ),
                TestParams(
                    javaType = "N",
                    internalName = "Ljava/lang/Number;",
                ),
                TestParams(
                    javaType = "N[]",
                    kotlinType = "Array<N>",
                    internalName = "[Ljava/lang/Number;",
                ),
                TestParams(
                    javaType = "kotlin.jvm.functions.Function1<java.lang.Integer,java.lang.String>",
                    kotlinType = "(Int) -> String",
                    internalName = "Lkotlin/jvm/functions/Function1;",
                ),
                TestParams(
                    javaType = "test.pkg.Outer.middle",
                    internalName = "Ltest/pkg/Outer\$middle;",
                ),
                TestParams(
                    javaType = "test.pkg.Outer.middle.Inner",
                    internalName = "Ltest/pkg/Outer\$middle\$Inner;",
                ),
                TestParams(
                    javaType = "test.pkg.lower.Inner",
                    internalName = "Ltest/pkg/lower\$Inner;",
                    // Signature files do not resolve classes when parsing types so cannot
                    // distinguish a top-level class with a lowercase name from a package name.
                    skipForInputFormats = setOf(InputFormat.SIGNATURE),
                ),
            )

        @JvmStatic @Parameterized.Parameters fun params() = params
    }

    @Test
    fun test() {
        // Some combinations do not work in some input formats.
        Assume.assumeFalse(
            "Test is not supported for $inputFormat",
            inputFormat in params.skipForInputFormats
        )

        // If the type is void/Unit then it can only be used as a return type but if it ends with
        // `...` then it can only be used as a parameter type so choose were the type will be used
        // and how it will be accessed.
        val (returnType, parameterType) =
            if (params.isVarargs()) {
                Pair("void", params.javaType)
            } else {
                Pair(params.javaType, "int")
            }

        val (kotlinReturnType, kotlinParameterType, kotlinParameterPrefix) =
            if (params.isVarargs()) {
                Triple("Unit", params.kotlinType, "vararg ")
            } else {
                Triple(params.kotlinType, "Int", "")
            }

        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public interface Foo<T, N extends java.lang.Number> {
                            method public $returnType method($parameterType p);
                          }
                          public class Outer {
                          }
                          public static class Outer.middle {
                          }
                          public static class Outer.middle.Inner {
                          }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;
                        public interface Foo<T, N extends Number> {
                            $returnType method($parameterType p);
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Outer {
                            private Outer() {}
                            public static class middle {
                                private middle() {}
                                public static class Inner {
                                    private Inner() {}
                                }
                            }
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class lower {
                            private lower() {}
                            public static class Inner {
                                private Inner() {}
                            }
                        }
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        interface Foo<T, N : Number> {
                            fun method(${kotlinParameterPrefix}p: $kotlinParameterType): $kotlinReturnType
                        }
                        class Outer private constructor() {
                            class middle private constructor() {
                                class Inner private constructor()
                            }
                        }
                        class lower private constructor() {
                            class Inner private constructor()
                        }
                    """
                ),
            ),
        ) {
            val methodItem = codebase.assertClass("test.pkg.Foo").methods().single()
            val typeItem = params.getTypeItem(methodItem)
            assertEquals(params.internalName, typeItem.internalName())
        }
    }
}
