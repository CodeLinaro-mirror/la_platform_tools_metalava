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

import com.android.tools.metalava.apilevels.ApiGenerator
import com.android.tools.metalava.apilevels.ApiHistoryUpdater
import com.android.tools.metalava.apilevels.ApiJsonPrinter
import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.apilevels.ApiXmlPrinter
import com.android.tools.metalava.apilevels.ExtVersion
import com.android.tools.metalava.apilevels.GenerateApiHistoryConfig
import com.android.tools.metalava.apilevels.MatchedPatternFile
import com.android.tools.metalava.apilevels.MissingClassAction
import com.android.tools.metalava.apilevels.PatternNode
import com.android.tools.metalava.apilevels.SdkExtensionInfo
import com.android.tools.metalava.apilevels.VersionedApi
import com.android.tools.metalava.apilevels.VersionedJarApi
import com.android.tools.metalava.apilevels.VersionedSignatureApi
import com.android.tools.metalava.apilevels.VersionedSourceApi
import com.android.tools.metalava.cli.common.EarlyOptions
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.common.RequiresOtherGroups
import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.android.tools.metalava.cli.common.cliError
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.fileForPathInner
import com.android.tools.metalava.cli.common.map
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import java.io.File

// XML API version related arguments.
const val ARG_GENERATE_API_LEVELS = "--generate-api-levels"

const val ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS =
    "--remove-missing-class-references-in-api-levels"

const val ARG_CURRENT_VERSION = "--current-version"
const val ARG_FIRST_VERSION = "--first-version"
const val ARG_CURRENT_CODENAME = "--current-codename"

const val ARG_ANDROID_JAR_PATTERN = "--android-jar-pattern"

const val ARG_SDK_INFO_FILE = "--sdk-extensions-info"

// JSON API version related arguments
const val ARG_GENERATE_API_VERSION_HISTORY = "--generate-api-version-history"
const val ARG_API_VERSION_SIGNATURE_FILES = "--api-version-signature-files"
const val ARG_API_VERSION_NAMES = "--api-version-names"

