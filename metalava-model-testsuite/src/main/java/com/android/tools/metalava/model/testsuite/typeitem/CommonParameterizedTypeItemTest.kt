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

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.testing.arrayTypeItem
import com.android.tools.metalava.model.testing.classTypeItem
import com.android.tools.metalava.model.testing.primitiveTypeForKind
import com.android.tools.metalava.model.testing.variableTypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

class CommonParameterizedTypeItemTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class TestParams
    @EntryPoint
    constructor(
        val javaTypeParameter: String? = null,
        val javaType: String,
        val name: String = javaType,
        val kotlinModifiers: String? = null,
        val kotlinTypeParameter: String? = null,
        val kotlinType: String,
        val expectedTypeItem: TypeItem,
        val expectedAsErasedTypeItem: TypeItem = expectedTypeItem,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        fun javaParameter(): String = "$javaType p"

        fun javaTypeParameter(): String = javaTypeParameter ?: ""

        fun kotlinParameter(): String = "${kotlinModifiers?:""} p: $kotlinType"

        fun kotlinTypeParameter(): String = kotlinTypeParameter ?: ""

        override fun toString(): String {
            return name
        }
    }

    companion object {
        private val params =
            listOf(
                TestParams(
                    javaType = "int",
                    kotlinType = "Int",
                    expectedTypeItem = primitiveTypeForKind(PrimitiveTypeItem.Primitive.INT),
                ),
                TestParams(
                    javaType = "int[]",
                    kotlinType = "IntArray",
                    expectedTypeItem =
                        arrayTypeItem(primitiveTypeForKind(PrimitiveTypeItem.Primitive.INT)),
                ),
                TestParams(
                    javaType = "test.pkg.Generic<String>",
                    kotlinType = "test.pkg.Generic<String>",
                    expectedTypeItem =
                        classTypeItem(
                            "test.pkg.Generic",
                            arguments =
                                listOf(
                                    classTypeItem("java.lang.String"),
                                ),
                        ),
                    expectedAsErasedTypeItem = classTypeItem("test.pkg.Generic"),
                ),
                TestParams(
                    javaType = "String[]...",
                    kotlinModifiers = "vararg",
                    kotlinType = "Array<String>",
                    expectedTypeItem =
                        arrayTypeItem(
                            arrayTypeItem(classTypeItem("java.lang.String")),
                            isVarargs = true,
                        ),
                    expectedAsErasedTypeItem =
                        arrayTypeItem(arrayTypeItem(classTypeItem("java.lang.String"))),
                ),
                TestParams(
                    javaTypeParameter = "<T extends test.pkg.Generic<T>>",
                    javaType = "java.util.Map.Entry<String, T>",
                    kotlinTypeParameter = "<T: test.pkg.Generic<T>>",
                    kotlinType = "java.util.Map.Entry<String, T>",
                    expectedTypeItem =
                        classTypeItem(
                            "java.util.Map.Entry",
                            outerClassType = classTypeItem("java.util.Map"),
                            arguments =
                                listOf(
                                    classTypeItem("java.lang.String"),
                                    variableTypeItem("T"),
                                ),
                        ),
                    expectedAsErasedTypeItem =
                        classTypeItem(
                            "java.util.Map.Entry",
                            outerClassType = classTypeItem("java.util.Map"),
                        ),
                ),
                TestParams(
                    javaTypeParameter = "<T>",
                    javaType = "T",
                    kotlinTypeParameter = "<T>",
                    kotlinType = "T",
                    expectedTypeItem = variableTypeItem("T"),
                    expectedAsErasedTypeItem = classTypeItem("java.lang.Object"),
                ),
                TestParams(
                    name = "T extends Generic",
                    javaTypeParameter = "<T extends test.pkg.Generic<T>>",
                    javaType = "T",
                    kotlinTypeParameter = "<T: test.pkg.Generic<T>>",
                    kotlinType = "T",
                    expectedTypeItem = variableTypeItem("T"),
                    expectedAsErasedTypeItem = classTypeItem("test.pkg.Generic"),
                ),
                TestParams(
                    javaTypeParameter = "<T extends test.pkg.Generic<T>>",
                    javaType = "T[]",
                    kotlinTypeParameter = "<T: test.pkg.Generic<T>>",
                    kotlinType = "Array<T>",
                    expectedTypeItem = arrayTypeItem(variableTypeItem("T")),
                    expectedAsErasedTypeItem = arrayTypeItem(classTypeItem("test.pkg.Generic")),
                ),
                TestParams(
                    javaType = "test.pkg.Generic<Integer>[]",
                    kotlinType = "Array<test.pkg.Generic<Int>>",
                    expectedTypeItem =
                        arrayTypeItem(
                            classTypeItem(
                                "test.pkg.Generic",
                                arguments =
                                    listOf(
                                        classTypeItem("java.lang.Integer"),
                                    ),
                            )
                        ),
                    expectedAsErasedTypeItem = arrayTypeItem(classTypeItem("test.pkg.Generic")),
                ),
            )

        @JvmStatic @Parameterized.Parameters fun data() = params
    }

    internal data class TestContext(
        val codebase: Codebase,
        val typeItem: TypeItem,
    )

    private fun runTypeItemTest(test: TestContext.() -> Unit) {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public interface Foo {
                                method public ${params.javaTypeParameter()} void method(${params.javaParameter()});
                            }
                            public interface Generic<A> {
                            }
                        }
                    """
                ),
            ),
            inputSet(
                java(
                    """
                        package test.pkg;
                        public interface Foo {
                            ${params.javaTypeParameter()} void method(${params.javaParameter()});
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
                        interface Foo {
                            fun ${params.kotlinTypeParameter()} method(${params.kotlinParameter()})
                        }
                    """
                ),
                kotlin(
                    """
                        package test.pkg
                        interface Generic<A> {
                        }
                    """
                )
            ),
        ) {
            val methodItem = codebase.assertClass("test.pkg.Foo").methods().single()
            val parameterItem = methodItem.parameters()[0]
            val typeItem = parameterItem.type()
            TestContext(
                    codebase = codebase,
                    typeItem = typeItem,
                )
                .test()
        }
    }

    @Test
    fun `Test type`() {
        runTypeItemTest { assertEquals(params.expectedTypeItem, typeItem) }
    }

    @Test
    fun `Test asErasedType`() {
        runTypeItemTest { assertEquals(params.expectedAsErasedTypeItem, typeItem.asErasedType()) }
    }

    @Test
    fun `Test consistency of asErasedType's toTypeString and toErasedTypeString`() {
        /**
         * The [TypeItem.toErasedTypeString] must be the same as the [[TypeItem.toTypeString] of
         * [TypeItem.asErasedType].
         */
        val expectedErasedTypeString = params.expectedAsErasedTypeItem.toTypeString()
        runTypeItemTest { assertEquals(expectedErasedTypeString, typeItem.toErasedTypeString()) }
    }
}
