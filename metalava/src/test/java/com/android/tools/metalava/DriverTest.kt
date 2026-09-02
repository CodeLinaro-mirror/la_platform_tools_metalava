/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.SdkConstants.DOT_TXT
import com.android.tools.lint.LintCliClient
import com.android.tools.lint.checks.ApiLookup
import com.android.tools.lint.checks.infrastructure.ClassName
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.stripComments
import com.android.tools.lint.client.api.LintClient
import com.android.tools.metalava.cli.common.ARG_CLASS_PATH
import com.android.tools.metalava.cli.common.ARG_COMPILED_SOURCES
import com.android.tools.metalava.cli.common.ARG_ERROR
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.cli.common.ARG_JAVA_SOURCE
import com.android.tools.metalava.cli.common.ARG_MERGE_INCLUSION_ANNOTATIONS
import com.android.tools.metalava.cli.common.ARG_MERGE_QUALIFIER_ANNOTATIONS
import com.android.tools.metalava.cli.common.ARG_NO_COLOR
import com.android.tools.metalava.cli.common.ARG_PROJECT
import com.android.tools.metalava.cli.common.ARG_QUIET
import com.android.tools.metalava.cli.common.ARG_REPEAT_ERRORS_MAX
import com.android.tools.metalava.cli.common.ARG_SOURCE_PATH
import com.android.tools.metalava.cli.common.ARG_TRACE_FILE
import com.android.tools.metalava.cli.common.ARG_VERBOSE
import com.android.tools.metalava.cli.common.ARG_WARNING
import com.android.tools.metalava.cli.common.CheckerContext
import com.android.tools.metalava.cli.common.CheckerFunction
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.common.TestEnvironment
import com.android.tools.metalava.cli.compatibility.ARG_API_COMPAT_ANNOTATION
import com.android.tools.metalava.cli.compatibility.ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED
import com.android.tools.metalava.cli.compatibility.ARG_CHECK_COMPATIBILITY_API_RELEASED
import com.android.tools.metalava.cli.compatibility.ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED
import com.android.tools.metalava.cli.compatibility.ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED
import com.android.tools.metalava.cli.compatibility.ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED
import com.android.tools.metalava.cli.lint.ARG_API_LINT
import com.android.tools.metalava.cli.lint.ARG_API_LINT_PREVIOUS_API
import com.android.tools.metalava.cli.lint.ARG_BASELINE_API_LINT
import com.android.tools.metalava.cli.lint.ARG_ERROR_MESSAGE_API_LINT
import com.android.tools.metalava.cli.lint.ARG_UPDATE_BASELINE_API_LINT
import com.android.tools.metalava.cli.multiplatform.ARG_MULTIPLATFORM_API_DIR
import com.android.tools.metalava.cli.multiplatform.ARG_MULTIPLATFORM_API_SOURCES
import com.android.tools.metalava.cli.multiplatform.ARG_MULTIPLATFORM_CHECK_COMPATIBILITY
import com.android.tools.metalava.cli.multiplatform.ARG_MULTIPLATFORM_ENABLED
import com.android.tools.metalava.cli.signature.ARG_FORMAT
import com.android.tools.metalava.model.ANDROIDX_ANNOTATION_PACKAGE
import com.android.tools.metalava.model.ANDROID_ANNOTATION_PACKAGE
import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.StripJavaLangPrefix
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.source.SourceModelProvider
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.source.utils.DOT_KT
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig
import com.android.tools.metalava.model.testing.CodebaseCreatorConfigAware
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.INCLUDE_TYPE_USE_ANNOTATIONS
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.KOTLIN_NAME_TYPE_ORDER
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.KOTLIN_STYLE_NULLS
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.STRIP_JAVA_LANG_PREFIX
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.text.assertSignatureFilesMatch
import com.android.tools.metalava.model.text.prepareSignatureFileForTest
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Issues.Issue
import com.android.tools.metalava.reporter.ReporterEnvironment
import com.android.tools.metalava.reporter.Severity
import com.android.tools.metalava.reporter.ThrowingReporter
import com.android.tools.metalava.testing.BaseTemporaryFolderOwner
import com.android.tools.metalava.testing.JavacCompilationError
import com.android.tools.metalava.testing.JavacHelper
import com.android.tools.metalava.testing.KnownJarFiles
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.createFiles
import com.android.tools.metalava.testing.findKotlinStdlibPaths
import com.android.tools.metalava.testing.getAndroidJar
import com.android.tools.metalava.testing.xml
import com.android.utils.SdkUtils
import com.google.common.io.Closeables
import com.intellij.openapi.util.Disposer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.nio.file.Files
import junit.framework.ComparisonFailure
import kotlin.text.Charsets.UTF_8
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ErrorCollector
import org.junit.runner.RunWith

