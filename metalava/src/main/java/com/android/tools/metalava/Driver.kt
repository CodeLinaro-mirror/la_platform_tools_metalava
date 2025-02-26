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
@file:JvmName("Driver")

package com.android.tools.metalava

import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_TXT
import com.android.tools.metalava.apilevels.ApiGenerator
import com.android.tools.metalava.cli.common.ActionContext
import com.android.tools.metalava.cli.common.CheckerContext
import com.android.tools.metalava.cli.common.EarlyOptions
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.common.MetalavaCommand
import com.android.tools.metalava.cli.common.VersionCommand
import com.android.tools.metalava.cli.common.cliError
import com.android.tools.metalava.cli.common.commonOptions
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions.CheckRequest
import com.android.tools.metalava.cli.help.HelpCommand
import com.android.tools.metalava.cli.historical.AndroidJarsToSignaturesCommand
import com.android.tools.metalava.cli.internal.MakeAnnotationsPackagePrivateCommand
import com.android.tools.metalava.cli.signature.MergeSignaturesCommand
import com.android.tools.metalava.cli.signature.SignatureCatCommand
import com.android.tools.metalava.cli.signature.SignatureToDexCommand
import com.android.tools.metalava.cli.signature.SignatureToJDiffCommand
import com.android.tools.metalava.cli.signature.UpdateSignatureHeaderCommand
import com.android.tools.metalava.compatibility.CompatibilityCheck
import com.android.tools.metalava.doc.DocAnalyzer
import com.android.tools.metalava.lint.ApiLint
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.ItemVisitor
import com.android.tools.metalava.model.ModelOptions
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.psi.PsiModelOptions
import com.android.tools.metalava.model.snapshot.NonFilteringDelegatingVisitor
import com.android.tools.metalava.model.source.EnvironmentManager
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.text.ApiClassResolution
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.visitors.ApiFilters
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.model.visitors.FilteringApiVisitor
import com.android.tools.metalava.model.visitors.MatchOverridingMethodPredicate
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.stub.StubConstructorManager
import com.android.tools.metalava.stub.StubWriter
import com.android.tools.metalava.stub.createFilteringVisitorForStubs
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

fun main(args: Array<String>) {
    val executionEnvironment = ExecutionEnvironment()
    val exitCode = run(executionEnvironment = executionEnvironment, originalArgs = args)

    executionEnvironment.stdout.flush()
    executionEnvironment.stderr.flush()

    exitProcess(exitCode)
}

/**
 * The metadata driver is a command line interface to extracting various metadata from a source tree
 * (or existing signature files etc.). Run with --help to see more details.
 */
fun run(
    executionEnvironment: ExecutionEnvironment,
    originalArgs: Array<String>,
): Int {
    val stdout = executionEnvironment.stdout
    val stderr = executionEnvironment.stderr

    // Preprocess the arguments by adding any additional arguments specified in environment
    // variables.
    val modifiedArgs = preprocessArgv(executionEnvironment, originalArgs)

    // Process the early options. This does not consume any arguments, they will be parsed again
    // later. A little inefficient but produces cleaner code.
    val earlyOptions = EarlyOptions.parse(modifiedArgs)

    val progressTracker = ProgressTracker(earlyOptions.verbosity.verbose, stdout)

    progressTracker.progress("$PROGRAM_NAME started\n")

    // Dump the arguments, and maybe generate a rerun-script.
    maybeDumpArgv(executionEnvironment, originalArgs, modifiedArgs)

    // Actual work begins here.
    val command =
        createMetalavaCommand(
            executionEnvironment,
            progressTracker,
        )
    val exitCode = command.process(modifiedArgs)

    stdout.flush()
    stderr.flush()

    progressTracker.progress("$PROGRAM_NAME exiting with exit code $exitCode\n")

    return exitCode
}

