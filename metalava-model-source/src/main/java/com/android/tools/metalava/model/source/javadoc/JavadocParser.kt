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
import com.android.tools.metalava.model.source.doc.TagTypes
import com.android.tools.metalava.model.source.doc.skipBackwardsOverTrailingWhitespace
import com.android.tools.metalava.model.source.doc.skipForwardsOverLeadingWhitespace
import com.android.tools.metalava.reporter.Issues
import java.nio.CharBuffer
import org.antlr.v4.runtime.ANTLRErrorListener
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CodePointBuffer
import org.antlr.v4.runtime.CodePointCharStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.NoViableAltException
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.TokenStream

/**
 * Parses a block of text into a [JavadocContent].
 *
 * A wrapper around [antlrParser] which actually does the parsing.
 */
internal class JavadocParser
private constructor(
    private val antlrParser: AntlrJavadocParser,
    private val context: DocCommentContext,
    private val reporter: DocumentationIssueReporter,
) {

    companion object {
        /**
         * Parse [text] from [startInclusive] up to, but not including [endExclusive] as a javadoc
         * comment (optionally including the /** ... */).
         *
         * @param context context that applies to [text].
         * @param text the String to be parsed.
         * @param startInclusive the index of the first character to parse.
         * @param endExclusive the index after the last character to parse.
         */
        fun parse(
            context: DocCommentContext,
            text: String,
            startInclusive: Int,
            endExclusive: Int,
            reporter: DocumentationIssueReporter,
        ): JavadocContent? {
            var fileName = "<unknown>"
            val errorListener = JavadocErrorListener(reporter)

            // Create the ANTLR lexer.
            val charStream = charStreamFromStringRange(text, startInclusive, endExclusive, fileName)
            val lexer = AntlrJavadocLexer(charStream)
            lexer.setErrorListener(errorListener)

            // Create the ANTLR parser.
            val tokenStream = CommonTokenStream(lexer)
            val antlrParser = AntlrJavadocParser(tokenStream)
            antlrParser.setErrorListener(errorListener)

            val parser = JavadocParser(antlrParser, context, reporter)
            return parser.parse()
        }

        /**
         * Create a [CodePointCharStream] that is backed by [text] from [startInclusive] up to but
         * not including [endExclusive].
         */
        private fun charStreamFromStringRange(
            text: String,
            startInclusive: Int,
            endExclusive: Int,
            fileName: String,
        ): CodePointCharStream {
            var length = endExclusive - startInclusive
            val codePointBufferBuilder = CodePointBuffer.builder(length)
            val cb = CharBuffer.allocate(length)
            cb.put(text, startInclusive, endExclusive)
            cb.flip()
            codePointBufferBuilder.append(cb)
            return CodePointCharStream.fromBuffer(codePointBufferBuilder.build(), fileName)
        }

        /** Remove any default listeners and add [listener] as the only one. */
        private fun Recognizer<*, *>.setErrorListener(listener: ANTLRErrorListener) {
            removeErrorListeners()
            addErrorListener(listener)
        }
    }

    private fun parse(): JavadocContent? {
        val descriptionContext = antlrParser.description()
        return JavadocContentBuilder.buildFrom(descriptionContext, context, reporter)
    }
}

