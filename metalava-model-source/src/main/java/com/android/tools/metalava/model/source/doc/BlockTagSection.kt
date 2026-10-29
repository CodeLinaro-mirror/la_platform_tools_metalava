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

import com.android.tools.metalava.model.doc.DocContentOwner
import com.android.tools.metalava.model.source.javadoc.DocTag
import com.android.tools.metalava.model.source.javadoc.ExtractorResult
import com.android.tools.metalava.model.source.javadoc.JavadocContent
import com.android.tools.metalava.model.source.javadoc.extractTagDataForTagType

/**
 * A block tag section of [DocComment.blockTagSections].
 *
 * Looks like:
 * ```
 *     * @<tag-type> <description>
 * ```
 */
internal interface BlockTagSection : DocContentOwner, DocTag {
    /** The type of the block tag. */
    override val tagType: TagType<*>

    /** The description of the block tag. */
    val description: JavadocContent?

    /** Implements [DocTag.content] */
    override val content: JavadocContent?
        get() = description

    /** The optional [tagType] specific data. */
    override val tagData: TagData?

    /**
     * Get the type safe tag specific data for [tagType].
     *
     * Returns `null` if [tagType] is not [BlockTagSection.tagType] or the [tagType] returned `null`
     * from [TagType.extractData].
     */
    fun <D : TagData> typeSafeTagData(tagType: TagType<D>): D?

    companion object {
        /** Sort [TagData] so that `null` comes after non-`null`. */
        private val tagDataComparator = nullsLast(naturalOrder<TagData>())

        /**
         * Comparator used to sort [BlockTagSection]s roughly according to the rules referenced in
         * [BlockTagOrder].
         */
        val comparator: Comparator<BlockTagSection> =
            // First, order by [BlockTagOrder].
            compareBy<BlockTagSection> { it.tagType.ordinal }
                // Then by tag type name for those tag types with the same ordinal, i.e. unknown tag
                // types.
                .thenBy { it.tagType.name }
                // Then by tag specific order as determined by their custom tag data, if any.
                .thenBy(tagDataComparator) { it.tagData }
    }
}

/**
 * A [BlockTagSection] that is created for practically every block tag that appears in the sources,
 * whether they end up as part of the API or not. Their creation is on the critical path of most of
 * what Metalava does and as such they have to minimize the amount of work that they do on creation
 * to avoid causing a huge performance degradation.
 *
 * As much work as possible should be deferred until after creation when this is actually interacted
 * with as that only happens when processing documentation that will be part of the API.
 */
internal class DefaultBlockTagSection(
    context: DocCommentContext,
    override val tagType: TagType<*>,
    descriptionSupplier: ContentSupplier,
) :
    DescriptionOwner(
        context,
        descriptionSupplier,
        noComment = false,
    ),
    BlockTagSection {

    /**
     * Backing field for [tagData].
     *
     * This is initialized lazily in [initializeDescription] at the same time as [description].
     */
    private var _tagData: TagData? = null

    override val tagData: TagData?
        get() {
            // TagData is initialized at the same time as [description].
            ensureDescriptionIsInitialized()
            return _tagData
        }

    /**
     * Override to extract [TagData] from [suppliedDescription] and store [ExtractorResult.tagData]
     * in [_tagData] and then delegate to the super method to store [ExtractorResult.remainder] in
     * [description].
     */
    override fun initializeDescription(suppliedDescription: JavadocContent?) {
        val result: ExtractorResult? =
            suppliedDescription?.extractTagDataForTagType(
                context,
                tagType,
                descriptionSupplier.reporter,
            )
        _tagData = result?.tagData

        // Delegate to the super method to store the remainder in description.
        super.initializeDescription(result?.remainder)
    }

    override fun <D : TagData> typeSafeTagData(tagType: TagType<D>): D? {
        if (this.tagType != tagType) return null
        @Suppress("UNCHECKED_CAST") // Safe cast as tagData was created by tagType
        return tagData as D?
    }

    override fun toString() = buildString {
        append("@")
        append(tagType)
        append(" ")
        // Use descriptionSupplier's toString not description's as accessing the latter changes the
        // state of this which is not recommended in toString() methods that may be used for
        // debugging as that can change the behavior. It also requires lots of work and could result
        // in performance degradation while debugging which can also affect behavior.
        append(descriptionSupplier)
        // This purposely does not include tagData as that can be expensive to create and while this
        // should only be used for debugging it is bad practice to do lots of work in toString
        // methods. Particularly, when that work could throw exceptions or degrade performance.
    }
}
