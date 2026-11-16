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

import com.android.tools.metalava.model.ExternalReferencableItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.InvalidReferencableItem
import com.android.tools.metalava.model.ReferencableItem
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.scope.NameClassification
import com.android.tools.metalava.model.value.StringValue
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.reporter.Issues

/** Context that is made available when evaluating [Expr]. */
internal interface ExprContext {
    /**
     * Check to see whether the flag called [flagName] is enabled.
     *
     * @param flagName is the name of the flag.
     */
    fun isFlagEnabled(flagName: String): Boolean
}

/** An expression that can be used with a Javadoc conditional expression. */
internal interface Expr {
    /**
     * Evaluated this within [context] to produce a [Boolean] result.
     *
     * Note: This returns a [Boolean] as that is all that is needed at the moment for the very
     * expressions that are supported. If a more complex set of expressions is required then this
     * can be changed to return other types, e.g. maybe [Value].
     */
    fun evaluate(context: ExprContext): Boolean
}

/**
 * The `flag(...)` function call.
 *
 * @param flagName the optional flag name, if `null` then this always evaluates to `false`
 */
internal class FlagFunctionCall(private val flagName: String?) : Expr {
    override fun evaluate(context: ExprContext) =
        if (flagName == null) false else context.isFlagEnabled(flagName)
}

/** Context that is made available when building [Expr]. */
internal interface ExprBuilderContext {
    /**
     * Resolve [sourceReference] as if it was a possibly qualified reference in the source, e.g.
     * `System.out` to a [ReferencableItem].
     *
     * Returns an [InvalidReferencableItem] if it could not be resolved.
     */
    fun resolveItemReference(
        sourceReference: String,
        nameClassification: NameClassification
    ): ReferencableItem
}