@RunWith(DriverTestRunner::class)
abstract class DriverTest :
    CodebaseCreatorConfigAware<SourceModelProvider>, BaseTemporaryFolderOwner(), Assertions {
    @get:Rule val errorCollector = ErrorCollector()

    /** The [CodebaseCreatorConfig] under which this test will be run. */
    final override lateinit var codebaseCreatorConfig: CodebaseCreatorConfig<SourceModelProvider>

    @Before
    fun setup() {
        Disposer.setDebugMode(true)
    }

    // Makes a note to fail the test, but still allows the test to complete before failing
    protected fun addError(error: String) {
        errorCollector.addError(Throwable(error))
    }

    protected fun getApiFile(): File {
        return File(temporaryFolder.root.path, "public-api.txt")
    }

    /**
     * Run the Metalava main command.
     *
     * This provides for three separate ways to handle the failure message:
     * 1. Not expected to fail. In this case `expectedToFail = false` and `expectedFailureMessage =
     *    null`.
     * 2. Expected to fail but do not care about the message as that is not what is being tested. In
     *    this case `expectedToFail = true` and `expectedFailureMessage = null`.
     * 3. Expected to fail with specific message. In this case `expectedToFail = true` and
     *    `expectedFailureMessage = "...expected message..."`.
     */
    private fun runDriver(
        // The SameParameterValue check reports that this is passed the same value because the first
        // value that is passed is always the same but this is a varargs parameter so other values
        // that are passed matter, and they are not the same.
        args: Array<String>,
        expectedToFail: Boolean,
        expectedFailureMessage: String?,
        reporterEnvironment: ReporterEnvironment,
        testEnvironment: TestEnvironment,
    ): String {
        // Capture the actual input and output from System.out/err and compare it to the output
        // printed through the official writer; they should be the same, otherwise we have stray
        // print calls littered in the code!
        val previousOut = System.out
        val previousErr = System.err
        try {
            val output = TeeWriter(previousOut)
            System.setOut(PrintStream(output))
            val error = TeeWriter(previousErr)
            System.setErr(PrintStream(error))

            val sw = StringWriter()
            val writer = PrintWriter(sw)

            Disposer.setDebugMode(true)

            val executionEnvironment =
                ExecutionEnvironment(
                    stdout = writer,
                    stderr = writer,
                    reporterEnvironment = reporterEnvironment,
                    testEnvironment = testEnvironment,
                )
            val exitCode = Driver.run(executionEnvironment, args)
            if (exitCode == 0) {
                if (expectedToFail) {
                    val message =
                        expectedFailureMessage?.let {
                            "expected to fail with following message but did not:\n${expectedFailureMessage.prependIndent("    ")}"
                        } ?: "expected to fail but did not"
                    errorCollector.addError(AssertionError(message))
                }
            } else {
                val actualFailureMessage = removeTestSpecificDirectories(sw.toString()).trim()
                if (expectedToFail) {
                    if (
                        expectedFailureMessage != null &&
                            expectedFailureMessage != actualFailureMessage
                    ) {
                        // If the failure was unexpected then report an error but carry on so that
                        // other checks can be performed.
                        val failure =
                            ComparisonFailure(
                                "expectedFailure mismatch",
                                expectedFailureMessage,
                                actualFailureMessage,
                            )
                        errorCollector.addError(failure)
                    }
                } else {
                    val message =
                        "did not expect it to fail but it failed with the following message:\n${actualFailureMessage.prependIndent("    ")}"
                    errorCollector.addError(AssertionError(message))
                }
            }

            val stdout = output.toString(UTF_8.name())
            if (stdout.isNotEmpty()) {
                addError("Unexpected write to stdout:\n $stdout")
            }
            val stderr = error.toString(UTF_8.name())
            if (stderr.isNotEmpty()) {
                addError("Unexpected write to stderr:\n $stderr")
            }

            val printedOutput = sw.toString()
            if (printedOutput.isNotEmpty() && printedOutput.trim().isEmpty()) {
                fail("Printed newlines with nothing else")
            }

            /* TODO(b/477826713): Temporarily disable this while the problem is fixed.
            UastEnvironment.checkApplicationEnvironmentDisposed()
            Disposer.assertIsEmpty(true)
             */

            return printedOutput
        } finally {
            System.setOut(previousOut)
            System.setErr(previousErr)
        }
    }

    // This is here, so we can keep a record of what was printed, to make sure we don't have any
    // unexpected print calls in the source that are left behind after debugging and pollute the
    // production output
    class TeeWriter(private val otherStream: PrintStream) : ByteArrayOutputStream() {
        override fun write(b: ByteArray, off: Int, len: Int) {
            otherStream.write(b, off, len)
            super.write(b, off, len)
        }

        override fun write(b: ByteArray) {
            otherStream.write(b)
            super.write(b)
        }

        override fun write(b: Int) {
            otherStream.write(b)
            super.write(b)
        }
    }

    private fun <T> buildOptionalArgs(option: T?, converter: (T) -> Array<String>): Array<String> {
        return if (option != null) {
            converter(option)
        } else {
            emptyArray()
        }
    }

    /** Test information related to a baseline file. */
    data class BaselineTestInfo(
        /**
         * The contents of the input baseline.
         *
         * If this is `null` then no baseline testing is performed.
         */
        val inputContents: String? = null,

        /** The contents of the expected updated baseline. */
        val expectedOutputContents: String? = null,

        /** Indicates whether testing of the baseline should suppress reporting of issues or not. */
        val silentUpdate: Boolean = true,
    ) {
        init {
            if (inputContents == null && expectedOutputContents != null) {
                error("`inputContents` must be non-null as `expectedOutputContents` is non-null")
            }
        }
    }

    /** Represents a check that can be performed on a baseline file. */
    @Suppress("ArrayInDataClass")
    private data class BaselineCheck(
        /** The option for the input baseline, used in test failure messages. */
        val baselineOption: String,

        /** The args to pass to metalava. */
        val args: Array<String>,

        /**
         * The input/output file.
         *
         * If this is `null` then no check is performed.
         */
        val file: File?,

        /** The expected contents of [file]. */
        val expectedFileContents: String,
    ) {
        /** Apply the baseline check. */
        fun apply() {
            file ?: return

            assertTrue(
                "${file.path} does not exist even though $baselineOption was used",
                file.exists()
            )

            val actualText = readFileFilterBlankLines(file)
            assertEquals(
                stripComments(
                    expectedFileContents.trimIndent(),
                    DOT_TXT,
                    stripLineComments = false
                ),
                actualText
            )
        }
    }

    private fun buildBaselineCheck(
        baselineOption: String,
        updateBaselineOption: String,
        filename: String,
        info: BaselineTestInfo,
    ): BaselineCheck {
        return info.inputContents?.let { inputContents ->
            val baselineFile = temporaryFolder.newFile(filename)
            baselineFile?.writeText(inputContents.trimIndent())
            val args = arrayOf(baselineOption, baselineFile.path)

            info.expectedOutputContents?.let { expectedOutputContents ->
                // If silent update is request then use the same baseline file for update as for the
                // input, otherwise create a separate update file.
                val updateFile =
                    if (info.silentUpdate) baselineFile
                    else temporaryFolder.newFile("update-$filename")

                // As expected output contents are provided add extra arguments to output the
                // baseline and then compare the baseline file against the expected output. Use the
                // update baseline option in any error messages.
                BaselineCheck(
                    updateBaselineOption,
                    args + arrayOf(updateBaselineOption, updateFile.path),
                    updateFile,
                    expectedOutputContents,
                )
            }
                ?:
                // As no expected output is provided then compare the baseline file against the
                // supplied input contents to make sure that they have not changed. Use the
                // basic baseline option in any error messages.
                BaselineCheck(
                    baselineOption,
                    args,
                    baselineFile,
                    inputContents,
                )
        } ?: BaselineCheck("", emptyArray(), null, "")
    }

    protected fun check(
        configFiles: Array<TestFile> = emptyArray(),
        /** Any jars to add to the class path */
        classpath: Array<TestFile>? = null,
        /** The API signature content (corresponds to --api) */
        @Language("TEXT") expectedApiSignature: String? = null,
        /** The removed API (corresponds to --removed-api) */
        removedApi: String? = null,
        /** Expected stubs (corresponds to --stubs) */
        expectedStubFiles: Array<TestFile> = emptyArray(),
        /**
         * Whether to ignore parameter names when comparing stub files. Should only be true when
         * generating stubs from signature files.
         */
        ignoreParameterNamesInStubFiles: Boolean = false,
        /** Expected paths of stub files created */
        stubPaths: Array<String>? = null,
        /**
         * Whether the stubs should be written as documentation stubs instead of plain stubs.
         * Decides whether the stubs include [ApiVariantType.DOC_ONLY] items, uses
         * rewritten/migration annotations, etc
         */
        docStubs: Boolean = false,
        /** Signature file format */
        format: FileFormat = FileFormat.V5,
        /**
         * All expected issues to be generated when analyzing these sources.
         *
         * If this contains an issue of severity error then this will expect the command to fail but
         * will not check the actual failure message unless a non-empty [expectedFail] is provided.
         *
         * @see expectedFail
         */
        expectedIssues: String? = "",
        /** Expected [Severity.ERROR] issues to be generated when analyzing these sources */
        errorSeverityExpectedIssues: String? = null,

        /** A list of [CompilationCheck]s to perform with the generated stubs. */
        compilationChecks: List<CompilationCheck>? = null,

        /**
         * If `true` then stubs will be generated and then compiled to make sure that they are valid
         * java.
         *
         * Defaults to `false` if [compilationChecks] is not specified or is empty.
         */
        checkCompilation: Boolean = compilationChecks?.isNotEmpty() == true,

        /** Annotations to merge in (in .xml format) */
        @Language("XML") mergeXmlAnnotations: String? = null,
        /** Annotations to merge in (in .txt/.signature format) */
        @Language("TEXT") mergeSignatureAnnotations: String? = null,
        /** Qualifier annotations to merge in (in Java stub format) */
        @Language("JAVA") mergeJavaStubAnnotations: String? = null,
        /** Inclusion annotations to merge in (in Java stub format) */
        mergeInclusionAnnotations: Array<TestFile> = emptyArray(),
        /** Optional API signature files content to load **instead** of Java/Kotlin source files */
        @Language("TEXT") signatureSources: Array<String> = emptyArray(),
        /**
         * An optional API signature file content to load **instead** of Java/Kotlin source files.
         * This is added to [signatureSources]. This argument exists for backward compatibility.
         */
        @Language("TEXT") signatureSource: String? = null,
        /** An optional API jar file content to load **instead** of Java/Kotlin source files */
        apiJar: File? = null,
        /**
         * An optional API signature to check the last released API's compatibility with.
         *
         * This can either be the name of a file or the contents of the signature file. In the
         * latter case the contents are adjusted to make sure it is a valid signature file with a
         * valid header and written to a file.
         */
        @Language("TEXT") checkCompatibilityApiReleased: String? = null,
        /**
         * Allow specifying multiple instances of [checkCompatibilityApiReleased].
         *
         * In order from narrowest to widest API.
         */
        checkCompatibilityApiReleasedList: List<String> = emptyList(),
        /**
         * An optional API signature to check the last released removed API's compatibility with.
         *
         * See [checkCompatibilityApiReleased].
         */
        @Language("TEXT") checkCompatibilityRemovedApiReleased: String? = null,
        /**
         * Allow specifying multiple instances of [checkCompatibilityRemovedApiReleased].
         *
         * In order from narrowest to widest API.
         */
        checkCompatibilityRemovedApiReleasedList: List<String> = emptyList(),
        @Language("TEXT") migrateNullsApi: String? = null,
        migrateNullsApiList: List<String> = listOfNotNull(migrateNullsApi),
        /** An optional Proguard keep file to generate */
        @Language("Proguard") proguard: String? = null,
        /** Show annotations (--show-annotation arguments) */
        showAnnotations: Array<String> = emptyArray(),
        /** Hide annotations (--hide-annotation arguments) */
        hideAnnotations: Array<String> = emptyArray(),
        /** API Compatibility important annotations (--api-compat-annotation) */
        apiCompatAnnotations: Set<String> = emptySet(),
        /** No compat check meta-annotations (--no-compat-check-meta-annotation arguments) */
        suppressCompatibilityMetaAnnotations: Array<String> = emptyArray(),
        /** If using [showAnnotations], whether to include unannotated */
        showUnannotated: Boolean = false,
        /** Additional arguments to supply */
        extraArguments: Array<out String> = emptyArray(),
        /** Expected output (stdout and stderr combined). If null, don't check. */
        expectedOutput: String? = null,
        /**
         * Expected fail message and state, if any.
         *
         * If this is set to a non-empty string then this will expect the command to fail with that
         * exact message (after [removeTestSpecificDirectories] is called on it).
         *
         * This only needs to be set by tests that actually care about the failure message that is
         * output. Otherwise, leaving this unset will not check the failure message.
         *
         * @see expectedIssues
         */
        expectedFail: String? = null,
        /** Optional manifest to load and associate with the codebase */
        @Language("XML") manifest: String? = null,
        /**
         * Packages to pre-import (these will therefore NOT be included in emitted stubs, signature
         * files etc
         */
        importedPackages: List<String> = emptyList(),
        /** See [TestEnvironment.skipEmitPackages], defaults to [DEFAULT_SKIP_EMIT_PACKAGES]. */
        skipEmitPackages: List<String>? = null,
        /** Optional test surface to use. */
        apiSurface: KnownApiSurface? = null,
        /** Whether we should warn about super classes that are stripped because they are hidden */
        includeStrippedSuperclassWarnings: Boolean = false,
        /**
         * Apply level to XML.
         *
         * This can either be the name of a file or the contents of the XML file. In the latter case
         * the contents are trimmed and written to a file.
         */
        applyApiLevelsXml: String? = null,
        /** Corresponds to SDK constants file broadcast_actions.txt */
        sdkBroadcastActions: String? = null,
        /** Corresponds to SDK constants file activity_actions.txt */
        sdkActivityActions: String? = null,
        /** Corresponds to SDK constants file service_actions.txt */
        sdkServiceActions: String? = null,
        /** Corresponds to SDK constants file categories.txt */
        sdkCategories: String? = null,
        /** Corresponds to SDK constants file features.txt */
        sdkFeatures: String? = null,
        /** Corresponds to SDK constants file widgets.txt */
        sdkWidgets: String? = null,
        /**
         * Extract annotations and check that the given packages contain the given extracted XML
         * files
         */
        extractAnnotations: Map<String, String>? = null,
        /**
         * Creates the nullability annotations validator, and check that the report has the given
         * lines (does not define files to be validated)
         */
        validateNullability: Set<String>? = null,
        /** Enable nullability validation for the listed classes */
        validateNullabilityFromList: String? = null,
        /** Hook for performing additional initialization of the project directory */
        projectSetup: ((File) -> Unit)? = null,
        /** [ARG_BASELINE] and [ARG_UPDATE_BASELINE] */
        baselineTestInfo: BaselineTestInfo = BaselineTestInfo(),
        /** [ARG_BASELINE_API_LINT] and [ARG_UPDATE_BASELINE_API_LINT] */
        baselineApiLintTestInfo: BaselineTestInfo = BaselineTestInfo(),
        /**
         * [ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED] and
         * [ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED]
         */
        baselineCheckCompatibilityReleasedTestInfo: BaselineTestInfo = BaselineTestInfo(),

        /** [ARG_ERROR_MESSAGE_API_LINT] */
        errorMessageApiLint: String? = null,
        /** [ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED] */
        errorMessageCheckCompatibilityReleased: String? = null,

        /**
         * If non-null, enable API lint. If non-blank, a codebase where only new APIs not in the
         * codebase are linted.
         */
        @Language("TEXT") apiLint: String? = null,
        /** The source files to pass to the analyzer */
        sourceFiles: Array<TestFile> = emptyArray(),
        /**
         * Additional source files that will be available on the source path but not included in the
         * source files passed on the command line.
         */
        additionalSourcePathFiles: Array<TestFile> = emptyArray(),
        /** Lint project description */
        projectDescription: TestFile? = null,
        /** Jar file with the compiled sources loaded in addition to [sourceFiles]. */
        compiledSourceJar: TestFile? = null,
        /** [ARG_REPEAT_ERRORS_MAX] */
        repeatErrorsMax: Int = 0,
        /** Whether to create a multiplatform codebase. Only supported with psi. */
        enableMultiplatform: Boolean = false,
        /**
         * A map from expected multiplatform API file name to contents. There should be one for each
         * source set with an expected signature file.
         */
        multiplatformApi: Map<String, String> = emptyMap(),
        /** Signature files to parse as source into a MultiplatformCodebase. */
        multiplatformSignatureSource: List<TestFile> = emptyList(),
        /**
         * If true, this does not include arguments specifying source files (from [sourceFiles]) in
         * the command run by Driver. This allows creating a multiplatform codebase (when
         * [enableMultiplatform] is true) without creating a regular codebase.
         */
        skipSourceArgs: Boolean = false,
        /** Signature files to parse into a MultiplatformCodebase for compatibility checks. */
        multiplatformCompatibilityApi: List<TestFile>? = null,
        /** Whether tracing should be enabled */
        enableTracing: Boolean = false,
        /**
         * Language level of the Java source files. If not specified
         * [com.android.tools.metalava.model.source.DEFAULT_JAVA_LANGUAGE_LEVEL] is used.
         */
        javaLanguageLevel: String? = null,
        /**
         * Called on a [CheckerContext] after the analysis phase in the metalava main command.
         *
         * This allows testing of the internal state of the metalava main command. Ideally, tests
         * should not use this as it makes the tests more fragile and can increase the cost of
         * refactoring. However, it is often the only way to verify the effects of changes that
         * start to add a new feature but which does not yet have any effect on the output.
         */
        postAnalysisChecker: CheckerFunction? = null,
    ) {
        // Ensure different API clients don't interfere with each other
        try {
            val method = ApiLookup::class.java.getDeclaredMethod("dispose")
            method.isAccessible = true
            method.invoke(null)
        } catch (ignore: Throwable) {
            ignore.printStackTrace()
        }

        // Ensure that lint infrastructure (for UAST) knows it's dealing with a test
        LintCliClient(LintClient.CLIENT_UNIT_TESTS)

        // Add any additional source files from the apiSurface.
        val allSourceFiles =
            apiSurface?.let { apiSurface -> sourceFiles + apiSurface.additionalSourceFiles }
                ?: sourceFiles

        // Verify that a test that provided kotlin code is only being run against a provider that
        // supports kotlin code.
        val anyKotlin = allSourceFiles.any { it.targetPath.endsWith(DOT_KT) }
        if (anyKotlin) {
            if (Capability.KOTLIN !in codebaseCreatorConfig.creator.capabilities) {
                error(
                    "Provider ${codebaseCreatorConfig.providerName} does not support Kotlin; please add `@RequiresCapabilities(Capability.KOTLIN)` to the test"
                )
            }
            if (format.version < FileFormat.Version.V4) {
                error("Kotlin test must use FileFormat.V4 or higher")
            }
        }

        val releasedApiCheck =
            CompatibilityCheckRequest.create(
                optionName = ARG_CHECK_COMPATIBILITY_API_RELEASED,
                fileOrSignatureContents = checkCompatibilityApiReleased,
                fileOrSignatureContentsList = checkCompatibilityApiReleasedList,
                newBasename = "released-api.txt",
            )
        val releasedRemovedApiCheck =
            CompatibilityCheckRequest.create(
                optionName = ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED,
                fileOrSignatureContents = checkCompatibilityRemovedApiReleased,
                fileOrSignatureContentsList = checkCompatibilityRemovedApiReleasedList,
                newBasename = "removed-released-api.txt",
            )

        // This is expected to fail if the expectedIssues contains an error issue or expectedFail
        // is not null and not empty.
        val expectedToFail = expectedIssues.containsErrorIssue() || !expectedFail.isNullOrEmpty()

        // Get the expected failure message.
        val expectedFailureMessage = expectedFail?.trimIndent()

        // Unit test which checks that a signature file is as expected
        val androidJar = getAndroidJar()

        // Create the main project directory containing the source files.
        val projectDir = createProjectDir(allSourceFiles)

        val sourcePathDir = File(projectDir, "src")
        if (!sourcePathDir.isDirectory) {
            sourcePathDir.mkdirs()
        }

        var sourcePath = sourcePathDir.path

        // Make it easy to configure a source path with more than one source root: src and src2
        if (allSourceFiles.any { it.targetPath.startsWith("src2") }) {
            sourcePath = sourcePath + File.pathSeparator + sourcePath + "2"
        }

        // Add any additional sources onto the source path.
        if (additionalSourcePathFiles.isNotEmpty()) {
            // Get the directory for the folder.
            val dir = getOrCreateFolder("extra-source-files")

            // Create the files. Note, that Java files are created in a `src` subdirectory of the
            // dir passed in to createFiles(File).
            additionalSourcePathFiles.createFiles(dir)

            // Create a file for the `src` subdirectory.
            val srcDir = dir.resolve("src")

            // Add a label for it.
            temporaryFolder.addTestLabelForFile(srcDir, "ADDITIONAL-SOURCE-PATH")

            // Add it to the source path.
            sourcePath = sourcePath + File.pathSeparator + srcDir
        }

        fun pathUnderProject(path: String): String = File(projectDir, path).path

        val projectDescriptionFile = projectDescription?.createFile(projectDir)

        val compiledSourceJarFile = compiledSourceJar?.createFile(projectDir)
        if (
            compiledSourceJarFile != null &&
                Capability.JAR_WITH_SOURCES !in codebaseCreatorConfig.creator.capabilities
        ) {
            error(
                "Provider ${codebaseCreatorConfig.providerName} does not support compiled " +
                    "sources; please add `@RequiresCapabilities(Capability.JAR_WITH_SOURCES)` to " +
                    "the test"
            )
        }

        val sourceList =
            if (signatureSources.isNotEmpty() || signatureSource != null) {
                sourcePathDir.mkdirs()

                // if signatureSource is set, add it to signatureSources.
                val sources = signatureSources.toMutableList()
                signatureSource?.let { sources.add(it) }

                var num = 0
                val args = mutableListOf<String>()
                sources.forEach { file ->
                    val signatureFile =
                        File(projectDir, "load-api${ if (++num == 1) "" else num.toString() }.txt")
                    signatureFile.writeSignatureText(file)
                    args.add(signatureFile.path)
                }
                if (!includeStrippedSuperclassWarnings) {
                    args.addAll(hiddenIssues(Issues.HIDDEN_SUPERCLASS)) // Suppress warning #111
                }
                args.toTypedArray()
            } else if (apiJar != null) {
                sourcePathDir.mkdirs()
                assert(allSourceFiles.isEmpty()) {
                    "Shouldn't combine sources with API jar file loads"
                }
                arrayOf(apiJar.path)
            } else {
                allSourceFiles
                    .asSequence()
                    .map { pathUnderProject(it.targetPath) }
                    .toList()
                    .toTypedArray()
            }

        val classpathArgs: Array<String> =
            if (classpath != null) {
                val classpathString =
                    classpath
                        .map { it.createFile(projectDir) }
                        .map { it.path }
                        .joinToString(separator = File.pathSeparator) { it }

                arrayOf(ARG_CLASS_PATH, classpathString)
            } else {
                emptyArray()
            }

        val allReportedIssues = StringBuilder()
        val errorSeverityReportedIssues = StringBuilder()
        val reporterEnvironment =
            object : ReporterEnvironment {
                override val rootFolder = projectDir

                override fun printReport(message: String, severity: Severity) {
                    val cleanedUpMessage = removeTestSpecificDirectories(message).trim()
                    if (severity == Severity.ERROR) {
                        errorSeverityReportedIssues.append(cleanedUpMessage).append('\n')
                    }
                    allReportedIssues.append(cleanedUpMessage).append('\n')
                }
            }

        val configFileArgs =
            configFiles
                .flatMap { listOf(ARG_CONFIG_FILE, it.indented().createFile(projectDir).path) }
                .toTypedArray()

        val mergeAnnotationsArgs =
            if (mergeXmlAnnotations != null) {
                val merged = File(projectDir, "merged-annotations.xml")
                merged.writeText(mergeXmlAnnotations.trimIndent())
                arrayOf(ARG_MERGE_QUALIFIER_ANNOTATIONS, merged.path)
            } else {
                emptyArray()
            }

        val signatureAnnotationsArgs =
            if (mergeSignatureAnnotations != null) {
                val merged = File(projectDir, "merged-annotations.txt")
                merged.writeText(mergeSignatureAnnotations.trimIndent())
                arrayOf(ARG_MERGE_QUALIFIER_ANNOTATIONS, merged.path)
            } else {
                emptyArray()
            }

        val javaStubAnnotationsArgs =
            if (mergeJavaStubAnnotations != null) {
                // We need to place the qualifier class into its proper package location
                // to make the parsing machinery happy
                val cls = ClassName(mergeJavaStubAnnotations)
                val pkg = cls.packageName
                val relative = pkg?.replace('.', File.separatorChar) ?: "."
                val merged = File(projectDir, "qualifier/$relative/${cls.className}.java")
                merged.parentFile.mkdirs()
                merged.writeText(mergeJavaStubAnnotations.trimIndent())
                arrayOf(ARG_MERGE_QUALIFIER_ANNOTATIONS, merged.path)
            } else {
                emptyArray()
            }

        val inclusionAnnotationsArgs =
            if (mergeInclusionAnnotations.isNotEmpty()) {
                // Create each file in their own directory.
                mergeInclusionAnnotations
                    .flatMapIndexed { i, testFile ->
                        val suffix = if (i == 0) "" else i.toString()
                        val targetDir = File(projectDir, "inclusion$suffix")
                        targetDir.mkdirs()
                        testFile.createFile(targetDir)
                        listOf(ARG_MERGE_INCLUSION_ANNOTATIONS, targetDir.path)
                    }
                    .toTypedArray()
            } else {
                emptyArray()
            }

        val apiLintArgs =
            if (apiLint != null) {
                if (apiLint.isBlank()) {
                    arrayOf(ARG_API_LINT)
                } else {
                    val file = File(projectDir, "prev-api-lint.txt")
                    file.writeSignatureText(apiLint)
                    arrayOf(ARG_API_LINT, ARG_API_LINT_PREVIOUS_API, file.path)
                }
            } else {
                emptyArray()
            }

        val manifestFileArgs =
            if (manifest != null) {
                val file = File(projectDir, "manifest.xml")
                file.writeText(manifest.trimIndent())
                arrayOf(ARG_MANIFEST, file.path)
            } else {
                emptyArray()
            }

        val migrateNullsArguments =
            migrateNullsApiList.contentOrPathListToArgsArray(
                projectDir,
                "stable-api.txt",
                ARG_MIGRATE_NULLNESS
            )

        val apiCompatAnnotationArguments =
            if (apiCompatAnnotations.isNotEmpty()) {
                val args = mutableListOf<String>()
                for (annotation in apiCompatAnnotations) {
                    args.add(ARG_API_COMPAT_ANNOTATION)
                    args.add(annotation)
                }
                args.toTypedArray()
            } else {
                emptyArray()
            }

        val quiet =
            if (expectedOutput != null && !extraArguments.contains(ARG_VERBOSE)) {
                // If comparing output, avoid noisy output such as the banner etc
                arrayOf(ARG_QUIET)
            } else {
                emptyArray()
            }

        var proguardFile: File? = null
        val proguardKeepArguments =
            if (proguard != null) {
                proguardFile = File(projectDir, "proguard.cfg")
                arrayOf(ARG_PROGUARD, proguardFile.path)
            } else {
                emptyArray()
            }

        val apiSurfaceArguments =
            if (apiSurface != null) {
                listOf(
                        ARG_API_SURFACE,
                        apiSurface.surface,
                        ARG_CONFIG_FILE,
                        apiSurface.configFile.createFile(projectDir).path,
                    )
                    .toTypedArray()
            } else {
                emptyArray()
            }

        val showAnnotationArguments =
            if (showAnnotations.isNotEmpty()) {
                buildList {
                        for (annotation in showAnnotations) {
                            add(ARG_SHOW_ANNOTATION)
                            add(annotation)
                        }
                    }
                    .toTypedArray()
            } else {
                emptyArray()
            }

        val hideAnnotationArguments =
            if (hideAnnotations.isNotEmpty()) {
                val args = mutableListOf<String>()
                for (annotation in hideAnnotations) {
                    args.add(ARG_HIDE_ANNOTATION)
                    args.add(annotation)
                }
                args.toTypedArray()
            } else {
                emptyArray()
            }

        val suppressCompatMetaAnnotationArguments =
            if (suppressCompatibilityMetaAnnotations.isNotEmpty()) {
                val args = mutableListOf<String>()
                for (annotation in suppressCompatibilityMetaAnnotations) {
                    args.add(ARG_SUPPRESS_COMPATIBILITY_META_ANNOTATION)
                    args.add(annotation)
                }
                args.toTypedArray()
            } else {
                emptyArray()
            }

        val showUnannotatedArgs =
            if (showUnannotated) {
                arrayOf(ARG_SHOW_UNANNOTATED)
            } else {
                emptyArray()
            }

        var removedApiFile: File? = null
        val removedArgs =
            if (removedApi != null) {
                removedApiFile = temporaryFolder.newFile("removed.txt")
                arrayOf(ARG_REMOVED_API, removedApiFile.path)
            } else {
                emptyArray()
            }

        // Always pass apiArgs and generate API text file in runDriver
        val apiFile: File = getOrCreateFile("public-api.txt")
        val apiArgs = arrayOf(ARG_API, apiFile.path)

        var stubsDir: File? = null
        val stubsArgs =
            if (expectedStubFiles.isNotEmpty() || stubPaths != null || checkCompilation) {
                stubsDir = getOrCreateFolder("stubs", testLabel = "STUBS")
                if (docStubs) {
                    arrayOf(ARG_DOC_STUBS, stubsDir.path)
                } else {
                    arrayOf(ARG_STUBS, stubsDir.path)
                }
            } else {
                emptyArray()
            }

        val applyApiLevelsXmlArgs =
            if (applyApiLevelsXml != null) {
                ApiLookup::class
                    .java
                    .getDeclaredMethod("dispose")
                    .apply { isAccessible = true }
                    .invoke(null)
                val applyApiLevelsXmlFile =
                    useExistingFileOrCreateNewFile(
                        projectDir,
                        applyApiLevelsXml,
                        "api-versions.xml"
                    ) {
                        it.trimIndent()
                    }
                arrayOf(ARG_APPLY_API_LEVELS, applyApiLevelsXmlFile.path)
            } else {
                emptyArray()
            }

        val baselineCheck =
            buildBaselineCheck(
                ARG_BASELINE,
                ARG_UPDATE_BASELINE,
                "baseline.txt",
                baselineTestInfo,
            )
        val baselineApiLintCheck =
            buildBaselineCheck(
                ARG_BASELINE_API_LINT,
                ARG_UPDATE_BASELINE_API_LINT,
                "baseline-api-lint.txt",
                baselineApiLintTestInfo,
            )
        val baselineCheckCompatibilityReleasedCheck =
            buildBaselineCheck(
                ARG_BASELINE_CHECK_COMPATIBILITY_RELEASED,
                ARG_UPDATE_BASELINE_CHECK_COMPATIBILITY_RELEASED,
                "baseline-check-released.txt",
                baselineCheckCompatibilityReleasedTestInfo,
            )

        val importedPackageArgs = mutableListOf<String>()
        importedPackages.forEach {
            importedPackageArgs.add("--stub-import-packages")
            importedPackageArgs.add(it)
        }

        val kotlinPathArgs = findKotlinStdlibPathArgs(sourceList)

        val sdkFilesDir: File?
        val sdkFilesArgs: Array<String>
        if (
            sdkBroadcastActions != null ||
                sdkActivityActions != null ||
                sdkServiceActions != null ||
                sdkCategories != null ||
                sdkFeatures != null ||
                sdkWidgets != null
        ) {
            val dir = File(projectDir, "sdk-files")
            sdkFilesArgs = arrayOf(ARG_SDK_VALUES, dir.path)
            sdkFilesDir = dir
        } else {
            sdkFilesArgs = emptyArray()
            sdkFilesDir = null
        }

        val extractedAnnotationsZip: File?
        val extractAnnotationsArgs =
            if (extractAnnotations != null) {
                extractedAnnotationsZip = temporaryFolder.newFile("extracted-annotations.zip")
                arrayOf(ARG_EXTRACT_ANNOTATIONS, extractedAnnotationsZip.path)
            } else {
                extractedAnnotationsZip = null
                emptyArray()
            }

        val validateNullabilityTxt: File?
        val validateNullabilityArgs =
            if (validateNullability != null) {
                validateNullabilityTxt = temporaryFolder.newFile("validate-nullability.txt")
                arrayOf(
                    ARG_NULLABILITY_WARNINGS_TXT,
                    validateNullabilityTxt.path,
                    ARG_NULLABILITY_ERRORS_NON_FATAL // for testing, report on errors instead of
                    // throwing
                )
            } else {
                validateNullabilityTxt = null
                emptyArray()
            }
        val validateNullabilityFromListFile: File?
        val validateNullabilityFromListArgs =
            if (validateNullabilityFromList != null) {
                validateNullabilityFromListFile =
                    temporaryFolder.newFile("validate-nullability-classes.txt")
                validateNullabilityFromListFile.writeText(validateNullabilityFromList)
                arrayOf(ARG_VALIDATE_NULLABILITY_FROM_LIST, validateNullabilityFromListFile.path)
            } else {
                emptyArray()
            }

        val errorMessageApiLintArgs =
            buildOptionalArgs(errorMessageApiLint) { arrayOf(ARG_ERROR_MESSAGE_API_LINT, it) }
        val errorMessageCheckCompatibilityReleasedArgs =
            buildOptionalArgs(errorMessageCheckCompatibilityReleased) {
                arrayOf(ARG_ERROR_MESSAGE_CHECK_COMPATIBILITY_RELEASED, it)
            }

        val repeatErrorsMaxArgs =
            if (repeatErrorsMax > 0) {
                arrayOf(ARG_REPEAT_ERRORS_MAX, repeatErrorsMax.toString())
            } else {
                emptyArray()
            }

        val multiplatformOptions =
            if (enableMultiplatform) {
                if (Capability.MULTIPLATFORM !in codebaseCreatorConfig.creator.capabilities) {
                    error(
                        "Provider ${codebaseCreatorConfig.providerName} does not support KMP; please add `@RequiresCapabilities(Capability.MULTIPLATFORM)` to the test"
                    )
                }
                arrayOf(ARG_MULTIPLATFORM_ENABLED)
            } else {
                emptyArray()
            }

        val multiplatformSignatureSourceOptions =
            if (multiplatformSignatureSource.isNotEmpty()) {
                // Create each multiplatform signature file in a new subdirectory.
                val multiplatformSignatureSourceDirectory =
                    File(projectDir, "multiplatform-signature-source")
                for (file in multiplatformSignatureSource) {
                    file.createFile(multiplatformSignatureSourceDirectory)
                }
                arrayOf(ARG_MULTIPLATFORM_API_SOURCES, multiplatformSignatureSourceDirectory.path)
            } else {
                emptyArray()
            }

        // Generate multiplatform API files if specified.
        var multiplatformApiDirectory: File? = null
        val multiplatformApiArgs =
            if (multiplatformApi.isNotEmpty()) {
                multiplatformApiDirectory = getOrCreateFolder("multiplatform-api")
                arrayOf(ARG_MULTIPLATFORM_API_DIR, multiplatformApiDirectory.path)
            } else {
                emptyArray()
            }

        // Run multiplatform compatibility checks if requested.
        val multiplatformCompatibilityApiDirectory: File?
        val multiplatformCompatibilityArgs =
            if (multiplatformCompatibilityApi != null) {
                multiplatformCompatibilityApiDirectory =
                    getOrCreateFolder("multiplatform-compatibility-api", "MULTIPLATFORM-API-DIR")
                for (file in multiplatformCompatibilityApi) {
                    file.createFile(multiplatformCompatibilityApiDirectory)
                }
                arrayOf(
                    ARG_MULTIPLATFORM_CHECK_COMPATIBILITY,
                    multiplatformCompatibilityApiDirectory.path
                )
            } else {
                emptyArray()
            }

        val traceFile: File?
        val tracingArguments =
            if (enableTracing) {
                traceFile = File(projectDir, "trace.perfetto-trace")
                arrayOf(ARG_TRACE_FILE, traceFile.path)
            } else {
                traceFile = null
                emptyArray()
            }

        // Run optional additional setup steps on the project directory
        projectSetup?.invoke(projectDir)

        val sourceArgs =
            if (skipSourceArgs) {
                emptyArray()
            } else {
                arrayOf(
                    ARG_SOURCE_PATH,
                    sourcePath,
                    *sourceList,
                )
            }

        val languageLevelArgs =
            if (javaLanguageLevel != null) {
                arrayOf(ARG_JAVA_SOURCE, javaLanguageLevel)
            } else {
                emptyArray()
            }

        val args =
            arrayOf(
                // Common options.
                ARG_NO_COLOR,
                *quiet,
                *tracingArguments,

                // The sub-command to run.
                "main",

                // Annotation generation temporarily turned off by default while integrating with
                // SDK builds; tests need these
                ARG_INCLUDE_ANNOTATIONS,
                *sourceArgs,
                *configFileArgs,
                *removedArgs,
                *apiArgs,
                *stubsArgs,
                *mergeAnnotationsArgs,
                *signatureAnnotationsArgs,
                *javaStubAnnotationsArgs,
                *inclusionAnnotationsArgs,
                *migrateNullsArguments,
                *releasedApiCheck.arguments(projectDir),
                *releasedRemovedApiCheck.arguments(projectDir),
                *proguardKeepArguments,
                *manifestFileArgs,
                *applyApiLevelsXmlArgs,
                *baselineCheck.args,
                *baselineApiLintCheck.args,
                *baselineCheckCompatibilityReleasedCheck.args,
                *apiCompatAnnotationArguments,
                *apiSurfaceArguments,
                *showAnnotationArguments,
                *hideAnnotationArguments,
                *suppressCompatMetaAnnotationArguments,
                *showUnannotatedArgs,
                *sdkFilesArgs,
                *importedPackageArgs.toTypedArray(),
                *extractAnnotationsArgs,
                *validateNullabilityArgs,
                *validateNullabilityFromListArgs,
                format.outputFlags(),
                *extraArguments,
                *apiLintArgs,
                *errorMessageApiLintArgs,
                *errorMessageCheckCompatibilityReleasedArgs,
                *repeatErrorsMaxArgs,
                *multiplatformOptions,
                *multiplatformApiArgs,
                *multiplatformSignatureSourceOptions,
                *multiplatformCompatibilityArgs,
                *languageLevelArgs,
            ) +
                buildList {
                        if (projectDescriptionFile != null) {
                            // Classpath isn't needed when it is specified through the project xml
                            add(ARG_PROJECT)
                            add(projectDescriptionFile.absolutePath)
                            // When project description is provided,
                            // skip listing (common) sources
                        } else {
                            add(ARG_CLASS_PATH)
                            add(androidJar.path)
                            addAll(classpathArgs)
                            addAll(kotlinPathArgs)
                        }
                        if (compiledSourceJarFile != null) {
                            add(ARG_COMPILED_SOURCES)
                            add(compiledSourceJarFile.absolutePath)
                        }
                    }
                    .toTypedArray()

        val testEnvironment =
            TestEnvironment(
                skipEmitPackages = skipEmitPackages ?: DEFAULT_SKIP_EMIT_PACKAGES,
                sourceModelProvider = codebaseCreatorConfig.creator,
                modelOptions = codebaseCreatorConfig.modelOptions,
                postAnalysisChecker = postAnalysisChecker,
            )

        val actualOutput =
            runDriver(
                args = args,
                expectedToFail = expectedToFail,
                expectedFailureMessage = expectedFailureMessage,
                reporterEnvironment = reporterEnvironment,
                testEnvironment = testEnvironment,
            )

        if (expectedIssues != null || allReportedIssues.toString() != "") {
            assertEquals(
                "expectedIssues does not match actual issues reported",
                expectedIssues?.trimIndent()?.trim() ?: "",
                allReportedIssues.toString().trim(),
            )
        }
        if (errorSeverityExpectedIssues != null) {
            assertEquals(
                "errorSeverityExpectedIssues does not match actual issues reported",
                errorSeverityExpectedIssues.trimIndent().trim(),
                errorSeverityReportedIssues.toString().trim(),
            )
        }

        if (expectedOutput != null) {
            assertEquals(
                "expectedOutput does not match actual output",
                expectedOutput.trimIndent().trim(),
                actualOutput.trim()
            )
        }

        if (expectedApiSignature != null) {
            assertTrue(
                "${apiFile.path} does not exist even though --api was used",
                apiFile.exists()
            )
            assertSignatureFilesMatch(
                expectedApiSignature,
                apiFile.readText(),
                expectedFormat = format
            )
            // Make sure we can read back the files we write
            ApiFile.parseApi(SignatureFile.fromFiles(apiFile), Codebase.Config.NOOP)
        }

        baselineCheck.apply()
        baselineApiLintCheck.apply()
        baselineCheckCompatibilityReleasedCheck.apply()

        if (removedApi != null && removedApiFile != null) {
            assertTrue(
                "${removedApiFile.path} does not exist even though --removed-api was used",
                removedApiFile.exists()
            )
            assertSignatureFilesMatch(
                removedApi,
                removedApiFile.readText(),
                expectedFormat = format
            )
            // Make sure we can read back the files we write
            ApiFile.parseApi(SignatureFile.fromFiles(removedApiFile), Codebase.Config.NOOP)
        }

        if (proguard != null && proguardFile != null) {
            assertTrue(
                "${proguardFile.path} does not exist even though --proguard was used",
                proguardFile.exists()
            )
            val expectedProguard = readFileFilterBlankLines(proguardFile)
            assertEquals(
                stripComments(proguard, DOT_TXT, stripLineComments = false).trimIndent(),
                expectedProguard
            )
        }

        if (sdkBroadcastActions != null) {
            val actual = readFileFilterBlankLines(File(sdkFilesDir, "broadcast_actions.txt"))
            assertEquals(sdkBroadcastActions.trimIndent().trim(), actual.trim())
        }

        if (sdkActivityActions != null) {
            val actual = readFileFilterBlankLines(File(sdkFilesDir, "activity_actions.txt"))
            assertEquals(sdkActivityActions.trimIndent().trim(), actual.trim())
        }

        if (sdkServiceActions != null) {
            val actual = readFileFilterBlankLines(File(sdkFilesDir, "service_actions.txt"))
            assertEquals(sdkServiceActions.trimIndent().trim(), actual.trim())
        }

        if (sdkCategories != null) {
            val actual = readFileFilterBlankLines(File(sdkFilesDir, "categories.txt"))
            assertEquals(sdkCategories.trimIndent().trim(), actual.trim())
        }

        if (sdkFeatures != null) {
            val actual = readFileFilterBlankLines(File(sdkFilesDir, "features.txt"))
            assertEquals(sdkFeatures.trimIndent().trim(), actual.trim())
        }

        if (sdkWidgets != null) {
            val actual = readFileFilterBlankLines(File(sdkFilesDir, "widgets.txt"))
            assertEquals(sdkWidgets.trimIndent().trim(), actual.trim())
        }

        if (extractAnnotations != null && extractedAnnotationsZip != null) {
            assertTrue(
                "Using --extract-annotations but $extractedAnnotationsZip was not created",
                extractedAnnotationsZip.isFile
            )
            for ((pkg, xml) in extractAnnotations) {
                assertPackageXml(pkg, extractedAnnotationsZip, xml)
            }
        }

        if (validateNullabilityTxt != null) {
            assertTrue(
                "Using $ARG_NULLABILITY_WARNINGS_TXT but $validateNullabilityTxt was not created",
                validateNullabilityTxt.isFile
            )
            val actualReport = validateNullabilityTxt.readLines().map(String::trim).toSet()
            assertEquals(validateNullability, actualReport)
        }

        val stubsCreated =
            stubsDir
                ?.walkTopDown()
                ?.filter { it.isFile }
                ?.map { it.relativeTo(stubsDir).path }
                ?.sorted()
                ?.joinToString("\n")

        if (stubPaths != null) {
            assertEquals("stub paths", stubPaths.joinToString("\n"), stubsCreated)
        }

        if (expectedStubFiles.isNotEmpty()) {
            for (expected in expectedStubFiles) {
                val actual = File(stubsDir!!, expected.targetRelativePath)
                if (!actual.exists()) {
                    throw FileNotFoundException(
                        "Could not find a generated stub for ${expected.targetRelativePath}. " +
                            "Found these files: \n${stubsCreated!!.prependIndent("  ")}"
                    )
                }
                val actualContents = readFile(actual)
                val stubSource = if (allSourceFiles.isEmpty()) "text" else "source"
                val message =
                    "Generated from-$stubSource stub contents does not match expected contents"
                compareStubFileContent(
                    message,
                    expected.contents,
                    actualContents,
                    ignoreParameterNamesInStubFiles
                )
            }
        }

        if (checkCompilation) {
            stubsDir
                ?: error("internal error: stubsDir must be non-null when checkCompilation=true")

            val checks = compilationChecks ?: listOf(CompilationCheck(label = "default"))
            for (check in checks) {
                check.compileStubs(
                    projectDir,
                    stubsDir,
                )
            }
        }

        // Validate multiplatform API files exist and have the expected contents.
        if (multiplatformApiDirectory != null) {
            assertTrue(
                "${multiplatformApiDirectory.path} does not exist even though $ARG_MULTIPLATFORM_API_DIR was used",
                multiplatformApiDirectory.exists(),
            )
            for ((sourceSet, expectedApi) in multiplatformApi) {
                val sourceSetApiFile = File(multiplatformApiDirectory, sourceSet)
                assertTrue(
                    "${sourceSetApiFile.path} does not exist but was expected",
                    sourceSetApiFile.exists(),
                )
                assertSignatureFilesMatch(
                    expectedApi,
                    sourceSetApiFile.readText(),
                    expectedFormat = format
                )
            }
            // Check that there aren't additional API files which were not expected.
            val multiplatformSignatureFiles = multiplatformApiDirectory.listFiles().toList()
            for (sourceSetApiFile in multiplatformSignatureFiles) {
                assertTrue(
                    "${sourceSetApiFile.path} was generated but was not expected",
                    sourceSetApiFile.name in multiplatformApi,
                )
            }
            // Parse back the multiplatform API to ensure there are no errors.
            ApiFile.parseMultiplatformApi(
                SignatureFile.fromFiles(multiplatformSignatureFiles),
                Codebase.Config.NOOP
            )
        }

        if (traceFile != null) {
            assertTrue("Trace file exists", traceFile.exists())
            assertTrue("Trace file is not empty", Files.size(traceFile.toPath()) > 0)
        }
    }

    /** Encapsulates information needed to request a compatibility check. */
    private class CompatibilityCheckRequest
    private constructor(
        private val optionName: String,
        private val fileOrSignatureContentsList: List<String>,
        private val newBasename: String,
    ) {
        companion object {
            fun create(
                optionName: String,
                fileOrSignatureContents: String?,
                fileOrSignatureContentsList: List<String>,
                newBasename: String,
            ): CompatibilityCheckRequest =
                CompatibilityCheckRequest(
                    optionName = optionName,
                    fileOrSignatureContentsList =
                        listOfNotNull(fileOrSignatureContents) + fileOrSignatureContentsList,
                    newBasename = newBasename,
                )
        }

        /** Indicates whether the compatibility check is required. */
        fun required(): Boolean = fileOrSignatureContentsList.isNotEmpty()

        /** The arguments to pass to Metalava. */
        fun arguments(project: File): Array<out String> {
            return fileOrSignatureContentsList.contentOrPathListToArgsArray(
                project,
                newBasename,
                optionName
            )
        }
    }

    /** Checks that the given zip annotations file contains the given XML package contents */
    private fun assertPackageXml(pkg: String, output: File, @Language("XML") expected: String) {
        assertNotNull(output)
        assertTrue(output.exists())
        val url =
            URI(
                    "jar:" +
                        SdkUtils.fileToUrlString(output) +
                        "!/" +
                        pkg.replace('.', '/') +
                        "/annotations.xml"
                )
                .toURL()
        val stream = url.openStream()
        try {
            val bytes = stream.readBytes()
            assertNotNull(bytes)
            val xml = String(bytes, UTF_8).replace("\r\n", "\n")
            assertEquals(expected.trimIndent().trim(), xml.trimIndent().trim())
        } finally {
            Closeables.closeQuietly(stream)
        }
    }

    /** Issues that should be treated as [ARG_HIDE]. */
    fun hiddenIssues(vararg issues: Issue) = Companion.hiddenIssues(*issues)

    /** Issues that should be treated as [ARG_WARNING]. */
    fun warningIssues(vararg issues: Issue) = Companion.warningIssues(*issues)

    /** Issues that should be treated as [ARG_ERROR]. */
    fun errorIssues(vararg issues: Issue) = Companion.errorIssues(*issues)

    companion object {
        private inline fun <T> Array<T>.prefixWith(prefix: String, lamba: (T) -> String) =
            flatMap { listOf(prefix, lamba(it)) }.toTypedArray()

        /** Issues that should be treated as [ARG_HIDE]. */
        fun hiddenIssues(vararg issues: Issue) = issues.prefixWith(ARG_HIDE) { it.name }

        /** Issues that should be treated as [ARG_WARNING]. */
        fun warningIssues(vararg issues: Issue) = issues.prefixWith(ARG_WARNING) { it.name }

        /** Issues that should be treated as [ARG_ERROR]. */
        fun errorIssues(vararg issues: Issue) = issues.prefixWith(ARG_ERROR) { it.name }

        /** Read a text file, filtering out any blank lines and removing whitespace from the end. */
        @JvmStatic
        protected fun readFileFilterBlankLines(file: File): String {
            val apiLines = file.readLines().filter { it.isNotBlank() }
            return apiLines.joinToString(separator = "\n") { it }.trim()
        }

        /** Read a text file, removing whitespace from the end. */
        private fun readFile(file: File): String {
            val apiLines = file.readLines()
            return apiLines.joinToString(separator = "\n") { it }.trimEnd()
        }

        /**
         * Get a signature API [File] from either a file path or its contents.
         *
         * @param project the directory in which to create a new file.
         * @param fileOrFileContents either a path to an existing file or the contents of the
         *   signature file. If the latter the contents will be trimmed, updated to add a
         *   [FileFormat.V2] header if needed and written to a new file created within [project].
         * @param newBasename the basename of a new file created.
         */
        private fun useExistingSignatureFileOrCreateNewFile(
            project: File,
            fileOrFileContents: String,
            newBasename: String
        ) =
            useExistingFileOrCreateNewFile(project, fileOrFileContents, newBasename) {
                prepareSignatureFileForTest(it, FileFormat.V2)
            }

        /**
         * Get an optional [File] from either a file path or its contents.
         *
         * @param project the directory in which to create a new file.
         * @param fileOrFileContents either a path to an existing file or the contents of the file.
         *   If the latter the [transformer] will be applied to [fileOrFileContents] and written to
         *   a new file created within [project].
         * @param newBasename the basename of a new file created.
         */
        private fun useExistingFileOrCreateNewFile(
            project: File,
            fileOrFileContents: String,
            newBasename: String,
            transformer: (String) -> String,
        ) =
            File(fileOrFileContents).let { maybeFile ->
                if (maybeFile.isFile) {
                    maybeFile
                } else {
                    val file = findNonExistentFile(project, newBasename)
                    file.writeText(transformer(fileOrFileContents))
                    file
                }
            }

        /**
         * Converts the contents of the list, which may be either the name of a file or the contents
         * of a file into an array of arguments.
         *
         * This will use files supplied and create new files from the contents of the file and then
         * precede each file by the [optionName].
         *
         * @param project the project directory for the test.
         * @param baseName the base name of the files, including extension. Any created files will
         *   have a unique name based on this name.
         * @param optionName the name of the option to use in the arguments.
         */
        private fun List<String>.contentOrPathListToArgsArray(
            project: File,
            baseName: String,
            optionName: String
        ): Array<String> {
            if (isEmpty()) return emptyArray()

            val paths = map { useExistingSignatureFileOrCreateNewFile(project, it, baseName).path }

            // For each path in the list generate an option with the path as the value.
            return paths.flatMap { listOf(optionName, it) }.toTypedArray()
        }

        private fun findNonExistentFile(project: File, basename: String): File {
            // Split the basename into the name without any extension an optional extension.
            val index = basename.lastIndexOf('.')
            val (nameWithoutExtension, optionalExtension) =
                if (index == -1) {
                    Pair(basename, "")
                } else {
                    Pair(basename.substring(0, index), basename.substring(index))
                }

            var count = 0
            do {
                val name =
                    if (count == 0) basename else "$nameWithoutExtension-$count$optionalExtension"
                count += 1

                val file = File(project, name)
                if (!file.isFile) return file
            } while (true)
        }

        /**
         * Compare stubs contents, checking that [expected] and [actual] match, reporting [message]
         * if they do not.
         *
         * How they match depends on [ignoreParameterNamesInStubFiles]. If that is `false` they have
         * to be character for character identical. If it is `true` then [removeParameterNames] is
         * applied to both beforehand to remove parameter names.
         */
        private fun compareStubFileContent(
            message: String,
            expected: String,
            actual: String,
            ignoreParameterNamesInStubFiles: Boolean,
        ) {
            if (ignoreParameterNamesInStubFiles) {
                val expectedWithout = expected.removeParameterNames()
                val actualWithout = actual.removeParameterNames()
                assertEquals("$message (without parameter names)", expectedWithout, actualWithout)
            } else {
                assertEquals(message, expected, actual)
            }
        }

        /**
         * Remove parameter names from stub file.
         *
         * This is not 100% accurate, it assumes that parameter names are preceded by a ` `, start
         * with a lower case letter, contain alphanumerics only and is immediately followed by a `,`
         * or `)`. However, given the strict formatting of stub files that should be sufficient.
         */
        private fun String.removeParameterNames() =
            replace(Regex(""" [a-z][a-zA-Z0-9_]*([,)])"""), "$1")

        /** Regex for finding an issue of severity error. */
        private val containsErrorSeverityIssueRegex = Regex("""\berror: """)

        /** Check to see whether this [String] contains an issue of error severity. */
        private fun String?.containsErrorIssue() =
            this != null && contains(containsErrorSeverityIssueRegex)
    }

    /**
     * Encapsulates information needed to check the compilation of the stubs.
     *
     * @param label the label for this check, used when reporting failures.
     * @param additionalFiles additional files to compile with the stubs.
     * @param expectedFailure the expected failure output.
     */
    inner class CompilationCheck(
        private val label: String,
        private val additionalFiles: List<TestFile> = emptyList(),
        private val expectedFailure: String = "",
    ) {
        /** Compile the stubs, verifying that it matches [expectedFailure]. */
        internal fun compileStubs(
            projectDir: File,
            stubsDir: File,
        ) {
            // Get a folder in which the additionalFiles, if any, will be created. Delete it and its
            // contents and then recreate it to ensure that it is empty so that multiple instances
            // of CompilationCheck do not collide.
            val additionalDir =
                getOrCreateFolder(
                    "additional-compilation-files",
                    testLabel = "ADDITIONAL",
                )
            additionalDir.deleteRecursively()
            additionalDir.mkdirs()

            // Create a source path for the stubsDir and the additionalDir if needed.
            val sourcePath = buildList {
                add(stubsDir)
                if (additionalFiles.isNotEmpty()) {
                    additionalFiles.createFiles(additionalDir)
                    add(additionalDir)
                }
            }

            // Get the sources to compile by scanning the sourcePath.
            val generated =
                SourceSet.createFromSourcePath(ThrowingReporter.INSTANCE, sourcePath).sources

            // Compile the stubs, throwing an exception if it fails.
            try {
                JavacHelper.compile(
                    outputDirectory = projectDir,
                    sources = generated,
                    classPath = listOf(KnownJarFiles.stubAnnotationsJar),
                )

                // If it was expected to fail but did not then fail.
                if (expectedFailure != "") {
                    fail("Expected failure: $expectedFailure")
                }
            } catch (e: JavacCompilationError) {
                // Process the output to remove any test specific paths.
                val output = removeTestSpecificDirectories(e.output.trim())
                assertEquals("$label compilation failure", expectedFailure.trimIndent(), output)
            } finally {
                // Delete the additional directory.
                additionalDir.deleteRecursively()
            }
        }
    }
}

