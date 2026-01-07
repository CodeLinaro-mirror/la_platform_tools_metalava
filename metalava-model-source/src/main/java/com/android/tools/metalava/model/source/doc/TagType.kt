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

package com.android.tools.metalava.model.source.doc

import com.android.tools.metalava.model.source.javadoc.JavadocContent
import com.android.tools.metalava.reporter.LocationSpecificReporter

/**
 * Base type of all tag specific data.
 *
 * Provides support for adding tag specific behavior that can access the tag data.
 */
internal interface TagData : Comparable<TagData> {
    override fun compareTo(other: TagData) = 0

    /** Print [this] to [contentPrinter] adding a leading space. */
    fun JavadocContent.printWithLeadingSpaceTo(contentPrinter: JavadocContentPrinter) {
        contentPrinter.writer.print(' ')
        contentPrinter.print(this)
    }

    /**
     * Called after the block or inline tag type has been written to [writer] to print the tag
     * contents to [contentPrinter].
     *
     * If it prints anything it must first print a space to separate it from the tag type.
     *
     * This must print any content that was removed by setting [ExtractDataResult.consumedContent]
     * to a non-`0` value in [TagType.extractData] plus [content].
     */
    fun printTagContents(contentPrinter: JavadocContentPrinter, content: JavadocContent?)

    fun textMatches(predicate: (String) -> Boolean): Boolean = false
}

/** Enumerates the possible forms a [TagType] supports. */
enum class TagTypeForm(
    internal val supportsBlockTag: Boolean = false,
    internal val supportsInlineTag: Boolean = false,
) {
    /** Can be used as an inline tag. */
    INLINE(supportsInlineTag = true),

    /** Can be used as a block tag. */
    BLOCK(supportsBlockTag = true),

    /** Can be used as a block tag or an inline tag. */
    BOTH(supportsBlockTag = true, supportsInlineTag = true),
}

/** Provides tag type specific functionality for block and inline tags. */
internal abstract class TagType<D : TagData>(
    /**
     * The name of the type, as used in Javadoc, e.g. `param` for `@param p ...` block tags and
     * `link` for `{@link Class}` inline tags.
     */
    val name: String,

    /** The form that this tag type takes. */
    val form: TagTypeForm,
) {
    /**
     * The ordinal of this tag type, defining its order within all tag types.
     *
     * This affects the order in which block tags appear in the block tag sections.
     *
     * Ignored for inline tags.
     */
    val ordinal: Int = BlockTagOrder.ordinalForTagType(name)

    /** Indicates whether inline tags of this type only contain text or not. */
    open val containsTextOnly: Boolean
        get() = false

    /**
     * Extract tag type specific data [D] from [text] using [context] where necessary.
     *
     * If this tag type does not have any type specific data then it returns `null`.
     *
     * If [text] does not match the expected structure for this tag type, e.g. an `@param` tag
     * without a parameter name then it also returns `null`.
     *
     * Otherwise, return an instance of [ExtractDataResult] such that:
     * * [ExtractDataResult.tagData] is set to the instance of [D] that this created.
     * * [ExtractDataResult.consumedContent] is set to the character position with [text] where the
     *   remainder of the content starts.
     *
     * If this returns a non-null value then type [D] must implement [TagData.printTagContents] to
     * print the tag contents.
     *
     * @param text the [CharSequence] from which this must extract data. For block tags this will
     *   have no leading whitespace as it is removed from the start of the block tag description.
     *   However, for inline tags it may have leading whitespace as it is preserved for inline tags.
     */
    open fun extractData(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        text: CharSequence,
    ): ExtractDataResult<D>? = null

    /** This must be the [name] of the tag type. */
    override fun toString() = name

    /**
     * Starting with the character at position [startInclusive] and searching forwards, return the
     * position of the first whitespace character.
     */
    private fun CharSequence.skipForwardsOverNonWhitespace(startInclusive: Int): Int {
        val length = this.length
        var index = startInclusive
        while (index < length && !this[index].isWhitespace()) {
            index += 1
        }
        return index
    }

    /**
     * Find the leading identifier, if any, in [this], returning `null` if it could not be found.
     *
     * For the purposes of this method an identifier is simply a series of non-whitespace
     * characters.
     *
     * @param startInclusive the start of the identifier, must be non-whitespace otherwise this will
     *   fail to find an identifier.
     */
    internal fun CharSequence.findLeadingIdentifier(startInclusive: Int = 0): String? {
        // Find the end of the identifier by finding the first non-whitespace character.
        val endIndex = skipForwardsOverNonWhitespace(startInclusive)

        // No identifier found.
        if (endIndex == startInclusive) return null

        return substring(startInclusive, endIndex)
    }
}

