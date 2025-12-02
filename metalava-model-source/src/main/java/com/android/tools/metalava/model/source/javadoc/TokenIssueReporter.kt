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

package com.android.tools.metalava.model.source.javadoc

import com.android.tools.metalava.model.source.doc.DocumentationFragmentIssueReporter
import com.android.tools.metalava.model.source.doc.DocumentationIssueReporter
import com.android.tools.metalava.reporter.Issues.Issue
import org.antlr.v4.runtime.Token

/** A [DocumentationIssueReporter] that reports issues for a [Token]. */
internal class TokenIssueReporter(reporter: DocumentationIssueReporter) :
    DocumentationFragmentIssueReporter(reporter) {
    /** The [Token] on which the issues will be reported. */
    internal var token: Token? = null

    /** The line offset of [token] from the beginning of the content parsed by [JavadocParser]. */
    override val lineOffsetFromContainer: Int
        get() =
            // The token's `line` property is 1-based but this is 0-based so convert the former
            // to the latter.
            token!!.line - 1

    /** The character offset of [token] from the beginning of the line containing it. */
    override val firstLineCharacterOffset: Int
        get() =
            // The token's `charPositionInLine` is already 0-based like this.
            token!!.charPositionInLine

    /** Treat any issues reported by [body] as if they were reported on [token]. */
    inline fun <R> reportAtToken(token: Token, body: () -> R): R {
        val oldToken = token
        this.token = token
        try {
            return body()
        } finally {
            this.token = oldToken
        }
    }

    /** Report [issue] with message for [token]. */
    fun report(token: Token, issue: Issue, message: String) {
        reportAtToken(token) { report(issue, message) }
    }
}
