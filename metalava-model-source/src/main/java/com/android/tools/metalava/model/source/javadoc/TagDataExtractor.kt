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

import com.android.tools.metalava.model.source.doc.DocCommentContext
import com.android.tools.metalava.model.source.doc.DocumentationIssueReporter
import com.android.tools.metalava.model.source.doc.TagData
import com.android.tools.metalava.model.source.doc.TagType
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.LocationSpecificReporter

/**
 * Uses [tagType] to extract [TagData] from [JavadocContent] using [context].
 *
 * Finds the [JavadocText], if any, at the beginning of the [JavadocContent] and calls
 * [TagType.extractData] on its [JavadocText.contents] property.
 *
 * @see [extractTagData].
 */
internal class TagDataExtractor(
    private val context: DocCommentContext,
    private val tagType: TagType<*>,
    private val reporter: DocumentationIssueReporter,
) : JavadocContentRewriter, LocationSpecificReporter {

    private var tagData: TagData? = null

    /** Implement [LocationSpecificReporter.report] to delegate to [reporter]. */
    override fun report(issue: Issues.Issue, message: String) {
        reporter.report(issue, message)
    }

    /**
     * Extract the [TagData], if any, from [JavadocContent].
     *
     * @return the [TagData] or `null` if it could not be found, e.g. because [TagType.extractData]
     *   returned `null` or [JavadocContent] did not start with [JavadocText].
     */
    fun extractTagData(content: JavadocContent): ExtractorResult {
        val remainder = content.accept(this)
        return ExtractorResult(tagData, remainder)
    }

    /** Extract the data from the first item in the [JavadocContentList.contents]. */
    override fun visit(list: JavadocContentList): JavadocContent? {
        // Can only extract data from the start of a list.
        var contents = list.contents
        val first = contents[0]
        val rewritten = first.accept(this)

        return when {
            rewritten === first -> list
            rewritten == null -> {
                when (contents.size) {
                    1 -> null
                    2 -> contents[1]
                    else -> JavadocContentList(contents.drop(1))
                }
            }
            else -> {
                when (contents.size) {
                    1 -> rewritten
                    else ->
                        JavadocContentList(
                            buildList {
                                add(rewritten)
                                for (i in 1 until contents.size) {
                                    add(contents[i])
                                }
                            }
                        )
                }
            }
        }
    }

    /** A [JavadocInlineTag] cannot have data extracted so do nothing. */
    override fun visit(inlineTag: JavadocInlineTag): JavadocContent? {
        // Nothing to do as cannot extract data from an inline tag.
        return inlineTag
    }

    /**
     * Calls [TagType.extractData] on [JavadocText.contents].
     *
     * This will only be called for a [JavadocText] that is at the start of the [JavadocContent]
     * passed into [extractTagData].
     */
    override fun visit(text: JavadocText): JavadocContent? {
        val contents = text.contents
        var result = tagType.extractData(context, reporter = this, contents)
        tagData = result?.tagData

        val consumedContent = result?.consumedContent ?: 0
        return if (consumedContent == 0) {
            text
        } else {
            contents.substring(consumedContent).toOptionalJavadocContent()
        }
    }
}

/** Extract [tagType]'s [TagData], if any, from this using [TagType.extractData]. */
internal fun JavadocContent.extractTagDataForTagType(
    context: DocCommentContext,
    tagType: TagType<*>,
    reporter: DocumentationIssueReporter,
) = TagDataExtractor(context, tagType, reporter).extractTagData(this)

/** The result of [TagDataExtractor.extractTagData]. */
internal data class ExtractorResult(
    /** The optional extracted [TagData]. */
    val tagData: TagData?,

    /**
     * The remainder of the [JavadocContent] passed to [TagDataExtractor.extractTagData] that was
     * not consumed by [tagData].
     */
    val remainder: JavadocContent?,
)
