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

import com.android.tools.metalava.model.testing.arrayTypeItem
import com.android.tools.metalava.model.testing.primitiveTypeForKind
import com.android.tools.metalava.model.testing.stringType
import com.android.tools.metalava.model.testing.variableTypeItem
import com.android.tools.metalava.model.testing.wildcardTypeItem
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun `Test substitute preserves isValueClassType`() {
        val newModifiers = TypeModifiers.emptyPlatformModifiers

        val primitiveType =
            primitiveTypeForKind(PrimitiveTypeItem.Primitive.INT, isValueClassType = true)
        assertEquals(true, primitiveType.substitute(modifiers = newModifiers).isValueClassType)

        val arrayType = arrayTypeItem(primitiveType, isValueClassType = true)
        assertEquals(true, arrayType.substitute(modifiers = newModifiers).isValueClassType)

        val classType = stringType(isValueClassType = true)
        assertEquals(true, classType.substitute(modifiers = newModifiers).isValueClassType)

        val lambdaType =
            TypeItem.createLambdaType(
                modifiers = TypeModifiers.emptyNonNullModifiers,
                qualifiedName = "kotlin.jvm.functions.Function0",
                arguments = emptyList(),
                outerClassType = null,
                isSuspend = false,
                receiverType = null,
                parameterTypes = emptyList(),
                returnType = primitiveTypeForKind(PrimitiveTypeItem.Primitive.VOID),
                isValueClassType = true,
            )
        assertEquals(true, lambdaType.substitute(modifiers = newModifiers).isValueClassType)

        val variableType = variableTypeItem("T", isValueClassType = true)
        assertEquals(true, variableType.substitute(modifiers = newModifiers).isValueClassType)
    }

    @Test
    fun `Test WildcardTypeItem isValueClassType throws`() {
        val wildcard = wildcardTypeItem()
        val exception = assertFailsWith<IllegalStateException> { wildcard.isValueClassType }
        assertEquals("? cannot be a value class type", exception.message)
    }
}
