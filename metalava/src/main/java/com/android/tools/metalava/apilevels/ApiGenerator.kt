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
package com.android.tools.metalava.apilevels

import com.android.tools.metalava.SdkIdentifier
import com.android.tools.metalava.SignatureFileCache
import com.android.tools.metalava.apilevels.ApiToExtensionsMap.Companion.fromXml
import com.android.tools.metalava.apilevels.ExtensionSdkJarReader.Companion.findExtensionSdkJarFiles
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.snapshot.NonFilteringDelegatingVisitor
import com.android.tools.metalava.model.text.SignatureFile
import java.io.File
import java.io.IOException

/**
 * Main class for command line command to convert the existing API XML/TXT files into diff-based
 * simple text files.
 */
class ApiGenerator(private val signatureFileCache: SignatureFileCache) {
    @Throws(IOException::class, IllegalArgumentException::class)
    fun generateXml(
        apiLevels: Array<File>,
        firstApiLevel: Int,
        currentApiLevel: Int,
        isDeveloperPreviewBuild: Boolean,
        outputFile: File,
        codebaseFragment: CodebaseFragment,
        sdkExtensionsArguments: SdkExtensionsArguments?,
        removeMissingClasses: Boolean
    ): Boolean {
        val notFinalizedApiLevel = currentApiLevel + 1
        val api = createApiFromAndroidJars(apiLevels, firstApiLevel)
        if (isDeveloperPreviewBuild || apiLevels.size - 1 < currentApiLevel) {
            // Only include codebase if we don't have a prebuilt, finalized jar for it.
            val apiLevel = if (isDeveloperPreviewBuild) notFinalizedApiLevel else currentApiLevel
            addApisFromCodebase(api, apiLevel, codebaseFragment, true)
        }
        api.backfillHistoricalFixes()
        var sdkIdentifiers = emptySet<SdkIdentifier>()
        if (sdkExtensionsArguments != null) {
            sdkIdentifiers =
                processExtensionSdkApis(
                    api,
                    notFinalizedApiLevel,
                    sdkExtensionsArguments.sdkExtJarRoot,
                    sdkExtensionsArguments.sdkExtInfoFile,
                )
        }
        api.clean()
        if (removeMissingClasses) {
            api.removeMissingClasses()
        } else {
            api.verifyNoMissingClasses()
        }
        val printer = ApiXmlPrinter(sdkIdentifiers, firstApiLevel)
        return createApiLevelsFile(outputFile, printer, api)
    }

    /**
     * Creates an [Api] from a list of past API signature files. In the generated [Api], the oldest
     * API version will be represented as level 1, the next as level 2, etc.
     *
     * @param previousApiFiles A list of API signature files, one for each version of the API, in
     *   order from oldest to newest API version.
     */
    private fun createApiFromSignatureFiles(previousApiFiles: List<File>): Api {
        // Starts at level 1 because 0 is not a valid API level.
        var apiLevel = 1
        val api = Api()
        for (apiFile in previousApiFiles) {
            val codebase: Codebase = signatureFileCache.load(SignatureFile.fromFiles(apiFile))
            val codebaseFragment =
                CodebaseFragment.create(codebase, ::NonFilteringDelegatingVisitor)
            addApisFromCodebase(api, apiLevel, codebaseFragment, false)
            apiLevel += 1
        }
        api.clean()
        return api
    }

    /**
     * Generates an API version history file based on the API surfaces of the versions provided.
     *
     * @param pastApiVersions A list of API signature files, ordered from the oldest API version to
     *   newest.
     * @param currentApiVersion A codebase representing the current API surface.
     * @param outputFile Path of the JSON file to write output to.
     * @param apiVersionNames The names of the API versions, ordered starting from version 1. This
     *   should include the names of all the [pastApiVersions], then the name of the
     *   [currentApiVersion].
     */
    fun generateJson(
        pastApiVersions: List<File>,
        currentApiVersion: CodebaseFragment,
        outputFile: File,
        apiVersionNames: List<String>,
    ) {
        val api = createApiFromSignatureFiles(pastApiVersions)
        addApisFromCodebase(
            api,
            apiVersionNames.size,
            currentApiVersion,
            false,
        )
        val printer = ApiJsonPrinter(apiVersionNames)
        createApiLevelsFile(outputFile, printer, api)
    }

