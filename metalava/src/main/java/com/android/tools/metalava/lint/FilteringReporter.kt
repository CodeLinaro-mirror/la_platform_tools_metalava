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

package com.android.tools.metalava.lint

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reportable
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.Severity

/** [Reporter] that filters out items that are not relevant for the current API surface. */
class FilteringReporter(
    private val delegateReporter: Reporter,
    private val oldCodebase: Codebase?,
    private val filterEmit: FilterPredicate
) : Reporter by delegateReporter {
    override fun report(
        id: Issues.Issue,
        reportable: Reportable?,
        message: String,
        location: FileLocation,
        maximumSeverity: Severity,
    ): Boolean {
        var actualMaximumSeverity = maximumSeverity

        val item = reportable as? Item
        if (item != null) {
            val previousItem = Codebase.findPreviouslyReleased(oldCodebase, item)

            val computedMaximumSeverity = computeMaximumSeverity(item, previousItem, id)
            if (computedMaximumSeverity == Severity.HIDDEN) {
                return false
            }

            actualMaximumSeverity = minOf(actualMaximumSeverity, computedMaximumSeverity)

            if (item.effectivelyDeprecated && previousItem?.effectivelyDeprecated != false) {
                return false
            }

            val testItem =
                when (item) {
                    is ParameterItem -> item.containingCallable()
                    is SelectableItem -> item
                    else -> error("Unknown item $item")
                }

            if (!filterEmit.test(testItem)) {
                return false
            }
        }

        return delegateReporter.report(id, reportable, message, location, actualMaximumSeverity)
    }

    private var contextItem: Item? = null
    private var maximumSeverityForItem: Severity = Severity.UNLIMITED
    private var maximumSeverityForItemContents: Severity = Severity.UNLIMITED

    private fun computeMaximumSeverity(item: Item?, previousItem: Item?, issue: Issues.Issue) =
        when {
            issue == Issues.UNFLAGGED_API -> Severity.ERROR
            item === contextItem -> maximumSeverityForItem
            maximumSeverityForItem == Severity.HIDDEN && previousItem == null ->
                Severity.WARNING_ERROR_WHEN_NEW
            else -> maximumSeverityForItemContents
        }

    internal fun withContext(contextItem: Item, checker: () -> Unit) {
        val oldContextItem = this.contextItem
        val oldMaximumSeverityForItem = this.maximumSeverityForItem
        val oldMaximumSeverityForItemContents = this.maximumSeverityForItemContents
        try {
            this.contextItem = contextItem
            val previouslyReleased =
                oldCodebase != null && Codebase.wasPreviouslyReleased(oldCodebase, contextItem)
            this.maximumSeverityForItem =
                if (previouslyReleased) Severity.HIDDEN else Severity.UNLIMITED
            this.maximumSeverityForItemContents = maximumSeverityForItem

            checker()
        } finally {
            this.contextItem = oldContextItem
            this.maximumSeverityForItem = oldMaximumSeverityForItem
            this.maximumSeverityForItemContents = oldMaximumSeverityForItemContents
        }
    }
}
