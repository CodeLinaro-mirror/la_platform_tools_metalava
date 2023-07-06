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

import com.android.tools.metalava.model.Item
import java.util.function.Predicate

/** Types of APIs emitted (or parsed etc.) */
enum class ApiType(val flagName: String, val displayName: String = flagName) {
    /** The public API */
    PUBLIC_API("api", "public") {

        override fun getEmitFilter(): Predicate<Item> {
            // This filter is for API signature files, where we don't need the "for stub purposes"
            // APIs.
            val apiFilter = FilterPredicate(ApiPredicate(includeApisForStubPurposes = false))
            val apiReference = ApiPredicate(ignoreShown = true)
            return apiFilter.and(ElidingPredicate(apiReference))
        }

        override fun getReferenceFilter(): Predicate<Item> {
            return ApiPredicate(ignoreShown = true)
        }
    },

    /** The API that has been removed */
    REMOVED("removed", "removed") {

        override fun getEmitFilter(): Predicate<Item> {
            // This filter is for API signature files, where we don't need the "for stub purposes"
            // APIs.
            val removedFilter =
                FilterPredicate(
                    ApiPredicate(includeApisForStubPurposes = false, matchRemoved = true)
                )
            val removedReference = ApiPredicate(ignoreShown = true, ignoreRemoved = true)
            return removedFilter.and(ElidingPredicate(removedReference))
        }

        override fun getReferenceFilter(): Predicate<Item> {
            return ApiPredicate(ignoreShown = true, ignoreRemoved = true)
        }
    },

    /** Everything */
    ALL("all", "all") {

        override fun getEmitFilter(): Predicate<Item> {
            return Predicate { it.emit }
        }

        override fun getReferenceFilter(): Predicate<Item> {
            return Predicate { true }
        }
    };

    abstract fun getEmitFilter(): Predicate<Item>

    abstract fun getReferenceFilter(): Predicate<Item>

    override fun toString(): String = displayName
}
