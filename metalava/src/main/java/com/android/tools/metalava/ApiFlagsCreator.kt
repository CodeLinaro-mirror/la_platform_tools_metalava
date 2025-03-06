/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.metalava.config.ApiFlagConfig
import com.android.tools.metalava.config.ApiFlagConfig.Mutability.IMMUTABLE
import com.android.tools.metalava.config.ApiFlagConfig.Mutability.MUTABLE
import com.android.tools.metalava.config.ApiFlagConfig.Status.DISABLED
import com.android.tools.metalava.config.ApiFlagConfig.Status.ENABLED
import com.android.tools.metalava.config.ApiFlagsConfig
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.utils.associateNotNull

/** Create [ApiFlags] from some source of information about the flags. */
object ApiFlagsCreator {
    /** Create [ApiFlags] from [apiFlagsConfig]. */
    fun createFromConfig(apiFlagsConfig: ApiFlagsConfig?): ApiFlags? {
        return apiFlagsConfig?.createApiFlags()
    }

    /** Create [ApiFlags] from [ApiFlagsConfig]. */
    private fun ApiFlagsConfig.createApiFlags(): ApiFlags {
        val byQualifiedName = flags.associateNotNull { config -> config.createApiFlag() }
        return ApiFlags(byQualifiedName)
    }

    /**
     * Create [Pair] of qualified flag name and [ApiFlag] from [ApiFlagConfig].
     *
     * Returns `null` if [ApiFlagConfig.mutability] is [IMMUTABLE] and [ApiFlagConfig.status] is
     * [DISABLED] as that is the default [ApiFlags.get] returns for flags that cannot be found.
     */
    private fun ApiFlagConfig.createApiFlag(): Pair<String, ApiFlag>? {
        val apiFlag =
            when (mutability) {
                MUTABLE -> ApiFlag.KEEP_FLAGGED_API
                IMMUTABLE ->
                    when (status) {
                        ENABLED -> ApiFlag.FINALIZE_FLAGGED_API
                        DISABLED -> return null
                    }
            }
        val qualifiedName = "$pkg.$name"
        return Pair(qualifiedName, apiFlag)
    }
}
