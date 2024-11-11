/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.visitors.ApiFilters

/** Types of APIs emitted (or parsed etc.) */
enum class ApiType(val flagName: String, val displayName: String = flagName) {
    /** The public API */
    PUBLIC_API("api", "public") {

        override fun getNonElidingFilter(apiPredicateConfig: ApiPredicate.Config): FilterPredicate {
            // This filter is for API signature files, where we don't need the "for stub purposes"
            // APIs.
            return ApiPredicate(
                includeApisForStubPurposes = false,
                config = apiPredicateConfig,
            )
        }

        override fun getReferenceFilter(apiPredicateConfig: ApiPredicate.Config): FilterPredicate {
            return ApiPredicate(config = apiPredicateConfig.copy(ignoreShown = true))
        }
    },

    /** The API that has been removed */
    REMOVED("removed", "removed") {

        override fun getNonElidingFilter(apiPredicateConfig: ApiPredicate.Config): FilterPredicate {
            // This filter is for API signature files, where we don't need the "for stub purposes"
            // APIs.
            return ApiPredicate(
                includeApisForStubPurposes = false,
                matchRemoved = true,
                config = apiPredicateConfig,
            )
        }

        override fun getReferenceFilter(apiPredicateConfig: ApiPredicate.Config): FilterPredicate {
            return ApiPredicate(
                ignoreRemoved = true,
                config = apiPredicateConfig.copy(ignoreShown = true),
            )
        }
    },

    /** Everything */
    ALL("all", "all") {

        override fun getNonElidingFilter(apiPredicateConfig: ApiPredicate.Config): FilterPredicate {
            return FilterPredicate { it.emit }
        }

        override fun getEmitFilter(apiPredicateConfig: ApiPredicate.Config): FilterPredicate {
            return FilterPredicate { it.emit }
        }

        override fun getReferenceFilter(apiPredicateConfig: ApiPredicate.Config): FilterPredicate {
            return FilterPredicate { true }
        }
    };

    protected abstract fun getNonElidingFilter(
        apiPredicateConfig: ApiPredicate.Config
    ): FilterPredicate

    open fun getEmitFilter(apiPredicateConfig: ApiPredicate.Config): FilterPredicate {
        val nonElidingFilter =
            MatchOverridingMethodPredicate(getNonElidingFilter(apiPredicateConfig))
        val referenceFilter = getReferenceFilter(apiPredicateConfig)
        return nonElidingFilter.and(elidingPredicate(referenceFilter, apiPredicateConfig))
    }

    abstract fun getReferenceFilter(apiPredicateConfig: ApiPredicate.Config): FilterPredicate

    /**
     * Create an [ElidingPredicate] that wraps [wrappedPredicate] and uses information from the
     * [apiPredicateConfig].
     */
    protected fun elidingPredicate(
        wrappedPredicate: FilterPredicate,
        apiPredicateConfig: ApiPredicate.Config
    ) =
        ElidingPredicate(
            wrappedPredicate,
            addAdditionalOverrides = apiPredicateConfig.addAdditionalOverrides,
        )

    /**
     * Get the [ApiFilters] for this [ApiType] that uses information from [apiPredicateConfig] to
     * customize their behavior.
     *
     * The returned [ApiFilters.emit] will elide methods overrides that match the overridden method.
     */
    fun getApiFilters(apiPredicateConfig: ApiPredicate.Config) =
        ApiFilters(
            emit = getEmitFilter(apiPredicateConfig),
            reference = getReferenceFilter(apiPredicateConfig),
        )

    /**
     * Get the [ApiFilters] for this [ApiType] that uses information from [apiPredicateConfig] to
     * customize their behavior.
     *
     * The returned [ApiFilters.emit] will NOT elide methods overrides that match the overridden
     * method.
     */
    fun getNonElidingApiFilters(apiPredicateConfig: ApiPredicate.Config) =
        ApiFilters(
            emit = getNonElidingFilter(apiPredicateConfig),
            reference = getReferenceFilter(apiPredicateConfig),
        )

    override fun toString(): String = displayName
}
