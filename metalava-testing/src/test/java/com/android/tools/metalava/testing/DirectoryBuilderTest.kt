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

import java.io.File
import java.util.jar.JarInputStream
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectoryBuilderTest : TemporaryFolderOwner {
    @get:Rule override val temporaryFolder = TemporaryFolder()

    private fun File.assertJarFileContents(expected: String) {
        val entries = buildList {
            JarInputStream(inputStream()).use { inputStream ->
                var entry = inputStream.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        this.add(entry.name)
                    }
                    entry = inputStream.nextEntry
                }
            }
        }
        assertEquals(expected, entries.sorted().joinToString("\n").trim())
    }

    @Test
    fun `Test jar with classpath`() {
        lateinit var jarFile: File
        buildFileStructure {
            val classPathJarFile =
                jar(
                    "classpath.jar",
                    java(
                        """
                        package test.pkg;
                        public class Bar {}
                    """
                    ),
                )

            // This will fail to compile if the class path is not provided.
            jarFile =
                jar(
                    "test.jar",
                    java(
                        """
                        package test.pkg;
                        public class Foo extends Bar {}
                    """
                    ),
                    classPath = listOf(classPathJarFile),
                )
        }

        jarFile.assertJarFileContents("test/pkg/Foo.class")
    }
}
