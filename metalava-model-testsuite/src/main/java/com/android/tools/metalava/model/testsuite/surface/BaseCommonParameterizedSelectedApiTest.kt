/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite.surface

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.ApiSurfaceSelector
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.junit4.ParameterFilter
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.ExitPoint
import com.android.tools.metalava.testing.KnownSourceFiles
import kotlin.test.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

/**
 * Base class of parameterized tests that verify selected API variants for different API surfaces.
 *
 * Subclasses provide test cases by defining a companion object extending [BaseCompanion] with a
 * `@Parameterized.Parameters` method that calls [buildTests].
 */
@SupportedInputFormats(InputFormat.JAVA, InputFormat.KOTLIN)
abstract class BaseCommonParameterizedSelectedApiTest : BaseModelTest() {

    @Parameterized.Parameter(0) internal lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class TestParams
    @EntryPoint
    constructor(
        val name: String,
        val surfaceRules: ApiSurfaceRules,
        val sources: List<TestFile>,
        val compiledSources: TestFile?,
        val surface: String,
        val expected: String,
        /** Optional configured [ApiFlags] to use when resolving flagged APIs. */
        val apiFlags: ApiFlags? = null,
        /** Optional previously released codebase sources, used to test API reverting/stability. */
        val previouslyReleasedSources: List<TestFile>? = null,
        val expectedContainsRevertedItem: Boolean = false,
        val expectedIssues: String = "",
        /**
         * Whether additional overrides are considered when checking if a method override is
         * elidable:
         * - `true`: additional overrides are considered.
         * - `false`: additional overrides are not considered.
         * - `null`: this test is independent of additional overrides (or does not specify a
         *   preference) and can run under both settings.
         */
        val addAdditionalOverrides: Boolean? = null,

        /** Optional list of [TestFile]s to add to the class path. */
        val classpath: List<TestFile> = emptyList(),
    ) {
        /** The [InputFormat] of [sources]. */
        val inputFormat: InputFormat by lazy {
            sources
                .asSequence()
                .map { InputFormat.fromFilename(it.targetRelativePath) }
                .reduce { if1, if2 -> if1.combineWith(if2) }
        }

        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString() = name
    }

    open class BaseCompanion {
        protected val extraSources =
            listOf(
                KnownSourceFiles.hideAnnotation,
                KnownSourceFiles.flaggedApiSource,
            ) + TestableApiSurfaces.annotationSources

        /**
         * Build [TestParams] and add them to this list.
         *
         * @param name the [TestParams.name].
         * @param surfaceRules the [TestParams.surfaceRules].
         * @param sources the [TestParams.sources].
         * @param apiFlags the [TestParams.apiFlags].
         * @param previouslyReleasedSources the [TestParams.previouslyReleasedSources].
         * @param expectedContainsRevertedItem the [TestParams.expectedContainsRevertedItem].
         * @param expectedIssues the [TestParams.expectedIssues].
         * @param classpath the [TestParams.classpath].
         * @param body lambda that will add tests for specific surfaces using [Builder.surfaceTest]
         *   or [Builder.additionalOverridesTest] which create a [TestParams] using the above plus
         *   some surface specific information.
         */
        @EntryPoint
        fun MutableList<TestParams>.buildTests(
            name: String,
            surfaceRules: ApiSurfaceRules,
            sources: List<TestFile>,
            compiledSources: TestFile? = null,
            apiFlags: ApiFlags? = null,
            previouslyReleasedSources: List<TestFile>? = null,
            expectedContainsRevertedItem: Boolean = false,
            expectedIssues: String = "",
            classpath: List<TestFile> = emptyList(),
            body: Builder.() -> Unit,
        ) {
            val builder =
                Builder(
                    this,
                    name,
                    surfaceRules,
                    sources,
                    compiledSources,
                    extraSources,
                    apiFlags,
                    previouslyReleasedSources,
                    expectedContainsRevertedItem,
                    expectedIssues,
                    classpath,
                )
            buildSurfaceTests(builder, body)
        }

