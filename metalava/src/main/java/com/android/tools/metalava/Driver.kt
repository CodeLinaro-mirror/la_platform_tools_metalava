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

import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_TXT
import com.android.tools.metalava.apilevels.ApiGenerator
import com.android.tools.metalava.cli.common.CheckerContext
import com.android.tools.metalava.cli.common.DefaultSignatureFileLoader
import com.android.tools.metalava.cli.common.EarlyOptions
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.common.IssueReportingOptions
import com.android.tools.metalava.cli.common.MetalavaCommand
import com.android.tools.metalava.cli.common.SourceOptions
import com.android.tools.metalava.cli.common.Verbosity
import com.android.tools.metalava.cli.common.VersionCommand
import com.android.tools.metalava.cli.common.cliError
import com.android.tools.metalava.cli.common.commonOptions
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions.CheckRequest
import com.android.tools.metalava.cli.flag.FlagReportCommand
import com.android.tools.metalava.cli.help.HelpCommand
import com.android.tools.metalava.cli.historical.AndroidJarsToSignaturesCommand
import com.android.tools.metalava.cli.internal.MakeAnnotationsPackagePrivateCommand
import com.android.tools.metalava.cli.lint.ApiLintOptions
import com.android.tools.metalava.cli.multiplatform.MultiplatformOptions
import com.android.tools.metalava.cli.signature.MergeSignaturesCommand
import com.android.tools.metalava.cli.signature.SignatureCatCommand
import com.android.tools.metalava.cli.signature.SignatureFormatOptions
import com.android.tools.metalava.cli.signature.SignatureToDexCommand
import com.android.tools.metalava.cli.signature.SignatureToJDiffCommand
import com.android.tools.metalava.cli.signature.migration.SignatureMigrateCommand
import com.android.tools.metalava.cli.signature.migration.SignatureReformatCommand
import com.android.tools.metalava.compatibility.CompatibilityCheck
import com.android.tools.metalava.jar.JarCodebaseLoader
import com.android.tools.metalava.lint.ApiLint
import com.android.tools.metalava.lint.FlaggedApiLint
import com.android.tools.metalava.lint.MultiplatformLint
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.snapshot.NonFilteringDelegatingVisitor
import com.android.tools.metalava.model.source.EnvironmentManager
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.ADD_ADDITIONAL_OVERRIDES
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.JAVA_RECORD_CLASSES
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.text.SignatureWriter
import com.android.tools.metalava.model.text.createCodebaseFragmentForSignatureFile
import com.android.tools.metalava.model.visitors.ApiFilters
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.model.visitors.FilteringApiVisitor
import com.android.tools.metalava.model.visitors.MatchOverridingMethodPredicate
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.stub.StubGenerator
import com.github.ajalt.clikt.core.subcommands
import com.google.common.base.Stopwatch
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Arrays
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.system.exitProcess

const val PROGRAM_NAME = "metalava"

