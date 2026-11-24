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

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class BaseCachingValueProviderTest {

    class ValueProviderCounter(valueUseSite: ValueUseSite, private val provider: () -> Value?) :
        BaseCachingValueProvider(valueUseSite) {
        var provideValueCallCount = 0

        override fun provideValue(): Value? {
            provideValueCallCount += 1
            return provider()
        }
    }

    @Test
    fun `Test only calls provideValue once when it returns null`() {
        val counter =
            ValueProviderCounter(
                // Use field as it allows null and this test needs to test that it can cache nulls.
                valueUseSite = ValueUseSite.FIELD,
            ) {
                null
            }
        assertEquals(0, counter.provideValueCallCount, "before")
        assertNull(counter.optionalValue)
        assertEquals(1, counter.provideValueCallCount, "after first call")
        assertNull(counter.optionalValue)
        assertEquals(1, counter.provideValueCallCount, "after second call")
    }

    @Test
    fun `Test only calls provideValue once when it returns non-null`() {
        val stringValue = Value.createLiteralValue(null, "string")
        val counter =
            ValueProviderCounter(
                // Use annotation as it does not allow nulls.
                valueUseSite = ValueUseSite.ANNOTATION,
            ) {
                stringValue
            }
        assertEquals(0, counter.provideValueCallCount, "before")
        assertSame(stringValue, counter.optionalValue)
        assertEquals(1, counter.provideValueCallCount, "after first call")
        assertSame(stringValue, counter.optionalValue)
        assertEquals(1, counter.provideValueCallCount, "after second call")
    }

    @Test
    fun `Test value fails when provideValue returns null`() {
        val counter =
            ValueProviderCounter(
                // Use field as it allows null and this test needs to test that provider can return
                // nulls, but they are inaccessible via `value` property.
                valueUseSite = ValueUseSite.FIELD,
            ) {
                null
            }
        val exception = assertThrows(IllegalStateException::class.java) { counter.value }

        assertEquals("No value provided", exception.message)
    }

    @Test
    fun `Test optionalValue fails when provideValue returns null when it is not allowed`() {
        val counter =
            ValueProviderCounter(
                // Use annotation as it does not allow nulls.
                valueUseSite = ValueUseSite.ANNOTATION,
            ) {
                null
            }
        val exception = assertThrows(IllegalStateException::class.java) { counter.value }

        assertEquals(
            "Provider returned `null` but nulls are not allowed on annotation values",
            exception.message
        )
    }
}
