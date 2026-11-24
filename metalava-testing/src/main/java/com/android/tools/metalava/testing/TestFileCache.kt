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
import java.nio.file.Files
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Cache [TestFile]s such that they can be easily shared between tests.
 *
 * The primary purpose of this is to amortize the cost of creating jars for testing by sharing the
 * resulting [File] across multiple tests at the cost of increased disk usage. However, it can be
 * used to cache, and share, any [TestFile] whose [TestFile.createFile] will create a [File] in the
 * supplied `targetDir`.
 *
 * Must call [open] before calling [TestFile.createFile] on any [TestFile] returned from [cache].
 */
class TestFileCache : AutoCloseable {
    /** The directory in which [File]s are created. */
    private lateinit var cacheDir: File

    /** Map from [TestFile.targetRelativePath] to [CachedTestFile] wrapper. */
    private val targetRelativePathToTestFile = mutableMapOf<String, CachedTestFile>()

    /**
     * Open the cache for use by supplying it with a [cacheDir] in which it will create the [File]s.
     *
     * This can be called after calling [cache] to cache a [TestFile] but must be called before any
     * of those [TestFile]s are created. Separating this from initialization allows the cache to be
     * created and used to cache [TestFile]s without creating a directory that may never be used and
     * may not be easily cleaned up.
     *
     * It is the responsibility of the caller of [open] to ensure that [cacheDir] is deleted, either
     * by calling [close] or some other way, e.g. by passing in a directory created by
     * [TemporaryFolder].
     */
    fun open(cacheDir: File): TestFileCache {
        if (::cacheDir.isInitialized) {
            error(
                "Cannot open cache with `$cacheDir` as it has already been opened with `${this.cacheDir}"
            )
        }
        this.cacheDir = cacheDir
        return this
    }

    /**
     * Cache a [TestFile] for sharing.
     *
     * This returns a [TestFile] wrapper around [testFile] that will ensure that [testFile] will
     * only be created once within this cache. The [TestFile.targetRelativePath] must uniquely
     * identify [testFile] among all [TestFile]s added to this cache.
     */
    fun cache(testFile: TestFile): TestFile {
        val relative =
            testFile.targetRelativePath
                ?: error("Cannot cache $testFile as it has no targetRelativePath")
        val existing = targetRelativePathToTestFile[relative]
        if (existing != null) {
            if (existing.underlying !== testFile) {
                error(
                    "Cannot cache $testFile as its targetRelativePath of `$relative` clashes with $existing"
                )
            }
            return existing
        }

        val cached = CachedTestFile(testFile).apply { to(testFile.targetRelativePath) }
        targetRelativePathToTestFile[relative] = cached
        return cached
    }

    override fun close() {
        if (::cacheDir.isInitialized) {
            cacheDir.deleteRecursively()
        }
    }

    private fun createUnderlyingFile(underlyingFile: TestFile): TestFile {
        if (!::cacheDir.isInitialized) {
            error("Cannot create underlying file as cache has not yet been opened")
        }
        val file = underlyingFile.createFile(cacheDir)
        // Compute the path of the newly created file relative to the directory.
        val relative = file.relativeTo(cacheDir).path

        // Create a TestFile that when created will create a symbolic link to `file` in the same
        // directory relative to the target directory.
        return file.toTestFile().to(relative)
    }

    /** Wrapper around a [TestFile] that ensures that only one instance will be created. */
    private inner class CachedTestFile(val underlying: TestFile) : TestFile() {
        /** Lazily create the [File] from [underlying] and wrap in a [TestFile]. */
        private val file by lazy { createUnderlyingFile(underlying) }

        override fun createFile(targetDir: File?): File =
            // Create the underlying file if necessary and then create a symbolic link to it.
            file.createFile(targetDir)
    }
}

/**
 * A [TestRule] that is intended to be used with `@ClassRule` to create a per test class cache of
 * [TestFile]s.
 *
 * It can also be used with `@Rule` to create a per test cache if a [TestFile] might be created
 * multiple times within the same test.
 */
class TestFileCacheRule : TestRule {
    /**
     * Create the [TestFileCache] immediately so it can be used to cache [TestFile]s before entering
     * the [Statement] returned by [apply].
     */
    private var _cache: TestFileCache? = TestFileCache()

    /** Get the cache to use. */
    val cache: TestFileCache
        get() = _cache ?: error("Cannot access cache outside test run")

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    val cacheDir = Files.createTempDirectory("test-file-cache").toFile()
                    _cache!!.open(cacheDir).use { base.evaluate() }
                } finally {
                    // Prevent the cache from being accessed after it has been used.
                    _cache = null
                }
            }
        }
    }
}

/**
 * Cache this [TestFile] in [cache].
 *
 * Returns a [TestFile] that will only create this file once and will use a symbolic link to add it
 * to the `targetDir` of [TestFile.createFile].
 */
fun TestFile.cacheIn(cache: TestFileCache) = cache.cache(this)

/**
 * Cache this [TestFile] in [cacheRule]'s [TestFileCacheRule.cache].
 *
 * Returns a [TestFile] that will only create this file once and will use a symbolic link to add it
 * to the `targetDir` of [TestFile.createFile].
 */
fun TestFile.cacheIn(cacheRule: TestFileCacheRule) = cacheRule.cache.cache(this)
