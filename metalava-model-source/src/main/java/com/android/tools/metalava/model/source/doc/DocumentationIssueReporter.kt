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

/**
 * Reports issues found within the documentation comment associated with this.
 *
 * Implementations of this have an associated documentation comment for which they will report
 * issues.
 */
internal interface DocumentationIssueReporter {
    /**
     * Report [issue] with [message] at [lineOffset] from the beginning of the associated comment.
     *
     * The [lineOffset] is the 0-based index of the line within which the issue occurs.
     */
    fun report(issue: Issues.Issue, message: String, lineOffset: Int)
}
