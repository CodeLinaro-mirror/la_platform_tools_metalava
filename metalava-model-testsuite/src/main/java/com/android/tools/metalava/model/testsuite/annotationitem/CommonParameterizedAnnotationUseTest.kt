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

package com.android.tools.metalava.model.testsuite.annotationitem

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationUse
import com.android.tools.metalava.model.junit4.ParameterFilter
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

/** Common tests for implementations of [AnnotationItem]. */
class CommonParameterizedAnnotationUseTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class TestParams
    @EntryPoint
    constructor(
        val className: String,
        val testFile: TestFile,
        val expectedAnnotationUse: AnnotationUse,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String {
            return className
        }
    }

    companion object {
        private val params =
            listOf(
                TestParams(
                    className = "type.use.only.NonNull",
                    testFile = KnownSourceFiles.typeUseOnlyNonNullSource,
                    expectedAnnotationUse = AnnotationUse.TYPE_ONLY,
                ),
                TestParams(
                    className = "not.type.use.Nullable",
                    testFile = KnownSourceFiles.notTypeUseNullableSource,
                    expectedAnnotationUse = AnnotationUse.DECLARATION_ONLY,
                ),
                TestParams(
                    className = "libcore.util.NonNull",
                    testFile = KnownSourceFiles.libcoreNonNullSource,
                    expectedAnnotationUse = AnnotationUse.DECLARATION_AND_TYPE,
                ),
                TestParams(
                    className = "libcore.util.Nullable",
                    testFile = KnownSourceFiles.libcoreNullableSource,
                    expectedAnnotationUse = AnnotationUse.DECLARATION_AND_TYPE,
                ),
                TestParams(
                    className = "androidx.annotation.IntRange",
                    testFile = KnownSourceFiles.intRangeTypeUseSource,
                    expectedAnnotationUse = AnnotationUse.DECLARATION_AND_TYPE,
                ),
                TestParams(
                    className = "androidx.annotation.RestrictTo",
                    testFile = KnownSourceFiles.restrictToSource,
                    expectedAnnotationUse = AnnotationUse.DECLARATION_ONLY,
                ),
                TestParams(
                    className = "test.pkg.KotlinAndJava",
                    testFile =
                        kotlin(
                            """
                                package test.pkg

                                import java.lang.annotation.ElementType.*

                                @Target(
                                    AnnotationTarget.TYPE,
                                )
                                @Suppress("DEPRECATED_JAVA_ANNOTATION")
                                @java.lang.annotation.Target(TYPE)
                                annotation class KotlinAndJava
                            """
                        ),
                    expectedAnnotationUse = AnnotationUse.DECLARATION_AND_TYPE,
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = params

        /** Filter the parameters. */
        @JvmStatic
        @ParameterFilter
        fun parameterFilter(
            config: CodebaseCreatorConfig<ModelSuiteRunner>,
            testParams: TestParams,
        ): Boolean {
            val inputFormat = config.inputFormat

            // Ignore any tests that are not valid for the InputFormat supported by the test file.
            return InputFormat.fromFilename(testParams.testFile.targetRelativePath) == inputFormat
        }
    }

    @Test
    fun `Test annotation use`() {
        runCodebaseTest(
            params.testFile,
        ) {
            val testClass = codebase.assertClass(params.className)
            assertEquals(params.expectedAnnotationUse, testClass.annotationClass.annotationUse)
        }
    }
}
