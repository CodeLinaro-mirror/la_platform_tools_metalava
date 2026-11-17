/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.JAVA_LANG_OBJECT
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.parser.SharedLexer
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.testing.arrayTypeItem
import com.android.tools.metalava.model.testing.classTypeItem
import com.android.tools.metalava.model.testing.primitiveTypeForKind
import com.android.tools.metalava.model.testing.stringType
import com.android.tools.metalava.model.testing.typeParameterItem
import com.android.tools.metalava.model.testing.value.annotationItem
import com.android.tools.metalava.model.testing.variableTypeItem
import com.android.tools.metalava.model.testing.wildcardTypeItem
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParameterizedTypeItemParserTest {

    @Parameterized.Parameter(0) lateinit var parserProvider: TypeItemParserProvider<TestCase>

    @Parameterized.Parameter(1) lateinit var testCase: TestCase

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestCase] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { testCase.entryPointCallerTracker }

    data class TestCase
    @EntryPoint
    constructor(
        val typeString: String,
        val expectedType: TypeItem,
        val typeParameterScope: TypeParameterScope = TypeParameterScope.empty,
        val kotlinStyleNulls: Boolean = false,
        val contextNullability: ContextNullability = ContextNullability.none,
        val unqualifiedClassHandler: UnqualifiedClassHandler =
            UnqualifiedClassHandler.PREFIX_WITH_JAVA_LANG_OR_REPORT_ERROR,
        val expectedIssues: String = "",
        val description: String? = null,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String =
            if (description != null) "$typeString ($description)" else typeString
    }

    companion object {
        private fun annotation(source: String): AnnotationItem =
            AnnotationItem.createFromSource(AnnotationContext.DEFAULT_RESOLVE_NULL, source)!!

        private val annoA = annotationItem("A")
        private val annoB = annotationItem("B")
        private val annoC = annotationItem("C")
        private val nonNullAnno = annotationItem("NonNull")

        private val objectPlatformType =
            classTypeItem(JAVA_LANG_OBJECT, nullability = TypeNullability.PLATFORM)
        private val numberPlatformType =
            classTypeItem("java.lang.Number", nullability = TypeNullability.PLATFORM)
        private val stringPlatformType = stringType(nullability = TypeNullability.PLATFORM)

        private val tParam = typeParameterItem("T")
        private val stringParam = typeParameterItem("String")
        private val intParam = typeParameterItem("int")
        private val testScope =
            TypeParameterScope.empty.nestedScope(
                "test",
                listOf(tParam, stringParam, intParam),
            )

        private val primitiveTypeCases =
            Primitive.entries.map { primitive ->
                TestCase(
                    typeString = primitive.primitiveName,
                    expectedType = primitiveTypeForKind(primitive),
                )
            } +
                listOf(
                    TestCase(
                        typeString = "int?",
                        expectedType = primitiveTypeForKind(Primitive.INT),
                        expectedIssues =
                            "3: Format does not support Kotlin-style null type syntax: int? [TypeParseError]\n" +
                                "0: Invalid nullability suffix on primitive: int? [TypeParseError]",
                    ),
                )

        private val variableTypeCases =
            listOf(
                TestCase(
                    typeString = "T",
                    expectedType =
                        variableTypeItem(tParam, nullability = TypeNullability.UNDEFINED),
                    typeParameterScope = testScope,
                ),
                TestCase(
                    typeString = "String",
                    description = "shadowed by type variable",
                    expectedType =
                        variableTypeItem(stringParam, nullability = TypeNullability.UNDEFINED),
                    typeParameterScope = testScope,
                ),
                TestCase(
                    typeString = "int",
                    description = "shadowed by type variable",
                    expectedType =
                        variableTypeItem(intParam, nullability = TypeNullability.UNDEFINED),
                    typeParameterScope = testScope,
                ),
            )

        private val classTypeCases =
            listOf(
                TestCase(
                    typeString = "dynamic",
                    expectedType = classTypeItem("dynamic", nullability = TypeNullability.PLATFORM),
                ),
                TestCase(
                    typeString = "String",
                    expectedType = stringPlatformType,
                ),
                TestCase(
                    typeString = "String",
                    description = "forceNonNull",
                    contextNullability = ContextNullability.forceNonNull,
                    expectedType = stringType(nullability = TypeNullability.NONNULL),
                ),
                TestCase(
                    typeString = "String",
                    description = "inferNullability",
                    contextNullability =
                        ContextNullability(inferNullability = { TypeNullability.NULLABLE }),
                    expectedType = stringType(nullability = TypeNullability.NULLABLE),
                ),
                TestCase(
                    typeString = "java.lang.String",
                    expectedType = stringPlatformType,
                ),
                TestCase(
                    typeString = "java.util.List<java.lang.String>",
                    expectedType =
                        classTypeItem(
                            "java.util.List",
                            arguments = listOf(stringPlatformType),
                            nullability = TypeNullability.PLATFORM,
                        ),
                ),
                TestCase(
                    typeString = "test.pkg.Outer<a.P1>.Inner<b.P2>",
                    expectedType =
                        classTypeItem(
                            "test.pkg.Outer.Inner",
                            arguments =
                                listOf(
                                    classTypeItem("b.P2", nullability = TypeNullability.PLATFORM),
                                ),
                            outerClassType =
                                classTypeItem(
                                    "test.pkg.Outer",
                                    arguments =
                                        listOf(
                                            classTypeItem(
                                                "a.P1",
                                                nullability = TypeNullability.PLATFORM,
                                            ),
                                        ),
                                    nullability = TypeNullability.NONNULL,
                                ),
                            nullability = TypeNullability.PLATFORM,
                        ),
                ),
                TestCase(
                    typeString = "Comparable<test.pkg.Foo>blah2",
                    kotlinStyleNulls = true,
                    expectedType =
                        classTypeItem(
                            "java.lang.Comparable",
                            arguments = listOf(classTypeItem("test.pkg.Foo")),
                        ),
                    expectedIssues =
                        "24: Could not parse type `Comparable<test.pkg.Foo>blah2`. Found unexpected string after type parameters: blah2 [TypeParseError]",
                ),
            )

        private val wildcardTypeCases =
            listOf(
                TestCase(
                    typeString = "?",
                    expectedType = wildcardTypeItem(extendsBound = objectPlatformType),
                ),
                TestCase(
                    typeString = "? extends Number",
                    expectedType = wildcardTypeItem(extendsBound = numberPlatformType),
                ),
                TestCase(
                    typeString = "? super Number",
                    expectedType =
                        wildcardTypeItem(
                            extendsBound = objectPlatformType,
                            superBound = numberPlatformType,
                        ),
                ),
                TestCase(
                    typeString = "Comparable<? blah1>",
                    expectedType =
                        classTypeItem(
                            "java.lang.Comparable",
                            arguments = listOf(wildcardTypeItem(extendsBound = objectPlatformType)),
                            nullability = TypeNullability.PLATFORM,
                        ),
                    expectedIssues =
                        "11: Type starts with \"?\" but doesn't appear to be wildcard: ? blah1 [TypeParseError]",
                ),
            )

        private val arrayTypeCases =
            listOf(
                TestCase(
                    typeString = "String[]",
                    expectedType =
                        arrayTypeItem(
                            stringPlatformType,
                            nullability = TypeNullability.PLATFORM,
                        ),
                ),
                TestCase(
                    typeString = "String...",
                    expectedType =
                        arrayTypeItem(
                            stringPlatformType,
                            isVarargs = true,
                            nullability = TypeNullability.PLATFORM,
                        ),
                ),
                TestCase(
                    typeString = "? extends java.lang.String[]",
                    expectedType =
                        wildcardTypeItem(
                            extendsBound =
                                arrayTypeItem(
                                    stringPlatformType,
                                    nullability = TypeNullability.PLATFORM,
                                ),
                        ),
                ),
                TestCase(
                    typeString = "String![]![]?",
                    kotlinStyleNulls = true,
                    expectedType =
                        arrayTypeItem(
                            arrayTypeItem(
                                stringPlatformType,
                                nullability = TypeNullability.PLATFORM,
                            ),
                            nullability = TypeNullability.NULLABLE,
                        ),
                ),
            )

        private val mapEntryType =
            classTypeItem(
                "java.util.Map.Entry",
                arguments =
                    listOf(
                        classTypeItem("a.A", nullability = TypeNullability.PLATFORM),
                        classTypeItem("b.B", nullability = TypeNullability.PLATFORM),
                    ),
                outerClassType =
                    classTypeItem("java.util.Map", nullability = TypeNullability.NONNULL),
                nullability = TypeNullability.NONNULL,
                annotations = listOf(nonNullAnno),
            )

        private val annotatedTypeCases =
            listOf(
                TestCase(
                    typeString = "@androidx.annotation.IntRange(from=1, to=5) int",
                    expectedType =
                        primitiveTypeForKind(
                            Primitive.INT,
                            annotations =
                                listOf(annotation("@androidx.annotation.IntRange(from=1, to=5)")),
                        ),
                ),
                TestCase(
                    typeString = "@A @B test.pkg.Foo",
                    expectedType =
                        classTypeItem(
                            "test.pkg.Foo",
                            nullability = TypeNullability.PLATFORM,
                            annotations = listOf(annoA, annoB),
                        ),
                ),
                TestCase(
                    typeString = "java.lang.annotation.@NonNull Annotation",
                    expectedType =
                        classTypeItem(
                            "java.lang.annotation.Annotation",
                            nullability = TypeNullability.NONNULL,
                            annotations = listOf(nonNullAnno),
                        ),
                ),
                TestCase(
                    typeString = "java.util.Map.@NonNull Entry<a.A,b.B>",
                    expectedType = mapEntryType,
                ),
                TestCase(
                    typeString = "java.util.@NonNull Set<java.util.Map.@NonNull Entry<a.A,b.B>>",
                    expectedType =
                        classTypeItem(
                            "java.util.Set",
                            arguments = listOf(mapEntryType),
                            nullability = TypeNullability.NONNULL,
                            annotations = listOf(nonNullAnno),
                        ),
                ),
                TestCase(
                    typeString =
                        "java.util.@test.pkg.A(a = \"hi@\", b = 0) @test.pkg.B(v = \"<hi>\") List<java.lang.@test.pkg.B(v = \"@\") String>",
                    expectedType =
                        classTypeItem(
                            "java.util.List",
                            arguments =
                                listOf(
                                    stringType(
                                        nullability = TypeNullability.PLATFORM,
                                        annotations = listOf(annotation("@test.pkg.B(v = \"@\")")),
                                    ),
                                ),
                            nullability = TypeNullability.PLATFORM,
                            annotations =
                                listOf(
                                    annotation("@test.pkg.A(a = \"hi@\", b = 0)"),
                                    annotation("@test.pkg.B(v = \"<hi>\")"),
                                ),
                        ),
                ),
                TestCase(
                    typeString = "test.pkg.@test.pkg.A Outer<a.P1>.@test.pkg.B Inner<b.P2>",
                    expectedType =
                        classTypeItem(
                            "test.pkg.Outer.Inner",
                            arguments =
                                listOf(
                                    classTypeItem("b.P2", nullability = TypeNullability.PLATFORM),
                                ),
                            outerClassType =
                                classTypeItem(
                                    "test.pkg.Outer",
                                    arguments =
                                        listOf(
                                            classTypeItem(
                                                "a.P1",
                                                nullability = TypeNullability.PLATFORM,
                                            ),
                                        ),
                                    nullability = TypeNullability.NONNULL,
                                    annotations = listOf(annotation("@test.pkg.A")),
                                ),
                            nullability = TypeNullability.PLATFORM,
                            annotations = listOf(annotation("@test.pkg.B")),
                        ),
                ),
                TestCase(
                    typeString = "test.pkg.@A @B Foo @B @C []",
                    expectedType =
                        arrayTypeItem(
                            classTypeItem(
                                "test.pkg.Foo",
                                nullability = TypeNullability.PLATFORM,
                                annotations = listOf(annoA, annoB),
                            ),
                            nullability = TypeNullability.PLATFORM,
                            annotations = listOf(annoB, annoC),
                        ),
                ),
                TestCase(
                    typeString = "java.lang.annotation.@NonNull Annotation @NonNull []",
                    expectedType =
                        arrayTypeItem(
                            classTypeItem(
                                "java.lang.annotation.Annotation",
                                nullability = TypeNullability.NONNULL,
                                annotations = listOf(nonNullAnno),
                            ),
                            nullability = TypeNullability.NONNULL,
                            annotations = listOf(nonNullAnno),
                        ),
                ),
                TestCase(
                    typeString = "char @NonNull []",
                    expectedType =
                        arrayTypeItem(
                            primitiveTypeForKind(Primitive.CHAR),
                            nullability = TypeNullability.NONNULL,
                            annotations = listOf(nonNullAnno),
                        ),
                ),
                TestCase(
                    typeString = "@A String! @B []! @C []?",
                    kotlinStyleNulls = true,
                    expectedType =
                        arrayTypeItem(
                            arrayTypeItem(
                                stringType(
                                    nullability = TypeNullability.PLATFORM,
                                    annotations = listOf(annoA),
                                ),
                                nullability = TypeNullability.PLATFORM,
                                annotations = listOf(annoC),
                            ),
                            nullability = TypeNullability.NULLABLE,
                            annotations = listOf(annoB),
                        ),
                ),
            )

        private val allTestCases =
            primitiveTypeCases +
                variableTypeCases +
                classTypeCases +
                wildcardTypeCases +
                arrayTypeCases +
                annotatedTypeCases

        private val parserProviders =
            listOf(
                TypeItemParserProvider(
                    "Default",
                    ::DefaultTypeItemParser,
                    allTestCases,
                ),
            )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}: {1}")
        fun params() = parserProviders.toTestParameters()
    }

    @Test
    fun `Test obtainTypeFromString`() {
        val errorReporter = CollatingErrorReporter()
        val typeParser =
            parserProvider.createParser(
                unqualifiedClassHandler = testCase.unqualifiedClassHandler,
                kotlinStyleNulls = testCase.kotlinStyleNulls,
                errorReporter = errorReporter,
            )
        val actual =
            typeParser.obtainTypeFromString(
                testCase.typeString,
                testCase.typeParameterScope,
                testCase.contextNullability,
            )
        assertEquals(testCase.expectedType, actual)
        assertEquals(testCase.expectedIssues, errorReporter.toString())
    }

    @Test
    fun `Test obtainTypeFromStream with trailing identifier`() {
        assumeTrue(
            testCase.expectedType !is WildcardTypeItem &&
                (testCase.expectedIssues.isEmpty() || testCase.typeString.endsWith("blah2"))
        )
        val errorReporter = CollatingErrorReporter()
        val typeParser =
            parserProvider.createParser(
                unqualifiedClassHandler = testCase.unqualifiedClassHandler,
                kotlinStyleNulls = testCase.kotlinStyleNulls,
                errorReporter = errorReporter,
            ) as DefaultTypeItemParser
        val (sourceText, expectedTrailingIdentifier) =
            if (testCase.typeString.endsWith("blah2")) {
                testCase.typeString to "blah2"
            } else {
                val separator =
                    if (testCase.typeString.endsWith(">") || testCase.typeString.endsWith("]")) ""
                    else " "
                "${testCase.typeString}${separator}paramName" to "paramName"
            }
        val tokens = SharedLexer(sourceText).tokenize()
        val actual =
            typeParser.obtainTypeFromStream(
                tokens = tokens,
                sourceText = sourceText,
                typeParameterScope = testCase.typeParameterScope,
                contextNullability = testCase.contextNullability,
            )
        assertEquals(testCase.expectedType, actual)
        assertEquals("", errorReporter.toString())
        val trailingToken = tokens.consume()
        assertEquals(SharedTokenType.IDENTIFIER, trailingToken.type)
        assertEquals(expectedTrailingIdentifier, trailingToken.text(sourceText))
    }
}
