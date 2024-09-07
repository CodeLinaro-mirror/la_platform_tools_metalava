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
import com.android.tools.metalava.model.noOpAnnotationManager
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.transformer.CodebaseTransformer
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner
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
                annotationManager = noOpAnnotationManager,
                modelOptions = inputs.modelOptions,
            )
        return sourceParser.parseSources(
            sourceSet(inputs.mainSourceDir),
            sourceSet(inputs.commonSourceDir),
            description = "Test Codebase",
            classPath = classPath,
        )
    }

    private fun sourceSet(sourceDir: ModelSuiteRunner.SourceDir?) =
        if (sourceDir == null) SourceSet.empty()
        else SourceSet(sourceDir.createFiles(), listOf(sourceDir.dir))

    override fun toString(): String = sourceModelProvider.providerName
}