private fun FileFormat.outputFlags(): String {
    return "$ARG_FORMAT=${specifier()}"
}

private fun File.writeSignatureText(contents: String) {
    writeText(prepareSignatureFileForTest(contents, FileFormat.V2))
}

/** Returns the paths returned by [findKotlinStdlibPaths] as metalava args expected by Options. */
fun findKotlinStdlibPathArgs(sources: Array<String>): Array<String> {
    val kotlinPaths = findKotlinStdlibPaths(sources)

    return if (kotlinPaths.isEmpty()) emptyArray()
    else
        arrayOf(
            ARG_CLASS_PATH,
            kotlinPaths.joinToString(separator = File.pathSeparator) { it.path }
        )
}

val intRangeAnnotationSource = KnownSourceFiles.intRangeAnnotationSource

val intDefAnnotationSource: TestFile =
    java(
            """
    package android.annotation;
    import java.lang.annotation.Retention;
    import java.lang.annotation.RetentionPolicy;
    import java.lang.annotation.Target;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({ANNOTATION_TYPE})
    public @interface IntDef {
        int[] value() default {};
        boolean flag() default false;
    }
    """
        )
        .indented()

val longDefAnnotationSource: TestFile =
    java(
            """
    package android.annotation;
    import java.lang.annotation.Retention;
    import java.lang.annotation.RetentionPolicy;
    import java.lang.annotation.Target;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({ANNOTATION_TYPE})
    public @interface LongDef {
        long[] value() default {};
        boolean flag() default false;
    }
    """
        )
        .indented()

