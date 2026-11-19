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

package com.android.tools.metalava.cli.signature

import com.android.tools.metalava.ARG_API_SURFACE
import com.android.tools.metalava.ARG_CONFIG_FILE
import com.android.tools.metalava.ARG_FORCE_CONVERT_TO_WARNING_NULLABILITY_ANNOTATIONS
import com.android.tools.metalava.ARG_STUBS
import com.android.tools.metalava.ApiFlagsCreator
import com.android.tools.metalava.ConfigFileOptions
import com.android.tools.metalava.PROGRAM_NAME
import com.android.tools.metalava.cli.common.DefaultSignatureFileLoader
import com.android.tools.metalava.cli.common.IssueReportingOptions
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.commonOptions
import com.android.tools.metalava.cli.common.executionEnvironment
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.newDir
import com.android.tools.metalava.cli.common.registerPostCommandAction
import com.android.tools.metalava.cli.common.splitMultiple
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.cli.common.tracer
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.api.surface.ApiSurfacePredicate
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.reporter.DefaultReporter
import com.android.tools.metalava.stub.StubGenerator
import com.android.tools.metalava.stub.StubWriterConfig
import com.android.tools.metalava.trace
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import java.io.File

/** Name of the `--api-path` option specifying signature files on the API path. */
const val ARG_API_PATH = "--api-path"

/**
 * Command that generates stub source files directly from API signature files without parsing Java
 * or Kotlin source files.
 */
