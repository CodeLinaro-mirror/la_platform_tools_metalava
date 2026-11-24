/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.manifest

import com.android.tools.metalava.ATTR_NAME
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.xml.getFirstSubTagByName
import com.android.tools.metalava.xml.getNextTagByName
import com.android.tools.metalava.xml.parseDocument
import java.io.File

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val TAG_PERMISSION = "permission"
private const val TAG_USES_SDK = "uses-sdk"
private const val ATTR_MIN_SDK_VERSION = "minSdkVersion"

/**
 * An empty manifest. This is safe to share as while it is not strictly immutable it only mutates
 * the object when lazily initializing [Manifest.info].
 */
val emptyManifest: Manifest by lazy { Manifest(null, null) }

private data class ManifestInfo(
    val permissions: Map<String, String>,
    val minSdkVersion: MinSdkVersion
)

private val defaultInfo = ManifestInfo(emptyMap(), UnsetMinSdkVersion)

/** Provides access to information from an `AndroidManifest.xml` file. */
class Manifest(private val manifest: File?, private val reporter: Reporter?) {

    private val info: ManifestInfo by lazy { readManifestInfo() }

    private fun readManifestInfo(): ManifestInfo {
        if (manifest == null) {
            return defaultInfo
        }

        return try {
            val doc = parseDocument(manifest.readText(Charsets.UTF_8), true)

            // Extract permissions.
            val map = HashMap<String, String>(600)
            var current = getFirstSubTagByName(doc.documentElement, TAG_PERMISSION)
            while (current != null) {
                val permissionName = current.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val protectionLevel = current.getAttributeNS(ANDROID_URI, "protectionLevel")
                map[permissionName] = protectionLevel
                current = getNextTagByName(current, TAG_PERMISSION)
            }

            // Extract minSdkVersion.
            val min: MinSdkVersion = run {
                val usesSdk = getFirstSubTagByName(doc.documentElement, TAG_USES_SDK)
                if (usesSdk == null) {
                    UnsetMinSdkVersion
                } else {
                    val value = usesSdk.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
                    if (value.isEmpty()) UnsetMinSdkVersion else SetMinSdkVersion(value.toInt())
                }
            }

            ManifestInfo(map, min)
        } catch (error: Throwable) {
            reporter?.report(
                Issues.PARSE_ERROR,
                manifest,
                "Failed to parse $manifest: ${error.message}"
            ) ?: throw error
            defaultInfo
        }
    }

    /** Check whether the manifest is empty or not. */
    fun isEmpty(): Boolean {
        return manifest == null
    }

    /**
     * Returns the permission level of the named permission, if specified in the manifest. This
     * method should only be called if the codebase has been configured with a manifest
     */
    fun getPermissionLevel(name: String): String? {
        assert(manifest != null) {
            "This method should only be called when a manifest has been configured on the codebase"
        }

        return info.permissions[name]
    }

    fun getMinSdkVersion(): MinSdkVersion {
        return info.minSdkVersion
    }

    override fun toString(): String {
        return manifest.toString()
    }
}

sealed class MinSdkVersion

data class SetMinSdkVersion(val value: Int) : MinSdkVersion()

object UnsetMinSdkVersion : MinSdkVersion()
