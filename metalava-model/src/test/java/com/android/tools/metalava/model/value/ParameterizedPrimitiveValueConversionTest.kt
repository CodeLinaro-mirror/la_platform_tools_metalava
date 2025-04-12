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

import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.testing.primitiveTypeForKind
import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A comprehensive set of tests that verify the behavior of converting from one [Primitive] type to
 * another, for all possible combinations (excluding [Primitive.VOID].
 */
@RunWith(Parameterized::class)
class ParameterizedPrimitiveValueConversionTest {
    @Parameterized.Parameter(0) lateinit var conversionTest: ConversionTest

    /** An example of a conversion. */
    interface ConversionExample {
        /** Check the example within the context of [ConversionTest]. */
        fun ConversionTest.checkExample()
    }

    /** A [ConversionExample] that works ok. */
    class ConversionIsOk(
        private val input: Any,
        private val expectedOutput: Any,
    ) : ConversionExample {
        override fun ConversionTest.checkExample() {
            val expectedClass = targetKind.wrapperClass
            checkNormalization(input, targetKind, expectedOutput, expectedClass)
        }

        override fun toString() = "ok on $input"
    }

    /** A [ConversionExample] that produces the same output as the input. */
    class ConversionIsSame(
        private val input: Any,
    ) : ConversionExample {
        override fun ConversionTest.checkExample() {
            val expectedClass = targetKind.wrapperClass
            checkNormalization(input, targetKind, input, expectedClass)
        }

        override fun toString() = "same on $input"
    }

    /** A [ConversionExample] that is lossy. */
    class ConversionIsLossy(
        private val input: Number,
        private val lossyOutput: Number,
        private val roundTripValue: Number,
    ) : ConversionExample {
        override fun ConversionTest.checkExample() {
            checkNormalizationIsLossy(input, targetKind, lossyOutput, roundTripValue)
        }

        override fun toString() = "lossy when $input"
    }

    /** A [ConversionExample] that is unsupported. */
    class ConversionIsUnsupported : ConversionExample {
        override fun ConversionTest.checkExample() {
            // If the conversion is unsupported then it does not matter what the input is so just
            // use the default value.
            val input = fromKind.defaultValue!!
            checkNormalizationIsUnsupported(input, targetKind)
        }

        override fun toString() = "unsupported"
    }

    /** A conversion test from [fromKind] to [targetKind] using [conversionExample]. */
    data class ConversionTest(
        val fromKind: Primitive,
        val targetKind: Primitive,
        val conversionExample: ConversionExample,
    ) {
        fun runTest() {
            with(conversionExample) { checkExample() }
        }

        /**
         * Check that the normalization of the [input] to match the [targetKind] results in the
         * [expectedOutput] of the [expectedClass].
         */
        fun checkNormalization(
            input: Any,
            targetKind: Primitive,
            expectedOutput: Any,
            expectedClass: Class<*>
        ) {
            val description =
                "Converting $input of ${input.javaClass} to ${targetKind.primitiveName}"
            val normalized = createLiteralValue(targetKind, input).underlyingValue
            assertEquals<Class<*>>(
                expectedClass,
                normalized.javaClass,
                message = "$description: incorrect class"
            )
            assertEquals(expectedOutput, normalized, message = "$description: incorrect output")
        }

        /**
         * Check the normalization of [input] to match the [targetKind] results in the [lossyOutput]
         * and if it is then round tripped back to the original type it results in [roundTripValue].
         */
        fun checkNormalizationIsLossy(
            input: Number,
            targetKind: Primitive,
            lossyOutput: Number,
            roundTripValue: Number,
        ) {
            val description =
                "Converting $input of ${input.javaClass} to ${targetKind.primitiveName}"
            val exception =
                assertThrows(RuntimeException::class.java) { createLiteralValue(targetKind, input) }
            assertEquals(
                "Conversion of $input to ${targetKind.primitiveName} is lossy and produces $lossyOutput; round trip value is $roundTripValue",
                exception.message,
                description
            )
        }

        /** Check the normalization of [input] to match the [targetKind] is unsupported. */
        fun checkNormalizationIsUnsupported(
            input: Any,
            targetKind: Primitive,
        ) {
            val description =
                "Converting $input of ${input.javaClass} to ${targetKind.primitiveName}"
            val exception =
                assertThrows(RuntimeException::class.java) { createLiteralValue(targetKind, input) }
            assertEquals(
                "Unsupported primitive type: $targetKind, for underlying value `$input` of ${input.javaClass}",
                exception.message,
                description
            )
        }

        private fun createLiteralValue(targetKind: Primitive, input: Any): LiteralValue<*> {
            val typeItem = primitiveTypeForKind(targetKind)
            return Value.createLiteralValue(typeItem, input)
        }

        override fun toString() =
            "${fromKind.primitiveName} to ${targetKind.primitiveName} is $conversionExample"
    }

    companion object {
        /** The conversion between two [Primitive]s is unsupported. */
        private val UNSUPPORTED = listOf(ConversionIsUnsupported())

        /** The conversion between two primitives works fine. */
        private fun ok(input: Any, expectedOutput: Any) =
            listOf(ConversionIsOk(input, expectedOutput))

        /** The conversion between two primitives produces the same output as input. */
        private fun same(input: Any) = listOf(ConversionIsSame(input))

        /** The conversion between two primitives is lossy. */
        private fun lossy(
            input: Number,
            lossyOutput: Number,
            roundTripValue: Number,
        ) = listOf(ConversionIsLossy(input, lossyOutput, roundTripValue))

        /**
         * A map from every [Primitive] to every [Primitive] with a list of [ConversionExample]s.
         */
        private val conversionMatrix =
            mapOf(
                Primitive.BOOLEAN to
                    mapOf(
                        Primitive.BOOLEAN to same(input = true),
                        Primitive.BYTE to UNSUPPORTED,
                        Primitive.CHAR to UNSUPPORTED,
                        Primitive.DOUBLE to UNSUPPORTED,
                        Primitive.FLOAT to UNSUPPORTED,
                        Primitive.INT to UNSUPPORTED,
                        Primitive.LONG to UNSUPPORTED,
                        Primitive.SHORT to UNSUPPORTED,
                    ),
                Primitive.BYTE to
                    mapOf(
                        Primitive.BOOLEAN to UNSUPPORTED,
                        Primitive.BYTE to same(input = 100.toByte()),
                        Primitive.CHAR to UNSUPPORTED,
                        Primitive.DOUBLE to
                            ok(
                                input = (-77).toByte(),
                                expectedOutput = -77.0,
                            ),
                        Primitive.FLOAT to
                            ok(
                                input = 15.toByte(),
                                expectedOutput = 15.0f,
                            ),
                        Primitive.INT to
                            ok(
                                input = 127.toByte(),
                                expectedOutput = 127,
                            ),
                        Primitive.LONG to
                            ok(
                                input = (-125).toByte(),
                                expectedOutput = -125L,
                            ),
                        Primitive.SHORT to
                            ok(
                                input = 53.toByte(),
                                expectedOutput = 53.toShort(),
                            ),
                    ),
                Primitive.CHAR to
                    mapOf(
                        Primitive.BOOLEAN to UNSUPPORTED,
                        Primitive.BYTE to UNSUPPORTED,
                        Primitive.CHAR to same(input = 'a'),
                        Primitive.DOUBLE to UNSUPPORTED,
                        Primitive.FLOAT to UNSUPPORTED,
                        Primitive.INT to UNSUPPORTED,
                        Primitive.LONG to UNSUPPORTED,
                        Primitive.SHORT to UNSUPPORTED,
                    ),
                Primitive.DOUBLE to
                    mapOf(
                        Primitive.BOOLEAN to UNSUPPORTED,
                        Primitive.BYTE to UNSUPPORTED,
                        Primitive.CHAR to UNSUPPORTED,
                        Primitive.DOUBLE to same(input = 7.89E-200),
                        Primitive.FLOAT to
                            ok(
                                input = -3.125,
                                expectedOutput = -3.125f,
                            ) +
                                lossy(
                                    input = 2.0e60,
                                    lossyOutput = Float.POSITIVE_INFINITY,
                                    roundTripValue = Double.POSITIVE_INFINITY,
                                ),
                        Primitive.INT to UNSUPPORTED,
                        Primitive.LONG to UNSUPPORTED,
                        Primitive.SHORT to UNSUPPORTED,
                    ),
                Primitive.FLOAT to
                    mapOf(
                        Primitive.BOOLEAN to UNSUPPORTED,
                        Primitive.BYTE to UNSUPPORTED,
                        Primitive.CHAR to UNSUPPORTED,
                        Primitive.DOUBLE to
                            ok(
                                input = Float.MAX_VALUE,
                                expectedOutput = 3.4028234663852886E38,
                            ),
                        Primitive.FLOAT to same(input = 123456.79f),
                        Primitive.INT to UNSUPPORTED,
                        Primitive.LONG to UNSUPPORTED,
                        Primitive.SHORT to UNSUPPORTED,
                    ),
                Primitive.INT to
                    mapOf(
                        Primitive.BOOLEAN to UNSUPPORTED,
                        Primitive.BYTE to
                            ok(
                                input = 1,
                                expectedOutput = 1.toByte(),
                            ) +
                                lossy(
                                    input = 128,
                                    lossyOutput = -128,
                                    roundTripValue = -128,
                                ),
                        Primitive.CHAR to UNSUPPORTED,
                        Primitive.DOUBLE to
                            ok(
                                input = Int.MAX_VALUE,
                                expectedOutput = 2.147483647E9,
                            ),
                        Primitive.FLOAT to
                            ok(
                                input = 123456,
                                expectedOutput = 123456.0f,
                            ) +
                                lossy(
                                    input = 16_777_217,
                                    lossyOutput = 1.6777216E7f,
                                    roundTripValue = 16_777_216,
                                ),
                        Primitive.INT to same(input = 99),
                        Primitive.LONG to
                            ok(
                                input = Int.MIN_VALUE,
                                expectedOutput = -2147483648L,
                            ),
                        Primitive.SHORT to
                            ok(
                                input = -123,
                                expectedOutput = (-123).toShort(),
                            ) +
                                lossy(
                                    input = 1 shl 18,
                                    lossyOutput = 0,
                                    roundTripValue = 0,
                                ),
                    ),
                Primitive.LONG to
                    mapOf(
                        Primitive.BOOLEAN to UNSUPPORTED,
                        Primitive.BYTE to
                            ok(
                                input = 1L,
                                expectedOutput = 1.toByte(),
                            ) +
                                lossy(
                                    input = 130L,
                                    lossyOutput = (-126).toByte(),
                                    roundTripValue = -126L,
                                ),
                        Primitive.CHAR to UNSUPPORTED,
                        Primitive.DOUBLE to
                            ok(
                                input = 20_000_000_000L,
                                expectedOutput = 2.0E10,
                            ) +
                                lossy(
                                    input = (1L shl 53) + 1,
                                    lossyOutput = 9.007199254740992E15,
                                    roundTripValue = 9007199254740992L,
                                ),
                        Primitive.FLOAT to
                            ok(
                                input = 9_999_999L,
                                expectedOutput = 9999999.0f,
                            ) +
                                lossy(
                                    input = 123_456_789_123L,
                                    lossyOutput = 1.2345679E11,
                                    roundTripValue = 123456790528L,
                                ),
                        Primitive.INT to
                            ok(
                                input = 1L shl 30,
                                expectedOutput = 1 shl 30,
                            ) +
                                lossy(
                                    input = 1L shl 56,
                                    lossyOutput = 0,
                                    roundTripValue = 0,
                                ),
                        Primitive.LONG to same(input = Long.MIN_VALUE),
                        Primitive.SHORT to
                            ok(
                                input = -32768L,
                                expectedOutput = Short.MIN_VALUE,
                            ) +
                                lossy(
                                    input = 44444L,
                                    lossyOutput = (-21092).toShort(),
                                    roundTripValue = -21092,
                                ),
                    ),
                Primitive.SHORT to
                    mapOf(
                        Primitive.BOOLEAN to UNSUPPORTED,
                        Primitive.BYTE to
                            ok(
                                input = 9.toShort(),
                                expectedOutput = 9.toByte(),
                            ) +
                                lossy(
                                    input = 257.toShort(),
                                    lossyOutput = 1,
                                    roundTripValue = 1,
                                ),
                        Primitive.CHAR to UNSUPPORTED,
                        Primitive.DOUBLE to
                            ok(
                                input = Short.MAX_VALUE,
                                expectedOutput = 32767.0,
                            ),
                        Primitive.FLOAT to
                            ok(
                                input = Short.MIN_VALUE,
                                expectedOutput = -32768.0f,
                            ),
                        Primitive.INT to
                            ok(
                                input = 1234,
                                expectedOutput = 1234,
                            ),
                        Primitive.LONG to
                            ok(
                                input = -12345,
                                expectedOutput = -12345L,
                            ),
                        Primitive.SHORT to same(input = 9876.toShort()),
                    ),
            )

        /**
         * Flatten the matrix of [Primitive] to [Primitive] to list of [ConversionExample]s into a
         * list of [ConversionTest]s.
         */
        private val conversionTests =
            conversionMatrix.flatMap { (fromKind, targetMap) ->
                targetMap.flatMap { (targetKind, examples) ->
                    examples.map { example -> ConversionTest(fromKind, targetKind, example) }
                }
            }

        /** Supply the list of test cases as the parameters for this test class. */
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = conversionTests
    }

    @Test
    fun `conversion test`() {
        conversionTest.runTest()
    }
}