class SignatureToStubsCommand :
    MetalavaSubCommand(
        help = "Generate stub source files from API signature files.",
    ) {

    /** Options for loading configuration files. */
    private val configFileOptions by ConfigFileOptions()

    /** Options for configuring issue reporting. */
    private val issueReportingOptions by IssueReportingOptions()

    /**
     * Signature files defining referenced APIs (analogous to `--classpath` for source files).
     *
     * Classes loaded from these files are created with [ClassOrigin.CLASS_PATH] so that they can be
     * resolved (for example, to determine superclass constructors or annotation retention) without
     * having stub `.java` files emitted for them unless they are also declared in [sourceFiles].
     */
    private val apiPath by
        option(
                ARG_API_PATH,
                metavar = "<paths>",
                help =
                    """
                        One or more signature files (separated by `${File.pathSeparator}`) defining
                        APIs that may be referenced by the <files> signature files but for which
                        stubs should not be generated.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .splitMultiple(File.pathSeparator)

    /**
     * Optional name of the target API surface being generated. Must correspond to an
     * `<api-surface>` element in `--config-file` when specified.
     */
    private val apiSurfaceName by
        option(
            ARG_API_SURFACE,
            metavar = "<surface>",
            help =
                """
                    The API surface currently being generated. Must correspond to an <api-surface>
                    element in a $ARG_CONFIG_FILE.
                """
                    .trimIndent(),
        )

    /**
     * Optional [PackageFilter] specifying packages in which `@Nullable`/`@NonNull` annotations in
     * generated stubs should be replaced with `@RecentlyNullable`/`@RecentlyNonNull`.
     */
    private val forceConvertToWarningNullabilityAnnotations by
        option(
                ARG_FORCE_CONVERT_TO_WARNING_NULLABILITY_ANNOTATIONS,
                metavar = "<package1:-package2:...>",
                help =
                    """
                        On every API declared in a class referenced by the given filter, makes
                        nullability issues appear to callers as warnings rather than errors by
                        replacing @Nullable/@NonNull in these APIs with
                        @RecentlyNullable/@RecentlyNonNull.

                        See `metalava help package-filters` for more information.
                    """
                        .trimIndent(),
            )
            .convert { PackageFilter.parse(it) }

    /** The directory into which generated stub `.java` files will be written. */
    private val stubsDir by
        option(
                ARG_STUBS,
                metavar = "<dir>",
                help = "Base directory to output the generated stub source files for the API.",
            )
            .newDir()
            .required()

    /**
     * The list of signature files defining the API for which stubs will be generated. When
     * generating stubs for an extending API surface, this includes the base surface signature files
     * followed by the extending surface signature file.
     */
    private val sourceFiles by
        argument(
                name = "<files>",
                help = "Signature files from which stub source files will be generated.",
            )
            .existingFile()
            .multiple(required = true)

    override fun run() {
        val config = configFileOptions.config

        // Configure the issue reporter from CLI options and any <issues> elements in config files.
        val computedIssueReportingOptions =
            issueReportingOptions.compute(commonOptions, config.issues)
        val reporter =
            DefaultReporter(
                environment = executionEnvironment.reporterEnvironment,
                issueConfiguration = computedIssueReportingOptions.issueConfiguration,
                config = computedIssueReportingOptions.reporterConfig,
            )

        // Ensure the suppressed-issues writer is closed when the command finishes.
        registerPostCommandAction {
            computedIssueReportingOptions.reporterConfig.reportEvenIfSuppressedWriter?.close()
        }

        try {
            // Create ApiFlags and ApiSurfaces from the loaded configuration.
            val apiFlags = ApiFlagsCreator.createFromConfig(config.apiFlags)
            val apiSurfaces =
                apiSurfaceName?.let { configFileOptions.apiSurfacesForTargetSurface(it) }
                    ?: ApiSurfaces.DEFAULT
            val apiSurface = apiSurfaces.main

            // Configure the annotation manager so that annotations (such as @FlaggedApi) are
            // normalized, reverted, or converted to @RequiresFlag as appropriate.
            val annotationManager =
                DefaultAnnotationManager(
                    DefaultAnnotationManager.Config(
                        reporter = reporter,
                        annotationClassPredicate = ApiSurfacePredicate.wholeCoreApi(apiSurface),
                        apiFlags = apiFlags,
                        annotationClassTargets =
                            config.annotationClasses?.toAnnotationClassTargets() ?: emptyMap(),
                    )
                )

            val codebaseConfig =
                Codebase.Config(
                    annotationManager = annotationManager,
                    apiFlags = apiFlags,
                    apiSurfaces = apiSurfaces,
                    reporter = reporter,
                )

            val signatureFileLoader = DefaultSignatureFileLoader(codebaseConfig, tracer = tracer)

            // Parse the --api-path signature files with ClassOrigin.CLASS_PATH followed by the
            // <files> signature files with ClassOrigin.COMMAND_LINE into a single merged
            // TextCodebase.
            val signatureFiles =
                SignatureFile.fromFiles(
                    apiPath,
                    classOrigin = ClassOrigin.CLASS_PATH,
                ) +
                    SignatureFile.fromFiles(
                        sourceFiles,
                        classOrigin = ClassOrigin.COMMAND_LINE,
                    )
            val codebase = signatureFileLoader.load(signatureFiles)

            // Configure and run the stub generator on the parsed signature codebase.
            val generatorConfig =
                StubGenerator.Config(
                    stubWriterConfig = StubWriterConfig(includeDocumentationInStubs = false),
                    stubsDir = stubsDir,
                    generateAnnotations = true,
                    nullabilityConversionPackageFilter =
                        forceConvertToWarningNullabilityAnnotations,
                )

            StubGenerator(
                    generatorConfig,
                    codebase,
                    tracer,
                    executionEnvironment,
                    reporter,
                    apiSurface,
                )
                .generateStubs()
        } finally {
            // Write all saved reports. Do this even if the previous code threw an exception.
            tracer.trace("writeSavedReports") { reporter.writeSavedReports() }
        }

        if (reporter.hasErrors()) {
            // Repeat the errors at the end to make it easy to find the actual problems.
            if (issueReportingOptions.repeatErrorsMax > 0) {
                stderr.println("Error: $PROGRAM_NAME detected the following problems:")
                val totalErrors = reporter.errorCount
                val numShown = reporter.printErrors(stderr, issueReportingOptions.repeatErrorsMax)
                if (numShown < totalErrors) {
                    stderr.println(
                        "${totalErrors - numShown} more error(s) omitted. Search the log for 'error:' to find all of them."
                    )
                }
            }

            // Make sure that the process exits with an error code.
            throw MetalavaCliException(exitCode = -1)
        }
    }
}