val nonNullSource = KnownSourceFiles.nonNullSource
val nullableSource = KnownSourceFiles.nullableSource
val libcoreNonNullSource = KnownSourceFiles.libcoreNonNullSource
val libcoreNullableSource = KnownSourceFiles.libcoreNullableSource

val libcoreNullFromTypeParamSource: TestFile =
    java(
            """
    package libcore.util;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    import java.lang.annotation.*;
    @Documented
    @Retention(SOURCE)
    @Target({TYPE_USE})
    public @interface NullFromTypeParam {
    }
    """
        )
        .indented()

val requiresPermissionSource: TestFile =
    java(
            """
    package android.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({ANNOTATION_TYPE,METHOD,CONSTRUCTOR,FIELD,PARAMETER})
    public @interface RequiresPermission {
        String value() default "";
        String[] allOf() default {};
        String[] anyOf() default {};
        boolean conditional() default false;
        @Target({FIELD, METHOD, PARAMETER})
        @interface Read {
            RequiresPermission value() default @RequiresPermission;
        }
        @Target({FIELD, METHOD, PARAMETER})
        @interface Write {
            RequiresPermission value() default @RequiresPermission;
        }
    }
    """
        )
        .indented()

val requiresFeatureSource: TestFile =
    java(
            """
    package android.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({TYPE,FIELD,METHOD,CONSTRUCTOR})
    public @interface RequiresFeature {
        String value();
    }
    """
        )
        .indented()

