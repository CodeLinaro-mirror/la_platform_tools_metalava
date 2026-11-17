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

import com.android.tools.metalava.model.TypeItem
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests for [ValueFactory.createLiteralValue] when no [TypeItem] is provided.
 *
 * Makes sure that the correct [LiteralValue] subclass is created depending on the
 * `underlyingValue`. The type should ensure that but this test just makes sure.
 */
@RunWith(Parameterized::class)
class ParameterizedCreateLiteralNoTypeItemTest {
    @Parameterized.Parameter(0) lateinit var creationTest: CreationTest

    class CreationTest(
        val underlyingValue: Any,
        val expectedLiteralValue: LiteralValue<*>,
    ) {
        override fun toString() = underlyingValue.javaClass.simpleName.lowercase()
    }

    companion object {
        private val creationTests =
            listOf(
                CreationTest(true, DefaultBooleanValue(true)),
                CreationTest(123.toByte(), DefaultByteValue(123.toByte())),
                CreationTest('a', DefaultCharValue('a')),
                CreationTest(1.0, DefaultDoubleValue(1.0)),
                CreationTest(1.0f, DefaultFloatValue(1.0f)),
                CreationTest(12, DefaultIntValue(12)),
                CreationTest(99L, DefaultLongValue(99L)),
                CreationTest(1234.toShort(), DefaultShortValue(1234.toShort())),
                CreationTest("string", DefaultStringValue("string")),
            )

        /** Supply the list of creation tests as the parameters for this test class. */
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = creationTests
    }

    @Test
    fun `creation test`() {
        val literalValue = Value.createLiteralValue(null, creationTest.underlyingValue)
        assertEquals(literalValue, creationTest.expectedLiteralValue, message = "literal value")
        assertEquals(
            creationTest.underlyingValue,
            literalValue.underlyingValue,
            "literal value's underlying value"
        )
    }
}
