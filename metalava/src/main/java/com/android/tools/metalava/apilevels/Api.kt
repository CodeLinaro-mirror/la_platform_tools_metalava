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

import java.util.Collections
import java.util.TreeMap
import java.util.TreeSet

/**
 * Represents the whole Android API.
 *
 * @param useInternalNames `true` if JVM internal names should be used, `false` otherwise.
 */
class Api(val useInternalNames: Boolean) : ParentApiElement {
    /**
     * This has to behave as if it exists since before any specific version (so that every class
     * always specifies its `since` attribute.
     */
    override val since: ApiVersion = ApiVersion.LOWEST

    override var lastPresentIn = since
        private set

    override val sdks: String? = null

    override val deprecatedIn: ApiVersion? = null

    private val mClasses: MutableMap<String, ApiClass> = HashMap()

    /**
     * Updates this with information for a specific API version.
     *
     * @param apiVersion an API version that this contains.
     */
    fun update(apiVersion: ApiVersion) {
        // Track the last version added to this.
        if (lastPresentIn < apiVersion) {
            lastPresentIn = apiVersion
        }
    }

    override fun toString() = "Android Api"

    /**
     * Updates the [ApiClass] for the class called [name], creating and adding one if necessary.
     *
     * @param name the name of the class
     * @param updater the [ApiHistoryUpdater] that will update the element with information about
     *   the version to which it belongs.
     * @param deprecated whether the class was deprecated in the API version
     * @return the newly created or a previously existed class
     */
    fun updateClass(
        name: String,
        updater: ApiHistoryUpdater,
        deprecated: Boolean,
    ): ApiClass {
        val existing = mClasses[name]
        val classElement = existing ?: ApiClass(name).apply { mClasses[name] = this }
        updater.update(classElement, deprecated)
        return classElement
    }

    fun findClass(name: String?): ApiClass? {
        return if (name == null) null else mClasses[name]
    }

    /** Cleans up the API surface for printing after all elements have been added. */
    fun clean() {
        inlineFromHiddenSuperClasses()
        removeImplicitInterfaces()
        removeOverridingMethods()
        prunePackagePrivateClasses()
    }

    val classes: Collection<ApiClass>
        get() = Collections.unmodifiableCollection(mClasses.values)

    /**
     * Patch up the `android.os.ext.SdkExtensions` history to improve backward compatibility.
     *
     * This does nothing if the class is not defined in this [Api].
     */
    fun patchSdkExtensionsHistory() {
        val sdkExtensions =
            findClass("android/os/ext/SdkExtensions")
            // This is either for the module-lib/system-server (null) or for a non-Android API.
            // Either way it does not need patching.
            ?: return

        val sdk30 = ApiVersion.fromLevel(30)
        val sdk31 = ApiVersion.fromLevel(31)
        val sdk33 = ApiVersion.fromLevel(33)
        val sdkExtensionsSince = sdkExtensions.since
        if (sdkExtensionsSince != sdk30 && sdkExtensionsSince != sdk33) {
            throw AssertionError("Received unexpected historical data")
        } else if (sdkExtensionsSince == sdk30) {
            // This is the system API db (30). The class does not need patching but the members do.
            // Drop through.
        } else {
            // The class was added in 30/R, but was a SystemApi to avoid publishing the versioning
            // API publicly before there was any valid use for it. It was made public between S and
            // T, but we pretend here like it was always public, for maximum backward compatibility.
            sdkExtensions.update(sdk30, false)
        }

        val sdk30Updater = ApiHistoryUpdater.forApiVersion(sdk30)
        val sdk31Updater = ApiHistoryUpdater.forApiVersion(sdk31)

        // Remove the sdks attribute from the extends for public and system.
        sdkExtensions.updateSuperClass("java/lang/Object", sdk30Updater).apply {
            // Pretend this was not added in any extension.
            clearSdkExtensionInfo()
        }

        // getExtensionVersion was added in 30/R along with the class, and just like the class we
        // pretend it was always public.
        sdkExtensions.updateMethod("getExtensionVersion(I)I", sdk30Updater, false)

        // getAllExtensionsVersions was added as part of 31/S SystemApi. Just like for the class
        // we pretend it was always public.
        sdkExtensions
            .updateMethod("getAllExtensionVersions()Ljava/util/Map;", sdk31Updater, false)
            .apply {
                // Pretend this was not added in any extension.
                clearSdkExtensionInfo()
            }
    }

    /**
     * The bytecode visitor registers interfaces listed for a class. However, a class will **also**
     * implement interfaces implemented by the super classes. This isn't available in the class
     * file, so after all classes have been read in, we iterate through all classes, and for those
     * that have interfaces, we check up the inheritance chain to see if it has already been
     * introduced in a super class at an earlier API level.
     */
    private fun removeImplicitInterfaces() {
        for (classElement in mClasses.values) {
            classElement.removeImplicitInterfaces(mClasses)
        }
    }

    /** @see ApiClass.removeOverridingMethods */
    private fun removeOverridingMethods() {
        for (classElement in mClasses.values) {
            classElement.removeOverridingMethods(mClasses)
        }
    }

    private fun inlineFromHiddenSuperClasses() {
        val hidden: MutableMap<String, ApiClass> = HashMap()
        for (classElement in mClasses.values) {
            if (classElement.alwaysHidden) {
                // hidden in the .jar files? (mMax==codebase, -1: jar files)
                hidden[classElement.name] = classElement
            }
        }
        for (classElement in mClasses.values) {
            classElement.inlineFromHiddenSuperClasses(hidden)
        }
    }

    private fun prunePackagePrivateClasses() {
        for (cls in mClasses.values) {
            cls.removeHiddenSuperClasses(mClasses)
        }
    }

    fun removeMissingClasses() {
        for (cls in mClasses.values) {
            cls.removeMissingClasses(mClasses)
        }
    }

    fun verifyNoMissingClasses() {
        val results: MutableMap<String?, MutableSet<String?>> = TreeMap()
        for (cls in mClasses.values) {
            val missing = cls.findMissingClasses(mClasses)
            // Have the missing classes as keys, and the referencing classes as values.
            for (missingClass in missing) {
                val missingName = missingClass.name
                if (!results.containsKey(missingName)) {
                    results[missingName] = TreeSet()
                }
                results[missingName]!!.add(cls.name)
            }
        }
        if (results.isNotEmpty()) {
            var message = ""
            for ((key, value) in results) {
                message += """
  $key referenced by:"""
                for (referencer in value) {
                    message += "\n    $referencer"
                }
            }
            throw IllegalStateException(
                "There are classes in this API that reference other " +
                    "classes that do not exist in this API. " +
                    "This can happen when an api is provided by an apex, but referenced " +
                    "from non-updatable platform code. Use --remove-missing-classes-in-api-levels to " +
                    "make metalava remove these references instead of erroring out." +
                    message
            )
        }
    }
}
