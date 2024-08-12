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

package com.android.tools.metalava

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.source.EnvironmentManager
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Issues.Category
import com.android.tools.metalava.reporter.Reportable
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.Severity

/**
 * A special [Reporter] that is passed to [EnvironmentManager.createSourceParser] which will in turn
 * be passed through and be accessible as [Codebase.reporter].
 *
 * This will redirect the reports based on the [Category] to an appropriate [Reporter]. This is
 * needed because a [Reporter] will produce a custom error message if any errors were reported
 * through it and reporting an inappropriate issues could cause confusion. e.g. reporting an
 * unresolved import through an API Lint [Reporter] would suggest that it be resolved by using
 * `@SuppressWarnings` but that would not work.
 *
 * Issues in the [Category.COMPATIBILITY] should only be reported from the specific compatibility
 * check not within the [Codebase] so attempting to report one of them will result in an error.
 */
class CategoryRedirectingReporter(
    /** Destination for [Issues.Issue] in [Category.UNKNOWN]. */
    private val defaultReporter: Reporter,
    /** Destination for [Issues.Issue] in [Category.API_LINT] and [Category.DOCUMENTATION]. */
    private val apiLintReporter: Reporter = defaultReporter,
    /** Destination for [Issues.Issue] in [Category.COMPATIBILITY]. */
    private val compatibilityReporter: Reporter = defaultReporter,
) : Reporter {

    override fun report(
        id: Issues.Issue,
        reportable: Reportable?,
        message: String,
        location: FileLocation,
        maximumSeverity: Severity
    ) =
        when (id.category) {
            Category.API_LINT,
            Category.DOCUMENTATION ->
                apiLintReporter.report(id, reportable, message, location, maximumSeverity)
            Category.COMPATIBILITY ->
                compatibilityReporter.report(id, reportable, message, location, maximumSeverity)
            else -> defaultReporter.report(id, reportable, message, location, maximumSeverity)
        }

    override fun isSuppressed(id: Issues.Issue, reportable: Reportable?, message: String?) =
        // It does not matter which reporter this is delegated to as it only depends on the
        // reportable and the issue configuration which is identical for both.
        defaultReporter.isSuppressed(id, reportable, message)
}