/** Parses and builds [Expr] instances for conditional `@if` documentation tags. */
internal class ExprBuilder(
    /** Full source text containing the documentation comment. */
    private val text: String,

    /** Context used to resolve field references inside conditional expressions. */
    private val context: ExprBuilderContext,

    /** Reporter for syntax and expression validation issues on tokens. */
    private val reporter: TokenIssueReporter,
) {
    /**
     * Parse a conditional expression from [tokens], currently limited to a function call.
     *
     * @return the parsed [Expr], or `null` if expression parsing failed.
     */
    fun parseExpr(tokens: TokenStream): Expr? {
        val functionNameToken =
            reporter.expect(tokens, SharedTokenType.IDENTIFIER, "expected function name")
        if (functionNameToken == Token.NONE) {
            skipUntilParenClose(tokens)
            return null
        }

        return parseFunctionCall(tokens, functionNameToken)
    }

    /**
     * Parse a function call expression, e.g. `flag(pkg.Flags.FLAG)`.
     *
     * @param tokens the [TokenStream] to read arguments from.
     * @param functionNameToken the token for the function name (e.g. `flag`).
     * @return the [Expr] representing the function call.
     */
    private fun parseFunctionCall(tokens: TokenStream, functionNameToken: Token): Expr {
        val parenOpen =
            reporter.expect(tokens, SharedTokenType.PAREN_OPEN, "expected '(' after function name")
        if (parenOpen == Token.NONE) {
            return FlagFunctionCall(null)
        }

        // Parse the qualified field reference argument: (IDENTIFIER DOT)* IDENTIFIER
        val fieldReferenceTokens = mutableListOf<Token>()
        if (tokens.peekType() == SharedTokenType.IDENTIFIER) {
            fieldReferenceTokens.add(tokens.consume())

            while (tokens.peekType() == SharedTokenType.DOT) {
                fieldReferenceTokens.add(tokens.consume())
                val nextIdent =
                    reporter.expect(
                        tokens,
                        SharedTokenType.IDENTIFIER,
                        "expected identifier after '.'",
                    )
                if (nextIdent != Token.NONE) {
                    fieldReferenceTokens.add(nextIdent)
                }
            }
        } else {
            val token = tokens.peek()
            reporter.report(
                token,
                Issues.INVALID_JAVADOC,
                "expected field reference, found '${token.text(text)}'",
            )
        }

        reporter.expect(tokens, SharedTokenType.PAREN_CLOSE, "expected ')' after field reference")
        return buildFunctionCall(functionNameToken, fieldReferenceTokens)
    }

    /**
     * Error recovery helper: skips tokens in [tokens] until a matching closing parenthesis `)` is
     * found or an opening brace `{` or EOF is reached.
     */
    private fun skipUntilParenClose(tokens: TokenStream) {
        var parenDepth = 1
        while (parenDepth > 0) {
            when (tokens.peekType()) {
                SharedTokenType.PAREN_OPEN -> parenDepth++
                SharedTokenType.PAREN_CLOSE -> parenDepth--
                SharedTokenType.BRACE_OPEN,
                SharedTokenType.EOF -> {
                    // Stop before consuming `{` or EOF so the enclosing `@if` parser can still
                    // parse or recover the branch body when `(` was unclosed.
                    break
                }
            }
            tokens.consume()
        }
    }

    /**
     * Build an [Expr] from [functionNameToken] and [fieldReferenceTokens].
     *
     * Validates that the function name is `flag`, extracts and resolves the field reference to a
     * [FieldItem], and extracts its constant string value to use as the flag name. Reports an
     * [Issues.INVALID_JAVADOC_EXPR] issue if the function is unknown or the field reference cannot
     * be resolved to a constant string value.
     *
     * @param functionNameToken the token containing the function name (expected to be `flag`).
     * @param fieldReferenceTokens the list of tokens representing the qualified field reference.
     * @return a [FlagFunctionCall] expression with the resolved flag name, or `null` if invalid.
     */
    private fun buildFunctionCall(
        functionNameToken: Token,
        fieldReferenceTokens: List<Token>,
    ): Expr {
        val name = functionNameToken.text(text)
        if (name != "flag") {
            reporter.report(
                functionNameToken,
                Issues.INVALID_JAVADOC_EXPR,
                "unknown function '$name', expected 'flag'"
            )

            // Return an expr that always evaluates to false.
            return FlagFunctionCall(null)
        }

        if (fieldReferenceTokens.isEmpty()) {
            return FlagFunctionCall(null)
        }

        // Get the field reference, removing any white space.
        val fieldReference = fieldReferenceTokens.joinToString("") { it.text(text) }

        // Resolve the field reference.
        val resolved = context.resolveItemReference(fieldReference, NameClassification.FIELD)

        // Get the Token to use for reporting errors in the flag reference.
        val fieldSymbol = fieldReferenceTokens.first()

        // Determine the flag name, use `null` if no name could be determined.
        val flagName =
            when (resolved) {
                is FieldItem -> {
                    // Check the constant value.
                    val value = resolved.constantValue
                    when (value) {
                        is StringValue -> value.underlyingValue
                        else -> {
                            when (value) {
                                null -> {
                                    reporter.report(
                                        fieldSymbol,
                                        Issues.INVALID_JAVADOC_EXPR,
                                        "invalid flag field '$fieldReference', it does not have a constant value"
                                    )
                                }
                                else -> {
                                    reporter.report(
                                        fieldSymbol,
                                        Issues.INVALID_JAVADOC_EXPR,
                                        "invalid flag field '$fieldReference', expected a string value, found ${value.toValueString()} of type ${value.kind}"
                                    )
                                }
                            }
                            null
                        }
                    }
                }
                is ExternalReferencableItem -> {
                    reporter.report(
                        fieldSymbol,
                        Issues.INVALID_JAVADOC_EXPR,
                        "invalid flag field '$fieldReference', cannot reference field in external package"
                    )
                    null
                }
                // Did not find any item.
                is InvalidReferencableItem -> {
                    reporter.report(fieldSymbol, Issues.INVALID_JAVADOC_EXPR, resolved.message)
                    null
                }
                // This should never happen as passing in NameClassification.FIELD above should
                // limit the returned types to FieldItem, ExternalReferencableItem, or
                // InvalidReferencableItem
                else -> error("type '$fieldReference' was resolved to an unknown type $resolved")
            }

        // Create the flag function call expression. If `flagName` is `null` then this will always
        // evaluate to false.
        return FlagFunctionCall(flagName)
    }
}
