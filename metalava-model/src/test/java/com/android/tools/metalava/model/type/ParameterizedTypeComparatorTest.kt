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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeComparator
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.testing.arrayTypeItem
import com.android.tools.metalava.model.testing.classTypeItem
import com.android.tools.metalava.model.testing.primitiveTypeForKind
import com.android.tools.metalava.model.testing.stringType
import com.android.tools.metalava.model.testing.testTypeString
import com.android.tools.metalava.model.testing.typeParameterItem
import com.android.tools.metalava.model.testing.value.annotationItem
import com.android.tools.metalava.model.testing.variableTypeItem
import com.android.tools.metalava.model.testing.wildcardTypeItem
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParameterizedTypeComparatorTest {

    @Parameterized.Parameter(0) lateinit var testCase: TestCase

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestCase] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { testCase.entryPointCallerTracker }

    data class TestCase
    @EntryPoint
    constructor(
        /** The first [TypeItem] to compare. */
        val type1: TypeItem?,

        /** The second [TypeItem] to compare. */
        val type2: TypeItem?,

        /** Result of comparing [type1] and [type2] with [TypeComparator.IDENTICAL]. */
        val expectedIdenticalResult: Boolean,

        /**
         * Result of comparing [type1] and [type2] with [TypeComparator.STRICT].
         *
         * Defaults to [expectedIdenticalResult].
         */
        val expectedStrictResult: Boolean = expectedIdenticalResult,

        /**
         * Result of comparing [type1] and [type2] with [TypeComparator.NULLABILITY_AWARE].
         *
         * Defaults to [expectedStrictResult].
         */
        val expectedNullabilityAwareResult: Boolean = expectedStrictResult,

        /**
         * Result of comparing [type1] and [type2] with [TypeComparator.FLATTENED_WILDCARDS].
         *
         * Defaults to [expectedNullabilityAwareResult].
         */
        val expectedFlattenedWildcardsResult: Boolean = expectedNullabilityAwareResult,

        /**
         * Result of comparing [type1] and [type2] with [TypeComparator.IGNORE_NULLABILITY].
         *
         * Defaults to [expectedNullabilityAwareResult].
         */
        val expectedIgnoreNullabilityResult: Boolean = expectedNullabilityAwareResult,

        /**
         * Result of comparing [type1] and [type2] with [TypeComparator.ERASED].
         *
         * Defaults to [expectedIgnoreNullabilityResult].
         */
        val expectedErasedResult: Boolean = expectedIgnoreNullabilityResult,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        /** The name of the test case, used as the display name. */
        private val name: String = "${typeString(type1)} vs ${typeString(type2)}"

        override fun toString(): String = name
    }

    companion object {
        /**
         * Get a string representation of [type] for test case display names, including annotations
         * and Kotlin-style nullability markers, or `"null"` if [type] is null.
         */
        private fun typeString(type: TypeItem?): String =
            type?.testTypeString(annotations = true, kotlinStyleNulls = true) ?: "null"

        private val testCases = buildList {
            // Primitives
            val intType = primitiveTypeForKind(PrimitiveTypeItem.Primitive.INT)
            val longType = primitiveTypeForKind(PrimitiveTypeItem.Primitive.LONG)
            val booleanType = primitiveTypeForKind(PrimitiveTypeItem.Primitive.BOOLEAN)

            add(
                TestCase(
                    intType,
                    intType,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    intType,
                    longType,
                    expectedIdenticalResult = false,
                )
            )
            add(
                TestCase(
                    intType,
                    booleanType,
                    expectedIdenticalResult = false,
                )
            )

            // Classes & Nullability
            val string = stringType()
            val nullableString = string.substitute(TypeNullability.NULLABLE) as ClassTypeItem
            val platformString = string.substitute(TypeNullability.PLATFORM) as ClassTypeItem
            val objectType = classTypeItem("java.lang.Object")

            add(
                TestCase(
                    string,
                    string,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    string,
                    objectType,
                    expectedIdenticalResult = false,
                )
            )
            add(
                TestCase(
                    string,
                    nullableString,
                    expectedIdenticalResult = false,
                    expectedIgnoreNullabilityResult = true,
                )
            )
            add(
                TestCase(
                    string,
                    platformString,
                    expectedIdenticalResult = false,
                    expectedIgnoreNullabilityResult = true,
                )
            )
            add(
                TestCase(
                    nullableString,
                    nullableString,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    nullableString,
                    platformString,
                    expectedIdenticalResult = false,
                    expectedIgnoreNullabilityResult = true,
                )
            )
            add(
                TestCase(
                    platformString,
                    platformString,
                    expectedIdenticalResult = true,
                )
            )

            // Generics / Type Arguments
            val listString = classTypeItem("java.util.List", arguments = listOf(string))
            val listString2 = classTypeItem("java.util.List", arguments = listOf(string))
            val listNullableString =
                classTypeItem("java.util.List", arguments = listOf(nullableString))
            val listObject = classTypeItem("java.util.List", arguments = listOf(objectType))
            val setString = classTypeItem("java.util.Set", arguments = listOf(string))
            val rawList = classTypeItem("java.util.List", arguments = emptyList())

            add(
                TestCase(
                    listString,
                    listString2,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    listString,
                    listNullableString,
                    expectedIdenticalResult = false,
                    expectedIgnoreNullabilityResult = true,
                )
            )
            add(
                TestCase(
                    listString,
                    listObject,
                    expectedIdenticalResult = false,
                    expectedErasedResult = true,
                )
            )
            add(
                TestCase(
                    listString,
                    setString,
                    expectedIdenticalResult = false,
                )
            )
            add(
                TestCase(
                    listString,
                    rawList,
                    expectedIdenticalResult = false,
                    expectedErasedResult = true,
                )
            )

            val mapStringInt =
                classTypeItem(
                    "java.util.Map",
                    arguments = listOf(string, classTypeItem("java.lang.Integer"))
                )
            val mapStringInt2 =
                classTypeItem(
                    "java.util.Map",
                    arguments = listOf(string, classTypeItem("java.lang.Integer"))
                )
            val mapStringLong =
                classTypeItem(
                    "java.util.Map",
                    arguments = listOf(string, classTypeItem("java.lang.Long"))
                )

            add(
                TestCase(
                    mapStringInt,
                    mapStringInt2,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    mapStringInt,
                    mapStringLong,
                    expectedIdenticalResult = false,
                    expectedErasedResult = true,
                )
            )

            val listListString = classTypeItem("java.util.List", arguments = listOf(listString))
            val listListString2 = classTypeItem("java.util.List", arguments = listOf(listString))
            val listListNullableString =
                classTypeItem("java.util.List", arguments = listOf(listNullableString))

            add(
                TestCase(
                    listListString,
                    listListString2,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    listListString,
                    listListNullableString,
                    expectedIdenticalResult = false,
                    expectedIgnoreNullabilityResult = true,
                )
            )

            // Outer / Inner Classes
            val outer1 = classTypeItem("test.pkg.Outer1")
            val outer2 = classTypeItem("test.pkg.Outer2")
            val inner1 = classTypeItem("test.pkg.Outer1.Inner", outerClassType = outer1)
            val inner2 = classTypeItem("test.pkg.Outer1.Inner", outerClassType = outer1)
            val inner3 = classTypeItem("test.pkg.Outer2.Inner", outerClassType = outer2)
            val innerNoOuter = classTypeItem("test.pkg.Outer1.Inner")

            add(
                TestCase(
                    inner1,
                    inner2,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    inner1,
                    inner3,
                    expectedIdenticalResult = false,
                )
            )
            add(
                TestCase(
                    inner1,
                    innerNoOuter,
                    expectedIdenticalResult = false,
                )
            )

            // Arrays
            val intArray = arrayTypeItem(intType)
            val intArray2 = arrayTypeItem(intType)
            val longArray = arrayTypeItem(longType)
            val intVarargs = arrayTypeItem(intType, isVarargs = true)
            val intVarargs2 = arrayTypeItem(intType, isVarargs = true)
            val stringArray = arrayTypeItem(string)
            val nullableStringArray = arrayTypeItem(nullableString)
            val stringArrayNullable = stringArray.substitute(TypeNullability.NULLABLE)
            val int2dArray = arrayTypeItem(intArray)
            val int2dArray2 = arrayTypeItem(intArray)

            add(
                TestCase(
                    intArray,
                    intArray2,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    intArray,
                    longArray,
                    expectedIdenticalResult = false,
                )
            )
            add(
                TestCase(
                    intArray,
                    intVarargs,
                    expectedIdenticalResult = false,
                    expectedErasedResult = true,
                )
            )
            add(
                TestCase(
                    intVarargs,
                    intVarargs2,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    stringArray,
                    nullableStringArray,
                    expectedIdenticalResult = false,
                    expectedIgnoreNullabilityResult = true,
                )
            )
            add(
                TestCase(
                    stringArray,
                    stringArrayNullable,
                    expectedIdenticalResult = false,
                    expectedIgnoreNullabilityResult = true,
                )
            )
            add(
                TestCase(
                    int2dArray,
                    int2dArray2,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    int2dArray,
                    intArray,
                    expectedIdenticalResult = false,
                )
            )

            // Type Variables
            val typeParameterT = typeParameterItem("T")
            val typeVarT = variableTypeItem(typeParameterT)
            val typeVarTSameParam = variableTypeItem(typeParameterT)

            val typeParameterTDiffParam = typeParameterItem("T")
            val typeVarTDiffParam = variableTypeItem(typeParameterTDiffParam)

            val typeParameterTWithBounds = typeParameterItem("T", bounds = listOf(string))
            val typeVarTWithBounds = variableTypeItem(typeParameterTWithBounds)
            val typeParameterTWithBounds2 = typeParameterItem("T", bounds = listOf(string))
            val typeVarTWithBounds2 = variableTypeItem(typeParameterTWithBounds2)

            val typeVarU = variableTypeItem("U")
            val nullableTypeVarT = typeVarT.substitute(TypeNullability.NULLABLE)

            // Two VariableTypeItems with the same TypeParameterItem
            add(
                TestCase(
                    typeVarT,
                    typeVarTSameParam,
                    expectedIdenticalResult = true,
                )
            )
            // Two VariableTypeItems with different TypeParameterItems but the same name and bounds
            add(
                TestCase(
                    typeVarT,
                    typeVarTDiffParam,
                    expectedIdenticalResult = false,
                    expectedStrictResult = true,
                )
            )
            // Two VariableTypeItems with different TypeParameterItems but the same name and
            // non-empty bounds
            add(
                TestCase(
                    typeVarTWithBounds,
                    typeVarTWithBounds2,
                    expectedIdenticalResult = false,
                    expectedStrictResult = true,
                )
            )
            add(
                TestCase(
                    typeVarT,
                    typeVarU,
                    expectedIdenticalResult = false,
                    expectedErasedResult = true,
                )
            )
            add(
                TestCase(
                    typeVarT,
                    nullableTypeVarT,
                    expectedIdenticalResult = false,
                    expectedIgnoreNullabilityResult = true,
                )
            )

            // Wildcards
            val wildcard = wildcardTypeItem()
            val wildcard2 = wildcardTypeItem()
            val wildcardExtendsString = wildcardTypeItem(extendsBound = string)
            val wildcardExtendsString2 = wildcardTypeItem(extendsBound = string)
            val wildcardExtendsNullableString = wildcardTypeItem(extendsBound = nullableString)
            val wildcardExtendsObject = wildcardTypeItem(extendsBound = objectType)
            val wildcardSuperString = wildcardTypeItem(superBound = string)
            val wildcardSuperString2 = wildcardTypeItem(superBound = string)
            val wildcardSuperObject = wildcardTypeItem(superBound = objectType)

            add(
                TestCase(
                    wildcard,
                    wildcard2,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    wildcardExtendsString,
                    wildcardExtendsString2,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    wildcardExtendsString,
                    wildcardExtendsNullableString,
                    expectedIdenticalResult = false,
                    expectedIgnoreNullabilityResult = true,
                )
            )
            add(
                TestCase(
                    wildcardExtendsString,
                    wildcardExtendsObject,
                    expectedIdenticalResult = false,
                )
            )
            add(
                TestCase(
                    wildcardExtendsString,
                    wildcard,
                    expectedIdenticalResult = false,
                )
            )
            add(
                TestCase(
                    wildcardSuperString,
                    wildcardSuperString2,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    wildcardSuperString,
                    wildcardSuperObject,
                    expectedIdenticalResult = false,
                )
            )
            add(
                TestCase(
                    wildcardSuperString,
                    wildcardExtendsString,
                    expectedIdenticalResult = false,
                    expectedFlattenedWildcardsResult = true,
                )
            )
            add(
                TestCase(
                    wildcardSuperString,
                    wildcard,
                    expectedIdenticalResult = false,
                )
            )

            // Wildcards in parameterized types
            val listWildcardExtendsString =
                classTypeItem("java.util.List", arguments = listOf(wildcardExtendsString))
            val listWildcardSuperString =
                classTypeItem("java.util.List", arguments = listOf(wildcardSuperString))
            val listWildcardExtendsNullableString =
                classTypeItem("java.util.List", arguments = listOf(wildcardExtendsNullableString))
            val listWildcard = classTypeItem("java.util.List", arguments = listOf(wildcard))

            add(
                TestCase(
                    listString,
                    listWildcardExtendsString,
                    expectedIdenticalResult = false,
                    expectedFlattenedWildcardsResult = true,
                    expectedErasedResult = true,
                )
            )
            add(
                TestCase(
                    listString,
                    listWildcardSuperString,
                    expectedIdenticalResult = false,
                    expectedFlattenedWildcardsResult = true,
                    expectedErasedResult = true,
                )
            )
            add(
                TestCase(
                    listWildcardExtendsString,
                    listWildcardSuperString,
                    expectedIdenticalResult = false,
                    expectedFlattenedWildcardsResult = true,
                    expectedErasedResult = true,
                )
            )
            add(
                TestCase(
                    listString,
                    listWildcard,
                    expectedIdenticalResult = false,
                    expectedErasedResult = true,
                )
            )
            add(
                TestCase(
                    listWildcardExtendsNullableString,
                    listString,
                    expectedIdenticalResult = false,
                    expectedErasedResult = true,
                )
            )
            add(
                TestCase(
                    listWildcardExtendsNullableString,
                    listNullableString,
                    expectedIdenticalResult = false,
                    expectedFlattenedWildcardsResult = true,
                    expectedErasedResult = true,
                )
            )
            add(
                TestCase(
                    listWildcardExtendsNullableString,
                    listWildcardExtendsString,
                    expectedIdenticalResult = false,
                    expectedFlattenedWildcardsResult = false,
                    expectedIgnoreNullabilityResult = true,
                    expectedErasedResult = true,
                )
            )

            // Annotations
            val annoA = annotationItem("test.pkg.A")
            val annoB = annotationItem("test.pkg.B")
            val annoModifiersA = TypeModifiers.create(listOf(annoA), TypeNullability.NONNULL)
            val annoModifiersB = TypeModifiers.create(listOf(annoB), TypeNullability.NONNULL)
            val annotatedStringA = string.substitute(annoModifiersA)
            val annotatedStringA2 = string.substitute(annoModifiersA)
            val annotatedStringB = string.substitute(annoModifiersB)

            add(
                TestCase(
                    annotatedStringA,
                    annotatedStringA2,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    annotatedStringA,
                    annotatedStringB,
                    expectedIdenticalResult = false,
                    expectedNullabilityAwareResult = true,
                )
            )
            add(
                TestCase(
                    annotatedStringA,
                    string,
                    expectedIdenticalResult = false,
                    expectedNullabilityAwareResult = true,
                )
            )

            val listAnnotatedStringA =
                classTypeItem("java.util.List", arguments = listOf(annotatedStringA))
            val listAnnotatedStringA2 =
                classTypeItem("java.util.List", arguments = listOf(annotatedStringA))
            val listAnnotatedStringB =
                classTypeItem("java.util.List", arguments = listOf(annotatedStringB))

            add(
                TestCase(
                    listAnnotatedStringA,
                    listAnnotatedStringA2,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    listAnnotatedStringA,
                    listAnnotatedStringB,
                    expectedIdenticalResult = false,
                    expectedNullabilityAwareResult = true,
                )
            )
            add(
                TestCase(
                    listAnnotatedStringA,
                    listString,
                    expectedIdenticalResult = false,
                    expectedNullabilityAwareResult = true,
                )
            )

            // Nulls
            add(
                TestCase(
                    null,
                    null,
                    expectedIdenticalResult = true,
                )
            )
            add(
                TestCase(
                    string,
                    null,
                    expectedIdenticalResult = false,
                )
            )
            add(
                TestCase(
                    null,
                    string,
                    expectedIdenticalResult = false,
                )
            )

            // Cross-kind comparisons
            add(
                TestCase(
                    intType,
                    classTypeItem("java.lang.Integer"),
                    expectedIdenticalResult = false,
                )
            )
            add(
                TestCase(
                    intType,
                    intArray,
                    expectedIdenticalResult = false,
                )
            )
            add(
                TestCase(
                    string,
                    stringArray,
                    expectedIdenticalResult = false,
                )
            )
            add(
                TestCase(
                    string,
                    typeVarT,
                    expectedIdenticalResult = false,
                )
            )
            add(
                TestCase(
                    string,
                    wildcardExtendsString,
                    expectedIdenticalResult = false,
                    expectedFlattenedWildcardsResult = true,
                )
            )
            add(
                TestCase(
                    intArray,
                    typeVarT,
                    expectedIdenticalResult = false,
                )
            )
            add(
                TestCase(
                    typeVarTWithBounds,
                    string,
                    expectedIdenticalResult = false,
                    expectedErasedResult = true,
                )
            )
            add(
                TestCase(
                    typeVarTWithBounds,
                    objectType,
                    expectedIdenticalResult = false,
                )
            )
        }

        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params(): List<TestCase> = testCases
    }

    @Test
    fun `test identical comparator`() {
        assertEquals(
            testCase.expectedIdenticalResult,
            TypeComparator.IDENTICAL.compare(testCase.type1, testCase.type2),
            message = "compare(type1, type2)",
        )
        // Also verify symmetry
        assertEquals(
            testCase.expectedIdenticalResult,
            TypeComparator.IDENTICAL.compare(testCase.type2, testCase.type1),
            message = "compare(type2, type1)",
        )
        if (testCase.expectedIdenticalResult) {
            assertEquals(
                TypeComparator.IDENTICAL.hash(testCase.type1),
                TypeComparator.IDENTICAL.hash(testCase.type2),
                message = "hash",
            )
        }
    }

    @Test
    fun `test strict comparator`() {
        assertEquals(
            testCase.expectedStrictResult,
            TypeComparator.STRICT.compare(testCase.type1, testCase.type2),
            message = "compare(type1, type2)",
        )
        // Also verify symmetry
        assertEquals(
            testCase.expectedStrictResult,
            TypeComparator.STRICT.compare(testCase.type2, testCase.type1),
            message = "compare(type2, type1)",
        )
        if (testCase.expectedStrictResult) {
            assertEquals(
                TypeComparator.STRICT.hash(testCase.type1),
                TypeComparator.STRICT.hash(testCase.type2),
                message = "hash",
            )
        }
    }

    @Test
    fun `test nullability aware comparator`() {
        assertEquals(
            testCase.expectedNullabilityAwareResult,
            TypeComparator.NULLABILITY_AWARE.compare(testCase.type1, testCase.type2),
            message = "compare(type1, type2)",
        )
        // Also verify symmetry
        assertEquals(
            testCase.expectedNullabilityAwareResult,
            TypeComparator.NULLABILITY_AWARE.compare(testCase.type2, testCase.type1),
            message = "compare(type2, type1)",
        )
        if (testCase.expectedNullabilityAwareResult) {
            assertEquals(
                TypeComparator.NULLABILITY_AWARE.hash(testCase.type1),
                TypeComparator.NULLABILITY_AWARE.hash(testCase.type2),
                message = "hash",
            )
        }
    }

    @Test
    fun `test ignore nullability comparator`() {
        assertEquals(
            testCase.expectedIgnoreNullabilityResult,
            TypeComparator.IGNORE_NULLABILITY.compare(testCase.type1, testCase.type2),
            message = "compare(type1, type2)",
        )
        // Also verify symmetry
        assertEquals(
            testCase.expectedIgnoreNullabilityResult,
            TypeComparator.IGNORE_NULLABILITY.compare(testCase.type2, testCase.type1),
            message = "compare(type2, type1)",
        )
        if (testCase.expectedIgnoreNullabilityResult) {
            assertEquals(
                TypeComparator.IGNORE_NULLABILITY.hash(testCase.type1),
                TypeComparator.IGNORE_NULLABILITY.hash(testCase.type2),
                message = "hash",
            )
        }
    }

    @Test
    fun `test erased comparator`() {
        assertEquals(
            testCase.expectedErasedResult,
            TypeComparator.ERASED.compare(testCase.type1, testCase.type2),
            message = "compare(type1, type2)",
        )
        // Also verify symmetry
        assertEquals(
            testCase.expectedErasedResult,
            TypeComparator.ERASED.compare(testCase.type2, testCase.type1),
            message = "compare(type2, type1)",
        )
        if (testCase.expectedErasedResult) {
            assertEquals(
                TypeComparator.ERASED.hash(testCase.type1),
                TypeComparator.ERASED.hash(testCase.type2),
                message = "hash",
            )
        }
    }

    @Test
    fun `test flattened wildcards comparator`() {
        assertEquals(
            testCase.expectedFlattenedWildcardsResult,
            TypeComparator.FLATTENED_WILDCARDS.compare(testCase.type1, testCase.type2),
            message = "compare(type1, type2)",
        )
        // Also verify symmetry
        assertEquals(
            testCase.expectedFlattenedWildcardsResult,
            TypeComparator.FLATTENED_WILDCARDS.compare(testCase.type2, testCase.type1),
            message = "compare(type2, type1)",
        )
        if (testCase.expectedFlattenedWildcardsResult) {
            assertEquals(
                TypeComparator.FLATTENED_WILDCARDS.hash(testCase.type1),
                TypeComparator.FLATTENED_WILDCARDS.hash(testCase.type2),
                message = "hash",
            )
        }
    }
}