val requiresApiSource: TestFile =
    java(
            """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @Retention(SOURCE)
    @Target({TYPE,FIELD,METHOD,CONSTRUCTOR})
    public @interface RequiresApi {
        int value() default 1;
        int api() default 1;
    }
    """
        )
        .indented()

private fun restrictedForEnvironmentClass(packageName: String): TestFile =
    java(
            """
            package $packageName;
            import java.lang.annotation.*;
            import android.annotation.StringDef;
            import static java.lang.annotation.ElementType.*;
            import static java.lang.annotation.RetentionPolicy;
            /** @hide */
            @Target({TYPE})
            @Retention(RetentionPolicy.RUNTIME)
            @Repeatable(RestrictedForEnvironment.Container.class)
            public @interface RestrictedForEnvironment {
              @Environment String[] environments();
              int from();
              String ENVIRONMENT_SDK_RUNTIME = "SDK Runtime";
              /** @hide */
              @StringDef(prefix = "ENVIRONMENT_", value = {
                ENVIRONMENT_SDK_RUNTIME
              })
              @Retention(RetentionPolicy.SOURCE)
              @interface Environment {}

              @Retention(RetentionPolicy.RUNTIME)
              @Target(TYPE)
              @interface Container {
                  RestrictedForEnvironment[] value();
              }
            }
        """
        )
        .indented()

