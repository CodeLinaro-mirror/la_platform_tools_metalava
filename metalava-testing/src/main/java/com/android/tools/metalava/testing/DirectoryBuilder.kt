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
import java.io.File

/**
 * Helper to build directory structures for testing.
 *
 * @param dir the directory being populated.
 */
class DirectoryBuilder(val dir: File) {
    /**
     * Populate a directory [relative] to [dir], creating the directory and its parents, if
     * necessary. if necessary.
     *
     * Once the directory is created a new [DirectoryBuilder] is created for that directory on which
     * [body] is called to populate it.
     */
    fun dir(relative: String, body: DirectoryBuilder.() -> Unit): File {
        val newDir = dir.resolve(relative)
        newDir.mkdirs()
        val builder = DirectoryBuilder(newDir)
        builder.body()
        return newDir
    }

    /** Create an empty file [relative] to [dir], creating parent directories, if necessary. */
    fun emptyFile(relative: String): File {
        val file = dir.resolve(relative)
        file.parentFile.mkdirs()
        file.createNewFile()
        return file
    }

    /** Create a jar file [relative] to [dir], containing compiled [sources]. */
    fun jar(relative: String, vararg sources: TestFile, classPath: List<File> = emptyList()): File {
        val jarFile = dir.resolve(relative)
        JavacHelper.compileAndJar(jarFile, sources.toList(), classPath)
        return jarFile
    }

    /**
     * Create a signature file [relative] to [dir], contains [contents] trimmed by
     * [String.trimIndent].
     */
    fun signature(relative: String, contents: String) =
        testFile(source(relative, contents.trimIndent()))

    /** Create a [TestFile] in [dir]. */
    fun testFile(testFile: TestFile): File = testFile.createFile(dir)
}

/**
 * Build a file structure within this [File], which must be a directory.
 *
 * Creates a [DirectoryBuilder] for this directory and then invokes [body] on it to populate the
 * directory.
 */
fun File.buildFileStructure(body: DirectoryBuilder.() -> Unit) {
    require(isDirectory) { "Cannot build a file structure in $this as it is not a directory" }

    val builder = DirectoryBuilder(this)
    builder.body()
}
