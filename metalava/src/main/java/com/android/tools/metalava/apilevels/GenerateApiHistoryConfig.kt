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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.apilevels.ApiGenerator.SdkExtensionsArguments
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import java.io.File

/**
 * Properties for the [ApiGenerator.generateApiHistory] method that come from command line options.
 */
data class GenerateApiHistoryConfig(
    /**
     * A list of [VersionedApi]s that contribute to the API history.
     *
     * i.e.
     * 1. Historical, i.e. previously released, APIs, in order from the oldest API version to the
     *    newest.
     * 2. An optional [VersionedApi] for the current sources [Codebase], whose version is higher
     *    than the last historical version.
     * 3. The optional [VersionedApi]s for SDK extensions, grouped by module and in order from the
     *    oldest. API version to the newest.
     */
    val versionedApis: List<VersionedApi>,

    /** The API levels file that will be generated. */
    val outputFile: File,

    /**
     * Optional SDK extensions arguments.
     *
     * If provided then SDK extension information will be included in the API levels file.
     */
    val sdkExtensionsArguments: SdkExtensionsArguments?,

    /**
     * Determines what to do with If `true` then any references to undefined classes will be removed
     * from super class and interface lists; otherwise any such references will be treated as an
     * error.
     *
     * An undefined class is one that is not defined within any of the API versions loaded.
     */
    val missingClassAction: MissingClassAction,

    /**
     * Determines whether the [Api] should use internal names for the elements, or more source like
     * names.
     */
    val useInternalNames: Boolean,
)

/**
 * Possible actions to take when an [Api] object contains references in extends and implements types
 * to classes which are not defined within the [Api] (i.e. have no corresponding [ApiClass] because
 * they were not defined within any of the API versions loaded).
 */
enum class MissingClassAction(val help: String) {
    /** Keep the references in the [Api]. */
    KEEP("Keep references to missing classes.") {
        override fun apply(api: Api) {
            // Do nothing.
        }
    },

    /** Remove the references from the [Api]. */
    REMOVE("Remove references to missing classes.") {
        override fun apply(api: Api) {
            api.removeMissingClasses()
        }
    },

    /** Report the references as errors. */
    REPORT("Report references to missing classes as errors.") {
        override fun apply(api: Api) {
            api.verifyNoMissingClasses()
        }
    };

    /** Apply this action to [api]. */
    abstract fun apply(api: Api)
}

/**
 * Factory for creating a [VersionedApi] from an [ApiHistoryUpdater] and a list of
 * [MatchedPatternFile].
 */
internal typealias VersionedApiFactory =
    (ApiHistoryUpdater, List<MatchedPatternFile>) -> VersionedApi

/**
 * Construct a [GenerateApiHistoryConfig] by scanning for historical API files matching
 * [apiFilePatterns], combining them with an optional current API and optional SDK extension APIs.
 */
