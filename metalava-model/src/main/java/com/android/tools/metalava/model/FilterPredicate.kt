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

import java.util.function.Predicate

/**
 * A [Predicate] that is generally used to filter [SelectableItem]s that are defined in the API, or
 * can be referenced from the API.
 *
 * A null [FilterPredicate] should be treated as if it matched everything, i.e. was `{ true }`. It
 * can be used to optimize code paths.
 */
abstract class FilterPredicate : Predicate<SelectableItem> {
    /**
     * Returns a composed [FilterPredicate] that represents a short-circuiting logical AND of this
     * predicate and [other].
     *
     * If either predicate is a composed AND predicate, it will be flattened.
     */
    fun and(other: FilterPredicate): FilterPredicate = andPredicates(this, other)

    /**
     * Returns a composed [FilterPredicate] that represents a short-circuiting logical OR of this
     * predicate and [other].
     *
     * If either predicate is a composed OR predicate, it will be flattened.
     */
    fun or(other: FilterPredicate): FilterPredicate = orPredicates(this, other)

    /**
     * Formats this predicate into [indenter] for human-readable debug output.
     *
     * The default implementation appends the simple class name. Subclasses override this to format
     * their parameters or nested predicates.
     */
    internal open fun format(indenter: Indenter) {
        indenter.append(javaClass.simpleName)
    }

    final override fun toString() = buildString {
        val indenter = Indenter(this)
        format(indenter)

        // Trim trailing newline if there was one.
        val lastIndex = length - 1
        if (get(lastIndex) == '\n') {
            setLength(lastIndex)
        }
    }
}

/** Helper for building indented, formatted text representations of predicates. */
internal class Indenter(private val builder: StringBuilder) {
    private var indent: String = ""

    /**
     * Appends [prefix] followed by a newline, increases indentation by four spaces for [body],
     * restores the previous indentation, and appends [suffix].
     */
    fun indented(prefix: String, suffix: String, body: (Indenter) -> Unit) {
        append(prefix)
        append("\n")
        val oldIndent = indent
        indent += "    "
        body(this)
        indent = oldIndent
        append(suffix)
    }

    /**
     * Appends [text] to the underlying [builder], prepending [indent] to any line that begins after
     * a newline.
     *
     * The flow processes [text] in line slices without allocating intermediate strings:
     * 1. Checks whether [builder] currently ends with a newline (`precedingNewline`). If true, any
     *    text appended at the start of this call starts on a fresh line and needs indentation.
     * 2. Loops while `start < length` of [text]:
     *     - Finds the next newline index via `text.indexOf('\n', start)`.
     *     - Determines the slice [end]: includes the newline (`newline + 1`) if found, or takes the
     *       remaining characters (`length`) if no further newlines exist.
     *     - If [precedingNewline] is true, prepends [indent] to [builder].
     *     - Appends the slice `[start, end)` directly from [text] into [builder].
     *     - If no newline was found (`newline == -1`), breaks the loop.
     *     - Advances [start] past the newline (`newline + 1`) for the next line segment.
     */
    fun append(text: String) {
        var start = 0
        val length = text.length
        var precedingNewline = builder.isNotEmpty() && builder[builder.length - 1] == '\n'
        while (start < length) {
            val newline = text.indexOf('\n', start)
            val end = if (newline == -1) length else newline + 1
            if (precedingNewline) {
                builder.append(indent)
            }
            builder.append(text, start, end)
            if (newline == -1) break
            start = newline + 1
        }
    }
}

/**
 * Invoked this optional [FilterPredicate].
 *
 * If this [FilterPredicate] is `null` then this returns `true`, otherwise it returns the result of
 * invoking [Predicate.test] on [item].
 */
fun FilterPredicate?.testOrTrue(item: SelectableItem) = this?.test(item) ?: true

/**
 * [FilterPredicate] that only returns true for items that have [SelectableItem.emit] set to true.
 */
object EmittedOnlyPredicate : FilterPredicate() {
    override fun test(t: SelectableItem) = t.emit
}

/** [FilterPredicate] that matches everything. */
object MatchAllPredicate : FilterPredicate() {
    override fun test(t: SelectableItem) = true
}

/** [FilterPredicate] that matches nothing. */
object MatchNonePredicate : FilterPredicate() {
    override fun test(t: SelectableItem) = false
}

/** [FilterPredicate] that matches if all [predicates] match. Flattens nested [AndPredicate]s. */
private class AndPredicate(predicates: List<FilterPredicate>) : FilterPredicate() {
    private val predicates: List<FilterPredicate> =
        predicates.flatMap { if (it is AndPredicate) it.predicates else listOf(it) }

    override fun test(t: SelectableItem) = predicates.all { it.test(t) }

    override fun format(indenter: Indenter) {
        indenter.indented(prefix = "AndPredicate(", suffix = ")") {
            for (predicate in predicates) {
                predicate.format(indenter)
                indenter.append("\n")
            }
        }
    }
}

/** [FilterPredicate] that matches if any [predicates] match. Flattens nested [OrPredicate]s. */
private class OrPredicate(predicates: List<FilterPredicate>) : FilterPredicate() {
    private val predicates: List<FilterPredicate> =
        predicates.flatMap { if (it is OrPredicate) it.predicates else listOf(it) }

    override fun test(t: SelectableItem) = predicates.any { it.test(t) }

    override fun format(indenter: Indenter) {
        indenter.indented(prefix = "OrPredicate(", suffix = ")") {
            for (predicate in predicates) {
                predicate.format(indenter)
                indenter.append("\n")
            }
        }
    }
}

/**
 * Returns a composed [FilterPredicate] that represents a short-circuiting logical AND of all
 * [predicates].
 *
 * If any of [predicates] are composed AND predicates, they will be flattened. If no predicates are
 * provided, the returned predicate will match everything.
 */
fun andPredicates(vararg predicates: FilterPredicate): FilterPredicate =
    AndPredicate(predicates.toList())

/**
 * Returns a composed [FilterPredicate] that represents a short-circuiting logical OR of all
 * [predicates].
 *
 * If any of [predicates] are composed OR predicates, they will be flattened. If no predicates are
 * provided, the returned predicate will match nothing.
 */
fun orPredicates(vararg predicates: FilterPredicate): FilterPredicate =
    OrPredicate(predicates.toList())
