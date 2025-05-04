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

package com.android.tools.metalava.model.value

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.testing.value.annotationItem
import com.android.tools.metalava.model.testing.value.annotationValue
import com.android.tools.metalava.model.testing.value.assertValuesAreStrictlyEqual
import com.android.tools.metalava.model.testing.value.literalValue
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Tests for [ValueParser.parseAnnotationItem]. */
@RunWith(Parameterized::class)
class ParameterizedAnnotationParserTestCase {

    /** The [TestCase] currently being tested. */
    @Parameterized.Parameter(0) lateinit var testCase: TestCase

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestCase] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { testCase.entryPointCallerTracker }

    class TestCase(
        private val label: String,
        val input: String,
        val expected: AnnotationItem,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString() = label
    }

    companion object {
        private val testCases =
            listOf(
                // Test without a leading @ just to show that they can be processed. Following tests
                // will all use @ as it looks more obviously an annotation.
                TestCase(
                    "no attribute list - no leading @",
                    input = "test.pkg.Anno",
                    expected = annotationItem("test.pkg.Anno"),
                ),
                TestCase(
                    "no attribute list - leading @",
                    input = "@test.pkg.Anno",
                    expected = annotationItem("test.pkg.Anno"),
                ),
                TestCase(
                    "empty attribute list",
                    input = "@test.pkg.Anno()",
                    expected = annotationItem("test.pkg.Anno"),
                ),
                TestCase(
                    "single attribute - no name",
                    input = "@test.pkg.Anno(1)",
                    expected =
                        annotationItem(
                            "test.pkg.Anno",
                            "value" to literalValue(1),
                        ),
                ),
                TestCase(
                    "single attribute - value name",
                    input = "@test.pkg.Anno(value = 1)",
                    expected =
                        annotationItem(
                            "test.pkg.Anno",
                            "value" to literalValue(1),
                        ),
                ),
                TestCase(
                    "single attribute - other name",
                    input = "@test.pkg.Anno(other = 1)",
                    expected =
                        annotationItem(
                            "test.pkg.Anno",
                            "other" to literalValue(1),
                        ),
                ),
                TestCase(
                    "single attribute - trailing comma",
                    // This is not strictly legal in Java, but it is in Kotlin and there are quite a
                    // few tests that do this.
                    input = "@test.pkg.Anno(other = 1,)",
                    expected =
                        annotationItem(
                            "test.pkg.Anno",
                            "other" to literalValue(1),
                        ),
                ),
                TestCase(
                    "multiple attributes - simple",
                    input = "@test.pkg.Anno(other = 1, another = 2L)",
                    expected =
                        annotationItem(
                            "test.pkg.Anno",
                            "other" to literalValue(1),
                            "another" to literalValue(2L),
                        ),
                ),
                TestCase(
                    "multiple attributes - trailing comma",
                    // This is not strictly legal in Java, but it is in Kotlin and there are quite a
                    // few tests that do this.
                    input = "@test.pkg.Anno(other = 1, another = 2L,)",
                    expected =
                        annotationItem(
                            "test.pkg.Anno",
                            "other" to literalValue(1),
                            "another" to literalValue(2L),
                        ),
                ),
                TestCase(
                    "legacy attributes - subtract one",
                    input = "@test.pkg.Anno(other = 0x40000000 - 1)",
                    expected =
                        annotationItem(
                            "test.pkg.Anno",
                            "other" to literalValue(0x3fffffff),
                        ),
                ),
                TestCase(
                    "nested annotation - no attributes",
                    input = "@test.pkg.Anno(other = @test.pkg.OtherAnno)",
                    expected =
                        annotationItem(
                            "test.pkg.Anno",
                            "other" to annotationValue("test.pkg.OtherAnno"),
                        ),
                ),
                TestCase(
                    "nested annotation - empty attributes",
                    input = "@test.pkg.Anno(other = @test.pkg.OtherAnno())",
                    expected =
                        annotationItem(
                            "test.pkg.Anno",
                            "other" to annotationValue("test.pkg.OtherAnno"),
                        ),
                ),
                TestCase(
                    "nested annotation - kotlin style - empty attributes",
                    input = "@test.pkg.Anno(other = test.pkg.OtherAnno())",
                    expected =
                        annotationItem(
                            "test.pkg.Anno",
                            "other" to annotationValue("test.pkg.OtherAnno"),
                        ),
                ),
                TestCase(
                    "nested annotation - multiple attributes",
                    input = "@test.pkg.Anno(other = @test.pkg.OtherAnno(value = 'a', attr = 1.0))",
                    expected =
                        annotationItem(
                            "test.pkg.Anno",
                            "other" to
                                annotationValue(
                                    "test.pkg.OtherAnno",
                                    "value" to literalValue('a'),
                                    "attr" to literalValue(1.0),
                                ),
                        ),
                ),
                TestCase(
                    "nested annotation - kotlin style - multiple attributes",
                    // Kotlin style.
                    input = "@test.pkg.Anno(other = test.pkg.OtherAnno(value = 'a', attr = 1.0))",
                    expected =
                        annotationItem(
                            "test.pkg.Anno",
                            "other" to
                                annotationValue(
                                    "test.pkg.OtherAnno",
                                    "value" to literalValue('a'),
                                    "attr" to literalValue(1.0),
                                ),
                        ),
                ),
            )

        /** Supply the list of test cases as the parameters for this test class. */
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = testCases
    }

    @Test
    fun `Test parse`() {
        val annotation = ValueParser.DEFAULT.parseAnnotationItem(testCase.input)!!

        // Wrap in an AnnotationValue before comparing to use its equals(...) method which is
        // defined in terms of the qualified name and the attribute name/Value pairs, ignoring the
        // order.
        val expected = Value.createAnnotationValue(testCase.expected)
        val actual = Value.createAnnotationValue(annotation)

        assertValuesAreStrictlyEqual(expected, actual)
    }
}
