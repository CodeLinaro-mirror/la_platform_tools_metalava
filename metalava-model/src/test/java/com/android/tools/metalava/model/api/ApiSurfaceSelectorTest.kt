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

package com.android.tools.metalava.model.api

import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.testing.api.assertState
import org.junit.Assert.*
import org.junit.Test

class ApiSurfaceSelectorTest {
    /**
     * Check the rules encapsulated within the [ApiSurfaceSelector].
     *
     * All the information about the rules is encapsulated within the [ApiSurfaceSelector.matcher]
     * apart from that which is part of [ApiSurfaceSelector.showUnannotated].
     */
    private fun checkRules(
        apiSurfaces: ApiSurfaces = ApiSurfaces.create(),
        rulesByName: Map<String, List<SurfaceSelectionRule>>,
        mainSurfaceName: String? = null,
        expectedMatcherState: String,
        expectedShowUnannotated: Boolean,
        expectedUnannotatedSurfaceName: String?,
    ) {
        val apiSurfaceRules =
            ApiSurfaceRules(apiSurfaces, rulesByName).let {
                if (mainSurfaceName == null) it else it.retargetAt(mainSurfaceName)
            }
        val surfaceSelector = ApiSurfaceSelector(apiSurfaceRules)
        surfaceSelector.assertState(
            expectedMatcherState = expectedMatcherState,
            expectedShowUnannotated = expectedShowUnannotated,
            expectedUnannotatedSurfaceName = expectedUnannotatedSurfaceName,
        )
    }

