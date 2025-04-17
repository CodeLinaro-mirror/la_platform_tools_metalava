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
import com.android.tools.metalava.model.testsuite.value.ValueExample.Companion.NO_INITIAL_FIELD_VALUE
import com.android.tools.metalava.model.testsuite.value.ValueExample.Companion.valueExamples
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

    class TestCase(
        val label: String,
        /** The [ValueExample] on which this test case is based. */
        val valueExample: ValueExample,
        val javaType: String,
        val input: String,
        val expectedValue: Value,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TestCase

            if (javaType != other.javaType) return false
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

        /** The set of [ValueUseSite]s which end up being written to a signature file. */
        private val valueUseSitesWrittenToSignatureFiles =
            EnumSet.of(
                ValueUseSite.ANNOTATION_TO_SOURCE,
                ValueUseSite.ATTRIBUTE_DEFAULT_VALUE,
                ValueUseSite.FIELD_WRITE_WITH_SEMICOLON,
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
                    val javaType = valueExample.javaType
                    buildList {
                        // Iterate over jar and source representations.
                        for (producerKind in ProducerKind.entries) {
                            // Get the expected value. Uses ValueUseSite.ATTRIBUTE_VALUE but any
                            // would be ok as all use sites have the same expected value.
                            val expectedValue =
                                valueExample.expectedValue?.expectationFor(
                                    producerKind,
                                    ValueUseSite.ATTRIBUTE_VALUE
                                ) ?: continue

                            // Add a test case for the input java expression.
                            add(
                                TestCase(
                                    "${valueExample.name},javaExpression,${producerKind.name.lowercase()}",
                                    valueExample,
                                    javaType,
                                    valueExample.signatureExpression,
                                    expectedValue
                                )
                            )

                            // Iterate over all value use sites that are written to signature files.
                            for (valueUseSite in valueUseSitesWrittenToSignatureFiles) {
                                // Cover Java dnd Kotlin representations.
                                for (sourceInputFormat in sourceInputFormats) {
                                    // Get the expected representation for this combination of
                                    // options as this will be an input to the parser.
                                    val input =
                                        valueExample
                                            .expectedLegacySourceFor(sourceInputFormat)
                                            .expectationFor(producerKind, valueUseSite)

                                    // Ignore no values.
                                    if (input == NO_INITIAL_FIELD_VALUE) continue

                                    val label =
                                        "${valueExample.name},${valueUseSite.name.lowercase()},${sourceInputFormat.name.lowercase()},${producerKind.name.lowercase()}"

                                    // Add a test case for the input.
                                    add(
                                        TestCase(
                                            label,
                                            valueExample,
                                            javaType,
                                            input,
                                            expectedValue
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
                .sortedWith(compareBy({ it.label }))
                // Apply some filtering to remove known problematic cases.
                .filter {
                    // TODO(b/354633349): Support Kotlin syntax for class literals in signature
                    //  files.
                    !it.input.contains("::class")
                    // Ignore test cases that require imports as they are not fully qualified
                    // and signature files require class types to be fully qualified.
                    && it.valueExample.javaImports.isEmpty()
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
                        field public static final ${testCase.javaType} FIELD;
                      }
                    }
                """
            )
        ) {
            // Run the test on the expected value ignoring any [ValueProviderException]s if its
            // kind is not fully supported across implementation models.
            testCase.expectedValue.runValueTest { expected ->
                val typeItem = codebase.assertClass("test.pkg.Foo").assertField("FIELD").type()
                val valueParser = ValueParser.DEFAULT
                val actualValue = valueParser.parse(typeItem, testCase.input)
                assertEquals(expected, actualValue)
            }
        }
    }
}
