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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.text.CustomizableProperty.Companion.KOTLIN_STYLE_NULLS
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.OVERLOADED_METHOD_ORDER
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.SURFACE
import org.junit.Assert.*
import org.junit.Test

class PropertyMapTest {
    @Test
    fun `Test emptyPropertyMap`() {
        val propertyMap = emptyPropertyMap()
        assertEquals("{}", propertyMap.toString())
    }

    @Test
    fun `Test create mutable`() {
        val mutablePropertyMap = mutablePropertyMap()
        mutablePropertyMap[SURFACE] = "public"
        mutablePropertyMap[KOTLIN_STYLE_NULLS] = true
        val mutableMapString = mutablePropertyMap.toString()
        assertEquals("{surface=public, kotlin-style-nulls=yes}", mutableMapString)

        val propertyMap = mutablePropertyMap.toMap()
        assertEquals(mutableMapString, propertyMap.toString())

        val anotherMutableMap = propertyMap.toMutableMap()
        anotherMutableMap[SURFACE] = null
        anotherMutableMap[OVERLOADED_METHOD_ORDER] = FileFormat.OverloadedMethodOrder.SOURCE
        assertEquals(
            "{kotlin-style-nulls=yes, overloaded-method-order=source}",
            anotherMutableMap.toString()
        )

        assertNotEquals(propertyMap, anotherMutableMap.toMap())
    }

    @Test
    fun `Test equals and hashCode`() {
        val propertyMap1 = buildPropertyMap {
            this[SURFACE] = "public"
            this[KOTLIN_STYLE_NULLS] = true
        }

        val propertyMap2 = buildPropertyMap {
            this[SURFACE] = "public"
            this[KOTLIN_STYLE_NULLS] = true
        }

        assertEquals(propertyMap1, propertyMap2)
        assertEquals(propertyMap1.hashCode(), propertyMap2.hashCode())

        val propertyMap3 = buildPropertyMap {
            this[SURFACE] = "surface"
            this[KOTLIN_STYLE_NULLS] = true
        }

        assertNotEquals(propertyMap1, propertyMap3)
    }
}
