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

import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter

/** Represents a parent of [ApiElement]. */
interface ParentApiElement {
    /** The API version this API was first introduced in. */
    val since: ApiVersion

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
    val deprecatedIn: ApiVersion?

    /** The optional API level this element was removed in. */
    val removedIn: ApiVersion?
}

/**
 * Represents an API element, e.g. class, method or field.
 *
 * @param name the name of the API element
 */
open class ApiElement(val name: String) : ParentApiElement, Comparable<ApiElement> {
    /**
     * A special constructor, used by [ApiClass.addClassDependentMembersIfNeeded] that will be used
     * to create additional members on [containingClass] whose API history is identical to the
     * class.
     *
     * This is currently used for compiler generated enum methods `valueOf(String)` and `values()`.
     */
    internal constructor(name: String, containingClass: ApiClass) : this(name) {
        since = containingClass.since
        sinceExtension = containingClass.sinceExtension
        sdks = containingClass.sdks
        mainlineModule = containingClass.mainlineModule
        deprecatedIn = containingClass.deprecatedIn
        lastPresentIn = containingClass.lastPresentIn
        removedIn = containingClass.removedIn
    }

    /**
     * Initializes this element with explicit attributes read from an `api-versions.xml` file.
     *
     * @param since the API version in which this element was introduced.
     * @param sdks the optional comma-separated SDK extension versions string.
     * @param deprecatedIn the optional API version in which this element was deprecated.
     * @param removedIn the optional API version in which this element was removed.
     * @param mainlineModule the optional mainline module name for this element.
     */
    internal fun initFromXml(
        since: ApiVersion,
        sdks: String? = null,
        deprecatedIn: ApiVersion? = null,
        removedIn: ApiVersion? = null,
        mainlineModule: String? = null,
    ) {
        this.since = since
        this.sdks = sdks
        this.deprecatedIn = deprecatedIn
        this.removedIn = removedIn
        this.mainlineModule = mainlineModule
    }

    /**
     * The Android API level of this ApiElement. i.e. The Android platform SDK version this API was
     * first introduced in.
     */
    final override lateinit var since: ApiVersion
        protected set

    /** True if [since] has been initialized. */
    internal val isSinceInitialized: Boolean
        get() = ::since.isInitialized

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
    final override var deprecatedIn: ApiVersion? = null
        private set

    /**
     * The version in which this API last appeared, if this is not the latest API then it will be
     * treated as having been removed in the next API version.
     */
    lateinit var lastPresentIn: ApiVersion
        private set

    /** The optional API level this element was removed in. */
    final override var removedIn: ApiVersion? = null
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
     * @param apiVersion an API version for which the API element existed
     * @param deprecated whether the API element was deprecated in the API version in question
     */
    fun update(apiVersion: ApiVersion, deprecated: Boolean = deprecatedIn != null) {
        assert(apiVersion.isValid)
        if (!::since.isInitialized || since > apiVersion) {
            since = apiVersion
        }
        if (!::lastPresentIn.isInitialized || lastPresentIn < apiVersion) {
            lastPresentIn = apiVersion
        }
        val deprecatedVersion = deprecatedIn
        if (deprecated) {
            // If it was not previously deprecated or was deprecated in a later version than this
            // one then deprecate it in this version.
            if (deprecatedVersion == null || deprecatedVersion > apiVersion) {
                deprecatedIn = apiVersion
            }
        } else {
            // If it was previously deprecated and was deprecated in an earlier version than this
            // one then treat it as being undeprecated.
            if (deprecatedVersion != null && deprecatedVersion < apiVersion) {
                deprecatedIn = null
            }
        }
    }

    /** Updates [lastPresentIn] if [version] is greater than the current [lastPresentIn]. */
    fun updateLastPresentIn(version: ApiVersion) {
        if (!::lastPresentIn.isInitialized || lastPresentIn < version) {
            lastPresentIn = version
        }
    }

    /**
     * Updates the [deprecatedIn] version of this element if it was not already deprecated in an
     * earlier version.
     */
    fun updateDeprecatedIn(version: ApiVersion) {
        if (deprecatedIn == null || deprecatedIn!! > version) {
            deprecatedIn = version
        }
    }

    /** Updates the [removedIn] version of this element to [version]. */
    fun updateRemovedIn(version: ApiVersion) {
        removedIn = version
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
     * This is only intended for use by [Api.patchSdkExtensionsHistory].
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
}

/**
 * Removes any [ApiElement] in this map whose [ApiElement.since] property has not been initialized,
 * reporting an [Issues.INCONSISTENT_API_HISTORY] issue for each one.
 */
internal fun <T : ApiElement> MutableMap<String, T>.removeInconsistentExtensionElements(
    reporter: Reporter,
    mainlineModule: String? = null,
    describeElement: (T) -> String,
) {
    values.removeAll { element ->
        if (!element.isSinceInitialized) {
            val module = element.mainlineModule ?: mainlineModule
            reporter.report(
                Issues.INCONSISTENT_API_HISTORY,
                reportable = null,
                "${describeElement(element)} was added in SDK extension ${element.sinceExtension} (module $module) but is missing from the latest SDK extension and all SDK versions",
            )
            true
        } else {
            false
        }
    }
}

operator fun ApiVersion?.compareTo(other: ApiVersion?): Int =
    if (this == null) {
        if (other == null) 0 else -1
    } else if (other == null) +1 else this.compareTo(other)
