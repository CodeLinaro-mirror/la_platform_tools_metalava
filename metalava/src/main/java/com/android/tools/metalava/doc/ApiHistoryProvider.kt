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

import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.model.Item

/**
 * Represents the SDK extension and version in which an API item was first introduced, used to
 * populate `@sdkExtSince` documentation tags.
 */
data class SdkAndVersion(
    /** The display name of the SDK extension, e.g. `"R Extensions"`. */
    val name: String,
    /** The SDK extension version number in which the API item was introduced. */
    val version: Int,
)

/**
 * Historical API version metadata for a single [Item] (class, callable, or field) used by
 * [DocAnalyzer] to populate `@apiSince`, `@deprecatedSince`, and `@sdkExtSince` documentation tags.
 */
data class ItemApiHistory(
    /**
     * The Android platform [ApiVersion] in which the [Item] was introduced, or `null` if unknown.
     */
    val since: ApiVersion? = null,
    /**
     * The Android platform [ApiVersion] in which the [Item] was deprecated, or `null` if not
     * deprecated.
     */
    val deprecatedIn: ApiVersion? = null,
    /**
     * The first SDK extension and version in which the [Item] was introduced, or `null` if it was
     * not introduced in an SDK extension.
     */
    val sdkExtSince: SdkAndVersion? = null,
) {
    companion object {
        /**
         * Creates an [ItemApiHistory] from [since], [deprecatedIn], and [sdkExtSince], or returns
         * `null` if all three values are `null`.
         */
        fun create(
            since: ApiVersion?,
            deprecatedIn: ApiVersion?,
            sdkExtSince: SdkAndVersion?,
        ): ItemApiHistory? {
            if (since == null && deprecatedIn == null && sdkExtSince == null) return null
            return ItemApiHistory(since, deprecatedIn, sdkExtSince)
        }
    }
}
