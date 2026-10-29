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
import kotlin.test.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ApiVariantSetTest {
    private val apiSurfaces = ApiSurfaces.create(needsBase = true)

    private val main = apiSurfaces.main
    private val base = apiSurfaces.base!!

    private val mainCore = main.variantFor(ApiVariantType.CORE)
    private val mainRemoved = main.variantFor(ApiVariantType.REMOVED)
    private val baseRemoved = base.variantFor(ApiVariantType.REMOVED)
    private val baseDocOnly = base.variantFor(ApiVariantType.DOC_ONLY)

    private fun ApiVariantSet.format() = formatFor(apiSurfaces)

    @Test
    fun `Test empty variant set`() {
        val variantSet = ApiVariantSet.EMPTY

        assertTrue(variantSet.isEmpty(), "isEmpty")
        assertFalse(variantSet.isNotEmpty(), "isNotEmpty")

        for (variant in apiSurfaces.variants) {
            assertFalse(variantSet.contains(variant), "contains($variant)")
        }

        assertEquals("ApiVariantSet[]", variantSet.format(), "empty set")
    }

    @Test
    fun `Test plus and minus variant`() {
        var variantSet = ApiVariantSet.EMPTY

        variantSet += mainCore

        assertTrue(variantSet.isNotEmpty(), "isNotEmpty")
        assertEquals("ApiVariantSet[main(C)]", variantSet.format())
        assertTrue(mainCore in variantSet, "main(C) should contain main(CORE)")
        assertFalse(baseRemoved in variantSet, "main(C) should not contain base(REMOVED)")
        assertTrue(variantSet.containsAny(main), "main(C) should contain something from main")
        assertFalse(variantSet.containsAny(base), "main(C) should not contain anything from base")

        variantSet += baseRemoved

        assertEquals("ApiVariantSet[base(R),main(C)]", variantSet.format())
        assertTrue(mainCore in variantSet, "base(R),main(C) should contain main(CORE)")
        assertTrue(baseRemoved in variantSet, "base(R),main(C) should contain base(REMOVED)")
        assertTrue(
            variantSet.containsAny(main),
            "base(R),main(C) should contain something from main"
        )
        assertTrue(
            variantSet.containsAny(base),
            "base(R),main(C) should contain something from base"
        )

        variantSet -= mainCore

        assertEquals("ApiVariantSet[base(R)]", variantSet.format())
        assertFalse(mainCore in variantSet, "base(R) should not contain main(CORE)")
        assertTrue(baseRemoved in variantSet, "base(R) should contain base(REMOVED)")
        assertFalse(variantSet.containsAny(main), "base(R) should not contain anything from main")
        assertTrue(variantSet.containsAny(base), "base(R) should contain something from base")

        variantSet -= baseRemoved
        assertTrue(variantSet.isEmpty(), "isEmpty")
    }

    @Test
    fun `Test plus and minus set`() {
        val set1 = apiSurfaces.createVariantSet(mainCore, mainRemoved, baseDocOnly)
        val set2 = apiSurfaces.createVariantSet(mainCore, baseRemoved)

        assertEquals(
            "ApiVariantSet[base(RD),main(CR)]",
            (set1 + set2).format(),
            message = "set1 + set2"
        )
        assertEquals(
            "ApiVariantSet[base(RD),main(CR)]",
            (set2 + set1).format(),
            message = "set2 + set1"
        )

        assertEquals(
            "ApiVariantSet[base(D),main(R)]",
            (set1 - set2).format(),
            message = "set1 - set2"
        )
        assertEquals("ApiVariantSet[base(R)]", (set2 - set1).format(), message = "set2 - set1")
    }

    @Test
    fun `Test intersectionWith`() {
        val set1 = apiSurfaces.createVariantSet(mainCore, mainRemoved, baseDocOnly)
        val set2 = apiSurfaces.createVariantSet(mainCore, baseRemoved)
        val set3 = apiSurfaces.createVariantSet(baseRemoved, baseDocOnly)

        assertEquals(
            "ApiVariantSet[main(C)]",
            set1.intersectionWith(set2).format(),
            message = "set1 intersection set2"
        )
        assertEquals(
            "ApiVariantSet[main(C)]",
            set2.intersectionWith(set1).format(),
            message = "set2 intersection set1"
        )
        assertEquals(
            "ApiVariantSet[base(D)]",
            set1.intersectionWith(set3).format(),
            message = "set1 intersection set3"
        )
        assertEquals(
            "ApiVariantSet[base(R)]",
            set2.intersectionWith(set3).format(),
            message = "set2 intersection set3"
        )
        assertEquals(
            "ApiVariantSet[]",
            set1.intersectionWith(ApiVariantSet.EMPTY).format(),
            message = "set1 intersection empty"
        )
    }

    @Test
    fun `Test isHiddenOrRemoved`() {
        // An empty set represents an item not present in any API variant (i.e. hidden).
        assertTrue(ApiVariantSet.EMPTY.isHiddenOrRemoved(), "empty set is hidden")

        // Sets containing only REMOVED variants across one or more surfaces are hiddenOrRemoved.
        val mainRemovedSet = apiSurfaces.createVariantSet(mainRemoved)
        assertTrue(mainRemovedSet.isHiddenOrRemoved(), "main(R) is hidden or removed")

        val baseRemovedSet = apiSurfaces.createVariantSet(baseRemoved)
        assertTrue(baseRemovedSet.isHiddenOrRemoved(), "base(R) is hidden or removed")

        val allRemovedSet = apiSurfaces.createVariantSet(mainRemoved, baseRemoved)
        assertTrue(allRemovedSet.isHiddenOrRemoved(), "base(R),main(R) is hidden or removed")

        // Sets containing CORE variants are not hidden or removed.
        val mainCoreSet = apiSurfaces.createVariantSet(mainCore)
        assertFalse(mainCoreSet.isHiddenOrRemoved(), "main(C) is not hidden or removed")

        val baseCore = base.variantFor(ApiVariantType.CORE)
        val baseCoreSet = apiSurfaces.createVariantSet(baseCore)
        assertFalse(baseCoreSet.isHiddenOrRemoved(), "base(C) is not hidden or removed")

        // Sets containing DOC_ONLY variants are not hidden or removed.
        val baseDocOnlySet = apiSurfaces.createVariantSet(baseDocOnly)
        assertFalse(baseDocOnlySet.isHiddenOrRemoved(), "base(D) is not hidden or removed")

        val mainDocOnly = main.variantFor(ApiVariantType.DOC_ONLY)
        val mainDocOnlySet = apiSurfaces.createVariantSet(mainDocOnly)
        assertFalse(mainDocOnlySet.isHiddenOrRemoved(), "main(D) is not hidden or removed")

        // Sets containing a mix of REMOVED and non-removed variants are not hidden or removed.
        val mixedSet1 = apiSurfaces.createVariantSet(mainCore, mainRemoved)
        assertFalse(mixedSet1.isHiddenOrRemoved(), "main(CR) is not hidden or removed")

        val mixedSet2 = apiSurfaces.createVariantSet(baseRemoved, mainCore)
        assertFalse(mixedSet2.isHiddenOrRemoved(), "base(R),main(C) is not hidden or removed")

        val mixedSet3 = apiSurfaces.createVariantSet(baseRemoved, baseDocOnly)
        assertFalse(mixedSet3.isHiddenOrRemoved(), "base(RD) is not hidden or removed")

        // Test across 3 surfaces
        val threeSurfaces =
            ApiSurfaces.build {
                createSurface("public")
                createSurface("system", extends = "public")
                createSurface("module", extends = "system", isMain = true)
            }
        val publicRemoved =
            threeSurfaces.byName.getValue("public").variantFor(ApiVariantType.REMOVED)
        val systemRemoved =
            threeSurfaces.byName.getValue("system").variantFor(ApiVariantType.REMOVED)
        val moduleRemoved =
            threeSurfaces.byName.getValue("module").variantFor(ApiVariantType.REMOVED)
        val moduleCore = threeSurfaces.byName.getValue("module").variantFor(ApiVariantType.CORE)

        val threeRemoved =
            threeSurfaces.createVariantSet(publicRemoved, systemRemoved, moduleRemoved)
        assertTrue(
            threeRemoved.isHiddenOrRemoved(),
            "3 surfaces with only REMOVED are hidden or removed",
        )

        val threeMixed = threeSurfaces.createVariantSet(publicRemoved, systemRemoved, moduleCore)
        assertFalse(
            threeMixed.isHiddenOrRemoved(),
            "3 surfaces with REMOVED and CORE is not hidden or removed",
        )
    }

    @Test
    fun `Test narrowest and widest surfaces`() {
        val emptySet = ApiVariantSet.EMPTY
        assertEquals(null, emptySet.narrowestSurfaceFor(apiSurfaces), "empty narrowest")
        assertEquals(null, emptySet.widestSurfaceFor(apiSurfaces), "empty widest")

        val mainSet = apiSurfaces.createVariantSet(mainCore, mainRemoved)
        assertEquals(main, mainSet.narrowestSurfaceFor(apiSurfaces), "main only narrowest")
        assertEquals(main, mainSet.widestSurfaceFor(apiSurfaces), "main only widest")

        val baseSet = apiSurfaces.createVariantSet(baseRemoved, baseDocOnly)
        assertEquals(base, baseSet.narrowestSurfaceFor(apiSurfaces), "base only narrowest")
        assertEquals(base, baseSet.widestSurfaceFor(apiSurfaces), "base only widest")

        val mixedSet = apiSurfaces.createVariantSet(mainCore, baseDocOnly)
        assertEquals(base, mixedSet.narrowestSurfaceFor(apiSurfaces), "mixed narrowest")
        assertEquals(main, mixedSet.widestSurfaceFor(apiSurfaces), "mixed widest")
    }

    @Test
    fun `Test moveVariantsBetweenSurfaces`() {
        val baseCore = base.variantFor(ApiVariantType.CORE)
        val mainDocOnly = main.variantFor(ApiVariantType.DOC_ONLY)

        // Move from narrower to wider surface.
        val baseSet = apiSurfaces.createVariantSet(baseCore, baseRemoved)
        assertEquals(
            "ApiVariantSet[main(CR)]",
            baseSet.moveVariantsBetweenSurfaces(from = base, to = main).format(),
            message = "move base to main",
        )

        // Move from wider to narrower surface.
        val mainSet = apiSurfaces.createVariantSet(mainRemoved, mainDocOnly)
        assertEquals(
            "ApiVariantSet[base(RD)]",
            mainSet.moveVariantsBetweenSurfaces(from = main, to = base).format(),
            message = "move main to base",
        )

        // Move ignores variants that are not from the source surface.
        val mixedSet = apiSurfaces.createVariantSet(baseCore, mainRemoved)
        assertEquals(
            "ApiVariantSet[main(C)]",
            mixedSet.moveVariantsBetweenSurfaces(from = base, to = main).format(),
            message = "move base to main from mixed set",
        )
        assertEquals(
            "ApiVariantSet[base(R)]",
            mixedSet.moveVariantsBetweenSurfaces(from = main, to = base).format(),
            message = "move main to base from mixed set",
        )

        // Move from empty set or a set with no variants in source surface returns empty set.
        assertEquals(
            "ApiVariantSet[]",
            ApiVariantSet.EMPTY.moveVariantsBetweenSurfaces(from = base, to = main).format(),
            message = "move from empty set",
        )
        assertEquals(
            "ApiVariantSet[]",
            mainSet.moveVariantsBetweenSurfaces(from = base, to = main).format(),
            message = "move when no variants in from surface",
        )

        // Move between same surface preserves variants from that surface and filters out others.
        assertEquals(
            "ApiVariantSet[base(C)]",
            mixedSet.moveVariantsBetweenSurfaces(from = base, to = base).format(),
            message = "move base to base",
        )
        assertEquals(
            "ApiVariantSet[main(R)]",
            mixedSet.moveVariantsBetweenSurfaces(from = main, to = main).format(),
            message = "move main to main",
        )

        // Test with 3 surfaces: public -> system -> module
        val threeSurfaces =
            ApiSurfaces.build {
                createSurface("public")
                createSurface("system", extends = "public")
                createSurface("module", extends = "system", isMain = true)
            }
        val publicSurface = threeSurfaces.byName.getValue("public")
        val systemSurface = threeSurfaces.byName.getValue("system")
        val moduleSurface = threeSurfaces.byName.getValue("module")

        val publicCore = publicSurface.variantFor(ApiVariantType.CORE)
        val publicRemoved = publicSurface.variantFor(ApiVariantType.REMOVED)
        val testSet = threeSurfaces.createVariantSet(publicCore, publicRemoved)

        // public -> system (shift 1 surface up)
        assertEquals(
            "ApiVariantSet[system(CR)]",
            testSet
                .moveVariantsBetweenSurfaces(from = publicSurface, to = systemSurface)
                .formatFor(threeSurfaces),
            message = "move public to system",
        )
        // public -> module (shift 2 surfaces up)
        assertEquals(
            "ApiVariantSet[module(CR)]",
            testSet
                .moveVariantsBetweenSurfaces(from = publicSurface, to = moduleSurface)
                .formatFor(threeSurfaces),
            message = "move public to module",
        )

        // module -> public (shift 2 surfaces down)
        val moduleSet =
            testSet.moveVariantsBetweenSurfaces(from = publicSurface, to = moduleSurface)
        assertEquals(
            "ApiVariantSet[public(CR)]",
            moduleSet
                .moveVariantsBetweenSurfaces(from = moduleSurface, to = publicSurface)
                .formatFor(threeSurfaces),
            message = "move module to public",
        )
        // module -> system (shift 1 surface down)
        assertEquals(
            "ApiVariantSet[system(CR)]",
            moduleSet
                .moveVariantsBetweenSurfaces(from = moduleSurface, to = systemSurface)
                .formatFor(threeSurfaces),
            message = "move module to system",
        )
    }
}
