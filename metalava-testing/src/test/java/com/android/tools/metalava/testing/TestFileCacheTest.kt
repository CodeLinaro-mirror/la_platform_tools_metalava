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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TestFileCacheTest : TemporaryFolderOwner {
    @get:Rule override val temporaryFolder = TemporaryFolder()

    @get:Rule val testFileCacheRule = TestFileCacheRule()

    private val underlyingTestFile = TouchCountingTestFile().apply { to("subdir/cached.txt") }

    private val cachedTestFile = underlyingTestFile.cacheIn(testFileCacheRule)

    /** Check that this [TestFile] creates a file properly in [targetDirName]. */
    private fun TestFile.checkTestFileCreated(targetDirName: String) {
        val dir = temporaryFolder.newFolder(targetDirName)
        val file = createFile(dir)
        assertEquals(dir.resolve(targetPath), file, "created file has wrong path")
        assertTrue(file.exists(), "$targetDirName file does not exist")
    }

    @Test
    fun `Test normal TestFile is called multiple times`() {
        val normalTestFile = TouchCountingTestFile().apply { to("subdir/uncached.txt") }

        normalTestFile.checkTestFileCreated("first")
        normalTestFile.checkTestFileCreated("second")

        assertEquals(2, normalTestFile.count)
    }

    @Test
    fun `Test cached TestFile is only called once`() {
        cachedTestFile.checkTestFileCreated("first")
        cachedTestFile.checkTestFileCreated("second")

        assertEquals(1, underlyingTestFile.count)
    }

    class TouchCountingTestFile : TestFile() {
        var count: Int = 0

        override fun createFile(targetDir: File): File {
            count += 1
            val file = targetDir.resolve(targetPath)
            file.parentFile.mkdirs()
            file.writeText("")
            return file
        }
    }
}
