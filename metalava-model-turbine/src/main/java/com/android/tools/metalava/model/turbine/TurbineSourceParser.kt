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

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.source.utils.findPackage
import com.android.tools.metalava.reporter.Reporter
import com.google.turbine.diag.SourceFile
import com.google.turbine.parse.Parser
import java.io.File

internal class TurbineSourceParser(
    private val reporter: Reporter,
    private val annotationManager: AnnotationManager,
    private val allowReadingComments: Boolean
) : SourceParser {

    override fun getClassResolver(classPath: List<File>): ClassResolver {
        TODO("implement it")
    }

    /**
     * Returns a codebase initialized from the given Java source files, with the given description.
     */
    override fun parseSources(
        sourceSet: SourceSet,
        commonSourceSet: SourceSet,
        description: String,
        classPath: List<File>,
    ): Codebase {
        val rootDir = sourceSet.sourcePath.firstOrNull() ?: File("").canonicalFile

        val sources = sourceSet.sources

        // Scan the files looking for package.html files and return a map from name to file just in
        // case they are needed to create packages.
        val packageHtmlByPackageName = findPackageHtmlFileByPackageName(sources)

        val sourceFiles = getSourceFiles(sources)
        val units = sourceFiles.map { Parser.parse(it) }

        // Create the Codebase. The initialization of the codebase has to done after the creation of
        // the codebase and not during, i.e. in the lambda, because the codebase will not be fully
        // initialized when it is called.
        val codebase =
            TurbineBasedCodebase(rootDir, description, annotationManager, reporter) { codebase ->
                TurbineCodebaseInitialiser(
                    units,
                    codebase as TurbineBasedCodebase,
                    classPath,
                    allowReadingComments,
                )
            }

        // Initialize the codebase.
        (codebase.assembler as TurbineCodebaseInitialiser).initialize(packageHtmlByPackageName)

        // Return the newly created and initialized codebase.
        return codebase
    }

    private fun getSourceFiles(sources: List<File>): List<SourceFile> {
        return sources
            .filter { it.isFile && it.extension == "java" } // Ensure only Java files are included
            .map { SourceFile(it.path, it.readText()) }
    }

    override fun loadFromJar(apiJar: File): Codebase {
        TODO("b/299044569 handle this")
    }

    /**
     * Finds `package.html` files in the source and returns a mapping from the package name,
     * obtained from the file path, to the file.
     */
    private fun findPackageHtmlFileByPackageName(files: List<File>): Map<String, File> {
        return files
            .filter { it.isFile && it.name == "package.html" }
            .associateBy({ findPackageName(it) }) { it }
    }

    /**
     * Attempts to find the package name by looking for any Java class files in the same directory,
     * if unsuccessful, it will guess based on the directory structure.
     */
    private fun findPackageName(file: File): String {
        // First try to find a package name using the utility method which might analyze the java
        // file
        file.parentFile
            .listFiles()
            ?.filter { it.isFile && it.extension == "java" }
            ?.forEach { javaFile ->
                findPackage(javaFile)?.let {
                    return it
                }
            }

        // If no class file with package declaration was found, deduce from the directory structure
        return file.parentFile.absolutePath
            .split(File.separatorChar)
            .dropWhile { it != "java" }
            .drop(1)
            .joinToString(".")
    }
}
