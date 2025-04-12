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

import com.android.tools.metalava.model.testing.value.arrayValueFromAny
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests [ValueFactory] behavior that is not covered by the other parameterized tests that test
 * different aspects of this.
 */
class ValueFactoryTest {
    @Test
    fun `createLiteralValue - no type item - invalid underlyingValue`() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                Value.createLiteralValue(null, ValueFactoryTest::class.java)
            }

        assertEquals(
            "Underlying value 'class com.android.tools.metalava.model.value.ValueFactoryTest' of class java.lang.Class is not supported",
            exception.message
        )
    }

    @Test
    fun `createArrayValue - empty`() {
        val firstEmpty = Value.createArrayValue(emptyList())
        val secondEmpty = arrayValueFromAny()
        assertSame(firstEmpty, secondEmpty)
    }

    @Test
    fun `createArrayValue - mixture of kinds`() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                arrayValueFromAny(1, 1.0f, 2, 3.0, "text")
            }

        assertEquals(
            """
                Expected array elements to be all of the same kind but found 4 different kinds of value:
                    int -> DefaultIntValue(1), DefaultIntValue(2)
                    float -> DefaultFloatValue(1.0f)
                    double -> DefaultDoubleValue(3.0)
                    string -> DefaultStringValue("text")
            """
                .trimIndent(),
            exception.message?.trimEnd()
        )
    }
}
