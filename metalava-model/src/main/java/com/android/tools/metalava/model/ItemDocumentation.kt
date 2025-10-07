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

/** A factory that will create an [ItemDocumentation] for a specific [Item]. */
typealias ItemDocumentationFactory = (Item) -> ItemDocumentation

/**
 * The documentation associated with an [Item].
 *
 * This implements [CharSequence] to simplify migration.
 */
interface ItemDocumentation {
    val text: String

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
    fun snapshot(item: Item): ItemDocumentation

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
