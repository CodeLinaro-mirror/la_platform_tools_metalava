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
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.source.doc.DocComment
import com.android.tools.metalava.model.source.doc.DocumentationIssueReporter
import com.android.tools.metalava.reporter.Issues
import java.io.PrintWriter
import java.util.regex.Pattern

/**
 * Abstract [ItemDocumentation] into which functionality that is common to all models will be added.
 */
abstract class AbstractItemDocumentation(
    protected val item: SelectableItem,
) : ItemDocumentation, DocumentationIssueReporter {

    /**
     * The mutable text contents of the documentation. This is abstract to allow the implementations
     * of this to optimize how it is accessed, e.g. initialize it lazily.
     */
    abstract override var text: String

    /**
     * Call when [text] changes to discard the [_docComment] so it will be regenerated next time it
     * is accessed.
     *
     * This ensures that [text] and [_docComment] do not get out of sync. It is needed because
     * currently the [text] is modified directly. Longer term, changes will be applied directly to
     * [_docComment] and [text] will be dropped.
     */
    protected fun textChanged() {
        _docComment = null
    }

    /** Lazily initialized from [text]. Is cleared by [textChanged] if [text] is modified. */
    private var _docComment: DocComment? = null

    private val docComment: DocComment
        get() {
            val docComment = _docComment
            return if (docComment == null) {
                val new = DocComment.createDocComment(text, this)
                _docComment = new
                new
            } else {
                docComment
            }
        }

    override val isHidden
        get() = hasBlockTagOfType("hide")

    override val isDocOnly
        get() = hasBlockTagOfType("doconly")

    override val isRemoved
        get() = hasBlockTagOfType("removed")

    override fun hasBlockTagOfType(blockTagType: String) =
        docComment.hasBlockTagOfType(blockTagType)

    override fun print(writer: PrintWriter) {
        val text = fullyQualifiedDocumentation()
        if (text.isNotBlank()) {
            val trimmed = trimDocIndent(text)
            writer.println(trimmed)
        }
    }

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

    override fun report(issue: Issues.Issue, message: String, lineOffset: Int) {
        val location = fileLocation.forLineOffset(lineOffset)
        item.codebase.reporter.report(issue, null, message, location)
    }
}

/**
 * Matches a comment line, allowing significant content to be extracted.
 *
 * Significant content excludes trailing whitespace and leading asterisks
 * https://docs.oracle.com/en/java/javase/11/docs/specs/doc-comment-spec.html#leading-asterisks
 */
private val COMMENT_LINE = Pattern.compile("""^(\h*\*+)?(.*?)\h*$""", Pattern.MULTILINE)

/** The group in [COMMENT_LINE] that contains the optional leading asterisks. */
private const val LEADING_ASTERISKS_GROUP = 1

/** The group in [COMMENT_LINE] that contains the significant content. */
private const val SIGNIFICANT_TEXT_GROUP = 2

/**
 * Trim indentation from the [existingDoc] comment.
 *
 * Removes indentation whitespace after the first newline, making sure that there is at least one
 * white space of indentation.
 */
fun trimDocIndent(existingDoc: String): String {
    // Trim leading/trailing whitespace from the existing documentation
    val trimmed = existingDoc.trim()

    val index = trimmed.indexOf('\n')
    if (index == -1) {
        return trimmed
    }

    // Transform the comment normalizing the indentation.
    return buildString {
        var separator = ""
        val matcher = COMMENT_LINE.matcher(trimmed)

        // Match each comment line to determine if it has any leading asterisks and what the
        // significant content is.
        while (matcher.find()) {
            append(separator)
            separator = "\n"

            // Get the bounds of the significant text from the comment line.
            val start = matcher.start(SIGNIFICANT_TEXT_GROUP)
            val end = matcher.end(SIGNIFICANT_TEXT_GROUP)

            if (matcher.start(LEADING_ASTERISKS_GROUP) != -1 || start == end) {
                // It either has leading asterisks in which case they need to be replaced with " *"
                // or it is blank in which case it should be represented with " *".
                append(" *")
            } else {
                val c = trimmed[start]
                // Add a leading " *" to every line except lines that start with a "/" as that would
                // produce a "*/" token which would prematurely end the comment and cause
                // compilation errors.
                if (c != '/') {
                    append(" *")
                }
            }

            // Add the significant text from the comment line.
            if (start != end) {
                append(trimmed, start, end)
            }
        }
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
