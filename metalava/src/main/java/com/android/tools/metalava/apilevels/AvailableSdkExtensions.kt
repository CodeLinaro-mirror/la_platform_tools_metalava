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

import com.android.tools.metalava.SdkExtension
import com.android.tools.metalava.apilevels.ApiToExtensionsMap.Companion.ANDROID_PLATFORM_SDK_ID

/** The set of available SDK extensions. */
class AvailableSdkExtensions(internal val sdkExtensions: Set<SdkExtension>) {

    init {
        // verify: the predefined Android platform SDK ID is not reused as an extension SDK ID
        if (sdkExtensions.any { it.id == ANDROID_PLATFORM_SDK_ID }) {
            throw IllegalArgumentException(
                "bad SDK definition: the ID $ANDROID_PLATFORM_SDK_ID is reserved for the Android platform SDK"
            )
        }

        // verify: no duplicate SDK IDs
        if (sdkExtensions.size != sdkExtensions.distinctBy { it.id }.size) {
            throw IllegalArgumentException("bad SDK definitions: duplicate SDK IDs")
        }

        // verify: no duplicate SDK names
        if (sdkExtensions.size != sdkExtensions.distinctBy { it.shortname }.size) {
            throw IllegalArgumentException("bad SDK definitions: duplicate SDK short names")
        }

        // verify: no duplicate SDK names
        if (sdkExtensions.size != sdkExtensions.distinctBy { it.name }.size) {
            throw IllegalArgumentException("bad SDK definitions: duplicate SDK names")
        }

        // verify: no duplicate SDK references
        if (sdkExtensions.size != sdkExtensions.distinctBy { it.reference }.size) {
            throw IllegalArgumentException("bad SDK definitions: duplicate SDK references")
        }
    }

    private val shortToSdkExtension = sdkExtensions.associateBy { it.shortname }

    /** Check to see if this contains an SDK extension with [shortExtensionName]. */
    fun containsSdkExtension(shortExtensionName: String) = shortExtensionName in shortToSdkExtension

    /**
     * Retrieve the SDK extension appropriate for the [shortExtensionName], throwing an exception if
     * it could not be found.
     */
    fun retrieveSdkExtension(shortExtensionName: String): SdkExtension {
        return shortToSdkExtension[shortExtensionName]
            ?: throw IllegalStateException("unknown extension SDK \"$shortExtensionName\"")
    }
}
