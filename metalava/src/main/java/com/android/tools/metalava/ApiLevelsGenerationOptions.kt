/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.metalava.apilevels.ApiHistoryUpdater
import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.apilevels.GenerateApiHistoryConfig
import com.android.tools.metalava.apilevels.MatchedPatternFile
import com.android.tools.metalava.apilevels.MissingClassAction
import com.android.tools.metalava.apilevels.PatternNode
import com.android.tools.metalava.apilevels.VersionedSignatureApi
import com.android.tools.metalava.apilevels.VersionedSourceApi
import com.android.tools.metalava.cli.common.MetalavaOptionGroup
import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.android.tools.metalava.cli.common.cliError
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.fileForPathInner
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import java.io.File

const val ARG_API_VERSION_FOR_SOURCES = "--api-version-for-sources"

const val ARG_API_VERSION_SIGNATURE_PATTERN = "--api-version-signature-pattern"

// JSON API version related arguments
const val ARG_GENERATE_API_VERSION_HISTORY = "--generate-api-version-history"
const val ARG_API_VERSION_SIGNATURE_FILES = "--api-version-signature-files"

/** Convert an option value to an [ApiVersion]. */
internal fun OptionWithValues<String?, String, String>.apiVersion() = convert {
    ApiVersion.fromString(it)
}

class ApiLevelsGenerationOptions :
    MetalavaOptionGroup(
        name = "Api Levels Generation",
        help =
            """
                Options controlling the API levels file, e.g. `api-versions.xml` file.
            """
                .trimIndent()
    ) {

    internal val apiVersionForSources: ApiVersion? by
        option(
                ARG_API_VERSION_FOR_SOURCES,
                metavar = "<api-version>",
                help =
                    """
                        Sets the API version of unfinalized apis in the current source code. This supports a
                        single integer level, `major.minor`, `major.minor.patch` and
                        `major.minor.patch-quality` formats. Where `major`, `minor` and `patch` are
                        all non-negative integers and `quality` is an alphanumeric string.
                    """
                        .trimIndent(),
            )
            .apiVersion()

    /**
     * The list of patterns used to find matching signature files in the set of files visible to
     * Metalava.
     */
    private val signaturePatterns by
        option(
                ARG_API_VERSION_SIGNATURE_PATTERN,
                metavar = "<historical-api-pattern>",
                help =
                    """
                        Pattern to use to locate signature files. Typically ends with `.txt`.

                        See `metalava help historical-api-patterns` for more information.
                    """
                        .trimIndent(),
            )
            .multiple()

    /** API version history file to generate */
    private val generateApiVersionHistory by
        option(
                ARG_GENERATE_API_VERSION_HISTORY,
                metavar = "<output-file>",
                help =
                    """
                        Reads API signature files and generates a JSON or XML file depending on the
                        extension, which must be one of `json` or `xml` respectively. The JSON file
                        will record the API version in which each class, method, and field. was
                        added in and (if applicable) deprecated in. The XML file will include that
                        information and more but will be optimized to exclude information from
                        class members which is the same as the containing class.
                    """
                        .trimIndent(),
            )
            .newFile()

    /**
     * Ordered list of signatures for each past API version, when generating
     * [generateApiVersionHistory].
     */
    private val apiVersionSignatureFiles by
        option(
                ARG_API_VERSION_SIGNATURE_FILES,
                metavar = "<files>",
                help =
                    """
                        An ordered list of text API signature files. The oldest API version should
                        be first, the newest last. This should not include a signature file for the
                        current API version, which will be parsed from the provided source files.
                        Not required to generate API version JSON if the current version is the only
                        version.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .split(File.pathSeparator)
            .default(emptyList(), defaultForHelp = "")

    /**
     * Construct the [GenerateApiHistoryConfig] from the options.
     *
     * If no relevant command line options were provided then this will return `null`, otherwise it
     * will validate the options and if all is well construct and return a
     * [GenerateApiHistoryConfig] object.
     *
     * @param signatureFileLoader used for loading [Codebase]s from signature files.
     * @param codebaseFragmentProvider provides access to the [CodebaseFragment] for the API defined
     *   in the sources. This will only be called if a [GenerateApiHistoryConfig] needs to be
     *   created.
     */
    fun fromSignatureFilesConfig(
        signatureFileLoader: SignatureFileLoader,
        codebaseFragmentProvider: () -> CodebaseFragment,
    ): GenerateApiHistoryConfig? {
        val apiVersionsFile = generateApiVersionHistory
        return if (apiVersionsFile != null) {
            val (sourceVersion, matchedPatternFiles) =
                sourceVersionAndMatchedPatternFilesFromSignaturePatterns()

            // Create VersionedApis for the signature files and the source codebase.
            val versionedApis = buildList {
                matchedPatternFiles.mapTo(this) {
                    val updater = ApiHistoryUpdater.forApiVersion(it.version)
                    VersionedSignatureApi(signatureFileLoader, listOf(it.file), updater)
                }
                // Add a VersionedSourceApi for the source code.
                add(VersionedSourceApi(codebaseFragmentProvider, sourceVersion))
            }

            GenerateApiHistoryConfig(
                versionedApis = versionedApis,
                outputFile = apiVersionsFile,
                // None are available when generating from signature files.
                sdkExtensionsArguments = null,
                // Keep any references to missing classes.
                missingClassAction = MissingClassAction.KEEP,
                // Do not use internal names.
                useInternalNames = false,
            )
        } else {
            null
        }
    }

    /**
     * Get the source [ApiVersion] and list of [MatchedPatternFile]s from [signaturePatterns] as
     * well as [apiVersionForSources] and [apiVersionSignatureFiles].
     */
    private fun sourceVersionAndMatchedPatternFilesFromSignaturePatterns():
        Pair<ApiVersion, List<MatchedPatternFile>> {

        // TODO b/454050901 : Remove dependency on current version
        val sourceVersion =
            apiVersionForSources
                ?: cliError(
                    "Must specify or $ARG_API_VERSION_FOR_SOURCES with $ARG_API_VERSION_SIGNATURE_PATTERN"
                )

        val historicalSignatureFiles = apiVersionSignatureFiles
        if (historicalSignatureFiles.isEmpty()) return sourceVersion to emptyList()

        val patternNode = PatternNode.parsePatterns(signaturePatterns)
        val matchedFiles =
            patternNode.scan(
                PatternNode.ScanConfig(
                    dir = fileForPathInner("."),
                    fileProvider = PatternNode.LimitedFileSystemProvider(historicalSignatureFiles)
                )
            )

        if (matchedFiles.size != historicalSignatureFiles.size) {
            val matched = matchedFiles.map { it.file }.toSet()
            val unmatched = historicalSignatureFiles.filter { it !in matched }
            cliError(
                "$ARG_API_VERSION_SIGNATURE_FILES: The following files were unmatched by a signature pattern:\n${unmatched.joinToString("\n") {"    $it"}}"
            )
        }

        return sourceVersion to matchedFiles
    }
}
