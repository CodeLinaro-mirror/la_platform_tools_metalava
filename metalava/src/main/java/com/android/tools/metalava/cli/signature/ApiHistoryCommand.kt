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
import com.android.tools.metalava.ARG_API_VERSION_LABEL
import com.android.tools.metalava.ARG_CONFIG_FILE
import com.android.tools.metalava.ConfigFileOptions
import com.android.tools.metalava.apiVersion
import com.android.tools.metalava.apilevels.ApiGenerator
import com.android.tools.metalava.apilevels.ApiHistoryUpdater
import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.apilevels.GenerateApiHistoryConfig
import com.android.tools.metalava.apilevels.MatchedPatternFile
import com.android.tools.metalava.apilevels.MissingClassAction
import com.android.tools.metalava.apilevels.PatternNode
import com.android.tools.metalava.apilevels.VersionedSignatureApi
import com.android.tools.metalava.apilevels.createGenerateApiHistoryConfig
import com.android.tools.metalava.cli.common.DefaultSignatureFileLoader
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.cliError
import com.android.tools.metalava.cli.common.enumOption
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.fileForPathInner
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.cli.common.splitMultiple
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.cli.common.tracer
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.reporter.BasicReporter
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import java.io.File

private const val ARG_OUTPUT_FILE = "--output-file"
private const val ARG_PATTERN = "--pattern"
private const val ARG_HISTORICAL_FILES = "--historical-files"
const val ARG_HISTORICAL_VERSIONS = "--historical-versions"
private const val ARG_CURRENT_VERSION = "--current-version"
private const val ARG_CURRENT_FILES = "--current-files"
const val ARG_SDK_INFO_FILE = "--sdk-extensions-info"
private const val ARG_SDK_EXTENSION_VERSIONS = "--sdk-extension-versions"
const val ARG_API_VERSION_FOR_SDK_EXTENSION = "--api-version-for-sdk-extension"
private const val ARG_MISSING_CLASS_ACTION = "--missing-class-action"

/** Convert an option value to a [ClosedRange] of [ApiVersion]. */
private fun OptionWithValues<String?, String, String>.apiVersionRange() = convert { text ->
    val parts = text.split(':')
    if (parts.size != 2) {
        error("Must be of the form <version>:<version> but found '$text'")
    }
    val (from, to) = parts
    ApiVersion.fromString(from).rangeTo(ApiVersion.fromString(to))
}

/**
 * Generates an API versions XML or JSON file from historical and/or current API signature files.
 */
