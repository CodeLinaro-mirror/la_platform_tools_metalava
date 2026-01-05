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

import java.util.regex.Pattern
import kotlin.text.isWhitespace

/**
 * Parse a [String] as if it was a document comment (could be either Javadoc or KDoc).
 *
 * A document comment is of the form:
 * ```
 *     <description>
 *     (@<block-tag-type> <description>)*
 * ```
 */
internal object DocCommentParser {
    /**
     * Pattern to match a block tag or the end of the content.
     *
     * This uses horizontal spaces (`\h`) to avoid matching newline characters as newline characters
     * are significant.
     */
    private val BLOCK_TAG_OR_END_PATTERN =
        Pattern.compile("""(^\h*\*?\h*@([a-zA-Z]+)\h*)|\z""", Pattern.MULTILINE)

    /** The index of the group in [BLOCK_TAG_TYPE_GROUP_INDEX] that contains the block tag type. */
    private const val BLOCK_TAG_TYPE_GROUP_INDEX = 2

    fun parseText(
        context: DocCommentContext,
        text: String,
        reporter: DocumentationIssueReporter,
    ): DocComment {
        val length = text.length

        // Trim any whitespace from the start as well as the start token `/**` (if any).
        val commentBodyStartInclusive = skipDocCommentStartToken(text)

        // The comment does not exist if skipping forwards over whitespace reaches the end of the
        // text. An empty comment (e.g. `/** */`) would stop at the end token.
        if (commentBodyStartInclusive == length) {
            // There was no comment so return an empty DocComment immediately to save time.
            return DefaultDocComment(
                context,
                ContentSupplier.NULL,
                emptyList(),
                noComment = true,
            )
        }

        // Trim the end token (`*/`) as well as any whitespace that precedes or follows it.
        val commentBodyEndExclusive = skipDocCommentEndTokens(text)

        val blockTagSections = mutableListOf<BlockTagSection>()

        // Split the input text into an optional description followed by one or more block tags.
        // This partitions the text using the [BLOCK_TAG_OPEN_BRACE_OR_END] to find each block tag
        // and the end of the text. The description is the partition before the first block tag (or
        // the end of the text if no block tags were found). Each block tag is the partition from
        // the start of the block tag to the start of the next block tag (or the end of the text
        // for the last block tag).

        // The end of the description, if -1 then it means that the description has not yet been
        // found.
        var descriptionEndExclusive = -1

        // The type of the previous block tag, null if there was none. This is set when matching a
        // block tag but used on the next iteration where the block tag is added to the list.
        var blockTagType: TagType<*>? = null

        // The start of the description of the previous block tag, -1 if there was none. This is set
        // when matching a block tag but used on the next iteration where the block tag is added to
        // the list.
        var blockTagDescriptionStartInclusive = -1

        // Create a matcher for finding the start of block tags or the end of the content. Restrict
        // it to the main body of the comment so it does not have to deal with the doc start/end
        // tokens.
        val matcher =
            BLOCK_TAG_OR_END_PATTERN.matcher(text)
                .region(commentBodyStartInclusive, commentBodyEndExclusive)
        while (true) {
            // This will always match as it can just match the end of the input.
            if (!matcher.find()) {
                error("Internal Error: BLOCK_TAG_OR_END_PATTERN must always match")
            }

            val matchStart = matcher.start()

            // If this is the first match then it is the end of the description.
            if (descriptionEndExclusive == -1) {
                descriptionEndExclusive = matchStart
            }

            // End of input or the start of a block tag was found.

            // First, check to see if there was a preceding block tag that needs recording.
            if (blockTagType != null) {
                val blockTagDescriptionEndExclusive = matchStart
                val blockTagDescription =
                    LazyContentSupplier(
                        context,
                        reporter,
                        text,
                        blockTagDescriptionStartInclusive,
                        blockTagDescriptionEndExclusive,
                    )
                val blockTagSection =
                    DefaultBlockTagSection(
                        context,
                        blockTagType,
                        blockTagDescription,
                    )
                blockTagSections.add(blockTagSection)
            }

            if (matchStart == commentBodyEndExclusive) {
                // The end of the content was reached so there are no more partitions to be found.
                break
            } else {
                // A block tag was found so record the block tag type and start of the description
                // for use in the next iteration of the loop.
                val tagTypeName = matcher.group(BLOCK_TAG_TYPE_GROUP_INDEX)!!

                // Map it to a [TagType].
                blockTagType = BlockTagTypes.tagTypeOf(tagTypeName)

                // The start of the block tag description is the end of the match (which excludes
                // any white space after the block tag name).
                blockTagDescriptionStartInclusive = matcher.end()
            }
        }

        // Create the description, from the start of the comment body to the start of the first
        // block tag, if present, or the end of the comment body, otherwise.
        val description =
            LazyContentSupplier(
                context,
                reporter,
                text,
                commentBodyStartInclusive,
                descriptionEndExclusive,
            )

        // Create the doc comment.
        return DefaultDocComment(
            context,
            description,
            blockTagSections.toList(),
            noComment = false,
        )
    }

