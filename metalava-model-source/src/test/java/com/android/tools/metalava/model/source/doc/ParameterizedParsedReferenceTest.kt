/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.source.doc

import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.source.doc.CallableSourceReference.SourceParameter
import com.android.tools.metalava.model.testing.arrayTypeItem
import com.android.tools.metalava.model.testing.classTypeItem
import com.android.tools.metalava.model.testing.primitiveTypeForKind
import com.android.tools.metalava.model.testing.typeParameterItem
import com.android.tools.metalava.model.testing.variableTypeItem
import com.android.tools.metalava.model.testing.wildcardTypeItem
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParameterizedParsedReferenceTest {

    @Parameterized.Parameter(0) internal lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    internal data class TestParams
    @EntryPoint
    constructor(
        val name: String,
        val reference: String = name,
        val expectedIssues: String = "",
        val expectedParsed: ParsedReference? = null,
        val expectedNormalized: String = reference,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String {
            return name
        }
    }

    companion object {
        /** Construct a [CallableSourceReference]. */
        private fun methodSourceReference(name: String, vararg parameters: SourceParameter) =
            CallableSourceReference(name, parameters.toList())

        private val params =
            listOf(
                // Packages
                TestParams(
                    name = "java",
                    expectedParsed = AmbiguousSourceReference(name = "java"),
                ),
                TestParams(
                    name = "java.util",
                    expectedParsed = AmbiguousSourceReference(name = "java.util"),
                ),

                // Classes
                TestParams(
                    name = "java.util.Map",
                    expectedParsed = AmbiguousSourceReference(name = "java.util.Map"),
                ),
                TestParams(
                    name = "java.util.Map.Entry",
                    expectedParsed = AmbiguousSourceReference(name = "java.util.Map.Entry"),
                ),

                // Fields (or maybe methods, no parentheses is ambiguous).
                TestParams(
                    name = "java.lang.Integer#MAX_VALUE",
                    expectedParsed =
                        QualifyingClassSourceReference(
                            className = "java.lang.Integer",
                            AmbiguousMemberSourceReference(name = "MAX_VALUE"),
                        ),
                ),
                TestParams(
                    name = "#MAX_VALUE",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member = AmbiguousMemberSourceReference(name = "MAX_VALUE"),
                        ),
                ),
                TestParams(
                    name = "MAX_VALUE",
                    expectedParsed = AmbiguousSourceReference(name = "MAX_VALUE"),
                ),

                // Methods with no parameters.
                TestParams(
                    name = "java.lang.Character#isWhitespace()",
                    expectedParsed =
                        QualifyingClassSourceReference(
                            className = "java.lang.Character",
                            member = methodSourceReference(name = "isWhitespace"),
                        ),
                ),
                TestParams(
                    name = "#isWhitespace()",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member = methodSourceReference(name = "isWhitespace"),
                        ),
                ),
                TestParams(
                    name = "isWhitespace()",
                    expectedParsed = methodSourceReference(name = "isWhitespace"),
                ),

                // Methods with one parameter (no name).
                TestParams(
                    name = "java.lang.String#toLowerCase(Locale)",
                    expectedParsed =
                        QualifyingClassSourceReference(
                            className = "java.lang.String",
                            member =
                                methodSourceReference(
                                    name = "toLowerCase",
                                    SourceParameter(classTypeItem("Locale")),
                                ),
                        ),
                ),
                TestParams(
                    // Not strictly valid (no # separating class and method) but still supported.
                    name = "java.lang.String.toLowerCase(Locale)",
                    expectedParsed =
                        methodSourceReference(
                            name = "java.lang.String.toLowerCase",
                            SourceParameter(classTypeItem("Locale")),
                        ),
                ),
                TestParams(
                    name = "#toLowerCase(Locale)",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member =
                                methodSourceReference(
                                    name = "toLowerCase",
                                    SourceParameter(classTypeItem("Locale")),
                                ),
                        ),
                ),
                TestParams(
                    name = "toLowerCase(Locale)",
                    expectedParsed =
                        methodSourceReference(
                            name = "toLowerCase",
                            SourceParameter(classTypeItem("Locale")),
                        ),
                ),

                // Methods with one parameter (with name).
                TestParams(
                    name = "java.lang.String#toLowerCase(Locale locale)",
                    expectedParsed =
                        QualifyingClassSourceReference(
                            className = "java.lang.String",
                            member =
                                methodSourceReference(
                                    name = "toLowerCase",
                                    SourceParameter(classTypeItem("Locale"), "locale"),
                                ),
                        ),
                    expectedNormalized = "java.lang.String#toLowerCase(Locale)",
                ),
                TestParams(
                    // Not strictly valid (no # separating class and method) but still supported.
                    name = "java.lang.String.toLowerCase(Locale locale)",
                    expectedParsed =
                        methodSourceReference(
                            name = "java.lang.String.toLowerCase",
                            SourceParameter(classTypeItem("Locale"), "locale"),
                        ),
                    expectedNormalized = "java.lang.String.toLowerCase(Locale)",
                ),
                TestParams(
                    name = "#toLowerCase(Locale locale)",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member =
                                methodSourceReference(
                                    name = "toLowerCase",
                                    SourceParameter(classTypeItem("Locale"), "locale"),
                                ),
                        ),
                    expectedNormalized = "#toLowerCase(Locale)",
                ),
                TestParams(
                    name = "toLowerCase(Locale locale)",
                    expectedParsed =
                        methodSourceReference(
                            name = "toLowerCase",
                            SourceParameter(classTypeItem("Locale"), "locale"),
                        ),
                    expectedNormalized = "toLowerCase(Locale)",
                ),

                // Methods with two parameters (no names).
                TestParams(
                    name = "java.util.Map#put(K, V)",
                    expectedParsed =
                        QualifyingClassSourceReference(
                            className = "java.util.Map",
                            member =
                                methodSourceReference(
                                    name = "put",
                                    SourceParameter(variableTypeItem("K")),
                                    SourceParameter(variableTypeItem("V")),
                                ),
                        ),
                    expectedNormalized = "java.util.Map#put(K,V)",
                ),
                TestParams(
                    name = "#put(K, V)",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member =
                                methodSourceReference(
                                    name = "put",
                                    SourceParameter(variableTypeItem("K")),
                                    SourceParameter(variableTypeItem("V")),
                                ),
                        ),
                    expectedNormalized = "#put(K,V)",
                ),

                // Methods with two parameters (with names).
                TestParams(
                    name = "java.util.Map#put(K key, V value)",
                    expectedParsed =
                        QualifyingClassSourceReference(
                            className = "java.util.Map",
                            member =
                                methodSourceReference(
                                    name = "put",
                                    SourceParameter(variableTypeItem("K"), "key"),
                                    SourceParameter(variableTypeItem("V"), "value"),
                                ),
                        ),
                    expectedNormalized = "java.util.Map#put(K,V)",
                ),
                TestParams(
                    name = "#put(K key, V value)",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member =
                                methodSourceReference(
                                    name = "put",
                                    SourceParameter(variableTypeItem("K"), "key"),
                                    SourceParameter(variableTypeItem("V"), "value"),
                                ),
                        ),
                    expectedNormalized = "#put(K,V)",
                ),
                TestParams(
                    name = "put(K key, V value)",
                    expectedParsed =
                        methodSourceReference(
                            name = "put",
                            SourceParameter(variableTypeItem("K"), "key"),
                            SourceParameter(variableTypeItem("V"), "value"),
                        ),
                    expectedNormalized = "put(K,V)",
                ),
                TestParams(
                    name = "#bar(java.lang.Integer p1, java.lang.String)",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member =
                                methodSourceReference(
                                    name = "bar",
                                    SourceParameter(classTypeItem("java.lang.Integer"), "p1"),
                                    SourceParameter(classTypeItem("java.lang.String")),
                                ),
                        ),
                    expectedNormalized = "#bar(java.lang.Integer,java.lang.String)",
                ),
                TestParams(
                    name = "#bar(int[]p1,List<String>p2)",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member =
                                methodSourceReference(
                                    name = "bar",
                                    SourceParameter(
                                        arrayTypeItem(
                                            primitiveTypeForKind(PrimitiveTypeItem.Primitive.INT),
                                        ),
                                        "p1"
                                    ),
                                    SourceParameter(
                                        classTypeItem(
                                            "List",
                                            arguments = listOf(classTypeItem("String")),
                                        ),
                                        "p2"
                                    ),
                                ),
                        ),
                    expectedNormalized = "#bar(int[],List<String>)",
                ),

                // Methods with generic parameter types.
                TestParams(
                    name = "java.util.Collection#addAll(Collection<? extends E>)",
                    expectedParsed =
                        QualifyingClassSourceReference(
                            className = "java.util.Collection",
                            member =
                                methodSourceReference(
                                    name = "addAll",
                                    SourceParameter(
                                        classTypeItem(
                                            "Collection",
                                            arguments =
                                                listOf(wildcardTypeItem(variableTypeItem("E"))),
                                        ),
                                    ),
                                ),
                        ),
                    expectedNormalized = "java.util.Collection#addAll(Collection<? extends E>)",
                ),

                // Methods with pathological formatting.
                TestParams(
                    name = "whitespaces but no parameters",
                    reference = "Class#foo(   \t\n\r)",
                    expectedParsed =
                        QualifyingClassSourceReference(
                            className = "Class",
                            member = methodSourceReference(name = "foo"),
                        ),
                    expectedNormalized = "Class#foo()",
                ),
                TestParams(
                    name = "leading and trailing whitespace",
                    reference = "Class#foo(   int    )",
                    expectedParsed =
                        QualifyingClassSourceReference(
                            className = "Class",
                            member =
                                methodSourceReference(
                                    name = "foo",
                                    SourceParameter(
                                        primitiveTypeForKind(PrimitiveTypeItem.Primitive.INT),
                                    ),
                                ),
                        ),
                    expectedNormalized = "Class#foo(int)",
                ),
                TestParams(
                    name = "complex",
                    reference =
                        "Class#foo(   Collection<? extends Bar> [  ]   param   ,   Map< Integer  , List <String > >   )",
                    // TODO(b/447588621): This should not be reported.
                    expectedIssues =
                        "1:1: Could not parse type `Collection<? extends Bar> [  ]`. Found unexpected string after type parameters:  [  ] [TypeParseError]",
                    expectedParsed =
                        QualifyingClassSourceReference(
                            className = "Class",
                            member =
                                methodSourceReference(
                                    name = "foo",
                                    SourceParameter(
                                        classTypeItem(
                                            "Collection",
                                            arguments =
                                                listOf(wildcardTypeItem(classTypeItem("Bar"))),
                                        ),
                                        "param"
                                    ),
                                    SourceParameter(
                                        classTypeItem(
                                            "Map",
                                            arguments =
                                                listOf(
                                                    classTypeItem("Integer"),
                                                    classTypeItem(
                                                        // TODO(b/447588621): This should not have
                                                        //  a trailing whitespace.
                                                        "List ",
                                                        arguments =
                                                            listOf(
                                                                classTypeItem("String"),
                                                            ),
                                                    ),
                                                ),
                                        ),
                                    ),
                                ),
                        ),
                    expectedNormalized =
                        // TODO(b/447588621): The [] is missing.
                        "Class#foo(Collection<? extends Bar>,Map<Integer,List <String>>)",
                ),
                TestParams(
                    name = "qualified type with spaces",
                    reference = "Class#foo(   java . lang . String   )",
                    expectedParsed =
                        QualifyingClassSourceReference(
                            className = "Class",
                            member =
                                methodSourceReference(
                                    name = "foo",
                                    SourceParameter(classTypeItem("java . lang . String")),
                                ),
                        ),
                    expectedNormalized = "Class#foo(java . lang . String)",
                ),

                // Fragment reference
                TestParams(
                    name = "java.util.Collection##optional-restrictions",
                    expectedParsed =
                        QualifyingClassSourceReference(
                            className = "java.util.Collection",
                            member =
                                UriFragmentSourceReference(uriFragment = "optional-restrictions"),
                        ),
                ),
                TestParams(
                    name = "##optional-restrictions",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member =
                                UriFragmentSourceReference(uriFragment = "optional-restrictions"),
                        ),
                    expectedNormalized = "##optional-restrictions",
                ),

                // Invalid references
                TestParams(
                    name = "<empty>",
                    reference = "",
                ),
                TestParams(
                    name = ".java.util.List",
                ),
                TestParams(
                    name = "List<String>",
                ),
                TestParams(
                    name = "unbalanced-parentheses",
                    reference = "#method(int",
                ),
                TestParams(
                    name = "trailing-junk",
                    reference = "#method(int),",
                ),
                TestParams(
                    name = "missing-method",
                    reference = "#()",
                ),
                TestParams(
                    name = "just-empty-parentheses",
                    reference = "()",
                ),
                TestParams(
                    name = "just-parentheses",
                    reference = "(blah)",
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "{0}") internal fun params() = params

        /** The type parameter items referenced in [params]. */
        private val typeParameterScope =
            TypeParameterScope.empty.nestedScope(
                "test",
                listOf(
                    typeParameterItem("K"),
                    typeParameterItem("V"),
                    typeParameterItem("E"),
                )
            )
    }

    internal val reporter = CollatingDocumentationIssueReporter()

    val docTypeParser = DocTypeParser.create(reporter, typeParameterScope)

    @Test
    fun `Test parsing`() {
        val parsed = LabeledRefTagType.parseReference(params.reference, docTypeParser)
        reporter.assertJavadocParserIssues(params.expectedIssues)
        assertEquals(params.expectedParsed, parsed)
    }

    @Test
    fun `Test normalized form`() {
        assumeNotNull(params.expectedParsed)

        val parsed = LabeledRefTagType.parseReference(params.reference, docTypeParser)
        assertEquals(params.expectedNormalized, parsed!!.normalizedForm)
    }
}
