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

package com.android.tools.metalava.model.visitors

import com.android.tools.metalava.model.EmittedOnlyPredicate
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Indenter
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MatchAllPredicate
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.inclusionFilter

/** Encapsulates filters needed by [ApiVisitor]. */
data class ApiFilters(
    /**
     * Returns `true` for [Item]s that can be referenced from the API, this is a super set of
     * [Item]s that can be emitted.
     */
    val reference: FilterPredicate,

    /** Returns `true` for [Item]s that should be defined in the API and emitted as part of it. */
    val emit: FilterPredicate = EmittedOnlyPredicate.and(reference),

    /**
     * Optional filter that determines whether an [Item] should be visited during traversal.
     *
     * If provided, [ApiFiltersVisitor] skips any [SelectableItem] for which this predicate returns
     * `false`. Otherwise, [ApiFiltersVisitor] uses [emit] for that purpose.
     */
    val traversal: FilterPredicate? = null,
) {
    /**
     * Return an [ApiFilters] that will filter by [targetLanguages] in addition to this filter.
     *
     * If [targetLanguages] is [TargetLanguageSet.ALL], this returns `this`.
     */
    fun forTargetLanguages(targetLanguages: Set<TargetLanguage>): ApiFilters {
        val targetLanguagesInclusionFilter = targetLanguages.inclusionFilter() ?: return this
        return ApiFilters(
            reference = reference.and(targetLanguagesInclusionFilter),
            emit = emit.and(targetLanguagesInclusionFilter),
            traversal = traversal?.and(targetLanguagesInclusionFilter),
        )
    }

    override fun toString() = buildString {
        val indenter = Indenter(this)
        indenter.indented(prefix = "ApiFilters(", suffix = ")") {
            if (traversal != null) {
                indenter.indented(prefix = "traversal =", suffix = "\n") {
                    traversal.format(indenter)
                }
            }
            indenter.indented(prefix = "emit =", suffix = "\n") { emit.format(indenter) }
            indenter.indented(prefix = "reference =", suffix = "\n") { reference.format(indenter) }
        }
    }

    companion object {
        /**
         * Emits all [SelectableItem]s whose [SelectableItem.emit] is `true` and references any
         * [SelectableItem].
         */
        val ALL =
            ApiFilters(
                reference = MatchAllPredicate,
                emit = EmittedOnlyPredicate,
            )
    }
}
