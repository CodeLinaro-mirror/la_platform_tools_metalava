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

import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.reporter.Issues

/** Context that is made available when evaluating [Expr]. */
internal interface ExprContext {
    /**
     * Check to see whether the flag referenced by [flagFieldReference] is enabled.
     *
     * @param flagFieldReference is a reference to a constant field that contains the name of the
     *   flag. The reference must be formatted as a normal reference in the code would be.
     */
    fun isFlagEnabled(flagFieldReference: String): Boolean
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
 * @param flagFieldReference the reference to the flag field that can be passed to
 *   [ExprContext.isFlagEnabled].
 */
internal class FlagFunctionCall(private val flagFieldReference: String) : Expr {
    override fun evaluate(context: ExprContext): Boolean {
        return context.isFlagEnabled(flagFieldReference)
    }
}

/** Builds [Expr] instances. */
internal class ExprBuilder(private val reporter: TokenIssueReporter) :
    AntlrJavadocParserBaseVisitor<Expr>() {

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
        }
        val field = ctx.fieldReference().text.replace(Regex("""\s+"""), "")
        return FlagFunctionCall(field)
    }
}
