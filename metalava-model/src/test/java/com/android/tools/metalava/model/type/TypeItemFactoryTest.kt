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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.Assertions
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class TypeItemFactoryTest : Assertions {
    @Test
    fun `isClassByConvention - package only`() {
        // A qualified name that looks like a package should not be treated as a class type.
        assertFalse(isClassByConvention("java.lang"))
    }

    @Test
    fun `isClassByConvention - class`() {
        // A qualified name that looks like a class should be treated as a class type.
        assertTrue(isClassByConvention("java.lang.String"))
    }

    @Test
    fun `isClassByConvention - simple nested class`() {
        // A qualified name that looks like a class should be treated as a class type.
        assertTrue(isClassByConvention("java.util.Map.Entry"))
    }

    @Test
    fun `isClassByConvention - nested class that looks like a package`() {
        // A qualified name of a nested class that looks like a package but is qualified by
        // something that looks like a class then it should be treated as a class type.
        assertTrue(isClassByConvention("pkg.Outer.nested"))
    }
}
