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

import com.android.tools.metalava.model.api.flags.ApiFlagAction
import com.android.tools.metalava.model.api.flags.FlagState
import kotlin.test.assertEquals
import org.junit.Test

class ConditionalJavadocTest : BaseJavadocTest() {
    fun checkConditionalParse(
        text: String,
        flags: Map<String, Boolean> = emptyMap(),
        surfaces: Set<String> = emptySet(),
        expectedJavadocIssues: String = "",
        expectedStructure: String,
    ) {
        checkParse(
            text,
            flagToEnabledStatus = flags,
            includedSurfaces = surfaces,
            expectedJavadocIssues = expectedJavadocIssues,
            expectedStructure = expectedStructure,
        )
    }

    @Test
    fun `Test if - missing expr`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if {missing expr }}after.
                 */
            """,
            expectedJavadocIssues =
                """
                    2:11: missing <expr> [InvalidIfTag]
                    2:16: expected '(', found '{' [InvalidJavadoc]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if - unbalanced parenthesis`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG) {unbalanced parenthesis }}after.
                 */
            """,
            expectedJavadocIssues =
                """
                    2:39: expected ')', found '{' [InvalidJavadoc]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if - unknown function`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (blah()) {unknown function }}after.
                 */
            """,
            expectedJavadocIssues =
                """
                    2:17: unknown function 'blah', expected 'flag' or 'surface' [InvalidJavadocExpr]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if - missing true expression`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))}after.
                 */
            """,
            expectedJavadocIssues =
                """
                    2:39: expected '{', found '}' [InvalidJavadoc]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if - qualified flag with underscores and numbers`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(pkg.Flags.TEST_FLAG1))
                 *   {true }
                 * else
                 *   {false }
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "pkg.Flags.TEST_FLAG1" to false,
                ),
            expectedStructure =
                """
                    text: 'Before false after.'
                """,
        )
    }

    @Test
    fun `Test if - no else - false`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))
                 *   {true }
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to false,
                ),
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if - no else - true`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))
                 *   {true }
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to true,
                ),
            expectedStructure =
                """
                    text: 'Before true after.'
                """,
        )
    }

    @Test
    fun `Test if - with else - false`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))
                 *   {true }
                 * else
                 *   {false }
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to false,
                ),
            expectedStructure =
                """
                    text: 'Before false after.'
                """,
        )
    }

    @Test
    fun `Test if - with else - true`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))
                 *   {true }
                 * else
                 *   {false }
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to true,
                ),
            expectedStructure =
                """
                    text: 'Before true after.'
                """,
        )
    }

    @Test
    fun `Test if - inside if tag`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))
                 *   {{@if (flag(Flags.NESTED_TEST_FLAG))
                 *       {true }
                 *     else
                 *       {false }
                 *   }}
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to true,
                    "Flags.NESTED_TEST_FLAG" to true,
                ),
            expectedStructure =
                """
                    text: 'Before true after.'
                """,
        )
    }

    @Test
    fun `Test if - inside inline tag`() {
        checkConditionalParse(
            """
                /**
                 * {@literal Before {@if (flag(Flags.TEST_FLAG)) {inline }}after}.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to true,
                ),
            expectedStructure =
                """
                    inlineTag: literal
                      text: 'Before inline after'
                    text: '.'
                """,
        )
    }

    @Test
    fun `Test if - inside link tag - false`() {
        checkConditionalParse(
            """
                /**
                 * Before {@link {@if (flag(Flags.TEST_FLAG)) {ref1} else {ref2}} label} after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to false,
                ),
            expectedStructure =
                """
                    text: 'Before '
                    inlineTag: link LabeledRefTagData(sourceReference=ref2, resolvedReference=ClassReference(qualifiedName=resolved.ref2))
                      text: 'label'
                    text: ' after.'
                """,
        )
    }

    @Test
    fun `Test if - inside link tag - true`() {
        checkConditionalParse(
            """
                /**
                 * Before {@link {@if (flag(Flags.TEST_FLAG)) {ref1} else {ref2}} label} after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to true,
                ),
            expectedStructure =
                """
                    text: 'Before '
                    inlineTag: link LabeledRefTagData(sourceReference=ref1, resolvedReference=ClassReference(qualifiedName=resolved.ref1))
                      text: 'label'
                    text: ' after.'
                """,
        )
    }

    @Test
    fun `Test if - containing inline tag`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))
                 *   {{@code literal content} }
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to true,
                ),
            expectedStructure =
                """
                    text: 'Before '
                    inlineTag: code
                      text: 'literal content'
                    text: ' after.'
                """,
        )
    }

    @Test
    fun `Test if - containing inline tag - false`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG))
                 *   {{@code literal content} }
                 * }after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to false,
                ),
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if - containing inline tag followed by text - false`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG)) { text before {@link SomeClass} text after }} after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to false,
                ),
            expectedStructure =
                """
                    text: 'Before  after.'
                """,
        )
    }

    @Test
    fun `Test if - containing inline tag at end of branch - false`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (flag(Flags.TEST_FLAG)) { text before {@link SomeClass}}} after.
                 */
            """,
            flags =
                mapOf(
                    "Flags.TEST_FLAG" to false,
                ),
            expectedStructure =
                """
                    text: 'Before  after.'
                """,
        )
    }

    @Test
    fun `Test if surface - matching surface`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (surface("module-lib"))
                 *   {true }
                 * else
                 *   {false }
                 * }after.
                 */
            """,
            surfaces = setOf("public", "system", "module-lib"),
            expectedStructure =
                """
                    text: 'Before true after.'
                """,
        )
    }

    @Test
    fun `Test if surface - non-matching surface`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (surface("test"))
                 *   {true }
                 * else
                 *   {false }
                 * }after.
                 */
            """,
            surfaces = setOf("public", "system", "module-lib"),
            expectedStructure =
                """
                    text: 'Before false after.'
                """,
        )
    }

    @Test
    fun `Test if surface - missing argument`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (surface()) {true }}after.
                 */
            """,
            expectedJavadocIssues =
                """
                    2:25: expected string literal, found ')' [InvalidJavadoc]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if surface - non-string argument`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (surface(public)) {true }}after.
                 */
            """,
            expectedJavadocIssues =
                """
                    2:25: expected string literal, found 'public' [InvalidJavadoc]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if surface - unclosed string literal`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (surface("public
                 * )) {true }}after.
                 */
            """,
            expectedJavadocIssues =
                """
                    2:25: unclosed string literal [InvalidJavadoc]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if surface - unclosed string literal ending with escaped quote`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (surface("public\"
                 * )) {true }}after.
                 */
            """,
            expectedJavadocIssues =
                """
                    2:25: unclosed string literal [InvalidJavadoc]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if surface - invalid escape sequence`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (surface("a\xb")) {true }}after.
                 */
            """,
            expectedJavadocIssues =
                """
                    2:25: invalid escape sequence '\x' in string literal [InvalidJavadoc]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if surface - unicode escape in surface name`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (surface("pub${'\\'}u006cic")) {true }}after.
                 */
            """,
            surfaces = setOf("public"),
            expectedStructure =
                """
                    text: 'Before true after.'
                """,
        )
    }

    @Test
    fun `Test if surface - empty surface name`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (surface("")) {true }}after.
                 */
            """,
            surfaces = setOf(""),
            expectedJavadocIssues =
                """
                    2:25: invalid surface name '', must match '[a-z-]+' [InvalidJavadocExpr]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if surface - invalid surface name characters`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (surface("Public_2")) {true }}after.
                 */
            """,
            surfaces = setOf("Public_2"),
            expectedJavadocIssues =
                """
                    2:25: invalid surface name 'Public_2', must match '[a-z-]+' [InvalidJavadocExpr]
                """,
            expectedStructure =
                """
                    text: 'Before after.'
                """,
        )
    }

    @Test
    fun `Test if surface - extra token before closing paren`() {
        checkConditionalParse(
            """
                /**
                 * Before {@if (surface("public" "extra")) {true }}after.
                 */
            """,
            surfaces = setOf("public"),
            expectedJavadocIssues =
                """
                    2:34: expected ')' after surface name [InvalidJavadoc]
                """,
            expectedStructure =
                """
                    text: 'Before true after.'
                """,
        )
    }

    @Test
    fun `Test if flag - conditionFlagState passed to checkReference`() {
        val (_, context) =
            createTestDocCommentAndContext(
                input =
                    """
                        /**
                         * {@if (flag(Flags.KEPT_FLAG)) {{@link KeptTarget}}}
                         * {@if (flag(Flags.REVERTED_FLAG)) {{@link SkippedTarget}} else {{@link ElseTarget}}}
                         * {@if (flag(Flags.FINALIZED_FLAG)) {{@link FinalizedTarget}}}
                         * {@if (flag(Flags.KEPT_FLAG)) {{@if (flag(Flags.OTHER_KEPT_FLAG)) {{@link NestedTarget}}}}}
                         * {@if (surface("public")) {{@link SurfaceTarget}}}
                         * {@link OutsideTarget}
                         */
                    """,
                includedSurfaces = setOf("public"),
                flagActions =
                    mapOf(
                        "Flags.KEPT_FLAG" to ApiFlagAction.KEEP,
                        "Flags.OTHER_KEPT_FLAG" to ApiFlagAction.KEEP,
                        "Flags.REVERTED_FLAG" to ApiFlagAction.REVERT,
                        "Flags.FINALIZED_FLAG" to ApiFlagAction.FINALIZE,
                    ),
            )

        val keptState = FlagState.additionFlag("Flags.KEPT_FLAG", ApiFlagAction.KEEP)
        val otherKeptState = FlagState.additionFlag("Flags.OTHER_KEPT_FLAG", ApiFlagAction.KEEP)
        assertEquals(
            listOf(
                "resolved.KeptTarget" to keptState,
                "resolved.ElseTarget" to FlagState.NONE,
                "resolved.FinalizedTarget" to FlagState.NONE,
                "resolved.NestedTarget" to (keptState combineWith otherKeptState),
                "resolved.SurfaceTarget" to FlagState.NONE,
                "resolved.OutsideTarget" to FlagState.NONE,
            ),
            context.checkedReferences,
        )
    }

    @Test
    fun `Test if flag - inside reference tag target conditionFlagState`() {
        val (_, context) =
            createTestDocCommentAndContext(
                input =
                    """
                        /**
                         * {@link {@if (flag(Flags.KEPT_FLAG)) {InlineIfLinkTarget} else {SkippedLinkTarget}} label}
                         * {@link {@if (flag(Flags.REVERTED_FLAG)) {SkippedElseLinkTarget} else {ElseLinkTarget}} label}
                         * {@link Partial.{@if (flag(Flags.KEPT_FLAG)) {Target} else {Other}} label}
                         * {@link LabelOnlyTarget {@if (flag(Flags.KEPT_FLAG)) {flagged label}}}
                         * {@if (flag(Flags.OTHER_KEPT_FLAG)) {{@link {@if (flag(Flags.KEPT_FLAG)) {CombinedLinkTarget} else {FallbackTarget}}}}}
                         */
                    """,
                flagActions =
                    mapOf(
                        "Flags.KEPT_FLAG" to ApiFlagAction.KEEP,
                        "Flags.OTHER_KEPT_FLAG" to ApiFlagAction.KEEP,
                        "Flags.REVERTED_FLAG" to ApiFlagAction.REVERT,
                    ),
            )

        val otherKeptState = FlagState.additionFlag("Flags.OTHER_KEPT_FLAG", ApiFlagAction.KEEP)
        // TODO(b/568477816): Scope reference tag resolution by JavadocText FlagStateSpans so
        //  targets emitted completely inside {@if (flag(...))} inherit that branch's FlagState.
        assertEquals(
            listOf(
                "resolved.InlineIfLinkTarget" to FlagState.NONE,
                "resolved.ElseLinkTarget" to FlagState.NONE,
                "Partial.Target" to FlagState.NONE,
                "resolved.LabelOnlyTarget" to FlagState.NONE,
                "resolved.CombinedLinkTarget" to otherKeptState,
            ),
            context.checkedReferences,
        )
    }
}
