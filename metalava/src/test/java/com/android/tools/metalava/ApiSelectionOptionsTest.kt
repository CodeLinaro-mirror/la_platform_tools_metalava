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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.BaseOptionGroupTest
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.config.ApiSurfaceConfig
import com.android.tools.metalava.config.ApiSurfacesConfig
import com.android.tools.metalava.model.ANDROID_SYSTEM_API
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

val API_SELECTION_OPTIONS_HELP =
    """
Api Selection:

  Options that select which parts of the source files will be part of the generated API.

  --api-surface <surface>                    The API surface currently being generated. Must correspond to an
                                             <api-surface> element in a --config-file.
  --show-unannotated                         Include un-annotated public APIs in the signature file as well. (default:
                                             true if no --show*-annotation options specified)
  --show-annotation <annotation-filter>      Unhide any hidden elements that are also annotated with the given
                                             annotation.
  --show-single-annotation <annotation-filter>
                                             Like --show-annotation, but does not apply to members; these must also be
                                             explicitly annotated.
  --show-for-stub-purposes-annotation <annotation-filter>
                                             Like --show-annotation, but elements annotated with it are assumed to be
                                             "implicitly" included in the API surface, and they'll be included in
                                             certain kinds of output such as stubs, but not in others, such as the
                                             signature file and API lint.
  --hide-annotation <annotation-filter>      Treat any elements annotated with the given annotation as hidden.
    """
        .trimIndent()

class ApiSelectionOptionsTest :
    BaseOptionGroupTest<ApiSelectionOptions>(API_SELECTION_OPTIONS_HELP) {
    override fun createOptions() = ApiSelectionOptions()

    @Test
    fun `Test no --show-unannotated no show annotations`() {
        runTest { assertThat(options.showUnannotated).isTrue() }
    }

    @Test
    fun `Test no --show-unannotated with --show-annotation`() {
        runTest(ARG_SHOW_ANNOTATION, "test.pkg.Show") {
            assertThat(options.showUnannotated).isFalse()
        }
    }

    /**
     * Run the test, providing an optional [ApiSelectionOptions] to
     * [ApiSelectionOptions.apiSurfacesConfigProvider].
     */
    private fun runTestWithConfig(
        vararg args: String,
        apiSurfacesConfig: ApiSurfacesConfig? =
            ApiSurfacesConfig(
                apiSurfaceList =
                    listOf(
                        ApiSurfaceConfig(name = "public"),
                        ApiSurfaceConfig(name = "system", extends = "public"),
                    )
            ),
        test: Result<ApiSelectionOptions>.() -> Unit,
    ) {
        val optionGroup =
            ApiSelectionOptions(
                apiSurfacesConfigProvider = { apiSurfacesConfig },
            )
        runTest(args = args, optionGroup = optionGroup, test = test)
    }

    /**
     * Run [body] and make sure that it throws a [MetalavaCliException] with the [expectedMessage].
     */
    private fun assertThrowsCliError(expectedMessage: String, body: () -> Unit) {
        val exception = assertThrows(MetalavaCliException::class.java) { body() }
        assertThat(exception.message).isEqualTo(expectedMessage)
    }

    @Test
    fun `Test --api-surface option no api-surfaces configuration`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "public",
            apiSurfacesConfig = null,
        ) {
            assertThrowsCliError(
                "--api-surface requires at least one <api-surface> to have been configured in a --config-file"
            ) {
                options.apiSurfaces
            }
        }
    }

    @Test
    fun `Test configuring API surfaces no --api-surface option`() {
        runTestWithConfig {
            // Configuration is ignored when no --api-surface is provided.
            options.apiSurfaces.assertBaseWasNotCreated()
        }
    }

    @Test
    fun `Test configuring API surfaces invalid --api-surface option`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "unknown",
        ) {
            assertThrowsCliError(
                "--api-surface (`unknown`) does not match an <api-surface> in a --config-file, expected one of `public`, `system`"
            ) {
                options.apiSurfaces
            }
        }
    }

    @Test
    fun `Test configuring extending surface without --show-annotation option`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "system",
        ) {
            assertThrowsCliError(
                """Configuration of `<api-surface name="system">` is inconsistent with command line options because `system` extends public which requires that it not show unannotated items but --show-unannotated is true"""
            ) {
                options.apiSurfaces
            }
        }
    }

    @Test
    fun `Test configuring extending surface with --show-annotation option`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "system",
            ARG_SHOW_ANNOTATION,
            ANDROID_SYSTEM_API,
        ) {
            options.apiSurfaces.assertBaseWasCreated()
        }
    }

    @Test
    fun `Test configuring non-extending surface with --show-annotation option`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "public",
            ARG_SHOW_ANNOTATION,
            ANDROID_SYSTEM_API,
        ) {
            assertThrowsCliError(
                """Configuration of `<api-surface name="public">` is inconsistent with command line options because `public` does not extend another surface which requires that it show unannotated items but --show-unannotated is false"""
            ) {
                options.apiSurfaces
            }
        }
    }

    @Test
    fun `Test configuring non-extending surface without --show-annotation option`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "public",
        ) {
            options.apiSurfaces.assertBaseWasNotCreated()
        }
    }
}
