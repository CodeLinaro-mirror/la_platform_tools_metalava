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
        /** Construct a [MethodSourceReference]. */
        private fun methodSourceReference(name: String, parameters: String = "()") =
            MethodSourceReference(name, parameters)

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
                                    parameters = "(Locale)",
                                ),
                        ),
                ),
                TestParams(
                    // Not strictly valid (no # separating class and method) but still supported.
                    name = "java.lang.String.toLowerCase(Locale)",
                    expectedParsed =
                        methodSourceReference(
                            name = "java.lang.String.toLowerCase",
                            parameters = "(Locale)",
                        ),
                ),
                TestParams(
                    name = "#toLowerCase(Locale)",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member =
                                methodSourceReference(
                                    name = "toLowerCase",
                                    parameters = "(Locale)",
                                ),
                        ),
                ),
                TestParams(
                    name = "toLowerCase(Locale)",
                    expectedParsed =
                        methodSourceReference(
                            name = "toLowerCase",
                            parameters = "(Locale)",
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
                                    parameters = "(Locale locale)",
                                ),
                        ),
                ),
                TestParams(
                    // Not strictly valid (no # separating class and method) but still supported.
                    name = "java.lang.String.toLowerCase(Locale locale)",
                    expectedParsed =
                        methodSourceReference(
                            name = "java.lang.String.toLowerCase",
                            parameters = "(Locale locale)",
                        ),
                ),
                TestParams(
                    name = "#toLowerCase(Locale locale)",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member =
                                methodSourceReference(
                                    name = "toLowerCase",
                                    parameters = "(Locale locale)",
                                ),
                        ),
                ),
                TestParams(
                    name = "toLowerCase(Locale locale)",
                    expectedParsed =
                        methodSourceReference(
                            name = "toLowerCase",
                            parameters = "(Locale locale)",
                        ),
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
                                    parameters = "(K, V)",
                                ),
                        ),
                ),
                TestParams(
                    name = "#put(K, V)",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member =
                                methodSourceReference(
                                    name = "put",
                                    parameters = "(K, V)",
                                ),
                        ),
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
                                    parameters = "(K key, V value)",
                                ),
                        ),
                ),
                TestParams(
                    name = "#put(K key, V value)",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member =
                                methodSourceReference(
                                    name = "put",
                                    parameters = "(K key, V value)",
                                ),
                        ),
                ),
                TestParams(
                    name = "put(K key, V value)",
                    expectedParsed =
                        methodSourceReference(
                            name = "put",
                            parameters = "(K key, V value)",
                        ),
                ),
                TestParams(
                    name = "#bar(java.lang.Integer p1, java.lang.String)",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member =
                                methodSourceReference(
                                    name = "bar",
                                    parameters = "(java.lang.Integer p1, java.lang.String)",
                                ),
                        ),
                    expectedNormalized = "#bar(java.lang.Integer p1, java.lang.String)",
                ),
                TestParams(
                    name = "#bar(int[]p1,List<String>p2)",
                    expectedParsed =
                        CurrentClassSourceReference(
                            member =
                                methodSourceReference(
                                    name = "bar",
                                    parameters = "(int[]p1,List<String>p2)",
                                ),
                        ),
                    expectedNormalized = "#bar(int[]p1,List<String>p2)",
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
                                    parameters = "(Collection<? extends E>)",
                                ),
                        ),
                ),

                // Methods with pathological formatting.
                TestParams(
                    name = "whitespaces but no parameters",
                    reference = "Class#foo(   \t\n\r)",
                    expectedParsed =
                        QualifyingClassSourceReference(
                            className = "Class",
                            member =
                                methodSourceReference(
                                    name = "foo",
                                    parameters = "(   \t\n\r)",
                                ),
                        ),
                    expectedNormalized = "Class#foo(   \t\n\r)",
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
                                    parameters = "(   int    )",
                                ),
                        ),
                    expectedNormalized = "Class#foo(   int    )",
                ),
                TestParams(
                    name = "complex",
                    reference =
                        "Class#foo(   Collection<? extends Bar> [  ]   param   ,   Map< Integer  , List <String > >   )",
                    expectedParsed =
                        QualifyingClassSourceReference(
                            className = "Class",
                            member =
                                methodSourceReference(
                                    name = "foo",
                                    parameters =
                                        "(   Collection<? extends Bar> [  ]   param   ,   Map< Integer  , List <String > >   )",
                                ),
                        ),
                    expectedNormalized =
                        "Class#foo(   Collection<? extends Bar> [  ]   param   ,   Map< Integer  , List <String > >   )",
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
                                    parameters = "(   java . lang . String   )",
                                ),
                        ),
                    expectedNormalized = "Class#foo(   java . lang . String   )",
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
    }

    @Test
    fun `Test parsing`() {
        val parsed = LabeledRefTagType.parseReference(params.reference)
        assertEquals(params.expectedParsed, parsed)
    }

    @Test
    fun `Test normalized form`() {
        assumeNotNull(params.expectedParsed)

        val parsed = LabeledRefTagType.parseReference(params.reference)
        assertEquals(params.expectedNormalized, parsed!!.normalizedForm)
    }
}