        /**
         * Invokes [body] on [builder].
         *
         * Separated out as per instructions in [ExitPoint].
         */
        @ExitPoint
        fun buildSurfaceTests(builder: Builder, body: Builder.() -> Unit) {
            builder.body()
        }

        /** Builder of [TestParams]. */
        class Builder(
            private val params: MutableList<TestParams>,
            private val name: String,
            private val surfaceRules: ApiSurfaceRules,
            private val sources: List<TestFile>,
            private val compiledSources: TestFile?,
            private val extraSources: List<TestFile>,
            private val apiFlags: ApiFlags? = null,
            private val previouslyReleasedSources: List<TestFile>? = null,
            private val expectedContainsRevertedItem: Boolean = false,
            private val expectedIssues: String = "",
            private val classpath: List<TestFile> = emptyList(),
        ) {
            /**
             * Create a test for [surface] that expects [expected] to be the result of calling
             * [Codebase.assertSelectedApiVariants], with `addAdditionalOverrides = null` (meaning
             * the test result is independent of whether additional overrides are considered).
             */
            @EntryPoint
            fun surfaceTest(
                surface: String,
                expected: String,
                expectedContainsRevertedItem: Boolean = this.expectedContainsRevertedItem,
            ) {
                params.add(
                    TestParams(
                        "$name/$surface",
                        surfaceRules,
                        sources + extraSources,
                        compiledSources,
                        surface,
                        expected,
                        apiFlags,
                        previouslyReleasedSources,
                        expectedContainsRevertedItem,
                        expectedIssues,
                        addAdditionalOverrides = null,
                        classpath = classpath,
                    )
                )
            }

            /**
             * Create two tests for [surface], one with `addAdditionalOverrides = false` expecting
             * [expectedWithoutAdditionalOverrides] and one with `addAdditionalOverrides = true`
             * expecting [expectedWithAdditionalOverrides].
             */
            @EntryPoint
            fun additionalOverridesTest(
                surface: String,
                expectedWithAdditionalOverrides: String,
                expectedWithoutAdditionalOverrides: String,
                expectedContainsRevertedItem: Boolean = this.expectedContainsRevertedItem,
            ) {
                params.add(
                    TestParams(
                        "$name without addAdditionalOverrides/$surface",
                        surfaceRules,
                        sources + extraSources,
                        compiledSources,
                        surface,
                        expectedWithoutAdditionalOverrides,
                        apiFlags,
                        previouslyReleasedSources,
                        expectedContainsRevertedItem,
                        expectedIssues,
                        addAdditionalOverrides = false,
                        classpath = classpath,
                    )
                )
                params.add(
                    TestParams(
                        "$name with addAdditionalOverrides/$surface",
                        surfaceRules,
                        sources + extraSources,
                        compiledSources,
                        surface,
                        expectedWithAdditionalOverrides,
                        apiFlags,
                        previouslyReleasedSources,
                        expectedContainsRevertedItem,
                        expectedIssues,
                        addAdditionalOverrides = true,
                        classpath = classpath,
                    )
                )
            }
        }
    }

    companion object : BaseCompanion() {
        /**
         * Filter out any test parameter combinations that are not valid for a specific
         * [InputFormat].
         */
        @JvmStatic
        @ParameterFilter
        fun parameterFilter(
            config: CodebaseCreatorConfig<ModelSuiteRunner>,
            testParams: TestParams,
        ): Boolean {
            val inputFormat = config.inputFormat
            return testParams.inputFormat == inputFormat
        }
    }

