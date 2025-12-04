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
 * A [DocComment] description block.
 *
 * This represents a block of text and inline tags in a [DocComment]. It can either be the main
 * description for the item or the description of a block tag in the item.
 */
internal interface DocDescription {
    /** The [JavadocContent], `null` if this is empty. */
    val content: JavadocContent?

    /** Return `true` if this is empty, `false` otherwise. */
    fun isEmpty(): Boolean = content == null

    /** Return `true` if this is not empty, `false` otherwise. */
    fun isNotEmpty() = !isEmpty()

    /** Determines how much vertical space this [DocDescription] requires when printed. */
    fun requiredSpace(): RequiredSpace =
        when {
            isEmpty() -> RequiredSpace.EMPTY
            content?.isMultiLine() == true -> RequiredSpace.MULTI_LINE
            else -> RequiredSpace.SINGLE_LINE
        }

    companion object {
        /** An empty [DocDescription]. */
        val EMPTY: DocDescription = EmptyDocDescription()
    }
}

internal class EmptyDocDescription : DocDescription {
    override val content
        get() = null

    override fun isEmpty() = true

    override fun requiredSpace() = RequiredSpace.EMPTY

    override fun toString() = "<<>>"
}

/** A simple [DocDescription] that encapsulates [content]. */
internal class DefaultDocDescription(override val content: JavadocContent) : DocDescription {
    override fun toString(): String {
        return "<$content>"
    }
}

/**
 * A lazy [DocDescription] that creates [content] lazily by parsing a subsequence of [text] starting
 * from [startInclusive] and ending at [endExclusive].
 */
internal class LazyDocDescription(
    private val text: String,
    private val startInclusive: Int,
    private val endExclusive: Int,
    private val reporter: DocumentationIssueReporter,
) : DocDescription, DocumentationIssueReporter {
    /** Secondary constructor to simple creation during testing. */
    constructor(
        text: String,
        reporter: DocumentationIssueReporter
    ) : this(text, 0, text.length, reporter)

    private lateinit var _content: Optional<JavadocContent>

    override val content: JavadocContent?
        get() {
            if (!::_content.isInitialized) {
                // Trim whitespace from the end of the description.
                val trimmedEnd = text.skipBackwardsOverTrailingWhitespace(endExclusive - 1) + 1
                val optionalContent =
                    if (trimmedEnd <= startInclusive) {
                        null
                    } else {
                        JavadocParser.parse(
                            text,
                            startInclusive,
                            trimmedEnd,
                            // Pass this as the reporter so that this can apply corrections to the
                            // line and char offset based on the [startInclusive] position within
                            // [text].
                            this,
                        )
                    }
                _content = Optional.ofNullable(optionalContent)
            }
            return _content.getOrNull()
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
