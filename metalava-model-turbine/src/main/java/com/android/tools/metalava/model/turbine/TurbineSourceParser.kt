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

import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.source.SourceSet
import com.google.turbine.diag.TurbineError
import java.io.File
import java.nio.file.Files
import kotlin.io.writeText

internal class TurbineSourceParser(
    private val codebaseConfig: Codebase.Config,
) : SourceParser {

    /**
     * A [SourceSet] that contains a fake `java.lang.Object` class.
     *
     * Needed to work around a limitation in Turbine where it requires a java.lang.Object to be
     * provided.
     */
    private val fakeJavaLangObject by lazy {
        val dir = Files.createTempDirectory("metalava-model-turbine").toFile()
        val file =
            dir.resolve("java/lang/Object.java").apply {
                parentFile.mkdirs()
                writeText(
                    """
                package java.lang;
                public class Object {}
            """
                        .trimIndent()
                )
            }

        SourceSet(listOf(file), emptyList())
    }

    override fun getClassPathResolver(classPath: List<File>): ClassPathResolver {
        return try {
            // First try the default implementation.
            super.getClassPathResolver(classPath)
        } catch (e: IllegalArgumentException) {
            // If it failed for some unexpected reason then rethrow the exception.
            if (e.message != "Could not find java.lang on bootclasspath") {
                throw e
            }

            // Otherwise, try again with a fake java.lang.Object class.
            parseSources(
                sourceSet = fakeJavaLangObject,
                description = "Codebase from classpath",
                classPath = classPath,
            ) ?: error("Could not create resolver from $classPath")
        }
    }

    /**
     * Returns a codebase initialized from the given Java source files, with the given description.
     */
    override fun parseSources(
        sourceSet: SourceSet,
        description: String,
        classPath: List<File>,
        apiPackages: PackageFilter?,
        projectDescription: File?,
        compiledSourceJar: File?,
    ): Codebase? {
        if (projectDescription != null) {
            error("Turbine model does not support --project")
        }
        if (compiledSourceJar != null) {
            error("Turbine model does not support --compiled-jar")
        }

        val sourceSetWithExtractedRoots = sourceSet.extractRoots(codebaseConfig.reporter)

        val rootDir = sourceSetWithExtractedRoots.sourcePath.firstOrNull() ?: File("").canonicalFile

        val assembler =
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
                    )
                },
                classpath = classPath,
            )

        try {
            // Initialize the codebase.
            assembler.initialize(sourceSetWithExtractedRoots, apiPackages)
        } catch (_: TurbineError) {
            // Processing was aborted so the `codebase` is not valid so return `null`.
            return null
        }

        // Return the newly created and initialized codebase.
        return assembler.codebase
    }

    override fun loadFromJar(apiJar: File, classPath: List<File>): Codebase {
        TODO("b/299044569 handle this")
    }

    override fun createMultiplatformCodebase(projectDescription: File): MultiplatformCodebase {
        error("Turbine model does not support multiplatform codebase creation")
    }
}

private val NULL =
    object : ClassPathResolver {
        override fun resolveClass(erasedName: String) = null

        override fun resolvePackage(pkgName: String) = null
    }
