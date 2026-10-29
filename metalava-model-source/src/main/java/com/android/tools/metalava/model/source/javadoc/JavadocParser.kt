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

/**
 * Parses a block of text into a [JavadocContent].
 *
 * This parser operates as a recursive-descent parser on a stream of [Token]s produced by
 * [JavadocLexer]. The parsing process is divided into two distinct phases:
 * 1. **Lexing ([JavadocLexer])**:
 *     - The lexer scans the raw character sequence from `startInclusive` to `endExclusive`.
 *     - It uses a mode stack to handle context-sensitive syntax. For example, it distinguishes
 *       between regular description text (`DEFAULT` mode), tag names and brace-balanced tag bodies
 *       (`INLINE_TAG` and `BALANCED_BRACE` modes), and conditional if-tag structures and
 *       expressions (`INLINE_IF_TAG` and `EXPR` modes).
 *     - The lexer handles comment formatting nuances, such as collapsing line-break continuation
 *       prefixes (e.g. `\n * `) into [TokenType.NEWLINE] tokens.
 *     - The result is a flat [List] of [Token]s ending with a [TokenType.EOF] token.
 * 2. **Parsing ([JavadocParser])**:
 *     - Operates over the pre-tokenized [Token] stream using recursive-descent methods ([peek],
 *       [consume], [match], [expect]).
 *     - **Text Accumulation**: Consecutive text, whitespace, and newline tokens are accumulated
 *       into [textBuffer]. When a non-text structure (such as an inline tag) is encountered or at
 *       the end of parsing, buffered text is flushed into a [JavadocText] node, trimming leading or
 *       trailing whitespace as configured.
 *     - **Inline Tags**: When a [TokenType.INLINE_TAG_START] is encountered, the parser delegates
 *       to [inlineTagHandler]. Tags are parsed into [JavadocInlineTag] instances, extracting
 *       tag-specific data unless in a text-only context (e.g. inside `{@code}` or `{@literal}`)
 *       where they are preserved as literal text.
 *     - **Conditional Javadoc**: When an [TokenType.INLINE_IF_TAG_START] (`{@if`) is encountered,
 *       the condition expression is parsed using [ExprBuilder] and evaluated against [context]. The
 *       active branch is then parsed and emitted into the content, while the inactive branch is
 *       skipped using [skipBraceExpression].
 *     - **Issue Reporting**: Syntax and validation errors are reported through [tokenIssueReporter]
 *       and [reporter], using the line numbers and character offsets tracked in each [Token].
 *
 * @param tokens the list of [Token]s to parse.
 * @param context context that applies to the Javadoc comment (such as resolving references).
 * @param reporter used for reporting issues found during parsing.
 */
