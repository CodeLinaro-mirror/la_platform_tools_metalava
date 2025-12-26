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

package com.android.tools.metalava.model.source.doc

import com.android.tools.metalava.reporter.Issues

/** [DocumentationIssueReporter] that reports issues for a fragment of a larger block of content. */
internal abstract class DocumentationFragmentIssueReporter(
    /**
     * The [DocumentationIssueReporter] that reports issues for the larger block of content
     * containing the fragment on which this reports.
     */
    private val container: DocumentationIssueReporter
) : DocumentationIssueReporter {
    /** Get the line offset of the fragment within the content of the [container]. */
    protected abstract val lineOffsetFromContainer: Int

    /**
     * Get the character offset of the first line of the fragment within the content of the
     * [container].
     */
    protected abstract val firstLineCharacterOffset: Int

    /**
     * Reports an issue in the fragment so that it appears in the correct position in the
     * [container].
     *
     * This takes [lineOffset] and [charOffset], which are relative to the fragment, and applies a
     * correction based on [lineOffsetFromContainer] and [firstLineCharacterOffset] before
     * forwarding to [container].
     */
    override fun report(issue: Issues.Issue, message: String, lineOffset: Int, charOffset: Int) {
        val lineOffsetCorrection = lineOffsetFromContainer

        // If this issue is being reported on the first line then make sure to compensate for any
        // possible indentation of that first line from the start of the line in the container.
        val charOffsetCorrection = if (lineOffset == 0) firstLineCharacterOffset else 0

        container.report(
            issue,
            message,
            lineOffset + lineOffsetCorrection,
            charOffset + charOffsetCorrection
        )
    }
}