/** A [BaseErrorListener] that throws an exception for syntax errors. */
internal class JavadocErrorListener(
    private val reporter: DocumentationIssueReporter,
) : BaseErrorListener() {
    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String,
        e: RecognitionException?
    ) {
        // Construct a full message that includes lots of debug information about the nature of the
        // problem. This is not intended to report issues for developers to handle.
        val fullMsg = appendExpectedTokens(e, msg, recognizer) ?: msg

        // line is 1-based but lineOffset is 0-based so subtract 1 from the former to create the
        // latter.
        val lineOffset = line - 1
        // charPositionInLine is 0-based and so is charOffset so the former can be used as the
        // latter directly.
        val charOffset = charPositionInLine
        reporter.report(Issues.INVALID_JAVADOC, fullMsg, lineOffset, charOffset)
    }

    /**
     * Append information about expected tokens in [e] (if any) from [recognizer], from [e] to the
     * [msg], or return `null` if the original [msg] is to be used unchanged.
     */
    private fun appendExpectedTokens(
        e: RecognitionException?,
        msg: String,
        recognizer: Recognizer<*, *>?
    ): String? {
        // If there is no exception then return immediately.
        e ?: return null

        // If there is no recognizer then there is no way to create meaningful descriptions of any
        // expected tokens so return immediately.
        recognizer ?: return null

        // Work around a problem where calling expectedTokens does not work if [recognizer] has
        // never entered a valid state.
        if (e.offendingState == -1) return null

        // If the exception does not have any expected tokens then return immediately.
        val expectedTokens = e.expectedTokens ?: return null

        // Otherwise, append the expected token information to `msg`.
        return buildString {
            append(msg)
            append("\n")
            append("  Expected:\n")
            val vocabulary = recognizer.vocabulary
            for (interval in expectedTokens.intervals) {
                for (tokenTypeIndex in interval.a.rangeTo(interval.b)) {
                    append("    ${vocabulary.getSymbolicName(tokenTypeIndex)}\n")
                }
            }
            val offendingToken = e.offendingToken
            val tokens = recognizer.inputStream as? TokenStream
            if (e is NoViableAltException && tokens != null) {
                append("  No viable alternative found for sequence:\n")
                for (tokenIndex in e.startToken.tokenIndex.rangeTo(offendingToken.tokenIndex)) {
                    val token = tokens[tokenIndex]
                    append("    ${vocabulary?.getSymbolicName(token.type)} \"${token.text}\"\n")
                }
            } else {
                append("  Found:\n")
                append(
                    "    ${vocabulary?.getSymbolicName(offendingToken.type)} \"${offendingToken.text}\"\n"
                )
            }
        }
    }
}

