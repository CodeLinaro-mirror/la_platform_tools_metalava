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

package com.android.tools.metalava.doc

import com.android.tools.metalava.apilevels.Api
import com.android.tools.metalava.apilevels.ApiElement
import com.android.tools.metalava.apilevels.ApiToExtensionsMap.Companion.ANDROID_PLATFORM_SDK_ID
import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.apilevels.ApiXmlParser
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import java.io.File

/**
 * Implementation of [ApiHistoryProvider] backed by Metalava's [Api] model and [ApiXmlParser].
 *
 * @param xmlFile the `api-versions.xml` file to load.
 */
class MetalavaApiHistoryProvider(xmlFile: File) : ApiHistoryProvider {

    /** The [Api] model parsed from [xmlFile]. */
    private val api: Api = ApiXmlParser.parse(xmlFile)

    /**
     * Returns the [ItemApiHistory] for [cls] from the parsed [Api], or `null` if [cls] is not
     * present in `api-versions.xml`.
     */
    override fun getApiHistory(cls: ClassItem): ItemApiHistory? {
        val apiClass = api.findClass(cls.internalName()) ?: return null
        return ItemApiHistory.create(
            since = apiClass.platformSince(),
            deprecatedIn = apiClass.deprecatedIn?.normalizeMinorZero(),
            sdkExtSince = null,
        )
    }

    /**
     * Returns the [ItemApiHistory] for [callable] from the parsed [Api], or `null` if [callable]'s
     * containing class is not present in `api-versions.xml` or [callable] has no recorded history.
     */
    override fun getApiHistory(callable: CallableItem): ItemApiHistory? {
        return null
    }

    /**
     * Returns the [ItemApiHistory] for [field] from the parsed [Api], or `null` if [field]'s
     * containing class is not present in `api-versions.xml` or [field] has no recorded history.
     */
    override fun getApiHistory(field: FieldItem): ItemApiHistory? {
        return null
    }

    companion object {
        /** [ApiHistoryProvider.Loader] that creates a [MetalavaApiHistoryProvider]. */
        val loader: ApiHistoryProvider.Loader =
            object : ApiHistoryProvider.Loader {
                override fun load(xmlFile: File) = MetalavaApiHistoryProvider(xmlFile)

                override fun toString() = "metalava"
            }
    }
}

/**
 * Normalizes an [ApiVersion] whose [ApiVersion.minor] is `0` to a major-only [ApiVersion] so that
 * `@apiSince` and `@deprecatedSince` tags omit a trailing `.0` (e.g. `36` instead of `36.0`).
 */
private fun ApiVersion.normalizeMinorZero(): ApiVersion =
    if (minor == 0) ApiVersion.fromLevel(major) else this

/**
 * Computes the Android platform [ApiVersion] for this [ApiElement], or `null` if this element only
 * exists in SDK extensions and has not been finalized in an Android platform SDK.
 *
 * When `sdks` is absent or empty, `since` is always an Android platform SDK version. When `sdks` is
 * present, `since` may instead hold an extension SDK version if the API was not yet finalized in an
 * Android platform SDK; therefore, only `0:<version>` entries (matching [ANDROID_PLATFORM_SDK_ID])
 * in `sdks` count as Android platform SDK versions.
 */
private fun ApiElement.platformSince(): ApiVersion? {
    val sdksAttr = sdks
    if (sdksAttr.isNullOrEmpty()) {
        return since.normalizeMinorZero()
    }
    var minPlatformVersion: ApiVersion? = null
    for (entry in sdksAttr.split(',')) {
        val colonIndex = entry.indexOf(':')
        if (colonIndex == -1) continue
        val sdkId = entry.substring(0, colonIndex).toIntOrNull() ?: continue
        if (sdkId == ANDROID_PLATFORM_SDK_ID) {
            val version =
                ApiVersion.fromString(entry.substring(colonIndex + 1)).normalizeMinorZero()
            if (minPlatformVersion == null || version < minPlatformVersion) {
                minPlatformVersion = version
            }
        }
    }
    return minPlatformVersion
}
