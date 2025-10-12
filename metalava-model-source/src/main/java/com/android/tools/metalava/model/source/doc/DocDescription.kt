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
import com.android.tools.metalava.model.source.javadoc.JavadocContentList
import com.android.tools.metalava.model.source.javadoc.JavadocContentVisitor
import com.android.tools.metalava.model.source.javadoc.JavadocInlineTag
import com.android.tools.metalava.model.source.javadoc.JavadocParser
import com.android.tools.metalava.model.source.javadoc.JavadocText
import java.io.PrintWriter

/**
 * A [DocComment] description block.
 *
 * This represents a block of text and inline tags in a [DocComment]. It can either be the main
 * description for the item or the description of a block tag in the item.
 */
interface DocDescription {
    /** Return `true` if this is empty, `false` otherwise. */
    fun isEmpty(): Boolean

    /** Return `true` if this is not empty, `false` otherwise. */
    fun isNotEmpty() = !isEmpty()

    fun requiredSpace(): RequiredSpace

    /** Print this as part of a Javadoc comment to [writer]. */
    fun printAsJavadocComment(writer: PrintWriter)

    companion object {
        /** An empty [DocDescription]. */
        val EMPTY: DocDescription = EmptyDocDescription()
    }
}

internal class EmptyDocDescription : DocDescription {
    override fun isEmpty() = true

    override fun requiredSpace() = RequiredSpace.EMPTY

    override fun printAsJavadocComment(writer: PrintWriter) {
        // Nothing to do.
    }

    override fun toString() = "<<>>"
}

/**
 * The default [DocDescription] whose content is a subsequence of [text] starting from
 * [startInclusive] and ending at [endExclusive].
 */
internal class DefaultDocDescription(
    private val text: String,
    private val startInclusive: Int,
    private val endExclusive: Int,
    private val reporter: DocumentationIssueReporter,
) : DocDescription {

    private lateinit var _content: JavadocContent

    val content: JavadocContent
        get() {
            if (!::_content.isInitialized) {
                _content =
                    JavadocParser.parse(
                        text,
                        startInclusive,
                        endExclusive,
                        reporter,
                    )
            }
            return _content
        }

    override fun isEmpty() = content == JavadocContent.EMPTY

    override fun requiredSpace() =
        when {
            isEmpty() -> RequiredSpace.EMPTY
            content.isMultiLine() -> RequiredSpace.MULTI_LINE
            else -> RequiredSpace.SINGLE_LINE
        }

    override fun printAsJavadocComment(writer: PrintWriter) {
        content.accept(
            object : JavadocContentVisitor {
                override fun visit(list: JavadocContentList) {
                    list.visitContents(this)
                }

                override fun visit(inlineTag: JavadocInlineTag) {
                    writer.print("{@")
                    writer.print(inlineTag.tagType)
                    inlineTag.content?.let { nestedContent ->
                        if (!nestedContent.startsWithNewline()) {
                            writer.print(" ")
                        }
                        nestedContent.accept(this)
                    }
                    writer.print("}")
                }

                override fun visit(text: JavadocText) {
                    var previousChar = '\u0000'
                    for (c in text.text) {
                        if (previousChar == '\n' && c != '/') {
                            writer.print(" *")
                        }
                        writer.print(c)
                        previousChar = c
                    }

                    if (previousChar == '\n') {
                        writer.print(" *")
                    }
                }
            }
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
}
