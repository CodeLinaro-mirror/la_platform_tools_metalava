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

package com.android.tools.metalava.cli.util

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.ARG_CONFIG_FILE
import com.android.tools.metalava.cli.common.ARG_CLASS_PATH
import com.android.tools.metalava.cli.common.ARG_COMPILED_SOURCES
import com.android.tools.metalava.cli.common.ARG_PROJECT
import com.android.tools.metalava.cli.common.ARG_SOURCE_FILES
import com.android.tools.metalava.cli.common.ARG_SOURCE_PATH
import com.android.tools.metalava.model.source.utils.DOT_KT
import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.android.tools.metalava.testing.createFiles
import com.android.tools.metalava.testing.getAndroidJar
import com.android.tools.metalava.testing.getKotlinStdlibPaths
import com.android.tools.metalava.writeSignatureText
import java.io.File

/** Returns the CLI args needed to specify the input [configFiles]. */
fun TemporaryFolderOwner.configFileOptions(vararg configFiles: TestFile?): Array<String> {
    if (configFiles.none { it != null }) return emptyArray()
    val configFolder = getOrCreateFolder("config")
    return buildList {
            for (configFile in configFiles) {
                if (configFile != null) {
                    add(ARG_CONFIG_FILE)
                    add(configFile.indented().createFile(configFolder).path)
                }
            }
        }
        .toTypedArray()
}

/** Options for specifying sources in a test. */
class TestSourceOptions(
    /** The main project directory containing the source files. */
    val projectDir: File,
    /** The arguments specifying the sources as required by `SourceOptions`. */
    val args: Array<String>,
)

/**
 * Returns [TestSourceOptions] based on the inputs.
 *
 * @param sourceFiles Java or Kotlin source files provided with [ARG_SOURCE_FILES]
 * @param additionalSourcePathFiles Java or Kotlin source files to include in the source path
 *   provided with [ARG_SOURCE_PATH]
 * @param projectDescription an optional XML file containing the Lint project structure
 * @param compiledSourceJar an optional jar file containing the compiled version of [sourceFiles]
 * @param classpath jars to include on the classpath (in addition to the Android jar and Kotlin
 *   stdlib)
 * @param signatureSources signature file contents to provide as [ARG_SOURCE_FILES] instead of
 *   [sourceFiles]
 * @param skipSourceArgs if true, the created [TestSourceOptions.args] will skip [ARG_SOURCE_FILES]
 *   and [ARG_SOURCE_PATH]
 */
fun TemporaryFolderOwner.testSources(
    sourceFiles: Array<TestFile>,
    additionalSourcePathFiles: Array<TestFile>,
    projectDescription: TestFile?,
    compiledSourceJar: TestFile?,
    classpath: Array<TestFile>?,
    signatureSources: Array<String>,
    skipSourceArgs: Boolean,
): TestSourceOptions {
    val projectDir = createProjectDir(sourceFiles)
    val classpathFiles = classpath?.map { it.createFile(projectDir) }

    val args: Array<String> =
        buildList {
                val sourceList = getSourceList(projectDir, signatureSources, sourceFiles)
                if (!skipSourceArgs) {
                    add(ARG_SOURCE_PATH)
                    add(getSourcePath(projectDir, sourceFiles, additionalSourcePathFiles))
                    for (sourceFile in sourceList) {
                        add(ARG_SOURCE_FILES)
                        add(sourceFile)
                    }
                }

                if (projectDescription != null) {
                    val projectDescriptionFile = projectDescription.createFile(projectDir)
                    // Classpath isn't needed when it is specified through the project xml
                    add(ARG_PROJECT)
                    add(projectDescriptionFile.absolutePath)
                    // When project description is provided, skip listing (common) sources
                } else {
                    val androidJar = getAndroidJar()
                    add(ARG_CLASS_PATH)
                    add(androidJar.path)
                    if (classpathFiles != null) {
                        add(ARG_CLASS_PATH)
                        val classpathString =
                            classpathFiles
                                .map { it.path }
                                .joinToString(separator = File.pathSeparator) { it }
                        add(classpathString)
                    }
                    addAll(findKotlinStdlibPathArgs(sourceList))
                }

                if (compiledSourceJar != null) {
                    val compiledSourceJarFile = compiledSourceJar.createFile(projectDir)
                    add(ARG_COMPILED_SOURCES)
                    add(compiledSourceJarFile.absolutePath)
                }
            }
            .toTypedArray()

    return TestSourceOptions(projectDir, args)
}

