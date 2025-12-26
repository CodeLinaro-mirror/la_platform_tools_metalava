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

import com.android.tools.metalava.reporter.Issues.Issue

/**
 * Reports issues found within the documentation comment or comment fragment associated with this.
 *
 * This is intended to be used to report issues within a fragment of a source file, e.g. in a
 * comment, or fragment of a comment. The reporter will not know the location of that fragment
 * within the source file and so it is the responsibility of the implementation to apply some
 * corrections.
 *
 * e.g. if the source file contains something like this:
 * ```
 * package test.pkg;
 *
 * public class Test {
 *     /** Comment with some {@code text with an unclosed inline tag */
 *     public void method1() {}
 * }
 * ```
 *
 * Then when parsing the main description for `method1()` the parser will be passed the [String]:
 * ```
 *     " Single line comment with some {@code text with an unclosed inline tag"
 * ```
 *
 * If an error was reported then it would be reported with a `lineOffset = 0` and `charOffset = 31`
 * but those are not the position of the error in the whole file. So, the parser needs to be passed
 * a [DocumentationIssueReporter] that will apply a correction to them by adding `3` to `lineOffset`
 * and `7` to `charOffset`.
 *
 * [DocumentationIssueReporter]s will be arranged in a chain where anything that selects a subset or
 * the contents it is given needs to provide a [DocumentationIssueReporter] wrapper around the
 * [DocumentationIssueReporter] it is provided to correct the offsets to be relative to the whole
 * contents.
 *
 * The implementation of [DocumentationIssueReporter] at the beginning of the chain must map from
 * the 0-based `lineOffset/charOffset` to the 1-based line and character position used in Metalava
 * messages.
 */
internal interface DocumentationIssueReporter {
    /**
     * Report [issue] with [message] at [lineOffset] from the beginning of the associated comment or
     * comment fragment.
     *
     * @param issue the [Issue] to report.
     * @param [message] the message to report.
     * @param lineOffset is the 0-based index of the line within the comment or comment fragment
     *   where the issue occurred. This is intended to be added to the line number of the comment or
     *   comment fragment within the source file to give th line number within the source file where
     *   the issues occurred.
     * @param charOffset is the 0-based index of the character within the line where the issue
     *   occurred. If this is `-1` then no character position is reported.
     */
    fun report(issue: Issue, message: String, lineOffset: Int = 0, charOffset: Int = 0)

    companion object {
        /**
         * A special [DocumentationIssueReporter] that will immediately throw an error for the first
         * issue reported.
         */
        val THROWING =
            object : DocumentationIssueReporter {
                override fun report(
                    issue: Issue,
                    message: String,
                    lineOffset: Int,
                    charOffset: Int
                ) {
                    error("${lineOffset + 1}:${charOffset + 1}: $message [${issue.name}]")
                }
            }

        /** A special [DocumentationIssueReporter] that will ignore all the issues. */
        val NULL =
            object : DocumentationIssueReporter {
                override fun report(
                    issue: Issue,
                    message: String,
                    lineOffset: Int,
                    charOffset: Int
                ) {}
            }
    }
}