val androidXRestrictedForEnvironment = restrictedForEnvironmentClass(ANDROIDX_ANNOTATION_PACKAGE)
val androidRestrictedForEnvironment = restrictedForEnvironmentClass(ANDROID_ANNOTATION_PACKAGE)

val broadcastBehaviorSource: TestFile =
    java(
            """
    package android.annotation;
    import java.lang.annotation.*;
    /** @hide */
    @Target({ ElementType.FIELD })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BroadcastBehavior {
        boolean explicitOnly() default false;
        boolean registeredOnly() default false;
        boolean includeBackground() default false;
        boolean protectedBroadcast() default false;
    }
    """
        )
        .indented()

val recentlyNonNullSource: TestFile =
    java(
            """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @SuppressWarnings("WeakerAccess")
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD, TYPE_USE})
    public @interface RecentlyNonNull {
    }
    """
        )
        .indented()

val recentlyNullableSource: TestFile =
    java(
            """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    @SuppressWarnings("WeakerAccess")
    @Retention(SOURCE)
    @Target({METHOD, PARAMETER, FIELD, TYPE_USE})
    public @interface RecentlyNullable {
    }
    """
        )
        .indented()

val uiThreadSource: TestFile =
    java(
            """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    /**
     * Denotes that the annotated method or constructor should only be called on the
     * UI thread. If the annotated element is a class, then all methods in the class
     * should be called on the UI thread.
     * @memberDoc This method must be called on the thread that originally created
     *            this UI element. This is typically the main thread of your app.
     * @classDoc Methods in this class must be called on the thread that originally created
     *            this UI element, unless otherwise noted. This is typically the
     *            main thread of your app.
     * @hide
     */
    @SuppressWarnings({"WeakerAccess", "JavaDoc"})
    @Retention(SOURCE)
    @Target({METHOD,CONSTRUCTOR,TYPE,PARAMETER})
    public @interface UiThread {
    }
    """
        )
        .indented()