@Suppress("DEPRECATION")
internal fun processFlags(
    executionEnvironment: ExecutionEnvironment,
    environmentManager: EnvironmentManager,
    progressTracker: ProgressTracker
) {
    val stopwatch = Stopwatch.createStarted()

    val reporter = options.reporter

    val codebaseConfig = options.codebaseConfig
    val modelOptions =
        // If the option was specified on the command line then use [ModelOptions] created from
        // that.
        options.useK2Uast?.let { useK2Uast ->
            ModelOptions.build("from command line") { this[PsiModelOptions.useK2Uast] = useK2Uast }
        }
        // Otherwise, use the [ModelOptions] specified in the [TestEnvironment] if any.
        ?: executionEnvironment.testEnvironment?.modelOptions?.apply {
                // Make sure that the [options.useK2Uast] matches the test environment.
                options.useK2Uast = this[PsiModelOptions.useK2Uast]
            }
            // Otherwise, use the default
            ?: ModelOptions.empty
    val sourceParser =
        environmentManager.createSourceParser(
            codebaseConfig = codebaseConfig,
            javaLanguageLevel = options.javaLanguageLevelAsString,
            kotlinLanguageLevel = options.kotlinLanguageLevelAsString,
            modelOptions = modelOptions,
            allowReadingComments = options.allowReadingComments,
            jdkHome = options.jdkHome,
        )

    val signatureFileCache = options.signatureFileCache

    val actionContext =
        ActionContext(
            progressTracker = progressTracker,
            reporter = reporter,
            reporterApiLint = reporter,
            sourceParser = sourceParser,
        )

    val classResolverProvider =
        ClassResolverProvider(
            sourceParser = sourceParser,
            apiClassResolution = options.apiClassResolution,
            classpath = options.classpath,
        )

    val sources = options.sources
    val codebase =
        if (sources.isNotEmpty() && sources[0].path.endsWith(DOT_TXT)) {
            // Make sure all the source files have .txt extensions.
            sources
                .firstOrNull { !it.path.endsWith(DOT_TXT) }
                ?.let {
                    cliError(
                        "Inconsistent input file types: The first file is of $DOT_TXT, but detected different extension in ${it.path}"
                    )
                }
            val signatureFileLoader = options.signatureFileLoader
            signatureFileLoader.load(
                SignatureFile.fromFiles(sources),
                classResolverProvider.classResolver,
            )
        } else if (sources.size == 1 && sources[0].path.endsWith(DOT_JAR)) {
            actionContext.loadFromJarFile(sources[0])
        } else if (sources.isNotEmpty() || options.sourcePath.isNotEmpty()) {
            actionContext.loadFromSources(signatureFileCache, classResolverProvider)
        } else {
            return
        }

    // If provided by a test, run some additional checks on the internal state of this.
    executionEnvironment.testEnvironment?.let { testEnvironment ->
        testEnvironment.postAnalysisChecker?.let { function ->
            val context = CheckerContext(options, codebase)
            context.function()
        }
    }

    progressTracker.progress(
        "$PROGRAM_NAME analyzed API in ${stopwatch.elapsed(SECONDS)} seconds\n"
    )

    options.subtractApi?.let {
        progressTracker.progress("Subtracting API: ")
        actionContext.subtractApi(signatureFileCache, codebase, it)
    }

    val generateXmlConfig =
        options.apiLevelsGenerationOptions.forAndroidConfig(
            // Do not use a cache here as each file loaded is only loaded once and the created
            // Codebase is discarded immediately after use so caching just uses memory for no
            // performance benefit.
            options.signatureFileLoader,
        ) {
            var codebaseFragment =
                CodebaseFragment.create(codebase) { delegatedVisitor ->
                    FilteringApiVisitor(
                        delegate = delegatedVisitor,
                        apiFilters = ApiVisitor.defaultFilters(options.apiPredicateConfig),
                        preFiltered = false,
                    )
                }

            // If reverting some changes then create a snapshot that combines the items from the
            // sources
            // for any un-reverted changes and items from the previously released API for any
            // reverted
            // changes.
            if (options.revertAnnotations.isNotEmpty()) {
                codebaseFragment =
                    codebaseFragment.snapshotIncludingRevertedItems(
                        // Allow references to any of the ClassItems in the original Codebase. This
                        // should not be a problem for api-versions.xml files as they only refer to
                        // them
                        // by name and do not care about their contents.
                        referenceVisitorFactory = ::NonFilteringDelegatingVisitor,
                    )
            }

            codebaseFragment
        }
    val apiGenerator = ApiGenerator()
    if (generateXmlConfig != null) {
        progressTracker.progress(
            "Generating API levels XML descriptor file, ${generateXmlConfig.outputFile.name}: "
        )

        apiGenerator.generateApiHistory(generateXmlConfig)
    }

    if (options.docStubsDir != null || options.enhanceDocumentation) {
        if (!codebase.supportsDocumentation()) {
            error("Codebase does not support documentation, so it cannot be enhanced.")
        }
        progressTracker.progress("Enhancing docs: ")
        val docAnalyzer =
            DocAnalyzer(
                executionEnvironment,
                codebase,
                reporter,
                options.apiVersionLabelProvider,
                options.includeApiLevelInDocumentation,
                options.apiPredicateConfig,
            )
        docAnalyzer.enhance()
        val applyApiLevelsXml = options.applyApiLevelsXml
        if (applyApiLevelsXml != null) {
            progressTracker.progress("Applying API levels")
            docAnalyzer.applyApiVersions(applyApiLevelsXml)
        }
    }

    options.apiLevelsGenerationOptions
        .fromSignatureFilesConfig(
            // Do not use a cache here as each file loaded is only loaded once and the created
            // Codebase is discarded immediately after use so caching just uses memory for no
            // performance benefit.
            options.signatureFileLoader,
            // Provide a CodebaseFragment from the sources that will be included in the generated
            // version history.
            codebaseFragmentProvider = {
                val apiType = ApiType.PUBLIC_API
                val apiFilters = apiType.getApiFilters(options.apiPredicateConfig)

                CodebaseFragment.create(codebase) { delegatedVisitor ->
                    FilteringApiVisitor(
                        delegate = delegatedVisitor,
                        apiFilters = apiFilters,
                        preFiltered = false,
                    )
                }
            }
        )
        ?.let { config ->
            progressTracker.progress(
                "Generating API version history file ${config.outputFile.name}: "
            )

            apiGenerator.generateApiHistory(config)
        }

    // Generate the documentation stubs *before* we migrate nullness information.
    options.docStubsDir?.let {
        createStubFiles(
            progressTracker,
            it,
            codebase,
            docStubs = true,
        )
    }

    // Based on the input flags, generates various output files such as signature files and/or stubs
    // files
    options.apiFile?.let { apiFile ->
        val fileFormat = options.signatureFileFormat
        var codebaseFragment =
            CodebaseFragment.create(codebase) { delegate ->
                createFilteringVisitorForSignatures(
                    delegate = delegate,
                    fileFormat = fileFormat,
                    apiType = ApiType.PUBLIC_API,
                    preFiltered = codebase.preFiltered,
                    showUnannotated = options.showUnannotated,
                    apiPredicateConfig = options.apiPredicateConfig,
                )
            }

        // If reverting some changes then create a snapshot that combines the items from the sources
        // for any un-reverted changes and items from the previously released API for any reverted
        // changes.
        if (options.revertAnnotations.isNotEmpty()) {
            codebaseFragment =
                codebaseFragment.snapshotIncludingRevertedItems(
                    // Allow references to any of the ClassItems in the original Codebase. This
                    // should not be a problem for signature files as they only refer to them by
                    // name and do not care about their contents.
                    referenceVisitorFactory = ::NonFilteringDelegatingVisitor,
                )
        }

        createReportFile(progressTracker, codebaseFragment, apiFile, "API") { printWriter ->
            SignatureWriter(
                writer = printWriter,
                fileFormat = fileFormat,
            )
        }
    }

    options.removedApiFile?.let { apiFile ->
        val fileFormat = options.signatureFileFormat
        var codebaseFragment =
            CodebaseFragment.create(codebase) { delegate ->
                createFilteringVisitorForSignatures(
                    delegate = delegate,
                    fileFormat = fileFormat,
                    apiType = ApiType.REMOVED,
                    preFiltered = false,
                    showUnannotated = options.showUnannotated,
                    apiPredicateConfig = options.apiPredicateConfig,
                )
            }

        // If reverting some changes then create a snapshot that combines the items from the sources
        // for any un-reverted changes and items from the previously released API for any reverted
        // changes.
        if (options.revertAnnotations.isNotEmpty()) {
            codebaseFragment =
                codebaseFragment.snapshotIncludingRevertedItems(
                    // Allow references to any of the ClassItems in the original Codebase. This
                    // should not be a problem for signature files as they only refer to them by
                    // name and do not care about their contents.
                    referenceVisitorFactory = ::NonFilteringDelegatingVisitor,
                )
        }

        createReportFile(
            progressTracker,
            codebaseFragment,
            apiFile,
            "removed API",
            options.deleteEmptyRemovedSignatures
        ) { printWriter ->
            SignatureWriter(
                writer = printWriter,
                emitHeader = options.includeSignatureFormatVersionRemoved,
                fileFormat = fileFormat,
            )
        }
    }

    options.proguard?.let { proguard ->
        val apiPredicateConfig = options.apiPredicateConfig
        val apiPredicateConfigIgnoreShown = apiPredicateConfig.copy(ignoreShown = true)
        val apiReferenceIgnoreShown = ApiPredicate(config = apiPredicateConfigIgnoreShown)
        val apiEmit = MatchOverridingMethodPredicate(ApiPredicate(config = apiPredicateConfig))
        val apiFilters = ApiFilters(emit = apiEmit, reference = apiReferenceIgnoreShown)
        createReportFile(progressTracker, codebase, proguard, "Proguard file") { printWriter ->
            ProguardWriter(printWriter).let { proguardWriter ->
                FilteringApiVisitor(
                    proguardWriter,
                    inlineInheritedFields = true,
                    apiFilters = apiFilters,
                    preFiltered = codebase.preFiltered,
                )
            }
        }
    }

    options.sdkValueDir?.let { dir ->
        dir.mkdirs()
        SdkFileWriter(codebase, dir).generate()
    }

    for (check in options.compatibilityChecks) {
        actionContext.checkCompatibility(signatureFileCache, classResolverProvider, codebase, check)
    }

    val previouslyReleasedApi = options.migrateNullsFrom
    if (previouslyReleasedApi != null) {
        val previous =
            previouslyReleasedApi.load { signatureFiles -> signatureFileCache.load(signatureFiles) }

        // If configured, checks for newly added nullness information compared
        // to the previous stable API and marks the newly annotated elements
        // as migrated (which will cause the Kotlin compiler to treat problems
        // as warnings instead of errors

        NullnessMigration.migrateNulls(codebase, previous)

        previous.dispose()
    }

    convertToWarningNullabilityAnnotations(
        codebase,
        options.forceConvertToWarningNullabilityAnnotations
    )

    // Now that we've migrated nullness information we can proceed to write non-doc stubs, if any.

    options.stubsDir?.let {
        createStubFiles(
            progressTracker,
            it,
            codebase,
            docStubs = false,
        )
    }

    options.externalAnnotations?.let { extractAnnotations(progressTracker, codebase, it) }

    val packageCount = codebase.size()
    progressTracker.progress(
        "$PROGRAM_NAME finished handling $packageCount packages in ${stopwatch.elapsed(SECONDS)} seconds\n"
    )
}

