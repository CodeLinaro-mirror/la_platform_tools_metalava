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

package com.android.tools.metalava.model.api.surface

import com.android.tools.metalava.model.visitors.ApiType
import kotlin.test.assertEquals
import org.junit.Test

/** Tests for [ApiSurfacePredicate]. */
class ApiSurfacePredicateTest {
    private val apiSurfaces = ApiSurfaces.create(needsBase = true)
    private val main = apiSurfaces.main
    private val base = apiSurfaces.base!!

    @Test
    fun `Test wholeCoreApi`() {
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(C)])",
            ApiSurfacePredicate.wholeCoreApi(base).toString(),
        )
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(C),main(C)])",
            ApiSurfacePredicate.wholeCoreApi(main).toString(),
        )
    }

    @Test
    fun `Test wholeCoreAndRemovedApi`() {
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(CR)])",
            ApiSurfacePredicate.wholeCoreAndRemovedApi(base).toString(),
        )
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(CR),main(CR)])",
            ApiSurfacePredicate.wholeCoreAndRemovedApi(main).toString(),
        )
    }

    @Test
    fun `Test forStubs`() {
        assertEquals(
            """
                ApiFilters(
                    traversal =
                        AndPredicate(
                            EmittedOnlyPredicate
                            ItemApiVariantsPredicate(ApiVariantSet[base(C)])
                        )
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            ItemApiVariantsPredicate(ApiVariantSet[base(C)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(C)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forStubs(base, includeDocOnly = false).toString(),
            message = "base without docOnly",
        )
        assertEquals(
            """
                ApiFilters(
                    traversal =
                        AndPredicate(
                            EmittedOnlyPredicate
                            ItemApiVariantsPredicate(ApiVariantSet[base(CD)])
                        )
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            ItemApiVariantsPredicate(ApiVariantSet[base(CD)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(CD)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forStubs(base, includeDocOnly = true).toString(),
            message = "base with docOnly",
        )
        assertEquals(
            """
                ApiFilters(
                    traversal =
                        AndPredicate(
                            EmittedOnlyPredicate
                            ItemApiVariantsPredicate(ApiVariantSet[base(C),main(C)])
                        )
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            ItemApiVariantsPredicate(ApiVariantSet[base(C),main(C)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(C),main(C)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forStubs(main, includeDocOnly = false).toString(),
            message = "main without docOnly",
        )
        assertEquals(
            """
                ApiFilters(
                    traversal =
                        AndPredicate(
                            EmittedOnlyPredicate
                            ItemApiVariantsPredicate(ApiVariantSet[base(CD),main(CD)])
                        )
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            ItemApiVariantsPredicate(ApiVariantSet[base(CD),main(CD)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(CD),main(CD)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forStubs(main, includeDocOnly = true).toString(),
            message = "main with docOnly",
        )
    }

    @Test
    fun `Test forDelta`() {
        assertEquals(
            """
                OrPredicate(
                    ItemApiVariantsPredicate(ApiVariantSet[base(C)])
                    SuperClassApiVariantsPredicate(ApiVariantSet[base(C)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forDelta(ApiType.CORE, base).toString(),
        )
        assertEquals(
            """
                OrPredicate(
                    ItemApiVariantsPredicate(ApiVariantSet[base(R)])
                    SuperClassApiVariantsPredicate(ApiVariantSet[base(R)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forDelta(ApiType.REMOVED, base).toString(),
        )
        assertEquals(
            """
                OrPredicate(
                    ItemApiVariantsPredicate(ApiVariantSet[main(C)])
                    SuperClassApiVariantsPredicate(ApiVariantSet[main(C)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forDelta(ApiType.CORE, main).toString(),
        )
        assertEquals(
            """
                OrPredicate(
                    ItemApiVariantsPredicate(ApiVariantSet[main(R)])
                    SuperClassApiVariantsPredicate(ApiVariantSet[main(R)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forDelta(ApiType.REMOVED, main).toString(),
        )
    }

    @Test
    fun `Test referenceFilter`() {
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(C)])",
            ApiSurfacePredicate.referenceFilter(ApiType.CORE, base).toString(),
        )
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(CR)])",
            ApiSurfacePredicate.referenceFilter(ApiType.REMOVED, base).toString(),
        )
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(C),main(C)])",
            ApiSurfacePredicate.referenceFilter(ApiType.CORE, main).toString(),
        )
        assertEquals(
            "ItemApiVariantsPredicate(ApiVariantSet[base(CR),main(CR)])",
            ApiSurfacePredicate.referenceFilter(ApiType.REMOVED, main).toString(),
        )
    }

    @Test
    fun `Test forSurfaceFilters`() {
        assertEquals(
            """
                ApiFilters(
                    traversal =
                        AndPredicate(
                            EmittedOnlyPredicate
                            OrPredicate(
                                ItemApiVariantsPredicate(ApiVariantSet[base(C)])
                                ContentApiVariantsPredicate(ApiVariantSet[base(C)])
                                SuperClassApiVariantsPredicate(ApiVariantSet[base(C)])
                            )
                        )
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            OrPredicate(
                                ItemApiVariantsPredicate(ApiVariantSet[base(C)])
                                SuperClassApiVariantsPredicate(ApiVariantSet[base(C)])
                            )
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(C)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forSurfaceFilters(ApiType.CORE, base).toString(),
        )
        assertEquals(
            """
                ApiFilters(
                    traversal =
                        AndPredicate(
                            EmittedOnlyPredicate
                            OrPredicate(
                                ItemApiVariantsPredicate(ApiVariantSet[base(R)])
                                ContentApiVariantsPredicate(ApiVariantSet[base(R)])
                                SuperClassApiVariantsPredicate(ApiVariantSet[base(R)])
                            )
                        )
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            OrPredicate(
                                ItemApiVariantsPredicate(ApiVariantSet[base(R)])
                                SuperClassApiVariantsPredicate(ApiVariantSet[base(R)])
                            )
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(CR)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forSurfaceFilters(ApiType.REMOVED, base).toString(),
        )
        assertEquals(
            """
                ApiFilters(
                    traversal =
                        AndPredicate(
                            EmittedOnlyPredicate
                            OrPredicate(
                                ItemApiVariantsPredicate(ApiVariantSet[main(C)])
                                ContentApiVariantsPredicate(ApiVariantSet[main(C)])
                                SuperClassApiVariantsPredicate(ApiVariantSet[main(C)])
                            )
                        )
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            OrPredicate(
                                ItemApiVariantsPredicate(ApiVariantSet[main(C)])
                                SuperClassApiVariantsPredicate(ApiVariantSet[main(C)])
                            )
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(C),main(C)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forSurfaceFilters(ApiType.CORE, main).toString(),
        )
        assertEquals(
            """
                ApiFilters(
                    traversal =
                        AndPredicate(
                            EmittedOnlyPredicate
                            OrPredicate(
                                ItemApiVariantsPredicate(ApiVariantSet[main(R)])
                                ContentApiVariantsPredicate(ApiVariantSet[main(R)])
                                SuperClassApiVariantsPredicate(ApiVariantSet[main(R)])
                            )
                        )
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            OrPredicate(
                                ItemApiVariantsPredicate(ApiVariantSet[main(R)])
                                SuperClassApiVariantsPredicate(ApiVariantSet[main(R)])
                            )
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(CR),main(CR)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.forSurfaceFilters(ApiType.REMOVED, main).toString(),
        )
    }

    @Test
    fun `Test apiFilters`() {
        assertEquals(
            """
                ApiFilters(
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            OrPredicate(
                                ItemApiVariantsPredicate(ApiVariantSet[base(C)])
                                SuperClassApiVariantsPredicate(ApiVariantSet[base(C)])
                            )
                            NotElidablePredicate(ApiVariantSet[base(C)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(C)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.apiFilters(ApiType.CORE, base).toString(),
        )
        assertEquals(
            """
                ApiFilters(
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            OrPredicate(
                                ItemApiVariantsPredicate(ApiVariantSet[base(R)])
                                SuperClassApiVariantsPredicate(ApiVariantSet[base(R)])
                            )
                            NotElidablePredicate(ApiVariantSet[base(R)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(CR)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.apiFilters(ApiType.REMOVED, base).toString(),
        )
        assertEquals(
            """
                ApiFilters(
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            OrPredicate(
                                ItemApiVariantsPredicate(ApiVariantSet[main(C)])
                                SuperClassApiVariantsPredicate(ApiVariantSet[main(C)])
                            )
                            NotElidablePredicate(ApiVariantSet[main(C)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(C),main(C)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.apiFilters(ApiType.CORE, main).toString(),
        )
        assertEquals(
            """
                ApiFilters(
                    emit =
                        AndPredicate(
                            EmittedOnlyPredicate
                            OrPredicate(
                                ItemApiVariantsPredicate(ApiVariantSet[main(R)])
                                SuperClassApiVariantsPredicate(ApiVariantSet[main(R)])
                            )
                            NotElidablePredicate(ApiVariantSet[main(R)])
                        )
                    reference =
                        ItemApiVariantsPredicate(ApiVariantSet[base(CR),main(CR)])
                )
            """
                .trimIndent(),
            ApiSurfacePredicate.apiFilters(ApiType.REMOVED, main).toString(),
        )
    }
}
