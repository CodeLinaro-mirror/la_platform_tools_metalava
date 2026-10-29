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

package com.android.tools.metalava.model.turbine

import androidx.tracing.Tracer
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.api.SelectedApi
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.source.AbstractSourceParser
import com.android.tools.metalava.model.source.SourceParser
import com.google.turbine.binder.ClassPathBinder
import com.google.turbine.binder.JimageClassBinder
import com.google.turbine.diag.TurbineError
import java.io.File

internal class TurbineSourceParser(
    private val codebaseConfig: Codebase.Config,
    private val jdkHome: File?,
    private val tracer: Tracer,
) : AbstractSourceParser(codebaseConfig.reporter) {
    /**
     * Returns a codebase initialized from the given Java source files, with the given description.
     */
    override fun processInputs(inputs: SourceParser.Inputs): Codebase? {
        if (inputs.projectDescription != null) {
            error("Turbine model does not support --project")
        }
        if (inputs.compiledSourceJar != null) {
            error("Turbine model does not support --compiled-jar")
        }

        val classpath =
            tracer.trace("turbine.bindClasspath") {
                ClassPathBinder.bindClasspath(inputs.classPath.map { it.toPath() })
            }
        val bootclasspath =
            tracer.trace("turbine.bindBootclasspath") {
                jdkHome?.let { home -> JimageClassBinder.bind(home.path) }
                    ?: ClassPathBinder.bindClasspath(listOf())
            }

        val sourceSet = inputs.sourceSet

        val rootDir = sourceSet.sourcePath.firstOrNull() ?: File("").canonicalFile

        val assembler =
            tracer.trace("turbine.createCodebaseInitialiser") {
                TurbineCodebaseInitialiser(
                    codebaseFactory = { assembler ->
                        DefaultCodebase(
                            location = rootDir,
                            description = inputs.description,
                            preFiltered = false,
                            config = codebaseConfig,
                            trustedApi = false,
                            supportsDocumentation = true,
                            assembler = assembler,

                            // Create a [SelectedApi] instance that will be initialized lazily from
                            // the source.
                            selectedApiFactory = SelectedApi.sourceFactory(codebaseConfig),
                        )
                    },
                    bootclasspath = bootclasspath,
                    classpath = classpath,
                )
            }

        try {
            // Initialize the codebase.
            tracer.trace("turbine.initialize") {
                assembler.initialize(sourceSet, inputs.apiPackages, tracer)
            }
        } catch (_: TurbineError) {
            // Processing was aborted so the `codebase` is not valid so return `null`.
            return null
        }

        // Return the newly created and initialized codebase.
        return assembler.codebase
    }

    override fun createMultiplatformCodebase(projectDescription: File): MultiplatformCodebase {
        error("Turbine model does not support multiplatform codebase creation")
    }
}
