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
import com.android.tools.metalava.cli.common.PreviouslyReleasedApi
import com.android.tools.metalava.cli.common.VersionCommand
import com.android.tools.metalava.cli.common.cliError
import com.android.tools.metalava.cli.common.commonOptions
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions.CheckRequest
import com.android.tools.metalava.cli.flag.FlagReportCommand
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
import com.android.tools.metalava.jar.JarCodebaseLoader
import com.android.tools.metalava.lint.ApiLint
import com.android.tools.metalava.lint.FlaggedApiLint
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
import com.android.tools.metalava.model.text.SignatureWriter
import com.android.tools.metalava.model.text.createFilteringVisitorForSignatures
import com.android.tools.metalava.model.visitors.ApiFilters
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.model.visitors.FilteringApiVisitor
import com.android.tools.metalava.model.visitors.MatchOverridingMethodPredicate
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
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
    var exitCode = 0
    try {
        exitCode = run(executionEnvironment = executionEnvironment, originalArgs = args)
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
    val modelOptions = createModelOptions(options, executionEnvironment)
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
    val codebase =
        createCodebaseFromOptions(
            options,
            classResolverProvider,
            signatureFileCache,
            actionContext,
        ) ?: return

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

    options.subtractApiFile?.let {
        progressTracker.progress("Subtracting API: ")
        actionContext.subtractApiFromCodebase(signatureFileCache, codebase, it)
    }

    generateApiHistoryFromOptions(options, codebase, progressTracker)

    enhanceCodebaseDocumentationFromOptions(
        options,
        codebase,
        progressTracker,
        executionEnvironment,
        reporter,
    )

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
    createApiSignatureFilesFromOptions(
        options,
        codebase,
        progressTracker,
        signatureFileCache,
        classResolverProvider,
        reporter
    )

    options.proguard?.let { proguard ->
        val apiPredicateConfig = options.apiPredicateConfig
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

    options.sdkValueDir?.let { dir ->
        dir.mkdirs()
        SdkFileWriter(codebase, dir).generate()
    }

    for (check in options.compatibilityChecks) {
        actionContext.checkCompatibility(signatureFileCache, classResolverProvider, codebase, check)
    }

    convertToWarningNullabilityAnnotations(
        codebase,
        options.migrateNullsFrom,
        options.forceConvertToWarningNullabilityAnnotations,
        signatureFileCache
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

private fun runApiChecksFromOptions(
    options: Options,
    progressTracker: ProgressTracker,
    signatureFileCache: SignatureFileCache,
    classResolverProvider: ClassResolverProvider,
    codebase: Codebase,
    reporter: Reporter,
    apiCheckMethod: (Codebase, Codebase?, Reporter, Options) -> Unit
) {
    options.apiLintOptions.let { apiLintOptions ->
        if (!apiLintOptions.apiLintEnabled) return@let

        progressTracker.progress("API Lint: ")
        val localTimer = Stopwatch.createStarted()

        // See if we should provide a previous codebase to provide a delta from?
        val previouslyReleasedCodebase by lazy {
            apiLintOptions.previouslyReleasedApi?.load { signatureFiles ->
                signatureFileCache.load(signatureFiles, classResolverProvider.classResolver)
            }
        }
        apiCheckMethod(codebase, previouslyReleasedCodebase, reporter, options)
        progressTracker.progress(
            "$PROGRAM_NAME ran api api-lint in ${localTimer.elapsed(SECONDS)} seconds"
        )
    }
}

/** write api signature to files specified by option flags (e.g. current.txt) */
private fun createApiSignatureFilesFromOptions(
    options: Options,
    codebase: Codebase,
    progressTracker: ProgressTracker,
    signatureFileCache: SignatureFileCache,
    classResolverProvider: ClassResolverProvider,
    reporter: Reporter,
) {
    val fileFormat = options.signatureFileFormat
    val codebaseFragment =
        createCodeFragmentForSignatureFile(codebase) { delegate ->
            createFilteringVisitorForSignatures(
                delegate = delegate,
                fileFormat = fileFormat,
                apiType = ApiType.PUBLIC_API,
                preFiltered = codebase.preFiltered,
                showUnannotated = options.showUnannotated,
                apiPredicateConfig = options.apiPredicateConfig,
            )
        }

    runApiChecksFromOptions(
        options,
        progressTracker,
        signatureFileCache,
        classResolverProvider,
        codebase,
        reporter
    ) { _, previouslyReleasedCodebase, reporter, options ->
        val flaggedApiLintVisitor =
            FlaggedApiLint(previouslyReleasedCodebase, reporter, options.apiPredicateConfig)
        codebaseFragment.accept(flaggedApiLintVisitor)
    }

    options.apiSignatureFile?.let { apiSignatureFile ->
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

    options.removedApiSignatureFile?.let { apiSignatureFile ->
        val removedApiCodebaseFragment =
            createCodeFragmentForSignatureFile(codebase) { delegate ->
                createFilteringVisitorForSignatures(
                    delegate = delegate,
                    fileFormat = fileFormat,
                    apiType = ApiType.REMOVED,
                    preFiltered = false,
                    showUnannotated = options.showUnannotated,
                    apiPredicateConfig = options.apiPredicateConfig,
                )
            }

        createOutputFileFromCodebaseFragment(
            progressTracker,
            removedApiCodebaseFragment,
            apiSignatureFile,
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
}

fun createCodeFragmentForSignatureFile(
    codebase: Codebase,
    fragmentFactory: (DelegatedVisitor) -> ItemVisitor
): CodebaseFragment {
    var codebaseFragment = CodebaseFragment.create(codebase, fragmentFactory)

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

/** Depending on option flags, enhance codebase documentation */
private fun enhanceCodebaseDocumentationFromOptions(
    options: Options,
    codebase: Codebase,
    progressTracker: ProgressTracker,
    executionEnvironment: ExecutionEnvironment,
    reporter: Reporter,
) {
    if (options.docStubsDir == null && !options.enhanceDocumentation) return
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
    val applyApiLevelsXmlFile = options.applyApiLevelsXmlFile
    if (applyApiLevelsXmlFile != null) {
        progressTracker.progress("Applying API levels")
        docAnalyzer.applyApiVersions(applyApiLevelsXmlFile)
    }
}

/** Create [ModelOptions] object from option flags */
private fun createModelOptions(
    options: Options,
    executionEnvironment: ExecutionEnvironment
): ModelOptions {
    // If the option was specified on the command line then use [ModelOptions] created from that
    return options.useK2Uast?.let { useK2Uast ->
        ModelOptions.build("from command line") { this[PsiModelOptions.useK2Uast] = useK2Uast }
    }
        // Otherwise, use the [ModelOptions] specified in the [TestEnvironment] if any.
        ?: executionEnvironment.testEnvironment?.modelOptions?.apply {
            // Make sure that the [options.useK2Uast] matches the test environment.
            options.useK2Uast = this[PsiModelOptions.useK2Uast]
        }
        // Otherwise, use the default
        ?: ModelOptions.empty
}

/** Create [Codebase] object from option flags */
private fun createCodebaseFromOptions(
    options: Options,
    classResolverProvider: ClassResolverProvider,
    signatureFileCache: SignatureFileCache,
    actionContext: ActionContext
): Codebase? {
    val sources = options.sources
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
        return signatureFileLoader.load(
            SignatureFile.fromFiles(sources),
            classResolverProvider.classResolver,
        )
    } else if (sources.size == 1 && sources[0].path.endsWith(DOT_JAR)) {
        return actionContext.loadFromJarFile(sources[0])
    } else if (sources.isNotEmpty() || options.sourcePath.isNotEmpty()) {
        return actionContext.loadFromSources(signatureFileCache, classResolverProvider)
    }

    return null
}

/** write api history to files specified by option flags (e.g. api-versions.xml) */
private fun generateApiHistoryFromOptions(
    options: Options,
    codebase: Codebase,
    progressTracker: ProgressTracker
) {
    val androidConfigCodeFragmentProvider: () -> CodebaseFragment = {
        var codebaseFragment =
            CodebaseFragment.create(codebase) { delegatedVisitor ->
                FilteringApiVisitor(
                    delegate = delegatedVisitor,
                    apiFilters = ApiVisitor.defaultFilters(options.apiPredicateConfig),
                    preFiltered = false,
                )
            }

        // If reverting some changes then create a snapshot that combines the items from the
        // sources for any un-reverted changes and items from the previously released API for
        // any reverted changes.
        if (codebaseFragment.codebase.containsRevertedItem) {
            // Allow references to any of the ClassItems in the original Codebase. This
            // should not be a problem for api-versions.xml files as they only refer to
            // them
            // by name and do not care about their contents.
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
        val apiFilters = apiType.getApiFilters(options.apiPredicateConfig)

        CodebaseFragment.create(codebase) { delegatedVisitor ->
            FilteringApiVisitor(
                delegate = delegatedVisitor,
                apiFilters = apiFilters,
                preFiltered = false,
            )
        }
    }

    val apiGenerator = ApiGenerator()
    options.apiLevelsGenerationOptions
        .forAndroidConfig(
            // Do not use a cache here as each file loaded is only loaded once and the created
            // Codebase is discarded immediately after use so caching just uses memory for no
            // performance benefit.
            options.signatureFileLoader,
            androidConfigCodeFragmentProvider,
        )
        ?.let { config ->
            progressTracker.progress(
                "Generating API levels XML descriptor file, ${config.outputFile.name}: "
            )

            apiGenerator.generateApiHistory(config)
        }

    options.apiLevelsGenerationOptions
        .fromSignatureFilesConfig(
            // Do not use a cache here as each file loaded is only loaded once and the created
            // Codebase is discarded immediately after use so caching just uses memory for no
            // performance benefit.
            options.signatureFileLoader,
            codebaseFragmentProvider = signatureFileConfigCodeFragmentProvider
        )
        ?.let { config ->
            progressTracker.progress(
                "Generating API version history file ${config.outputFile.name}: "
            )

            apiGenerator.generateApiHistory(config)
        }
}

private fun ActionContext.subtractApiFromCodebase(
    signatureFileCache: SignatureFileCache,
    codebase: Codebase,
    subtractApiFile: File,
) {
    val path = subtractApiFile.path
    val codebaseToSubtract =
        when {
            path.endsWith(DOT_TXT) ->
                signatureFileCache.load(SignatureFile.fromFiles(subtractApiFile))
            path.endsWith(DOT_JAR) -> loadFromJarFile(subtractApiFile)
            else ->
                cliError(
                    "Unsupported $ARG_SUBTRACT_API format, expected .txt or .jar: ${subtractApiFile.name}"
                )
        }

    // Iterate over the top level classes in the codebase and if they are present in the codebase
    // being subtracted then do not emit the class or any of its nested classes.
    for (classItem in codebase.getTopLevelClassesFromSource()) {
        if (codebaseToSubtract.findClass(classItem.qualifiedName()) != null) {
            stopEmittingClassAndContents(classItem)
        }
    }
}

/** Stop emitting [classItem] and any of its nested classes. */
private fun stopEmittingClassAndContents(classItem: ClassItem) {
    classItem.emit = false
    for (nestedClass in classItem.nestedClasses()) {
        stopEmittingClassAndContents(nestedClass)
    }
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
            ApiType.PUBLIC_API -> options.apiSignatureFile
            ApiType.REMOVED -> options.removedApiSignatureFile
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
            } ?: false
        // TODO(b/301282006): Remove global variable use when this can be tested properly
        fastPathCheckResult = compatibilityCheckCanBeSkipped
        if (compatibilityCheckCanBeSkipped) return
    }

    val oldCodebase =
        check.previouslyReleasedApi.load { signatureFiles ->
            signatureFileCache.load(signatureFiles, classResolverProvider.classResolver)
        }

    val apiName =
        if (apiType == ApiType.REMOVED) {
            "removed"
        } else options.apiSelectionOptions.apiSurface

    // If configured, compares the new API with the previous API and reports any incompatibilities.
    CompatibilityCheck.checkCompatibility(
        newCodebase,
        oldCodebase,
        apiType,
        reporter,
        options.issueConfiguration,
        options.apiCompatAnnotations,
        apiName,
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

private fun convertToWarningNullabilityAnnotations(
    codebase: Codebase,
    previouslyReleasedApi: PreviouslyReleasedApi?,
    filter: PackageFilter?,
    signatureFileCache: SignatureFileCache
) {
    if (previouslyReleasedApi != null) {
        val previousCodebase =
            previouslyReleasedApi.load { signatureFiles -> signatureFileCache.load(signatureFiles) }

        // If configured, checks for newly added nullness information compared
        // to the previous stable API and marks the newly annotated elements
        // as migrated (which will cause the Kotlin compiler to treat problems
        // as warnings instead of errors

        NullnessMigration.migrateNulls(codebase, previousCodebase)

        previousCodebase.dispose()
    }

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
            compiledSourceJar = options.compiledSourceJar
        )

    progressTracker.progress("Analyzing API: ")

    val analyzer = ApiAnalyzer(sourceParser, codebase, reporterApiLint, options.apiAnalyzerConfig)
    analyzer.mergeExternalInclusionAnnotations()

    analyzer.computeApi()

    val apiPredicateConfigIgnoreShown = options.apiPredicateConfig.copy(ignoreShown = true)
    val apiEmitAndReference = ApiPredicate(config = apiPredicateConfigIgnoreShown)

    analyzer.handleFileFacadeClassesAndExperimentalPackages(apiEmitAndReference)

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

    runApiChecksFromOptions(
        options,
        progressTracker,
        signatureFileCache,
        classResolverProvider,
        codebase,
        reporter
    ) { codebase, previouslyReleasedCodebase, reporter, options ->
        ApiLint.check(
            codebase,
            previouslyReleasedCodebase,
            reporter,
            options.manifest,
            options.apiPredicateConfig,
            options.apiLintOptions.allowedAcronyms,
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
    if (codebaseFragment.codebase.containsRevertedItem) {
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
        SignatureToDexCommand(),
        SignatureToJDiffCommand(),
        UpdateSignatureHeaderCommand(),
        VersionCommand(),
    )
    return command
}
