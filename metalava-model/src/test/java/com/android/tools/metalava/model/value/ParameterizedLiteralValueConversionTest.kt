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
import com.android.tools.metalava.model.testing.stringType
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A comprehensive set of tests that verify the behavior of converting from one
 * [ValueKind.LITERAL_KINDS] to another, for all possible combinations.
 */
@RunWith(Parameterized::class)
class ParameterizedLiteralValueConversionTest {
    @Parameterized.Parameter(0) lateinit var conversionTest: ConversionTest

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [conversionTest] that is currently being tested was created.
     */
    @get:Rule
    val entryPointCallerRule = EntryPointCallerRule {
        conversionTest.conversionExample.entryPointCallerTracker
    }

    /** An example of a conversion. */
    abstract class ConversionExample {
        /** Record stack trace for this instance. */
        val entryPointCallerTracker = EntryPointCallerTracker()

        /** Check the example within the context of [ConversionTest]. */
        abstract fun ConversionTest.checkExample()
    }

    /** A [ConversionExample] that works ok. */
    class ConversionIsOk(
        private val input: Any,
        private val expectedOutput: Any,
    ) : ConversionExample() {
        override fun ConversionTest.checkExample() {
            val expectedClass = targetKind.wrapperClass
            checkNormalization(input, targetKind, expectedOutput, expectedClass)
        }

        override fun toString() = "ok on $input"
    }

    /** A [ConversionExample] that produces the same output as the input. */
    class ConversionIsSame(
        private val input: Any,
    ) : ConversionExample() {
        override fun ConversionTest.checkExample() {
            val expectedClass = targetKind.wrapperClass
            checkNormalization(input, targetKind, input, expectedClass)
        }

        override fun toString() = "same on $input"
    }

    /** A [ConversionExample] that is lossy. */
    class ConversionIsLossy(
        private val input: Number,
        private val lossyOutput: Any,
        private val roundTripValue: Number,
    ) : ConversionExample() {
        override fun ConversionTest.checkExample() {
            checkNormalizationIsLossy(input, targetKind, lossyOutput, roundTripValue)
        }

        override fun toString() = "lossy when $input"
    }

    /** A [ConversionExample] that is unsupported. */
    class ConversionIsUnsupported(private val input: Any?) : ConversionExample() {
        override fun ConversionTest.checkExample() {
            // If no input value has been provided then just use the default value. For strings use
            // an empty string as the default.
            val actualInput = input ?: fromKind.primitiveKind?.defaultValue ?: ""
            checkNormalizationIsUnsupported(actualInput, targetKind)
        }

        override fun toString() = "unsupported"
    }

    /** A [ConversionExample] that is incompatible. */
    class ConversionIsIncompatible : ConversionExample() {
        override fun ConversionTest.checkExample() {
            // If the conversion is incompatible then it does not matter what the input is so just
            // use the default value.
            val input = fromKind.primitiveKind?.defaultValue ?: ""
            checkNormalizationIsIncompatible(input, targetKind)
        }

        override fun toString() = "incompatible"
    }

    /** A conversion test from [fromKind] to [targetKind] using [conversionExample]. */
    data class ConversionTest(
        val fromKind: ValueKind,
        val targetKind: ValueKind,
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
            targetKind: ValueKind,
            expectedOutput: Any,
            expectedClass: Class<*>
        ) {
            val description = "Converting $input of ${input.javaClass} to $targetKind"
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
            targetKind: ValueKind,
            lossyOutput: Any,
            roundTripValue: Number,
        ) {
            val description = "Converting $input of ${input.javaClass} to $targetKind"
            val exception =
                assertThrows(RuntimeException::class.java) { createLiteralValue(targetKind, input) }
            assertEquals(
                "Conversion of $input to $targetKind is lossy and produces $lossyOutput; round trip value is $roundTripValue",
                exception.message,
                description
            )
        }

        /** Check the normalization of [input] to match the [targetKind] is unsupported. */
        fun checkNormalizationIsUnsupported(
            input: Any,
            targetKind: ValueKind,
        ) {
            val description = "Converting $input of ${input.javaClass} to $targetKind"
            val exception =
                assertThrows(RuntimeException::class.java) { createLiteralValue(targetKind, input) }
            assertEquals(
                "Unsupported primitive type: $targetKind, for underlying value `$input` of ${input.javaClass}",
                exception.message,
                description
            )
        }

