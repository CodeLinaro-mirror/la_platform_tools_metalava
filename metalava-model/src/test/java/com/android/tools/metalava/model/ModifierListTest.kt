/*
 * Copyright (C) 2026 The Android Open Source Project
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

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ModifierListTest {
    @Test
    fun `Test non-sealed`() {
        val modifiers = createMutableModifiers(0)
        assertFalse(modifiers.isNonSealed(), message = "before setting")
        modifiers.setNonSealed(true)
        assertTrue(modifiers.isNonSealed(), message = "after setting to true")
        modifiers.setNonSealed(false)
        assertFalse(modifiers.isNonSealed(), message = "after setting to false")
    }

    @Test
    fun `Test mayBeSubtypeOfJavaSealedType - no flags`() {
        val modifiers = createMutableModifiers(0)
        assertFalse(modifiers.mayBeSubtypeOfJavaSealedType())
    }

    @Test
    fun `Test mayBeSubtypeOfJavaSealedType - final`() {
        val modifiers = createMutableModifiers(0).apply { setFinal(true) }
        assertTrue(modifiers.mayBeSubtypeOfJavaSealedType())
    }

    @Test
    fun `Test mayBeSubtypeOfJavaSealedType - sealed`() {
        val modifiers = createMutableModifiers(0).apply { setSealed(true) }
        assertTrue(modifiers.mayBeSubtypeOfJavaSealedType())
    }

    @Test
    fun `Test mayBeSubtypeOfJavaSealedType - non-sealed`() {
        val modifiers = createMutableModifiers(0).apply { setNonSealed(true) }
        assertTrue(modifiers.mayBeSubtypeOfJavaSealedType())
    }
}