class ApiHistoryCommand :
    MetalavaSubCommand(
        help = "Generate an API versions XML or JSON file from API signature files.",
    ) {

    /** Add options for loading configuration files (e.g. `<api-surfaces>`). */
    private val configFileOptions by ConfigFileOptions()

    /** Output XML or JSON API versions file to generate. */
    private val outputFile by
        option(
                ARG_OUTPUT_FILE,
                metavar = "<file>",
                help =
                    """
                        Output XML or JSON API versions file to generate. The format is determined
                        from the file extension, which must be `.xml` or `.json` respectively.
                    """
                        .trimIndent(),
            )
            .newFile()
            .required()

    /** Patterns used to match and extract metadata from [historicalFiles]. */
    private val patterns by
        option(
                ARG_PATTERN,
                metavar = "<historical-api-pattern>",
                help =
                    """
                        Pattern to use to locate historical signature files. Typically ends with
                        `.txt`.

                        See `metalava help historical-api-patterns` for more information.
                    """
                        .trimIndent(),
            )
            .multiple()

    /** Explicit list of historical signature files to scan using [patterns]. */
    private val historicalFiles by
        option(
                ARG_HISTORICAL_FILES,
                metavar = "<files>",
                help =
                    """
                        Historical signature files (separated by `${File.pathSeparator}`) to scan
                        using $ARG_PATTERN.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .splitMultiple(File.pathSeparator)

    /** Optional range of historical API versions to include. */
    private val historicalVersions by
        option(
                ARG_HISTORICAL_VERSIONS,
                metavar = "<api-version>:<api-version>",
                help =
                    """
                        The optional range of historical versions that can be included in the API
                        version history. The `from` and `to` parts of the range are separated by a
                        `:` and are both inclusive. See $ARG_CURRENT_VERSION for acceptable
                        `<api-version>`s.

                        If unspecified, version history will default to include every historical
                        version.
                    """
                        .trimIndent(),
            )
            .apiVersionRange()

    /** Optional API version for [currentFiles]. */
    private val currentVersion by
        option(
                ARG_CURRENT_VERSION,
                metavar = "<api-version>",
                help =
                    """
                        Sets the API version for the current API signature files specified by
                        $ARG_CURRENT_FILES. This supports a single integer level, `major.minor`,
                        `major.minor.patch` and `major.minor.patch-quality` formats. Where `major`,
                        `minor` and `patch` are all non-negative integers and `quality` is an
                        alphanumeric string.
                    """
                        .trimIndent(),
            )
            .apiVersion()

    /** Optional current API signature files for [currentVersion]. */
    private val currentFiles by
        option(
                ARG_CURRENT_FILES,
                metavar = "<files>",
                help =
                    """
                        Current API signature files (separated by `${File.pathSeparator}`) for the
                        API version specified by $ARG_CURRENT_VERSION.
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
     * Rules to filter out some extension SDK APIs from the API, and assign extensions to the APIs
     * that are kept.
     */
    private val sdkInfoFile by
        option(
                ARG_SDK_INFO_FILE,
                metavar = "<sdk-info-file>",
                help =
                    """
                        Points to map of extension SDK APIs to include, if any. The file is an XML
                        file and describes, per extension SDK, what APIs from that extension to
                        include in the file created via $ARG_OUTPUT_FILE.

                        If specified then $ARG_PATTERN must include at least one pattern that uses
                        `{version:extension}` and `{module}` placeholders and that pattern must
                        match at least one file in $ARG_HISTORICAL_FILES.
                    """
                        .trimIndent(),
            )
            .existingFile()

    /**
     * The range of historical SDK Extension API versions that can be included in the API version
     * history.
     */
    private val sdkExtensionVersions by
        option(
                ARG_SDK_EXTENSION_VERSIONS,
                metavar = "<api-version>:<api-version>",
                help =
                    """
                        The optional range of historical SDK extension versions that can be included
                        in the API version history. The `from` and `to` parts of the range are
                        separated by a `:` and are both inclusive. See $ARG_CURRENT_VERSION for
                        acceptable `<api-version>`s.

                        If unspecified then allow every historical version.
                    """
                        .trimIndent(),
            )
            .apiVersionRange()

    /**
     * Fallback Android SDK version for APIs that only exist in an SDK extension and not in any
     * finalized Android SDK version.
     */
    private val apiVersionForSdkExtension by
        option(
                ARG_API_VERSION_FOR_SDK_EXTENSION,
                metavar = "<api-version>",
                help =
                    """
                        SDK extension APIs can be added between SDK versions and they do not become
                        available in an SDK version until the next SDK version is released. However,
                        when generating an API history it is required that every API is in an API
                        version, even those added as part of an SDK extension.

                        If an SDK extension is being prepared for inclusion in an SDK version then
                        this should be the SDK version. If an SDK extension is being prepared
                        between SDK versions then this should be a magic version number that
                        indicates that the newly added SDK extension APIs are not yet present in
                        any SDK version.

                        In the latter case the $ARG_API_VERSION_LABEL should be used when generating
                        documentation from that `api-versions.xml` file to give the magic version a
                        meaningful name in the documentation.
                    """
                        .trimIndent(),
            )
            .apiVersion()

    /** Action to take when an XML API versions file references a missing superclass/interface. */
    private val missingClassAction by
        enumOption(
            ARG_MISSING_CLASS_ACTION,
            help =
                """
                    Determines what to do when generating an XML API versions file and a class
                    extends or implements a class that is not defined within any of the API versions
                    loaded. Ignored when generating a JSON API versions file.
                """
                    .trimIndent(),
            enumValueHelpGetter = { it.help },
            default = MissingClassAction.REPORT,
        )

    /** The [GenerateApiHistoryConfig] created by [run], exposed for testing. */
    internal lateinit var config: GenerateApiHistoryConfig

    override fun run() {
        val useInternalNames =
            when (val extension = outputFile.extension) {
                "xml" -> true
                "json" -> false
                else ->
                    cliError(
                        "$ARG_OUTPUT_FILE ($outputFile) has unexpected extension '$extension', must be 'xml' or 'json'"
                    )
            }

        if (historicalFiles.isEmpty() && currentFiles.isEmpty()) {
            cliError("Must specify at least one of $ARG_HISTORICAL_FILES or $ARG_CURRENT_FILES")
        }
        if (historicalFiles.isNotEmpty() && patterns.isEmpty()) {
            cliError("Must specify $ARG_PATTERN with $ARG_HISTORICAL_FILES")
        }
        if (currentVersion != null && currentFiles.isEmpty()) {
            cliError("Must specify $ARG_CURRENT_FILES with $ARG_CURRENT_VERSION")
        }
        if (currentFiles.isNotEmpty() && currentVersion == null) {
            cliError("Must specify $ARG_CURRENT_VERSION with $ARG_CURRENT_FILES")
        }

        val apiSurfaces = apiSurfaceName?.let { configFileOptions.apiSurfacesForTargetSurface(it) }

        val codebaseConfig =
            Codebase.Config(
                reporter = BasicReporter(stderr),
            )
        val signatureFileLoader =
            DefaultSignatureFileLoader(
                codebaseConfig = codebaseConfig,
                tracer = tracer,
            )

        fun createVersionedSignatureApi(
            updater: ApiHistoryUpdater,
            files: List<MatchedPatternFile>,
        ) = VersionedSignatureApi(signatureFileLoader, files.map { it.file }, updater)

        config =
            createGenerateApiHistoryConfig(
                outputFile = outputFile,
                currentDir = fileForPathInner("."),
                fileProvider = PatternNode.LimitedFileSystemProvider(historicalFiles),
                apiFilePatterns = patterns,
                apiVersionRange = historicalVersions,
                sdkExtensionVersionRange = sdkExtensionVersions,
                sdkInfoFile = sdkInfoFile,
                apiVersionForSdkExtension = apiVersionForSdkExtension,
                apiSurfaces = apiSurfaces,
                currentVersion = currentVersion,
                currentVersionedApiFactory =
                    if (currentFiles.isEmpty()) {
                        null
                    } else
                        { version ->
                            VersionedSignatureApi(
                                signatureFileLoader,
                                currentFiles,
                                ApiHistoryUpdater.forApiVersion(version),
                            )
                        },
                versionedApiFactory = ::createVersionedSignatureApi,
                missingClassAction =
                    if (useInternalNames) missingClassAction else MissingClassAction.KEEP,
                useInternalNames = useInternalNames,
            )

        ApiGenerator(tracer).generateApiHistory(config)
    }
}
