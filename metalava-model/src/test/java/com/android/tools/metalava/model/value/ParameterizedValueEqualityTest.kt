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

import com.android.tools.metalava.model.testing.value.literalValue
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Tests for [Value.equals] and [Value.hashCode]. */
@RunWith(Parameterized::class)
class ParameterizedValueEqualityTest {
    @Parameterized.Parameter(0) lateinit var equalityTest: EqualityTest

    /**
     * An equality test.
     *
     * @param value1 to compare.
     * @param value2 to compare.
     * @param equal true if [value1] and [value2] should compare equal, false otherwise.
     */
    class EqualityTest(
        private val value1: Value,
        private val value2: Value,
        private val equal: Boolean,
    ) {
        private val operator
            get() = if (equal) "==" else "!="

        override fun toString() =
            "${value1.kind}(${value1.toValueString()}) $operator ${value2.kind}(${value2.toValueString()})"

        fun runTest() {
            if (equal) {
                assertEquals(value1, value2, message = "equality")

                // If the values are equal then they hash codes must also be equal.
                val hash1 = value1.hashCode()
                val hash2 = value2.hashCode()
                assertEquals(hash1, hash2, message = "hash code")
            } else {
                assertNotEquals(value1, value2)
            }
        }
    }

    companion object {
        /** Create a pair of two different [LiteralValue]s. */
        private fun <T : Any> differentLiterals(t1: T, t2: T) =
            if (t1 == t2) error("$t1 and $t2 must be different")
            else {
                val l1 = literalValue(t1)
                val l2 = literalValue(t2)
                if (l1.kind != l2.kind) error("$l1 and $l2 must be of the same kind")
                l1 to l2
            }

        /**
         * Create a list of pairs of unequal [Value]s.
         *
         * This ensures that for every kind there is at least one value of the same kind which is
         * different.
         */
        private val pairsOfUnequalValues =
            listOf(
                differentLiterals(true, false),
                differentLiterals(Byte.MAX_VALUE, Byte.MIN_VALUE),
                differentLiterals('a', 'b'),
                differentLiterals(Double.NaN, 1.0),
                differentLiterals(1001.5E99, -17.5),
                differentLiterals(Float.NaN, 1.0f),
                differentLiterals(745.67f, -93.4f),
                differentLiterals(19, 10),
                differentLiterals(100L, -100L),
                differentLiterals(Short.MAX_VALUE, Short.MIN_VALUE),
                differentLiterals("alpha", "beta")
            )

        /**
         * Flatten the pairs into one list of [Value]s where each [Value] in the list is unequal to
         * all the others.
         */
        private val allValues = pairsOfUnequalValues.flatMap { listOf(it.first, it.second) }

        /**
         * Create a list of [EqualityTest]s to run that test all possible combinations of the
         * values.
         */
        private val equalityTests = buildList {
            // Iterate over all the values twice, with indices.
            for ((index1, value1) in allValues.withIndex()) {
                for ((index2, value2) in allValues.withIndex()) {
                    // Use the indices to tell if the values are the same, not equality of the
                    // values as this is testing equality of values.
                    if (index1 == index2) {
                        // Each value must compare equal to itself.
                        add(EqualityTest(value1, value1, equal = true))
                    } else {
                        // Each value must compare unequal to the others.
                        add(EqualityTest(value1, value2, equal = false))
                    }
                }
            }
        }

        /** Supply the list of equality tests as the parameters for this test class. */
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = equalityTests
    }

    @Test
    fun `equality test`() {
        equalityTest.runTest()
    }
}
