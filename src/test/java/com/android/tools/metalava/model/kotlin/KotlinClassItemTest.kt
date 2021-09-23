/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.metalava.model.kotlin

import com.android.tools.metalava.kotlin
import com.android.tools.metalava.withClass
import com.android.tools.metalava.withCodebase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinClassItemTest {
    @Test
    fun simpleName() {
        withClass("class Foo") { assertEquals("Foo", it.simpleName()) }
    }

    @Test
    fun qualifiedName() {
        withClass(
            """
                package androidx.pkg

                class Foo
            """
        ) { assertEquals("androidx.pkg.Foo", it.qualifiedName()) }
    }

    @Test
    fun isEnum() {
        withCodebase(
            kotlin("enum class MyEnum { VALUE }"),
            kotlin("class NonEnum")
        ) { codebase ->
            assertTrue(codebase.findClass("MyEnum")!!.isEnum())
            assertFalse(codebase.findClass("NonEnum")!!.isEnum())
        }
    }

    @Test
    fun containingPackage() {
        withClass(
            """
                package androidx.pkg

                class Foo
            """
        ) { assertEquals("androidx.pkg", it.containingPackage().qualifiedName()) }
    }
}