internal fun createGenerateApiHistoryConfig(
    outputFile: File,
    currentDir: File,
    fileProvider: PatternNode.FileProvider = PatternNode.WholeFileSystemProvider(),
    apiFilePatterns: List<String>,
    apiVersionRange: ClosedRange<ApiVersion>?,
    sdkExtensionVersionRange: ClosedRange<ApiVersion>?,
    sdkInfoFile: File?,
    apiVersionForSdkExtension: ApiVersion?,
    apiSurfaces: ApiSurfaces?,
    currentVersion: ApiVersion?,
    currentVersionedApiFactory: ((ApiVersion) -> VersionedApi)?,
    versionedApiFactory: VersionedApiFactory,
    missingClassAction: MissingClassAction,
    useInternalNames: Boolean,
): GenerateApiHistoryConfig {
    val matchedApiFiles =
        findHistoricalApiFiles(
            dir = currentDir,
            fileProvider = fileProvider,
            patterns = apiFilePatterns,
            apiVersionRange = apiVersionRange,
            sdkExtensionVersionRange = sdkExtensionVersionRange,
            apiSurfaces = apiSurfaces,
        )

    // Split the files into primary api files and extension api files.
    val (primaryApiFiles, extensionApiFiles) = matchedApiFiles.partition { !it.isExtension }

    // Get a VersionedApi for each of the released API files.
    val versionedHistoricalApis =
        constructVersionedApisForHistoricalFiles(primaryApiFiles, versionedApiFactory)

    // Get the optional SDK extension arguments.
    val sdkExtensionsArguments =
        if (sdkInfoFile != null) {
            // The not finalized SDK version is either:
            // 1. The version specified using --api-version-for-sdk-extension.
            // 2. Or the version after the last historical version.
            //
            // If the latter then that is either:
            // 1. The version used for the current codebase.
            // 2. Or the next version, i.e. b/454053322 placeholder value
            val notFinalizedSdkVersion =
                apiVersionForSdkExtension ?: currentVersion ?: ApiVersion.fromString("10000")
            SdkExtensionsArguments(
                sdkInfoFile,
                notFinalizedSdkVersion,
            )
        } else {
            null
        }

    // Create a list of VersionedApis that need to be incorporated into the Api history.
    val versionedApis = buildList {
        addAll(versionedHistoricalApis)

        // Add a VersionedApi for the current API if required.
        if (currentVersion != null && currentVersionedApiFactory != null) {
            add(currentVersionedApiFactory(currentVersion))
        }

        // Add any VersionedApis for SDK extensions. These must be added after all
        // VersionedApis for SDK versions as their behavior depends on whether an API was
        // defined in an SDK version.
        if (sdkExtensionsArguments != null) {
            require(extensionApiFiles.isNotEmpty()) {
                "no extension api files found by ${apiFilePatterns.joinToString()}"
            }

            // Get the potentially future API version that new SDK extension APIs will
            // be assumed to have been added.
            val apiVersionForNewSdkExtensionApis = sdkExtensionsArguments.notFinalizedSdkVersion

            // Any APIs added from the latest sources may not be present in the SDK
            // extension APIs so care needs to be taken to ensure that they are not treated
            // as being removed.
            if (currentVersion != null && currentVersionedApiFactory != null) {
                // If the latest version of the API is not the same as the version that will
                // be used for new SDK extension APIs then assume that all APIs from the
                // latest version are present in the API version in which new SDK extension
                // APIs will be added. This is necessary because otherwise an API from the
                // latest version which is in an SDK extension class but not yet present in
                // an SDK extension version would not be present in that version and so
                // would be assumed to have been removed.
                if (currentVersion != apiVersionForNewSdkExtensionApis) {
                    add(currentVersionedApiFactory(apiVersionForNewSdkExtensionApis))
                }
            }

            addVersionedExtensionApis(
                this,
                apiVersionForNewSdkExtensionApis,
                extensionApiFiles,
                sdkExtensionsArguments.sdkExtensionInfo,
                versionedApiFactory,
            )
        }
    }

    return GenerateApiHistoryConfig(
        versionedApis = versionedApis,
        outputFile = outputFile,
        sdkExtensionsArguments = sdkExtensionsArguments,
        missingClassAction = missingClassAction,
        useInternalNames = useInternalNames,
    )
}

/**
 * Find all historical files that match the patterns in [patterns] and are within [apiVersionRange]
 * and [sdkExtensionVersionRange].
 *
 * @param dir the directory to scan.
 * @param fileProvider the [PatternNode.FileProvider] used to scan [dir].
 * @param patterns the patterns that determine the files that will be found.
 */
