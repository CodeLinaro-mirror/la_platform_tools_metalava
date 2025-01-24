/*
 * Copyright (C) 2022 The Android Open Source Project
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

import java.io.File

/**
 * Scan for and read extension jars.
 *
 * @param surface the optional surface of extension jars to read. If specified then only extension
 *   jars in the `extensions/<version>/<surface>` directories will be used. Otherwise, any available
 *   jar in the `extensions/<version>/<any>` directories will be used. The latter is used in the
 *   Android build as it uses a sandbox to provide only those extension jars in the correct surface
 *   anyway.
 */
class ExtensionSdkJarReader(private val surface: String?) {

    /**
     * Find extension SDK jar files in an extension SDK tree.
     *
     * @return a mapping from SDK module name -> list of [MatchedPatternFile] objects, sorted from
     *   earliest to last version.
     */
    internal fun findExtensionSdkJarFiles(root: File): Map<String, List<MatchedPatternFile>> {
        val node =
            PatternNode.parsePatterns(
                listOf(
                    "{version:level}/${surface?:"*"}/{module}.jar",
                )
            )
        return node.scan(PatternNode.ScanConfig(dir = root)).groupBy({ it.module!! }) {
            it.copy(file = root.resolve(it.file).normalize())
        }
    }

    /**
     * Find the extension jars and versions for all modules, wrap in a [VersionedApi] and add them
     * to [list].
     *
     * Some APIs only exist in extension SDKs and not in the Android SDK, but for backwards
     * compatibility with tools that expect the Android SDK to be the only SDK, metalava needs to
     * assign such APIs some Android SDK API version. This uses [versionNotInAndroidSdk].
     *
     * @param versionNotInAndroidSdk fallback API level for APIs not in the Android SDK
     * @param sdkJarRoot path to directory containing extension SDK jars (usually
     *   $ANDROID_ROOT/prebuilts/sdk/extensions)
     * @param sdkExtensionInfo the [SdkExtensionInfo] read from sdk-extension-info.xml file.
     */
    fun addVersionedExtensionApis(
        list: MutableList<VersionedApi>,
        versionNotInAndroidSdk: ApiVersion,
        sdkJarRoot: File,
        sdkExtensionInfo: SdkExtensionInfo,
    ) {
        val map = findExtensionSdkJarFiles(sdkJarRoot)
        require(map.isNotEmpty()) { "no extension sdk jar files found in $sdkJarRoot" }

        // Iterate over the mainline modules and their different versions.
        for ((mainlineModule, value) in map) {
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
}
