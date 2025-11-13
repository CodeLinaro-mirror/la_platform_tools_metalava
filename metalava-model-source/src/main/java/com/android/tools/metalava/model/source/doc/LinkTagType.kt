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

/** [TagType] for `@link` and `@linkplain` inline tags. */
internal class LinkTagType(name: String) : TagType<LinkTagData>(name) {
    /** Link tags can only contain */
    override val containsTextOnly: Boolean
        get() = true

    /** Override to extract the source reference from the tag content. */
    override fun extractData(
        context: DocCommentContext,
        text: CharSequence
    ): ExtractDataResult<LinkTagData>? {
        val referenceStart = text.skipForwardsOverLeadingWhitespace(0)
        val referenceEndExclusive = text.findEndOfReference(referenceStart)
        if (referenceEndExclusive == 0) return null

        val sourceReference =
            text
                .substring(0, referenceEndExclusive)
                // Normalize whitespace by replacing blocks of whitespace with a single space.
                // Ensures consistent formatting irrespective of how it was formatted in the source.
                .replace(SOME_WHITESPACE, " ")

        // Resolve the source reference, if it failed then still return a non-null result to ensure
        // that whitespace is normalized consistently.
        val resolvedReference = context.resolveReference(sourceReference)

        return ExtractDataResult(
            LinkTagData(sourceReference, resolvedReference),
            // The source reference and any following whitespace must be removed from the content as
            // they are part of [LinkTagData].
            consumedContent = text.skipForwardsOverLeadingWhitespace(referenceEndExclusive)
        )
    }

    companion object {
        /** Regex that matches one or more whitespace characters. */
        private val SOME_WHITESPACE = Regex("""\s+""")
    }
}

/**
 * Find the end of the reference.
 *
 * A reference can contain whitespace but only within parentheses. The parentheses must be balanced,
 * i.e. for every `(` have a corresponding `)`.
 *
 * @param startInclusive the start of the reference, must be non-whitespace otherwise this will fail
 *   to find a reference.
 */
internal fun CharSequence.findEndOfReference(startInclusive: Int): Int {
    require(!this[startInclusive].isWhitespace()) {
        "startInclusive must not point to a whitespace character"
    }
    // Keep track of the parenthesis nesting level. This should not really be necessary as the only
    // way to have multiple levels of parentheses is to have Kotlin lambda types which are not
    // supported in Java. However, this is a simple way to track it.
    var nesting = 0

    // Scan forward trying to find the end of the reference.
    for (index in startInclusive until length) {
        val c = this[index]
        when {
            c == '(' -> {
                nesting += 1
            }
            c == ')' -> {
                // Increase the nesting level.
                nesting -= 1
            }
            // If whitespace is encountered then stop only if outside parentheses.
            c.isWhitespace() -> {
                if (nesting == 0) return index
            }
        }
    }

    // TODO(b/456188750): Report issues with unbalanced parentheses.

    return length
}

/** Encapsulates information about the `@link` and `@linkplain` tags. */
internal data class LinkTagData(
    /** The reference from the source; used as the label if necessary. */
    val sourceReference: String,
    /** The resolved reference, subclasses identify the specific part of the API it references. */
    val resolvedReference: ResolvedReference?,
) : TagData {
    /**
     * Print the tag contents which consists of the [sourceReference] and the [content] which is the
     * optional label.
     *
     * If the [resolvedReference] is different to the [sourceReference] and [content] is `null` then
     * this will use the [sourceReference] as the label.
     */
    override fun printTagContents(contentPrinter: JavadocContentPrinter, content: JavadocContent?) {
        val writer = contentPrinter.writer
        writer.print(" ")
        val resolvedText = resolvedReference?.displayName ?: sourceReference
        writer.print(resolvedText)

        // The content is the label of the link tag so check it if exists.
        if (content == null) {
            // If the label is not specified and the resolved text is different to the source
            // reference then use the source reference as the label to try and preserve the
            // developer's original intent.
            if (resolvedText != sourceReference) {
                writer.print(" ")
                writer.print(sourceReference)
            }
        } else {
            // Print the remaining content. Always preceded by a space as any leading whitespace has
            // been trimmed from it.
            content.printWithLeadingSpaceTo(contentPrinter)
        }
    }

    /**
     * Make sure that the [sourceReference] is searchable just like it would be if it was part of
     * the content.
     */
    override fun textMatches(predicate: (String) -> Boolean) = predicate(sourceReference)
}
