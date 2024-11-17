/*
 * Copyright (C) 2024 The Android Open Source Project
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
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ApiVariantSetTest {
    private val apiSurfaces = ApiSurfaces.create(needsBase = true)

    private val main = apiSurfaces.main
    private val base = apiSurfaces.base!!

    @Test
    fun `Test creation`() {
        val mutable = MutableApiVariantSet.setOf(apiSurfaces)

        assertTrue(mutable.isEmpty(), "isEmpty")
        assertFalse(mutable.isNotEmpty(), "isNotEmpty")

        for (variant in apiSurfaces.variants) {
            assertFalse(mutable.contains(variant), "contains($variant)")
        }

        assertEquals("ApiVariantSet[]", mutable.toString(), "empty set")
    }

    @Test
    fun `Test mutations`() {
        val mutable = MutableApiVariantSet.setOf(apiSurfaces)

        val mainCore = main.variantFor(ApiVariantType.CORE)
        val baseRemoved = base.variantFor(ApiVariantType.REMOVED)

        mutable.add(mainCore)

        assertEquals("ApiVariantSet[main(C)]", mutable.toString())
        assertTrue(mainCore in mutable, "main(C) should contain main(CORE)")
        assertFalse(baseRemoved in mutable, "main(C) should not contain base(REMOVED)")
        assertTrue(mutable.containsAny(main), "main(C) should contain something from main")
        assertFalse(mutable.containsAny(base), "main(C) should not contain anything from base")

        mutable.add(baseRemoved)

        assertEquals("ApiVariantSet[base(R),main(C)]", mutable.toString())
        assertTrue(mainCore in mutable, "base(R),main(C) should contain main(CORE)")
        assertTrue(baseRemoved in mutable, "base(R),main(C) should contain base(REMOVED)")
        assertTrue(mutable.containsAny(main), "base(R),main(C) should contain something from main")
        assertTrue(mutable.containsAny(base), "base(R),main(C) should contain something from base")

        mutable.remove(mainCore)

        assertEquals("ApiVariantSet[base(R)]", mutable.toString())
        assertFalse(mainCore in mutable, "base(R),main(C) should not contain main(CORE)")
        assertTrue(baseRemoved in mutable, "base(R),main(C) should contain base(REMOVED)")
        assertFalse(
            mutable.containsAny(main),
            "base(R),main(C) should not contain anything from main"
        )
        assertTrue(mutable.containsAny(base), "base(R),main(C) should contain something from base")
    }

    @Test
    fun `Test clear`() {
        val mutable = MutableApiVariantSet.setOf(apiSurfaces)

        val mainCore = main.variantFor(ApiVariantType.CORE)
        val baseCore = base.variantFor(ApiVariantType.CORE)

        mutable.add(mainCore)
        mutable.add(baseCore)

        assertFalse(mutable.isEmpty(), "expected not empty before clear")
        mutable.clear()
        assertTrue(mutable.isEmpty(), "expected empty before clear")
    }

    @Test
    fun `Test mutable and immutable`() {
        val mutable = MutableApiVariantSet.setOf(apiSurfaces)

        val mainCore = main.variantFor(ApiVariantType.CORE)
        val baseCore = base.variantFor(ApiVariantType.CORE)

        mutable.add(mainCore)
        mutable.add(baseCore)

        // Explicit type is needed to ensure correct type inference.
        val immutable: BaseApiVariantSet = mutable.toImmutable()
        assertEquals(mutable, immutable, "mutable and immutable set should be equal")
        assertEquals(
            mutable.hashCode(),
            immutable.hashCode(),
            "mutable and immutable set hashCode should be equal"
        )

        mutable.remove(mainCore)

        assertNotEquals(mutable, immutable, "mutable and immutable set should not be equal")
        assertNotEquals(
            mutable.hashCode(),
            immutable.hashCode(),
            "mutable and immutable set hashCode should not be equal"
        )

        val anotherImmutable: BaseApiVariantSet = mutable.toImmutable()

        assertNotEquals(
            anotherImmutable,
            immutable,
            "anotherImmutable and immutable set should not be equal"
        )
        assertNotEquals(
            anotherImmutable.hashCode(),
            immutable.hashCode(),
            "anotherImmutable and immutable set hashCode should not be equal"
        )
    }

    @Test
    fun `Test empty variant set`() {
        val empty = apiSurfaces.emptyVariantSet
        assertEquals(empty.toString(), "ApiVariantSet[]", "empty toString")

        val mutable = MutableApiVariantSet.setOf(apiSurfaces)
        assertSame(empty, mutable.toImmutable(), "empty mutable to immutable")
    }
}
