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

package com.android.tools.metalava.model.api.surface

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiSurfacesTest {
    /**
     * Check for basic self-consistency.
     *
     * This does not place any requirements on whether [ApiSurfaces.base] is `null` or not just that
     * whatever it is that it is consistent.
     */
    private fun ApiSurfaces.assertSelfConsistent() {
        assertTrue(main.isMain, "main is main")
        if (base == null) {
            assertNull(main.extends, "main does not extend anything")
        } else {
            assertSame(base, main.extends, "main extends base")
            assertFalse(base!!.isMain, "base is not main")
        }
    }

    @Test
    fun `Test without base`() {
        val apiSurfaces = ApiSurfaces.create(needsBase = false)
        apiSurfaces.assertSelfConsistent()
        assertNull(apiSurfaces.base, "base not expected")
    }

    @Test
    fun `Test with base`() {
        val apiSurfaces = ApiSurfaces.create(needsBase = true)
        apiSurfaces.assertSelfConsistent()
        assertNotNull(apiSurfaces.base, "base is expected")
    }

    @Test
    fun `Test with no main`() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                ApiSurfaces.build { createSurface(name = "public") }
            }
        assertEquals("No call to createSurface() set isMain to true", exception.message)
    }

    @Test
    fun `Test with two mains`() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                ApiSurfaces.build {
                    createSurface(name = "public", isMain = true)
                    createSurface(name = "other", isMain = true)
                }
            }
        assertEquals(
            "Main surface already set to `public`, cannot set to `other`",
            exception.message
        )
    }

    @Test
    fun `Test with other names needs base`() {
        val apiSurfaces =
            ApiSurfaces.build {
                createSurface(name = "public")
                createSurface(name = "system", extends = "public", isMain = true)
            }
        apiSurfaces.assertSelfConsistent()
        assertEquals(apiSurfaces.main.name, "system")
        assertNotNull(apiSurfaces.base, "base is expected")
        assertEquals(apiSurfaces.base?.name, "public")
    }

    @Test
    fun `Test with other names does not need base`() {
        val apiSurfaces =
            ApiSurfaces.build {
                createSurface(name = "public", isMain = true)
                createSurface(name = "system", extends = "public")
            }
        apiSurfaces.assertSelfConsistent()
        assertEquals(apiSurfaces.main.name, "public")
        assertNull(apiSurfaces.base, "base is not expected")
    }
}
