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

package com.android.tools.metalava

import com.android.tools.lint.checks.infrastructure.TestFile
import java.io.File

object KnownJarFiles {
    /**
     * The jar produced by the `:stub-annotations` project.
     *
     * This must only be access from a project that depends on `:stub-annotations` and sets the
     * `METALAVA_STUB_ANNOTATIONS_JAR` environment variable to the jar produced by the project.
     */
    val stubAnnotationsJar by lazy {
        val envValue =
            System.getenv("METALAVA_STUB_ANNOTATIONS_JAR")
                ?: error("Environment variable METALAVA_STUB_ANNOTATIONS_JAR was not set")
        require(envValue.isNotBlank()) {
            "Invalid environment variable METALAVA_STUB_ANNOTATIONS_JAR: '$envValue'"
        }
        val jar = File(envValue)
        require(jar.isFile) { "stub-annotations jar not found: $jar" }
        jar
    }

    /** The jar produced by the `:stub-annotations` project, exposed as a [TestFile]. */
    val stubAnnotationsTestFile: TestFile by lazy { ExistingFile(stubAnnotationsJar) }
}

/** A simple [TestFile] that just uses an existing file without copying. */
private class ExistingFile(private val file: File) : TestFile() {
    override fun createFile(targetDir: File): File {
        if (targetRelativePath == null) {
            return file
        } else {
            error("Does not support copying file to new target directory")
        }
    }
}
