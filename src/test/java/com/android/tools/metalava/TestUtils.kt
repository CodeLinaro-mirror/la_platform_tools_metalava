/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.visitors.ItemVisitor
import org.intellij.lang.annotations.Language
import org.junit.Assume.assumeTrue
import kotlin.io.path.createTempDirectory

fun java(to: String, @Language("JAVA") source: String): TestFile {
    return TestFiles.java(to, source.trimIndent())
}

fun java(@Language("JAVA") source: String): TestFile {
    return TestFiles.java(source.trimIndent())
}

fun kotlin(@Language("kotlin") source: String): TestFile {
    return TestFiles.kotlin(source.trimIndent())
}

fun kotlin(to: String, @Language("kotlin") source: String): TestFile {
    return TestFiles.kotlin(to, source.trimIndent())
}

internal inline fun withCodebase(
    vararg sources: TestFile,
    useKtModel: Boolean = true,
    action: (Codebase) -> Unit
) {
    // This is thread-safe as it adds a random suffix to the directory prefix
    val tempDirectory = createTempDirectory("codebase").toFile()
    try {
        val codebase = parseSources(
            sources = sources.map { it.createFile(tempDirectory) },
            description = "Test Codebase",
            useKtModel = useKtModel
        )
        try {
            action(codebase)
        } finally {
            // This cleans up underlying services in a PSI codebase
            codebase.dispose()
        }
    } finally {
        // Have to assert here, since [deleteRecursively] returns a success/failure as a boolean
        assert(tempDirectory.deleteRecursively()) { "Temporary directory not cleaned up" }
    }
}

internal inline fun withClass(
    @Language("kotlin") source: String,
    useKtModel: Boolean = true,
    action: (ClassItem) -> Unit
) {
    withCodebase(kotlin(source), useKtModel = useKtModel) { codebase ->
        action(codebase.assumeSingleTopLevelClass())
    }
}

fun Codebase.assumeSingleTopLevelClass(): ClassItem {
    var count = 0
    var result: ClassItem? = null

    this.accept(object : ItemVisitor() {
        override fun visitClass(cls: ClassItem) {
            if (cls.isTopLevelClass()) {
                count++
                result = cls
            }
        }
    })

    assumeTrue("Expected one top level class, got $count", count == 1)

    return result!!
}