val workerThreadSource: TestFile =
    java(
            """
    package androidx.annotation;
    import java.lang.annotation.*;
    import static java.lang.annotation.ElementType.*;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    /**
     * @memberDoc This method may take several seconds to complete, so it should
     *            only be called from a worker thread.
     * @classDoc Methods in this class may take several seconds to complete, so it should
     *            only be called from a worker thread unless otherwise noted.
     * @hide
     */
    @SuppressWarnings({"WeakerAccess", "JavaDoc"})
    @Retention(SOURCE)
    @Target({METHOD,CONSTRUCTOR,TYPE,PARAMETER})
    public @interface WorkerThread {
    }
    """
        )
        .indented()

val suppressLintSource: TestFile =
    java(
            """
    package android.annotation;

    import static java.lang.annotation.ElementType.*;
    import java.lang.annotation.*;
    @Target({TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE})
    @Retention(RetentionPolicy.CLASS)
    public @interface SuppressLint {
        String[] value();
    }
    """
        )
        .indented()

val systemServiceSource: TestFile =
    java(
            """
    package android.annotation;
    import static java.lang.annotation.ElementType.TYPE;
    import static java.lang.annotation.RetentionPolicy.SOURCE;
    import java.lang.annotation.*;
    @Retention(SOURCE)
    @Target(TYPE)
    public @interface SystemService {
        String value();
    }
    """
        )
        .indented()

val systemApiSource = KnownSourceFiles.systemApiSource

val testApiSource = KnownSourceFiles.testApiSource

val widgetSource: TestFile =
    java(
            """
    package android.annotation;
    import java.lang.annotation.*;
    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Widget {
    }
    """
        )
        .indented()