        /** Check the normalization of [input] to match the [targetKind] is incompatible. */
        fun checkNormalizationIsIncompatible(
            input: Any,
            targetKind: ValueKind,
        ) {
            val description = "Converting $input of ${input.javaClass} to $targetKind"
            val exception =
                assertThrows(RuntimeException::class.java) { createLiteralValue(targetKind, input) }
            assertEquals(
                "Incompatible type '${targetKind.wrapperClass.name}', for underlying value `$input` of ${input.javaClass}",
                exception.message,
                description
            )
        }

        private fun createLiteralValue(targetKind: ValueKind, input: Any): LiteralValue<*> {
            val typeItem =
                targetKind.primitiveKind?.let { primitiveTypeForKind(it) } ?: stringType()
            return Value.createLiteralValue(typeItem, input)
        }

        override fun toString() = "$fromKind to $targetKind is $conversionExample"
    }

    companion object {
        /**
         * Get the `java.lang` boxing wrapper class suitable for this [ValueKind].
         *
         * Only works for primitive [ValueKind]s and [ValueKind.STRING].
         */
        val ValueKind.wrapperClass
            get() =
                primitiveKind?.wrapperClass
                    ?: if (this == ValueKind.STRING) String::class.java
                    else error("No wrapper class found for $this")

        /** The conversion between two [ValueKind]s is unsupported. */
        @EntryPoint
        internal fun unsupported(input: Any? = null) = listOf(ConversionIsUnsupported(input))

        /** The conversion between two [ValueKind]s is incompatible. */
        private fun incompatible() = listOf(ConversionIsIncompatible())

        /** The conversion between two literals works fine. */
        @EntryPoint
        internal fun ok(input: Any, expectedOutput: Any) =
            listOf(ConversionIsOk(input, expectedOutput))

        /** The conversion between two literals produces the same output as input. */
        @EntryPoint internal fun same(input: Any) = listOf(ConversionIsSame(input))

        /** The conversion between two primitives is lossy. */
        @EntryPoint
        internal fun lossy(
            input: Number,
            lossyOutput: Any,
            roundTripValue: Number,
        ) = listOf(ConversionIsLossy(input, lossyOutput, roundTripValue))

        /**
         * A map from every [Primitive] to every [Primitive] with a list of [ConversionExample]s.
         */
        private val conversionMatrix =
            mapOf(
                ValueKind.BOOLEAN to
                    mapOf(
                        ValueKind.BOOLEAN to same(input = true),
                        ValueKind.BYTE to unsupported(),
                        ValueKind.CHAR to unsupported(),
                        ValueKind.DOUBLE to unsupported(),
                        ValueKind.FLOAT to unsupported(),
                        ValueKind.INT to unsupported(),
                        ValueKind.LONG to unsupported(),
                        ValueKind.SHORT to unsupported(),
                        ValueKind.STRING to incompatible(),
                    ),
                ValueKind.BYTE to
                    mapOf(
                        ValueKind.BOOLEAN to unsupported(),
                        ValueKind.BYTE to same(input = 100.toByte()),
                        ValueKind.CHAR to
                            ok(
                                input = 66.toByte(),
                                expectedOutput = 'B',
                            ) +
                                // A negative byte to a character to an int, to a byte produces the
                                // exact same negative byte as this started with so it is not
                                // considered lossy.
                                ok(
                                    input = (-12).toByte(),
                                    expectedOutput = '\uFFF4',
                                ),
                        ValueKind.DOUBLE to
                            ok(
                                input = (-77).toByte(),
                                expectedOutput = -77.0,
                            ),
                        ValueKind.FLOAT to
                            ok(
                                input = 15.toByte(),
                                expectedOutput = 15.0f,
                            ),
                        ValueKind.INT to
                            ok(
                                input = 127.toByte(),
                                expectedOutput = 127,
                            ),
                        ValueKind.LONG to
                            ok(
                                input = (-125).toByte(),
                                expectedOutput = -125L,
                            ),
                        ValueKind.SHORT to
                            ok(
                                input = 53.toByte(),
                                expectedOutput = 53.toShort(),
                            ),
                        ValueKind.STRING to incompatible(),
                    ),
                ValueKind.CHAR to
                    mapOf(
                        ValueKind.BOOLEAN to unsupported(),
                        ValueKind.BYTE to unsupported(),
                        ValueKind.CHAR to same(input = 'a'),
                        ValueKind.DOUBLE to unsupported(),
                        ValueKind.FLOAT to unsupported(),
                        ValueKind.INT to unsupported(),
                        ValueKind.LONG to unsupported(),
                        ValueKind.SHORT to unsupported(),
                        ValueKind.STRING to incompatible(),
                    ),
                ValueKind.DOUBLE to
                    mapOf(
                        ValueKind.BOOLEAN to unsupported(),
                        ValueKind.BYTE to unsupported(),
                        ValueKind.CHAR to unsupported(),
                        ValueKind.DOUBLE to same(input = 7.89E-200),
                        ValueKind.FLOAT to
                            ok(
                                input = -3.125,
                                expectedOutput = -3.125f,
                            ) +
                                lossy(
                                    input = 2.0e60,
                                    lossyOutput = Float.POSITIVE_INFINITY,
                                    roundTripValue = Double.POSITIVE_INFINITY,
                                ),
                        ValueKind.INT to unsupported(),
                        ValueKind.LONG to unsupported(),
                        ValueKind.SHORT to unsupported(),
                        ValueKind.STRING to incompatible(),
                    ),
                ValueKind.FLOAT to
                    mapOf(
                        ValueKind.BOOLEAN to unsupported(),
                        ValueKind.BYTE to unsupported(),
                        ValueKind.CHAR to unsupported(),
                        ValueKind.DOUBLE to
                            ok(
                                input = Float.MAX_VALUE,
                                expectedOutput = 3.4028234663852886E38,
                            ),
                        ValueKind.FLOAT to same(input = 123456.79f),
                        ValueKind.INT to unsupported(),
                        ValueKind.LONG to unsupported(),
                        ValueKind.SHORT to unsupported(),
                        ValueKind.STRING to incompatible(),
                    ),
                ValueKind.INT to
                    mapOf(
                        ValueKind.BOOLEAN to unsupported(),
                        ValueKind.BYTE to
                            ok(
                                input = 1,
                                expectedOutput = 1.toByte(),
                            ) +
                                lossy(
                                    input = 128,
                                    lossyOutput = -128,
                                    roundTripValue = -128,
                                ),
                        ValueKind.CHAR to
                            ok(
                                input = 169,
                                expectedOutput = '©',
                            ) +
                                lossy(
                                    input = 123456,
                                    lossyOutput = '\uE240',
                                    roundTripValue = 57920,
                                ),
                        ValueKind.DOUBLE to
                            ok(
                                input = Int.MAX_VALUE,
                                expectedOutput = 2.147483647E9,
                            ),
                        ValueKind.FLOAT to
                            ok(
                                input = 123456,
                                expectedOutput = 123456.0f,
                            ) +
                                lossy(
                                    input = 16_777_217,
                                    lossyOutput = 1.6777216E7f,
                                    roundTripValue = 16_777_216,
                                ),
                        ValueKind.INT to same(input = 99),
                        ValueKind.LONG to
                            ok(
                                input = Int.MIN_VALUE,
                                expectedOutput = -2147483648L,
                            ),
                        ValueKind.SHORT to
                            ok(
                                input = -123,
                                expectedOutput = (-123).toShort(),
                            ) +
                                lossy(
                                    input = 1 shl 18,
                                    lossyOutput = 0,
                                    roundTripValue = 0,
                                ),
                        ValueKind.STRING to incompatible(),
                    ),
                ValueKind.LONG to
                    mapOf(
                        ValueKind.BOOLEAN to unsupported(),
                        ValueKind.BYTE to
                            ok(
                                input = 1L,
                                expectedOutput = 1.toByte(),
                            ) +
                                lossy(
                                    input = 130L,
                                    lossyOutput = (-126).toByte(),
                                    roundTripValue = -126L,
                                ),
                        ValueKind.CHAR to
                            ok(
                                input = 0x2345L,
                                expectedOutput = '\u2345',
                            ) +
                                lossy(
                                    input = 12_345_678_900L,
                                    lossyOutput = '\u1C34',
                                    roundTripValue = 7220,
                                ),
                        ValueKind.DOUBLE to
                            ok(
                                input = 20_000_000_000L,
                                expectedOutput = 2.0E10,
                            ) +
                                lossy(
                                    input = (1L shl 53) + 1,
                                    lossyOutput = 9.007199254740992E15,
                                    roundTripValue = 9007199254740992L,
                                ),
                        ValueKind.FLOAT to
                            ok(
                                input = 9_999_999L,
                                expectedOutput = 9999999.0f,
                            ) +
                                lossy(
                                    input = 123_456_789_123L,
                                    lossyOutput = 1.2345679E11,
                                    roundTripValue = 123456790528L,
                                ),
                        ValueKind.INT to
                            ok(
                                input = 1L shl 30,
                                expectedOutput = 1 shl 30,
                            ) +
                                lossy(
                                    input = 1L shl 56,
                                    lossyOutput = 0,
                                    roundTripValue = 0,
                                ),
                        ValueKind.LONG to same(input = Long.MIN_VALUE),
                        ValueKind.SHORT to
                            ok(
                                input = -32768L,
                                expectedOutput = Short.MIN_VALUE,
                            ) +
                                lossy(
                                    input = 44444L,
                                    lossyOutput = (-21092).toShort(),
                                    roundTripValue = -21092,
                                ),
                        ValueKind.STRING to incompatible(),
                    ),
                ValueKind.SHORT to
                    mapOf(
                        ValueKind.BOOLEAN to unsupported(),
                        ValueKind.BYTE to
                            ok(
                                input = 9.toShort(),
                                expectedOutput = 9.toByte(),
                            ) +
                                lossy(
                                    input = 257.toShort(),
                                    lossyOutput = 1,
                                    roundTripValue = 1,
                                ),
                        ValueKind.CHAR to
                            ok(
                                input = Short.MAX_VALUE,
                                expectedOutput = '\u7FFF',
                            ) +
                                // A negative short to a character to an int, to a short produces
                                // the exact same negative short as this started with so it is not
                                // considered lossy.
                                ok(
                                    input = (-56).toShort(),
                                    expectedOutput = '\uFFC8',
                                ),
                        ValueKind.DOUBLE to
                            ok(
                                input = Short.MAX_VALUE,
                                expectedOutput = 32767.0,
                            ),
                        ValueKind.FLOAT to
                            ok(
                                input = Short.MIN_VALUE,
                                expectedOutput = -32768.0f,
                            ),
                        ValueKind.INT to
                            ok(
                                input = 1234,
                                expectedOutput = 1234,
                            ),
                        ValueKind.LONG to
                            ok(
                                input = -12345,
                                expectedOutput = -12345L,
                            ),
                        ValueKind.SHORT to same(input = 9876.toShort()),
                        ValueKind.STRING to incompatible(),
                    ),
                ValueKind.STRING to
                    mapOf(
                        ValueKind.BOOLEAN to unsupported(),
                        ValueKind.BYTE to unsupported(),
                        ValueKind.CHAR to
                            // Single character string can be converted to a char.
                            ok(
                                input = "a",
                                expectedOutput = 'a',
                            ) +
                                // Empty strings cannot be converted to a char.
                                unsupported(
                                    input = "",
                                ) +
                                // Multi-character strings cannot be converted to a char.
                                unsupported(
                                    input = "aa",
                                ),
                        ValueKind.DOUBLE to unsupported(),
                        ValueKind.FLOAT to unsupported(),
                        ValueKind.INT to unsupported(),
                        ValueKind.LONG to unsupported(),
                        ValueKind.SHORT to unsupported(),
                        ValueKind.STRING to same(input = "string"),
                    ),
            )

        /**
         * Flatten the matrix of [Primitive] to [Primitive] to list of [ConversionExample]s into a
         * list of [ConversionTest]s.
         */
        private val conversionTests = let {
            // Make sure that every literal kind is used as the fromKind and targetKind.
            val allKinds = ValueKind.LITERAL_KINDS

            require(conversionMatrix.keys == allKinds) {
                "Expected conversion from every one of $allKinds, but found ${conversionMatrix.keys}"
            }
            conversionMatrix.flatMap { (fromKind, targetMap) ->
                require(targetMap.keys == allKinds) {
                    "Expected conversion from $fromKind to every one of $allKinds, but found ${targetMap.keys}"
                }

                targetMap.flatMap { (targetKind, examples) ->
                    examples.map { example -> ConversionTest(fromKind, targetKind, example) }
                }
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