private fun ActionContext.subtractApi(
    signatureFileCache: SignatureFileCache,
    codebase: Codebase,
    subtractApiFile: File,
) {
    val path = subtractApiFile.path
    val oldCodebase =
        when {
            path.endsWith(DOT_TXT) ->
                signatureFileCache.load(SignatureFile.fromFiles(subtractApiFile))
            path.endsWith(DOT_JAR) -> loadFromJarFile(subtractApiFile)
            else ->
                cliError(
                    "Unsupported $ARG_SUBTRACT_API format, expected .txt or .jar: ${subtractApiFile.name}"
                )
        }

    @Suppress("DEPRECATION")
    CodebaseComparator()
        .compare(
            object : ComparisonVisitor() {
                override fun compareClassItems(old: ClassItem, new: ClassItem) {
                    new.emit = false
                }
            },
            oldCodebase,
            codebase,
            ApiType.ALL.getReferenceFilter(options.apiPredicateConfig)
        )
}

/** Checks compatibility of the given codebase with the codebase described in the signature file. */
@Suppress("DEPRECATION")
private fun ActionContext.checkCompatibility(
    signatureFileCache: SignatureFileCache,
    classResolverProvider: ClassResolverProvider,
    newCodebase: Codebase,
    check: CheckRequest,
) {
    progressTracker.progress("Checking API compatibility ($check): ")

    val apiType = check.apiType
    val generatedApiFile =
        when (apiType) {
            ApiType.PUBLIC_API -> options.apiFile
            ApiType.REMOVED -> options.removedApiFile
            else -> error("unsupported $apiType")
        }

    // Fast path: if we've already generated a signature file, and it's identical to the previously
    // released API then we're good.
    //
    // Reading two files that may be a couple of MBs each isn't a particularly fast path so check
    // the lengths first and then compare contents byte for byte so that it exits quickly if they're
    // different and does not do all the UTF-8 conversions.
    generatedApiFile?.let { apiFile ->
        val compatibilityCheckCanBeSkipped =
            check.lastSignatureFile?.let { signatureFile ->
                compareFileContents(apiFile, signatureFile)
            }
                ?: false
        // TODO(b/301282006): Remove global variable use when this can be tested properly
        fastPathCheckResult = compatibilityCheckCanBeSkipped
        if (compatibilityCheckCanBeSkipped) return
    }

    val oldCodebase =
        check.previouslyReleasedApi.load { signatureFiles ->
            signatureFileCache.load(signatureFiles, classResolverProvider.classResolver)
        }

    // If configured, compares the new API with the previous API and reports
    // any incompatibilities.
    CompatibilityCheck.checkCompatibility(
        newCodebase,
        oldCodebase,
        apiType,
        reporter,
        options.issueConfiguration,
        options.apiCompatAnnotations,
    )
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

/**
 * Used to store whether the fast path check in the previous method succeeded or not that can be
 * checked by tests.
 *
 * The test must initialize it to `null`. Then if the fast path check is run it will set it a
 * non-null to indicate whether the fast path was taken or not. The test can then differentiate
 * between the following states:
 * * `null` - the fast path check was not performed.
 * * `false` - the fast path check was performed and the fast path was not taken.
 * * `true` - the fast path check was performed and the fast path was taken.
 *
 * This is used because there is no nice way to test this code in isolation but the code needs to be
 * updated to deal with some test failures. This is a hack to avoid a catch-22 where this code needs
 * to be refactored to allow it to be tested but it needs to be tested before it can be safely
 * refactored.
 *
 * TODO(b/301282006): Remove this variable when the fast path this can be tested properly
 */
internal var fastPathCheckResult: Boolean? = null

private fun convertToWarningNullabilityAnnotations(codebase: Codebase, filter: PackageFilter?) {
    if (filter != null) {
        // Our caller has asked for these APIs to not trigger nullness errors (only warnings) if
        // their callers make incorrect nullness assumptions (for example, calling a function on a
        // reference of nullable type). The way to communicate this to kotlinc is to mark these
        // APIs as RecentlyNullable/RecentlyNonNull
        codebase.accept(MarkPackagesAsRecent(filter))
    }
}

@Suppress("DEPRECATION")
private fun ActionContext.loadFromSources(
    signatureFileCache: SignatureFileCache,
    classResolverProvider: ClassResolverProvider,
): Codebase {
    progressTracker.progress("Processing sources: ")

    val sourceSet =
        if (options.sources.isEmpty()) {
            if (options.verbose) {
                options.stdout.println(
                    "No source files specified: recursively including all sources found in the source path (${options.sourcePath.joinToString()}})"
                )
            }
            SourceSet.createFromSourcePath(options.reporter, options.sourcePath)
        } else {
            SourceSet(options.sources, options.sourcePath)
        }

    progressTracker.progress("Reading Codebase: ")
    val codebase =
        sourceParser.parseSources(
            sourceSet,
            "Codebase loaded from source folders",
            classPath = options.classpath,
            apiPackages = options.apiPackages,
            projectDescription = options.projectDescription,
        )

    progressTracker.progress("Analyzing API: ")

    val analyzer = ApiAnalyzer(sourceParser, codebase, reporterApiLint, options.apiAnalyzerConfig)
    analyzer.mergeExternalInclusionAnnotations()

    analyzer.computeApi()

    val apiPredicateConfigIgnoreShown = options.apiPredicateConfig.copy(ignoreShown = true)
    val apiEmitAndReference = ApiPredicate(config = apiPredicateConfigIgnoreShown)

    // Copy methods from soon-to-be-hidden parents into descendant classes, when necessary. Do
    // this before merging annotations or performing checks on the API to ensure that these methods
    // can have annotations added and are checked properly.
    progressTracker.progress("Insert missing stubs methods: ")
    analyzer.generateInheritedStubs(apiEmitAndReference, apiEmitAndReference)

    analyzer.mergeExternalQualifierAnnotations()
    options.nullabilityAnnotationsValidator?.validateAllFrom(
        codebase,
        options.validateNullabilityFromList
    )
    options.nullabilityAnnotationsValidator?.report()

    // Prevent the codebase from being mutated.
    codebase.freezeClasses()

    analyzer.handleStripping()

    // General API checks for Android APIs
    AndroidApiChecks(reporterApiLint).check(codebase)

    options.apiLintOptions.let { apiLintOptions ->
        if (!apiLintOptions.apiLintEnabled) return@let

        progressTracker.progress("API Lint: ")
        val localTimer = Stopwatch.createStarted()

        // See if we should provide a previous codebase to provide a delta from?
        val previouslyReleasedApi =
            apiLintOptions.previouslyReleasedApi?.load { signatureFiles ->
                signatureFileCache.load(signatureFiles, classResolverProvider.classResolver)
            }

        ApiLint.check(
            codebase,
            previouslyReleasedApi,
            reporter,
            options.manifest,
            options.apiPredicateConfig,
            options.apiLintOptions.allowedAcronyms,
        )
        progressTracker.progress(
            "$PROGRAM_NAME ran api-lint in ${localTimer.elapsed(SECONDS)} seconds"
        )
    }

    progressTracker.progress("Performing misc API checks: ")
    analyzer.performChecks()

    return codebase
}

/**
 * Avoids creating a [ClassResolver] unnecessarily as it is expensive to create but once created
 * allows it to be reused for the same reason.
 */
private class ClassResolverProvider(
    private val sourceParser: SourceParser,
    private val apiClassResolution: ApiClassResolution,
    private val classpath: List<File>
) {
    val classResolver: ClassResolver? by lazy {
        if (apiClassResolution == ApiClassResolution.API_CLASSPATH && classpath.isNotEmpty()) {
            sourceParser.getClassResolver(classpath)
        } else {
            null
        }
    }
}

fun ActionContext.loadFromJarFile(
    apiJar: File,
    apiAnalyzerConfig: ApiAnalyzer.Config = @Suppress("DEPRECATION") options.apiAnalyzerConfig,
): Codebase {
    val jarCodebaseLoader =
        JarCodebaseLoader.createForSourceParser(
            progressTracker,
            reporterApiLint,
            sourceParser,
        )
    return jarCodebaseLoader.loadFromJarFile(apiJar, apiAnalyzerConfig)
}

@Suppress("DEPRECATION")
private fun extractAnnotations(progressTracker: ProgressTracker, codebase: Codebase, file: File) {
    val localTimer = Stopwatch.createStarted()

    options.externalAnnotations?.let { outputFile ->
        ExtractAnnotations(codebase, options.reporter, outputFile).extractAnnotations()
        if (options.verbose) {
            progressTracker.progress(
                "$PROGRAM_NAME extracted annotations into $file in ${localTimer.elapsed(SECONDS)} seconds\n"
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun createStubFiles(
    progressTracker: ProgressTracker,
    stubDir: File,
    codebase: Codebase,
    docStubs: Boolean,
) {
    if (docStubs) {
        progressTracker.progress("Generating documentation stub files: ")
    } else {
        progressTracker.progress("Generating stub files: ")
    }

    val localTimer = Stopwatch.createStarted()

    val stubWriterConfig =
        options.stubWriterConfig.let {
            if (docStubs) {
                // Doc stubs always include documentation.
                it.copy(includeDocumentationInStubs = true)
            } else {
                it
            }
        }

    var codebaseFragment =
        CodebaseFragment.create(codebase) { delegate ->
            createFilteringVisitorForStubs(
                delegate = delegate,
                docStubs = docStubs,
                preFiltered = codebase.preFiltered,
                apiPredicateConfig = options.apiPredicateConfig,
            )
        }

    // If reverting some changes then create a snapshot that combines the items from the sources for
    // any un-reverted changes and items from the previously released API for any reverted changes.
    if (options.revertAnnotations.isNotEmpty()) {
        codebaseFragment =
            codebaseFragment.snapshotIncludingRevertedItems(
                referenceVisitorFactory = { delegate ->
                    createFilteringVisitorForStubs(
                        delegate = delegate,
                        docStubs = docStubs,
                        preFiltered = codebase.preFiltered,
                        apiPredicateConfig = options.apiPredicateConfig,
                        ignoreEmit = true,
                    )
                },
            )
    }

    // Add additional constructors needed by the stubs.
    val filterEmit =
        if (codebaseFragment.codebase.preFiltered) {
            FilterPredicate { true }
        } else {
            val apiPredicateConfigIgnoreShown = options.apiPredicateConfig.copy(ignoreShown = true)
            ApiPredicate(ignoreRemoved = false, config = apiPredicateConfigIgnoreShown)
        }
    val stubConstructorManager = StubConstructorManager(codebaseFragment.codebase)
    stubConstructorManager.addConstructors(filterEmit)

    val stubWriter =
        StubWriter(
            stubsDir = stubDir,
            generateAnnotations = options.generateAnnotations,
            docStubs = docStubs,
            reporter = options.reporter,
            config = stubWriterConfig,
            stubConstructorManager = stubConstructorManager,
        )

    codebaseFragment.accept(stubWriter)

    if (docStubs) {
        // Overview docs? These are generally in the empty package.
        codebase.findPackage("")?.let { empty ->
            val overview = empty.overviewDocumentation
            if (overview != null) {
                stubWriter.writeDocOverview(empty, overview)
            }
        }
    }

    progressTracker.progress(
        "$PROGRAM_NAME wrote ${if (docStubs) "documentation" else ""} stubs directory $stubDir in ${
        localTimer.elapsed(SECONDS)} seconds\n"
    )
}

fun createReportFile(
    progressTracker: ProgressTracker,
    codebaseFragment: CodebaseFragment,
    apiFile: File,
    description: String?,
    deleteEmptyFiles: Boolean = false,
    createVisitorWriter: (PrintWriter) -> DelegatedVisitor,
) {
    createReportFile(
        progressTracker,
        codebaseFragment.codebase,
        apiFile,
        description,
        deleteEmptyFiles,
    ) {
        val delegatedWriter = createVisitorWriter(it)
        codebaseFragment.createVisitor(delegatedWriter)
    }
}

fun createReportFile(
    progressTracker: ProgressTracker,
    codebase: Codebase,
    apiFile: File,
    description: String?,
    deleteEmptyFiles: Boolean = false,
    createVisitor: (PrintWriter) -> ItemVisitor
) {
    if (description != null) {
        progressTracker.progress("Writing $description file: ")
    }
    val localTimer = Stopwatch.createStarted()
    try {
        val stringWriter = StringWriter()
        val writer = PrintWriter(stringWriter)
        writer.use { printWriter ->
            val apiWriter = createVisitor(printWriter)
            codebase.accept(apiWriter)
        }
        val text = stringWriter.toString()
        if (text.isNotEmpty() || !deleteEmptyFiles) {
            apiFile.parentFile.mkdirs()
            apiFile.writeText(text)
        }
    } catch (e: IOException) {
        codebase.reporter.report(Issues.IO_ERROR, apiFile, "Cannot open file for write.")
    }
    if (description != null) {
        progressTracker.progress(
            "$PROGRAM_NAME wrote $description file $apiFile in ${localTimer.elapsed(SECONDS)} seconds\n"
        )
    }
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
        HelpCommand(),
        JarToJDiffCommand(),
        MakeAnnotationsPackagePrivateCommand(),
        MergeSignaturesCommand(),
        SignatureCatCommand(),
        SignatureToDexCommand(),
        SignatureToJDiffCommand(),
        UpdateSignatureHeaderCommand(),
        VersionCommand(),
    )
    return command
}
