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

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.transformer.CodebaseTransformer
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner.SourceDir
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner.TestConfiguration
import com.android.tools.metalava.reporter.BasicReporter
import com.android.tools.metalava.testing.getAndroidJar
import com.android.tools.metalava.testing.getKotlinStdlibPaths
import java.io.File
import java.io.PrintWriter

/** A [ModelSuiteRunner] that is implemented using a [SourceModelProvider]. */
class SourceModelSuiteRunner(private val sourceModelProvider: SourceModelProvider) :
    ModelSuiteRunner {

    override val providerName = sourceModelProvider.providerName

    override val supportedInputFormats = sourceModelProvider.supportedInputFormats

    override val capabilities: Set<Capability> = sourceModelProvider.capabilities

    override val testConfigurations: List<TestConfiguration> =
        supportedInputFormats.flatMap { inputFormat ->
            sourceModelProvider.modelOptionsList.map { modelOptions ->
                TestConfiguration(inputFormat, modelOptions)
            }
        }

    override fun createCodebaseAndRun(
        inputs: ModelSuiteRunner.TestInputs,
        test: (Codebase) -> Unit
    ) {
        sourceModelProvider.createEnvironmentManager(forTesting = true).use { environmentManager ->
            val classPath = buildList {
                add(getAndroidJar())
                if (inputs.inputFormat == InputFormat.KOTLIN) {
                    addAll(getKotlinStdlibPaths())
                }
            }
            val codebase =
                createTestCodebase(
                    environmentManager,
                    inputs,
                    classPath,
                )

            // If available, transform the codebase for testing, otherwise use the one provided.
            val transformedCodebase = CodebaseTransformer.transformIfAvailable(codebase)

            test(transformedCodebase)
        }
    }

    private fun createTestCodebase(
        environmentManager: EnvironmentManager,
        inputs: ModelSuiteRunner.TestInputs,
        classPath: List<File>,
    ): Codebase {
        val reporter = BasicReporter(PrintWriter(System.err))
        val sourceParser =
            environmentManager.createSourceParser(
                reporter = reporter,
                annotationManager = inputs.annotationManager,
                modelOptions = inputs.modelOptions,
            )
        return sourceParser.parseSources(
            sourceSet(inputs.mainSourceDir, inputs.additionalMainSourceDir),
            sourceSet(inputs.commonSourceDir),
            description = "Test Codebase",
            classPath = classPath,
            apiPackages = null,
        )
    }

    /**
     * Create a [SourceSet] from some [SourceDir] instances.
     *
     * @param sourceDir if supplied the files created from this will be added to the
     *   [SourceSet.sources] list and its directory will be added to the [SourceSet.sourcePath]
     *   list.
     * @param sourcePathDir if supplied the root directories in which its files are created will be
     *   added to the [SourceSet.sourcePath] but the files themselves will not be added to the
     *   [SourceSet.sources] list.
     */
    private fun sourceSet(sourceDir: SourceDir?, sourcePathDir: SourceDir? = null) =
        if (sourceDir == null && sourcePathDir == null) SourceSet.empty()
        else {
            val sources = mutableListOf<File>()

            // Create a set that will dedup the directories but maintain the order in which they
            // were added.
            val sourcePath = mutableSetOf<File>()
            if (sourceDir != null) {
                // Create the files and add them to the sources and the containing directory to the
                // source path.
                sources.addAll(sourceDir.createFiles())
                sourcePath.add(sourceDir.dir)
            }
            if (sourcePathDir != null) {
                // Create the files but do not add them to the sources, instead just add the
                // directory in which the files were created to the source path.
                val dir = sourcePathDir.dir
                for (testFile in sourcePathDir.contents) {
                    testFile.createFile(dir)
                    // Get the root directory in which the test file was created and add that to the
                    // source path.
                    val rootDir = testFile.targetRootFolder?.let { dir.resolve(it) } ?: dir
                    sourcePath.add(rootDir)
                }
                sourcePath.add(sourcePathDir.dir.resolve("src"))
            }

            SourceSet(sources, sourcePath.toList())
        }

    override fun toString(): String = sourceModelProvider.providerName
}
