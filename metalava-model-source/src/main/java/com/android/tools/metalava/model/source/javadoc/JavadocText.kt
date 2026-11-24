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

import com.android.tools.metalava.model.api.flags.AdditionFlagState
import com.android.tools.metalava.model.api.flags.FlagState

/**
 * Records that characters `[startInclusive, endExclusive)` within a [JavadocText] were emitted by
 * an active conditional `{@if}` scope with [flagState].
 */
internal data class FlagStateSpan(
    val startInclusive: Int,
    val endExclusive: Int,
    val flagState: FlagState,
)

/**
 * A block of text in a Javadoc comment.
 *
 * @param contents a non-empty string.
 * @param flagStateSpans optional sorted, non-overlapping [FlagStateSpan]s for substrings emitted
 *   inside active `{@if (flag(...))}` branches.
 */
internal class JavadocText(
    val contents: String,
    private val flagStateSpans: List<FlagStateSpan>? = null,
) : JavadocContent, CharSequence by contents {
    init {
        require(contents.isNotEmpty()) { "contents must contain at least one character" }
    }

    /**
     * Return the [FlagState] guarding every character in `[startInclusive, endExclusive)`, or
     * [FlagState.NONE] if any character in the range is unguarded.
     */
    fun flagStateForRange(startInclusive: Int, endExclusive: Int): FlagState {
        if (startInclusive >= endExclusive) return FlagState.NONE
        val spans = flagStateSpans ?: return FlagState.NONE

        var coveredUpTo = startInclusive
        var commonFlags: Set<String>? = null
        for (span in spans) {
            if (span.endExclusive <= startInclusive) continue
            if (span.startInclusive > coveredUpTo) return FlagState.NONE

            // Fast path when a single span covers the entire requested range.
            if (span.startInclusive <= startInclusive && span.endExclusive >= endExclusive) {
                return span.flagState
            }

            val spanFlags =
                (span.flagState.addition as? AdditionFlagState.Flagged)?.flags
                    ?: return FlagState.NONE
            commonFlags = commonFlags?.intersect(spanFlags) ?: spanFlags
            if (commonFlags.isEmpty()) return FlagState.NONE

            coveredUpTo = span.endExclusive
            if (coveredUpTo >= endExclusive) break
        }

        if (coveredUpTo < endExclusive) return FlagState.NONE
        val flags = commonFlags ?: return FlagState.NONE
        return FlagState(addition = AdditionFlagState.Flagged(flags))
    }

    /**
     * Return a [JavadocText] containing the suffix starting at [startIndex] (shifting any remaining
     * [flagStateSpans]), or `null` if [startIndex] is at the end of [contents].
     */
    fun subSequenceFrom(startIndex: Int): JavadocContent? {
        if (startIndex >= contents.length) return null
        if (startIndex == 0) return this

        val shiftedSpans =
            flagStateSpans
                ?.mapNotNull { span ->
                    if (span.endExclusive <= startIndex) {
                        null
                    } else {
                        FlagStateSpan(
                            startInclusive = (span.startInclusive - startIndex).coerceAtLeast(0),
                            endExclusive = span.endExclusive - startIndex,
                            flagState = span.flagState,
                        )
                    }
                }
                ?.ifEmpty { null }
        return JavadocText(contents.substring(startIndex), shiftedSpans)
    }

    override fun <R> accept(visitor: JavadocContentVisitor<R>) = visitor.visit(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JavadocText

        return contents == other.contents
    }

    override fun hashCode() = contents.hashCode()

    override fun toString() =
        "JavadocText(\"${contents.replace("\n", "\\n").replace("\"", "\\\"")}\")"
}
