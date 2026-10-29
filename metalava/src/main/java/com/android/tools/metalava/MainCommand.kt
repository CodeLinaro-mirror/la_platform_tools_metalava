/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.metalava.cli.common.ARG_SOURCE_FILES
import com.android.tools.metalava.cli.common.CommonOptions
import com.android.tools.metalava.cli.common.DriverCommand
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.common.SourceOptions
import com.android.tools.metalava.cli.common.executionEnvironment
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.reporter.DEFAULT_BASELINE_NAME
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import java.io.File

/**
 * A command that is passed to [com.android.tools.metalava.cli.common.MetalavaCommand] when the main
 * metalava functionality needs to be run when no subcommand is provided.
 */
class MainCommand(
    commonOptions: CommonOptions,
    executionEnvironment: ExecutionEnvironment,
) :
    DriverCommand(
        commonOptions,
        executionEnvironment,
        help = "The default sub-command that is run if no sub-command is specified.",
    ) {

    /** Property into which all the arguments (and unknown options) are gathered. */
    private val additionalSourceFiles by
        argument(
                name = "source-files",
                help = "Additional source files to append to $ARG_SOURCE_FILES",
            )
            .existingFile()
            .multiple()

    private val configFileOptions by ConfigFileOptions()

    override val configFileOptionsProvider: () -> ConfigFileOptions
        get() = { configFileOptions }

    internal val sourceOptions: SourceOptions by
        SourceOptions(
            executionEnvironment = executionEnvironment,
            additionalSourceFilesProvider = { additionalSourceFiles },
        )

    override val sourceOptionsProvider: () -> SourceOptions
        get() = { sourceOptions }

    override fun run() {
        // Perform any necessary initialization.
        initializeOptionGroups()

        val sourceModelProvider =
            // Use the [SourceModelProvider] specified by the [TestEnvironment], if any.
            executionEnvironment.testEnvironment?.sourceModelProvider
                // Otherwise, use the one specified on the command line, or the default.
                ?: sourceOptions.sourceModelProvider

        sourceModelProvider
            .createEnvironmentManager(executionEnvironment.disableStderrDumping())
            .use { environmentManager -> runAndReportIssues(environmentManager) }
    }

    /** Initialize any option groups that require it. */
    private fun initializeOptionGroups() {
        // Make sure that any config files are processed.
        configFileOptions.config
    }

    /**
     * Produce a default file name for the baseline. It's normally "baseline.txt", but can be
     * prefixed by show annotations; e.g. @TestApi -> test-baseline.txt, @SystemApi ->
     * system-baseline.txt, etc.
     *
     * Note because the default baseline file is not explicitly set in the command line, this file
     * would trigger a --strict-input-files violation. To avoid that, always explicitly pass a
     * baseline file.
     */
    override fun getDefaultBaselineFile(): File? {
        val sourcePath = sourceOptions.sourcePath
        if (sourcePath.isNotEmpty() && sourcePath[0].path.isNotBlank()) {
            // Create the file name.
            val fileName = buildString {
                // Prefix with the API surface name, if provided.
                apiSelectionOptions.apiSurfaceName?.let { apiSurfaceName ->
                    append(apiSurfaceName)
                    append("-")
                }
                append(DEFAULT_BASELINE_NAME)
            }

            var base = sourcePath[0]
            // Convention: in AOSP, signature files are often in sourcepath/api: let's place
            // baseline files there too
            val api = File(base, "api")
            if (api.isDirectory) {
                base = api
            }
            return File(base, fileName)
        } else {
            return null
        }
    }
}