class Driver(
    private val executionEnvironment: ExecutionEnvironment,
    private val progressTracker: ProgressTracker,
    private val environmentManager: EnvironmentManager,
    private val reporter: Reporter,
    private val verbosity: Verbosity,
    private val miscellaneousOptions: MiscellaneousOptions,
    private val apiLevelsGenerationOptions: ApiLevelsGenerationOptions,
    private val apiLintOptions: ApiLintOptions,
    internal val apiSelectionOptions: ApiSelectionOptions,
    internal val compatibilityCheckOptions: CompatibilityCheckOptions,
    internal val configFileOptions: ConfigFileOptions,
    private val issueReportingOptions: IssueReportingOptions,
    private val multiplatformOptions: MultiplatformOptions,
    private val nullabilityValidationOptions: NullabilityValidationOptions,
    private val signatureFileOptions: SignatureFileOptions,
    private val signatureFormatOptions: SignatureFormatOptions,
    private val sourceOptions: SourceOptions,
    private val stubGenerationOptions: StubGenerationOptions,
) {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val executionEnvironment = ExecutionEnvironment()
            var exitCode = 0
            try {
                exitCode = run(executionEnvironment = executionEnvironment, args = args)
            } catch (e: Throwable) {
                exitCode = -1
                e.printStackTrace(executionEnvironment.stderr)
            } finally {
                executionEnvironment.stdout.flush()
                executionEnvironment.stderr.flush()

                exitProcess(exitCode)
            }
        }

        /**
         * The metadata driver is a command line interface to extracting various metadata from a
         * source tree (or existing signature files etc.). Run with --help to see more details.
         */
        fun run(
            executionEnvironment: ExecutionEnvironment,
            args: Array<String>,
        ): Int {
            val stdout = executionEnvironment.stdout
            val stderr = executionEnvironment.stderr

            // Process the early options. This does not consume any arguments, they will be parsed
            // again later. A little inefficient but produces cleaner code.
            val earlyOptions = EarlyOptions.parse(args)

            val progressTracker = ProgressTracker(earlyOptions.verbosity.verbose, stdout)

            progressTracker.progress("$PROGRAM_NAME started\n")

            // Actual work begins here.
            val command =
                createMetalavaCommand(
                    executionEnvironment,
                    progressTracker,
                )
            val exitCode = command.process(args)

            stdout.flush()
            stderr.flush()

            progressTracker.progress("$PROGRAM_NAME exiting with exit code $exitCode\n")

            return exitCode
        }

        private fun createMetalavaCommand(
            executionEnvironment: ExecutionEnvironment,
            progressTracker: ProgressTracker
        ): MetalavaCommand {
            val command =
                MetalavaCommand(
                    executionEnvironment = executionEnvironment,
                    progressTracker = progressTracker,
                    defaultCommandName = "main",
                )
            command.subcommands(
                MainCommand(command.commonOptions, executionEnvironment),
                AndroidJarsToSignaturesCommand(),
                FlagReportCommand(),
                HelpCommand(),
                JarToJDiffCommand(),
                MakeAnnotationsPackagePrivateCommand(),
                MergeSignaturesCommand(),
                SignatureCatCommand(),
                SignatureMigrateCommand(),
                SignatureReformatCommand(),
                SignatureToDexCommand(),
                SignatureToJDiffCommand(),
                VersionCommand(),
            )
            return command
        }
    }

    private val apiFlags by lazy {
        ApiFlagsCreator.createFromConfig(configFileOptions.config.apiFlags)
    }

    private val annotationManager: AnnotationManager by lazy {
        DefaultAnnotationManager(
            DefaultAnnotationManager.Config(
                reporter = reporter,
                passThroughAnnotations = apiSelectionOptions.passThroughAnnotations,
                allShowAnnotations = apiSelectionOptions.allShowAnnotations,
                showAnnotations = apiSelectionOptions.showAnnotations,
                showSingleAnnotations = apiSelectionOptions.showSingleAnnotations,
                showForStubPurposesAnnotations = apiSelectionOptions.showForStubPurposesAnnotations,
                hideAnnotations = apiSelectionOptions.hideAnnotations,
                suppressCompatibilityMetaAnnotations =
                    apiSelectionOptions.suppressCompatibilityMetaAnnotations,
                excludeAnnotations = apiSelectionOptions.excludeAnnotations,
                typedefMode = apiSelectionOptions.typedefMode,
                apiPredicate = ApiPredicate(config = apiPredicateConfig),
                previouslyReleasedCodebaseProvider = {
                    compatibilityCheckOptions.previouslyReleasedApi?.load {
                        signatureFileCache.load(it)
                    }
                },
                apiFlags = apiFlags,
            )
        )
    }

    private val codebaseConfig by
        lazy(LazyThreadSafetyMode.NONE) {
            Codebase.Config(
                allowReadingComments = sourceOptions.allowReadingComments,
                annotationManager = annotationManager,
                apiFlags = apiFlags,
                apiSurfaces = apiSelectionOptions.apiSurfaces,
                reporter = reporter,
            )
        }

    private val sourceParser by
        lazy(LazyThreadSafetyMode.NONE) {
            val modelOptions = sourceOptions.modelOptions
            environmentManager.createSourceParser(
                codebaseConfig = codebaseConfig,
                javaLanguageLevel = sourceOptions.javaLanguageLevelAsString,
                kotlinLanguageLevel = sourceOptions.kotlinLanguageLevelAsString,
                modelOptions = modelOptions,
                jdkHome = sourceOptions.jdkHome,
            )
        }

    private val signatureFileLoader by
        lazy(LazyThreadSafetyMode.NONE) { DefaultSignatureFileLoader(codebaseConfig) }

    internal val signatureFileCache by
        lazy(LazyThreadSafetyMode.NONE) { SignatureFileCache(signatureFileLoader) }

    /**
     * Avoids creating a [ClassPathResolver] unnecessarily as it is expensive to create but once
     * created allows it to be reused for the same reason.
     */
    private val classPathResolver: ClassPathResolver? by lazy {
        val classpath = sourceOptions.classpath
        if (classpath.isNotEmpty()) {
            sourceParser.getClassPathResolver(classpath)
        } else {
            null
        }
    }

    /** The configuration options for the [ApiAnalyzer] class. */
    private val apiAnalyzerConfig by lazy {
        val skipEmitPackages = executionEnvironment.testEnvironment?.skipEmitPackages ?: emptyList()
        ApiAnalyzer.Config(
            manifest = miscellaneousOptions.manifest,
            skipEmitPackages = skipEmitPackages,
            mergeQualifierAnnotations = sourceOptions.mergeQualifierAnnotations,
            mergeInclusionAnnotations = sourceOptions.mergeInclusionAnnotations,
            allShowAnnotations = apiSelectionOptions.allShowAnnotations,
            apiPredicateConfig = apiPredicateConfig,
            annotationsMergerConfig =
                AnnotationsMerger.Config(
                    apiPredicateConfig = apiPredicateConfig,
                    sources = sourceOptions.sourceFiles,
                    sourcePath = sourceOptions.sourcePath,
                    classpath = sourceOptions.classpath,
                    apiPackageFilter = sourceOptions.apiPackageFilter,
                    nullabilityAnnotationsValidator =
                        nullabilityValidationOptions.validatorForMerging,
                ),
        )
    }

    private val apiPredicateConfig by lazy {
        ApiPredicate.Config(
            ignoreShown = apiSelectionOptions.showUnannotated,
            addAdditionalOverrides = signatureFormatOptions.fileFormat[ADD_ADDITIONAL_OVERRIDES],
        )
    }

    internal fun processFlags() {
        val stopwatch = Stopwatch.createStarted()

        val codebase = createCodebaseFromOptions()

        // Create a multiplatform codebase if requested.
        val multiplatformCodebase = createOptionalMultiplatformCodebase()

        // If provided by a test, run some additional checks on the internal state of this.
        executionEnvironment.testEnvironment?.let { testEnvironment ->
            testEnvironment.postAnalysisChecker?.let { function ->
                val context = CheckerContext(this, codebase, multiplatformCodebase)
                context.function()
            }
        }

        progressTracker.progress(
            "$PROGRAM_NAME analyzed API in ${stopwatch.elapsed(SECONDS)} seconds\n"
        )

        // Run operations on the regular codebase, if it exists.
        codebase?.let { runCodebaseChecks(stopwatch, codebase) }

        // Run additional operations on the multiplatform codebase, if it exists.
        multiplatformCodebase?.let { runMultiplatformCodebaseChecks(multiplatformCodebase) }
    }

    private fun runCodebaseChecks(stopwatch: Stopwatch, codebase: Codebase) {
        generateApiHistoryFromOptions(codebase)

        // Generate signature files based on provided input flags (i.e. if api file locations were
        // provided).
        // Also run API lint checks on current codebase
        createApiSignatureFilesFromOptions(
            codebase,
        )

        miscellaneousOptions.proguardFile?.let { proguard ->
            val apiPredicateConfigIgnoreShown = apiPredicateConfig.copy(ignoreShown = true)
            val apiReferenceIgnoreShown = ApiPredicate(config = apiPredicateConfigIgnoreShown)
            val apiEmit = MatchOverridingMethodPredicate(ApiPredicate(config = apiPredicateConfig))
            val apiFilters = ApiFilters(emit = apiEmit, reference = apiReferenceIgnoreShown)
            val codebaseFragment =
                CodebaseFragment.create(codebase) { delegatedVisitor ->
                    FilteringApiVisitor(
                        delegatedVisitor,
                        inlineInheritedFields = true,
                        apiFilters = apiFilters,
                        preFiltered = codebase.preFiltered,
                    )
                }

            createOutputFileFromCodebaseFragment(
                progressTracker,
                codebaseFragment,
                proguard,
                "Proguard file",
            ) { printWriter ->
                ProguardWriter(printWriter)
            }
        }

        miscellaneousOptions.sdkValueDir?.let { dir ->
            dir.mkdirs()
            SdkFileWriter(codebase, dir).generate()
        }

        for (check in compatibilityCheckOptions.compatibilityChecks) {
            checkCompatibility(codebase, check)
        }

        miscellaneousOptions.externalAnnotationsFile?.let { outputFile ->
            extractAnnotations(
                outputFile,
                codebase,
            )
        }

        // Generate the stubs. This must be done as the last operation in this method as it can
        // modify the [codebase].
        val generatorConfig =
            stubGenerationOptions.generatorConfig(
                javaRecordClasses = signatureFormatOptions.fileFormat[JAVA_RECORD_CLASSES],
            )
        StubGenerator(
                generatorConfig,
                codebase,
                progressTracker,
                executionEnvironment,
                reporter,
                signatureFileCache,
                apiPredicateConfig,
            )
            .generateStubs()

        val packageCount = codebase.size()
        progressTracker.progress(
            "$PROGRAM_NAME finished handling $packageCount packages in ${stopwatch.elapsed(SECONDS)} seconds\n"
        )
    }

    private fun runApiChecksFromOptions(
        codebase: Codebase,
        apiCheckMethod: (Codebase, Codebase?) -> Unit
    ) {
        apiLintOptions.let { apiLintOptions ->
            if (!apiLintOptions.apiLintEnabled) return@let

            progressTracker.progress("API Lint: ")
            val localTimer = Stopwatch.createStarted()

            // See if we should provide a previous codebase to provide a delta from?
            val previouslyReleasedCodebase by lazy {
                apiLintOptions.previouslyReleasedApi?.load { signatureFiles ->
                    signatureFileCache.load(signatureFiles, classPathResolver)
                }
            }
            apiCheckMethod(codebase, previouslyReleasedCodebase)
            progressTracker.progress(
                "$PROGRAM_NAME ran api api-lint in ${localTimer.elapsed(SECONDS)} seconds"
            )
        }
    }

    /** write api signature to files specified by option flags (e.g. current.txt) */
    private fun createApiSignatureFilesFromOptions(codebase: Codebase) {
        val fileFormat = signatureFormatOptions.fileFormat
        val codebaseFragment =
            createSignatureFileFragment(
                codebase,
                fileFormat = fileFormat,
                apiType = ApiType.PUBLIC_API,
                preFiltered = codebase.preFiltered,
            )

        runApiChecksFromOptions(codebase) { _, previouslyReleasedCodebase ->
            val flaggedApiLintVisitor =
                FlaggedApiLint(previouslyReleasedCodebase, reporter, apiPredicateConfig)
            codebaseFragment.accept(flaggedApiLintVisitor)
        }

        signatureFileOptions.apiFile?.let { apiSignatureFile ->
            createOutputFileFromCodebaseFragment(
                progressTracker,
                codebaseFragment,
                apiSignatureFile,
                "API"
            ) { printWriter ->
                SignatureWriter(
                    writer = printWriter,
                    fileFormat = fileFormat,
                )
            }
        }

        signatureFileOptions.removedApiFile?.let { apiSignatureFile ->
            val removedApiCodebaseFragment =
                createSignatureFileFragment(
                    codebase,
                    fileFormat = fileFormat,
                    apiType = ApiType.REMOVED,
                    preFiltered = false,
                )

            createOutputFileFromCodebaseFragment(
                progressTracker,
                removedApiCodebaseFragment,
                apiSignatureFile,
                "removed API",
                signatureFileOptions.deleteEmptyRemovedSignatures,
            ) { printWriter ->
                SignatureWriter(
                    writer = printWriter,
                    emitHeader = signatureFileOptions.includeSignatureFormatVersionRemoved,
                    fileFormat = fileFormat,
                )
            }
        }
    }

    private fun createSignatureFileFragment(
        codebase: Codebase,
        fileFormat: FileFormat,
        apiType: ApiType,
        preFiltered: Boolean,
    ): CodebaseFragment {
        var codebaseFragment =
            createCodebaseFragmentForSignatureFile(
                codebase,
                fileFormat = fileFormat,
                apiType = apiType,
                preFiltered = preFiltered,
                showUnannotated = apiSelectionOptions.showUnannotated,
                apiPredicateConfig = apiPredicateConfig,
            )

        // If reverting some changes then create a snapshot that combines the items from the sources
        // for any un-reverted changes and items from the previously released API for any reverted
        // changes.
        if (codebaseFragment.codebase.containsRevertedItem) {
            codebaseFragment =
                codebaseFragment.snapshotIncludingRevertedItems(
                    // Allow references to any of the ClassItems in the original Codebase. This
                    // should not be a problem for signature files as they only refer to them by
                    // name and do not care about their contents.
                    referenceVisitorFactory = ::NonFilteringDelegatingVisitor,
                )
        }
        return codebaseFragment
    }

    /** Create [Codebase] object from option flags */
    private fun createCodebaseFromOptions(): Codebase? {
        val sources = sourceOptions.sourceFiles
        if (sources.isNotEmpty() && sources[0].path.endsWith(DOT_TXT)) {
            // Make sure all the source files have .txt extensions.
            sources
                .firstOrNull { !it.path.endsWith(DOT_TXT) }
                ?.let {
                    cliError(
                        "Inconsistent input file types: The first file is of $DOT_TXT, but detected different extension in ${it.path}"
                    )
                }
            return signatureFileLoader.load(
                SignatureFile.fromFiles(sources),
                classPathResolver,
            )
        } else if (sources.size == 1 && sources[0].path.endsWith(DOT_JAR)) {
            return loadFromJarFile(sources[0])
        } else if (sources.isNotEmpty() || sourceOptions.sourcePath.isNotEmpty()) {
            return loadFromSources()
        }

        return null
    }

    private fun createOptionalMultiplatformCodebase(): MultiplatformCodebase? {
        if (!multiplatformOptions.enabled) return null
        return sourceOptions.projectDescription?.let { projectDescription ->
            sourceParser.createMultiplatformCodebase(projectDescription)
        } ?: error("Project description is required to create multiplatform codebase from sources.")
    }

    private fun runMultiplatformCodebaseChecks(multiplatformCodebase: MultiplatformCodebase) {
        if (apiLintOptions.apiLintEnabled) {
            MultiplatformLint(reporter).check(multiplatformCodebase)

            // For the regular, non-multiplatform codebase operations, if they happened, either the
            // android or jvm source set would have been used. Find which one of these it was.
            // If neither exist, treat the common source set as the main one.
            val mainCodebaseEntry =
                multiplatformCodebase.sourceSetToCodebase.entries.singleOrNull {
                    it.key == "androidMain"
                }
                    ?: multiplatformCodebase.sourceSetToCodebase.entries.singleOrNull {
                        it.key == "jvmMain"
                    }
                    ?: multiplatformCodebase.sourceSetToCodebase.entries.singleOrNull {
                        it.key == "commonMain"
                    }
            val mainSourceSet = mainCodebaseEntry?.key
            val mainCodebase = mainCodebaseEntry?.value

            // If there isn't an android or jvm source set, API lint won't have run yet for common.
            // Run it here so that the common source set can be treated as the "old" codebase in
            // the checks below, and any lint issues in common will only be reported once here
            // instead of being duplicated.
            if (mainSourceSet == "commonMain") {
                ApiLint.check(
                    mainCodebase!!,
                    null,
                    reporter,
                    apiPredicateConfig,
                    ApiLint.Config(
                        manifest = miscellaneousOptions.manifest,
                        allowedAcronyms = apiLintOptions.allowedAcronyms,
                    ),
                )
            }

            // Run regular API lint checks for each source set.
            for ((sourceSet, codebase) in multiplatformCodebase.sourceSetToCodebase) {
                // Skip checking the main source set, which will already have been checked through
                // the non-multiplatform lint checks. Also skip checking common, since all APIs will
                // be included in the checks for other source sets.
                if (sourceSet == mainSourceSet || sourceSet == "commonMain") continue
                runApiChecksFromOptions(codebase) { codebase, _ ->
                    ApiLint.check(
                        codebase,
                        // By making the main android/jvm/common codebase the "oldCodebase", any
                        // issues which have already been reported for the main codebase through the
                        // non-multiplatform checks or the common check above will be skipped.
                        oldCodebase = mainCodebase,
                        reporter,
                        apiPredicateConfig,
                        ApiLint.Config(
                            manifest = miscellaneousOptions.manifest,
                            allowedAcronyms = apiLintOptions.allowedAcronyms,
                        ),
                    )
                }
            }
        }
    }

    /** write api history to files specified by option flags (e.g. api-versions.xml) */
    private fun generateApiHistoryFromOptions(
        codebase: Codebase,
    ) {
        val androidConfigCodeFragmentProvider: () -> CodebaseFragment = {
            var codebaseFragment =
                CodebaseFragment.create(codebase) { delegatedVisitor ->
                    FilteringApiVisitor(
                        delegate = delegatedVisitor,
                        apiFilters = ApiVisitor.defaultFilters(apiPredicateConfig),
                        preFiltered = false,
                    )
                }

            // If reverting some changes then create a snapshot that combines the items from the
            // sources for any un-reverted changes and items from the previously released API for
            // any reverted changes.
            if (codebaseFragment.codebase.containsRevertedItem) {
                // Allow references to any of the ClassItems in the original Codebase. This should
                // not be a problem for api-versions.xml files as they only refer to them by name
                // and do not care about their contents.
                codebaseFragment =
                    codebaseFragment.snapshotIncludingRevertedItems(
                        referenceVisitorFactory = ::NonFilteringDelegatingVisitor,
                    )
            }

            codebaseFragment
        }

        // Provide a CodebaseFragment from the sources that will be included in the generated
        // version history.
        val signatureFileConfigCodeFragmentProvider: () -> CodebaseFragment = {
            val apiType = ApiType.PUBLIC_API
            val apiFilters = apiType.getApiFilters(apiPredicateConfig)

            CodebaseFragment.create(codebase) { delegatedVisitor ->
                FilteringApiVisitor(
                    delegate = delegatedVisitor,
                    apiFilters = apiFilters,
                    preFiltered = false,
                )
            }
        }

        val apiGenerator = ApiGenerator()
        apiLevelsGenerationOptions
            .forAndroidConfig(
                // Do not use a cache here as each file loaded is only loaded once and the created
                // Codebase is discarded immediately after use so caching just uses memory for no
                // performance benefit.
                signatureFileLoader,
                androidConfigCodeFragmentProvider,
            )
            ?.let { config ->
                progressTracker.progress(
                    "Generating API levels XML descriptor file, ${config.outputFile.name}: "
                )

                apiGenerator.generateApiHistory(config)
            }

        apiLevelsGenerationOptions
            .fromSignatureFilesConfig(
                // Do not use a cache here as each file loaded is only loaded once and the created
                // Codebase is discarded immediately after use so caching just uses memory for no
                // performance benefit.
                signatureFileLoader,
                codebaseFragmentProvider = signatureFileConfigCodeFragmentProvider
            )
            ?.let { config ->
                progressTracker.progress(
                    "Generating API version history file ${config.outputFile.name}: "
                )

                apiGenerator.generateApiHistory(config)
            }
    }

    /**
     * Checks compatibility of the given codebase with the codebase described in the signature file.
     */
    private fun checkCompatibility(
        newCodebase: Codebase,
        check: CheckRequest,
    ) {
        progressTracker.progress("Checking API compatibility ($check): ")

        val apiType = check.apiType
        val generatedApiFile =
            when (apiType) {
                ApiType.PUBLIC_API -> signatureFileOptions.apiFile
                ApiType.REMOVED -> signatureFileOptions.removedApiFile
                else -> error("unsupported $apiType")
            }

        // Fast path: if we've already generated a signature file, and it's identical to the
        // previously released API then we're good.
        //
        // Reading two files that may be a couple of MBs each isn't a particularly fast path so
        // check the lengths first and then compare contents byte for byte so that it exits quickly
        // if they're different and does not do all the UTF-8 conversions.
        generatedApiFile?.let { apiFile ->
            val compatibilityCheckCanBeSkipped =
                check.lastSignatureFile?.let { signatureFile ->
                    compareFileContents(apiFile, signatureFile)
                } ?: false
            // TODO(b/301282006): Remove global variable use when this can be tested properly
            check.fastPathCheckResult = compatibilityCheckCanBeSkipped
            if (compatibilityCheckCanBeSkipped) return
        }

        val oldCodebase =
            check.previouslyReleasedApi.load { signatureFiles ->
                signatureFileCache.load(signatureFiles, classPathResolver)
            }

        val apiName =
            if (apiType == ApiType.REMOVED) {
                "removed"
            } else apiSelectionOptions.apiSurface

        // If configured, compares the new API with the previous API and reports any
        // incompatibilities.
        CompatibilityCheck.checkCompatibility(
            newCodebase,
            oldCodebase,
            apiType,
            reporter,
            issueReportingOptions.issueConfiguration,
            compatibilityCheckOptions.apiCompatAnnotations,
            apiName,
            apiPredicateConfig,
            apiSelectionOptions.showUnannotated,
        )
    }

    private fun loadFromSources(): Codebase? {
        progressTracker.progress("Processing sources: ")

        val sourceSet =
            if (sourceOptions.sourceFiles.isEmpty()) {
                if (verbosity.verbose) {
                    executionEnvironment.stdout.println(
                        "No source files specified: recursively including all sources found in the source path (${sourceOptions.sourcePath.joinToString()}})"
                    )
                }
                SourceSet.createFromSourcePath(reporter, sourceOptions.sourcePath)
            } else {
                SourceSet(sourceOptions.sourceFiles, sourceOptions.sourcePath)
            }

        progressTracker.progress("Reading Codebase: ")

        val inputs =
            SourceParser.Inputs(
                sourceSet,
                "Codebase loaded from source folders",
                classPath = sourceOptions.classpath,
                apiPackages = sourceOptions.apiPackageFilter,
                projectDescription = sourceOptions.projectDescription,
                compiledSourceJar = sourceOptions.compiledSourceJar,
            )

        val codebase = sourceParser.parseSources(inputs) ?: return null

        progressTracker.progress("Analyzing API: ")

        val analyzer = ApiAnalyzer(sourceParser, codebase, reporter, apiAnalyzerConfig)
        analyzer.mergeExternalInclusionAnnotations()

        analyzer.computeApi()

        val apiPredicateConfigIgnoreShown = apiPredicateConfig.copy(ignoreShown = true)
        val apiEmitAndReference = ApiPredicate(config = apiPredicateConfigIgnoreShown)

        analyzer.handleFileFacadeClassesAndExperimentalPackages(apiEmitAndReference)

        // Copy methods from soon-to-be-hidden parents into descendant classes, when necessary. Do
        // this before merging annotations or performing checks on the API to ensure that these
        // methods can have annotations added and are checked properly.
        progressTracker.progress("Insert missing stubs methods: ")
        analyzer.generateInheritedStubs(apiEmitAndReference, apiEmitAndReference)

        analyzer.mergeExternalQualifierAnnotations()

        nullabilityValidationOptions.validatorForSources?.let { validator ->
            // Validate any explicitly specified classes.
            validator.validateExplicitlySpecifiedClasses(codebase)

            // Report any issues found in the validator. This can include issues found while merging
            // in annotations.
            validator.report()
        }

        // Prevent the codebase from being mutated.
        codebase.freezeClasses()

        analyzer.handleStripping()

        // General API documentation checks for Android APIs.
        // They are pointless if Javadoc comments are not being read.
        if (codebase.config.allowReadingComments) {
            AndroidApiChecks(reporter, apiPredicateConfig).check(codebase)
        }

        runApiChecksFromOptions(codebase) { codebase, previouslyReleasedCodebase ->
            ApiLint.check(
                codebase,
                previouslyReleasedCodebase,
                reporter,
                apiPredicateConfig,
                ApiLint.Config(
                    manifest = miscellaneousOptions.manifest,
                    allowedAcronyms = apiLintOptions.allowedAcronyms,
                ),
            )
        }

        progressTracker.progress("Performing misc API checks: ")
        analyzer.performChecks()

        return codebase
    }

    fun loadFromJarFile(apiJar: File): Codebase {
        val jarCodebaseLoader =
            JarCodebaseLoader.createForSourceParser(
                progressTracker,
                reporter,
                sourceParser,
            )
        return jarCodebaseLoader.loadFromJarFile(apiJar, apiAnalyzerConfig)
    }

    private fun extractAnnotations(outputFile: File, codebase: Codebase) {
        val localTimer = Stopwatch.createStarted()

        ExtractAnnotations(
                codebase,
                reporter,
                outputFile,
                apiPredicateConfig,
            )
            .extractAnnotations()
        if (verbosity.verbose) {
            progressTracker.progress(
                "$PROGRAM_NAME extracted annotations into $outputFile in ${
                    localTimer.elapsed(
                        SECONDS
                    )
                } seconds\n"
            )
        }
    }
}

