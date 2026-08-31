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
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.SourceOptions
import com.android.tools.metalava.cli.common.executionEnvironment
import com.android.tools.metalava.cli.common.registerPostCommandAction
import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.model.source.EnvironmentManager
import com.android.tools.metalava.model.source.SourceModelProvider
import com.github.ajalt.clikt.core.findOrSetObject
import com.github.ajalt.clikt.parameters.groups.provideDelegate

/**
 * Command which is run before one or more [SingleSurfaceCommand]s in order to set up shared state
 * for processing multiple API surfaces.
 */
class MultiSurfaceCommand :
    MetalavaSubCommand(
        help =
            """
            Command that sets up state to run operations for multiple API surfaces.

            Should be run with one or more `single-surface` subcommands to process the API surfaces.
            """
                .trimIndent(),
        allowMultipleSubcommands = true,
    ) {
    private val configFileOptions by ConfigFileOptions()

    private val sourceOptions: SourceOptions by SourceOptions()

    /** Adds shared state to the context to be reused by [SingleSurfaceCommand]. */
    private val sharedOptions by findOrSetObject { SharedOptions(configFileOptions, sourceOptions) }

    override fun run() {
        stdout.println("The multi-surface command is currently experimental")

        // Initialize the environment manager for the SingleSurfaceCommands.
        sharedOptions.createEnvironmentManager(
            sourceOptions.sourceModelProvider,
            executionEnvironment.disableStderrDumping()
        )

        // Set the environment manager to be closed after all commands have run.
        registerPostCommandAction { sharedOptions.environmentManager.close() }
    }

    /** Holds state created by [MultiSurfaceCommand] which is reused by [SingleSurfaceCommand]. */
    internal class SharedOptions(
        val configFileOptions: ConfigFileOptions,
        val sourceOptions: SourceOptions,
    ) {
        /**
         * [EnvironmentManager] to be used by [SingleSurfaceCommand]s. The [MultiSurfaceCommand]
         * must call [createEnvironmentManager] to initialize it.
         */
        lateinit var environmentManager: EnvironmentManager

        /** Initializes [environmentManager]. */
        fun createEnvironmentManager(
            sourceModelProvider: SourceModelProvider,
            disableStderrDumping: Boolean
        ) {
            environmentManager = sourceModelProvider.createEnvironmentManager(disableStderrDumping)
        }
    }
}