    /**
     * Check an invalid set of rules that should cause [ApiSurfaceSelector] to throw an exception.
     */
    private fun invalidRules(
        apiSurfaces: ApiSurfaces = ApiSurfaces.create(),
        rulesByName: Map<String, List<SurfaceSelectionRule>>,
        expectedError: String,
    ) {
        val apiSurfaceRules = ApiSurfaceRules(apiSurfaces, rulesByName)
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                ApiSurfaceSelector(apiSurfaceRules)
            }
        assertEquals(expectedError, exception.message)
    }

    @Test
    fun `Test invalid - unannotated not on narrowest surface`() {
        invalidRules(
            apiSurfaces = ApiSurfaces.create(needsBase = true),
            rulesByName =
                mapOf(
                    "base" to
                        listOf(
                            SurfaceSelectionRule.createAnnotationRule(
                                "android.annotation.Hide",
                                effect = SurfaceSelectionRule.Effect.HIDE
                            ),
                        ),
                    "main" to
                        listOf(
                            SurfaceSelectionRule.unannotated,
                            SurfaceSelectionRule.createAnnotationRule(
                                "android.annotation.OtherApi"
                            ),
                        ),
                ),
            expectedError =
                "unannotated rule is only allowed on narrowest surface ApiSurface(base) but was found on ApiSurface(main)",
        )
    }

    @Test
    fun `Test invalid - hide not on narrowest surface`() {
        invalidRules(
            apiSurfaces = ApiSurfaces.create(needsBase = true),
            rulesByName =
                mapOf(
                    "base" to
                        listOf(
                            SurfaceSelectionRule.unannotated,
                        ),
                    "main" to
                        listOf(
                            SurfaceSelectionRule.createAnnotationRule(
                                "android.annotation.Hide",
                                effect = SurfaceSelectionRule.Effect.HIDE
                            ),
                            SurfaceSelectionRule.createAnnotationRule(
                                "android.annotation.OtherApi"
                            ),
                        ),
                ),
            expectedError =
                "hide rules are only allowed on narrowest surface ApiSurface(base) but SelectAnnotated(annotationPattern=android.annotation.Hide, effect=HIDE, recursive=true) was found on ApiSurface(main)",
        )
    }

    @Test
    fun `Test invalid - non-recursive not on main surface`() {
        invalidRules(
            apiSurfaces = ApiSurfaces.create(needsBase = true),
            rulesByName =
                mapOf(
                    "base" to
                        listOf(
                            SurfaceSelectionRule.unannotated,
                            SurfaceSelectionRule.createAnnotationRule(
                                "android.annotation.OtherApi",
                                recursive = false,
                            ),
                        ),
                    "main" to
                        listOf(
                            SurfaceSelectionRule.createAnnotationRule(
                                "android.annotation.OtherApi"
                            ),
                        ),
                ),
            expectedError =
                "non-recursive rules are only allowed on main surface ApiSurface(main) but was found on ApiSurface(base)",
        )
    }

    @Test
    fun `Test create - unannotated`() {
        checkRules(
            rulesByName = mapOf("main" to listOf(SurfaceSelectionRule.unannotated)),
            expectedMatcherState = "AnnotationMatcher(\n)",
            expectedShowUnannotated = true,
            expectedUnannotatedSurfaceName = "main",
        )
    }

    @Test
    fun `Test create - unannotated and annotated`() {
        checkRules(
            rulesByName =
                mapOf(
                    "main" to
                        listOf(
                            SurfaceSelectionRule.unannotated,
                            SurfaceSelectionRule.createAnnotationRule(
                                "android.annotation.Hide",
                                effect = SurfaceSelectionRule.Effect.HIDE
                            ),
                            SurfaceSelectionRule.createAnnotationRule(
                                "android.annotation.OtherApi"
                            ),
                        ),
                ),
            expectedMatcherState =
                """
                    AnnotationMatcher(
                        android.annotation.Hide -> {
                            Entry(
                                result: SurfaceAnnotationData(surface=ApiSurface(main), effect=HIDE, recursive=true)
                            )
                        }
                        android.annotation.OtherApi -> {
                            Entry(
                                result: SurfaceAnnotationData(surface=ApiSurface(main), effect=SHOW, recursive=true)
                            )
                        }
                    )
                """,
            expectedShowUnannotated = true,
            expectedUnannotatedSurfaceName = "main",
        )
    }

    @Test
    fun `Test create - unannotated extended by annotated once`() {
        checkRules(
            apiSurfaces = ApiSurfaces.create(needsBase = true),
            rulesByName =
                mapOf(
                    "base" to
                        listOf(
                            SurfaceSelectionRule.unannotated,
                            SurfaceSelectionRule.createAnnotationRule(
                                "android.annotation.Hide",
                                effect = SurfaceSelectionRule.Effect.HIDE
                            ),
                        ),
                    "main" to
                        listOf(
                            SurfaceSelectionRule.createAnnotationRule(
                                "android.annotation.OtherApi"
                            ),
                        ),
                ),
            expectedMatcherState =
                """
                    AnnotationMatcher(
                        android.annotation.Hide -> {
                            Entry(
                                result: SurfaceAnnotationData(surface=ApiSurface(base), effect=HIDE, recursive=true)
                            )
                        }
                        android.annotation.OtherApi -> {
                            Entry(
                                result: SurfaceAnnotationData(surface=ApiSurface(main), effect=SHOW, recursive=true)
                            )
                        }
                    )
                """,
            expectedShowUnannotated = false,
            expectedUnannotatedSurfaceName = "base",
        )
    }

    @Test
    fun `Test create - unannotated extended by annotated twice - main`() {
        checkRules(
            apiSurfaces = baseIntermediateMainSurfaces,
            rulesByName = baseIntermediateMainRules,
            expectedMatcherState =
                """
                    AnnotationMatcher(
                        android.annotation.Hide -> {
                            Entry(
                                result: SurfaceAnnotationData(surface=ApiSurface(base), effect=HIDE, recursive=true)
                            )
                        }
                        android.annotation.SystemApi -> {
                            Entry(
                                client=android.annotation.SystemApi.Client.MODULE_LIBRARIES
                                result: SurfaceAnnotationData(surface=ApiSurface(main), effect=SHOW, recursive=true)
                            )
                            Entry(
                                client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                result: SurfaceAnnotationData(surface=ApiSurface(intermediate), effect=SHOW, recursive=true)
                            )
                        }
                    )
                """,
            expectedShowUnannotated = false,
            expectedUnannotatedSurfaceName = "base",
        )
    }

    @Test
    fun `Test create - unannotated extended by annotated twice - intermediate`() {
        checkRules(
            apiSurfaces = baseIntermediateMainSurfaces,
            rulesByName = baseIntermediateMainRules,
            mainSurfaceName = "intermediate",
            expectedMatcherState =
                """
                    AnnotationMatcher(
                        android.annotation.Hide -> {
                            Entry(
                                result: SurfaceAnnotationData(surface=ApiSurface(base), effect=HIDE, recursive=true)
                            )
                        }
                        android.annotation.SystemApi -> {
                            Entry(
                                client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                result: SurfaceAnnotationData(surface=ApiSurface(intermediate), effect=SHOW, recursive=true)
                            )
                            Entry(
                                client=android.annotation.SystemApi.Client.MODULE_LIBRARIES
                                result: SurfaceAnnotationData(surface=ApiSurface(base), effect=HIDE, recursive=true)
                            )
                        }
                    )
                """,
            expectedShowUnannotated = false,
            expectedUnannotatedSurfaceName = "base",
        )
    }

    companion object {
        private val baseIntermediateMainSurfaces =
            ApiSurfaces.build {
                createSurface("base")
                createSurface("intermediate", extends = "base")
                createSurface("main", extends = "intermediate", isMain = true)
            }

        val baseIntermediateMainRules =
            mapOf(
                "base" to
                    listOf(
                        SurfaceSelectionRule.unannotated,
                        SurfaceSelectionRule.createAnnotationRule(
                            "android.annotation.Hide",
                            effect = SurfaceSelectionRule.Effect.HIDE
                        ),
                    ),
                "intermediate" to
                    listOf(
                        SurfaceSelectionRule.createAnnotationRule(
                            "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)"
                        ),
                    ),
                "main" to
                    listOf(
                        SurfaceSelectionRule.createAnnotationRule(
                            "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.MODULE_LIBRARIES)"
                        ),
                    ),
            )
    }
}