/**
 * Returns the paths of the directories to provide as the [ARG_SOURCE_PATH].
 *
 * If any [sourceFiles] are in a `src2` directory, adds that to the path.
 *
 * If there are [additionalSourcePathFiles], those are placed in a separate directory also included
 * on the path.
 */
private fun TemporaryFolderOwner.getSourcePath(
    projectDir: File,
    sourceFiles: Array<TestFile>,
    additionalSourcePathFiles: Array<TestFile>,
): String {
    val sourcePathDir = File(projectDir, "src")
    if (!sourcePathDir.isDirectory) {
        sourcePathDir.mkdirs()
    }

    var sourcePath = sourcePathDir.path

    // Make it easy to configure a source path with more than one source root: src and src2
    if (sourceFiles.any { it.targetPath.startsWith("src2") }) {
        sourcePath = sourcePath + File.pathSeparator + sourcePath + "2"
    }

    // Add any additional sources onto the source path.
    if (additionalSourcePathFiles.isNotEmpty()) {
        // Get the directory for the folder.
        val dir = getOrCreateFolder("extra-source-files")

        // Create the files. Note, that Java files are created in a `src` subdirectory of
        // the dir passed in to createFiles(File).
        additionalSourcePathFiles.createFiles(dir)

        // Create a file for the `src` subdirectory.
        val srcDir = dir.resolve("src")

        // Add a label for it.
        temporaryFolder.addTestLabelForFile(srcDir, "ADDITIONAL-SOURCE-PATH")

        // Add it to the source path.
        sourcePath = sourcePath + File.pathSeparator + srcDir
    }
    return sourcePath
}

private fun pathUnderProject(projectDir: File, path: String): String = File(projectDir, path).path

/**
 * Returns a list of the paths to provide as [ARG_SOURCE_FILES].
 *
 * If there are [signatureSources], they are written to text files in the [projectDir] and their
 * paths are returned.
 *
 * If there are no [signatureSources], the paths of the [sourceFiles] are returned.
 */
private fun getSourceList(
    projectDir: File,
    signatureSources: Array<String>,
    sourceFiles: Array<TestFile>
): List<String> {
    return if (signatureSources.isNotEmpty()) {
        var num = 0
        signatureSources.map { file ->
            val signatureFile =
                File(projectDir, "load-api${if (++num == 1) "" else num.toString()}.txt")
            signatureFile.writeSignatureText(file)
            signatureFile.absolutePath
        }
    } else {
        sourceFiles.map { pathUnderProject(projectDir, it.targetPath) }
    }
}

/**
 * If any of the [sources] are Kotlin, returns the paths of the Kotlin stdlib jars as metalava args
 * expected by SourceOptions.
 *
 * If none of the [sources] are Kotlin, returns an empty array.
 */
private fun findKotlinStdlibPathArgs(sources: List<String>): Array<String> {
    // If there are no Kotlin files, don't include the stdlib in the classpath.
    if (sources.none { it.endsWith(DOT_KT) }) return emptyArray()
    val kotlinPaths = getKotlinStdlibPaths()
    return if (kotlinPaths.isEmpty()) emptyArray()
    else
        arrayOf(
            ARG_CLASS_PATH,
            kotlinPaths.joinToString(separator = File.pathSeparator) { it.path }
        )
}
