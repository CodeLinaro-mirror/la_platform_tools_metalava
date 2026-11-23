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
import com.android.tools.metalava.config.ConfigParser
import com.android.tools.metalava.config.toInputSource
import com.android.tools.metalava.model.api.ApiSurfaceSelector
import com.android.tools.metalava.model.testing.api.assertState
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.xml
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Test the state of [ApiSurfaceSelector] created from [ApiSelectionOptions]. */
@RunWith(Parameterized::class)
class ParameterizedApiSurfaceSelectorTest :
    BaseOptionGroupTest<ApiSelectionOptions>(API_SELECTION_OPTIONS_HELP) {

    @Parameterized.Parameter(0) internal lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    internal data class TestParams
    @EntryPoint
    constructor(
        val surface: String,
        val expectedMatcherState: String,
        val expectedShowUnannotated: Boolean,
        val expectedUnannotatedSurfaceName: String?,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String {
            return surface
        }
    }

    companion object {
        val apiSurfacesConfigFile =
            xml(
                "complex-api-surfaces.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../resources/schemas/config.xsd">
                      <api-surfaces>
                        <api-surface name="public">
                          <selection-criteria unannotated="show">
                            <annotation-rule pattern="android.annotation.Hide" effect="hide"/>
                          </selection-criteria>
                        </api-surface>
                        <api-surface name="system" extends="public">
                          <selection-criteria>
                            <annotation-rule pattern="android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)"/>
                          </selection-criteria>
                        </api-surface>
                        <api-surface name="module-lib" extends="system">
                          <selection-criteria>
                            <annotation-rule pattern="android.annotation.SystemApi(client=android.annotation.SystemApi.Client.MODULE_LIBRARIES)"/>
                          </selection-criteria>
                        </api-surface>
                        <api-surface name="test" extends="system">
                          <selection-criteria>
                            <annotation-rule pattern="android.annotation.TestApi"/>
                          </selection-criteria>
                        </api-surface>
                        <api-surface name="system-server" extends="public">
                          <selection-criteria>
                            <annotation-rule pattern="android.annotation.SystemApi(client=android.annotation.SystemApi.Client.SYSTEM_SERVER)"/>
                          </selection-criteria>
                        </api-surface>
                        <api-surface name="system-server-complete" extends="module-lib">
                          <selection-criteria>
                            <annotation-rule pattern="android.annotation.SystemApi(client=android.annotation.SystemApi.Client.SYSTEM_SERVER)"/>
                          </selection-criteria>
                        </api-surface>

                        <api-surface name="core-platform-plus-public">
                          <selection-criteria unannotated="show">
                            <annotation-rule pattern="android.annotation.Hide" effect="hide"/>
                            <annotation-rule pattern="libcore.api.CorePlatformApi(status=libcore.api.CorePlatformApi.Status.STABLE)" recursive="false"/>
                          </selection-criteria>
                        </api-surface>
                      </api-surfaces>
                    </config>
                """
            )

        val apiSurfacesConfig =
            ConfigParser.parseInputSources(listOf(apiSurfacesConfigFile.toInputSource()))
                .apiSurfaces

        private val params =
            listOf(
                TestParams(
                    surface = "public",
                    expectedMatcherState =
                        """
                            AnnotationMatcher(
                                android.annotation.Hide -> {
                                    Entry(
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                }
                                android.annotation.SystemApi -> {
                                    Entry(
                                        client=android.annotation.SystemApi.Client.SYSTEM_SERVER
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                    Entry(
                                        client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                    Entry(
                                        client=android.annotation.SystemApi.Client.MODULE_LIBRARIES
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                }
                                android.annotation.TestApi -> {
                                    Entry(
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                }
                            )
                        """,
                    expectedShowUnannotated = true,
                    expectedUnannotatedSurfaceName = "public",
                ),
                TestParams(
                    surface = "system",
                    expectedMatcherState =
                        """
                            AnnotationMatcher(
                                android.annotation.Hide -> {
                                    Entry(
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                }
                                android.annotation.SystemApi -> {
                                    Entry(
                                        client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                        result: SurfaceAnnotationData(surface=ApiSurface(system), effect=SHOW, recursive=true)
                                    )
                                    Entry(
                                        client=android.annotation.SystemApi.Client.SYSTEM_SERVER
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                    Entry(
                                        client=android.annotation.SystemApi.Client.MODULE_LIBRARIES
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                }
                                android.annotation.TestApi -> {
                                    Entry(
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                }
                            )
                        """,
                    expectedShowUnannotated = false,
                    expectedUnannotatedSurfaceName = "public",
                ),
                TestParams(
                    surface = "test",
                    expectedMatcherState =
                        """
                            AnnotationMatcher(
                                android.annotation.Hide -> {
                                    Entry(
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                }
                                android.annotation.SystemApi -> {
                                    Entry(
                                        client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                        result: SurfaceAnnotationData(surface=ApiSurface(system), effect=SHOW, recursive=true)
                                    )
                                    Entry(
                                        client=android.annotation.SystemApi.Client.SYSTEM_SERVER
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                    Entry(
                                        client=android.annotation.SystemApi.Client.MODULE_LIBRARIES
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                }
                                android.annotation.TestApi -> {
                                    Entry(
                                        result: SurfaceAnnotationData(surface=ApiSurface(test), effect=SHOW, recursive=true)
                                    )
                                }
                            )
                        """,
                    expectedShowUnannotated = false,
                    expectedUnannotatedSurfaceName = "public",
                ),
                TestParams(
                    surface = "system-server",
                    expectedMatcherState =
                        """
                            AnnotationMatcher(
                                android.annotation.Hide -> {
                                    Entry(
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                }
                                android.annotation.SystemApi -> {
                                    Entry(
                                        client=android.annotation.SystemApi.Client.SYSTEM_SERVER
                                        result: SurfaceAnnotationData(surface=ApiSurface(system-server), effect=SHOW, recursive=true)
                                    )
                                    Entry(
                                        client=android.annotation.SystemApi.Client.SYSTEM_SERVER
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                    Entry(
                                        client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                    Entry(
                                        client=android.annotation.SystemApi.Client.MODULE_LIBRARIES
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                }
                                android.annotation.TestApi -> {
                                    Entry(
                                        result: SurfaceAnnotationData(surface=ApiSurface(public), effect=HIDE, recursive=true)
                                    )
                                }
                            )
                        """,
                    expectedShowUnannotated = false,
                    expectedUnannotatedSurfaceName = "public",
                ),
                TestParams(
                    surface = "core-platform-plus-public",
                    expectedMatcherState =
                        """
                            AnnotationMatcher(
                                android.annotation.Hide -> {
                                    Entry(
                                        result: SurfaceAnnotationData(surface=ApiSurface(core-platform-plus-public), effect=HIDE, recursive=true)
                                    )
                                }
                                libcore.api.CorePlatformApi -> {
                                    Entry(
                                        status=libcore.api.CorePlatformApi.Status.STABLE
                                        result: SurfaceAnnotationData(surface=ApiSurface(core-platform-plus-public), effect=SHOW, recursive=false)
                                    )
                                }
                            )
                        """,
                    expectedShowUnannotated = true,
                    expectedUnannotatedSurfaceName = "core-platform-plus-public",
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "{0}") internal fun params() = params
    }

    override fun createOptions() = ApiSelectionOptions()

    @Test
    fun `Test complex api-surfaces`() {
        runTest(
            ARG_API_SURFACE,
            params.surface,
        ) {
            options
                .compute(apiSurfacesConfig)
                .apiSurfaceSelector
                .assertState(
                    params.expectedMatcherState,
                    params.expectedShowUnannotated,
                    params.expectedUnannotatedSurfaceName,
                )
        }
    }
}
