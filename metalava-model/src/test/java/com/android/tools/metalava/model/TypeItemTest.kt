/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.testing.primitiveTypeForKind
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import org.junit.Test

class TypeItemTest {
    @Test
    fun `test shortenTypes`() {
        assertThat(TypeItem.shortenTypes("@androidx.annotation.Nullable")).isEqualTo("@Nullable")
        assertThat(
                TypeItem.shortenTypes(
                    "java.util.List<@androidx.annotation.NonNull java.lang.String>"
                )
            )
            .isEqualTo("java.util.List<@NonNull java.lang.String>")
    }

    @Test
    fun testToLambdaFormat() {
        fun check(typeName: String, expected: String = typeName) {
            assertThat(TypeItem.toLambdaFormat(typeName)).isEqualTo(expected)
        }

        // Expected to pass string through unchanged
        check("androidx.pkg.Foo")
        check("kotlin.jvm.functions<<>")

        check("kotlin.jvm.functions.Function0<kotlin.Unit>", "() -> kotlin.Unit")
        check("kotlin.jvm.functions.Function1<pkg.Foo, pkg.Bar>", "(pkg.Foo) -> pkg.Bar")
        check(
            "kotlin.jvm.functions.Function2<Integer, String, Map<Integer, String>>",
            "(Integer, String) -> Map<Integer, String>"
        )
        check("kotlin.jvm.functions<<>")
    }

    @Test
    fun `Test ArrayTypeItem substitute`() {
        val originalModifiers = TypeModifiers.emptyNonNullModifiers
        val originalComponent = primitiveTypeForKind(PrimitiveTypeItem.Primitive.INT)
        val originalVarargs = false
        val original =
            TypeItem.createArrayType(
                originalModifiers,
                originalComponent,
                originalVarargs,
            )

        // Make sure that substituting identical modifiers returns the original.
        assertSame(original, original.substitute(modifiers = originalModifiers))

        // Make sure that substituting different modifiers returns a new copy with the new
        // modifiers.
        original.substitute(modifiers = TypeModifiers.emptyPlatformModifiers).let { substitute ->
            assertNotSame(original, substitute)
            assertEquals(TypeModifiers.emptyPlatformModifiers, substitute.modifiers)
        }

        // Make sure that substituting an identical component returns the original.
        assertSame(original, original.substitute(componentType = originalComponent))

        // Make sure that substituting a different component returns a new copy with the new
        // component.
        val longPrimitive = primitiveTypeForKind(PrimitiveTypeItem.Primitive.LONG)
        original.substitute(componentType = longPrimitive).let { substitute ->
            assertNotSame(original, substitute)
            assertEquals(longPrimitive, substitute.componentType)
        }

        // Make sure that substituting an identical isVarargs returns the original.
        assertSame(original, original.substitute(isVarargs = originalVarargs))

        // Make sure that substituting a different isVarargs returns a new copy with the new
        // isVarargs.
        original.substitute(isVarargs = !originalVarargs).let { substitute ->
            assertNotSame(original, substitute)
            assertEquals(!originalVarargs, substitute.isVarargs)
        }
    }
}
