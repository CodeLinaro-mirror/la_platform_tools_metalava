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
package com.android.tools.metalava

import com.android.tools.metalava.apilevels.ApiVersion

/**
 * ID and aliases for a given SDK extension.
 *
 * An SDK extension has an [id] corresponding to an Android dessert release that it extends, e.g.
 * the T extension has an [id] of 33.
 *
 * @param id: numerical ID of the extension SDK, primarily used in generated artifacts and consumed
 *   by tools
 * @param shortname: short name for the SDK, primarily used in configuration files
 * @param name: human-readable name for the SDK; used in the official documentation
 * @param reference: Java symbol in the Android SDK with the same numerical value as the id, using a
 *   JVM signature like syntax: "some/clazz$INNER$FIELD"
 */
sealed class SdkExtension
private constructor(
    val id: Int,
    val shortname: String,
    val name: String,
    val reference: String,
) {
    /**
     * Check to see whether this SDK extension supersedes the Android SDK version
     * [androidSdkVersion].
     *
     * A dessert based extension supersedes an Android SDK version if it is based on that version or
     * earlier.
     *
     * A dessert independent extension will always supersede an Android SDK version.
     *
     * @param androidSdkVersion the version of the Android SDK in which an API element was added.
     */
    abstract fun supersedesAndroidSdkVersion(androidSdkVersion: ApiVersion): Boolean

    init {
        require(id >= 1) { "SDK extensions cannot have an id less than 1 but it is $id" }
    }

    companion object {
        /**
         * Create an [SdkExtension] from the attributes that appear in sdk-extension-info.xml and
         * api-versions.xml files.
         *
         * If [id] is greater than or equal to [DESSERT_RELEASE_INDEPENDENT_SDK_BASE] then the
         * [SdkExtension] is independent of the Android SDK version, otherwise [id] is the base SDK
         * version of the extension.
         */
        fun fromXmlAttributes(id: Int, shortname: String, name: String, reference: String) =
            if (id >= DESSERT_RELEASE_INDEPENDENT_SDK_BASE)
                DessertReleaseIndependentSdkExtension(id, shortname, name, reference)
            else DessertReleaseBasedSdkExtension(id, shortname, name, reference)

        /**
         * The base of dessert release independent SDKs.
         *
         * A dessert release independent SDK is one which is not coupled to the Android dessert
         * release numbering. Any SDK greater than or equal to this is not comparable to either each
         * other, or to the Android dessert release. e.g. `1000000` is not the same as, later than,
         * or earlier than SDK 31. Similarly, `1000001` is not the same as, later than, or earlier
         * then `1000000`.
         */
        private const val DESSERT_RELEASE_INDEPENDENT_SDK_BASE = 1000000
    }

    /**
     * An [SdkExtension] that is based on a specific version of the Android Sdk.
     *
     * The [id] is the major version of the Android SDK on which this is based.
     */
    private class DessertReleaseBasedSdkExtension(
        id: Int,
        shortname: String,
        name: String,
        reference: String,
    ) : SdkExtension(id, shortname, name, reference) {

        /**
         * The base [ApiVersion] of this extension. This the version of the Android SDK to which
         * this extension applies.
         */
        private val baseSdkVersion = ApiVersion.fromLevel(id)

        override fun supersedesAndroidSdkVersion(androidSdkVersion: ApiVersion) =
            baseSdkVersion <= androidSdkVersion
    }

    /** An [SdkExtension] that is independent of an Android SDK version. */
    private class DessertReleaseIndependentSdkExtension(
        id: Int,
        shortname: String,
        name: String,
        reference: String,
    ) : SdkExtension(id, shortname, name, reference) {
        override fun supersedesAndroidSdkVersion(androidSdkVersion: ApiVersion) = true
    }
}
