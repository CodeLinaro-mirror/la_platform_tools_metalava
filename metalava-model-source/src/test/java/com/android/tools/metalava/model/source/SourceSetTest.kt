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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.reporter.RecordingReporter
import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import java.io.File
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SourceSetTest : TemporaryFolderOwner {
    /** Provides access to temporary files. */
    @get:Rule override val temporaryFolder = TemporaryFolder()

    private val reporter = RecordingReporter()

    private fun assertAndRemoveReportedIssues(expectedIssues: String) {
        val issues = replaceFileWithSymbol(reporter.removeIssues())
        assertEquals(expectedIssues.trimIndent(), issues.trimIndent())
    }

    @After
    fun tearDown() {
        // Make sure that no unexpected issues were reported.
        assertAndRemoveReportedIssues("")
    }

    @Test
    fun `Test extract roots - mixture of relative and absolute files`() {
        val sources = buildList {
            add(
                File("src/main/java/com/android/tools/metalava/model/source/SourceSet.kt")
                    .absoluteFile
            )
            add(File("src/main/java/com/android/tools/metalava/model/source/SourceParser.kt"))
        }
        val sourceSet = SourceSet(sources = sources, sourcePath = emptyList())
        val extractedRoots = sourceSet.extractRoots(reporter)

        assertEquals(sources, extractedRoots.sources)
        assertEquals(listOf(File("src/main/java").absoluteFile), extractedRoots.sourcePath)
    }

    @Test
    fun `Test extract roots - root package class - java`() {
        val testFiles =
            listOf(
                java(
                    """
                        public class RootPackageClass {}
                    """
                ),
            )

        val sources = testFiles.map { it.toFile() }

        val sourceSet = SourceSet(sources = sources, sourcePath = emptyList())
        val extractedRoots = sourceSet.extractRoots(reporter)

        val srcDir = temporaryFolder.root.resolve("src")
        assertEquals(sources, extractedRoots.sources)
        assertEquals(listOf(srcDir), extractedRoots.sourcePath)
    }

    @Test
    fun `Test extract roots - root package file - kotlin`() {
        val testFiles =
            listOf(
                kotlin(
                    "src/FooKt.kt",
                    """
                        const val CONST = 1
                    """
                ),
            )

        val sources = testFiles.map { it.toFile() }

        val sourceSet = SourceSet(sources = sources, sourcePath = emptyList())
        val extractedRoots = sourceSet.extractRoots(reporter)

        val srcDir = temporaryFolder.root.resolve("src")
        assertEquals(sources, extractedRoots.sources)
        assertEquals(listOf(srcDir), extractedRoots.sourcePath)
    }

    @Suppress("DanglingJavadoc")
    @Test
    fun `Regression test for 124333557`() {
        // Regression test for 124333557: Handle empty java files
        val testFiles =
            listOf(
                java(
                    "src/test/pkg/Something.java",
                    """
                        /** Nothing much here */
                    """
                ),
                java(
                    "src/test/pkg/Something2.java",
                    """
                        /** Nothing much here */
                        package test.pkg;
                    """
                ),
                java(
                    "src/test/Something2.java",
                    """
                        /** Wrong package */
                        package test.wrong;
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Test {
                            private Test() { }
                        }
                    """
                ),
            )

        testFiles.forEach { it.toFile() }

        // Make sure we handle blank/doc-only java doc files in root extraction.
        val srcDir = listOf(temporaryFolder.root.resolve("src"))
        val sourceSet = SourceSet.createFromSourcePath(reporter, srcDir)
        val roots = sourceSet.extractRoots(reporter).sourcePath
        assertEquals(srcDir, roots)

        assertAndRemoveReportedIssues(
            """
                TESTROOT/src/test/Something2.java: error: Unable to determine the package name. This usually means that a source file was where the directory does not seem to match the package declaration; we expected the path TESTROOT/src/test/Something2.java to end with /test/wrong/Something2.java [IoError]
            """
        )
    }

    @Test
    fun `Regression test for 359909520`() {
        // Regression test for 359909520: Handle kotlin packages that have `` in them.
        val testFiles =
            listOf(
                kotlin(
                    "com/google/receiver/Test.kt",
                    """
                        package com.google.`receiver`
                        class Test
                    """
                ),
            )

        testFiles.forEach { it.toFile() }

        val src = listOf(temporaryFolder.root.resolve("src"))
        val sourceSet = SourceSet.createFromSourcePath(reporter, src)
        val roots = sourceSet.extractRoots(reporter).sourcePath
        assertEquals(1, roots.size)
        assertEquals(src[0].path, roots[0].path)
    }
}
