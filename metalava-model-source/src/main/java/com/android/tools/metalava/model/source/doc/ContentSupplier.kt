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

import com.android.tools.metalava.model.source.javadoc.JavadocContent
import com.android.tools.metalava.model.source.javadoc.JavadocParser
import com.android.tools.metalava.reporter.Issues
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/**
 * Supplies a [JavadocContent] description block.
 *
 * This represents a block of text and inline tags in a [DocComment]. It can either be the main
 * description for the item or the description of a block tag in the item.
 */
internal interface ContentSupplier {
    /** The [JavadocContent], `null` if this is empty. */
    val content: JavadocContent?

    companion object {
        /** A null [ContentSupplier]. */
        val NULL: ContentSupplier = DefaultContentSupplier(null)
    }
}

/** A simple [ContentSupplier] that encapsulates [content]. */
private class DefaultContentSupplier(override val content: JavadocContent?) : ContentSupplier {
    override fun toString(): String {
        return "<<${content?: ""}>>"
    }
}

/** Wrap [this] optional [JavadocContent] in a [ContentSupplier]. */
internal fun JavadocContent?.toSupplier(): ContentSupplier =
    this?.let { DefaultContentSupplier(it) } ?: ContentSupplier.NULL

/**
 * A lazy [ContentSupplier] that creates [content] lazily by parsing a subsequence of [text]
 * starting from [startInclusive] and ending at [endExclusive].
 */
internal class LazyContentSupplier(
    private val context: DocCommentContext,
    private val reporter: DocumentationIssueReporter,
    private val text: String,
    private val startInclusive: Int = 0,
    private val endExclusive: Int = text.length,
) : ContentSupplier, DocumentationIssueReporter {
    private lateinit var _content: Optional<JavadocContent>

    override val content: JavadocContent?
        get() {
            if (!::_content.isInitialized) {
                val optionalContent = parseAsJavadocContent()
                _content = Optional.ofNullable(optionalContent)
            }
            return _content.getOrNull()
        }

    /**
     * Parse [text] from [startInclusive] to [endExclusive] producing a [JavadocContent], if
     * possible.
     */
    private fun parseAsJavadocContent(): JavadocContent? {
        // Trim whitespace from the end of the description.
        val trimmedEnd = text.skipBackwardsOverTrailingWhitespace(endExclusive - 1) + 1

        // It was all whitespace so there is no content.
        if (trimmedEnd <= startInclusive) return null

        // Parse the text.
        return JavadocParser.parse(
            context,
            text,
            startInclusive,
            trimmedEnd,
            // Pass this as the reporter so that this can apply corrections to the
            // line and char offset based on the [startInclusive] position within
            // [text].
            this,
        )
    }

    override fun toString() = buildString {
        append("<<")
        // Ignore any whitespace at the end of the description.
        val end = text.skipBackwardsOverTrailingWhitespace(endExclusive - 1) + 1
        for (i in startInclusive until end) {
            val c = text[i]
            if (c == '\n') append("\\n") else append(c)
        }
        append(">>")
    }

    /**
     * Provide an implementation of [DocumentationIssueReporter] that corrects the line and char
     * offsets based on the [startInclusive] position within [text].
     */
    override fun report(issue: Issues.Issue, message: String, lineOffset: Int, charOffset: Int) {
        val lineOffsetCorrection = text.lineOffsetFor(startInclusive)

        // If this issue is being reported on the first line then make sure to compensate for any
        // possible indentation of that first line before [startInclusive].
        val charOffsetCorrection =
            if (lineOffset == 0) text.characterOffsetFor(startInclusive) else 0

        reporter.report(
            issue,
            message,
            lineOffset + lineOffsetCorrection,
            charOffset + charOffsetCorrection
        )
    }
}
