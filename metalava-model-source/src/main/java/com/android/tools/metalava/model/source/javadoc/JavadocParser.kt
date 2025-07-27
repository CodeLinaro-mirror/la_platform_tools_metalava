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

import kotlin.math.max
import org.antlr.v4.runtime.ANTLRErrorListener
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.NoViableAltException
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.TokenStream
import org.antlr.v4.runtime.tree.ErrorNode

/**
 * Parses a block of text into a [JavadocComment].
 *
 * A wrapper around [antlrParser] which actually does the parsing.
 */
class JavadocParser private constructor(private val antlrParser: AntlrJavadocParser) {

    companion object {
        /**
         * Parse [text] as a javadoc comment (optionally including the /** ... */).
         *
         * @param fileName the file where the comment was located.
         * @param startLineNumber the line number where within [fileName] where the comment starts.
         * @param errorListener the optional [ANTLRErrorListener], defaults to
         *   [JavadocErrorListener] which will throw an exception.
         */
        fun parse(
            text: String,
            fileName: String = "<unknown>",
            startLineNumber: Int = 1,
            errorListener: ANTLRErrorListener = JavadocErrorListener(fileName, startLineNumber),
        ): JavadocComment {
            val charStream = CharStreams.fromString(text)
            val lexer = AntlrJavadocLexer(charStream)
            val tokenStream = CommonTokenStream(lexer)
            val antlrParser = AntlrJavadocParser(tokenStream)
            antlrParser.removeErrorListeners()
            antlrParser.addErrorListener(errorListener)
            val parser = JavadocParser(antlrParser)
            return parser.parse()
        }
    }

    private fun parse(): JavadocComment {
        val documentationContext = antlrParser.documentation()
        val visitor =
            object : AntlrJavadocParserBaseVisitor<Void>() {
                override fun visitErrorNode(node: ErrorNode?): Void? {
                    return super.visitErrorNode(node)
                }
            }
        documentationContext.accept(visitor)
        return JavadocComment()
    }
}

/** A [BaseErrorListener] that throws an exception for syntax errors. */
internal class JavadocErrorListener(
    private val fileName: String,
    startLineNumber: Int,
) : BaseErrorListener() {
    private val lineOffset = max(0, startLineNumber - 1)

    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String?,
        e: RecognitionException?
    ) {
        // Construct a full message that includes lots of debug information about the nature of the
        // problem. This is not intended to report issues for developers to
        val fullMsg =
            e?.expectedTokens?.let { expectedTokens ->
                buildString {
                    append(msg)
                    append("\n")
                    append("  Expected:\n")
                    val vocabulary = recognizer?.vocabulary
                    for (interval in expectedTokens.intervals) {
                        for (tokenTypeIndex in interval.a.rangeTo(interval.b)) {
                            append("    ${vocabulary?.getSymbolicName(tokenTypeIndex)}\n")
                        }
                    }
                    val offendingToken = e.offendingToken
                    val tokens = recognizer?.inputStream as? TokenStream
                    if (e is NoViableAltException && tokens != null) {
                        append("  No viable alternative found for sequence:\n")
                        for (tokenIndex in
                            e.startToken.tokenIndex.rangeTo(offendingToken.tokenIndex)) {
                            val token = tokens[tokenIndex]
                            append(
                                "    ${vocabulary?.getSymbolicName(token.type)} \"${token.text}\"\n"
                            )
                        }
                    } else {
                        append("  Found:\n")
                        append(
                            "    ${vocabulary?.getSymbolicName(offendingToken.type)} \"${offendingToken.text}\"\n"
                        )
                    }
                }
            } ?: msg
        val actualLine = line + lineOffset
        error("$fileName:$actualLine:$charPositionInLine $fullMsg")
    }
}
