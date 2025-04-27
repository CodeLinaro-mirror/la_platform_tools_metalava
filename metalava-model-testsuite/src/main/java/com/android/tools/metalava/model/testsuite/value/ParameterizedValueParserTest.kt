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

package com.android.tools.metalava.model.testsuite.value

import com.android.tools.metalava.model.junit4.ParameterFilter
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig
import com.android.tools.metalava.model.testing.value.runValueTest
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner
import com.android.tools.metalava.model.testsuite.value.ValueExample.Companion.valueExamples
import com.android.tools.metalava.model.type.TypeItemParser
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueParser
import com.android.tools.metalava.testing.EntryPointCallerRule
import java.util.EnumSet
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

/**
 * A parameterized test for [ValueParser] which will use all the possible values that could be
 * written out to a signature file for the [ValueExample.valueExamples].
 *
 * That ensures that any time new values are added to the [ValueExample.valueExamples] because of
 * output changes or just improving the test coverage it will also make sure that they can be read
 * back in from the signature files.
 */
class ParameterizedValueParserTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var testCase: TestCase

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [ValueExample] that is currently being tested was created.
     */
    @get:Rule
    val entryPointCallerRule = EntryPointCallerRule {
        testCase.valueExample.entryPointCallerTracker
    }

    /** Specifies how a [TestCase] will determine whether it passed or not. */
    enum class Comparison {
        /** The [Value] parsed from [TestCase.input] must match the [TestCase.expectedValue]. */
        STRICT,

        /**
         * A [Value] must have been parsed from [TestCase.input].
         *
         * The writing of some [Value]s to signature files loses information that is necessary to
         * recreate the original, e.g. the field references may not be fully qualified. In that case
         * the current goal is simply to ensure that they can be parsed back in.
         */
        PARSE,
    }

    class TestCase(
        val label: String,
        /** The [ValueExample] on which this test case is based. */
        val valueExample: ValueExample,
        val signatureType: String,
        val input: String,
        val expectedValue: Value,
        val comparison: Comparison,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TestCase

            if (signatureType != other.signatureType) return false
            if (input != other.input) return false
            if (expectedValue != other.expectedValue) return false

            return true
        }

        override fun hashCode(): Int {
            var result = input.hashCode()
            result = 31 * result + expectedValue.hashCode()
            return result
        }

        override fun toString() = label
    }

    companion object {
        /** Filter the parameters. */
        @JvmStatic
        @ParameterFilter
        fun parameterFilter(
            config: CodebaseCreatorConfig<ModelSuiteRunner>,
            testCase: TestCase,
        ): Boolean {
            val inputFormat = config.inputFormat

            // Ignore any tests that are not for the signature file.
            return inputFormat == InputFormat.SIGNATURE
        }

        /** The set of [LegacyValueUseSite]s which end up being written to a signature file. */
        private val legacyValueUseSitesWrittenToSignatureFiles =
            EnumSet.of(
                LegacyValueUseSite.ANNOTATION_TO_SOURCE,
                LegacyValueUseSite.ATTRIBUTE_DEFAULT_VALUE,
                LegacyValueUseSite.FIELD_WRITE_WITH_SEMICOLON,
            )

        /**
         * The set of [InputFormat]s that generate the content that gets written to the signature
         * files.
         */
        private val sourceInputFormats = EnumSet.of(InputFormat.JAVA, InputFormat.KOTLIN)

        /**
         * A list of [TestCase]s constructed from [ValueExample]s.
         *
         * The intent is that this will include every possible representation of values that are
         * written to signature files as that is the set of possible inputs to the [ValueParser].
         */
        private val testCases =
            valueExamples
                .flatMap { valueExample ->
                    // If the example is not suitable for signature files then ignore it.
                    if (InputFormat.SIGNATURE !in valueExample.validForInputFormats)
                        return@flatMap emptyList()

                    val signatureType = valueExample.signatureType
                    buildList {
                        // Iterate over jar and source representations.
                        for (producerKind in ProducerKind.entries) {
                            // Get the expected value. Uses LegacyValueUseSite.ATTRIBUTE_VALUE but
                            // any would be ok as all use sites have the same expected value.
                            val expectedValue =
                                valueExample.expectedValue?.expectationFor(
                                    producerKind,
                                    LegacyValueUseSite.ATTRIBUTE_VALUE
                                ) ?: continue

                            // The expression is only guaranteed to produce the expected source
                            // value. The jar value could have lost some information, e.g. a
                            // constant field reference will have been replaced with its constant
                            // value. Select how the result should be compared appropriately.
                            val comparison =
                                if (producerKind == ProducerKind.SOURCE) Comparison.STRICT
                                else Comparison.PARSE

                            // Add a test case for the input signature expression.
                            add(
                                TestCase(
                                    "${valueExample.name},signatureExpression,${producerKind.name.lowercase()}",
                                    valueExample,
                                    signatureType,
                                    valueExample.signatureExpression,
                                    expectedValue,
                                    comparison,
                                )
                            )

                            // Iterate over all value use sites that are written to signature files.
                            for (legacyValueUseSite in legacyValueUseSitesWrittenToSignatureFiles) {
                                // Cover Java dnd Kotlin representations.
                                for (sourceInputFormat in sourceInputFormats) {
                                    // Get the expected representation for this combination of
                                    // options as this will be an input to the parser.
                                    val input =
                                        valueExample
                                            .expectedLegacySourceFor(sourceInputFormat)
                                            .expectationFor(producerKind, legacyValueUseSite)

                                    // Ignore no values.
                                    if (input == null) continue

                                    val label =
                                        "${valueExample.name},${legacyValueUseSite.name.lowercase()},${sourceInputFormat.name.lowercase()},${producerKind.name.lowercase()}"

                                    // Add a test case for the input.
                                    add(
                                        TestCase(
                                            label,
                                            valueExample,
                                            signatureType,
                                            input,
                                            expectedValue,
                                            // Just make sure that the value can be parsed.
                                            Comparison.PARSE,
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                // The above produces a lot of duplicates so remove them.
                .distinct()
                // Put them in order.
                .sortedWith(compareBy { it.label })
                // Apply some filtering to remove known problematic cases.
                .filter {
                    // Ignore test cases that require imports as they are not fully qualified and
                    // signature files require class types to be fully qualified.
                    it.valueExample.javaImports.isEmpty() &&
                        // Ignore test cases that have an empty string as an input as they are in
                        // error.
                        it.input.isNotEmpty()
                }

        /** Supply the list of test cases as the parameters for this test class. */
        @JvmStatic @Parameterized.Parameters fun params() = testCases
    }

    @Test
    fun `Test parse`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        field public static final ${testCase.signatureType} FIELD;
                      }
                      public interface Constants {
                        field public static final String STRING_CONSTANT = "constant";
                        field public static final int INT_CONSTANT = 37;
                      }
                      public enum TestEnum {
                        enum_constant public static final test.pkg.TestEnum DEFAULT;
                        enum_constant public static final test.pkg.TestEnum VALUE1;
                      }
                    }
                """
            )
        ) {
            // Run the test on the expected value ignoring any [ValueProviderException]s if its
            // kind is not fully supported across implementation models.
            testCase.expectedValue.runValueTest { expected ->
                val typeItem = codebase.assertClass("test.pkg.Foo").assertField("FIELD").type()
                val valueParser = ValueParser(TypeItemParser.forValueParser(codebase))
                val actualValue = valueParser.parse(typeItem, testCase.input)
                when (testCase.comparison) {
                    Comparison.STRICT -> {
                        assertEquals(expected, actualValue)
                    }
                    Comparison.PARSE -> {
                        // If it got here then it did not fail above so there is nothing else to do.
                    }
                }
            }
        }
    }
}
