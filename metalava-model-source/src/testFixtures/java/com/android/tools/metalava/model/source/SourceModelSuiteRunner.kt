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

package com.android.tools.metalava.model.source

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.noOpAnnotationManager
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner
import com.android.tools.metalava.reporter.BasicReporter
import java.io.File
import java.io.PrintWriter

/**
 * A [ModelSuiteRunner] that is implemented using a [SourceModelProvider].
 *
 * This expects to be loaded on a class path that contains a single [SourceModelProvider] service
 * (retrievable via [SourceModelProvider.getImplementation]).
 */
// @AutoService(ModelSuiteRunner.class)
class SourceModelSuiteRunner : ModelSuiteRunner {

    /** Get the [SourceModelProvider] implementation that is available. */
    private val sourceModelProvider = SourceModelProvider.getImplementation({ true }, "of any type")

    override fun createCodebaseAndRun(
        tempDir: File,
        signature: String,
        source: TestFile,
        test: (Codebase) -> Unit
    ) {
        sourceModelProvider.createEnvironmentManager(forTesting = true).use { environmentManager ->
            val codebase =
                createTestCodebase(
                    environmentManager,
                    tempDir,
                    listOf(source),
                    emptyList(),
                )
            test(codebase)
        }
    }

    private fun createTestCodebase(
        environmentManager: EnvironmentManager,
        directory: File,
        sources: List<TestFile>,
        classPath: List<File>,
    ): Codebase {
        val reporter = BasicReporter(PrintWriter(System.err))
        return environmentManager
            .createSourceParser(reporter, noOpAnnotationManager)
            .parseSources(
                sources = sources.map { it.createFile(directory) },
                description = "Test Codebase",
                sourcePath = listOf(directory),
                classPath = classPath,
            )
    }

    override fun toString(): String = sourceModelProvider.providerName
}
