/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.LambdaTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeComparator
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.arrayTypeItem
import com.android.tools.metalava.model.testing.classTypeItem
import com.android.tools.metalava.model.testing.stringType
import com.android.tools.metalava.model.testing.value.annotationItem
import com.android.tools.metalava.model.testing.variableTypeItem
import com.android.tools.metalava.model.testing.wildcardTypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.kotlin
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

/**
 * Parameterized tests for [LambdaTypeItem] comparing all the different forms of lambdas against an
 * expected [ClassTypeItem] in preparation for adding `LambdaTypeItem.asJvmClassType()`
 * (b/566994677).
 */
@SupportedInputFormats(InputFormat.KOTLIN)
class CommonParameterizedLambdaTypeItemTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    /** Different declaration contexts in which a lambda type can be used. */
    enum class UseSite {
        PARAMETER {
            override fun declaration(kotlinType: String) =
                "class Test<T> { fun method(p: $kotlinType) {} }"

            override fun extractType(classItem: ClassItem) =
                classItem.methods().single().parameters().single().type()
        },
        RETURN {
            override fun declaration(kotlinType: String) =
                "interface Test<T> { fun method(): $kotlinType }"

            override fun extractType(classItem: ClassItem) =
                classItem.methods().single().returnType()
        },
        FIELD {
            override fun declaration(kotlinType: String) =
                "class Test<T>(@JvmField val field: $kotlinType)"

            override fun extractType(classItem: ClassItem) = classItem.fields().single().type()
        },
        PROPERTY {
            override fun declaration(kotlinType: String) =
                "interface Test<T> { val property: $kotlinType }"

            override fun extractType(classItem: ClassItem) = classItem.properties().single().type()
        },
        SUPERTYPE {
            override val isLambdaTypeItem: Boolean = false

            override fun declaration(kotlinType: String) = "interface Test<T> : $kotlinType"

            override fun extractType(classItem: ClassItem) = classItem.interfaceTypes().single()
        },
        ;

        /**
         * True if [extractType] is expected to return a [LambdaTypeItem], false if it is already
         * converted to a [ClassTypeItem] (e.g. for supertypes via `getHierarchicalClassType()`).
         */
        open val isLambdaTypeItem: Boolean = true

        /** Create the Kotlin class/interface declaration containing [kotlinType]. */
        abstract fun declaration(kotlinType: String): String

        /** Extract the [TypeItem] to test from [classItem]. */
        abstract fun extractType(classItem: ClassItem): TypeItem
    }

    data class TestParams
    @EntryPoint
    constructor(
        val kotlinType: String,
        val useSite: UseSite = UseSite.PARAMETER,
        val name: String = "${useSite.name.lowercase()} - $kotlinType",
        val expectedClassTypeItem: ClassTypeItem,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String = name
    }

    companion object {
        private val booleanType = classTypeItem("java.lang.Boolean")
        private val integerType = classTypeItem("java.lang.Integer")
        private val nullableIntegerType =
            integerType.substitute(TypeNullability.NULLABLE) as ClassTypeItem
        private val numberType = classTypeItem("java.lang.Number")
        private val objectType = classTypeItem("java.lang.Object")
        private val nullableObjectType =
            objectType.substitute(TypeNullability.NULLABLE) as ClassTypeItem
        private val stringType = stringType()
        private val nullableStringType =
            stringType.substitute(TypeNullability.NULLABLE) as ClassTypeItem
        private val stringArrayType = arrayTypeItem(stringType)
        private val listStringType = classTypeItem("java.util.List", arguments = listOf(stringType))
        private val throwableType = classTypeItem("java.lang.Throwable")
        private val unitType = classTypeItem("kotlin.Unit")
        private val voidType = classTypeItem("java.lang.Void")
        private val variableT =
            variableTypeItem("T").substitute(TypeModifiers.emptyUndefinedModifiers)

        private val jvmSuppressWildcardsAnnotation =
            annotationItem("kotlin.jvm.JvmSuppressWildcards")
        private val jvmSuppressWildcardsNullableModifiers =
            TypeModifiers.create(listOf(jvmSuppressWildcardsAnnotation), TypeNullability.NULLABLE)

        private val typeUseAnnotation = annotationItem("test.pkg.TypeUse")
        private val typeUseNonNullModifiers =
            TypeModifiers.create(listOf(typeUseAnnotation), TypeNullability.NONNULL)
        private val typeUseNullableModifiers =
            TypeModifiers.create(listOf(typeUseAnnotation), TypeNullability.NULLABLE)
        private val typeUseUndefinedModifiers =
            TypeModifiers.create(listOf(typeUseAnnotation), TypeNullability.UNDEFINED)

        /** Create a `? extends [bound]` wildcard. */
        private fun extendsWildcard(bound: ReferenceTypeItem) =
            wildcardTypeItem(extendsBound = bound)

        /**
         * Create a `? super [bound]` wildcard with the implicit nullable `java.lang.Object?`
         * extends bound used by Psi.
         */
        private fun superWildcard(
            bound: ReferenceTypeItem,
            extendsBound: ReferenceTypeItem? = nullableObjectType,
        ) = wildcardTypeItem(extendsBound = extendsBound, superBound = bound)

        /** Create a `kotlin.coroutines.Continuation<? super [returnType]>` type. */
        private fun continuationType(
            returnType: ReferenceTypeItem,
            superExtendsBound: ReferenceTypeItem? = nullableObjectType,
        ) =
            classTypeItem(
                "kotlin.coroutines.Continuation",
                arguments = listOf(superWildcard(returnType, extendsBound = superExtendsBound)),
            )

        private val params =
            listOf(
                // Method parameter lambdas
                TestParams(
                    kotlinType = "() -> Unit",
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function0",
                            arguments = listOf(unitType),
                        ),
                ),
                TestParams(
                    kotlinType = "(String) -> Int",
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function1",
                            arguments =
                                listOf(
                                    superWildcard(stringType),
                                    integerType,
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "(String) -> Int?",
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function1",
                            arguments =
                                listOf(
                                    superWildcard(stringType),
                                    nullableIntegerType,
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "(String) -> List<String>",
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function1",
                            arguments =
                                listOf(
                                    superWildcard(stringType),
                                    extendsWildcard(listStringType),
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "Number.(Array<String>) -> T",
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function2",
                            arguments =
                                listOf(
                                    superWildcard(numberType),
                                    superWildcard(stringArrayType),
                                    extendsWildcard(variableT),
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "(() -> Unit) -> Unit",
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function1",
                            arguments =
                                listOf(
                                    superWildcard(
                                        classTypeItem(
                                            "kotlin.jvm.functions.Function0",
                                            arguments = listOf(unitType),
                                        )
                                    ),
                                    unitType,
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "() -> (() -> Unit)",
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function0",
                            arguments =
                                listOf(
                                    extendsWildcard(
                                        classTypeItem(
                                            "kotlin.jvm.functions.Function0",
                                            arguments = listOf(unitType),
                                        )
                                    ),
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "suspend () -> Unit",
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function1",
                            arguments =
                                listOf(
                                    superWildcard(continuationType(unitType)),
                                    extendsWildcard(nullableObjectType),
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "suspend (Int) -> Int",
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function2",
                            arguments =
                                listOf(
                                    superWildcard(integerType),
                                    superWildcard(continuationType(integerType)),
                                    extendsWildcard(nullableObjectType),
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "suspend (Int, String) -> String?",
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function3",
                            arguments =
                                listOf(
                                    superWildcard(integerType),
                                    superWildcard(stringType),
                                    superWildcard(continuationType(nullableStringType)),
                                    extendsWildcard(nullableObjectType),
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "suspend Number.(Int) -> String?",
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function3",
                            arguments =
                                listOf(
                                    superWildcard(numberType),
                                    superWildcard(integerType),
                                    superWildcard(continuationType(nullableStringType)),
                                    extendsWildcard(nullableObjectType),
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "(@JvmSuppressWildcards Number.(Int) -> List<String>)?",
                    expectedClassTypeItem =
                        classTypeItem(
                                "kotlin.jvm.functions.Function2",
                                arguments =
                                    listOf(
                                        numberType,
                                        integerType,
                                        listStringType,
                                    ),
                            )
                            .substitute(jvmSuppressWildcardsNullableModifiers),
                ),
                TestParams(
                    name = "parameter - 23 parameters -> Number",
                    kotlinType = "(${List(23) { "Int" }.joinToString()}) -> Number",
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.FunctionN",
                            arguments = listOf(extendsWildcard(numberType)),
                        ),
                ),

                // Field lambdas
                TestParams(
                    kotlinType = "() -> Boolean",
                    useSite = UseSite.FIELD,
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function0",
                            arguments = listOf(booleanType),
                        ),
                ),
                TestParams(
                    kotlinType = "(() -> Boolean) -> Unit",
                    useSite = UseSite.FIELD,
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function1",
                            arguments =
                                listOf(
                                    classTypeItem(
                                        "kotlin.jvm.functions.Function0",
                                        arguments = listOf(booleanType),
                                    ),
                                    unitType,
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "((Boolean) -> Unit) -> Unit",
                    useSite = UseSite.FIELD,
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function1",
                            arguments =
                                listOf(
                                    classTypeItem(
                                        "kotlin.jvm.functions.Function1",
                                        arguments =
                                            listOf(
                                                superWildcard(booleanType),
                                                unitType,
                                            ),
                                    ),
                                    unitType,
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "suspend (Int) -> String?",
                    useSite = UseSite.FIELD,
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function2",
                            arguments =
                                listOf(
                                    integerType,
                                    continuationType(nullableStringType),
                                    nullableObjectType,
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "suspend Number.(Int) -> String?",
                    useSite = UseSite.FIELD,
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function3",
                            arguments =
                                listOf(
                                    numberType,
                                    integerType,
                                    continuationType(nullableStringType),
                                    nullableObjectType,
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "suspend Number.(Int) -> Unit",
                    useSite = UseSite.FIELD,
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function3",
                            arguments =
                                listOf(
                                    numberType,
                                    integerType,
                                    continuationType(unitType),
                                    nullableObjectType,
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "(Throwable) -> Nothing",
                    useSite = UseSite.FIELD,
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function1",
                            arguments =
                                listOf(
                                    throwableType,
                                    voidType,
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "(Int, Throwable) -> Nothing",
                    useSite = UseSite.FIELD,
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function2",
                            arguments =
                                listOf(
                                    integerType,
                                    throwableType,
                                    voidType,
                                ),
                        ),
                ),

                // Method return lambdas
                TestParams(
                    kotlinType = "((T) -> Int)?",
                    useSite = UseSite.RETURN,
                    expectedClassTypeItem =
                        classTypeItem(
                                "kotlin.jvm.functions.Function1",
                                arguments =
                                    listOf(
                                        variableT,
                                        integerType,
                                    ),
                            )
                            .substitute(TypeNullability.NULLABLE) as ClassTypeItem,
                ),
                TestParams(
                    kotlinType = "@TypeUse ((@TypeUse T) -> @TypeUse Int)?",
                    useSite = UseSite.RETURN,
                    expectedClassTypeItem =
                        classTypeItem(
                                "kotlin.jvm.functions.Function1",
                                arguments =
                                    listOf(
                                        variableT.substitute(typeUseUndefinedModifiers),
                                        integerType.substitute(typeUseNonNullModifiers),
                                    ),
                            )
                            .substitute(typeUseNullableModifiers),
                ),
                TestParams(
                    kotlinType = "() -> List<String>",
                    useSite = UseSite.RETURN,
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function0",
                            arguments = listOf(listStringType),
                        ),
                ),

                // Property lambdas (created via KaTypeItemFactory)
                TestParams(
                    kotlinType = "(Int, String?) -> Boolean",
                    useSite = UseSite.PROPERTY,
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function2",
                            arguments =
                                listOf(
                                    integerType,
                                    nullableStringType,
                                    booleanType,
                                ),
                        ),
                ),
                TestParams(
                    kotlinType = "suspend (Int) -> String?",
                    useSite = UseSite.PROPERTY,
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function2",
                            arguments =
                                listOf(
                                    integerType,
                                    continuationType(nullableStringType, superExtendsBound = null),
                                    extendsWildcard(objectType),
                                ),
                        ),
                ),
                TestParams(
                    name = "property - 23 parameters -> Number",
                    kotlinType = "(${List(23) { "Int" }.joinToString()}) -> Number",
                    useSite = UseSite.PROPERTY,
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.FunctionN",
                            arguments = listOf(numberType),
                        ),
                ),

                // Supertype lambdas
                TestParams(
                    kotlinType = "() -> Unit",
                    useSite = UseSite.SUPERTYPE,
                    expectedClassTypeItem =
                        classTypeItem(
                            "kotlin.jvm.functions.Function0",
                            arguments = listOf(unitType),
                        ),
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "{0}") fun data() = params
    }

    @Test
    fun `Test lambda asJvmClassType`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    @Target(AnnotationTarget.TYPE)
                    annotation class TypeUse
                    ${params.useSite.declaration(params.kotlinType)}
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val typeItem = params.useSite.extractType(testClass)
            val classTypeItem =
                if (params.useSite.isLambdaTypeItem) {
                    typeItem.assertLambdaTypeItem()
                    (typeItem as LambdaTypeItem).asJvmClassType()
                } else {
                    typeItem.assertClassTypeItem()
                    typeItem as ClassTypeItem
                }
            assertTypeComparison(
                params.expectedClassTypeItem,
                classTypeItem,
                TypeComparator.STRICT,
            )
        }
    }
}
