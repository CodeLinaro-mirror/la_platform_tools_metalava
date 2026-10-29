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

package com.android.tools.metalava.cli.compatibility

import com.android.tools.metalava.cli.common.PreviouslyReleasedApi
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.visitors.ApiType
import java.io.File

/**
 * Encapsulates information needed to perform a compatibility check of the current API being
 * generated against a previously released API.
 */
data class CheckRequest(
    /**
     * The previously released API with which the API being generated must be compatible.
     *
     * Each file is either a jar file (i.e. has an extension of `.jar`), or otherwise is a signature
     * file. The latter's extension is not checked because while it usually has an extension of
     * `.txt`, for legacy reasons Metalava will treat any file without a `,jar` extension as if it
     * was a signature file.
     */
    val previouslyReleasedApi: PreviouslyReleasedApi,

    /** The part of the API to be checked. */
    val type: CheckType,
) {
    /** The last signature file, if any, defining the previously released API. */
    val lastSignatureFile by previouslyReleasedApi::lastSignatureFile

    /**
     * Used to store whether the fast path check in
     * [com.android.tools.metalava.Driver.checkCompatibility] succeeded or not that can be checked
     * by tests.
     *
     * It is initialized to `null`. Then if the fast path check is run it will set it a non-null to
     * indicate whether the fast path was taken or not. The test can then differentiate between the
     * following states:
     * * `null` - the fast path check was not performed.
     * * `false` - the fast path check was performed and the fast path was not taken.
     * * `true` - the fast path check was performed and the fast path was taken.
     *
     * This is used because there is no nice way to test this code in isolation.
     */
    internal var fastPathCheckResult: Boolean? = null

    enum class CheckType(
        val cliFlagInfix: String,
        val displayName: String = cliFlagInfix,
        val apiType: ApiType,
    ) {
        /** The public API */
        PUBLIC_API(
            cliFlagInfix = "api",
            displayName = "public",
            apiType = ApiType.CORE,
        ),

        /** The API that has been removed */
        REMOVED(
            cliFlagInfix = "removed",
            displayName = "removed",
            apiType = ApiType.REMOVED,
        ),
        ;

        override fun toString(): String = displayName
    }

    companion object {
        /** Create a [CheckRequest] if [files] is not empty, otherwise return `null`. */
        internal fun optionalCheckRequest(files: List<File>, checkType: CheckType) =
            PreviouslyReleasedApi.optionalPreviouslyReleasedApi(
                    checkCompatibilityOptionForCheckType(checkType),
                    files,
                    apiVariantType =
                        when (checkType.apiType) {
                            ApiType.REMOVED -> ApiVariantType.REMOVED
                            else -> ApiVariantType.CORE
                        },
                )
                ?.let { previouslyReleasedApi -> CheckRequest(previouslyReleasedApi, checkType) }

        private fun checkCompatibilityOptionForCheckType(checkType: CheckType) =
            "--check-compatibility:${checkType.cliFlagInfix}:released"
    }

    override fun toString(): String {
        // This is only used when reporting progress.
        return "${checkCompatibilityOptionForCheckType(type)} $previouslyReleasedApi"
    }
}