/** Builds [JavadocContent] from [AntlrJavadocParser.DescriptionContext]. */
private class JavadocContentBuilder(
    private val context: DocCommentContext,
    reporter: DocumentationIssueReporter,
) : AntlrJavadocParserBaseVisitor<Unit>() {
    /** A [DocumentationIssueReporter] that can be used to report issues with a [Token]. */
    private val tokenIssueReporter = TokenIssueReporter(reporter)

    /** Backing field for [exprBuilder], lazily initialized when [exprBuilder] is accessed. */
    private lateinit var _exprBuilder: ExprBuilder

    /** Get the [ExprBuilder] to use to construct [Expr] for conditional javadoc processing. */
    private val exprBuilder: ExprBuilder
        get() {
            if (!::_exprBuilder.isInitialized) {
                _exprBuilder = ExprBuilder(context, tokenIssueReporter)
            }
            return _exprBuilder
        }

    /**
     * Determines whether whitespace should be trimmed from the start of the content.
     *
     * Initialized to `true`, set to `false` as soon as any non-newline content is added.
     *
     * This is needed because extra whitespace is often added at the beginning of a block of text to
     * prettify the formatting. That whitespace needs to be removed to ensure consistent behavior.
     */
    private var trimLeadingWhitespace = true

    /** Responsible for handling an [AntlrJavadocParser.InlineTagContext]. */
    private var inlineTagHandler: InlineTagHandler = ADD_INLINE_TAG_AS_OBJECT

    /**
     * A [MutableList] of consecutive [JavadocContent] instances that have been created from the
     * Javadoc.
     *
     * Is `null` if no [JavadocContent] has yet been added. This backs [contentList] and should not
     * be accessed directly except by [contentList], [nestedContent] and [getContent].
     */
    @Deprecated(message = "Do not access directly", replaceWith = ReplaceWith("contentList"))
    private var _contentList: MutableList<JavadocContent>? = null

    /**
     * A [MutableList] of consecutive [JavadocContent] instances that have been created from the
     * Javadoc.
     */
    @Suppress("DEPRECATION")
    private val contentList: MutableList<JavadocContent>
        get() =
            _contentList
                ?: let {
                    val list = mutableListOf<JavadocContent>()
                    _contentList = list
                    list
                }

    /**
     * Append [javadocContent] to [contentList].
     *
     * This will flush any text that has been buffered in [textBuffer].
     */
    private fun appendContent(javadocContent: JavadocContent) {
        // Make sure that any text which has been appended to [textBuffer] has been added to the
        // content list so that it appears before [javadocContent]. Trailing whitespace is not
        // trimmed as it could provide significant separation between any non-whitespace content and
        // [javadocContent].
        flushText(trimTrailingWhitespace = false)

        contentList.add(javadocContent)

        // Some non-newline content has been added so any newlines left are significant and should
        // be kept.
        trimLeadingWhitespace = false
    }

    /** [StringBuilder] into which consecutive blocks of text from the Javadoc are accumulated. */
    private val textBuffer = StringBuilder()

    /** Append [text] to [textBuffer]. */
    private fun appendText(text: String) {
        // If this could be the start of the whole description block then check to see if there are
        // any leading newlines that can be skipped.
        if (trimLeadingWhitespace) {
            // Find the first non-newline character in the text to be appended.
            val length = text.length
            val start = text.skipForwardsOverLeadingWhitespace(0)

            // If the text only consists of a newline character then do nothing.
            if (start == length) return

            // Append the text from the first non-newline character.
            textBuffer.append(text, start, length)

            // As a non-newline character was seen any newline characters found from now onwards
            // cannot be a leading newline.
            trimLeadingWhitespace = false
        } else {
            textBuffer.append(text)
        }
    }

    /**
     * Trim any trailing whitespace from the end of [textBuffer].
     *
     * It does that by scanning backwards from the end of the [textBuffer] to find the first
     * non-whitespace character and sets the length to ignore any characters after that.
     */
    private fun trimTrailingWhitespaceFromTextBuffer() {
        var length = textBuffer.length
        var trimmedEnd = textBuffer.skipBackwardsOverTrailingWhitespace(length - 1) + 1
        textBuffer.setLength(trimmedEnd)
    }

    /**
     * Trim any trailing non-newline whitespace from the end of [textBuffer].
     *
     * It does that by scanning backwards from the end of the [textBuffer] to find the first
     * non-whitespace/newline character and sets the length to ignore any characters after that.
     */
    private fun trimTrailingNonNewlineWhitespaceFromTextBuffer() {
        // Skip backwards over any trailing non-newline whitespace.
        var end = textBuffer.length - 1
        while (end >= 0) {
            val c = textBuffer[end]
            if (c == '\n' || !c.isWhitespace()) break
            end -= 1
        }
        textBuffer.setLength(end + 1)
    }

    /** Append newline character to [textBuffer]. */
    private fun appendNewline() {
        // Before appending the newline character remove any trailing non-newline/whitespace from
        // the end of the text buffer.
        trimTrailingNonNewlineWhitespaceFromTextBuffer()

        appendText("\n")
    }

    /**
     * If [textBuffer] is not empty then this will wrap it in a [JavadocText] object, add that to
     * the [contentList] and clear [textBuffer].
     *
     * @param trimTrailingWhitespace if true then remove any trailing whitespace from [textBuffer].
     */
    private fun flushText(trimTrailingWhitespace: Boolean) {
        if (textBuffer.isNotEmpty()) {
            // If required, remove any trailing whitespace from [textBuffer] before flushing.
            if (trimTrailingWhitespace) {
                // Trim trailing whitespace from the text buffer which may empty [textBuffer].
                trimTrailingWhitespaceFromTextBuffer()
                if (textBuffer.isEmpty()) return
            }

            var text = textBuffer.toString()
            textBuffer.clear()
            contentList.add(JavadocText(text))
        }
    }

    /**
     * Create a [JavadocContent] for nested content.
     *
     * This flushes the [textBuffer], saves away [_contentList], setting it to `null` and then
     * invokes [body] to apply this visitor to the nested content to populate [textBuffer] and
     * [contentList]. It then calls [getContent]
     */
    @Suppress("DEPRECATION")
    private fun nestedContent(containsTextOnly: Boolean, body: () -> Unit): JavadocContent? {
        // Make sure that any text which has been appended to [textBuffer] has been added to the
        // content list so that it appears before any nested content. Trailing whitespace is not
        // trimmed as it could provide significant separation between any non-whitespace content and
        // the nested content.
        flushText(trimTrailingWhitespace = false)

        val oldInlineTagHandler = inlineTagHandler
        inlineTagHandler =
            if (containsTextOnly) TREAT_INLINE_TAG_AS_TEXT else ADD_INLINE_TAG_AS_OBJECT

        // Save away the current _contentList and set it to null so a new list will be created if
        // any nested content is added.
        val oldContentList = _contentList
        _contentList = null
        try {
            // Call the body lambda which will add any nested content.
            body()

            // Get the nested content that was added by [body]. Trailing whitespace is not trimmed
            // as it could provide significant separation between any non-whitespace content in the
            // nested content and following content.
            return getContent(trimTrailingWhitespace = false)
        } finally {
            // Restore _contentList back to what it was before.
            _contentList = oldContentList

            // Restore inlineTagHandler back to what it was before.
            inlineTagHandler = oldInlineTagHandler
        }
    }

    /**
     * Get a [JavadocContent] object for any content that has been added to the [textBuffer] and
     * [_contentList].
     *
     * If [textBuffer] contains any textual content then it is added to [_contentList] first.
     *
     * If [_contentList] has not yet been created, or was created but is empty then there is no
     * content so this returns `null`. If [_contentList] contains a single item then it is returned.
     * Otherwise, the [_contentList] is wrapped in a [JavadocContentList].
     *
     * Irrespective of what this returns, [_contentList] will be `null` after this returns.
     *
     * @param trimTrailingWhitespace if true then remove any trailing whitespace from [textBuffer].
     */
    @Suppress("DEPRECATION")
    private fun getContent(trimTrailingWhitespace: Boolean): JavadocContent? {
        // Make sure that any text which has been appended to [textBuffer] has been added to the
        // content list so that it will be included in the returned content. Remove trailing
        // whitespace if required.
        flushText(trimTrailingWhitespace)

        // Get the optional content from _contentList.
        val content =
            _contentList?.let { contentList ->
                // Discard the content list to force a new one to be created next time content is
                // added. This will ensure correct behavior even if _contentList is wrapped in a
                // [JavadocContentList].
                _contentList = null

                contentList.toOptionalJavadocContent()
            }

        return content
    }

    override fun visitTextContent(ctx: AntlrJavadocParser.TextContentContext) {
        // Add the text to the text buffer.
        appendText(ctx.text)
    }

    override fun visitNewline(ctx: AntlrJavadocParser.NewlineContext?) {
        // This matches a newline possible following by white space and then a `*` (but not '*/').
        // However, the white space and `*` are not considered part of the comment so are ignored.
        appendNewline()
    }

    override fun visitInlineIfTag(ctx: AntlrJavadocParser.InlineIfTagContext) {
        // Convert the expression into an Expr object.
        val exprRule =
            ctx.expr()
                ?: run {
                    tokenIssueReporter.report(
                        ctx.INLINE_IF_TAG_START().symbol,
                        Issues.INVALID_IF_TAG,
                        "missing <expr>"
                    )
                    return
                }
        val expr = exprBuilder.buildExpr(exprRule)

        // Evaluate the expression to get a boolean result.
        val result = expr.evaluate(context)

        // Select the content to use based on the value of the expression.
        if (result) {
            // The content to use when true is always provided.
            ctx.braceExpression(0).braceContent().forEach { it.accept(this) }
        } else {
            // The else content is optional.
            ctx.braceExpression(1)?.braceContent()?.forEach { it.accept(this) }
        }
    }

    override fun visitInlineTag(ctx: AntlrJavadocParser.InlineTagContext) {
        inlineTagHandler.handleInlineTag(this, ctx)
    }

    /** Create a [JavadocInlineTag] from [ctx] and add to the [contentList]. */
    private fun addAsJavadocInlineTag(ctx: AntlrJavadocParser.InlineTagContext) {
        // The inline tag is the end of any leading whitespace so prevent any from being removed
        // from the start of the inline tag content.
        trimLeadingWhitespace = false

        val tagNameToken = ctx.INLINE_TAG_NAME().symbol
        val tagTypeName = tagNameToken.text
        val tagType = TagTypes.tagTypeOf(tagTypeName)
        if (!tagType.form.supportsInlineTag) {
            tokenIssueReporter.report(
                tagNameToken,
                Issues.INVALID_TAG_FORM,
                "Cannot use '$tagTypeName' as an inline tag"
            )
        }

        // If a BRACE_CLOSE token was not found then the inline tag was not closed properly so
        // report the issue.
        if (ctx.BRACE_CLOSE() == null) {
            tokenIssueReporter.report(
                ctx.INLINE_TAG_START().symbol,
                Issues.UNCLOSED_INLINE_TAG,
                "unclosed inline '@${tagTypeName}' tag",
            )
        }

        // Split the nested content, if any, into separate data and remaining content.
        val result =
            ctx.inlineTagContent()?.let { inlineCtx ->
                // Construct a nested JavadocContent object from the content of the inline tag.
                val nestedTagContent =
                    nestedContent(tagType.containsTextOnly) {
                        // Visit the inline tag content.
                        inlineCtx.accept(this)
                    }

                nestedTagContent?.let { tagContent ->
                    tokenIssueReporter.reportAtToken(inlineCtx.start) {
                        tagContent.extractTagDataForTagType(context, tagType, tokenIssueReporter)
                    }
                }
            }

        val tagData = result?.tagData
        val remainder = result?.remainder

        // Add an inline tag to the content.
        appendContent(JavadocInlineTag(tagType, tagData, remainder))
    }

    /** Treat [ctx] as a block of text. */
    private fun treatAsText(ctx: AntlrJavadocParser.InlineTagContext) {
        // Add all the children as text.
        val inlineContent = ctx.inlineTagContent()
        for (child in ctx.children) {
            if (child === inlineContent) {
                // Visit the inline content and have each append as text. This ensures that leading
                // asterisks are moves from the beginning of the newline.
                inlineContent.accept(this)
            } else {
                // All other children are simple tokens so just add their string representation.
                appendText(child.text)
            }
        }
    }

    override fun visitBraceExpression(ctx: AntlrJavadocParser.BraceExpressionContext) {
        // A brace expression implicitly starts and stops with braces so add them into the model.
        appendText("{")
        super.visitBraceExpression(ctx)
        appendText("}")
    }

    companion object {
        /** Build a optional [JavadocContent] from [descriptionContext]. */
        fun buildFrom(
            descriptionContext: AntlrJavadocParser.DescriptionContext,
            context: DocCommentContext,
            reporter: DocumentationIssueReporter,
        ): JavadocContent? {
            // Create a builder that will create [JavadocContent] by traversing the
            // [descriptionContext] structure.
            val builder = JavadocContentBuilder(context, reporter)

            // Traverse the [descriptionContent] structure.
            descriptionContext.accept(builder)

            // Get the [JavadocContent], if any, that was created.
            return builder.getContent(trimTrailingWhitespace = true)
        }

        /**
         * Adds [AntlrJavadocParser.InlineTagContext] to [JavadocContentBuilder] as a
         * [JavadocInlineTag] object.
         */
        private val ADD_INLINE_TAG_AS_OBJECT =
            object : InlineTagHandler {
                override fun handleInlineTag(
                    builder: JavadocContentBuilder,
                    ctx: AntlrJavadocParser.InlineTagContext
                ) {
                    builder.addAsJavadocInlineTag(ctx)
                }
            }

        private val TREAT_INLINE_TAG_AS_TEXT =
            object : InlineTagHandler {
                override fun handleInlineTag(
                    builder: JavadocContentBuilder,
                    ctx: AntlrJavadocParser.InlineTagContext
                ) {
                    builder.treatAsText(ctx)
                }
            }
    }
}

/**
 * Responsible for handling a [AntlrJavadocParser.InlineTagContext].
 *
 * Used in [JavadocContentBuilder] to select context specific handling of inline tags.
 */
private interface InlineTagHandler {
    /** Determine how [builder] should handle the [ctx] inline tag. */
    fun handleInlineTag(builder: JavadocContentBuilder, ctx: AntlrJavadocParser.InlineTagContext)
}
