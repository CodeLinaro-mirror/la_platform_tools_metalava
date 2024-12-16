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

/** Represents a parent of [ApiElement]. */
interface ParentApiElement {
    /** The API version this API was first introduced in. */
    val since: SdkVersion

    /**
     * The version in which this API last appeared, if this is not the latest API then it will be
     * treated as having been removed in the next API version, i.e. [lastPresentIn] + 1.
     */
    val lastPresentIn: SdkVersion

    /**
     * The SDKs and their versions this API was first introduced in.
     *
     * The value is a comma-separated list of &lt;int&gt;:&lt;int&gt; values, where the first
     * &lt;int&gt; is the integer ID of an SDK, and the second &lt;int&gt; the version of that SDK,
     * in which this API first appeared.
     *
     * This field is a super-set of mSince, and if non-null/non-empty, should be preferred.
     */
    val sdks: String?

    /** The optional API level this element was deprecated in. */
    val deprecatedIn: SdkVersion?
}

/**
 * Represents an API element, e.g. class, method or field.
 *
 * @param name the name of the API element
 */
open class ApiElement(val name: String) : ParentApiElement, Comparable<ApiElement> {

    /**
     * The Android API level of this ApiElement. i.e. The Android platform SDK version this API was
     * first introduced in.
     */
    final override lateinit var since: SdkVersion
        private set

    /**
     * The extension version of this ApiElement. i.e. The Android extension SDK version this API was
     * first introduced in.
     */
    var sinceExtension: ExtVersion? = null
        private set

    final override var sdks: String? = null
        private set

    var mainlineModule: String? = null
        private set

    /** The optional API level this element was deprecated in. */
    final override var deprecatedIn: SdkVersion? = null
        private set

    final override lateinit var lastPresentIn: SdkVersion
        private set

    override fun toString(): String {
        return name
    }

    /**
     * Checks if this API element was introduced not later than another API element.
     *
     * @param other the API element to compare to
     * @return true if this API element was introduced not later than `other`
     */
    fun introducedNotLaterThan(other: ApiElement?): Boolean {
        return since <= other!!.since
    }

    /**
     * Updates the API element with information for a specific API version.
     *
     * @param sdkVersion an API version for which the API element existed
     * @param deprecated whether the API element was deprecated in the API version in question
     */
    fun update(sdkVersion: SdkVersion, deprecated: Boolean = deprecatedIn != null) {
        assert(sdkVersion.isValid)
        if (!::since.isInitialized || since > sdkVersion) {
            since = sdkVersion
        }
        if (!::lastPresentIn.isInitialized || lastPresentIn < sdkVersion) {
            lastPresentIn = sdkVersion
        }
        val deprecatedVersion = deprecatedIn
        if (deprecated) {
            // If it was not previously deprecated or was deprecated in a later version than this
            // one then deprecate it in this version.
            if (deprecatedVersion == null || deprecatedVersion > sdkVersion) {
                deprecatedIn = sdkVersion
            }
        } else {
            // If it was previously deprecated and was deprecated in an earlier version than this
            // one then treat it as being undeprecated.
            if (deprecatedVersion != null && deprecatedVersion < sdkVersion) {
                deprecatedIn = null
            }
        }
    }

    /**
     * Analogous to update(), but for extensions sdk versions.
     *
     * @param extVersion an extension SDK version for which the API element existed
     */
    fun updateExtension(extVersion: ExtVersion) {
        assert(extVersion.isValid)
        // Record the earliest extension in which this appeared.
        if (sinceExtension == null || sinceExtension!! > extVersion) {
            sinceExtension = extVersion
        }
    }

    /**
     * Clears the sdk extension information from this [ApiElement].
     *
     * This is only intended for use by [Api.backfillSdkExtensions].
     */
    fun clearSdkExtensionInfo() {
        this.sinceExtension = null
        this.sdks = null
    }

    fun updateSdks(sdks: String?) {
        this.sdks = sdks
    }

    fun updateMainlineModule(module: String?) {
        mainlineModule = module
    }

    override fun compareTo(other: ApiElement): Int {
        return name.compareTo(other.name)
    }

    /**
     * Encapsulates the process of updating an [ApiElement] to mark it as being included in a
     * specific API version.
     */
    sealed interface Updater {
        /**
         * Updates the API element with information for a specific API version.
         *
         * @param apiElement the [ApiElement] to update.
         * @param deprecated whether the API element was deprecated in the API version in question
         */
        fun update(
            apiElement: ApiElement,
            deprecated: Boolean = apiElement.deprecatedIn != null,
        )

        /** Updates the [ApiElement] by calling [ApiElement.update]. */
        private open class SdkVersionUpdater(private val sdkVersion: SdkVersion) : Updater {
            override fun update(apiElement: ApiElement, deprecated: Boolean) {
                apiElement.update(sdkVersion, deprecated)
            }
        }

        /**
         * Extends [SdkVersionUpdater] to also update the [ApiElement.sinceExtension] and
         * [ApiElement.mainlineModule] properties.
         */
        private class ExtensionUpdater(
            nextSdkVersion: SdkVersion,
            private val extVersion: ExtVersion,
            private val module: String
        ) : SdkVersionUpdater(nextSdkVersion) {
            override fun update(apiElement: ApiElement, deprecated: Boolean) {
                super.update(apiElement, deprecated)
                apiElement.updateExtension(extVersion)
                if (apiElement is ApiClass) {
                    apiElement.updateMainlineModule(module)
                }
            }
        }

        companion object {
            /** Create an [Updater] for [sdkVersion]. */
            fun forSdkVersion(sdkVersion: SdkVersion): Updater {
                return SdkVersionUpdater(sdkVersion)
            }

            fun forExtVersion(
                nextSdkVersion: SdkVersion,
                extVersion: ExtVersion,
                module: String
            ): Updater {
                return ExtensionUpdater(nextSdkVersion, extVersion, module)
            }
        }
    }
}
