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
import com.android.tools.metalava.model.testing.value.assertValuesAreStrictlyEqual
import com.android.tools.metalava.model.testing.value.literalValue
import com.android.tools.metalava.model.testing.value.primitiveValueForKind
import kotlin.test.assertSame
import org.junit.Test

/** General tests for [LiteralValue] classes. */
class LiteralValueTest {
    @Test
    fun `Test convertToType - literal to literal of same type`() {
        val input = literalValue(1)
        val intType = primitiveTypeForKind(Primitive.INT)
        val converted = input.convertToType(intType)
        // No conversion was necessary.
        assertSame(input, converted)
    }

    @Test
    fun `Test convertToType - literal to literal of different type`() {
        val input = literalValue(1)
        val doubleType = primitiveTypeForKind(Primitive.DOUBLE)
        val converted = input.convertToType(doubleType)
        assertValuesAreStrictlyEqual(primitiveValueForKind(Primitive.DOUBLE, 1), converted)
    }

    @Test
    fun `Test convertToType - non-literal to literal of same type`() {
        val input = literalValue(1, nonLiteralInSource = true)
        val intType = primitiveTypeForKind(Primitive.INT)
        val converted = input.convertToType(intType)
        // No conversion was necessary.
        assertSame(input, converted)
    }

    @Test
    fun `Test convertToType - non-literal to literal of different type`() {
        val input = literalValue(1, nonLiteralInSource = true)
        val doubleType = primitiveTypeForKind(Primitive.DOUBLE)
        val converted = input.convertToType(doubleType)
        val expected =
            primitiveValueForKind(
                Primitive.DOUBLE,
                1,
                nonLiteralInSource = true,
            )
        assertValuesAreStrictlyEqual(expected, converted)
    }

    @Test
    fun `Test convertToType - float from int literal to double`() {
        val input = primitiveValueForKind(Primitive.FLOAT, 1)
        val doubleType = primitiveTypeForKind(Primitive.DOUBLE)
        val converted = input.convertToType(doubleType)
        assertValuesAreStrictlyEqual(primitiveValueForKind(Primitive.DOUBLE, 1), converted)
    }

    @Test
    fun `Test convertToType - literal to forced non-literal of same type`() {
        val input = literalValue(1)
        val intType = primitiveTypeForKind(Primitive.INT)
        val converted = input.convertToType(intType, forceNonLiteralInSource = true)
        assertValuesAreStrictlyEqual(literalValue(1, nonLiteralInSource = true), converted)
    }

    @Test
    fun `Test convertToType - literal to forced non-literal of different type`() {
        val input = literalValue(1)
        val doubleType = primitiveTypeForKind(Primitive.DOUBLE)
        val converted = input.convertToType(doubleType, forceNonLiteralInSource = true)
        assertValuesAreStrictlyEqual(
            primitiveValueForKind(Primitive.DOUBLE, 1, nonLiteralInSource = true),
            converted
        )
    }
}
