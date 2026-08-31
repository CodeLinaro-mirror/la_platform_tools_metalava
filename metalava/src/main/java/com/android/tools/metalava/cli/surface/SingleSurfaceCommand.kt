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

package com.android.tools.metalava.cli.surface

import com.android.tools.metalava.ConfigFileOptions
import com.android.tools.metalava.cli.common.CommonOptions
import com.android.tools.metalava.cli.common.DriverCommand
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.common.SourceOptions
import com.android.tools.metalava.cli.common.stdout
import com.github.ajalt.clikt.core.requireObject
import java.io.File

/** Subcommand of [MultiSurfaceCommand] which runs operations on a specific API surface. */
class SingleSurfaceCommand(
    commonOptions: CommonOptions,
    executionEnvironment: ExecutionEnvironment,
) :
    DriverCommand(
        commonOptions = commonOptions,
        executionEnvironment = executionEnvironment,
        help = "Performs operations on a single API surface.",
    ) {
    /** Options created by [MultiSurfaceCommand]. */
    private val sharedOptions by requireObject<MultiSurfaceCommand.SharedOptions>()

    // Pull options from the MultiSurfaceCommand
    override val sourceOptionsProvider: () -> SourceOptions
        get() = { sharedOptions.sourceOptions }

    override val configFileOptionsProvider: () -> ConfigFileOptions
        get() = { sharedOptions.configFileOptions }

    override fun getDefaultBaselineFile(): File? = null

    override fun run() {
        stdout.println("The single-surface command is currently experimental")
        // Run Driver with the environment manager created by the MultiSurfaceCommand
        runAndReportIssues(sharedOptions.environmentManager)
    }
}
