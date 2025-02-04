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

import com.android.tools.metalava.apilevels.ApiGenerator.SdkExtensionsArguments
import com.android.tools.metalava.model.Codebase
import java.io.File

/**
 * Properties for the [ApiGenerator.generateApiHistory] method that come from command line options.
 */
data class GenerateApiHistoryConfig(
    /**
     * A list of [VersionedApi]s that contribute to the API history.
     *
     * i.e.
     * 1. Historical, i.e. previously released, APIs, in order from the oldest API version to the
     *    newest.
     * 2. An optional [VersionedApi] for the current sources [Codebase], whose version is higher
     *    than the last historical version.
     * 3. The optional [VersionedApi]s for SDK extensions, grouped by module and in order from the
     *    oldest. API version to the newest.
     */
    val versionedApis: List<VersionedApi>,

    /** The API levels file that will be generated. */
    val outputFile: File,

    /**
     * Optional SDK extensions arguments.
     *
     * If provided then SDK extension information will be included in the API levels file.
     */
    val sdkExtensionsArguments: SdkExtensionsArguments?,

    /**
     * Determines what to do with If `true` then any references to undefined classes will be removed
     * from super class and interface lists; otherwise any such references will be treated as an
     * error.
     *
     * An undefined class is one that is not defined within any of the API versions loaded.
     */
    val missingClassAction: MissingClassAction,
)

/**
 * Possible actions to take when an [Api] object contains references in extends and implements types
 * to classes which are not defined within the [Api] (i.e. have no corresponding [ApiClass] because
 * they were not defined within any of the API versions loaded).
 */
enum class MissingClassAction {
    /** Keep the references in the [Api]. */
    KEEP {
        override fun apply(api: Api) {
            // Do nothing.
        }
    },

    /** Remove the references from the [Api]. */
    REMOVE {
        override fun apply(api: Api) {
            api.removeMissingClasses()
        }
    },

    /** Report the references as errors. */
    REPORT {
        override fun apply(api: Api) {
            api.verifyNoMissingClasses()
        }
    };

    /** Apply this action to [api]. */
    abstract fun apply(api: Api)
}
