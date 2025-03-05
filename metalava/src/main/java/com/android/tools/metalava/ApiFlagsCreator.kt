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
import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.utils.associateNotNull

/** Create [ApiFlags] from some source of information about the flags. */
object ApiFlagsCreator {
    /**
     * Create [ApiFlags] from either [revertAnnotations] or [apiFlagsConfig].
     *
     * Throws an exception if [revertAnnotations] is not empty and [apiFlagsConfig] is not null as
     * they are mutually exclusive.
     */
    fun create(revertAnnotations: List<String>, apiFlagsConfig: ApiFlagsConfig?): ApiFlags? {
        // Consistency check to make sure that tests do not provide both --revert-annotation
        // and ApiFlags.
        if (revertAnnotations.isNotEmpty() && apiFlagsConfig != null)
            error("Cannot provide non-empty revertAnnotations and non-null apiFlags")

        return apiFlagsConfig?.createApiFlags() ?: createFromRevertAnnotations(revertAnnotations)
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

    /**
     * [Regex] that matches the value of a `--revert-annotation` value that keeps the API associated
     * with a flag, e.g. `!android.annotation.FlaggedApi("<fully-qualified-flag-name>")`.
     */
    private val REVERT_ANNOTATION_KEEP_FLAG_REGEX = Regex("""!$ANDROID_FLAGGED_API\("([^"]+)"\)""")

    /**
     * Create [ApiFlags] from [revertAnnotations] which is a list of values passed to the
     * `--revert-annotation` option.
     *
     * If [revertAnnotations] is empty then this returns `null`. That has the effect of keeping all
     * `@FlaggedApi` annotated [Item]s and their associated `F@laggedApi` annotations.
     *
     * Otherwise, [revertAnnotations] is expected to contain one of:
     * 1. `android.annotation.FlaggedApi` (without any attributes), which indicates that all
     *    `@FlaggedApi` annotated [Item]s should be reverted, unless overridden by one of the
     *    following.
     * 2. `!android.annotation.FlaggedApi("<fully-qualified-flag-name>")`, which indicates that any
     *    [Item]s associated with flag `<fully-qualified-flag-name>` should be finalized, i.e. the
     *    [Item] must be kept, but the `@FlaggedApi` annotation must be dropped.
     */
    private fun createFromRevertAnnotations(revertAnnotations: List<String>): ApiFlags? {
        if (revertAnnotations.isEmpty()) return null
        val byQualifiedName = buildMap {
            for (revertAnnotation in revertAnnotations) {
                // The `--revert-annotation android.annotation.FlaggedApi` can just be ignored as an
                // empty ApiFlags will revert all `@FlaggedApi` annotated items.
                if (revertAnnotation == ANDROID_FLAGGED_API) {
                    continue
                }

                // All other `--revert-annotation` options must match the pattern.
                val result =
                    REVERT_ANNOTATION_KEEP_FLAG_REGEX.matchEntire(revertAnnotation)
                        ?: error("Unexpected $ARG_REVERT_ANNOTATION: $revertAnnotation")

                // If the flag name is listed then the API is expected to be finalized.
                val flagName = result.groups[1]!!.value
                put(flagName, ApiFlag.FINALIZE_FLAGGED_API)
            }
        }
        return ApiFlags(byQualifiedName)
    }
}