    /**
     * Skip past start Javadoc tokens from [text] returning the position of the character after the
     * token.
     */
    private fun skipDocCommentStartToken(text: String): Int {
        val length = text.length

        // Skip forwards over any leading whitespaces.
        var start = text.skipForwardsOverLeadingWhitespace(0)

        // Skip forwards over a leading /** token if it is present, otherwise just use the text
        // from this point onwards.
        if (
            start + 2 < length &&
                text[start] == '/' &&
                text[start + 1] == '*' &&
                text[start + 2] == '*'
        ) {
            start += 3
        }

        return start
    }

    /**
     * Skip past end Javadoc tokens from [text] returning the position of the first character of the
     * token.
     */
    private fun skipDocCommentEndTokens(text: String): Int {
        val length = text.length

        // Skip backwards over any trailing whitespace.
        var end = text.skipBackwardsOverTrailingWhitespace(length - 1)

        // Skip backwards over a trailing */ token.
        if (end - 1 > 0 && text[end] == '/' && text[end - 1] == '*') end -= 2

        return end + 1
    }
}

/**
 * Compute the line number offset from the beginning of this for [index].
 *
 * e.g. If [index] is `0` then the line number offset will also be `0` as [index] is on the first
 * line. If [index] was `100` and it was on line number `10` then the line number offset would be
 * `9`.
 */
fun String.lineOffsetFor(index: Int): Int {
    var count = 0
    // Handle the case when index is out of bounds by finding the offset for the final index.
    val target = index.coerceAtMost(length)
    for (i in 0 until target) {
        val c = this[i]
        if (c == '\n') count += 1
    }
    return count
}

/**
 * Compute the character offset from the beginning of the containing line for [index].
 *
 * e.g. If [index] is `0` then the character offset will also be `0` as [index] is the first
 * character on the first line. If [index] was `100` and it was on line number `10` and character
 * position `7` then the character offset would be `6`.
 */
fun String.characterOffsetFor(index: Int): Int {
    var count = 0
    for (i in index - 1 downTo 0) {
        val c = this[i]
        if (c == '\n') break
        count += 1
    }
    return count
}

/**
 * Starting with the character at position [startInclusive] and searching forwards, return the
 * position of the first non-whitespace character.
 */
internal fun CharSequence.skipForwardsOverLeadingWhitespace(startInclusive: Int): Int {
    val length = this.length
    var index = startInclusive
    while (index < length && this[index].isWhitespace()) {
        index += 1
    }
    return index
}

/**
 * Starting with the character at position [endInclusive] and searching backwards, return the
 * position of the first non-whitespace character.
 */
internal fun CharSequence.skipBackwardsOverTrailingWhitespace(endInclusive: Int): Int {
    // Skip backwards over any trailing whitespace.
    var end = endInclusive
    while (end >= 0 && this[end].isWhitespace()) {
        end -= 1
    }
    return end
}
