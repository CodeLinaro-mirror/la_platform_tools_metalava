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

package com.android.tools.metalava.testing

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.toBase64gzip
import java.io.File

private const val DOT_KT = ".kt"

/** Get Kotlin stdlib paths. */
fun getKotlinStdlibPaths(): MutableList<File> {
    val classPath: String = System.getProperty("java.class.path")
    val paths = mutableListOf<File>()
    for (path in classPath.split(':')) {
        val file = File(path)
        val name = file.name
        if (name.startsWith("kotlin-stdlib") || name.startsWith("kotlin-script-runtime")) {
            paths.add(file)
        }
    }
    if (paths.isEmpty()) {
        error("Did not find kotlin-stdlib-jre8 in classpath: $classPath")
    }
    return paths
}

/** Get the Kotlin stdlib paths if needed for [sources]. */
fun findKotlinStdlibPaths(sources: Array<String>): List<File> {
    val paths = getKotlinStdlibPaths()
    return if (sources.asSequence().any { it.endsWith(DOT_KT) }) {
        paths
    } else {
        emptyList()
    }
}

/**
 * Utility to create the base64gzip of a jar file compiled from test source [kotlinFiles].
 *
 * [kotlincPath] should be the absolute path to a local kotlinc binary
 * (https://kotlinlang.org/docs/command-line.html).
 *
 * The output includes a comment with the kotlinc version used followed by the base64gzip.
 *
 * Example:
 * ```
 * val kotlinFiles = listOf(
 *     kotlin("package test\nclass MyClass"),
 * )
 * val base64gzip = generateBase64gzipFromKotlin(kotlinFiles, "/path/to/kotlinc")
 * ```
 *
 * Use base64gzip in your test with `base64gzip("test.jar", base64gzip)`
 */
fun generateBase64gzipFromKotlin(kotlinFiles: List<TestFile>, kotlincPath: String): String {
    // Create all test kotlin files.
    val workingDirectory = TestFile.createTempDirectory()
    workingDirectory.mkdirs()
    val kotlinFilePaths =
        kotlinFiles.map { kotlinFile -> kotlinFile.createFile(workingDirectory).absolutePath }

    // Create the jar file which will be used as output.
    val outputJar = File(workingDirectory, "out.jar")
    outputJar.createNewFile()

    // Run the Kotlin compiler.
    val proc =
        ProcessBuilder(kotlincPath, "-d", outputJar.absolutePath, *kotlinFilePaths.toTypedArray())
            .directory(workingDirectory)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
    proc.waitFor()

    if (proc.exitValue() != 0) {
        error("Could not create jar:\n${proc.errorReader().readText()}")
    }

    // Convert the jar to base64gzip that can be used in tests.
    val base64gzip = toBase64gzip(outputJar)

    // Get the kotlinc version used to include in a comment
    val versionProc =
        ProcessBuilder(kotlincPath, "-version")
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
    versionProc.waitFor()
    val versionOutput = versionProc.errorReader().readText()

    return "// kotlinc version $versionOutput$base64gzip"
}