    private fun createApiFromAndroidJars(apiLevels: Array<File>, firstApiLevel: Int): Api {
        val api = Api()
        for (apiLevel in firstApiLevel until apiLevels.size) {
            val jar = apiLevels[apiLevel]
            api.readAndroidJar(apiLevel, jar)
        }
        return api
    }

    /**
     * Modify the extension SDK API parts of an API as dictated by a filter.
     * - remove APIs not listed in the filter
     * - assign APIs listed in the filter their corresponding extensions
     *
     * Some APIs only exist in extension SDKs and not in the Android SDK, but for backwards
     * compatibility with tools that expect the Android SDK to be the only SDK, metalava needs to
     * assign such APIs some Android SDK API level. The recommended value is current-api-level + 1,
     * which is what non-finalized APIs use.
     *
     * @param api the api to modify
     * @param apiLevelNotInAndroidSdk fallback API level for APIs not in the Android SDK
     * @param sdkJarRoot path to directory containing extension SDK jars (usually
     *   $ANDROID_ROOT/prebuilts/sdk/extensions)
     * @param filterPath path to the filter file. @see ApiToExtensionsMap
     * @throws IOException if the filter file can not be read
     * @throws IllegalArgumentException if an error is detected in the filter file, or if no jar
     *   files were found
     */
    @Throws(IOException::class, IllegalArgumentException::class)
    private fun processExtensionSdkApis(
        api: Api,
        apiLevelNotInAndroidSdk: Int,
        sdkJarRoot: File,
        filterPath: File,
    ): Set<SdkIdentifier> {
        val rules = filterPath.readText()
        val map = findExtensionSdkJarFiles(sdkJarRoot)
        require(map.isNotEmpty()) { "no extension sdk jar files found in $sdkJarRoot" }
        val moduleMaps: MutableMap<String, ApiToExtensionsMap> = HashMap()
        for ((mainlineModule, value) in map) {
            val moduleMap = fromXml(mainlineModule, rules)
            if (moduleMap.isEmpty())
                continue // TODO(b/259115852): remove this (though it is an optimization too).
            moduleMaps[mainlineModule] = moduleMap
            for ((version, path) in value) {
                api.readExtensionJar(version, mainlineModule, path, apiLevelNotInAndroidSdk)
            }
        }
        for (clazz in api.classes) {
            val module = clazz.mainlineModule ?: continue
            val extensionsMap = moduleMaps[module]
            var sdks =
                extensionsMap!!.calculateSdksAttr(
                    clazz.since,
                    apiLevelNotInAndroidSdk,
                    extensionsMap.getExtensions(clazz),
                    clazz.sinceExtension
                )
            clazz.updateSdks(sdks)
            var iter = clazz.fieldIterator
            while (iter.hasNext()) {
                val field = iter.next()
                sdks =
                    extensionsMap.calculateSdksAttr(
                        field.since,
                        apiLevelNotInAndroidSdk,
                        extensionsMap.getExtensions(clazz, field),
                        field.sinceExtension
                    )
                field.updateSdks(sdks)
            }
            iter = clazz.methodIterator
            while (iter.hasNext()) {
                val method = iter.next()
                sdks =
                    extensionsMap.calculateSdksAttr(
                        method.since,
                        apiLevelNotInAndroidSdk,
                        extensionsMap.getExtensions(clazz, method),
                        method.sinceExtension
                    )
                method.updateSdks(sdks)
            }
        }
        return fromXml("", rules).getSdkIdentifiers()
    }

    /**
     * Creates a file containing the [api].
     *
     * @param outFile the output file
     * @param printer the [ApiPrinter] to use to write the file.
     * @param api the api to write
     */
    private fun createApiLevelsFile(
        outFile: File,
        printer: ApiPrinter,
        api: Api,
    ): Boolean {
        val parentFile = outFile.parentFile
        if (!parentFile.exists()) {
            val ok = parentFile.mkdirs()
            if (!ok) {
                System.err.println("Could not create directory $parentFile")
                return false
            }
        }
        try {
            outFile.printWriter().use { writer -> printer.print(api, writer) }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    data class SdkExtensionsArguments(
        var sdkExtJarRoot: File,
        var sdkExtInfoFile: File,
    )
}
