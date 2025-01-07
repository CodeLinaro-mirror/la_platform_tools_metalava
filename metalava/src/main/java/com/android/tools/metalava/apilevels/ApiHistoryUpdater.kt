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

/**
 * Encapsulates the process of updating the API history by marking an [ApiElement] as being included
 * in a specific [apiVersion].
 */
sealed interface ApiHistoryUpdater {
    /** The [ApiVersion] that this will use to update the [Api]. */
    val apiVersion: ApiVersion

    /**
     * Updates the API with information for a specific API version.
     *
     * @param api the [Api] to update.
     */
    fun update(api: Api)

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
    private open class ApiVersionUpdater(override val apiVersion: ApiVersion) : ApiHistoryUpdater {
        override fun update(api: Api) {
            api.update(apiVersion)
        }

        override fun update(apiElement: ApiElement, deprecated: Boolean) {
            apiElement.update(apiVersion, deprecated)
        }
    }

    /**
     * Extends [ApiVersionUpdater] to also update the [ApiElement.sinceExtension] and
     * [ApiElement.mainlineModule] properties.
     */
    private class ExtensionUpdater(
        nextSdkVersion: ApiVersion,
        private val extVersion: ExtVersion,
        private val module: String
    ) : ApiVersionUpdater(nextSdkVersion) {
        override fun update(api: Api) {
            // Do not update the Api with the next sdk version as that could cause all classes
            // which are not provided by an extension to be treated as being removed as they
            // may not have been recorded as being part of the next sdk version.
        }

        override fun update(apiElement: ApiElement, deprecated: Boolean) {
            super.update(apiElement, deprecated)
            apiElement.updateExtension(extVersion)
            if (apiElement is ApiClass) {
                apiElement.updateMainlineModule(module)
            }
        }
    }

    companion object {
        /** Create an [ApiHistoryUpdater] for [apiVersion]. */
        fun forApiVersion(apiVersion: ApiVersion): ApiHistoryUpdater {
            return ApiVersionUpdater(apiVersion)
        }

        /**
         * Create an [ApiHistoryUpdater] for an extension version [extVersion] of [module].
         *
         * If an [ApiElement] was not defined in a previously finalized SDK then this assumes it
         * will be finalized in the[nextSdkVersion].
         */
        fun forExtVersion(
            nextSdkVersion: ApiVersion,
            extVersion: ExtVersion,
            module: String
        ): ApiHistoryUpdater {
            return ExtensionUpdater(nextSdkVersion, extVersion, module)
        }
    }
}
