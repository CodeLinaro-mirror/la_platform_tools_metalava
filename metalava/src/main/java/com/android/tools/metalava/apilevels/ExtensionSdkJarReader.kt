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

import com.android.SdkConstants
import com.android.SdkConstants.PLATFORM_WINDOWS
import java.io.File

object ExtensionSdkJarReader {

    private val REGEX_JAR_PATH = run {
        var pattern = ".*/(\\d+)/[^/]+/(.*)\\.jar$"
        if (SdkConstants.currentPlatform() == PLATFORM_WINDOWS) {
            pattern = pattern.replace("/", "\\\\")
        }
        Regex(pattern)
    }

    /**
     * Find extension SDK jar files in an extension SDK tree.
     *
     * @return a mapping SDK jar file -> list of VersionAndPath objects, sorted from earliest to
     *   last version
     */
    internal fun findExtensionSdkJarFiles(root: File): Map<String, List<VersionAndPath>> {
        val map = mutableMapOf<String, MutableList<VersionAndPath>>()
        root
            .walk()
            .maxDepth(3)
            .mapNotNull { file ->
                REGEX_JAR_PATH.matchEntire(file.path)?.groups?.let { groups ->
                    Triple(groups[2]!!.value, groups[1]!!.value.toInt(), file)
                }
            }
            .sortedBy { it.second }
            .forEach {
                map.getOrPut(it.first) { mutableListOf() }.add(VersionAndPath(it.second, it.third))
            }
        return map
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
            for ((level, path) in value) {
                val extVersion = ExtVersion.fromLevel(level)
                val updater =
                    ApiHistoryUpdater.forExtVersion(
                        versionNotInAndroidSdk,
                        extVersion,
                        mainlineModule,
                    )
                list.add(VersionedJarApi(path, updater))
            }
        }
    }
}

data class VersionAndPath(@JvmField val version: Int, @JvmField val path: File)
