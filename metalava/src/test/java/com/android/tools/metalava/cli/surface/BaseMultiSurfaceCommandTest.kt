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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.cli.common.CommonOptions
import com.android.tools.metalava.cli.util.SingleSurfaceOptions
import com.android.tools.metalava.cli.util.configFileOptions
import com.android.tools.metalava.cli.util.testSources
import com.android.tools.metalava.cli.util.tracingOptions
import com.github.ajalt.clikt.core.subcommands
import kotlin.collections.addAll

/**
 * Tests for [MultiSurfaceCommand] and [SingleSurfaceCommand] (which is run in the context of a
 * [MultiSurfaceCommand]).
 */
abstract class BaseMultiSurfaceCommandTest :
    BaseCommandTest<MultiSurfaceCommand>({
        // `single-surface` can only be run as a subcommand of `multi-surface`
        MultiSurfaceCommand()
            .subcommands(
                SingleSurfaceCommand(commonOptions = CommonOptions(), executionEnvironment = it)
            )
    }) {

    fun runTest(
        sourceFiles: Array<TestFile>,
        expectedOutput: String,
        vararg surfaceExpectations: SingleSurfaceOptions,
        enableTracing: Boolean = false,
    ) {
        val surfaces = surfaceExpectations.map { it.apiSurface }
        // Aggregate all extra source files needed for the surfaces
        val extraSourceFiles = surfaces.flatMap { it.additionalSourceFiles }.toSet()
        val sourceOptions =
            testSources(
                sourceFiles = sourceFiles + extraSourceFiles,
                additionalSourcePathFiles = emptyArray(),
                projectDescription = null,
                compiledSourceJar = null,
                classpath = null,
                signatureSources = emptyArray(),
                skipSourceArgs = false,
            )

        val tracingOptions = tracingOptions(enableTracing)

        val multiSurfaceArgs = buildList {
            addAll(tracingOptions.args)

            add("multi-surface")
            addAll(sourceOptions.args)

            // Aggregate all config files required by the surfaces
            val configFiles = surfaces.map { it.configFile }.toSet().toTypedArray()
            addAll(configFileOptions(*configFiles))

            for (surface in surfaceExpectations) {
                addAll(surface.args)
            }
        }

        commandTest {
            args += multiSurfaceArgs
            expectedStdout = expectedOutput
        }
        tracingOptions.check()
        for (surface in surfaceExpectations) {
            surface.check()
        }
    }
}
