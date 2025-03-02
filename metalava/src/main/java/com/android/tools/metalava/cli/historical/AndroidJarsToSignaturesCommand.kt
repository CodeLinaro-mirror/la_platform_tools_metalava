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

package com.android.tools.metalava.cli.historical

import com.android.tools.metalava.ARG_CONFIG_FILE
import com.android.tools.metalava.ConfigFileOptions
import com.android.tools.metalava.OptionsDelegate
import com.android.tools.metalava.StandaloneJarCodebaseLoader
import com.android.tools.metalava.apiSurfacesFromConfig
import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.cliError
import com.android.tools.metalava.cli.common.executionEnvironment
import com.android.tools.metalava.cli.common.existingDir
import com.android.tools.metalava.cli.common.map
import com.android.tools.metalava.cli.common.progressTracker
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.cli.signature.SignatureFormatOptions
import com.android.tools.metalava.reporter.BasicReporter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split

private const val ARG_ANDROID_ROOT_DIR = "<android-root-dir>"

private const val ARG_API_SURFACES = "--api-surfaces"

class AndroidJarsToSignaturesCommand :
    MetalavaSubCommand(
        help =
            """
    Rewrite the signature files in the `prebuilts/sdk` directory in the Android source tree.

    It does this by reading the API defined in the corresponding `android.jar` files.
"""
                .trimIndent(),
    ) {

    private val androidRootDir by
        argument(
                ARG_ANDROID_ROOT_DIR,
                help =
                    """
                        The root directory of the Android source tree. The new signature files will
                        be generated in the `prebuilts/sdk/<api>/<surface>/api/android.txt`
                        sub-directories.
                    """
                        .trimIndent()
            )
            .existingDir()
            .validate {
                require(it.resolve("prebuilts/sdk").isDirectory) {
                    cliError("$ARG_ANDROID_ROOT_DIR does not point to an Android source tree")
                }
            }

    private val configFileOptions by ConfigFileOptions()

    /** Add options for controlling the format of the generated files. */
    private val signatureFormat by SignatureFormatOptions()

    /** Optional set of [ApiVersion]s to convert. */
    private val apiVersions by
        option(
                help =
                    """
                        Comma separated list of api versions to convert. If unspecified then all
                        versions will be converted.
                    """
                        .trimIndent(),
                metavar = "<api-version-list>",
            )
            .split(",")
            .map { list -> list?.map { ApiVersion.fromString(it) }?.toSet() }

    private val apiSurfaceNames by
        option(
                ARG_API_SURFACES,
                help =
                    """
                        Comma separated list of api surfaces to convert. If unspecified then only
                        `public` will be converted.
                    """
                        .trimIndent(),
                metavar = "<api-surface-list>",
            )
            .split(",")
            .map { list -> list?.distinct() ?: listOf("public") }

    override fun run() {
        // Make sure that none of the code called by this command accesses the global `options`
        // property.
        OptionsDelegate.disallowAccess()

        // Create a self-consistent set of ApiSurfaces that either need to be converted or
        // contribute to a surface that needs to be converted.
        val apiSurfaces =
            configFileOptions.config.apiSurfaces?.let { apiSurfacesConfig ->

                // Collect the transitive closure of the configured ApiSurfaceConfigs for the
                // required
                // API surfaces.
                val surfaceConfigs = buildSet {
                    for (name in apiSurfaceNames) {
                        val surfaceConfig =
                            apiSurfacesConfig.getByNameOrError(name) {
                                "$ARG_API_SURFACES (`$it`) does not match an <api-surface> in a --config-file"
                            }
                        addAll(apiSurfacesConfig.contributesTo(surfaceConfig))
                    }
                }

                apiSurfacesFromConfig(surfaceConfigs, apiSurfaceNames.last())
            }
                ?: error(
                    "$ARG_CONFIG_FILE is either not specified or does not define any API surfaces"
                )

        // Get the selected ApiSurface(s). The lookup is guaranteed to not be null because the
        // name was checked above. Sort, so any extended ApiSurface comes before an extended
        // ApiSurface.
        val selectedApiSurfaces = apiSurfaceNames.map { apiSurfaces.byName[it]!! }.sorted()

        StandaloneJarCodebaseLoader.create(
                executionEnvironment,
                progressTracker,
                BasicReporter(stderr),
            )
            .use { jarCodebaseLoader ->
                ConvertJarsToSignatureFiles(
                        stderr,
                        stdout,
                        progressTracker,
                        signatureFormat.fileFormat,
                        apiVersions,
                        apiSurfaces,
                        selectedApiSurfaces,
                        jarCodebaseLoader,
                        androidRootDir,
                    )
                    .convertJars()
            }
    }
}
