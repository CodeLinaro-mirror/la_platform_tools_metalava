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

package com.android.tools.metalava.model.api

import com.android.tools.metalava.model.testing.value.annotationItem
import com.android.tools.metalava.model.testing.value.literalValue
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests for [AnnotationMatcher].
 *
 * There are two sets of inputs to testing [AnnotationMatcher.matchResult], the sets of patterns
 * used to build the matcher and the sets of annotations used to test the matcher. There are also
 * some additional methods that need testing which have at least one of those two sets.
 *
 * This test is organized by having one test method for each method or annotations against which the
 * matcher is created and parameterized by the set of patterns and the expected results of those
 * patterns in each test method. That ensures comprehensive test coverage at the expense of some
 * duplication of tests.
 */
@RunWith(Parameterized::class)
class ParameterizedAnnotationMatcherTest(private val params: Params) {

    enum class TestResult {
        MATCHED,
    }

    data class Params(
        val name: String,
        val patterns: List<String>,
        val expectedIncludedAnnotationNames: Set<String> = emptySet(),
        val expectedEmpty: TestResult? = null,
        val expectedMatchesSimple: TestResult? = null,
        val expectedMatchesNamedValue: TestResult? = null,
        val expectedMatchesNamedOther: TestResult? = null,
        val expectedMatchesAnnotationName: TestResult? = expectedMatchesSimple,
        val expectedMatchesOtherAnnotationName: TestResult? = null,
        val expectedMatchesSuffix: TestResult? = null,
    ) {
        override fun toString(): String {
            return name
        }
    }

    companion object {
        private val params =
            listOf(
                Params(
                    name = "empty",
                    patterns = emptyList(),
                    expectedEmpty = TestResult.MATCHED,
                ),
                Params(
                    name = "simple",
                    patterns = listOf("test.pkg.Annotation"),
                    expectedIncludedAnnotationNames = setOf("test.pkg.Annotation"),
                    expectedMatchesSimple = TestResult.MATCHED,
                    expectedMatchesNamedValue = TestResult.MATCHED,
                    expectedMatchesNamedOther = TestResult.MATCHED,
                ),
                Params(
                    name = "simple-plus-parentheses",
                    patterns = listOf("test.pkg.Annotation()"),
                    expectedIncludedAnnotationNames = setOf("test.pkg.Annotation"),
                    expectedMatchesSimple = TestResult.MATCHED,
                    expectedMatchesNamedValue = TestResult.MATCHED,
                    expectedMatchesNamedOther = TestResult.MATCHED,
                ),
                Params(
                    name = "simple-suffix-plus-value",
                    patterns = listOf("test.pkg.AnnotationSuffix(2)"),
                    expectedIncludedAnnotationNames = setOf("test.pkg.AnnotationSuffix"),
                    expectedMatchesSuffix = TestResult.MATCHED,
                ),
                Params(
                    name = "other-suffix-plus-value",
                    patterns = listOf("other.OtherAnnotationSuffix(2)"),
                    expectedIncludedAnnotationNames = setOf("other.OtherAnnotationSuffix"),
                    expectedMatchesSuffix = TestResult.MATCHED,
                ),
                Params(
                    name = "implicit-value",
                    patterns = listOf("""test.pkg.Annotation("value")"""),
                    expectedIncludedAnnotationNames = setOf("test.pkg.Annotation"),
                    expectedMatchesNamedValue = TestResult.MATCHED,
                    expectedMatchesAnnotationName = TestResult.MATCHED,
                ),
                Params(
                    name = "named-value",
                    patterns = listOf("""test.pkg.Annotation(value = "value")"""),
                    expectedIncludedAnnotationNames = setOf("test.pkg.Annotation"),
                    expectedMatchesNamedValue = TestResult.MATCHED,
                    expectedMatchesAnnotationName = TestResult.MATCHED,
                ),
                Params(
                    name = "other-value",
                    patterns = listOf("""test.pkg.Annotation(other = "value")"""),
                    expectedIncludedAnnotationNames = setOf("test.pkg.Annotation"),
                    expectedMatchesAnnotationName = TestResult.MATCHED,
                ),
                Params(
                    name = "everything-but-simple",
                    patterns =
                        listOf(
                            """test.pkg.Annotation(value = "value")""",
                            """test.pkg.Annotation(other = "other")""",
                            "other.OtherAnnotation",
                        ),
                    // This should probably be a sorted set not a list.
                    expectedIncludedAnnotationNames =
                        setOf(
                            "other.OtherAnnotation",
                            "test.pkg.Annotation",
                        ),
                    expectedMatchesNamedValue = TestResult.MATCHED,
                    expectedMatchesNamedOther = TestResult.MATCHED,
                    expectedMatchesAnnotationName = TestResult.MATCHED,
                    expectedMatchesOtherAnnotationName = TestResult.MATCHED,
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "{0}") fun testParameters() = params
    }

    private fun buildMatcher() =
        AnnotationMatcher.createFromRules(
            params.patterns.map { AnnotationMatcher.Rule(it, TestResult.MATCHED) }
        )

    @Test
    fun `Test match simple annotation no attributes`() {
        val matcher = buildMatcher()

        val annotationItem = annotationItem("test.pkg.Annotation")

        assertEquals(params.expectedMatchesSimple, matcher.matchResult(annotationItem))
    }

    @Test
    fun `Test match annotation, value property`() {
        val matcher = buildMatcher()

        val annotationItem =
            annotationItem(
                "test.pkg.Annotation",
                "value" to literalValue("value"),
            )

        assertEquals(params.expectedMatchesNamedValue, matcher.matchResult(annotationItem))
    }

    @Test
    fun `Test match annotation, other property`() {
        val matcher = buildMatcher()

        val annotationItem =
            annotationItem(
                "test.pkg.Annotation",
                "other" to literalValue("other"),
            )

        assertEquals(params.expectedMatchesNamedOther, matcher.matchResult(annotationItem))
    }

    @Test
    fun `Test included names`() {
        val matcher = buildMatcher()

        // Although the names are a set the order matters, however equality of sets ignores order so
        // convert each set to a list and then compare.
        assertEquals(
            params.expectedIncludedAnnotationNames.toList(),
            matcher.annotationNames.toList()
        )
    }
}