val restrictToSource = KnownSourceFiles.restrictToSource

val visibleForTestingSource: TestFile =
    java(
            """
    package androidx.annotation;
    import static java.lang.annotation.RetentionPolicy.CLASS;
    import java.lang.annotation.Retention;
    @Retention(CLASS)
    @SuppressWarnings("WeakerAccess")
    public @interface VisibleForTesting {
        int otherwise() default PRIVATE;
        int PRIVATE = 2;
        int PACKAGE_PRIVATE = 3;
        int PROTECTED = 4;
        int NONE = 5;
    }
    """
        )
        .indented()

val columnSource: TestFile =
    java(
            """
    package android.provider;

    import static java.lang.annotation.ElementType.FIELD;
    import static java.lang.annotation.RetentionPolicy.RUNTIME;

    import android.content.ContentProvider;
    import android.content.ContentValues;
    import android.database.Cursor;

    import java.lang.annotation.Documented;
    import java.lang.annotation.Retention;
    import java.lang.annotation.Target;

    @Documented
    @Retention(RUNTIME)
    @Target({FIELD})
    public @interface Column {
        int value();
        boolean readOnly() default false;
    }
    """
        )
        .indented()

val publishedApiSource: TestFile =
    kotlin(
            """
    /**
     * When applied to a class or a member with internal visibility allows to use it from public inline functions and
     * makes it effectively public.
     *
     * Public inline functions cannot use non-public API, since if they are inlined, those non-public API references
     * would violate access restrictions at a call site (https://kotlinlang.org/docs/reference/inline-functions.html#public-inline-restrictions).
     *
     * To overcome this restriction an `internal` declaration can be annotated with the `@PublishedApi` annotation:
     * - this allows to call that declaration from public inline functions;
     * - the declaration becomes effectively public, and this should be considered with respect to binary compatibility maintaining.
     */
    @Target(AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
    @Retention(AnnotationRetention.BINARY)
    @MustBeDocumented
    @SinceKotlin("1.1")
    annotation class PublishedApi
    """
        )
        .indented()

val DEFAULT_SKIP_EMIT_PACKAGES =
    listOf(
        // Do not emit classes in a number of java packages. While tests will
        // use those classes they generally do not want to emit definitions of
        // those classes as part of a signature or stubs files.
        "java.lang",
        "java.util",
        "java.io",
        // Ditto for android.annotation and androidx.annotation classes.
        ANDROID_ANNOTATION_PACKAGE,
        ANDROIDX_ANNOTATION_PACKAGE,
        // Ditto for libcore.util.
        "libcore.util",
        // Ditto for test only nullability annotations.
        "mixed.use",
        "not.type.use",
        "type.use.only",
    )

/**
 * A special [FileFormat] used by tests that want to output type use annotations to signature files.
 */
val TYPE_USE_FORMAT =
    FileFormat.V5.buildCopy {
        this[INCLUDE_TYPE_USE_ANNOTATIONS] = true
        this[KOTLIN_NAME_TYPE_ORDER] = true
        this[KOTLIN_STYLE_NULLS] = false
        this[STRIP_JAVA_LANG_PREFIX] = StripJavaLangPrefix.ALWAYS
    }

/**
 * Specifies a known API surface to use.
 *
 * @param surface the name of the surface, adding as [ARG_API_SURFACE].
 * @param configFile the config file that will define [surface].
 * @param optionalCommandLineOptions the command line options that are equivalent to [surface].
 * @param additionalSourceFiles additional source files to automatically add when this is used.
 */
data class KnownApiSurface(
    val surface: String,
    val configFile: TestFile,
    val additionalSourceFiles: List<TestFile> = emptyList(),
    val optionalCommandLineOptions: List<String>? = null,
) {
    val commandLineOptions: List<String>
        get() =
            optionalCommandLineOptions ?: error("$surface does not provide command line options")

    companion object {
        val additionalAndroidSourceFiles =
            listOf(
                KnownSourceFiles.hideAnnotation,
                KnownSourceFiles.docOnlyAnnotation,
                KnownSourceFiles.removedFromApiAnnotation,
                KnownSourceFiles.systemApiSource,
                KnownSourceFiles.testApiSource,
            )

        /** The public API as used by Android. */
        val PUBLIC =
            KnownApiSurface(
                "public",
                KnownConfigFiles.configKnownTestSurfaces,
                additionalAndroidSourceFiles,
                optionalCommandLineOptions =
                    listOf(
                        ARG_SHOW_UNANNOTATED,
                    )
            )

        /** The system API as used by Android. */
        val SYSTEM =
            KnownApiSurface(
                "system",
                KnownConfigFiles.configKnownTestSurfaces,
                additionalAndroidSourceFiles,
                optionalCommandLineOptions =
                    listOf(
                        ARG_SHOW_ANNOTATION,
                        "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
                    )
            )

        /** The system API plus public API. */
        val SYSTEM_WITH_PUBLIC =
            KnownApiSurface(
                "system-with-public",
                KnownConfigFiles.configSystemWithPublicSurface,
                additionalAndroidSourceFiles,
            )

        /** The system API with non-recursive annotation. */
        val NON_RECURSIVE_SYSTEM =
            KnownApiSurface(
                "system",
                xml(
                    "non-recursive-system.xml",
                    """
                        <config xmlns="http://www.google.com/tools/metalava/config"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../resources/schemas/config.xsd">
                            <api-surfaces>
                                <api-surface name="public">
                                    <selection-criteria unannotated="show"/>
                                </api-surface>
                                <api-surface name="system" extends="public">
                                    <selection-criteria>
                                        <annotation-rule pattern="android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)" recursive='false'/>
                                    </selection-criteria>
                                </api-surface>
                            </api-surfaces>
                        </config>
                    """
                ),
                additionalAndroidSourceFiles,
            )

        /** The module-lib API as used by Android. */
        val MODULE_LIB =
            KnownApiSurface(
                "module-lib",
                KnownConfigFiles.configKnownTestSurfaces,
                additionalAndroidSourceFiles,
            )

        /** The test API as used by Android. */
        val TEST =
            KnownApiSurface(
                "test",
                KnownConfigFiles.configKnownTestSurfaces,
                additionalAndroidSourceFiles,
            )

        const val TEST_HIDE_ANNOTATION = "test.annotation.Hide"
        const val TEST_MODULE_API_ANNOTATION = "test.annotation.ModuleApi"
        const val TEST_SYSTEM_API_ANNOTATION = "test.annotation.SystemApi"
        const val TEST_API_ANNOTATION = "test.annotation.TestApi"

        private val additionalTestSourceFiles =
            listOf(
                java(
                    """
                        package test.annotation;
                        @Hide
                        public @interface Hide {}
                    """
                ),
                java(
                    """
                        package test.annotation;
                        @Hide
                        public @interface SystemApi {}
                    """
                ),
                java(
                    """
                        package test.annotation;
                        @Hide
                        public @interface TestApi {}
                    """
                ),
                java(
                    """
                        package test.annotation;
                        @Hide
                        public @interface ModuleApi {}
                    """
                ),
            )

        private val apiSurfacesConfig =
            xml(
                "api-surfaces-config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../resources/schemas/config.xsd">
                        <api-surfaces>
                            <api-surface name="public">
                                <selection-criteria unannotated="show">
                                    <annotation-rule pattern="$TEST_HIDE_ANNOTATION" effect='hide'/>
                                </selection-criteria>
                            </api-surface>
                            <api-surface name="system" extends="public">
                                <selection-criteria>
                                    <annotation-rule pattern="$TEST_SYSTEM_API_ANNOTATION"/>
                                </selection-criteria>
                            </api-surface>
                            <api-surface name="test" extends="system">
                                <selection-criteria>
                                    <annotation-rule pattern="$TEST_API_ANNOTATION"/>
                                </selection-criteria>
                            </api-surface>
                            <api-surface name="module-lib" extends="system">
                                <selection-criteria>
                                    <annotation-rule pattern="$TEST_MODULE_API_ANNOTATION"/>
                                </selection-criteria>
                            </api-surface>
                        </api-surfaces>
                    </config>
                """
            )

        val TEST_PUBLIC_API_SURFACE =
            KnownApiSurface(
                "public",
                apiSurfacesConfig,
                additionalTestSourceFiles,
            )

        val TEST_SYSTEM_API_SURFACE =
            KnownApiSurface(
                "system",
                apiSurfacesConfig,
                additionalTestSourceFiles,
            )

        val TEST_API_SURFACE =
            KnownApiSurface(
                "test",
                apiSurfacesConfig,
                additionalTestSourceFiles,
            )

        val TEST_MODULE_API_SURFACE =
            KnownApiSurface(
                "module-lib",
                apiSurfacesConfig,
                additionalTestSourceFiles,
            )

        private val nonRecursiveConfigFile =
            xml(
                "non-recursive.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../resources/schemas/config.xsd">
                        <api-surfaces>
                            <api-surface name="non-recursive-without-unannotated">
                                <selection-criteria unannotated="hide">
                                    <annotation-rule pattern="test.annotation.Hide" effect="hide"/>
                                    <annotation-rule pattern="test.annotation.Show" recursive="false"/>
                                </selection-criteria>
                            </api-surface>
                            <api-surface name="non-recursive-with-unannotated">
                                <selection-criteria unannotated="show">
                                    <annotation-rule pattern="test.annotation.Hide" effect="hide"/>
                                    <annotation-rule pattern="test.annotation.Show" recursive="false"/>
                                </selection-criteria>
                            </api-surface>
                        </api-surfaces>
                    </config>
                """
            )

        val NON_RECURSIVE_SHOW_WITHOUT_UNANNOTATED =
            KnownApiSurface(
                "non-recursive-without-unannotated",
                nonRecursiveConfigFile,
            )

        val NON_RECURSIVE_SHOW_WITH_UNANNOTATED =
            KnownApiSurface(
                "non-recursive-with-unannotated",
                nonRecursiveConfigFile,
            )
    }
}