private fun findHistoricalApiFiles(
    dir: File,
    fileProvider: PatternNode.FileProvider,
    patterns: List<String>,
    apiVersionRange: ClosedRange<ApiVersion>?,
    sdkExtensionVersionRange: ClosedRange<ApiVersion>?,
    apiSurfaces: ApiSurfaces?,
): List<MatchedPatternFile> {
    // Find all the historical files for versions within the required range.
    val patternNode = PatternNode.parsePatterns(patterns)
    val effectiveSdkExtensionVersionRange =
        sdkExtensionVersionRange
            ?: ApiVersion.fromLevel(1).rangeTo(ApiVersion.fromLevel(Int.MAX_VALUE))
    val apiSurfaceByName = apiSurfaces?.byName
    val scanConfig =
        PatternNode.ScanConfig(
            dir = dir,
            fileProvider = fileProvider,
            apiVersionFilter = apiVersionRange?.let { it::contains },
            sdkExtensionVersionFilter = effectiveSdkExtensionVersionRange::contains,
            apiSurfaceByName = apiSurfaceByName,
        )
    return patternNode.scan(scanConfig)
}

/**
 * Create [VersionedApi]s for each historical file in [matchedFiles].
 *
 * @param matchedFiles a list of files that matched the historical API patterns.
 */
private fun constructVersionedApisForHistoricalFiles(
    matchedFiles: List<MatchedPatternFile>,
    versionedApiFactory: VersionedApiFactory,
): List<VersionedApi> {
    // TODO(b/383288863): Check to make sure that there is one VersionedApi for every major
    //  version in the range.

    val byVersion = matchedFiles.groupBy { it.version }

    // Convert the MatchedPatternFiles into VersionedApis.
    return byVersion.map { (apiVersion, files) ->
        val updater = ApiHistoryUpdater.forApiVersion(apiVersion)
        versionedApiFactory(updater, files)
    }
}

/**
 * Add [VersionedApi] instances to [list] for each of the [extensionApiFiles].
 *
 * Some APIs only exist in extension SDKs and not in the Android SDK, but for backwards
 * compatibility with tools that expect the Android SDK to be the only SDK, metalava needs to assign
 * such APIs some Android SDK API version. This uses [versionNotInAndroidSdk].
 *
 * @param versionNotInAndroidSdk fallback API level for APIs not in the Android SDK
 * @param extensionApiFiles extension api files.
 * @param sdkExtensionInfo the [SdkExtensionInfo] read from sdk-extension-info.xml file.
 * @param versionedApiFactory factory for creating [VersionedApi]s.
 */
private fun addVersionedExtensionApis(
    list: MutableList<VersionedApi>,
    versionNotInAndroidSdk: ApiVersion,
    extensionApiFiles: List<MatchedPatternFile>,
    sdkExtensionInfo: SdkExtensionInfo,
    versionedApiFactory: VersionedApiFactory,
) {
    val byModule = extensionApiFiles.groupBy({ it.module!! })
    // Iterate over the mainline modules and their different versions.
    for ((mainlineModule, moduleFiles) in byModule) {
        // Get the extensions information for the mainline module. If no information exists for
        // a particular module then the module is ignored.
        val moduleMap = sdkExtensionInfo.extensionsMapForJarOrEmpty(mainlineModule)
        if (moduleMap.isEmpty())
            continue // TODO(b/259115852): remove this (though it is an optimization too).

        // Extension modules only have a major version so group by that.
        val byMajorVersion = moduleFiles.groupBy { it.version.major }
        val lastMajorVersion = byMajorVersion.keys.max()
        byMajorVersion.mapTo(list) { (extVersionNumber, files) ->
            val extVersion = ExtVersion.fromLevel(extVersionNumber)
            // If the extension is the last extension version then it will make sure that any
            // new APIs appear to have been added in `versionNotInAndroidSdk`.
            val isLatestExtVersion = extVersionNumber == lastMajorVersion
            val updater =
                ApiHistoryUpdater.forExtVersion(
                    versionNotInAndroidSdk,
                    extVersion,
                    mainlineModule,
                    isLatestExtVersion,
                )
            versionedApiFactory(updater, files)
        }
    }
}
