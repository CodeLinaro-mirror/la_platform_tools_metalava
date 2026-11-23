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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.BaseOptionGroupTest
import com.android.tools.metalava.config.AnnotationRuleConfig
import com.android.tools.metalava.config.ApiSurfaceConfig
import com.android.tools.metalava.config.ApiSurfacesConfig
import com.android.tools.metalava.config.EffectConfig
import com.android.tools.metalava.config.SelectionCriteriaConfig
import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.ApiSurfaceSelector
import com.android.tools.metalava.model.testing.api.assertState
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Tests for the creation of [ApiSurfaceSelector] and related objects. */
@RunWith(Parameterized::class)
class ParameterizedApiSelectionOptionsTest :
    BaseOptionGroupTest<ApiSelectionOptions>(API_SELECTION_OPTIONS_HELP) {

    /** Parameterized by [SurfaceRuleSource]. */
    @Parameterized.Parameter(0) lateinit var surfaceRuleSource: SurfaceRuleSource

    /**
     * Enumeration of the source of the API surface rule information.
     *
     * @param useOptions use options to define the rules.
     * @param useConfig use [ApiSurfacesConfig] to define the rules.
     */
    enum class SurfaceRuleSource(
        val useOptions: Boolean,
        val useConfig: Boolean,
    ) {
        /** Run the test providing surface rules using command line options only. */
        OPTIONS_ONLY(
            useOptions = true,
            useConfig = false,
        ),

        /** Run the test providing surface rules using [ApiSurfacesConfig] only. */
        CONFIG_ONLY(
            useOptions = false,
            useConfig = true,
        ),
    }

    companion object {
        /** Supply the list of [SurfaceRuleSource] entries as the parameters for this test class. */
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = SurfaceRuleSource.entries
    }

    override fun createOptions() = ApiSelectionOptions()

    /**
     * Check the state of the [ApiSurfaceSelector] created by [ApiSelectionOptions].
     *
     * @param ruleOptions the command line options that specify the API selection rules. If set to
     *   `null` then this test will not be run for [SurfaceRuleSource.OPTIONS_ONLY].
     * @param apiSurfacesConfigWithRules the [ApiSurfaceConfig] to use.
     * @param expectedMatcherState see [assertState].
     * @param expectedShowUnannotated see [assertState].
     * @param expectedUnannotatedSurfaceName the expected name of the unannotated surface.
     * @param expectedSurfaceRules the expected [ApiSurfaceRules] passed to [ApiSurfaceSelector].
     */
    private fun checkApiSurfaceSelectorState(
        surface: String,
        ruleOptions: List<String>?,
        apiSurfacesConfigWithRules: ApiSurfacesConfig,
        expectedMatcherState: String,
        expectedShowUnannotated: Boolean,
        expectedUnannotatedSurfaceName: String?,
        expectedSurfaceRules: String,
    ) {
        // Ignore the test if it could not be replicated with options.
        if (surfaceRuleSource.useOptions) {
            assumeTrue(ruleOptions != null)
        }

        val apiSurfacesConfig =
            if (surfaceRuleSource.useConfig) {
                apiSurfacesConfigWithRules
            } else {
                null
            }

        val optionGroup = ApiSelectionOptions()
        val combinedArgs = buildList {
            if (surfaceRuleSource.useConfig) {
                add(ARG_API_SURFACE)
                add(surface)
            }
            if (surfaceRuleSource.useOptions) {
                addAll(ruleOptions!!)
            }
        }
        runTest(args = combinedArgs.toTypedArray(), optionGroup = optionGroup) {
            val computedOptions = options.compute(apiSurfacesConfig)
            val selector = computedOptions.apiSurfaceSelector
            selector.assertState(
                expectedMatcherState = expectedMatcherState,
                expectedShowUnannotated = expectedShowUnannotated,
                expectedUnannotatedSurfaceName = expectedUnannotatedSurfaceName,
            )

            if (surfaceRuleSource.useOptions) {
                val rules = computedOptions.createApiSurfaceRulesFromOptions()
                assertEquals(
                    expectedSurfaceRules.trimIndent(),
                    rules.toString(),
                    message = "ApiSurfaceRules from options"
                )
            }

            if (surfaceRuleSource.useConfig) {
                val rules = computedOptions.createApiSurfaceRulesFromConfig()
                assertEquals(
                    expectedSurfaceRules.trimIndent(),
                    rules.toString(),
                    message = "ApiSurfaceRules from config"
                )
            }
        }
    }

    @Test
    fun `Test configuring api surface rules - main`() {
        checkApiSurfaceSelectorState(
            surface = "main",
            ruleOptions =
                listOf(
                    ARG_HIDE_ANNOTATION,
                    "android.annotation.Hide",
                ),
            apiSurfacesConfigWithRules =
                ApiSurfacesConfig(
                    apiSurfaceList =
                        listOf(
                            ApiSurfaceConfig(
                                name = "main",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        unannotated = EffectConfig.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern = "android.annotation.Hide",
                                                    effect = EffectConfig.HIDE,
                                                ),
                                            ),
                                    ),
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
                    )
                """,
            expectedShowUnannotated = true,
            expectedUnannotatedSurfaceName = "main",
            expectedSurfaceRules =
                """
                    ApiSurfaceRules(
                        main -> {
                            SelectUnannotated
                            SelectAnnotated(annotationPattern=android.annotation.Hide, effect=HIDE, recursive=true)
                        }
                    )
                """,
        )
    }

    @Test
    fun `Test configuring api surface rules - main plus other`() {
        checkApiSurfaceSelectorState(
            surface = "main",
            ruleOptions =
                listOf(
                    ARG_HIDE_ANNOTATION,
                    "android.annotation.Hide",
                    ARG_SHOW_UNANNOTATED,
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.OtherApi",
                ),
            apiSurfacesConfigWithRules =
                ApiSurfacesConfig(
                    apiSurfaceList =
                        listOf(
                            ApiSurfaceConfig(
                                name = "main",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        unannotated = EffectConfig.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern = "android.annotation.Hide",
                                                    effect = EffectConfig.HIDE,
                                                ),
                                                AnnotationRuleConfig(
                                                    pattern = "android.annotation.OtherApi",
                                                ),
                                            ),
                                    ),
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
            expectedSurfaceRules =
                """
                    ApiSurfaceRules(
                        main -> {
                            SelectUnannotated
                            SelectAnnotated(annotationPattern=android.annotation.Hide, effect=HIDE, recursive=true)
                            SelectAnnotated(annotationPattern=android.annotation.OtherApi, effect=SHOW, recursive=true)
                        }
                    )
                """,
        )
    }

    @Test
    fun `Test configuring api surface rules - main extending base`() {
        checkApiSurfaceSelectorState(
            surface = "main",
            ruleOptions =
                listOf(
                    ARG_HIDE_ANNOTATION,
                    "android.annotation.Hide",
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
                ),
            apiSurfacesConfigWithRules =
                ApiSurfacesConfig(
                    apiSurfaceList =
                        listOf(
                            ApiSurfaceConfig(
                                name = "base",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        unannotated = EffectConfig.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern = "android.annotation.Hide",
                                                    effect = EffectConfig.HIDE,
                                                ),
                                            ),
                                    ),
                            ),
                            ApiSurfaceConfig(
                                name = "main",
                                extends = "base",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern =
                                                        "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
                                                ),
                                            ),
                                    ),
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
                        android.annotation.SystemApi -> {
                            Entry(
                                client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                result: SurfaceAnnotationData(surface=ApiSurface(main), effect=SHOW, recursive=true)
                            )
                        }
                    )
                """,
            expectedShowUnannotated = false,
            expectedUnannotatedSurfaceName = "base",
            expectedSurfaceRules =
                """
                    ApiSurfaceRules(
                        base -> {
                            SelectUnannotated
                            SelectAnnotated(annotationPattern=android.annotation.Hide, effect=HIDE, recursive=true)
                        }
                        main -> {
                            SelectAnnotated(annotationPattern=android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS), effect=SHOW, recursive=true)
                        }
                    )
                """,
        )
    }

    @Test
    fun `Test configuring api surface rules - extra`() {
        checkApiSurfaceSelectorState(
            surface = "extra",
            // Is not supported using command line options.
            ruleOptions = null,
            apiSurfacesConfigWithRules =
                ApiSurfacesConfig(
                    apiSurfaceList =
                        listOf(
                            ApiSurfaceConfig(
                                name = "base",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        unannotated = EffectConfig.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern = "android.annotation.Hide",
                                                    effect = EffectConfig.HIDE,
                                                ),
                                            ),
                                    ),
                            ),
                            ApiSurfaceConfig(
                                name = "main",
                                extends = "base",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern =
                                                        "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
                                                ),
                                            ),
                                    ),
                            ),
                            ApiSurfaceConfig(
                                name = "extra",
                                extends = "main",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern =
                                                        "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.MODULE_LIBRARIES)",
                                                ),
                                            ),
                                    ),
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
                        android.annotation.SystemApi -> {
                            Entry(
                                client=android.annotation.SystemApi.Client.MODULE_LIBRARIES
                                result: SurfaceAnnotationData(surface=ApiSurface(extra), effect=SHOW, recursive=true)
                            )
                            Entry(
                                client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                result: SurfaceAnnotationData(surface=ApiSurface(main), effect=SHOW, recursive=true)
                            )
                        }
                    )
                """,
            expectedShowUnannotated = false,
            expectedUnannotatedSurfaceName = "base",
            expectedSurfaceRules =
                """
                    ApiSurfaceRules(
                        base -> {
                            SelectUnannotated
                            SelectAnnotated(annotationPattern=android.annotation.Hide, effect=HIDE, recursive=true)
                        }
                        main -> {
                            SelectAnnotated(annotationPattern=android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS), effect=SHOW, recursive=true)
                        }
                        extra -> {
                            SelectAnnotated(annotationPattern=android.annotation.SystemApi(client=android.annotation.SystemApi.Client.MODULE_LIBRARIES), effect=SHOW, recursive=true)
                        }
                    )
                """,
        )
    }

    @Test
    fun `Test configuring api surface rules - non-recursive`() {
        checkApiSurfaceSelectorState(
            surface = "main",
            // Is not supported using command line options.
            ruleOptions = null,
            apiSurfacesConfigWithRules =
                ApiSurfacesConfig(
                    apiSurfaceList =
                        listOf(
                            ApiSurfaceConfig(
                                name = "base",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        unannotated = EffectConfig.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern = "android.annotation.Hide",
                                                    effect = EffectConfig.HIDE,
                                                ),
                                            ),
                                    ),
                            ),
                            ApiSurfaceConfig(
                                name = "main",
                                extends = "base",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern =
                                                        "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
                                                    recursive = false,
                                                ),
                                            ),
                                    ),
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
                        android.annotation.SystemApi -> {
                            Entry(
                                client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                result: SurfaceAnnotationData(surface=ApiSurface(main), effect=SHOW, recursive=false)
                            )
                        }
                    )
                """,
            expectedShowUnannotated = false,
            expectedUnannotatedSurfaceName = "base",
            expectedSurfaceRules =
                """
                    ApiSurfaceRules(
                        base -> {
                            SelectUnannotated
                            SelectAnnotated(annotationPattern=android.annotation.Hide, effect=HIDE, recursive=true)
                        }
                        main -> {
                            SelectAnnotated(annotationPattern=android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS), effect=SHOW, recursive=false)
                        }
                    )
                """,
        )
    }
}
