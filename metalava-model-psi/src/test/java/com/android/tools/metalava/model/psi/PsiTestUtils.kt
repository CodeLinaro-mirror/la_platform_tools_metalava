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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.noOpAnnotationManager
import com.android.tools.metalava.model.source.EnvironmentManager
import com.android.tools.metalava.reporter.BasicReporter
import java.io.File
import java.io.PrintWriter
import kotlin.test.assertNotNull

internal fun testCodebaseInTempDirectory(
    tempDirectory: File,
    sources: List<TestFile>,
    classPath: List<File>,
    action: (Codebase) -> Unit
) {
    PsiEnvironmentManager().use { environmentManager ->
        val codebase =
            createTestCodebase(
                environmentManager,
                tempDirectory,
                sources,
                classPath,
            )
        action(codebase)
    }
}

private fun createTestCodebase(
    environmentManager: EnvironmentManager,
    directory: File,
    sources: List<TestFile>,
    classPath: List<File>,
): Codebase {
    val reporter = BasicReporter(PrintWriter(System.err))
    return environmentManager
        .createSourceParser(reporter, noOpAnnotationManager)
        .parseSources(
            sources = sources.map { it.createFile(directory) },
            description = "Test Codebase",
            sourcePath = listOf(directory),
            classPath = classPath,
        )
}

fun Codebase.assertClass(qualifiedName: String): ClassItem {
    val classItem = this.findClass(qualifiedName)
    assertNotNull(classItem) { "Expected $qualifiedName to be defined" }
    return classItem
}

fun ClassItem.assertMethod(methodName: String, parameters: String): MethodItem {
    val methodItem = this.findMethod(methodName, parameters)
    assertNotNull(methodItem) { "Expected $methodName to be defined" }
    return methodItem
}
