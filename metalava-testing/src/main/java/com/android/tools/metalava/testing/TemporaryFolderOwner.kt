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
import com.android.tools.metalava.testing.TemporaryFolderOwner.Companion.createTestRule
import java.io.File
import java.util.TreeMap
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/** Provides helper functions for a test class that has a [TemporaryFolder] rule. */
interface TemporaryFolderOwner {
    /**
     * Provides access to temporary files.
     *
     * Must be defined as follows in subclasses:
     * ```
     *     @get:Rule final override val temporaryFolder = createTestRule()
     * ```
     */
    val temporaryFolder: CustomTemporaryFolder

    /**
     * Given an array of [TestFile] get a folder called "project" (creating it if it is empty),
     * write the files to the folder and then return the folder.
     */
    fun createProjectDir(files: Array<TestFile>): File {
        val dir =
            getOrCreateFolder(
                relative = "project",
                testLabel = "TESTROOT",
            )
        files.createFiles(dir)
        return dir
    }

    /**
     * Get a folder with a path [relative] to the root.
     *
     * Use an existing folder, or create a new one if necessary. It is an error if a file exists but
     * is not a directory.
     */
    fun getOrCreateFolder(relative: String = "", testLabel: String? = null): File {
        val dir = temporaryFolder.root.resolve(relative)
        // If the directory exists and is a directory then use it, otherwise drop through to create
        // a new one. If the directory exists but is not a directory then attempting to create a new
        // one will report an issue.
        val newFolder =
            if (dir.isDirectory) {
                dir
            } else {
                temporaryFolder.newFolder(relative)
            }

        // Add a mapping from the new folder to the test label if provided.
        if (testLabel != null) {
            temporaryFolder.addTestLabelForFile(newFolder, testLabel)
        }

        return newFolder
    }

    /**
     * Get a file with a path [relative] to the root.
     *
     * Use an existing file, or create an empty new one if necessary. It is an error if a file
     * exists but is not a normal file.
     */
    fun getOrCreateFile(relative: String = ""): File {
        val file = temporaryFolder.root.resolve(relative)
        // If the file exists and is a normal file then use it, otherwise drop through to create
        // a new one. If the file exists but is not a normal file then attempting to create a new
        // one will report an issue.
        return if (file.isFile) {
            file
        } else {
            file.parentFile.mkdirs()
            temporaryFolder.newFile(relative)
        }
    }

    /** Create a file (and containing directory if necessary) with a path [relative] to the root. */
    fun newFile(relative: String = ""): File {
        val file = temporaryFolder.root.resolve(relative)
        file.parentFile.mkdirs()
        return temporaryFolder.newFile(relative)
    }

    /**
     * Build a file structure in the directory [relative] to the root.
     *
     * Creates the directory first, if needed. Then creates a [DirectoryBuilder] for the directory
     * and then invokes [body] on it to populate the directory.
     */
    fun buildFileStructure(relative: String = "", body: DirectoryBuilder.() -> Unit): File {
        val dir = getOrCreateFolder(relative)
        dir.buildFileStructure(body)
        return dir
    }

    /**
     * Replaces temporary folders that can change from one test run to the next with fixed labels to
     * make it easier to test expectations.
     *
     * @see CustomTemporaryFolder.cleanupString
     */
    fun removeTestSpecificDirectories(string: String) = temporaryFolder.cleanupString(string)

    /** Create a [File] from this [TestFile] in the root directory of the [temporaryFolder]. */
    fun TestFile.toFile() = createFile(temporaryFolder.root)

    companion object {
        /** Create the [TemporaryFolder]. */
        fun createTestRule(): CustomTemporaryFolder = CustomTemporaryFolder()
    }
}

/** Base class for implementers of [TemporaryFolderOwner]. */
open class BaseTemporaryFolderOwner : TemporaryFolderOwner {
    @get:Rule final override val temporaryFolder = createTestRule()
}

/**
 * Custom extension of [TemporaryFolder] that keeps track of mappings from temporary files to a
 * label that is used in [TemporaryFolderOwner.removeTestSpecificDirectories].
 */
class CustomTemporaryFolder : TemporaryFolder() {
    /** Map from file to label to use in test expectations. */
    internal val temporaryFileToTestLabel = mutableMapOf<File, String>()

    override fun before() {
        super.before()

        // Map root to TESTROOT
        temporaryFileToTestLabel[root] = "TESTROOT"
    }

    override fun after() {
        super.after()

        temporaryFileToTestLabel.clear()
    }

    /** Create a new folder and associate it with [testLabel]. */
    fun newFolderWithTestLabel(testLabel: String): File =
        newFolder().also { dir -> addTestLabelForFile(dir, testLabel) }

    /** Add mapping from [file] to [testLabel]. */
    fun addTestLabelForFile(file: File, testLabel: String) {
        val existingLabel = temporaryFileToTestLabel[file]
        if (existingLabel != null && existingLabel != testLabel) {
            error(
                "Inconsistent test labels provided for $file, existing: $existingLabel, new: $testLabel"
            )
        }
        temporaryFileToTestLabel[file] = testLabel
    }

    /**
     * Replaces temporary folders that can change from one test run to the next with fixed labels to
     * make it easier to test expectations.
     *
     * Replaces all the mappings in [temporaryFileToTestLabel].
     */
    fun cleanupString(string: String) = replaceFileWithSymbol(string, temporaryFileToTestLabel)

    /**
     * Replaces temporary folders that can change from one test run to the next with fixed labels to
     * make it easier to test expectations.
     *
     * Creates a set of mappings consisting of:
     * * All the mappings in [fileToSymbol].
     * * A default mapping from [getRoot] to `TESTROOT`.
     *
     * It then applies those mappings in order from most specific (longest) to least specific
     * (shortest).
     */
    internal fun replaceFileWithSymbol(
        string: String,
        fileToSymbol: Map<File, String>,
    ): String {
        // Create an ordered mappings from longest to shortest. That ensures that the most specific
        // mapping is applied to each file.
        val orderedMappings =
            TreeMap<File, String>(longestFileFirst).apply {
                putAll(fileToSymbol)

                // Include the default mapping for the root directory.
                put(root, "TESTROOT")
            }

        var s = string

        // Iterate over all the mappings from longest to shortest.
        for ((file, symbol) in orderedMappings) {
            s = s.replace(file.path, symbol)
            s = s.replace(file.canonicalPath, symbol)
        }

        s = s.trim()

        return s
    }

    companion object {
        /**
         * Define a total order over [File] from longest to shortest. It also does it in reverse
         * alphabetical order but that should not matter.
         */
        private val longestFileFirst = compareByDescending<File> { it.path }
    }
}
