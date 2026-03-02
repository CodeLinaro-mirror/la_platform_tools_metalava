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

import com.android.tools.metalava.model.filterIfNotSame
import com.android.tools.metalava.model.mapIfNotSameNotNull
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Test

class ListUtilTest {
    @Test
    fun `Test filter with reuse - no change`() {
        val list = listOf(1, 2, 3)
        val newList = list.filterIfNotSame { it < 4 }
        assertSame(list, newList)
    }

    @Test
    fun `Test filter with reuse - remove middle`() {
        val list = listOf(1, 2, 3)
        val newList = list.filterIfNotSame { it != 2 }
        assertEquals(listOf(1, 3), newList)
    }

    @Test
    fun `Test map with reuse - no change`() {
        val list = listOf(1, 2, 3)
        val newList = list.mapIfNotSameNotNull { it }
        assertSame(list, newList)
    }

    @Test
    fun `Test map with reuse - change middle`() {
        val list = listOf(1, 2, 3)
        val newList = list.mapIfNotSameNotNull { if (it == 2) it * 2 else it }
        assertEquals(listOf(1, 4, 3), newList)
    }

    @Test
    fun `Test map with reuse - remove middle`() {
        val list = listOf(1, 2, 3)
        val newList = list.mapIfNotSameNotNull { if (it == 2) null else it }
        assertEquals(listOf(1, 3), newList)
    }
}
