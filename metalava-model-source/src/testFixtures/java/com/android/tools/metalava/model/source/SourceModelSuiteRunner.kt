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
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.FilterableCodebaseCreator
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.transformer.CodebaseTransformer
import com.android.tools.metalava.model.testsuite.JarSupport
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner.SourceDir
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner.TestConfiguration
import com.android.tools.metalava.testing.getAndroidJar
import com.android.tools.metalava.testing.getKotlinStdlibPaths
import com.android.tools.metalava.testing.getNoopTracer
import java.io.File

/** A [ModelSuiteRunner] that is implemented using a [SourceModelProvider]. */
class SourceModelSuiteRunner(private val sourceModelProvider: SourceModelProvider) :
    ModelSuiteRunner,
    // Delegate implementation to [sourceModelProvider].
    FilterableCodebaseCreator by sourceModelProvider {

    override val testConfigurations: List<TestConfiguration> =
        supportedInputFormats.flatMap { inputFormat ->
            sourceModelProvider.modelOptionsList.map { modelOptions ->
                TestConfiguration(inputFormat, modelOptions)
            }
        }

    override fun createCodebaseAndRun(
        inputs: ModelSuiteRunner.TestInputs,
        test: (Codebase?) -> Unit
    ) {
        // Skip tests that require using compiled sources if the provider does not support it
        if (
            inputs.compiledSourceJar != null &&
                !sourceModelProvider.capabilities.contains(Capability.JAR_WITH_SOURCES)
        )
            return

        sourceModelProvider.createEnvironmentManager(forTesting = true).use { environmentManager ->
            val classPath = buildList {
                add(getAndroidJar())
                if (inputs.inputFormat == InputFormat.KOTLIN) {
                    addAll(getKotlinStdlibPaths())
                }
                addAll(inputs.testFixture.additionalClassPath)
            }
            val codebase =
                createTestCodebase(
                    environmentManager,
                    inputs,
                    classPath,
                )

            // If available, transform the codebase for testing, otherwise use the one provided.
            val transformedCodebase = codebase?.let { CodebaseTransformer.transformIfAvailable(it) }

            test(transformedCodebase)
        }
    }

    override fun createMultiplatformCodebaseAndRun(
        inputs: ModelSuiteRunner.TestInputs,
        test: (MultiplatformCodebase?) -> Unit
    ) {
        if (Capability.MULTIPLATFORM !in capabilities) return
        return inputs.projectDescription?.let { projectDescription ->
            // Make sure that the input files have been created.
            sourceSet(inputs.mainSourceDir, inputs.additionalMainSourceDir)

            // Create an EnvironmentManager and run the tests within it, closing it when finished.
            sourceModelProvider.createEnvironmentManager(forTesting = true).use { environmentManager
                ->
                val testFixture = inputs.testFixture
                val sourceParser =
                    environmentManager.createSourceParser(
                        codebaseConfig = testFixture.codebaseConfig,
                        javaLanguageLevel = testFixture.javaLanguageLevel,
                        modelOptions = inputs.modelOptions,
                        tracer = getNoopTracer()
                    )

                val codebase = sourceParser.createMultiplatformCodebase(projectDescription)
                test(codebase)
            }
        } ?: error("Project description file is required to create multiplatform codebase.")
    }

    private fun createTestCodebase(
        environmentManager: EnvironmentManager,
        inputs: ModelSuiteRunner.TestInputs,
        classPath: List<File>,
    ): Codebase? {
        val testFixture = inputs.testFixture
        val sourceParser =
            environmentManager.createSourceParser(
                codebaseConfig = testFixture.codebaseConfig,
                javaLanguageLevel = testFixture.javaLanguageLevel,
                modelOptions = inputs.modelOptions,
                tracer = getNoopTracer()
            )

        val inputs =
            SourceParser.Inputs(
                sourceSet(inputs.mainSourceDir, inputs.additionalMainSourceDir),
                description = "Test Codebase",
                classPath = classPath,
                apiPackages = testFixture.apiPackages,
                projectDescription = inputs.projectDescription,
                compiledSourceJar = inputs.compiledSourceJar?.createFile(inputs.mainSourceDir.dir),
            )

        return sourceParser.parseSources(inputs)
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
            // Create the files from which the Codebase will be created and add them to the sources.
            val sources = sourceDir?.createFiles() ?: emptyList()

            // Create additional files that will be on the source path and which can be referenced
            // from the other source files but will not otherwise be part of the Codebase.
            val sourcePath =
                sourcePathDir?.let { additionalSourceDir ->
                    additionalSourceDir.createFiles()
                    listOf(additionalSourceDir.dir)
                } ?: emptyList()

            SourceSet(sources, sourcePath)
        }

    override fun createJarSupportAndRun(test: (JarSupport) -> Unit) {
        sourceModelProvider.createEnvironmentManager(forTesting = true).use { environmentManager ->
            val sourceParser =
                environmentManager.createSourceParser(
                    codebaseConfig = Codebase.Config(),
                    tracer = getNoopTracer()
                )

            val jarSupport = SourceParserJarSupport(sourceParser)
            test(jarSupport)
        }
    }

    override fun toString(): String = sourceModelProvider.providerName
}

/** A [JarSupport] implementation that delegates to [sourceParser]. */
private class SourceParserJarSupport(private val sourceParser: SourceParser) : JarSupport {
    override fun getClassPathResolver(classPath: List<File>) =
        sourceParser.getClassPathResolver(classPath)

    override fun loadFromJar(apiJar: File, classPath: List<File>) =
        sourceParser.loadFromJar(apiJar, classPath)
}
