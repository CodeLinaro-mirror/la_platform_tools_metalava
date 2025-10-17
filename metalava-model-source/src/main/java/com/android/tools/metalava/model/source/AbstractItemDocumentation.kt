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
     * Lazily initialized backing property for [text].
     *
     * If this is `null` then it requires setting. If [_docComment] is `null` then it will be set by
     * calling [initializeTextBackingField]. Otherwise, it will be set by printing [_docComment].
     */
    protected var _text: String? = null

    /**
     * Called when [_text] requires initializing. Implementations must set [_text] to a non-null
     * value.
     */
    protected abstract fun initializeTextBackingField()

    /**
     * The mutable text contents of the documentation.
     *
     * It uses [_text] as its backing field and setting this will invoke [textChanged].
     *
     * If [_docComment] is null then this needs initializing from the model, otherwise this is
     * initialized from [_docComment].
     */
    override var text: String
        get() {
            if (_text == null) {
                val docComment = _docComment
                if (docComment == null) {
                    // Initialize from the underlying model.
                    initializeTextBackingField()
                } else {
                    // Initialize from the _docComment so that it reflects any changes in it.
                    _text = docComment.asJavadocCommentString()
                }
            }
            return _text!!
        }
        set(value) {
            _text = value
            textChanged()
        }

    /**
     * Called when [text] changes to discard [_docComment] so it will be regenerated the next time
     * [docComment] is accessed.
     *
     * This ensures that [text] and [_docComment] do not get out of sync. It is needed because
     * currently both [text] and [docComment] are modified directly. Longer term, changes will be
     * applied directly to [_docComment] and [text] will be dropped.
     */
    private fun textChanged() {
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

    /**
     * Called when [docComment] is mutated to discard [_text] so it will be regenerated from
     * [_docComment] the next time [text] is accessed.
     *
     * This ensures that [text] and [_docComment] do not get out of sync. It is needed because
     * currently both [text] and [docComment] are modified directly. Longer term, changes will be
     * applied directly to [_docComment] and [text] will be dropped.
     */
    private fun docCommentMutated() {
        _text = null
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
        val originalText = text

        // Before printing fully qualify the comment. This expects a whole comment and will fix up
        // @link and @see tags.
        val fullyQualifiedText = fullyQualifiedDocumentation(text)

        // Only print the comment if it is not blank.
        if (fullyQualifiedText.isNotBlank()) {
            // If fully qualifying did not change the text then used the docComment, otherwise
            // create a new one from the fully qualified text.
            val fullyQualifiedComment =
                if (fullyQualifiedText == originalText) docComment
                else DocComment.createDocComment(fullyQualifiedText, this)

            // Print the docComment as Javadoc.
            fullyQualifiedComment.printAsJavadocComment(writer)
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
        // Try and remove all the `@deprecated` sections. If any were removed then report that the
        // docComment was mutated.
        val mutated = docComment.removeBlockTagSections { it.tagType == "deprecated" }
        if (mutated) {
            docCommentMutated()
        }
    }

    override fun report(issue: Issues.Issue, message: String, lineOffset: Int, charOffset: Int) {
        val location = fileLocation.adjustForLineAndCharOffset(lineOffset, charOffset)
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
