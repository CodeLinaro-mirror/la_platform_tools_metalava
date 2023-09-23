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

package com.android.tools.metalava.model.psi

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.noOpAnnotationManager
import com.android.tools.metalava.model.source.EnvironmentManager
import com.android.tools.metalava.reporter.BasicReporter
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.testing.TemporaryFolderOwner
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.rules.TemporaryFolder

open class BasePsiTest : TemporaryFolderOwner {

    @get:Rule override val temporaryFolder = TemporaryFolder()

    /** Project directory; initialized by [testCodebase] */
    protected lateinit var projectDir: File

    /**
     * Writer into which the output like error reports are written; initialized by [testCodebase]
     */
    private lateinit var outputWriter: StringWriter

    /** The contents of [outputWriter], cleaned up to remove any references to temporary files. */
    protected val output
        get() = cleanupString(outputWriter.toString(), projectDir)

    /** The [Reporter] that is used to intercept reports. */
    protected lateinit var reporter: Reporter

    fun testCodebase(
        vararg sources: TestFile,
        classPath: List<File> = emptyList(),
        action: (Codebase) -> Unit,
    ) {
        projectDir = temporaryFolder.newFolder()
        PsiEnvironmentManager().use { environmentManager ->
            outputWriter = StringWriter()
            reporter = BasicReporter(PrintWriter(outputWriter))
            val codebase =
                createTestCodebase(
                    environmentManager,
                    projectDir,
                    sources.toList(),
                    classPath,
                    reporter,
                )
            action(codebase)
        }
    }

    /** Runs the [action] for both a Java and Kotlin version of a codebase. */
    fun testJavaAndKotlin(
        javaSource: TestFile,
        kotlinSource: TestFile,
        classPath: List<File> = emptyList(),
        action: (Codebase) -> Unit
    ) {
        testCodebase(javaSource, classPath = classPath, action = action)
        testCodebase(kotlinSource, classPath = classPath, action = action)
    }

    private fun createTestCodebase(
        environmentManager: EnvironmentManager,
        directory: File,
        sources: List<TestFile>,
        classPath: List<File>,
        reporter: Reporter,
    ): Codebase {
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
}