internal class JavadocParser
private constructor(
    private val tokens: List<Token>,
    private val context: DocCommentContext,
    private val reporter: DocumentationIssueReporter,
) {
    /** A [TokenIssueReporter] that can be used to report issues with a [Token]. */
    private val tokenIssueReporter = TokenIssueReporter(reporter)

    /** [ExprBuilder] used to construct [Expr] for conditional javadoc processing. */
    private val exprBuilder = ExprBuilder(context, tokenIssueReporter)

    /** The index of the current token being examined in [tokens]. */
    private var current = 0

    /**
     * Determines whether whitespace should be trimmed from the start of the content.
     *
     * Initialized to `true`, set to `false` as soon as any non-newline content is added.
     *
     * This is needed because extra whitespace is often added at the beginning of a block of text to
     * prettify the formatting. That whitespace needs to be removed to ensure consistent behavior.
     */
    private var trimLeadingWhitespace = true

    /** Responsible for handling an inline tag. */
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

    /** [StringBuilder] into which consecutive blocks of text from the Javadoc are accumulated. */
    private val textBuffer = StringBuilder()

    companion object {
        /**
         * Parse [text] from [startInclusive] up to, but not including [endExclusive] as a javadoc
         * comment (optionally including the /** ... */).
         *
         * @param context context that applies to [text].
         * @param text the String to be parsed.
         * @param startInclusive the index of the first character to parse.
         * @param endExclusive the index after the last character to parse.
         * @param reporter used for reporting any issues encountered during parsing.
         * @return the parsed [JavadocContent], or `null` if the content was empty.
         */
        fun parse(
            context: DocCommentContext,
            text: String,
            startInclusive: Int,
            endExclusive: Int,
            reporter: DocumentationIssueReporter,
        ): JavadocContent? {
            val lexer = JavadocLexer(text, startInclusive, endExclusive, reporter)
            val tokens = lexer.tokenize()
            val parser = JavadocParser(tokens, context, reporter)
            return parser.parse()
        }

        /** Adds an inline tag to [JavadocParser] as a [JavadocInlineTag] object. */
        private val ADD_INLINE_TAG_AS_OBJECT =
            object : InlineTagHandler {
                override fun handleInlineTag(
                    parser: JavadocParser,
                    startToken: Token,
                    nameToken: Token
                ) {
                    parser.addAsJavadocInlineTag(startToken, nameToken)
                }
            }

        /** Treats an inline tag as literal text (used inside tags like `{@code}`). */
        private val TREAT_INLINE_TAG_AS_TEXT =
            object : InlineTagHandler {
                override fun handleInlineTag(
                    parser: JavadocParser,
                    startToken: Token,
                    nameToken: Token
                ) {
                    parser.treatAsText(startToken, nameToken)
                }
            }
    }

    /** Returns the token at [current] without advancing. */
    private fun peek(): Token = tokens[current]

    /** Returns the type of the token at [current]. */
    private fun peekType(): TokenType = peek().type

    /** Consumes and returns the token at [current], advancing [current] by 1. */
    private fun consume(): Token = tokens[current++]

    /** Consumes the token at [current] if its type equals [type], returning `true`. */
    private fun match(type: TokenType): Boolean {
        // If the current token matches the expected type, consume it and return true.
        if (peekType() == type) {
            consume()
            return true
        }
        return false
    }

    /**
     * Consumes and returns the token at [current] if its type equals [type]. Otherwise, reports an
     * [Issues.INVALID_JAVADOC] issue with [errorMessage] at the token's location and returns
     * `null`.
     */
    private fun expect(type: TokenType, errorMessage: String): Token? {
        // If the current token matches the expected type, consume and return it.
        if (peekType() == type) {
            return consume()
        }

        // Otherwise, report an issue at the current token's location and return null.
        val token = peek()
        reporter.report(
            Issues.INVALID_JAVADOC,
            errorMessage,
            token.line - 1,
            token.charPositionInLine
        )
        return null
    }

    /**
     * Parse the full token stream into [JavadocContent].
     *
     * Processes description elements until [TokenType.EOF] is reached, then flushes and trims
     * trailing whitespace.
     */
    private fun parse(): JavadocContent? {
        while (peekType() != TokenType.EOF) {
            parseDescriptionElement()
        }
        return getContent(trimTrailingWhitespace = true)
    }

    /**
     * Parse a single element of a top-level description.
     *
     * Can be text content, spaces, a newline, an inline tag, an inline `@if` tag, or literal
     * braces.
     */
    private fun parseDescriptionElement() {
        when (peekType()) {
            // Plain text content. Append directly to text buffer.
            TokenType.TEXT_CONTENT -> appendText(consume().text)

            // Horizontal whitespace. Append to text buffer.
            TokenType.SPACE -> appendText(consume().text)

            // Newline sequence. Append a newline (trimming preceding non-newline whitespace).
            TokenType.NEWLINE -> {
                consume()
                appendNewline()
            }

            // Start of an inline tag '{@...}'. Parse as an inline tag.
            TokenType.INLINE_TAG_START -> parseInlineTag()

            // Start of a conditional '{@if...}' tag. Parse as a conditional tag.
            TokenType.INLINE_IF_TAG_START -> parseInlineIfTag()

            // Opening brace '{'. Treated as literal text at description level.
            TokenType.BRACE_OPEN -> appendText(consume().text)

            // Closing brace '}'. Treated as literal text at description level.
            TokenType.BRACE_CLOSE -> appendText(consume().text)

            // End of token stream. Nothing to parse.
            TokenType.EOF -> return

            // Any other token type. Append as literal text.
            else -> {
                appendText(consume().text)
            }
        }
    }

    /**
     * Parse an inline tag starting at `{@`.
     *
     * Consumes the `{@` token, expects an inline tag name, and delegates to [inlineTagHandler].
     */
    private fun parseInlineTag() {
        val startToken = consume() // consume INLINE_TAG_START

        // Expect the inline tag name (e.g. "link", "code"). If missing, parsing cannot continue for
        // this tag.
        val nameToken = expect(TokenType.INLINE_TAG_NAME, "expected tag name after '{@'")
        if (nameToken == null) {
            return
        }

        inlineTagHandler.handleInlineTag(this, startToken, nameToken)
    }

    /**
     * Construct a [JavadocInlineTag] from [startToken] and [nameToken] and append it to
     * [contentList].
     *
     * Checks if the tag is valid as an inline tag, parses nested content until `}`, checks for
     * unclosed tags, extracts tag data (e.g. for `{@link ...}`), and appends the resulting
     * [JavadocInlineTag].
     */
    private fun addAsJavadocInlineTag(startToken: Token, nameToken: Token) {
        // The inline tag is the end of any leading whitespace so prevent any from being removed
        // from the start of the inline tag content.
        trimLeadingWhitespace = false

        val tagTypeName = nameToken.text
        val tagType = TagTypes.tagTypeOf(tagTypeName)

        // Check whether the tag type is permitted in inline form. If not, report an issue.
        if (!tagType.form.supportsInlineTag) {
            tokenIssueReporter.report(
                nameToken,
                Issues.INVALID_TAG_FORM,
                "Cannot use '$tagTypeName' as an inline tag"
            )
        }

        // Consume any spaces immediately following INLINE_TAG_NAME as part of the tag structure.
        while (peekType() == TokenType.SPACE) {
            consume()
        }

        var closed = false
        var firstContentToken: Token? = null

        // Parse nested content within the inline tag.
        val nestedTagContent =
            nestedContent(tagType.containsTextOnly) {
                while (peekType() != TokenType.EOF && peekType() != TokenType.BRACE_CLOSE) {
                    // Record the first content token for reporting purposes if needed.
                    if (firstContentToken == null) {
                        firstContentToken = peek()
                    }
                    parseInlineTagContentElement()
                }

                // If a closing brace '}' is found, consume it and mark the tag as closed.
                if (peekType() == TokenType.BRACE_CLOSE) {
                    consume()
                    closed = true
                }
            }

        // If a closing brace was not found then report the unclosed tag.
        if (!closed) {
            tokenIssueReporter.report(
                startToken,
                Issues.UNCLOSED_INLINE_TAG,
                "unclosed inline '@${tagTypeName}' tag",
            )
        }

        // Extract tag-specific data (e.g. reference for @link) from the nested content.
        val result =
            nestedTagContent?.let { tagContent ->
                val tokenForReport = firstContentToken ?: startToken
                tokenIssueReporter.reportAtToken(tokenForReport) {
                    tagContent.extractTagDataForTagType(context, tagType, tokenIssueReporter)
                }
            }

        val tagData = result?.tagData
        val remainder = result?.remainder

        // Append the inline tag to the content.
        appendContent(JavadocInlineTag(tagType, tagData, remainder))
    }

    /**
     * Treat an inline tag and its nested content as raw text.
     *
     * Used when an inline tag is nested inside a tag that only supports text (e.g. `{@code}`).
     */
    private fun treatAsText(startToken: Token, nameToken: Token) {
        appendText(startToken.text)
        appendText(nameToken.text)

        while (peekType() == TokenType.SPACE) {
            appendText(consume().text)
        }

        var closed = false
        while (peekType() != TokenType.EOF && peekType() != TokenType.BRACE_CLOSE) {
            parseInlineTagContentElement()
        }

        // If a closing brace '}' is encountered, append it as text and mark as closed.
        if (peekType() == TokenType.BRACE_CLOSE) {
            appendText(consume().text)
            closed = true
        }

        // If the tag was never closed with '}', report an unclosed inline tag issue.
        if (!closed) {
            tokenIssueReporter.report(
                startToken,
                Issues.UNCLOSED_INLINE_TAG,
                "unclosed inline '@${nameToken.text}' tag",
            )
        }
    }

    /**
     * Parse a single element inside an inline tag's content.
     *
     * Handles nested brace expressions `{ ... }`, text content, spaces, newlines, nested inline
     * tags, and nested conditional `@if` tags.
     */
    private fun parseInlineTagContentElement() {
        when (peekType()) {
            TokenType.BRACE_OPEN -> {
                // Opening brace '{'.
                // A brace expression implicitly preserves the braces in the model.
                consume()
                appendText("{")
                while (peekType() != TokenType.EOF && peekType() != TokenType.BRACE_CLOSE) {
                    parseInlineTagContentElement()
                }

                // If the matching closing brace '}' is found, consume it and append to text.
                if (peekType() == TokenType.BRACE_CLOSE) {
                    consume()
                    appendText("}")
                }
            }

            // Plain text content. Append directly to text buffer.
            TokenType.TEXT_CONTENT -> appendText(consume().text)

            // Horizontal whitespace. Append to text buffer.
            TokenType.SPACE -> appendText(consume().text)

            // Newline sequence. Append a newline (trimming preceding non-newline whitespace).
            TokenType.NEWLINE -> {
                consume()
                appendNewline()
            }

            // Nested inline tag '{@...}'. Parse with inline tag handler.
            TokenType.INLINE_TAG_START -> parseInlineTag()

            // Nested conditional '{@if...}' tag. Parse and evaluate condition.
            TokenType.INLINE_IF_TAG_START -> parseInlineIfTag()

            // End of token stream. Return without action.
            TokenType.EOF -> return

            // Any other token type. Append as literal text.
            else -> appendText(consume().text)
        }
    }

    /**
     * Parse an `{@if (expr) {trueBranch} (else {falseBranch})?}` conditional tag.
     *
     * Parses and evaluates the conditional [Expr] against [context]. Emits the true branch content
     * if the condition evaluates to `true`, or the optional false branch if it evaluates to
     * `false`. The unused branch is skipped without emitting content.
     */
    private fun parseInlineIfTag() {
        val ifStartToken = consume() // consume INLINE_IF_TAG_START

        // Expect opening parenthesis '(' for the condition expression.
        if (peekType() != TokenType.PAREN_OPEN) {
            // Missing '(': report error and attempt error recovery by skipping true branch if
            // present.
            tokenIssueReporter.report(ifStartToken, Issues.INVALID_IF_TAG, "missing <expr>")
            val token = peek()
            reporter.report(
                Issues.INVALID_JAVADOC,
                "expected '(', found '${token.text}'",
                token.line - 1,
                token.charPositionInLine
            )

            // Error recovery: skip true branch if present.
            if (peekType() == TokenType.BRACE_OPEN) {
                consume()
                skipBraceExpression()
                if (peekType() == TokenType.BRACE_CLOSE) {
                    consume()
                }
            }
            return
        }

        consume() // consume '('

        // Parse the conditional expression.
        val expr = parseExpr()

        // Expect closing parenthesis ')' after the condition expression.
        if (peekType() == TokenType.PAREN_CLOSE) {
            // Found ')': consume it.
            consume()
        } else {
            // Missing ')': report error at current token.
            val token = peek()
            reporter.report(
                Issues.INVALID_JAVADOC,
                "expected ')', found '${token.text}'",
                token.line - 1,
                token.charPositionInLine
            )
        }

        // Evaluate the expression to get a boolean result.
        val result = expr?.evaluate(context) ?: false

        // Expect opening brace '{' to begin the true branch.
        if (peekType() != TokenType.BRACE_OPEN) {
            // Missing '{': report error and exit.
            val token = peek()
            reporter.report(
                Issues.INVALID_JAVADOC,
                "expected '{', found '${token.text}'",
                token.line - 1,
                token.charPositionInLine
            )
            if (peekType() == TokenType.BRACE_CLOSE) {
                consume()
            }
            return
        }

        consume() // consume BRACE_OPEN

        // Process the true branch.
        if (result) {
            // Condition evaluated to true: parse and emit the true branch content.
            while (peekType() != TokenType.EOF && peekType() != TokenType.BRACE_CLOSE) {
                parseInlineTagContentElement()
            }
            expect(TokenType.BRACE_CLOSE, "expected '}' to close true branch")
        } else {
            // Condition evaluated to false: skip the true branch without emitting content.
            skipBraceExpression()
        }

        // Handle optional else branch.
        if (match(TokenType.IF_TAG_ELSE)) {
            // Found 'else' keyword.
            if (peekType() != TokenType.BRACE_OPEN) {
                // Missing '{' after 'else': report error.
                val token = peek()
                reporter.report(
                    Issues.INVALID_JAVADOC,
                    "expected '{' after 'else', found '${token.text}'",
                    token.line - 1,
                    token.charPositionInLine
                )
                if (peekType() == TokenType.BRACE_CLOSE) {
                    consume()
                }
                return
            }

            consume() // consume BRACE_OPEN
            if (!result) {
                // Condition evaluated to false: parse and emit the else branch content.
                while (peekType() != TokenType.EOF && peekType() != TokenType.BRACE_CLOSE) {
                    parseInlineTagContentElement()
                }
                expect(TokenType.BRACE_CLOSE, "expected '}' to close else branch")
            } else {
                // Condition evaluated to true: skip the else branch without emitting content.
                skipBraceExpression()
            }
        }

        // Expect closing brace '}' for the overall '{@if}' tag.
        expect(TokenType.BRACE_CLOSE, "expected '}' to close '@if' tag")
    }

    /**
     * Parse a conditional expression, currently limited to a function call.
     *
     * @return the parsed [Expr], or `null` if expression parsing failed.
     */
    private fun parseExpr(): Expr? {
        // Expect the function name identifier (e.g. "flag").
        val functionNameToken = expect(TokenType.IDENTIFIER, "expected function name")
        if (functionNameToken == null) {
            // If the function name is missing, skip tokens until closing parenthesis to recover.
            skipUntilParenClose()
            return null
        }

        return parseFunctionCall(functionNameToken)
    }

    /**
     * Parse a function call expression, e.g. `flag(pkg.Flags.FLAG)`.
     *
     * @param functionNameToken the token for the function name (e.g. `flag`).
     * @return the [Expr] representing the function call.
     */
    private fun parseFunctionCall(functionNameToken: Token): Expr {
        // Expect opening parenthesis '(' for the function call arguments.
        val parenOpen = expect(TokenType.PAREN_OPEN, "expected '(' after function name")
        if (parenOpen == null) {
            // Missing '('; return an empty function call representation.
            return FlagFunctionCall(null)
        }

        // Parse the qualified field reference argument: (IDENTIFIER DOT)* IDENTIFIER
        val fieldReferenceTokens = mutableListOf<Token>()
        if (peekType() == TokenType.IDENTIFIER) {
            // Field reference starts with an identifier.
            fieldReferenceTokens.add(consume())

            // Parse any dot-separated qualification segments (e.g. '.Flags.FLAG').
            while (peekType() == TokenType.DOT) {
                fieldReferenceTokens.add(consume()) // add DOT
                val nextIdent = expect(TokenType.IDENTIFIER, "expected identifier after '.'")
                if (nextIdent != null) {
                    fieldReferenceTokens.add(nextIdent)
                }
            }
        } else {
            // Missing initial identifier: report invalid Javadoc.
            val token = peek()
            reporter.report(
                Issues.INVALID_JAVADOC,
                "expected field reference, found '${token.text}'",
                token.line - 1,
                token.charPositionInLine
            )
        }

        expect(TokenType.PAREN_CLOSE, "expected ')' after field reference")
        return exprBuilder.buildFunctionCall(functionNameToken, fieldReferenceTokens)
    }

    /**
     * Error recovery helper: skips tokens until a matching closing parenthesis `)` is found or a
     * brace is reached.
     */
    private fun skipUntilParenClose() {
        var parenDepth = 1
        while (peekType() != TokenType.EOF && parenDepth > 0) {
            val token = consume()
            if (token.type == TokenType.PAREN_OPEN) {
                // Nested opening parenthesis: increase depth.
                parenDepth++
            } else if (token.type == TokenType.PAREN_CLOSE) {
                // Closing parenthesis: decrease depth.
                parenDepth--
            } else if (token.type == TokenType.BRACE_OPEN) {
                // Opening brace encountered: expression was likely unclosed before body block.
                // Step back so the parser can handle the opening brace.
                current--
                break
            }
        }
    }

    /**
     * Skips a balanced brace block without emitting any content.
     *
     * Assumes the initial opening brace `{` has already been consumed.
     */
    private fun skipBraceExpression() {
        while (peekType() != TokenType.EOF && peekType() != TokenType.BRACE_CLOSE) {
            skipBraceContent()
        }

        // If the matching closing brace '}' is found, consume it.
        if (peekType() == TokenType.BRACE_CLOSE) {
            consume()
        }
    }

    /**
     * Skips a single content element within a balanced brace block without emitting any content.
     */
    private fun skipBraceContent() {
        when (peekType()) {
            TokenType.BRACE_OPEN -> {
                consume()
                skipBraceExpression()
            }
            TokenType.INLINE_TAG_START -> {
                consume()
                if (peekType() == TokenType.INLINE_TAG_NAME) {
                    consume()
                }
                while (peekType() == TokenType.SPACE) {
                    consume()
                }
                while (peekType() != TokenType.EOF && peekType() != TokenType.BRACE_CLOSE) {
                    skipBraceContent()
                }
                if (peekType() == TokenType.BRACE_CLOSE) {
                    consume()
                }
            }
            TokenType.INLINE_IF_TAG_START -> {
                consume()
                if (peekType() == TokenType.PAREN_OPEN) {
                    skipUntilParenClose()
                }
                if (peekType() == TokenType.BRACE_OPEN) {
                    consume()
                    skipBraceExpression()
                }
                if (match(TokenType.IF_TAG_ELSE)) {
                    if (peekType() == TokenType.BRACE_OPEN) {
                        consume()
                        skipBraceExpression()
                    }
                }
                if (peekType() == TokenType.BRACE_CLOSE) {
                    consume()
                }
            }
            TokenType.EOF -> return
            else -> consume()
        }
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

    /**
     * Append [text] to [textBuffer].
     *
     * If [trimLeadingWhitespace] is true, strips leading whitespace from the start of the text.
     */
    private fun appendText(text: String) {
        // If this could be the start of the whole description block then check to see if there are
        // any leading newlines that can be skipped.
        if (trimLeadingWhitespace) {
            // Trimming leading whitespace is active.
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
            // Leading whitespace has already been handled; append text as-is.
            textBuffer.append(text)
        }
    }

    /**
     * Trim any trailing whitespace from the end of [textBuffer].
     *
     * Scans backwards from the end of [textBuffer] to find the first non-whitespace character and
     * truncates characters after that.
     */
    private fun trimTrailingWhitespaceFromTextBuffer() {
        val length = textBuffer.length
        val trimmedEnd = textBuffer.skipBackwardsOverTrailingWhitespace(length - 1) + 1
        textBuffer.setLength(trimmedEnd)
    }

    /**
     * Trim any trailing non-newline whitespace from the end of [textBuffer].
     *
     * Scans backwards from the end of [textBuffer] to find the first non-whitespace or newline
     * character and truncates characters after that.
     */
    private fun trimTrailingNonNewlineWhitespaceFromTextBuffer() {
        var end = textBuffer.length - 1
        while (end >= 0) {
            val c = textBuffer[end]

            // Stop scanning when reaching a newline or any non-whitespace character.
            if (c == '\n' || !c.isWhitespace()) break
            end -= 1
        }
        textBuffer.setLength(end + 1)
    }

    /**
     * Append a newline character to [textBuffer].
     *
     * Trims any trailing non-newline whitespace before appending the newline.
     */
    private fun appendNewline() {
        trimTrailingNonNewlineWhitespaceFromTextBuffer()
        appendText("\n")
    }

    /**
     * If [textBuffer] is not empty then this will wrap it in a [JavadocText] object, add that to
     * [contentList] and clear [textBuffer].
     *
     * @param trimTrailingWhitespace if true then remove any trailing whitespace from [textBuffer].
     */
    private fun flushText(trimTrailingWhitespace: Boolean) {
        if (textBuffer.isNotEmpty()) {
            // Text buffer has accumulated characters.

            // If required, remove any trailing whitespace from [textBuffer] before flushing.
            if (trimTrailingWhitespace) {
                trimTrailingWhitespaceFromTextBuffer()

                // If trimming trailing whitespace resulted in an empty buffer, nothing to emit.
                if (textBuffer.isEmpty()) return
            }

            val text = textBuffer.toString()
            textBuffer.clear()
            contentList.add(JavadocText(text))
        }
    }

    /**
     * Create a [JavadocContent] for nested content.
     *
     * Flushes [textBuffer], saves away [_contentList] (setting it to `null`), and invokes [body] to
     * populate [textBuffer] and [contentList]. It then calls [getContent] and restores previous
     * state.
     *
     * @param containsTextOnly whether the nested content should treat any nested inline tags as raw
     *   text.
     * @param body the parsing logic to execute for the nested content.
     * @return the parsed [JavadocContent], or `null` if empty.
     */
    @Suppress("DEPRECATION")
    private fun nestedContent(containsTextOnly: Boolean, body: () -> Unit): JavadocContent? {
        // Make sure that any text which has been appended to [textBuffer] has been added to the
        // content list so that it appears before any nested content. Trailing whitespace is not
        // trimmed as it could provide significant separation between any non-whitespace content and
        // the nested content.
        flushText(trimTrailingWhitespace = false)

        val oldInlineTagHandler = inlineTagHandler

        // Select inline tag handler: treat as raw text for text-only tags (e.g. {@code}),
        // otherwise add as structured JavadocInlineTag objects.
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
     * Get a [JavadocContent] object for any content that has been added to [textBuffer] and
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
}

/**
 * Responsible for handling an inline tag.
 *
 * Used in [JavadocParser] to select context-specific handling of inline tags (e.g. adding as a
 * structured object or treating as literal text).
 */
private interface InlineTagHandler {
    /** Determine how [parser] should handle the inline tag. */
    fun handleInlineTag(parser: JavadocParser, startToken: Token, nameToken: Token)
}
