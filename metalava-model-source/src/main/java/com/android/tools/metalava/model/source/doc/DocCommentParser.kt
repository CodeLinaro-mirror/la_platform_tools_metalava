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

import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.reporter.Issues
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
internal class DocCommentParser
private constructor(
    /** Context for resolving references and evaluating conditions within the comment. */
    private val context: DocCommentContext,

    /** Full source text containing the documentation comment. */
    private val text: String,

    /** Coarse token stream produced by [DocCommentLexer] for the comment body. */
    private val tokenStream: TokenStream,

    /** Reporter for documentation issues found while parsing block tags and descriptions. */
    private val reporter: DocumentationIssueReporter,
) {
    /** Parse [tokenStream] into a [DocComment]. */
    private fun parse(): DocComment {
        val description = consumeDescriptionSupplier()

        val blockTagSections = mutableListOf<BlockTagSection>()
        while (tokenStream.peekType() != DocCommentTokenType.EOF) {
            parseBlockTagSection(blockTagSections)
        }

        return DefaultDocComment(
            context,
            description,
            blockTagSections.toList(),
            noComment = false,
        )
    }

    /** Parse a single `@<tag>` block tag section and append it to [blockTagSections]. */
    private fun parseBlockTagSection(blockTagSections: MutableList<BlockTagSection>) {
        // Skip BLOCK_TAG_START; DocCommentLexer always pairs it with an immediately following
        // BLOCK_TAG_NAME token.
        tokenStream.consume()

        val nameToken = tokenStream.consume()
        val tagTypeName = nameToken.text(text)
        val blockTagType = resolveBlockTagType(tagTypeName, nameToken.startOffset)
        val blockTagDescription = consumeDescriptionSupplier()
        addBlockTagSection(
            blockTagSections,
            blockTagType,
            blockTagDescription,
        )
    }

    /**
     * Resolve [tagTypeName] at [position] to its [TagType], reporting any block tag form or
     * deprecation issues to [reporter].
     */
    private fun resolveBlockTagType(
        tagTypeName: String,
        position: Int,
    ): TagType<*> {
        val blockTagType = TagTypes.tagTypeOf(tagTypeName)
        if (!blockTagType.form.supportsBlockTag) {
            reporter.report(
                Issues.INVALID_TAG_FORM,
                "Cannot use '$tagTypeName' as a block tag",
                position,
            )
        }

        blockTagType.errorProvider?.let { errorProvider ->
            errorProvider(tagTypeName).let { error ->
                reporter.report(
                    error.issue,
                    error.message,
                    position,
                )
            }
        }

        return blockTagType
    }

    /** Create a [DefaultBlockTagSection] and append it to [blockTagSections]. */
    private fun addBlockTagSection(
        blockTagSections: MutableList<BlockTagSection>,
        blockTagType: TagType<*>,
        description: ContentSupplier,
    ) {
        blockTagSections.add(
            DefaultBlockTagSection(
                context,
                blockTagType,
                description,
            ),
        )
    }

    /**
     * If the next token is [DocCommentTokenType.DESCRIPTION], consumes it and wraps its slice in a
     * [LazyContentSupplier]; otherwise returns [ContentSupplier.NULL].
     */
    private fun consumeDescriptionSupplier(): ContentSupplier {
        if (tokenStream.peekType() != DocCommentTokenType.DESCRIPTION) {
            return ContentSupplier.NULL
        }
        val token = tokenStream.consume()
        return createDescriptionSupplier(
            token.startOffset,
            token.endOffset,
        )
    }

    /** Wrap `[startInclusive, endExclusive)` of [text] in a [LazyContentSupplier]. */
    private fun createDescriptionSupplier(
        startInclusive: Int,
        endExclusive: Int,
    ): ContentSupplier =
        LazyContentSupplier(
            context,
            reporter,
            text,
            startInclusive,
            endExclusive,
        )

    /** Entry point for [DocCommentParser]. */
    companion object {
        /** Parse [text] into a [DocComment] within [context], reporting issues to [reporter]. */
        fun parseText(
            context: DocCommentContext,
            text: String,
            reporter: DocumentationIssueReporter,
        ): DocComment {
            val length = text.length

            // Trim any whitespace from the start as well as the start token `/**` (if any).
            val commentBodyStartInclusive = skipDocCommentStartToken(text)

            // The comment does not exist if skipping forwards over whitespace reaches the end of
            // the text. An empty comment (e.g. `/** */`) would stop at the end token.
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
            val commentBodyEndExclusive =
                skipDocCommentEndTokens(text).coerceAtLeast(commentBodyStartInclusive)

            val lexer =
                DocCommentLexer(
                    text,
                    commentBodyStartInclusive,
                    commentBodyEndExclusive,
                )
            return DocCommentParser(
                    context,
                    text,
                    lexer.tokenize(),
                    reporter,
                )
                .parse()
        }
    }
}

/**
 * Skip past start Javadoc tokens from [text] returning the position of the character after the
 * token.
 */
internal fun skipDocCommentStartToken(text: String): Int {
    val length = text.length

    // Skip forwards over any leading whitespaces.
    var start = text.skipForwardsOverLeadingWhitespace(0)

    // Skip forwards over a leading /** token if it is present, otherwise just use the text from
    // this point onwards.
    if (
        start + 2 < length && text[start] == '/' && text[start + 1] == '*' && text[start + 2] == '*'
    ) {
        start += 3
    }

    return start
}

/**
 * Skip past end Javadoc tokens from [text] returning the position of the first character of the
 * token.
 */
internal fun skipDocCommentEndTokens(text: String): Int {
    val length = text.length

    // Skip backwards over any trailing whitespace.
    var end = text.skipBackwardsOverTrailingWhitespace(length - 1)

    // Skip backwards over a trailing */ token.
    if (end - 1 > 0 && text[end] == '/' && text[end - 1] == '*') end -= 2

    return end + 1
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
 * Starting with the character at position [startInclusive] and searching forwards up to
 * [endExclusive], return the position of the first non-whitespace character.
 */
internal fun CharSequence.skipForwardsOverLeadingWhitespace(
    startInclusive: Int,
    endExclusive: Int = this.length,
): Int {
    var index = startInclusive
    while (index < endExclusive && this[index].isWhitespace()) {
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