class ApiLevelsGenerationOptions(
    private val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment(),
    private val earlyOptions: EarlyOptions = EarlyOptions(),
) :
    OptionGroup(
        name = "Api Levels Generation",
        help =
            """
                Options controlling the API levels file, e.g. `api-versions.xml` file.
            """
                .trimIndent()
    ),
    RequiresOtherGroups {

    /** Make sure that the [earlyOptions] is correctly initialized when testing. */
    override val requiredGroups: List<OptionGroup>
        get() = listOf(earlyOptions)

    /** API level XML file to generate. */
    val generateApiLevelXml: File? by
        option(
                ARG_GENERATE_API_LEVELS,
                metavar = "<xmlfile>",
                help =
                    """
                        Reads android.jar SDK files and generates an XML file recording the API
                        level for each class, method and field. The $ARG_CURRENT_VERSION must also
                        be provided and must be greater than or equal to 27.
                    """
                        .trimIndent(),
            )
            .newFile()

    /** Whether references to missing classes should be removed from the api levels file. */
    private val removeMissingClassReferencesInApiLevels: Boolean by
        option(
                ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS,
                help =
                    """
                        Removes references to missing classes when generating the API levels XML
                        file. This can happen when generating the XML file for the non-updatable
                        portions of the module-lib sdk, as those non-updatable portions can
                        reference classes that are part of an updatable apex.
                    """
                        .trimIndent(),
            )
            .flag()

    /** Convert an option value to an [ApiVersion]. */
    fun OptionWithValues<String?, String, String>.apiVersion() = convert {
        ApiVersion.fromString(it)
    }

    /**
     * The first api version of the codebase; typically 1 but can be higher for example for the
     * System API.
     */
    private val firstApiVersion: ApiVersion by
        option(
                ARG_FIRST_VERSION,
                metavar = "<api-version>",
                help =
                    """
                        Sets the first API version to include in the API history file. See
                        $ARG_CURRENT_VERSION for acceptable `<api-version>`s.
                    """
                        .trimIndent()
            )
            .apiVersion()
            .default(ApiVersion.fromLevel(1))

    /**
     * The last api level.
     *
     * This is one more than [currentApiVersion] if this is a developer preview build.
     */
    private val lastApiVersion
        get() = currentApiVersion + if (isDeveloperPreviewBuild) 1 else 0

    /** The [ApiVersion] of the codebase, or null if not known/specified */
    private val optionalCurrentApiVersion: ApiVersion? by
        option(
                ARG_CURRENT_VERSION,
                metavar = "<api-version>",
                help =
                    """
                        Sets the current API version of the current source code. This supports a
                        single integer level, `major.minor`, `major.minor.patch` and
                        `major.minor.patch-quality` formats. Where `major`, `minor` and `patch` are
                        all non-negative integers and `quality` is an alphanumeric string.
                    """
                        .trimIndent(),
            )
            .apiVersion()

    /**
     * Get the current API version.
     *
     * This must only be called if needed as it will fail if [ARG_CURRENT_VERSION] has not been
     * specified.
     */
    internal val currentApiVersion: ApiVersion
        get() =
            optionalCurrentApiVersion
                ?: cliError("$ARG_GENERATE_API_LEVELS requires $ARG_CURRENT_VERSION")

    /**
     * The codename of the codebase: non-null string if this is a developer preview build, null if
     * this is a release build.
     */
    private val currentCodeName: String? by
        option(
                ARG_CURRENT_CODENAME,
                metavar = "<version-codename>",
                help =
                    """
                        Sets the code name for the current source code.
                    """
                        .trimIndent(),
            )
            .map { if (it == "REL") null else it }

    /**
     * True if [currentCodeName] is specified, false otherwise.
     *
     * If this is `true` then the API defined in the sources will be added to the API levels file
     * with an API level of [currentApiVersion]` - 1`.
     */
    private val isDeveloperPreviewBuild
        get() = currentCodeName != null

    /** The list of patterns used to find matching jars in the set of files visible to Metalava. */
    private val androidJarPatterns: List<String> by
        option(
                ARG_ANDROID_JAR_PATTERN,
                metavar = "<android-jar-pattern>",
                help =
                    """
                        Pattern to use to locate Android JAR files. Each pattern must contain a
                        {version:level} placeholder that will be replaced with each API level that
                        is being included and if the result is an existing jar file then it will be
                        taken as the definition of the API at that level.
                    """
                        .trimIndent(),
            )
            .multiple(default = emptyList())

    /**
     * Rules to filter out some extension SDK APIs from the API, and assign extensions to the APIs
     * that are kept
     */
    private val sdkInfoFile: File? by
        option(
                ARG_SDK_INFO_FILE,
                metavar = "<sdk-info-file>",
                help =
                    """
                        Points to map of extension SDK APIs to include, if any. The file is a plain
                        text file and describes, per extension SDK, what APIs from that extension
                        to include in the file created via $ARG_GENERATE_API_LEVELS. The format of
                        each line is one of the following:
                        \"<module-name> <pattern> <ext-name> [<ext-name> [...]]\", where
                        <module-name> is the name of the mainline module this line refers to,
                        <pattern> is a common Java name prefix of the APIs this line refers to, and
                        <ext-name> is a list of extension SDK names in which these SDKs first
                        appeared, or \"<ext-name> <ext-id> <type>\", where <ext-name> is the name of
                        an SDK, <ext-id> its numerical ID and <type> is one of \"platform\" (the
                        Android platform SDK), \"platform-ext\" (an extension to the Android
                        platform SDK), \"standalone\" (a separate SDK). Fields are separated by
                        whitespace. A mainline module may be listed multiple times.
                        The special pattern \"*\" refers to all APIs in the given mainline module.
                        Lines beginning with # are comments.

                        If specified then the $ARG_ANDROID_JAR_PATTERN must include at least one
                        pattern that uses `{version:extension}` and `{module}` placeholders and that
                        pattern must match at least one file.
                    """
                        .trimIndent(),
            )
            .existingFile()

    /**
     * Get label for [version].
     *
     * If a codename has been specified and [version] is greater than the current API version (which
     * defaults to `null` when not set) then use the codename as the label, otherwise use the
     * version itself.
     */
    fun getApiVersionLabel(version: ApiVersion): String {
        val codename = currentCodeName
        val current = optionalCurrentApiVersion
        return if (current == null || codename == null || version <= current) version.toString()
        else codename
    }

    /**
     * Check whether [version] should be included in documentation.
     *
     * If [isDeveloperPreviewBuild] is `true` then allow any [ApiVersion] as the documentation is
     * not going to be published outside Android, so it is safe to include all [ApiVersion]s,
     * including the next one.
     *
     * If no [currentApiVersion] has been provided then allow any [ApiVersion] level as there is no
     * way to determine whether the [ApiVersion] is a future API or not.
     *
     * Otherwise, it is a release build so ignore any [ApiVersion]s after the current one.
     */
    fun includeApiVersionInDocumentation(version: ApiVersion): Boolean {
        if (isDeveloperPreviewBuild) return true
        val current = optionalCurrentApiVersion ?: return true
        return version <= current
    }

    /**
     * Find all jars that matches the patterns in [patterns] and are in the range from
     * [firstApiVersion] to [lastApiVersion].
     *
     * @param dir the directory to scan.
     * @param patterns the patterns that determine the files that will be found.
     */
    private fun scanForJarFiles(dir: File, patterns: List<String>): List<MatchedPatternFile> {
        // Find all the android.jar files for versions within the required range.
        val patternNode = PatternNode.parsePatterns(patterns)
        val versionRange = firstApiVersion.rangeTo(lastApiVersion)
        val scanConfig = PatternNode.ScanConfig(dir = dir, apiVersionRange = versionRange)
        return patternNode.scan(scanConfig)
    }

    /**
     * Create [VersionedJarApi]s for each android stub jars in [matchedFiles].
     *
     * Returns a list of [VersionedApi]s from lowest [VersionedApi.apiVersion] to highest.
     *
     * @param matchedFiles a list of files that matched the historical API patterns.
     */
    private fun constructVersionedApisForAndroidJars(
        matchedFiles: List<MatchedPatternFile>
    ): List<VersionedApi> {
        // TODO(b/383288863): Check to make sure that there is one jar file for every major version
        //  in the range.

        // Convert the MatchedPatternFiles into VersionedJarApis.
        return matchedFiles.map { (jar, apiVersion) ->
            verbosePrint { "Found API $apiVersion at $jar" }
            val updater = ApiHistoryUpdater.forApiVersion(apiVersion)
            VersionedJarApi(jar, updater)
        }
    }

    /** Print string returned by [message] if verbose output has been requested. */
    private inline fun verbosePrint(message: () -> String) {
        if (earlyOptions.verbosity.verbose) {
            executionEnvironment.stdout.println(message())
        }
    }

    /**
     * Get the [GenerateApiHistoryConfig] for Android.
     *
     * This has some Android specific code, e.g. structure of SDK extensions.
     */
    fun forAndroidConfig(
        codebaseFragmentProvider: () -> CodebaseFragment,
    ) =
        generateApiLevelXml?.let { outputFile ->
            // Scan for all the files that could contribute to the API history.
            val matchedFiles = scanForJarFiles(fileForPathInner("."), androidJarPatterns)

            // Split the files into Android jar files and extension jar files.
            val (androidJarFiles, extensionJarFiles) = matchedFiles.partition { it.module == null }

            // Get a VersionedApi for each of the Android jar files.
            val versionedHistoricalApis = constructVersionedApisForAndroidJars(androidJarFiles)

            val currentSdkVersion = currentApiVersion
            if (currentSdkVersion.major <= 26) {
                cliError("Suspicious $ARG_CURRENT_VERSION $currentSdkVersion, expected at least 27")
            }

            val nextSdkVersion = currentSdkVersion + 1
            val lastFinalizedVersion = versionedHistoricalApis.lastOrNull()?.apiVersion

            // Compute the version to use for the current codebase, or null if the current codebase
            // should not be added to the API history. If a non-null version is selected it will
            // always be after the last historical version.
            val codebaseSdkVersion =
                when {
                    // The current codebase is a developer preview so use the next, in the
                    // process of being finalized version.
                    isDeveloperPreviewBuild -> nextSdkVersion

                    // If no finalized versions were provided or the last finalized version is less
                    // than the current version then use the current version as the version of the
                    // codebase.
                    lastFinalizedVersion == null || lastFinalizedVersion < currentSdkVersion ->
                        currentSdkVersion

                    // Else do not include the current codebase.
                    else -> null
                }

            // Get the optional SDK extension arguments.
            val sdkExtensionsArguments =
                if (sdkInfoFile != null) {
                    // The not finalized SDK version is the version after the last historical
                    // version. That is either the version used for the current codebase or the
                    // next version.
                    val notFinalizedSdkVersion = codebaseSdkVersion ?: nextSdkVersion
                    ApiGenerator.SdkExtensionsArguments(
                        sdkInfoFile!!,
                        notFinalizedSdkVersion,
                    )
                } else {
                    null
                }

            // Create a list of VersionedApis that need to be incorporated into the Api history.
            val versionedApis = buildList {
                addAll(versionedHistoricalApis)

                // Add a VersionedSourceApi for the current codebase if required.
                if (codebaseSdkVersion != null) {
                    add(
                        VersionedSourceApi(
                            codebaseFragmentProvider,
                            codebaseSdkVersion,
                            useInternalNames = true,
                        )
                    )
                }

                // Add any VersionedApis for SDK extensions. These must be added after all
                // VersionedApis for SDK versions as their behavior depends on whether an API was
                // defined in an SDK version.
                if (sdkExtensionsArguments != null) {
                    require(extensionJarFiles.isNotEmpty()) {
                        "no extension sdk jar files found in ${androidJarPatterns.joinToString()}"
                    }
                    addVersionedExtensionApis(
                        this,
                        sdkExtensionsArguments.notFinalizedSdkVersion,
                        extensionJarFiles,
                        sdkExtensionsArguments.sdkExtensionInfo,
                    )
                }
            }

            // Get a list of all versions, including the codebase version, if necessary. This is in
            // version order and is used to compute the version in which an API element has been
            // removed based on the last version it was present in. See [ApiXmlPrinter].
            val allVersions = buildList {
                versionedHistoricalApis.mapTo(this) { it.apiVersion }

                // Add the highest version. That is either the version used for the current
                // codebase, if present, or the next version. That ensures that the [ApiXmlPrinter]
                // can always compute the version in which an API element was removed.
                add(codebaseSdkVersion ?: nextSdkVersion)
            }

            val availableSdkExtensions =
                sdkExtensionsArguments?.sdkExtensionInfo?.availableSdkExtensions
            val printer = ApiXmlPrinter(availableSdkExtensions, allVersions)

            GenerateApiHistoryConfig(
                versionedApis = versionedApis,
                outputFile = outputFile,
                printer = printer,
                sdkExtensionsArguments = sdkExtensionsArguments,
                missingClassAction =
                    if (removeMissingClassReferencesInApiLevels) MissingClassAction.REMOVE
                    else MissingClassAction.REPORT,
            )
        }

    /**
     * Add [VersionedApi] instances to [list] for each of the [extensionJarFiles].
     *
     * Some APIs only exist in extension SDKs and not in the Android SDK, but for backwards
     * compatibility with tools that expect the Android SDK to be the only SDK, metalava needs to
     * assign such APIs some Android SDK API version. This uses [versionNotInAndroidSdk].
     *
     * @param versionNotInAndroidSdk fallback API level for APIs not in the Android SDK
     * @param extensionJarFiles extension jar files.
     * @param sdkExtensionInfo the [SdkExtensionInfo] read from sdk-extension-info.xml file.
     */
    private fun addVersionedExtensionApis(
        list: MutableList<VersionedApi>,
        versionNotInAndroidSdk: ApiVersion,
        extensionJarFiles: List<MatchedPatternFile>,
        sdkExtensionInfo: SdkExtensionInfo,
    ) {
        val extensionJarsByModule = extensionJarFiles.groupBy({ it.module!! })
        // Iterate over the mainline modules and their different versions.
        for ((mainlineModule, value) in extensionJarsByModule) {
            // Get the extensions information for the mainline module. If no information exists for
            // a particular module then the module is ignored.
            val moduleMap = sdkExtensionInfo.extensionsMapForJarOrEmpty(mainlineModule)
            if (moduleMap.isEmpty())
                continue // TODO(b/259115852): remove this (though it is an optimization too).
            for ((file, version) in value) {
                val extVersion = ExtVersion.fromLevel(version.major)
                val updater =
                    ApiHistoryUpdater.forExtVersion(
                        versionNotInAndroidSdk,
                        extVersion,
                        mainlineModule,
                    )
                list.add(VersionedJarApi(file, updater))
            }
        }
    }

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

    /**
     * The names of the API versions in [apiVersionSignatureFiles], in the same order, and the name
     * of the current API version (if it is not provided by [optionalCurrentApiVersion]).
     */
    private val apiVersionNames by
        option(
                ARG_API_VERSION_NAMES,
                metavar = "<api-versions>",
                help =
                    """
                        An ordered list of strings with the names to use for the API versions from
                        $ARG_API_VERSION_SIGNATURE_FILES. If $ARG_CURRENT_VERSION is not provided
                        then this must include an additional version at the end which is used for
                        the current API version. Required for $ARG_GENERATE_API_VERSION_HISTORY.
                    """
                        .trimIndent()
            )
            .split(" ")

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
            // The signature files can be null if the current version is the only version
            val pastApiVersions = apiVersionSignatureFiles ?: emptyList()

            val currentApiVersion = optionalCurrentApiVersion
            val allVersions = buildList {
                apiVersionNames?.mapTo(this) { ApiVersion.fromString(it) }
                if (currentApiVersion != null) add(currentApiVersion)
            }

            // Get the number of version names and signature files, defaulting to 0 if not provided.
            val numVersionNames = allVersions.size
            if (numVersionNames == 0) {
                cliError(
                    "Must specify $ARG_API_VERSION_NAMES and/or $ARG_CURRENT_VERSION with $ARG_GENERATE_API_VERSION_HISTORY"
                )
            }
            val numVersionFiles = apiVersionSignatureFiles?.size ?: 0
            // allVersions will include the current version but apiVersionSignatureFiles will not,
            // so there should be 1 more name than signature files.
            if (numVersionNames != numVersionFiles + 1) {
                if (currentApiVersion == null) {
                    cliError(
                        "$ARG_API_VERSION_NAMES must have one more version than $ARG_API_VERSION_SIGNATURE_FILES to include the current version name as $ARG_CURRENT_VERSION is not provided"
                    )
                } else {
                    cliError(
                        "$ARG_API_VERSION_NAMES must have the same number of versions as $ARG_API_VERSION_SIGNATURE_FILES has files as $ARG_CURRENT_VERSION is provided"
                    )
                }
            }

            val sourceVersion = allVersions.last()

            // Combine the `pastApiVersions` and `apiVersionNames` into a list of
            // `VersionedSignatureApi`s.
            val versionedApis = buildList {
                pastApiVersions.mapIndexedTo(this) { index, file ->
                    VersionedSignatureApi(signatureFileLoader, file, allVersions[index])
                }
                // Add a VersionedSourceApi for the source code.
                add(
                    VersionedSourceApi(
                        codebaseFragmentProvider,
                        sourceVersion,
                        useInternalNames = false
                    )
                )
            }

            val printer =
                when (val extension = apiVersionsFile.extension) {
                    "xml" -> ApiXmlPrinter(null, allVersions)
                    "json" -> ApiJsonPrinter()
                    else ->
                        error(
                            "unexpected extension for $apiVersionsFile, expected 'xml', or 'json' got '$extension'"
                        )
                }

            GenerateApiHistoryConfig(
                versionedApis = versionedApis,
                outputFile = apiVersionsFile,
                printer = printer,
                // None are available when generating from signature files.
                sdkExtensionsArguments = null,
                // Keep any references to missing classes.
                missingClassAction = MissingClassAction.KEEP,
            )
        } else {
            null
        }
    }
}
