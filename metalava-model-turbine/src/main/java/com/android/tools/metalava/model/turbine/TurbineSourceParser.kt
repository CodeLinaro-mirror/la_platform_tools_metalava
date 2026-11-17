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
import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.api.SelectedApi
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.source.AbstractSourceParser
import java.io.File

internal class TurbineSourceParser(
    private val turbineEnvironment: TurbineEnvironment,
    codebaseConfig: Codebase.Config,
    tracer: Tracer,
) :
    AbstractSourceParser(
        codebaseConfig,
        tracer,
    ) {
    override fun getClassPathResolver(): ClassPathResolver {
        val boundSources =
            turbineEnvironment.classPathBoundSources
                ?: error("Could not create codebase from ${turbineEnvironment.classPath}")
        return createCodebase(
            boundSources = boundSources,
            description = "Codebase from classpath",
            apiPackages = null,
            // A ClassPathResolver is used when resolving references from a signature file, possibly
            // for the previously released API, so it must not have a reference to the previously
            // released API itself.
            codebaseConfig = codebaseConfig.copy(previouslyReleasedCodebaseProvider = { null }),
        )
    }

    override fun processSources(
        description: String,
        apiPackages: PackageFilter?,
        compiledSourceJar: File?,
    ): Codebase? {
        if (turbineEnvironment.projectDescription != null) {
            error("Turbine model does not support --project")
        }
        if (compiledSourceJar != null) {
            error("Turbine model does not support --compiled-jar")
        }

        val boundSources = turbineEnvironment.boundSources ?: return null
        return createCodebase(
            boundSources = boundSources,
            description = description,
            apiPackages = apiPackages,
        )
    }

    override fun processJavaStubs(
        javaStubFiles: List<File>,
        apiPackages: PackageFilter?,
    ): Codebase? {
        val boundSources = turbineEnvironment.bindJavaStubs(javaStubFiles, reporter) ?: return null
        return createCodebase(
            boundSources = boundSources,
            description = "Codebase loaded from stubs",
            apiPackages = apiPackages,
        )
    }

    private fun createCodebase(
        boundSources: BoundSources,
        description: String,
        apiPackages: PackageFilter?,
        codebaseConfig: Codebase.Config = this.codebaseConfig,
    ): DefaultCodebase {
        val rootDir = boundSources.sourceSet.sourcePath.firstOrNull() ?: File("").canonicalFile

        val assembler =
            tracer.trace("turbine.createCodebaseInitialiser") {
                TurbineCodebaseInitialiser(
                    codebaseFactory = { assembler ->
                        DefaultCodebase(
                            location = rootDir,
                            description = description,
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
                )
            }

        // Initialize the codebase.
        tracer.trace("turbine.initialize") {
            assembler.initialize(boundSources, apiPackages, tracer)
        }

        // Return the newly created and initialized codebase.
        return assembler.codebase
    }

    override fun createMultiplatformCodebase(): MultiplatformCodebase {
        error("Turbine model does not support multiplatform codebase creation")
    }
}