    @Test
    fun `Test selected api variants`() {
        val rules = params.surfaceRules.retargetAt(params.surface)

        val addAdditionalOverrides = params.addAdditionalOverrides == true

        fun runSelectedApiTest(annotationManagerFactory: (TestFixture.() -> AnnotationManager)?) {
            runCodebaseTest(
                inputSet(params.sources),
                compiledSourceJar = params.compiledSources,
                testFixture =
                    TestFixture(
                        // Match test packages (e.g. `test` and `test.pkg`) but exclude `test.api.*`
                        // (which contains the surface selection annotations from
                        // [TestableApiSurfaces.annotationSources]). This allows the compiler to
                        // resolve annotations during compilation while preventing the annotation
                        // classes from being added to the codebase and dumped in
                        // [assertSelectedApiVariants].
                        apiPackages = PackageFilter.parse("test.*:-test.api.*"),
                        apiSurfaceRules = rules,
                        apiFlags = params.apiFlags,
                        additionalClassPath = params.classpath.map { it.toFile() },
                        addAdditionalOverrides = addAdditionalOverrides,
                        annotationManagerFactory = annotationManagerFactory,
                        javaLanguageLevel = "17",
                        // Disable the supported InputFormat check as this test is already
                        // parameterized and filtered by InputFormat.
                        checkSupportedInputFormats = false,
                    ),
            ) {
                // Snapshot codebases do not support hidden items. The lack of the HIDDEN_ITEMS
                // capability indicates this test is running against a snapshot codebase.
                val isSnapshot = !codebaseCreatorHasCapability(Capability.HIDDEN_ITEMS)

                // Snapshot codebases cannot test reverted items. When snapshotting,
                // CodebaseSnapshotTaker replaces reverted items with their released counterparts
                // (via actualItemToSnapshot) and copies variants directly from them instead of
                // verifying SelectedApiUpdater's variant calculations on the source items.
                assumeFalse(
                    "Snapshot cannot support revert test",
                    params.expectedContainsRevertedItem && isSnapshot,
                )

                codebase.assertSelectedApiVariants(params.expected)

                // Strip line numbers from reported issues if expectedIssues does not contain
                // line numbers, to handle differences between model providers (e.g. Turbine
                // vs PSI on record components).
                val actualIssues =
                    removeReportedIssues().let { issues ->
                        if (!params.expectedIssues.contains(Regex("""\.[a-z]+:\d+:"""))) {
                            issues.replace(Regex("""(\.[a-z]+):\d+:"""), "$1:")
                        } else {
                            issues
                        }
                    }
                assertEquals(params.expectedIssues.trimIndent(), actualIssues)

                // Snapshot codebases do not track whether items were reverted, so
                // codebase.containsRevertedItem is only checked on source codebases.
                if (!isSnapshot) {
                    assertEquals(
                        params.expectedContainsRevertedItem,
                        codebase.containsRevertedItem,
                        message = "codebase.containsRevertedItem",
                    )
                }
            }
        }

        // If previously released sources are provided, create a codebase from them to act as the
        // previously released codebase. This is supplied to the AnnotationManager so it can
        // determine the released status of items when testing API stability and reverting
        // flagged APIs.
        val previouslyReleasedSources = params.previouslyReleasedSources
        if (previouslyReleasedSources != null) {
            runCodebaseTest(
                inputSet(previouslyReleasedSources),
                // Disable the supported InputFormat check as this test is already
                // parameterized and filtered by InputFormat.
                testFixture =
                    TestFixture(
                        additionalClassPath = params.classpath.map { it.toFile() },
                        checkSupportedInputFormats = false,
                    ),
            ) {
                val releasedCodebase = codebase
                val annotationManagerFactory: TestFixture.() -> AnnotationManager = {
                    DefaultAnnotationManager(
                        DefaultAnnotationManager.Config(
                            reporter = recordingReporter,
                            apiSurfaceSelector =
                                ApiSurfaceSelector(
                                    rules,
                                    addAdditionalOverrides = addAdditionalOverrides,
                                ),
                            apiFlags = params.apiFlags,
                            previouslyReleasedCodebaseProvider = { releasedCodebase }
                        )
                    )
                }

                runSelectedApiTest(annotationManagerFactory)
            }
        } else {
            runSelectedApiTest(annotationManagerFactory = null)
        }
    }
}
