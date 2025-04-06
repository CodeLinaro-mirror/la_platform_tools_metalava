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
 * [TestFile] implementation that creates a jar file [relative] from a set of [sources] compiled
 * against [classPath].
 */
private class JarFromSourcesFile(
    relative: String,
    private val sources: List<TestFile>,
    private val classPath: List<TestFile>
) : TestFile() {
    init {
        to(relative)
    }

    override fun createFile(targetDir: File): File {
        val jarFile = targetDir.resolve(targetRelativePath)
        JavacHelper.compileAndJar(jarFile, sources, classPath)
        return jarFile
    }
}

/**
 * Create a [TestFile] that will create a jar file [relative] from a set of [sources] compiled
 * against [classPath].
 */
fun jarFromSources(
    relative: String,
    vararg sources: TestFile,
    classPath: List<TestFile> = emptyList()
): TestFile = JarFromSourcesFile(relative, sources.toList(), classPath)