fun createOutputFileFromCodebaseFragment(
    progressTracker: ProgressTracker,
    codebaseFragment: CodebaseFragment,
    outputFile: File,
    description: String?,
    deleteEmptyFiles: Boolean = false,
    createVisitorWriter: (PrintWriter) -> DelegatedVisitor,
) {
    if (description != null) {
        progressTracker.progress("Writing $description file: ")
    }
    val localTimer = Stopwatch.createStarted()
    try {
        val stringWriter = StringWriter()
        val writer = PrintWriter(stringWriter)
        writer.use { printWriter ->
            val writerVisitor = createVisitorWriter(printWriter)
            codebaseFragment.accept(writerVisitor)
        }
        val text = stringWriter.toString()
        if (text.isNotEmpty() || !deleteEmptyFiles) {
            outputFile.parentFile.mkdirs()
            outputFile.writeText(text)
        }
    } catch (e: IOException) {
        val codebase = codebaseFragment.codebase
        codebase.reporter.report(Issues.IO_ERROR, outputFile, "Cannot open file for write.")
    }
    if (description != null) {
        progressTracker.progress(
            "$PROGRAM_NAME wrote $description file $outputFile in ${localTimer.elapsed(SECONDS)} seconds\n"
        )
    }
}

/** Compare two files to see if they are byte for byte identical. */
private fun compareFileContents(file1: File, file2: File): Boolean {
    // First check the lengths, if they are different they cannot be identical.
    if (file1.length() == file2.length()) {
        // Then load the contents in chunks to see if they differ.
        file1.inputStream().buffered().use { stream1 ->
            file2.inputStream().buffered().use { stream2 ->
                val buffer1 = ByteArray(DEFAULT_BUFFER_SIZE)
                val buffer2 = ByteArray(DEFAULT_BUFFER_SIZE)
                do {
                    val c1 = stream1.read(buffer1)
                    val c2 = stream2.read(buffer2)
                    if (c1 != c2) {
                        // This should never happen as the files are the same length.
                        break
                    }
                    if (c1 == -1) {
                        // They have both reached the end of file.
                        return true
                    }
                    // Check the buffer contents, if they differ exit the loop otherwise, continue
                    // on to read the next chunks.
                } while (Arrays.equals(buffer1, 0, c1, buffer2, 0, c2))
            }
        }
    }
    return false
}
