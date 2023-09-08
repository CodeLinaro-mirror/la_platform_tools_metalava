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

package com.android.tools.metalava.model.psi

import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.reporter.BasicReporter
import com.android.tools.metalava.testing.findKotlinStdlibPaths
import com.android.tools.metalava.testing.getAndroidJar
import com.android.tools.metalava.testing.tempDirectory
import com.intellij.openapi.util.Disposer
import java.io.File
import java.io.PrintWriter
import kotlin.test.assertNotNull

inline fun testCodebase(vararg sources: TestFile, action: (PsiBasedCodebase) -> Unit) {
    tempDirectory { tempDirectory ->
        PsiEnvironmentManager().use { psiEnvironmentManager ->
            val codebase = createTestCodebase(psiEnvironmentManager, tempDirectory, *sources)
            try {
                action(codebase)
            } finally {
                destroyTestCodebase(codebase)
            }
        }
    }
}

fun createTestCodebase(
    psiEnvironmentManager: PsiEnvironmentManager,
    directory: File,
    vararg sources: TestFile,
): PsiBasedCodebase {
    Disposer.setDebugMode(true)

    val sourcePaths = sources.map { it.targetPath }.toTypedArray()
    val kotlinStdlibPaths = findKotlinStdlibPaths(sourcePaths)

    val reporter = BasicReporter(PrintWriter(System.err))
    return PsiSourceParser(psiEnvironmentManager, reporter)
        .parseSources(
            sources = sources.map { it.createFile(directory) },
            description = "Test Codebase",
            sourcePath = listOf(directory),
            classpath = kotlinStdlibPaths + listOf(getAndroidJar()),
        )
}

fun destroyTestCodebase(codebase: PsiBasedCodebase) {
    codebase.dispose()

    UastEnvironment.disposeApplicationEnvironment()
    Disposer.assertIsEmpty(true)
}

fun PsiBasedCodebase.assertClass(qualifiedName: String): PsiClassItem {
    val classItem = this.findClass(qualifiedName)
    assertNotNull(classItem) { "Expected $qualifiedName to be defined" }
    return classItem
}
