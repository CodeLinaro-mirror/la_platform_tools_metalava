/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model

import java.util.regex.Pattern

/** A factory that will create an [ItemDocumentation] for a specific [Item]. */
typealias ItemDocumentationFactory = (Item) -> ItemDocumentation

/**
 * The documentation associated with an [Item].
 *
 * This implements [CharSequence] to simplify migration.
 */
interface ItemDocumentation : CharSequence {
    val text: String

    override val length
        get() = text.length

    override fun get(index: Int) = text.get(index)

    override fun subSequence(startIndex: Int, endIndex: Int) =
        text.subSequence(startIndex, endIndex)

    /**
     * True if the documentation contains one of the following tags that indicates that it should
     * not be part of an API, unless overridden by a show annotation:
     * * `@hide`
     * * `@pending`
     * * `@suppress`
     */
    val isHidden: Boolean

    /**
     * True if the documentation contains `@doconly` which indicates that it should only be included
     * in stubs that are generated for documentation purposes.
     */
    val isDocOnly: Boolean

    /**
     * True if the documentation contains `@removed` which indicates that the [Item] must not be
     * included in stubs or the main signature file but will be included in the `removed` signature
     * file as it is still considered part of the API available at runtime and so cannot be removed
     * altogether.
     */
    val isRemoved: Boolean

    /**
     * Return a duplicate of this instance.
     *
     * [ItemDocumentation] instances can be mutable, and if they are then they must not be shared.
     */
    fun duplicate(item: Item): ItemDocumentation

    /**
     * Like [duplicate] except that it returns an instance of [ItemDocumentation] suitable for use
     * in the snapshot.
     */
    fun snapshot(item: Item): ItemDocumentation = text.toItemDocumentation()

    /** Work around javadoc cutting off the summary line after the first ". ". */
    fun workAroundJavaDocSummaryTruncationIssue() {}

    /**
     * Add the given text to the documentation.
     *
     * If the [tagSection] is null, add the comment to the initial text block of the description.
     * Otherwise, if it is "@return", add the comment to the return value. Otherwise, the
     * [tagSection] is taken to be the parameter name, and the comment added as parameter
     * documentation for the given parameter.
     *
     * @param tagSection if specified and not a parameter name then it is expected to start with
     *   `@`, e.g. `@deprecated`, not `deprecated`.
     */
    fun appendDocumentation(comment: String, tagSection: String?)

    /**
     * Check to see whether this has the named tag section.
     *
     * @param tagSection the name of the tag section, including preceding `@`.
     */
    fun hasTagSection(tagSection: String): Boolean {
        val length = text.length
        var startIndex = 0

        // Scan through the documentation looking for the tag section.
        while (startIndex < length) {
            // Find the position of the tag section starting with the supplied name.
            val index = text.indexOf(tagSection, startIndex)
            if (index == -1) return false

            // If the tag section is at the end of the documentation or is followed by a whitespace
            // then it matches.
            val nextIndex = index + tagSection.length
            if (text.length == nextIndex || Character.isWhitespace(text[nextIndex])) return true

            // Else, continue scanning from the end of the tag section.
            startIndex = nextIndex
        }
        return false
    }

    /**
     * Looks up docs for the first instance of a specific javadoc tag having the (optionally)
     * provided value (e.g. parameter name).
     */
    fun findTagDocumentation(tag: String, value: String? = null): String?

    /** Returns the main documentation for the method (the documentation before any tags). */
    fun findMainDocumentation(): String

    /**
     * Returns the [text], but with fully qualified links (except for the same package, and when
     * turning a relative reference into a fully qualified reference, use the javadoc syntax for
     * continuing to display the relative text, e.g. instead of {@link java.util.List}, use {@link
     * java.util.List List}.
     */
    fun fullyQualifiedDocumentation(): String = fullyQualifiedDocumentation(text)

    /** Expands the given documentation comment in the current name context */
    fun fullyQualifiedDocumentation(documentation: String): String = documentation

    /** Remove the `@deprecated` section, if any. */
    fun removeDeprecatedSection()

    companion object {
        /**
         * A special [ItemDocumentation] that contains no documentation.
         *
         * Used where there is no documentation possible, e.g. text model, type parameters,
         * parameters.
         */
        val NONE: ItemDocumentation = EmptyItemDocumentation()

        /**
         * A special [ItemDocumentationFactory] that returns [NONE] which contains no documentation.
         *
         * Used where there is no documentation possible, e.g. text model, type parameters,
         * parameters.
         */
        val NONE_FACTORY: ItemDocumentationFactory = { NONE }

        /** Wrap a [String] in an [ItemDocumentationFactory]. */
        fun String.toItemDocumentationFactory(): ItemDocumentationFactory = {
            toItemDocumentation()
        }

        /** Wrap a [String] in an [ItemDocumentation] instance. */
        fun String.toItemDocumentation(): ItemDocumentation = DefaultItemDocumentation(this)
    }

    /** An empty [ItemDocumentation] that can never contain any text. */
    private class EmptyItemDocumentation : ItemDocumentation {
        override val text
            get() = ""

        override val isHidden
            get() = false

        override val isDocOnly
            get() = false

        override val isRemoved
            get() = false

        // This is ok to share as it is immutable.
        override fun duplicate(item: Item) = this

        // This is ok to use in a snapshot as it is immutable and model independent.
        override fun snapshot(item: Item) = this

        override fun findTagDocumentation(tag: String, value: String?): String? = null

        override fun appendDocumentation(comment: String, tagSection: String?) {
            error("cannot modify documentation on an item that does not support documentation")
        }

        override fun findMainDocumentation() = ""

        override fun removeDeprecatedSection() {}
    }
}

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
        get() =
            text.contains('@') &&
                (text.contains("@hide") ||
                    text.contains("@pending") ||
                    // KDoc:
                    text.contains("@suppress"))

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
     * See [Item.appendDocumentation] for more details.
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

/** A default [ItemDocumentation] containing JavaDoc/KDoc. */
internal class DefaultItemDocumentation(override var text: String) : AbstractItemDocumentation() {

    override fun duplicate(item: Item) = DefaultItemDocumentation(text)

    override fun mergeDocumentation(comment: String, tagSection: String?) {
        TODO("Not yet implemented")
    }

    override fun findMainDocumentation(): String {
        TODO("Not yet implemented")
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
