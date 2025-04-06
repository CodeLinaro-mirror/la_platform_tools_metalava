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

package com.android.tools.metalava.model.testsuite

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig
import com.android.tools.metalava.model.testing.CodebaseCreatorConfigAware
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.ThrowingReporter
import com.android.tools.metalava.testing.TemporaryFolderOwner
import java.io.File
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter

/**
 * Base class for tests that verify the behavior of model implementations.
 *
 * This is parameterized by [CodebaseCreatorConfig] as even though the tests are run in different
 * projects the test results are collated and reported together. Having the parameters in the test
 * name makes it easier to differentiate them.
 *
 * Note: In the top-level test report produced by Gradle it appears to just display whichever test
 * ran last. However, the test reports in the model implementation projects do list each run
 * separately. If this is an issue then the [ModelSuiteRunner] implementations could all be moved
 * into the same project and run tests against them all at the same time.
 */
@RunWith(ModelTestSuiteRunner::class)
abstract class BaseModelTest() :
    CodebaseCreatorConfigAware<ModelSuiteRunner>, TemporaryFolderOwner, Assertions {

    /**
     * Set by injection by [Parameterized] after class initializers are called.
     *
     * Anything that accesses this, either directly or indirectly must do it after initialization,
     * e.g. from lazy fields or in methods called from test methods.
     *
     * The basic process is that each test class gets given a list of parameters. There are two ways
     * to do that, through field injection or via constructor. If any fields in the test class
     * hierarchy are annotated with the [Parameter] annotation then field injection is used,
     * otherwise they are passed via constructor.
     *
     * The [Parameter] specifies the index within the list of parameters of the parameter that
     * should be inserted into the field. The number of [Parameter] annotated fields must be the
     * same as the number of parameters in the list and each index within the list must be specified
     * by exactly one [Parameter].
     *
     * The life-cycle of a parameterized test class is as follows:
     * 1. The test class instance is created.
     * 2. The parameters are injected into the [Parameter] annotated fields.
     * 3. Follows the normal test class life-cycle.
     */
    final override lateinit var codebaseCreatorConfig: CodebaseCreatorConfig<ModelSuiteRunner>

    /** The [ModelSuiteRunner] that this test must use. */
    private val runner
        get() = codebaseCreatorConfig.creator

    /**
     * The [InputFormat] of the test files that should be processed by this test. It must ignore all
     * other [InputFormat]s.
     *
     * The [CodebaseCreatorConfig.inputFormat] is nullable for running tests in `metalava` project
     * as there is no single [InputFormat] that its runner uses as it mixes [InputFormat.JAVA] and
     * [InputFormat.KOTLIN] side by side. However, it is always provided by the
     * [ModelTestSuiteRunner].
     */
    protected val inputFormat
        get() = codebaseCreatorConfig.inputFormat!!

    @get:Rule override val temporaryFolder = TemporaryFolder()

    /**
     * Set of inputs for a test.
     *
     * Currently, this is limited to one file but in future it may be more.
     */
    data class InputSet(
        /** The [InputFormat] of the [testFiles]. */
        val inputFormat: InputFormat,

        /** The [TestFile]s to explicitly pass to code being tested. */
        val testFiles: List<TestFile>,

        /** The optional [TestFile]s to pass on source path. */
        val additionalTestFiles: List<TestFile>?,
    )

    /** Create an [InputSet] from a list of [TestFile]s. */
    fun inputSet(testFiles: List<TestFile>): InputSet = inputSet(*testFiles.toTypedArray())

    /**
     * Create an [InputSet].
     *
     * It is an error if [testFiles] is empty or if [testFiles] have a mixture of source
     * ([InputFormat.JAVA] or [InputFormat.KOTLIN]) and signature ([InputFormat.SIGNATURE]). If it
     * contains both [InputFormat.JAVA] and [InputFormat.KOTLIN] then the latter will be used.
     */
    fun inputSet(vararg testFiles: TestFile, sourcePathFiles: List<TestFile>? = null): InputSet {
        if (testFiles.isEmpty()) {
            throw IllegalStateException("Must provide at least one source file")
        }

        // Get the paths for the TestFiles.
        val paths = testFiles.map { it.targetRelativePath }

        // Fail if there are any name collisions.
        val uniquePaths = paths.groupBy { it }
        if (uniquePaths.size != testFiles.size) {
            val colliding = uniquePaths.mapNotNull { if (it.value.size == 1) null else it.key }
            error(
                "The following test files in the input set have the same name as another test file:\n${colliding.joinToString("\n") { "    $it" }}"
            )
        }

        val inputFormat =
            paths
                .asSequence()
                // Ignore HTML files.
                .filter { !it.endsWith(".html") }
                // Map to InputFormat.
                .map { InputFormat.fromFilename(it) }
                // Combine InputFormats to produce a single one, may throw an exception if they
                // are incompatible.
                .reduce { if1, if2 -> if1.combineWith(if2) }

        return InputSet(inputFormat, testFiles.toList(), sourcePathFiles)
    }

    /**
     * Context within which the main body of tests that check the state of the [Codebase] will run.
     */
    interface CodebaseContext {
        /** The newly created [Codebase]. */
        val codebase: Codebase

        /** The [InputFormat] from which [codebase] was created. */
        val inputFormat: InputFormat

        /** Replace any test run specific directories in [string] with a placeholder string. */
        fun removeTestSpecificDirectories(string: String): String
    }

    inner class DefaultCodebaseContext(
        override val codebase: Codebase,
        override val inputFormat: InputFormat,
        private val fileToSymbol: Map<File, String>,
    ) : CodebaseContext {
        override fun removeTestSpecificDirectories(string: String): String {
            return replaceFileWithSymbol(string, fileToSymbol)
        }
    }

    /** Additional properties that affect the behavior of the test. */
    data class TestFixture(
        /** The [AnnotationManager] to use when creating a [Codebase]. */
        val annotationManager: AnnotationManager = DefaultAnnotationManager(),

        /**
         * The optional [PackageFilter] that defines which packages can contribute to the API. If
         * this is unspecified then all packages can contribute to the API.
         */
        val apiPackages: PackageFilter? = null,

        /** The set of [ApiSurfaces] used in the test. */
        val apiSurfaces: ApiSurfaces = ApiSurfaces.DEFAULT,

        /** The [Reporter] to use for issues found creating the [Codebase]. */
        val reporter: Reporter = ThrowingReporter.INSTANCE,

        /** Additional jar files to add to the class path. */
        val additionalClassPath: List<File> = emptyList(),
    ) {
        /** The [Codebase.Config] to use when creating a [Codebase] to test. */
        val codebaseConfig =
            Codebase.Config(
                annotationManager = annotationManager,
                apiSurfaces = apiSurfaces,
                reporter = reporter,
            )
    }

    /**
     * Create a [Codebase] from any supplied [inputSets] whose [InputSet.inputFormat] is the same as
     * the current [inputFormat], and then runs a test on each [Codebase].
     */
    private fun createCodebaseFromInputSetAndRun(
        inputSets: Array<out InputSet>,
        projectDescription: TestFile?,
        testFixture: TestFixture,
        test: CodebaseContext.() -> Unit,
    ) {
        // Run the input sets that match the current inputFormat.
        for (inputSet in inputSets.filter { it.inputFormat == inputFormat }) {
            val mainSourceDir = sourceDir(inputSet)
            val projectDescriptionFile = projectDescription?.createFile(mainSourceDir.dir)

            val additionalSourceDir = inputSet.additionalTestFiles?.let { sourceDir(it) }

            val inputs =
                ModelSuiteRunner.TestInputs(
                    inputFormat = inputSet.inputFormat,
                    modelOptions = codebaseCreatorConfig.modelOptions,
                    mainSourceDir = mainSourceDir,
                    additionalMainSourceDir = additionalSourceDir,
                    testFixture = testFixture,
                    projectDescription = projectDescriptionFile,
                )
            runner.createCodebaseAndRun(inputs) { codebase ->
                val context =
                    DefaultCodebaseContext(
                        codebase,
                        inputFormat,
                        buildMap {
                            this[mainSourceDir.dir] = "MAIN_SRC"
                            additionalSourceDir?.dir?.let { dir -> this[dir] = "ADDITIONAL_SRC" }
                        }
                    )
                context.test()
            }
        }
    }

    private fun sourceDir(inputSet: InputSet): ModelSuiteRunner.SourceDir {
        return sourceDir(inputSet.testFiles)
    }

    private fun sourceDir(testFiles: List<TestFile>): ModelSuiteRunner.SourceDir {
        val tempDir = temporaryFolder.newFolder()
        return ModelSuiteRunner.SourceDir(dir = tempDir, contents = testFiles)
    }

    private fun testFilesToInputSets(testFiles: Array<out TestFile>): Array<InputSet> {
        return testFiles.map { inputSet(it) }.toTypedArray()
    }

    /**
     * Create a [Codebase] from one of the supplied [sources] and then run the [test] on that
     * [Codebase].
     *
     * The [sources] array should have at most one [TestFile] whose extension matches an
     * [InputFormat.extension].
     */
    fun runCodebaseTest(
        vararg sources: TestFile,
        testFixture: TestFixture = TestFixture(),
        test: CodebaseContext.() -> Unit,
    ) {
        runCodebaseTest(
            sources = testFilesToInputSets(sources),
            testFixture = testFixture,
            test = test,
        )
    }

    /**
     * Create a [Codebase] from one of the supplied [sources] [InputSet] and then run the [test] on
     * that [Codebase].
     *
     * The [sources] array should have at most one [InputSet] of each [InputFormat].
     */
    fun runCodebaseTest(
        vararg sources: InputSet,
        projectDescription: TestFile? = null,
        testFixture: TestFixture = TestFixture(),
        test: CodebaseContext.() -> Unit,
    ) {
        createCodebaseFromInputSetAndRun(
            inputSets = sources,
            projectDescription = projectDescription,
            testFixture = testFixture,
            test = test,
        )
    }

    /**
     * Create a [Codebase] from one of the supplied [sources] and then run the [test] on that
     * [Codebase].
     *
     * The [sources] array should have at most one [TestFile] whose extension matches an
     * [InputFormat.extension].
     */
    fun runSourceCodebaseTest(
        vararg sources: TestFile,
        projectDescription: TestFile? = null,
        testFixture: TestFixture = TestFixture(),
        test: CodebaseContext.() -> Unit,
    ) {
        runSourceCodebaseTest(
            sources = testFilesToInputSets(sources),
            projectDescription = projectDescription,
            testFixture = testFixture,
            test = test,
        )
    }

    /**
     * Create a [Codebase] from one of the supplied [sources] [InputSet]s and then run the [test] on
     * that [Codebase].
     *
     * The [sources] array should have at most one [InputSet] of each [InputFormat].
     */
    fun runSourceCodebaseTest(
        vararg sources: InputSet,
        projectDescription: TestFile? = null,
        testFixture: TestFixture = TestFixture(),
        test: CodebaseContext.() -> Unit,
    ) {
        createCodebaseFromInputSetAndRun(
            inputSets = sources,
            projectDescription = projectDescription,
            testFixture = testFixture,
            test = test,
        )
    }

    /**
     * Create a signature [TestFile] with the supplied [contents] in a file with a path of
     * `api.txt`.
     */
    fun signature(contents: String): TestFile = signature("api.txt", contents)

    /** Create a signature [TestFile] with the supplied [contents] in a file with a path of [to]. */
    fun signature(to: String, contents: String): TestFile =
        TestFiles.source(to, contents.trimIndent())
}
