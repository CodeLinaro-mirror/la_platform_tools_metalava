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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.ItemDocumentation
import java.util.regex.Pattern

/**
 * Abstract [ItemDocumentation] into which functionality that is common to all models will be added.
 */
abstract class AbstractItemDocumentation : ItemDocumentation {

    /**
     * The mutable text contents of the documentation. This is abstract to allow the implementations
     * of this to optimize how it is accessed, e.g. initialize it lazily.
     */
    abstract override var text: String

    override val isHidden
        get() = text.contains("@hide")

    override val isDocOnly
        get() = text.contains("@doconly")

    override val isRemoved
        get() = text.contains("@removed")

    override fun workAroundJavaDocSummaryTruncationIssue() {
        // Work around javadoc cutting off the summary line after the first ". ".
        val firstDot = text.indexOf(".")
        if (firstDot > 0 && text.regionMatches(firstDot - 1, "e.g. ", 0, 5, false)) {
            text = text.substring(0, firstDot) + ".g.&nbsp;" + text.substring(firstDot + 4)
        }
    }

    override fun findTagDocumentation(tag: String, value: String?): String? {
        TODO("Not yet implemented")
    }

    override fun appendDocumentation(comment: String, tagSection: String?) {
        if (comment.isBlank()) {
            return
        }

        // Micro-optimization: we're very often going to be merging @apiSince and to a lesser
        // extent @deprecatedSince into existing comments, since we're flagging every single
        // public API. Normally merging into documentation has to be done carefully, since
        // there could be existing versions of the tag we have to append to, and some parts
        // of the comment needs to be present in certain places. For example, you can't
        // just append to the description of a method by inserting something right before "*/"
        // since you could be appending to a javadoc tag like @return.
        //
        // However, for @apiSince and @deprecatedSince specifically, in addition to being frequent,
        // they will (a) never appear in existing docs, and (b) they're separate tags, which means
        // it's safe to append them at the end. So we'll special case these two tags here, to
        // help speed up the builds since these tags are inserted 30,000+ times for each framework
        // API target (there are many), and each time would have involved constructing a full
        // javadoc
        // AST with lexical tokens using IntelliJ's javadoc parsing APIs. Instead, we'll just
        // do some simple string heuristics.
        if (
            tagSection == "@apiSince" ||
                tagSection == "@deprecatedSince" ||
                tagSection == "@sdkExtSince"
        ) {
            text = addUniqueTag(text, tagSection, comment)
            return
        }

        mergeDocumentation(comment.trim(), tagSection)
    }

    /**
     * Merge the comment into the appropriate [tagSection].
     *
     * See [com.android.tools.metalava.model.Item.appendDocumentation] for more details.
     */
    protected abstract fun mergeDocumentation(comment: String, tagSection: String?)

    private fun addUniqueTag(text: String, tagSection: String, commentLine: String): String {
        assert(commentLine.indexOf('\n') == -1) // Not meant for multi-line comments

        if (text.isBlank()) {
            return "/** $tagSection $commentLine */"
        }

        // Already single line?
        if (text.indexOf('\n') == -1) {
            val end = text.lastIndexOf("*/")
            return "/**\n *" + text.substring(3, end) + "\n * $tagSection $commentLine\n */"
        }

        var end = text.lastIndexOf("*/")
        while (end > 0 && text[end - 1].isWhitespace() && text[end - 1] != '\n') {
            end--
        }
        // The comment ends with:
        // * some comment here */
        val insertNewLine: Boolean = text[end - 1] != '\n'

        val indent: String
        var linePrefix = ""
        val secondLine = text.indexOf('\n')
        if (secondLine == -1) {
            // Single line comment
            indent = "\n * "
        } else {
            val indentStart = secondLine + 1
            var indentEnd = indentStart
            while (indentEnd < text.length) {
                if (!text[indentEnd].isWhitespace()) {
                    break
                }
                indentEnd++
            }
            indent = text.substring(indentStart, indentEnd)
            // TODO: If it starts with "* " follow that
            if (text.startsWith("* ", indentEnd)) {
                linePrefix = "* "
            }
        }
        return text.substring(0, end) +
            (if (insertNewLine) "\n" else "") +
            indent +
            linePrefix +
            tagSection +
            " " +
            commentLine +
            "\n" +
            indent +
            " */"
    }

    override fun removeDeprecatedSection() {
        text = removeDeprecatedSection(text)
    }
}

/** Regular expression to match the start of a doc comment. */
private const val DOC_COMMENT_START_RE = """\Q/**\E"""

/**
 * Regular expression to match the end of a block comment. If the block comment is at the start of a
 * line, preceded by some white space then it includes all that white space.
 */
private const val BLOCK_COMMENT_END_RE = """(?m:^\s*)?\Q*/\E"""

/**
 * Regular expression to match the start of a line Javadoc tag, i.e. a Javadoc tag at the beginning
 * of a line. Optionally, includes the preceding white space and a `*` forming a left hand border.
 */
private const val START_OF_LINE_TAG_RE = """(?m:^\s*)\Q*\E\s*@"""

/**
 * A [Pattern[] for matching an `@deprecated` tag and its associated text. If the tag is at the
 * start of the line then it includes everything from the start of the line. It includes everything
 * up to the end of the comment (apart from the line for the end of the comment) or the start of the
 * next line tag.
 */
private val deprecatedTagPattern =
    """((?m:^\s*\*\s*)?@deprecated\b(?m:\s*.*?))($START_OF_LINE_TAG_RE|$BLOCK_COMMENT_END_RE)"""
        .toPattern(Pattern.DOTALL)

/** A [Pattern] that matches a blank, i.e. white space only, doc comment. */
private val blankDocCommentPattern = """$DOC_COMMENT_START_RE\s*$BLOCK_COMMENT_END_RE""".toPattern()

/** Remove the `@deprecated` section, if any, from [docs]. */
fun removeDeprecatedSection(docs: String): String {
    // Find the `@deprecated` tag.
    val deprecatedTagMatcher = deprecatedTagPattern.matcher(docs)
    if (!deprecatedTagMatcher.find()) {
        // Nothing to do as the documentation does not include @deprecated.
        return docs
    }

    // Remove the @deprecated tag and associated text.
    val withoutDeprecated =
        // The part before the `@deprecated` tag.
        docs.substring(0, deprecatedTagMatcher.start(1)) +
            // The part after the `@deprecated` tag.
            docs.substring(deprecatedTagMatcher.end(1))

    // Check to see if the resulting document comment is empty and if it is then discard it all
    // together.
    val emptyDocCommentMatcher = blankDocCommentPattern.matcher(withoutDeprecated)
    return if (emptyDocCommentMatcher.matches()) {
        ""
    } else {
        withoutDeprecated
    }
}