/** Result of a call to [TagType.extractData]. */
internal data class ExtractDataResult<D : TagData>(
    /** The [TagData] extracted. */
    val tagData: D,

    /**
     * The number of characters of the content that was consumed when extracting the [tagData].
     *
     * If this is non-`0` then this many characters will be removed from the content from which this
     * data was extracted.
     */
    val consumedContent: Int = 0,
)

/** The default [TagType] used for all tags that do not have special behavior. */
internal class DefaultTagType(name: String, form: TagTypeForm) : TagType<TagData>(name, form) {
    override fun extractData(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        text: CharSequence
    ) = null
}

/**
 * Collection of registered [TagType]s.
 *
 * Used below to intern block and inline tags.
 *
 * Although the set of tag types is not known at compile time it is safe to intern them globally as
 * the set of tag types that could be used in a specific invocation of Metalava is small. It will
 * consist of a fixed number of standard tag types and a small set of custom tags.
 */
internal object TagTypes {
    /**
     * Cache from [TagType.name] to [TagType].
     *
     * Populated on demand by [tagTypeOf].
     */
    private val tagTypes = mutableMapOf<String, TagType<*>>()

    /**
     * Register [tagType] in [tagTypes] by [alias] if provided or [TagType.name] if not, throwing an
     * error if it collides with an existing [TagType].
     */
    fun <D : TagData> register(tagType: TagType<D>, alias: String? = null): TagType<D> {
        val name = alias ?: tagType.name
        val existing = tagTypes.put(name, tagType)
        if (existing != null) {
            error("Duplicate tag types for $name, found $existing of ${existing.javaClass}")
        }
        return tagType
    }

    /** Register a [DefaultTagType] called [name]. */
    fun registerDefaultTagType(name: String, form: TagTypeForm) =
        register(DefaultTagType(name, form))

    /**
     * Get a [TagType] for [name].
     *
     * If no such [TagType] has been registered then creates a [DefaultTagType] and caches that.
     */
    fun tagTypeOf(name: String) =
        tagTypes.computeIfAbsent(name) { name ->
            DefaultTagType(
                name,
                // Default to supporting both forms.
                TagTypeForm.BOTH
            )
        }

    // All the block [TagType]s that have specialized behavior.
    //
    // Must be in the same order as [BlockTagOrder].

    val PARAM = register(ParamTagType("param"))
    val SEE = register(SeeTagType())

    init {
        register(ThrowsTagType()).also { throwsTagType ->
            // @exception as an alias for @throws
            register(throwsTagType, alias = "exception")
        }
    }

    val DEPRECATED = registerDefaultTagType("deprecated", TagTypeForm.BLOCK)

    // Inline [TagType]s that have specialized behavior.
    //
    // Strictly speaking `inheritDoc` is an inline tag but there are block tag uses in the Android
    // source.
    val INHERIT_DOC = registerDefaultTagType("inheritDoc", TagTypeForm.BOTH)

    val CODE = register(TextOnlyInlineTagType("code"))
    val LITERAL = register(TextOnlyInlineTagType("literal"))

    init {
        register(LabeledRefTagType("link", TagTypeForm.INLINE))
        register(LabeledRefTagType("linkplain", TagTypeForm.INLINE))
    }
}
