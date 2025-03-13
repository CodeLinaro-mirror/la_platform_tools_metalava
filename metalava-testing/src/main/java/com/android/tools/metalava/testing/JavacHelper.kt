/*
 * Copyright (C) 2025 The Android Open Source Project
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

object JavacHelper {
    private val jdkPath = getJdkPath()

    private fun getJdkPath(): String {
        val javaHome = System.getProperty("java.home")
        if (javaHome != null) {
            var javaHomeFile = File(javaHome)
            if (File(javaHomeFile, "bin${File.separator}javac").exists()) {
                return javaHome
            } else if (javaHomeFile.name == "jre") {
                javaHomeFile = javaHomeFile.parentFile
                if (File(javaHomeFile, "bin${File.separator}javac").exists()) {
                    return javaHomeFile.path
                }
            }
        }
        return System.getenv("JAVA_HOME") ?: error("Could not get JDK path")
    }

    private fun runCommand(executable: String, args: List<String>) {
        val command = buildList {
            add(executable)
            addAll(args)
        }

        val output = ByteArrayOutputStream()
        val exitCode =
            try {
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                process.inputStream.copyTo(output)
                process.waitFor()
            } catch (e: Exception) {
                error("Failed to run `$command` (${e.message})")
            }

        if (exitCode != 0) {
            error(
                "Executing `$command` failed with the following output:\n${output.toString(Charsets.UTF_8).prependIndent()}"
            )
        }
    }

    /** Compile the [sources] into [outputDirectory] throwing an exception if it fails. */
    fun compile(outputDirectory: File, sources: List<File>) {
        runCommand(
            "$jdkPath/bin/javac",
            buildList {
                add("-d")
                add(outputDirectory.path)
                sources.mapTo(this) { it.path }
            }
        )
    }

    /** Use the `jar` tool to create [jarFile] containing [classesDir]. */
    private fun jar(jarFile: File, classesDir: File) {
        runCommand(
            "$jdkPath/bin/jar",
            buildList {
                add("--create")
                add("--file")
                add(jarFile.path)
                // Change directory to classesDir
                add("-C")
                add(classesDir.path)
                // Include everything from this directory.
                add(".")
            }
        )
    }

    /** Compile the [sources] into [jarFile] throwing an exception if it fails. */
    fun compileAndJar(jarFile: File, sources: List<TestFile>) {
        // Make sure that the directory in which the jar file will be written exists.
        val jarDir = jarFile.parentFile
        jarDir.mkdirs()

        // Create a temporary directory for building the jar.
        val tempDir = Files.createTempDirectory(jarDir.toPath(), "jar").toFile()
        val srcDir = tempDir.resolve("src")
        val sourceFiles = sources.map { it.createFile(srcDir) }
        val classesDir = tempDir.resolve("classes")

        // Compile the source files.
        compile(outputDirectory = classesDir, sourceFiles)

        // Jar up the class files.
        jar(jarFile, classesDir)

        // Clean-up the temporary directory.
        tempDir.deleteRecursively()
    }
}
