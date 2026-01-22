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

import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.InvalidReferencableItem
import com.android.tools.metalava.model.ReferencableItem
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

/** Builds [Expr] instances. */
internal class ExprBuilder(
    private val context: ExprBuilderContext,
    private val reporter: TokenIssueReporter,
) : AntlrJavadocParserBaseVisitor<Expr>() {

    /** Build an [Expr] from [ctx]. */
    fun buildExpr(ctx: AntlrJavadocParser.ExprContext): Expr {
        return ctx.accept(this)
    }

    override fun visitFunctionCall(ctx: AntlrJavadocParser.FunctionCallContext): Expr {
        var identifier = ctx.IDENTIFIER()
        val name = identifier.text
        if (name != "flag") {
            reporter.report(
                identifier.symbol,
                Issues.INVALID_JAVADOC_EXPR,
                "unknown function '$name', expected 'flag'"
            )

            // Return an expr that always evaluates to false.
            return FlagFunctionCall(null)
        }

        // Get the context for the flag field reference.
        val fieldReferenceContext = ctx.fieldReference()

        // Get the field reference, removing any white space.
        val fieldReference = fieldReferenceContext.text.replace(Regex("""\s+"""), "")

        // Resolve the field reference.
        val resolved = context.resolveItemReference(fieldReference, NameClassification.FIELD)

        // Get the Token to use for reporting errors in the flag reference.
        val fieldSymbol = fieldReferenceContext.IDENTIFIER(0).symbol

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
                // Did not find any item.
                is InvalidReferencableItem -> {
                    reporter.report(fieldSymbol, Issues.INVALID_JAVADOC_EXPR, resolved.message)
                    null
                }
                // This should never happen as passing in NameClassification.FIELD above should
                // limit the returned types to FieldItem or InvalidReferencableItem
                else -> error("type '$fieldReference' was resolved to an unknown type $resolved")
            }

        // Create the flag function call expression. If `flagName` is `null` then this will always
        // evaluate to false.
        return FlagFunctionCall(flagName)
    }
}
