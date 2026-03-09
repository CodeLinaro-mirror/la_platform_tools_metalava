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

package com.android.tools.metalava.model.testsuite.sourcefile

import com.android.tools.metalava.model.JavaImport
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.imports.ImportResolver
import com.android.tools.metalava.model.imports.ResolvedImport
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.value.ValueExample
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

@RequiresCapabilities(
    // Only supports java imports at the moment.
    Capability.JAVA,
    // Requires access to the original imports.
    Capability.IMPORTS,
)
class CommonParameterizedImportTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [ValueExample] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class TestParams
    @EntryPoint
    constructor(
        val name: String,
        val imports: String = "import $name;\n",
        val expectedJavaImports: List<JavaImport>,
        val expectedResolvedImports: Map<String, ResolvedImport?> = emptyMap(),
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String {
            return name
        }
    }

    companion object {
        private val params =
            listOf(
                TestParams(
                    name = "java.util.Map.Entry",
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.util.Map.Entry",
                                onDemand = false,
                                static = false,
                            ),
                        ),
                    expectedResolvedImports =
                        mapOf(
                            "Entry" to ResolvedImport("java.util.Map.Entry"),
                            "Map" to null,
                        )
                ),
                TestParams(
                    name = "static java.util.Map.Entry",
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.util.Map.Entry",
                                onDemand = false,
                                static = true,
                            ),
                        ),
                    expectedResolvedImports =
                        mapOf(
                            "Entry" to ResolvedImport("java.util.Map", "Entry"),
                            "Map" to null,
                        )
                ),
                TestParams(
                    name = "java.util.Map.*",
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.util.Map",
                                onDemand = true,
                                static = false,
                            ),
                        ),
                    expectedResolvedImports =
                        mapOf(
                            "Entry" to ResolvedImport("java.util.Map.Entry"),
                            "Map" to null,
                        )
                ),
                TestParams(
                    name = "static java.util.Map.*",
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.util.Map",
                                onDemand = true,
                                static = true,
                            ),
                        ),
                    expectedResolvedImports =
                        mapOf(
                            "Entry" to ResolvedImport("java.util.Map", "Entry"),
                            "Map" to null,
                        )
                ),
                TestParams(
                    name = "java.io.*",
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.io",
                                onDemand = true,
                                static = false,
                            ),
                        ),
                    expectedResolvedImports =
                        mapOf(
                            "IOException" to ResolvedImport("java.io.IOException"),
                        )
                ),
                TestParams(
                    name = "static java.lang.Character.*",
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.lang.Character",
                                onDemand = true,
                                static = true,
                            ),
                        ),
                    expectedResolvedImports =
                        mapOf(
                            "isAlphabetic" to ResolvedImport("java.lang.Character", "isAlphabetic"),
                        )
                ),
                TestParams(
                    name = "static java.lang.System.err",
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.lang.System.err",
                                onDemand = false,
                                static = true,
                            ),
                        ),
                    expectedResolvedImports =
                        mapOf(
                            "err" to ResolvedImport("java.lang.System", "err"),
                        )
                ),
                // Make sure that on demand imports are processed after explicit imports.
                TestParams(
                    name = "on demand after explicit",
                    imports =
                        """
                            import java.sql.*;
                            import java.util.Date;
                        """,
                    expectedJavaImports =
                        listOf(
                            JavaImport(
                                qualifiedName = "java.sql",
                                onDemand = true,
                                static = false,
                            ),
                            JavaImport(
                                qualifiedName = "java.util.Date",
                                onDemand = false,
                                static = false,
                            ),
                        ),
                    expectedResolvedImports =
                        mapOf(
                            "Date" to ResolvedImport("java.util.Date"),
                            "SQLException" to ResolvedImport("java.sql.SQLException"),
                        )
                ),
            )

        @JvmStatic @Parameterized.Parameters fun params() = params
    }

    /** Check that [simpleName] resolves to [expectedResult] for this [ImportResolver]. */
    private fun ImportResolver.assertResolvedImport(
        simpleName: String,
        expectedResult: ResolvedImport?
    ) {
        // Check the named imports first, then the on demand imports.
        val actualResult =
            resolveImport(simpleName, onDemand = false)
                ?: resolveImport(simpleName, onDemand = true)
        assertEquals(expectedResult, actualResult, message = "$simpleName -> $expectedResult")
    }

    /**
     * Check the [SourceFile.allJavaImports] matches what was expected in
     * [TestParams.expectedJavaImports].
     */
    private fun CodebaseContext.checkJavaImports(sourceFile: SourceFile) {
        var allJavaImports = sourceFile.allJavaImports()
        assertEquals(params.expectedJavaImports, allJavaImports, message = "allJavaImports")

        val importResolver = ImportResolver(codebase, allJavaImports)
        for ((simpleName, expectedResolvedImport) in params.expectedResolvedImports) {
            importResolver.assertResolvedImport(simpleName, expectedResolvedImport)
        }
    }

    @Test
    fun `Test imports in class`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    ${params.imports.trimIndent()}
                    public class Test {}
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val sourceFile = classItem.sourceFile()!!

            checkJavaImports(sourceFile)
        }
    }

    @Test
    fun `Test imports in package-info java`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    "test/pkg/package-info.java",
                    """
                        package test.pkg;
                        ${params.imports.trimIndent()}
                    """
                ),
                // Empty class to make sure that the package is created.
                java(
                    """
                        package test.pkg;
                        public class Test {}
                    """
                ),
            )
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            val sourceFile = packageItem.sourceFile!!

            checkJavaImports(sourceFile)
        }
    }
}
